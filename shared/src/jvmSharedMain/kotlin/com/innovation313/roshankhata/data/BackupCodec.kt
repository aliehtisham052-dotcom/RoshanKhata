package com.innovation313.roshankhata.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The backup file's format, shared by the phone and the computer (KMP Phase
 * 4c, 18 Oct 2026): the JSON it is written as, the rows it is read back
 * into, and the one-transaction restore of the ledger. A backup made on the
 * phone opens on the computer and the other way round, because both read
 * and write it with this code.
 *
 * What only the phone has stays in [Backup]: Downloads and the app's own
 * folders, a picked file's Uri, and the shop Profile kept in
 * SharedPreferences (passed in here as JSON, handed back as
 * [BusinessProfileData]).
 */
object BackupCodec {

    /**
     * Raised to 4 when products arrived, to 5 when invoices and the Business
     * Profile text joined, to 6 when the "not a duplicate" decisions did, and
     * to 7 when goods moved into their own lines (entryItems). A version-6-or-
     * older file has no lines; restore builds them from its entries by the
     * same rule the database migration uses (EntryItem.fromLegacy), so the
     * restored book matches one that was migrated on the phone. 8 (21 Oct
     * 2026) added the counter: sales, saleItems, salePayments, auditLog, and
     * the saleId / paymentMethod / barcode columns on the old tables; 9 the
     * returns (saleReturns, saleReturnItems); 10 the staff logins (role and
     * the PIN's hash on each staff row — never the PIN); 11 stock adjustments
     * and purchase orders; 12 (schema 36) the book's settings (the owner's PIN
     * as its hash), loyalty points, shifts, every counter row's uid, product
     * packs/tax/variants, a sale line's tax and the audit log's seal; 13
     * (schema 37) the batch a stock adjustment is of; 14 (schema 38, live
     * sync) a uid on every remaining row a sync can carry — parties,
     * products, every line table, plans, staff, schemes and day closes; 15
     * (22 Oct 2026, the review) the photo path of an entry's bill, the one
     * column no backup carried — after any restore every credit bill's photo
     * was unlinked, the files back on disk with nothing pointing at them; 16
     * (28 Oct 2026, schema 39) the payment reference of a khata or cashbook
     * row — the transfer id, TID or cheque number its payment came with; 17
     * (29 Oct 2026, schema 40) the documents — an invoice row's kind
     * (quotation, proforma, sales order, challan, invoice), its status and the
     * document it was converted from (sourceDocId, a row id: a restore keeps
     * every id as written, so it points where it did; a merge re-points it
     * through the invoice map, as sourceEntryId is through the entry map); 18
     * (4 Nov 2026, schema 43) the owner's private note on a party, and the
     * picture path of a cashbook row and of a cheque (paths, as a bill photo's
     * — the files themselves travel only in a Drive backup's image archive); 19
     * (4 Nov 2026, schema 44) the book's seals (bookSeals: each day's sealed
     * entry fingerprints as base64, the digests and the witnesses' time-stamp
     * tokens) — a new table, so an older release refuses the file instead of
     * restoring a book whose proofs it would drop in silence. The helper's copy
     * (secrets = false) carries them too: they hold no PIN and no readable
     * entry, only hashes and the TSAs' signed times; 20 (16 Nov 2026, schema 45)
     * an entry's kind (entryKind: NORMAL or OPENING — an opening balance is no
     * sale, [EntryKind]) and a product's other names (otherNames). Neither is a
     * secret, so a helper's copy carries both. A file before 20 restores every
     * entry as NORMAL and every product with no other names: nothing then said
     * which entry was an opening balance; 21 (17 Nov 2026, schema 46) who
     * wrote a cashbook row (createdBy) and the held ticket a counter sale was
     * rung up from (heldUid, a uid). Neither is a secret (an entry's writer
     * already travels in a helper's copy); a file before 21 restores both as
     * null — nobody then wrote them down.
     *
     * The bump matters in one direction only: a file written today, opened by
     * an older release, is refused with "update the app first" rather than
     * imported without its newest data. Silently dropping a table the old code
     * cannot hold would look like a successful restore and lose data.
     * Reading OLD files is unaffected — every array is read optionally, so a
     * version-4 or -5 file still restores cleanly.
     */
    const val FORMAT_VERSION = 21

    /** Rows per page while streaming the big tables. */
    private const val PAGE = 2_000

    /**
     * The backup, written straight to [out] (P6, 7 Oct 2026). The small
     * tables and the profile are built as one JSONObject as they always were;
     * the three big ones are read in pages of [PAGE] by id and written one
     * record at a time, so the memory a backup needs no longer grows with the
     * book. The document is the same the owner's old backups are: an object
     * whose keys are the tables, restore reads it with the same parser.
     */
    /**
     * [progress], when given, hears how far the book is written as a whole percent (3 Nov 2026, Back up now's
     * progress ring on both platforms): the streamed tables' rows written of [KhataDao.backupStreamedRows],
     * each percent once, 100 only when the last brace is written. It changes nothing in the file.
     */
    suspend fun exportTo(
        dao: KhataDao,
        businessName: String?,
        profile: JSONObject,
        out: java.io.Writer,
        secrets: Boolean = true,
        progress: ((Int) -> Unit)? = null
    ) = dao.readAsOne { exportInside(dao, businessName, profile, out, secrets, progress) }

    private suspend fun exportInside(
        dao: KhataDao,
        businessName: String?,
        profile: JSONObject,
        out: java.io.Writer,
        secrets: Boolean,
        progress: ((Int) -> Unit)?
    ) {
        val total = if (progress != null) dao.backupStreamedRows().coerceAtLeast(1L) else 1L
        var written = 0L
        var said = -1
        fun tell() {
            if (progress == null) return
            val pct = ((written * 100) / total).toInt().coerceIn(0, 99)
            if (pct != said) { said = pct; progress(pct) }
        }
        tell()
        val small = exportSmall(dao, businessName, profile, secrets).toString(2)
        // The small document ends in "\n}" — drop that brace and add the
        // three streamed tables before closing the object ourselves.
        out.write(small.trimEnd().removeSuffix("}").trimEnd())
        suspend fun <T> table(name: String, page: suspend (Long, Int) -> List<T>, id: (T) -> Long, json: (T) -> JSONObject, size: Int = PAGE) {
            out.write(",\n  \"$name\": [")
            var after = 0L
            var first = true
            while (true) {
                val rows = page(after, size)
                if (rows.isEmpty()) break
                for (r in rows) {
                    out.write(if (first) "\n    " else ",\n    ")
                    first = false
                    out.write(json(r).toString())
                }
                written += rows.size
                tell()
                after = id(rows.last())
                if (rows.size < size) break
            }
            out.write(if (first) "]" else "\n  ]")
        }
        table("parties", dao::partiesPageForBackup, { it.id }, { partyToJson(it, secrets) })
        table("entries", dao::entriesPageForBackup, { it.id }, ::entryToJson)
        table("entryItems", dao::entryItemsPageForBackup, { it.id }, ::entryItemToJson)
        // The counter's tables and the cashbook grow with every ticket: a page at a time too (22 Oct 2026, the review).
        table("cashbook", dao::cashPageForBackup, { it.id }, ::cashToJson)
        table("sales", dao::salesPageForBackup, { it.id }, ::saleToJson)
        table("saleItems", dao::saleItemsPageForBackup, { it.id }, { saleItemToJson(it, secrets) })
        table("salePayments", dao::salePaymentsPageForBackup, { it.id }, ::salePaymentToJson)
        table("auditLog", dao::auditPageForBackup, { it.id }, ::auditToJson)
        // Format 19 (schema 44): the book's seals, a few a page — each can hold 5,000 fingerprints (7 Nov 2026, M5).
        table("bookSeals", dao::sealsPageForBackup, { it.id }, ::sealToJson, BookSeals.WITH_LEAVES_PAGE)
        out.write("\n}")
        out.flush()
        progress?.invoke(100)
    }


    /**
     * [secrets] false for a copy that leaves the owner's hands — the helper's
     * phone (22 Oct 2026, the review): the owner's PIN and recovery code and
     * each staff member's PIN are left out, as their hashes. A four-digit PIN
     * falls to a guessing program in minutes once its hash is in a file; a
     * read-only phone needs none of them.
     */
    private suspend fun exportSmall(dao: KhataDao, businessName: String?, profile: JSONObject, secrets: Boolean = true): JSONObject {
        val root = JSONObject()
        root.put("format", "RoshanKhata")
        root.put("version", FORMAT_VERSION)
        root.put("exportedAt", System.currentTimeMillis())
        // Whose book this is. Additive — an older app ignores unknown keys,
        // and a backup without it (every backup made before multi-business)
        // simply has no name to check. Restore uses it for one thing only:
        // warning the owner before this book lands in a differently-named
        // shop. It never blocks — moving a book on purpose stays possible.
        businessName?.let { root.put("businessName", it) }

        // Everything, soft-deleted rows included — the Recycle Bin is the
        // owner's data too, and a backup that quietly dropped it would be
        // throwing away something they can still get back.
        // parties, entries and entryItems — the three tables that grow with
        // the shop — are NOT built here. exportTo streams them a page at a
        // time straight into the output (P6, 7 Oct 2026): held as a
        // JSONArray tree, 300,000 lines was several hundred megabytes and
        // the backup died of memory before it wrote a byte. The file that
        // comes out is the same document; restore reads it as before.
        root.put("cheques", JSONArray().apply {
            dao.allChequesForBackup().forEach { put(chequeToJson(it)) }
        })
        // Staff (v29): who works here, the days marked, and what was handed over.
        root.put("staff", JSONArray().apply {
            dao.allStaffForBackup().forEach { st ->
                put(JSONObject().apply {
                    put("id", st.id); put("name", st.name); put("phone", st.phone ?: JSONObject.NULL)
                    put("monthlySalary", st.monthlySalary); put("isActive", st.isActive); put("createdAt", st.createdAt)
                    put("role", st.role ?: JSONObject.NULL)
                    put("pinHash", st.pinHash?.takeIf { secrets } ?: JSONObject.NULL); put("pinSalt", st.pinSalt?.takeIf { secrets } ?: JSONObject.NULL)
                    put("uid", st.uid ?: JSONObject.NULL)
                })
            }
        })
        root.put("staffAttendance", JSONArray().apply {
            dao.allAttendanceForBackup().forEach { m ->
                put(JSONObject().apply { put("id", m.id); put("staffId", m.staffId); put("day", m.day); put("status", m.status); put("uid", m.uid ?: JSONObject.NULL) })
            }
        })
        root.put("staffPayments", JSONArray().apply {
            dao.allStaffPaymentsForBackup().forEach { sp ->
                put(JSONObject().apply {
                    put("id", sp.id); put("staffId", sp.staffId); put("amount", sp.amount); put("kind", sp.kind)
                    put("timestamp", sp.timestamp); put("note", sp.note ?: JSONObject.NULL); put("isDeleted", sp.isDeleted)
                    put("uid", sp.uid ?: JSONObject.NULL)
                })
            }
        })
        root.put("schemes", JSONArray().apply {
            dao.allSchemesForBackup().forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id); put("name", s.name)
                    put("partyId", s.partyId ?: JSONObject.NULL); put("company", s.company ?: JSONObject.NULL)
                    put("startDate", s.startDate); put("endDate", s.endDate)
                    put("measure", s.measure); put("unit", s.unit ?: JSONObject.NULL); put("rewardKind", s.rewardKind)
                    put("target1", s.target1); put("reward1", s.reward1)
                    put("target2", s.target2 ?: JSONObject.NULL); put("reward2", s.reward2 ?: JSONObject.NULL)
                    put("target3", s.target3 ?: JSONObject.NULL); put("reward3", s.reward3 ?: JSONObject.NULL)
                    put("claimedAmount", s.claimedAmount ?: JSONObject.NULL); put("claimedAt", s.claimedAt ?: JSONObject.NULL)
                    put("note", s.note ?: JSONObject.NULL); put("isDeleted", s.isDeleted); put("createdAt", s.createdAt)
                    put("uid", s.uid ?: JSONObject.NULL)
                })
            }
        })
        root.put("dayCloses", JSONArray().apply {
            dao.allDayClosesForBackup().forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id); put("day", c.day); put("opening", c.opening)
                    put("cashIn", c.cashIn); put("cashOut", c.cashOut); put("counted", c.counted)
                    put("note", c.note ?: JSONObject.NULL); put("closedAt", c.closedAt)
                    put("uid", c.uid ?: JSONObject.NULL)
                })
            }
        })
        root.put("plans", JSONArray().apply {
            dao.allPlansForBackup().forEach { put(planToJson(it)) }
        })
        root.put("installments", JSONArray().apply {
            dao.allInstallmentsForBackup().forEach { put(installmentToJson(it)) }
        })
        root.put("bills", JSONArray().apply {
            dao.allBillsForBackup().forEach { put(billToJson(it)) }
        })
        root.put("billItems", JSONArray().apply {
            dao.allBillItemsForBackup().forEach { put(billItemToJson(it)) }
        })
        root.put("products", JSONArray().apply {
            dao.allProductsForBackup().forEach { put(productToJson(it, secrets)) }
        })
        root.put("invoices", JSONArray().apply {
            dao.allInvoicesForBackup().forEach { put(invoiceToJson(it)) }
        })
        root.put("invoiceItems", JSONArray().apply {
            dao.allInvoiceItemsForBackup().forEach { put(invoiceItemToJson(it)) }
        })
        root.put("dismissedDuplicates", JSONArray().apply {
            dao.allDismissedDuplicatesForBackup().forEach { put(dismissedDuplicateToJson(it)) }
        })
        // The counter (format 8): every ticket, its lines and payments, and the audit trail.
        // cashbook, sales, saleItems, salePayments and auditLog are streamed by exportInside (22 Oct 2026).
        root.put("saleReturns", JSONArray().apply { dao.allReturnsForBackup().forEach { put(returnToJson(it)) } })
        root.put("saleReturnItems", JSONArray().apply { dao.allReturnItemsForBackup().forEach { put(returnItemToJson(it)) } })
        root.put("stockAdjustments", JSONArray().apply { dao.allAdjustmentsForBackup().forEach { put(adjustmentToJson(it)) } })
        root.put("purchaseOrders", JSONArray().apply { dao.allOrdersForBackup().forEach { put(orderToJson(it)) } })
        root.put("purchaseOrderItems", JSONArray().apply { dao.allOrderItemsForBackup().forEach { put(orderItemToJson(it)) } })
        // Format 12: the book's own settings, loyalty points and shifts.
        root.put("bookSettings", JSONArray().apply {
            // Waris ki Chaabi's keys (the vault's secrets, the heirs' and an heir's own links) never leave the owner's
            // hands either: not in a helper's copy, not in the Waris snapshot itself (4 Nov 2026). A full backup keeps them
            // all but W, the key the vault's copies are sealed with, which is in no copy at all (7 Nov 2026, W3: a stolen
            // backup opened every copy; a restored book makes a new W). Nor do the customer pages' pass and keys (W4).
            dao.allSettingsForBackup().filter {
                it.key != Waris.K_KEY && (secrets ||
                    (it.key !in BookSettings.OWNER_PIN_KEYS && !Waris.isSecret(it.key) && !BookSettings.isCustomerLink(it.key)))
            }.forEach { put(JSONObject().apply { put("key", it.key); put("value", it.value ?: JSONObject.NULL) }) }
        })
        root.put("loyalty", JSONArray().apply { dao.allLoyaltyForBackup().forEach { put(loyaltyToJson(it)) } })
        root.put("shifts", JSONArray().apply { dao.allShiftsForBackup().forEach { put(shiftToJson(it)) } })
        // Format 19 (schema 44): the book's seals are written by exportInside, a few a page (7 Nov 2026, M5).

        // The Business Profile — TEXT ONLY. The three image flags
        // (QR/signature/stamp saved) are deliberately excluded: restoring a
        // "QR is saved = true" onto a phone whose image file does not exist
        // (because the image backup is the separate opt-in that has not run)
        // would make a statement claim a QR it cannot draw. Those flags come
        // back with the images, in Part B, or they stay false and honest.
        root.put("businessProfile", profile)

        return root
    }

    /**
     * Read a backup the app saved itself. [passwords]: what to try on a locked backup ([BackupSeal]), the typed one and
     * this device's own; a plain backup ignores them.
     */
    fun parseFile(file: File, passwords: List<String> = emptyList()): Pair<ImportResult, ParsedBackup?> {
        return try {
            // A file too big to hold whole is read a page at a time (17 Nov 2026, the audit's L001); every other file
            // is read exactly as it always was.
            if (file.length() > pagedFromBytes) return parsePaged(file, passwords)
            file.inputStream().buffered(64 * 1024).use { parseStream(it, passwords) }
        } catch (e: Exception) {
            ImportResult.Failed(ImportResult.Why.UNREADABLE) to null
        }
    }

    // ---------- Import ----------

    sealed class ImportResult {
        data class Ok(
            val parties: Int,
            val entries: Int,
            val cheques: Int,
            val cash: Int,
            val plans: Int = 0,
            val bills: Int = 0,
            val invoices: Int = 0
        ) : ImportResult()

        /**
         * Why a file was refused, as a kind rather than a sentence (9 Oct):
         * the parsers run without a Context, and an English sentence made
         * here reached the owner inside an Urdu dialog. reason(context) (the
         * phone's BackupFailedReason.kt) words it in
         * the app's language where the dialog is built; [detail] is the
         * exception's own text, kept for diagnosis.
         */
        data class Failed(val why: Why, val count: Int = 0, val detail: String? = null) : ImportResult() {
        }

        /**
         * LOCKED: a backup locked with a password ([BackupSeal]) and none given that opens it but this device's own —
         * the screen asks for one. WRONG_PASSWORD: the passwords given do not open it. Neither says anything of the
         * book inside, and nothing is read past the lock's check (17 Nov 2026).
         */
        enum class Why { UNREADABLE, NOT_OURS, TOO_NEW, INCOMPLETE, LOCKED, WRONG_PASSWORD }
    }


    /**
     * Validate a backup's contents. Shared by every route in — a file picked
     * from storage and one restored from the app's own copy get identical
     * checks, so neither can slip past on a technicality the other would catch.
     */
    /**
     * The shop's name out of a backup, without parsing the ledger inside it.
     *
     * Used when listing what a Drive account is holding: the owner should see
     * the shop names they chose, and reading a whole book to learn one line
     * would make that list slow for no reason. Null on a backup written before
     * names were recorded, or on anything that is not one of ours.
     */
    fun businessNameOf(json: String): String? = try {
        JSONObject(json).optString("businessName").takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }

    fun parseText(text: String): Pair<ImportResult, ParsedBackup?> =
        parseStream(text.byteInputStream(Charsets.UTF_8))

    /**
     * The backup, read as a stream (P7 of the scaling work, 7 Oct 2026).
     *
     * Restore used to be JSONObject(text): the whole file as a string, then
     * the whole file again as a tree of JSONObjects, before a single row was
     * built — several hundred megabytes for a book of 300,000 lines, the same
     * death the export died before P6. Now a JsonReader walks the document
     * once. The three tables that grow with the shop (parties, entries,
     * entryItems) are turned into rows one object at a time; everything else
     * — small by nature — is gathered into one JSONObject and handed to the
     * same code that has always read it, so nothing about what a backup
     * means has changed. The row converters are the old ones.
     */
    fun parseStream(input: java.io.InputStream, passwords: List<String> = emptyList()): Pair<ImportResult, ParsedBackup?> =
        openPlain(input, passwords, ::parsePlain)

    /**
     * The plain text of a backup handed to [read], through the lock when it has one ([BackupSeal]): [parseStream] and
     * [readPages] open a file alike. A locked file is read to its very end after [read] said Ok. Inline, so a restore's
     * pass may write each page it reads from inside [read] (17 Nov 2026, L001).
     */
    private inline fun <T> openPlain(
        input: java.io.InputStream, passwords: List<String>, read: (java.io.InputStream) -> Pair<ImportResult, T?>
    ): Pair<ImportResult, T?> {
        // A locked backup (17 Nov 2026, [BackupSeal]) is told by its first four bytes; a plain one starts with "{".
        val head = if (input.markSupported()) input else java.io.BufferedInputStream(input, 64 * 1024)
        val sealed = try {
            BackupSeal.startsSealed(head)
        } catch (e: Exception) {
            return ImportResult.Failed(ImportResult.Why.UNREADABLE, detail = e.message) to null
        }
        if (!sealed) return read(head)
        return when (val opened = BackupSeal.open(head, passwords)) {
            is BackupSeal.Open.Ok -> {
                // The reader closes what it reads at its end; the lock's stream stays open for [BackupSeal.finish].
                val parsed = read(BackupSeal.notClosing(opened.plain))
                if (parsed.first !is ImportResult.Ok) parsed
                else try {
                    // To its very end: a last record missing, changed or followed by more bytes is a damaged file,
                    // even when the text inside looked whole — never a book restored from part of a file.
                    BackupSeal.finish(opened.plain)
                    parsed
                } catch (e: Exception) {
                    ImportResult.Failed(ImportResult.Why.UNREADABLE, detail = e.message) to null
                }
            }
            BackupSeal.Open.NeedPassword -> ImportResult.Failed(ImportResult.Why.LOCKED) to null
            BackupSeal.Open.WrongPassword -> ImportResult.Failed(ImportResult.Why.WRONG_PASSWORD) to null
            is BackupSeal.Open.Damaged -> ImportResult.Failed(ImportResult.Why.UNREADABLE, detail = "locked backup: " + opened.why) to null
        }
    }

    /** The plain backup's text from [input] ([parseStream] after the lock, if there was one). */
    private fun parsePlain(input: java.io.InputStream): Pair<ImportResult, ParsedBackup?> {
        return try {
            val rest = JSONObject()
            val parties = ArrayList<Party>()
            val entries = ArrayList<LedgerEntry>()
            val entryItems = ArrayList<EntryItem>()
            var hadEntryItems = false
            val streamed = Streamed()
            openJsonStream(java.io.InputStreamReader(input, Charsets.UTF_8)).use { r ->
                r.beginObject()
                while (r.hasNext()) {
                    // A table added here is added to [readPlainPages] too (BackupPagesTest holds the two alike).
                    when (val key = r.nextName()) {
                        "parties" -> readRows(r) { parties += jsonToParty(it) }
                        "entries" -> readRows(r) { entries += jsonToEntry(it) }
                        "entryItems" -> { hadEntryItems = true; readRows(r) { entryItems += jsonToEntryItem(it) } }
                        // Row by row, not gathered as a tree first (22 Oct 2026, the review).
                        "cashbook" -> readRows(r) { streamed.cash += jsonToCash(it) }
                        "sales" -> readRows(r) { streamed.sales += jsonToSale(it) }
                        "saleItems" -> readRows(r) { streamed.saleItems += jsonToSaleItem(it) }
                        "salePayments" -> readRows(r) { streamed.salePayments += jsonToSalePayment(it) }
                        "auditLog" -> readRows(r) { streamed.audit += jsonToAudit(it) }
                        "bookSeals" -> readRows(r) { streamed.seals += jsonToSeal(it) }
                        else -> rest.put(key, readValue(r))
                    }
                }
                r.endObject()
            }
            parseRoot(rest, parties, entries, if (hadEntryItems) entryItems else null, streamed)
        } catch (e: Exception) {
            ImportResult.Failed(ImportResult.Why.UNREADABLE, detail = e.message) to null
        }
    }

    // ---------- The big tables a page at a time (17 Nov 2026, the audit's L001: the first step) ----------
    //
    // A restore today holds every row of the copy at once (ParsedBackup's lists: 141 of the emulator's 192 MB for a
    // 197 MB file). [readPages] is the reader a page-at-a-time restore will stand on: one pass over the file, through the
    // lock, the same converters as [parsePlain], each table that grows with the shop handed out [pageRows] rows at a time
    // and let go. NOTHING CALLS IT YET — what remains before a restore can use it: the orphan check and the cleared
    // cash-sale pairings on id sets instead of lists ([parseRoot]), RestoreCheck from a summary of the copy, restoreAll
    // written a page at a time in one transaction, and every caller (the phone's and the computer's restores, Drive,
    // a live pairing, BookMerge). See the 17 Nov 2026 note in CLAUDE.md.

    /** One page of one of the tables that grow with the shop, in the file's order ([readPages]). */
    sealed class Page {
        abstract val size: Int
        data class Parties(val rows: List<Party>) : Page() { override val size: Int get() = rows.size }
        data class Entries(val rows: List<LedgerEntry>) : Page() { override val size: Int get() = rows.size }
        data class EntryItems(val rows: List<EntryItem>) : Page() { override val size: Int get() = rows.size }
        data class Cash(val rows: List<CashEntry>) : Page() { override val size: Int get() = rows.size }
        data class Sales(val rows: List<Sale>) : Page() { override val size: Int get() = rows.size }
        data class SaleItems(val rows: List<SaleItem>) : Page() { override val size: Int get() = rows.size }
        data class SalePayments(val rows: List<SalePayment>) : Page() { override val size: Int get() = rows.size }
        data class Audit(val rows: List<AuditLog>) : Page() { override val size: Int get() = rows.size }
        data class Seals(val rows: List<BookSeal>) : Page() { override val size: Int get() = rows.size }
    }

    /**
     * What [readPages] kept besides the pages: the file's small part as [parsePlain] keeps it (to be read by the same
     * code), how many rows each big table had (by its key in the file), and whether the file had goods lines at all (a
     * file before them has none: its lines are rebuilt from the entries by [EntryItem.fromLegacy], as [parseRoot] does).
     */
    class PagedRead(val rest: JSONObject, val counts: Map<String, Int>, val hadEntryItems: Boolean)

    /**
     * The backup in [input] with its big tables handed to [onPage] a page at a time. Pages come before the file is known
     * to be whole: they count only once the answer is Ok (a damaged or locked-and-cut file says so at its end), so a
     * restore that writes them must do so inside the transaction that this answer ends. The answer's Ok counts parties,
     * entries and cashbook rows; every big table's count is in [PagedRead.counts]. The orphan check and the cleared
     * pairings [parseRoot] does are not done here (they need the whole file's ids).
     */
    fun readPages(
        input: java.io.InputStream, passwords: List<String> = emptyList(), pageRows: Int = PAGE, onPage: (Page) -> Unit
    ): Pair<ImportResult, PagedRead?> {
        require(pageRows > 0) { "a page holds at least one row" }
        return openPlain(input, passwords) { readPlainPages(it, pageRows, onPage) }
    }

    private fun readPlainPages(input: java.io.InputStream, pageRows: Int, onPage: (Page) -> Unit): Pair<ImportResult, PagedRead?> {
        return try {
            val rest = JSONObject()
            val counts = LinkedHashMap<String, Int>()
            var hadEntryItems = false
            openJsonStream(java.io.InputStreamReader(input, Charsets.UTF_8)).use { r ->
                r.beginObject()
                while (r.hasNext()) {
                    // The same tables [parsePlain] reads row by row, and nothing else.
                    when (val key = r.nextName()) {
                        "parties" -> counts[key] = paged(r, pageRows, ::jsonToParty) { onPage(Page.Parties(it)) }
                        "entries" -> counts[key] = paged(r, pageRows, ::jsonToEntry) { onPage(Page.Entries(it)) }
                        "entryItems" -> { hadEntryItems = true; counts[key] = paged(r, pageRows, ::jsonToEntryItem) { onPage(Page.EntryItems(it)) } }
                        "cashbook" -> counts[key] = paged(r, pageRows, ::jsonToCash) { onPage(Page.Cash(it)) }
                        "sales" -> counts[key] = paged(r, pageRows, ::jsonToSale) { onPage(Page.Sales(it)) }
                        "saleItems" -> counts[key] = paged(r, pageRows, ::jsonToSaleItem) { onPage(Page.SaleItems(it)) }
                        "salePayments" -> counts[key] = paged(r, pageRows, ::jsonToSalePayment) { onPage(Page.SalePayments(it)) }
                        "auditLog" -> counts[key] = paged(r, pageRows, ::jsonToAudit) { onPage(Page.Audit(it)) }
                        "bookSeals" -> counts[key] = paged(r, pageRows, ::jsonToSeal) { onPage(Page.Seals(it)) }
                        else -> rest.put(key, readValue(r))
                    }
                }
                r.endObject()
            }
            // [parseRoot]'s first two checks, on the small part.
            when {
                rest.optString("format") != "RoshanKhata" -> ImportResult.Failed(ImportResult.Why.NOT_OURS) to null
                rest.optInt("version", -1) > FORMAT_VERSION -> ImportResult.Failed(ImportResult.Why.TOO_NEW) to null
                else -> ImportResult.Ok(
                    parties = counts["parties"] ?: 0, entries = counts["entries"] ?: 0, cheques = 0, cash = counts["cashbook"] ?: 0
                ) to PagedRead(rest, counts, hadEntryItems)
            }
        } catch (e: Exception) {
            ImportResult.Failed(ImportResult.Why.UNREADABLE, detail = e.message) to null
        }
    }

    /** An array of rows read [pageRows] at a time, each page handed to [emit] and let go; returns how many rows. */
    private fun <T> paged(r: JsonStream, pageRows: Int, convert: (JSONObject) -> T, emit: (List<T>) -> Unit): Int {
        var count = 0
        var page = ArrayList<T>(minOf(pageRows, 256))
        readRows(r) { o ->
            page.add(convert(o))
            count++
            if (page.size >= pageRows) { emit(page); page = ArrayList(minOf(pageRows, 256)) }
        }
        if (page.isNotEmpty()) emit(page)
        return count
    }

    // ---------- A big file, restored a page at a time (17 Nov 2026, the audit's L001) ----------

    /**
     * A backup file larger than this is read a page at a time and restored from its file again ([ParsedBackup.paged]);
     * every smaller one is read whole into lists, exactly as before. 32 MiB: a few hundred thousand lines, about a sixth
     * of what held an emulator's 192 MB whole; under it a book fits in memory with room to spare on any phone the app
     * runs on. The phone and the computer alike (one [parseFile]).
     */
    const val PAGED_RESTORE_BYTES: Long = 32L * 1024 * 1024

    /** No id: a row's id is never this. */
    private const val NONE = Long.MIN_VALUE

    /** Where the paged path starts: [PAGED_RESTORE_BYTES]. Lowered only by a test, to send a small book down it. */
    @Volatile var pagedFromBytes: Long = PAGED_RESTORE_BYTES

    /**
     * [file] read once, a page at a time: the small part whole, the parties whole (the restore's check and a merge need
     * them, and they are few), the big tables only as the keys they point by ([BigKeys]) and the marks [RestoreCheck]
     * compares, with the file's SHA-256 of every byte. The answer is [parseRoot]'s, on the same keys a whole read makes.
     */
    private fun parsePaged(file: File, passwords: List<String>): Pair<ImportResult, ParsedBackup?> {
        val parties = ArrayList<Party>()
        val keys = BigKeys()
        val marks = RestoreCheck.CopyMarks.Builder()
        // A file before goods lines has its lines made from its entries (EntryItem.fromLegacy), as parseRoot makes them;
        // whether the file has goods lines is known only at its end, so each such line's three keys wait here as numbers
        // (entry, product, bill line; NONE for none), never as rows.
        var legacy = LongArray(3 * 1024)
        var legacyUsed = 0
        fun keepLegacy(i: EntryItem) {
            if (legacyUsed + 3 > legacy.size) legacy = legacy.copyOf(legacy.size * 2)
            legacy[legacyUsed++] = i.entryId
            legacy[legacyUsed++] = i.productId ?: NONE
            legacy[legacyUsed++] = i.billItemId ?: NONE
        }
        val (read, sha) = hashedRead(file) { input ->
            readPages(input, passwords) { page ->
                when (page) {
                    is Page.Parties -> parties += page.rows
                    is Page.Entries -> for (e in page.rows) {
                        keys.entry(e.id, e.partyId, e.productId, e.billItemId)
                        marks.entry(e)
                        EntryItem.fromLegacy(e)?.let { keepLegacy(it) }
                    }
                    is Page.EntryItems -> for (i in page.rows) keys.entryItem(i.entryId, i.productId, i.billItemId)
                    is Page.Cash -> for (c in page.rows) marks.cash(c)
                    is Page.Sales -> for (s in page.rows) { keys.sale(s.id); marks.sale(s) }
                    is Page.SaleItems -> for (l in page.rows) keys.saleLine(l.saleId)
                    is Page.SalePayments -> for (p in page.rows) keys.saleLine(p.saleId)
                    is Page.Audit -> Unit
                    is Page.Seals -> repeat(page.size) { marks.seal() }
                }
            }
        }
        val (result, pagedRead) = read
        if (result !is ImportResult.Ok || pagedRead == null) return result to null
        if (!pagedRead.hadEntryItems) {
            var k = 0
            while (k < legacyUsed) {
                keys.entryItem(legacy[k], legacy[k + 1].takeIf { it != NONE }, legacy[k + 2].takeIf { it != NONE })
                k += 3
            }
        }
        legacy = LongArray(0)
        val (rootResult, data) = parseRoot(pagedRead.rest, parties, ArrayList(), emptyList(), Streamed(), bigKeys = keys)
        if (rootResult !is ImportResult.Ok || data == null) return rootResult to null
        val counts = pagedRead.counts
        return rootResult.copy(entries = counts["entries"] ?: 0, cash = counts["cashbook"] ?: 0) to data.copy(
            paged = Paged(file, passwords, sha, counts, pagedRead.hadEntryItems, keys, marks.build())
        )
    }

    /**
     * [read] over [file]'s bytes, with the SHA-256 of every byte of the file: what [read] took and whatever it left
     * after the end of the book. The stream [read] gets is not closed by it.
     */
    private inline fun <T> hashedRead(file: File, read: (java.io.InputStream) -> T): Pair<T, ByteArray> {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        java.security.DigestInputStream(file.inputStream(), digest).use { raw ->
            val head = java.io.BufferedInputStream(raw, 64 * 1024)
            val out = read(BackupSeal.notClosing(head))
            val rest = ByteArray(64 * 1024)
            while (head.read(rest) >= 0) Unit
            return out to digest.digest()
        }
    }

    /**
     * Each page of the big table [table] of a paged copy handed to [take] in the file's order: one more pass over the
     * file, through its lock, every other table passed over. The file must be the very bytes read before
     * ([Paged.sha256]), else this throws after the pass — inside the restore's transaction, which then undoes all of it.
     */
    private suspend fun <T> pagesOf(paged: Paged, table: String, convert: (JSONObject) -> T, take: suspend (List<T>) -> Unit) {
        val (read, sha) = hashedRead(paged.source) { input ->
            openPlain(input, paged.passwords) { plain ->
                openJsonStream(java.io.InputStreamReader(plain, Charsets.UTF_8)).use { r ->
                    r.beginObject()
                    while (r.hasNext()) {
                        if (r.nextName() != table || r.peek() == JsonKind.NULL) { r.skipValue(); continue }
                        r.beginArray()
                        var page = ArrayList<T>(256)
                        while (r.hasNext()) {
                            page.add(convert(readValue(r) as JSONObject))
                            if (page.size >= PAGE) { take(page); page = ArrayList(256) }
                        }
                        r.endArray()
                        if (page.isNotEmpty()) take(page)
                    }
                    r.endObject()
                }
                ImportResult.Ok(parties = 0, entries = 0, cheques = 0, cash = 0) to Unit
            }
        }
        if (read.first !is ImportResult.Ok) throw java.io.IOException("the backup could not be read again: ${read.first}")
        if (!sha.contentEquals(paged.sha256)) throw java.io.IOException("the backup changed after it was read")
    }

    /** A paged copy's big tables, read again from its file ([pagesOf]) with the rules [parseRoot] applies to lists. */
    private class PagedRows(private val p: Paged) : BigRows {
        override suspend fun entries(take: suspend (List<LedgerEntry>) -> Unit) =
            BackupCodec.pagesOf(p, "entries", { BackupCodec.jsonToEntry(it) }) { page ->
                // A pairing whose other half is not in the file is cleared, as parseRoot clears it ([BigKeys.pairKept]).
                take(page.map { e -> val other = e.pairedEntryId; if (other != null && p.keys.pairKept(other) == null) e.copy(pairedEntryId = null) else e })
            }

        override suspend fun entryItems(take: suspend (List<EntryItem>) -> Unit) =
            if (p.hadEntryItems) BackupCodec.pagesOf(p, "entryItems", { BackupCodec.jsonToEntryItem(it) }, take)
            // A file before goods lines: its lines are made from its entries, as parseRoot makes them.
            else BackupCodec.pagesOf(p, "entries", { BackupCodec.jsonToEntry(it) }) { page -> page.mapNotNull { EntryItem.fromLegacy(it) }.takeIf { it.isNotEmpty() }?.let { take(it) } }

        override suspend fun cash(take: suspend (List<CashEntry>) -> Unit) = BackupCodec.pagesOf(p, "cashbook", { BackupCodec.jsonToCash(it) }, take)
        override suspend fun sales(take: suspend (List<Sale>) -> Unit) = BackupCodec.pagesOf(p, "sales", { BackupCodec.jsonToSale(it) }, take)
        override suspend fun saleItems(take: suspend (List<SaleItem>) -> Unit) = BackupCodec.pagesOf(p, "saleItems", { BackupCodec.jsonToSaleItem(it) }, take)
        override suspend fun salePayments(take: suspend (List<SalePayment>) -> Unit) = BackupCodec.pagesOf(p, "salePayments", { BackupCodec.jsonToSalePayment(it) }, take)
        override suspend fun audit(take: suspend (List<AuditLog>) -> Unit) = BackupCodec.pagesOf(p, "auditLog", { BackupCodec.jsonToAudit(it) }, take)
        override suspend fun seals(take: suspend (List<BookSeal>) -> Unit) = BackupCodec.pagesOf(p, "bookSeals", { BackupCodec.jsonToSeal(it) }, take)
    }

    /**
     * The copy with every list filled: itself when it was read whole, else its file read whole now — the same bytes as
     * before (the SHA-256), or this throws. For what needs every row at once and is not a restore: a merge
     * ([BookMerge], the owner's own copy, as rare as it is deliberate). Blocking; never on a screen's thread.
     */
    fun listed(data: ParsedBackup): ParsedBackup {
        val p = data.paged ?: return data
        val (read, sha) = hashedRead(p.source) { parseStream(it, p.passwords) }
        if (!sha.contentEquals(p.sha256)) throw java.io.IOException("the backup changed after it was read")
        return read.second?.takeIf { read.first is ImportResult.Ok }
            ?: throw java.io.IOException("the backup could not be read again: ${read.first}")
    }

    /**
     * A paged copy with a file of its own in [dir] (the very bytes, checked): for a restore whose own first step may
     * remove the file it is about to read again — a daily copy rotated out by the copy kept before it. A copy read whole
     * is returned as it is. The caller deletes the new file ([Paged.source]) when the restore is through.
     */
    fun ownCopy(data: ParsedBackup, dir: File): ParsedBackup {
        val p = data.paged ?: return data
        dir.mkdirs()
        val to = File.createTempFile("rk-restore-", ".tmp", dir)
        try {
            val (_, sha) = hashedRead(p.source) { input -> to.outputStream().use { input.copyTo(it) } }
            if (!sha.contentEquals(p.sha256)) throw java.io.IOException("the backup changed after it was read")
        } catch (e: Exception) {
            to.delete()
            throw e
        }
        return data.copy(paged = Paged(to, p.passwords, p.sha256, p.counts, p.hadEntryItems, p.keys, p.marks))
    }

    /** An array of objects, each handed to [row] as it is read; a null array is nothing. */
    private fun readRows(r: JsonStream, row: (JSONObject) -> Unit) {
        if (r.peek() == JsonKind.NULL) { r.nextNull(); return }
        r.beginArray()
        while (r.hasNext()) row(readValue(r) as JSONObject)
        r.endArray()
    }

    /** One JSON value off the reader as the org.json type the old parser expects. */
    private fun readValue(r: JsonStream): Any = when (r.peek()) {
        JsonKind.BEGIN_OBJECT -> {
            val o = JSONObject()
            r.beginObject()
            while (r.hasNext()) o.put(r.nextName(), readValue(r))
            r.endObject()
            o
        }
        JsonKind.BEGIN_ARRAY -> {
            val a = JSONArray()
            r.beginArray()
            while (r.hasNext()) a.put(readValue(r))
            r.endArray()
            a
        }
        JsonKind.STRING -> r.nextString()
        JsonKind.NUMBER -> {
            val n = r.nextString()
            if (n.any { it == '.' || it == 'e' || it == 'E' }) n.toDouble() else (n.toLongOrNull() ?: n.toDouble())
        }
        JsonKind.BOOLEAN -> r.nextBoolean()
        JsonKind.NULL -> { r.nextNull(); JSONObject.NULL }
        else -> throw IllegalStateException("unexpected token ${r.peek()}")
    }

    /** The old parser, given the three big tables already read. [entryItems] null = the key was absent (a backup from before goods lines). */
    /** The tables read row by row off the stream ([parseStream]); a file without one reads it as empty, as before. */
    private class Streamed {
        val cash = ArrayList<CashEntry>()
        val sales = ArrayList<Sale>()
        val saleItems = ArrayList<SaleItem>()
        val salePayments = ArrayList<SalePayment>()
        val audit = ArrayList<AuditLog>()
        val seals = ArrayList<BookSeal>()
    }

    private fun parseRoot(
        root: JSONObject,
        parties: List<Party>,
        // Mutable: a pairing whose other half is gone is cleared in place (C1, below).
        entries: MutableList<LedgerEntry>,
        entryItems: List<EntryItem>?,
        streamed: Streamed = Streamed(),
        // A big file read a page at a time brings the keys its pages fed (17 Nov 2026, L001); a file read whole has
        // them made here from its lists.
        bigKeys: BigKeys? = null
    ): Pair<ImportResult, ParsedBackup?> {
        return try {

            if (root.optString("format") != "RoshanKhata") {
                return ImportResult.Failed(ImportResult.Why.NOT_OURS) to null
            }

            val version = root.optInt("version", -1)
            if (version > FORMAT_VERSION) {
                return ImportResult.Failed(ImportResult.Why.TOO_NEW) to null
            }

            // parties, entries and entryItems were read off the stream already
            // (parseStream); a backup from before goods lines has no entryItems
            // key and its lines are rebuilt from the entries by the shared rule.
            val entryItems = entryItems ?: entries.mapNotNull { EntryItem.fromLegacy(it) }

            val cheques = root.optJSONArray("cheques")?.let { arr ->
                (0 until arr.length()).map { jsonToCheque(arr.getJSONObject(it)) }
            } ?: emptyList()

            val cash = streamed.cash

            // Absent before v29: no staff.
            val staff = root.optJSONArray("staff")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    Staff(id = o.getLong("id"), name = o.getString("name"), phone = o.optNullableString("phone"),
                        monthlySalary = o.getDouble("monthlySalary"), isActive = o.optBoolean("isActive", true),
                        createdAt = o.optLong("createdAt", 0L),
                        // Absent before format 10: no login.
                        role = o.optNullableString("role"), pinHash = o.optNullableString("pinHash"), pinSalt = o.optNullableString("pinSalt"),
                        uid = o.uid())
                }
            } ?: emptyList()
            val attendance = root.optJSONArray("staffAttendance")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    StaffAttendance(id = o.getLong("id"), staffId = o.getLong("staffId"), day = o.getLong("day"), status = o.getInt("status"), uid = o.uid())
                }
            } ?: emptyList()
            val staffPayments = root.optJSONArray("staffPayments")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    StaffPayment(id = o.getLong("id"), staffId = o.getLong("staffId"), amount = o.getDouble("amount"),
                        kind = o.getInt("kind"), timestamp = o.getLong("timestamp"), note = o.optNullableString("note"),
                        isDeleted = o.optBoolean("isDeleted", false), uid = o.uid())
                }
            } ?: emptyList()

            // Absent before v27: no schemes.
            val schemes = root.optJSONArray("schemes")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    Scheme(
                        id = o.getLong("id"), name = o.getString("name"),
                        partyId = o.optNullableLong("partyId"), company = o.optNullableString("company"),
                        startDate = o.getLong("startDate"), endDate = o.getLong("endDate"),
                        measure = o.getString("measure"), unit = o.optNullableString("unit"),
                        rewardKind = o.getString("rewardKind"),
                        target1 = o.getDouble("target1"), reward1 = o.getDouble("reward1"),
                        target2 = o.optNullableDouble("target2"), reward2 = o.optNullableDouble("reward2"),
                        target3 = o.optNullableDouble("target3"), reward3 = o.optNullableDouble("reward3"),
                        claimedAmount = o.optNullableDouble("claimedAmount"), claimedAt = o.optNullableLong("claimedAt"),
                        note = o.optNullableString("note"), isDeleted = o.optBoolean("isDeleted", false),
                        createdAt = o.optLong("createdAt", 0L), uid = o.uid()
                    )
                }
            } ?: emptyList()

            // Absent before v26: no counts to bring back.
            val dayCloses = root.optJSONArray("dayCloses")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    DayClose(
                        id = o.getLong("id"), day = o.getLong("day"),
                        opening = o.getDouble("opening"), cashIn = o.getDouble("cashIn"),
                        cashOut = o.getDouble("cashOut"), counted = o.getDouble("counted"),
                        note = o.optNullableString("note"), closedAt = o.optLong("closedAt", 0L), uid = o.uid()
                    )
                }
            } ?: emptyList()

            // Absent in a version-1 backup. Not an error — an old file is
            // still a valid file, and rejecting it would strand anyone who
            // backed up before this release.
            val plans = root.optJSONArray("plans")?.let { arr ->
                (0 until arr.length()).map { jsonToPlan(arr.getJSONObject(it)) }
            } ?: emptyList()

            val installments = root.optJSONArray("installments")?.let { arr ->
                (0 until arr.length()).map { jsonToInstallment(arr.getJSONObject(it)) }
            } ?: emptyList()

            // Absent in older backups. Not an error — an old file is still a
            // valid file, and rejecting it would strand anyone who backed up
            // before this release.
            val bills = root.optJSONArray("bills")?.let { arr ->
                (0 until arr.length()).map { jsonToBill(arr.getJSONObject(it)) }
            } ?: emptyList()

            val billItems = root.optJSONArray("billItems")?.let { arr ->
                (0 until arr.length()).map { jsonToBillItem(arr.getJSONObject(it)) }
            } ?: emptyList()

            val products = root.optJSONArray("products")?.let { arr ->
                (0 until arr.length()).map { jsonToProduct(arr.getJSONObject(it)) }
            } ?: emptyList()

            // Absent in a version-4-or-older backup. Not an error — an old file
            // is still a valid file, and rejecting it would strand anyone who
            // backed up before invoices joined the format.
            val invoices = root.optJSONArray("invoices")?.let { arr ->
                (0 until arr.length()).map { jsonToInvoice(arr.getJSONObject(it)) }
            } ?: emptyList()

            val invoiceItems = root.optJSONArray("invoiceItems")?.let { arr ->
                (0 until arr.length()).map { jsonToInvoiceItem(arr.getJSONObject(it)) }
            } ?: emptyList()

            // Absent in a version-5-or-older backup — read optionally, like the
            // rest. No foreign key, so nothing to orphan-check.
            val dismissedDuplicates = root.optJSONArray("dismissedDuplicates")?.let { arr ->
                (0 until arr.length()).map { jsonToDismissedDuplicate(arr.getJSONObject(it)) }
            } ?: emptyList()

            // The counter (format 8). Absent before: no tickets.
            val sales: List<Sale> = streamed.sales
            val saleItems: List<SaleItem> = streamed.saleItems
            val salePayments: List<SalePayment> = streamed.salePayments
            val audit: List<AuditLog> = streamed.audit
            val returns = root.optJSONArray("saleReturns")?.let { arr ->
                (0 until arr.length()).map { jsonToReturn(arr.getJSONObject(it)) }
            } ?: emptyList()
            val returnItems = root.optJSONArray("saleReturnItems")?.let { arr ->
                (0 until arr.length()).map { jsonToReturnItem(arr.getJSONObject(it)) }
            } ?: emptyList()
            val adjustments = root.optJSONArray("stockAdjustments")?.let { arr ->
                (0 until arr.length()).map { jsonToAdjustment(arr.getJSONObject(it)) }
            } ?: emptyList()
            val orders = root.optJSONArray("purchaseOrders")?.let { arr ->
                (0 until arr.length()).map { jsonToOrder(arr.getJSONObject(it)) }
            } ?: emptyList()
            val orderItems = root.optJSONArray("purchaseOrderItems")?.let { arr ->
                (0 until arr.length()).map { jsonToOrderItem(arr.getJSONObject(it)) }
            } ?: emptyList()

            // Format 12. Absent before: no settings, no points, no shifts.
            val settings = root.optJSONArray("bookSettings")?.let { arr ->
                (0 until arr.length()).map { i -> arr.getJSONObject(i).let { BookSetting(it.getString("key"), it.optNullableString("value")) } }
            } ?: emptyList()
            val loyalty = root.optJSONArray("loyalty")?.let { arr ->
                (0 until arr.length()).map { jsonToLoyalty(arr.getJSONObject(it)) }
            } ?: emptyList()
            val shifts = root.optJSONArray("shifts")?.let { arr ->
                (0 until arr.length()).map { jsonToShift(arr.getJSONObject(it)) }
            } ?: emptyList()
            // Format 19, read row by row off the stream (7 Nov 2026, M5). Absent before: no seals.
            val bookSeals: List<BookSeal> = streamed.seals

            // Business Profile is a single optional object, not an array. Null
            // in an older file, or in one written before the owner set any
            // details — either way there is simply nothing to restore.
            val businessProfile = root.optJSONObject("businessProfile")
                ?.let { jsonToBusinessProfile(it) }

            // An entry pointing at a party that is not in the file would be
            // orphaned on insert — better to refuse than to import a ledger
            // with holes in it.
            val partyIds = parties.map { it.id }.toSet()
            val planIds = plans.map { it.id }.toSet()
            val billIds = bills.map { it.id }.toSet()
            // A productId pointing outside the file is checked the same way as
            // a partyId. Null is fine — most entries have no product — but a
            // number that leads nowhere is a hole, and holes are refused here
            // rather than discovered months later by a stock count.
            val productIds = products.map { it.id }.toSet()
            val billItemIds = billItems.map { it.id }.toSet()
            // An invoice line whose invoice is missing would be orphaned on
            // insert — and its FK is onDelete=CASCADE, so a stray line points at
            // a parent that will never exist. Refused here, the same as a bill
            // item with no bill. Invoices themselves have no parent (customerName
            // is a copied string, never a party link), so they need no check.
            val invoiceIds = invoices.map { it.id }.toSet()
            // A goods line must belong to an entry in this same file, and may
            // only point at products and batches the file also carries. A
            // cash-sale pairing must lead to an entry that exists.
            // The big tables' pointers as numbers (17 Nov 2026, L001): the same keys a big file read a page at a time
            // keeps ([BigKeys]), so a file is judged alike by either path.
            val keys = bigKeys ?: BigKeys().apply {
                for (e in entries) entry(e.id, e.partyId, e.productId, e.billItemId)
                for (i in entryItems) entryItem(i.entryId, i.productId, i.billItemId)
                // A sale line or payment cascades off its sale, as an invoice line does.
                for (s in sales) sale(s.id)
                for (l in saleItems) saleLine(l.saleId)
                for (p in salePayments) saleLine(p.saleId)
            }
            // A cash-sale pairing whose other half the bin purged for good (3 Nov 2026 audit, C1). The link is
            // a note between two rows, not a foreign key: the survivor stands on its own. Counted as a hole, it
            // made every copy of such a book refuse to restore — Drive, the daily copies, a phone's pairing — so
            // it is cleared here instead, which also rescues the copies already made ([BigKeys.pairKept]).
            for (i in entries.indices) {
                val other = entries[i].pairedEntryId
                if (other != null && keys.pairKept(other) == null) entries[i] = entries[i].copy(pairedEntryId = null)
            }
            val returnIds = returns.map { it.id }.toSet()
            val orderIds = orders.map { it.id }.toSet()
            val orphans =
                loyalty.count { it.partyId !in partyIds } +
                orderItems.count { it.orderId !in orderIds } +
                orders.count { it.partyId !in partyIds } +
                adjustments.count { it.productId !in productIds } +
                adjustments.count { it.billItemId != null && it.billItemId !in billItemIds } +
                returnItems.count { it.returnId !in returnIds } +
                // The big tables: entries' party, product and bill line; goods lines' entry, product and bill line; a
                // ticket's lines and payments ([BigKeys.orphans] — one rule with a file read a page at a time).
                keys.orphans(partyIds, productIds, billItemIds) +
                billItems.count { it.productId != null && it.productId !in productIds } +
                cheques.count { it.partyId !in partyIds } +
                plans.count { it.partyId !in partyIds } +
                installments.count { it.planId !in planIds } +
                bills.count { it.partyId !in partyIds } +
                billItems.count { it.billId !in billIds } +
                invoiceItems.count { it.invoiceId !in invoiceIds }

            if (orphans > 0) {
                return ImportResult.Failed(ImportResult.Why.INCOMPLETE, count = orphans) to null
            }

            ImportResult.Ok(
                parties = parties.size,
                entries = entries.size,
                cheques = cheques.size,
                cash = cash.size,
                plans = plans.size,
                bills = bills.size,
                invoices = invoices.size
            ) to ParsedBackup(
                parties, entries, cheques, cash, plans, installments,
                bills, billItems, products, invoices, invoiceItems, businessProfile,
                dismissedDuplicates,
                businessName = root.optString("businessName").takeIf { it.isNotBlank() },
                entryItems = entryItems,
                dayCloses = dayCloses,
                schemes = schemes,
                staff = staff,
                attendance = attendance,
                staffPayments = staffPayments,
                sales = sales,
                saleItems = saleItems,
                salePayments = salePayments,
                audit = audit,
                returns = returns,
                returnItems = returnItems,
                adjustments = adjustments,
                orders = orders,
                orderItems = orderItems,
                settings = settings,
                loyalty = loyalty,
                shifts = shifts,
                bookSeals = bookSeals,
                version = version,
                exportedAt = root.optLong("exportedAt", 0L).takeIf { it > 0L }
            )
        } catch (e: Exception) {
            ImportResult.Failed(ImportResult.Why.UNREADABLE) to null
        }
    }

    data class ParsedBackup(
        val parties: List<Party>,
        val entries: List<LedgerEntry>,
        val cheques: List<Cheque>,
        val cash: List<CashEntry>,
        val plans: List<PaymentPlan> = emptyList(),
        val installments: List<Installment> = emptyList(),
        val bills: List<SupplierBill> = emptyList(),
        val billItems: List<BillItem> = emptyList(),
        val products: List<Product> = emptyList(),
        val invoices: List<Invoice> = emptyList(),
        val invoiceItems: List<InvoiceItem> = emptyList(),
        // Null when the file had no Business Profile at all (old file, or one
        // taken before the owner filled anything in). A present-but-empty
        // profile and an absent one are treated the same on restore: nothing
        // to write.
        val businessProfile: BusinessProfileData? = null,
        val dismissedDuplicates: List<DismissedDuplicate> = emptyList(),
        /** Which shop's book this file says it is. Null on any pre-multi-business backup. */
        val businessName: String? = null,
        /** Goods lines — read from the file, or rebuilt from an older file's entries. */
        val entryItems: List<EntryItem> = emptyList(),
        val dayCloses: List<DayClose> = emptyList(),
        val schemes: List<Scheme> = emptyList(),
        val staff: List<Staff> = emptyList(),
        val attendance: List<StaffAttendance> = emptyList(),
        val staffPayments: List<StaffPayment> = emptyList(),
        /** The counter (format 8). */
        val sales: List<Sale> = emptyList(),
        val saleItems: List<SaleItem> = emptyList(),
        val salePayments: List<SalePayment> = emptyList(),
        val audit: List<AuditLog> = emptyList(),
        /** Returns (format 9). */
        val returns: List<SaleReturn> = emptyList(),
        val returnItems: List<SaleReturnItem> = emptyList(),
        /** Stock adjustments and purchase orders (format 11). */
        val adjustments: List<StockAdjustment> = emptyList(),
        val orders: List<PurchaseOrder> = emptyList(),
        val orderItems: List<PurchaseOrderItem> = emptyList(),
        /** The book's settings, loyalty points and shifts (format 12). */
        val settings: List<BookSetting> = emptyList(),
        val loyalty: List<LoyaltyEntry> = emptyList(),
        val shifts: List<Shift> = emptyList(),
        /** The book's seals (format 19). */
        val bookSeals: List<BookSeal> = emptyList(),
        /**
         * The file's format. From 12 its rows carry their uids, so a restore
         * can say exactly what it would take away ([RestoreCheck]); an older
         * file is matched by what its rows say.
         */
        val version: Int = FORMAT_VERSION,
        /** When the copy was written (every backup carries it); null for a file that does not say. */
        val exportedAt: Long? = null,
        /**
         * A copy too big to hold whole (17 Nov 2026, the audit's L001): its big tables were read a page at a time and
         * let go, and are read again from [Paged.source] when the copy is restored ([restoreLedger]). Null — the lists
         * above hold everything — for every copy read whole, which is every copy not read from a file over
         * [pagedFromBytes]. When set, [entries], [cash], [entryItems], [sales], [saleItems], [salePayments], [audit] and
         * [bookSeals] are empty: ask [entryCount], [RestoreCheck.CopyMarks.of] or [listed], never those lists.
         */
        val paged: Paged? = null
    ) {
        /** How many khata entries the copy holds, whichever way it was read. */
        val entryCount: Int get() = paged?.keys?.entries ?: entries.size
    }

    /**
     * What a copy read a page at a time keeps instead of its big tables' rows ([ParsedBackup.paged]): the file and the
     * passwords that open it, the file's SHA-256 as it was read (a restore that finds other bytes stops and changes
     * nothing), how many rows each big table had, whether the file had goods lines, the keys its rows point by
     * ([BigKeys]) and the marks [RestoreCheck] compares.
     */
    class Paged(
        val source: File,
        val passwords: List<String>,
        val sha256: ByteArray,
        val counts: Map<String, Int>,
        val hadEntryItems: Boolean,
        val keys: BigKeys,
        val marks: RestoreCheck.CopyMarks
    )

    /**
     * The Business Profile's TEXT fields, carried through a backup. Deliberately
     * no image flags and no image bytes — see the export note and Part B.
     */
    data class BusinessProfileData(
        val businessName: String?,
        val businessAddress: String?,
        val bankName: String?,
        val bankAccountTitle: String?,
        val bankIban: String?,
        val bankJazzCash: String?,
        val termsAndConditions: String?,
        val strn: String?,
        val photoOnStatement: Boolean,
        /** Absent in every backup made before 4 Oct 2026 → Rs, as those books printed. */
        val currency: String? = null,
        /** The shop's calendar key (5 Oct); absent in older backups = Gregorian. */
        val calendar: String? = null,
        /** Absent before 4 Oct 2026 → stays unset, which reads as the agri default. */
        val trade: String? = null,
        /** Added 30 Sep 2026. A file from before it has no key and restores as blank. */
        val businessPhone: String? = null,
        /** An Other shop's own name and goods (11 Oct); absent before, and for every other trade. */
        val tradeOtherName: String? = null,
        val tradeOtherItems: String? = null,
        /** The shop's name in Urdu script for Urdu posters (11 Oct); absent before. */
        val businessNameUrdu: String? = null,
        /** The shop's email (12 Oct); absent before. */
        val businessEmail: String? = null,
        /** The shop's country code (21 Oct); absent before = Pakistan. */
        val country: String? = null
    )

    /**
     * The ledger, replaced in one transaction, all-or-nothing. If any step
     * fails, the whole thing rolls back and the existing ledger is left
     * untouched. The shop Profile is the caller's (Backup.restore on the phone).
     */
    suspend fun restoreLedger(dao: KhataDao, data: ParsedBackup, keepAccess: Boolean = true) {
        // A big copy read a page at a time (17 Nov 2026, L001): the same restore body, its big tables read again from
        // the file a page at a time. Every other copy: the lists, exactly as before.
        val paged = data.paged
        if (paged != null) dao.restoreAllFrom(
            parties = data.parties, cheques = data.cheques, plans = data.plans, installments = data.installments,
            bills = data.bills, billItems = data.billItems, products = data.products, invoices = data.invoices,
            invoiceItems = data.invoiceItems, dismissedDuplicates = data.dismissedDuplicates, dayCloses = data.dayCloses,
            schemes = data.schemes, staff = data.staff, attendance = data.attendance, staffPayments = data.staffPayments,
            returns = data.returns, returnItems = data.returnItems, adjustments = data.adjustments, orders = data.orders,
            orderItems = data.orderItems, settings = data.settings, loyalty = data.loyalty, shifts = data.shifts,
            big = PagedRows(paged), keepAccess = keepAccess
        ) else dao.restoreAll(
            keepAccess = keepAccess,
            parties = data.parties,
            entries = data.entries,
            cheques = data.cheques,
            cash = data.cash,
            plans = data.plans,
            installments = data.installments,
            bills = data.bills,
            billItems = data.billItems,
            products = data.products,
            invoices = data.invoices,
            invoiceItems = data.invoiceItems,
            dismissedDuplicates = data.dismissedDuplicates,
            entryItems = data.entryItems,
            dayCloses = data.dayCloses,
            schemes = data.schemes,
            staff = data.staff,
            attendance = data.attendance,
            staffPayments = data.staffPayments,
            sales = data.sales,
            saleItems = data.saleItems,
            salePayments = data.salePayments,
            audit = data.audit,
            returns = data.returns,
            returnItems = data.returnItems,
            adjustments = data.adjustments,
            orders = data.orders,
            orderItems = data.orderItems,
            settings = data.settings,
            loyalty = data.loyalty,
            shifts = data.shifts,
            bookSeals = data.bookSeals
        )
        // The book is replaced and committed by now. Nothing below may make the restore look failed to the caller (it
        // would resume live sync and tell the owner "not restored" on a book that was replaced), and the sign-out
        // must always run (a sign-in made in the old book must not carry into this one).
        try {
            // The restore itself is the first row of the restored log (POS Phase 5). A row that cannot be written is
            // only a missing line in Who did what: the book is whole.
            try {
                dao.log(AuditAction.RESTORE, Access.userName, detail = data.businessName)
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                LiveTrouble.note("restore log row", e)
            }
        } finally {
            // The book's PINs came with it (book_settings), so a sign-in made in the old book does not
            // carry into this one (21 Oct 2026: a copy whose owner PIN someone knew made them the owner,
            // and they stayed the owner after the real book came back). Phone and computer alike.
            Access.signOut()
        }
    }

    // ---------- Mapping ----------

    // [secrets] false (a helper's copy): the owner's private note stays out (8 Nov review m42).
    private fun partyToJson(p: Party, secrets: Boolean = true) = JSONObject().apply {
        put("uid", p.uid ?: JSONObject.NULL)
        put("id", p.id)
        put("name", p.name)
        put("phone", p.phone ?: JSONObject.NULL)
        put("fatherName", p.fatherName ?: JSONObject.NULL)
        put("village", p.village ?: JSONObject.NULL)
        put("isCustomer", p.isCustomer)
        put("photoPath", p.photoPath ?: JSONObject.NULL)
        put("creditLimit", p.creditLimit ?: JSONObject.NULL)
        put("noEntryShare", p.noEntryShare)
        put("harvestPromise", p.harvestPromise ?: JSONObject.NULL)
        put("createdAt", p.createdAt)
        put("isDeleted", p.isDeleted)
        put("deletedAt", p.deletedAt ?: JSONObject.NULL)
        // The customer's card QR (22 Oct 2026): kept by a restore, so a printed card still opens its customer
        // and a live pairing gives the computer the very same book. Older files had none: a new one is made.
        put("qrToken", p.qrToken ?: JSONObject.NULL)
        // Format 18 (schema 43): the owner's private note on this person.
        put("note", p.note?.takeIf { secrets } ?: JSONObject.NULL)
    }

    private fun jsonToParty(o: JSONObject) = Party(
        uid = o.uid(),
        id = o.getLong("id"),
        name = o.getString("name"),
        phone = o.optNullableString("phone"),
        fatherName = o.optNullableString("fatherName"),
        village = o.optNullableString("village"),
        isCustomer = o.optBoolean("isCustomer", true),
        photoPath = o.optNullableString("photoPath"),
        creditLimit = o.optNullableDouble("creditLimit"),
        // Absent before v23: offer as usual.
        noEntryShare = o.optBoolean("noEntryShare", false),
        // Absent before v28: no harvest promise.
        harvestPromise = o.optNullableLong("harvestPromise"),
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        isDeleted = o.optBoolean("isDeleted", false),
        deletedAt = o.optNullableLong("deletedAt"),
        qrToken = o.optNullableString("qrToken")?.takeIf { it.isNotBlank() } ?: QrTag.newToken(),
        // Absent before format 18.
        note = o.optNullableString("note")?.takeIf { it.isNotBlank() }
    )

    private fun entryToJson(e: LedgerEntry) = JSONObject().apply {
        put("id", e.id)
        put("partyId", e.partyId)
        put("amount", e.amount)
        put("isGiven", e.isGiven)
        put("note", e.note ?: JSONObject.NULL)
        put("entryNumber", e.entryNumber)
        put("timestamp", e.timestamp)
        put("isQarzeHasna", e.isQarzeHasna)
        put("recovery", e.recovery)
        put("itemName", e.itemName ?: JSONObject.NULL)
        put("quantity", e.quantity ?: JSONObject.NULL)
        put("unit", e.unit ?: JSONObject.NULL)
        put("productId", e.productId ?: JSONObject.NULL)
        put("billItemId", e.billItemId ?: JSONObject.NULL)
        // Carried from the day the column exists, so a backup taken before
        // staff logins arrive is still a complete record of its own rows.
        put("createdBy", e.createdBy ?: JSONObject.NULL)
        put("paymentMethod", e.paymentMethod ?: JSONObject.NULL)
        // Format 16 (28 Oct 2026): the reference the payment came with (a transfer id, a TID, a cheque number).
        put("paymentRef", e.paymentRef ?: JSONObject.NULL)
        put("rateType", e.rateType ?: JSONObject.NULL)
        put("pairedEntryId", e.pairedEntryId ?: JSONObject.NULL)
        put("season", e.season ?: JSONObject.NULL)
        put("saleId", e.saleId ?: JSONObject.NULL)
        // Format 20 (schema 45): NORMAL or OPENING; an opening balance is owed, never a sale.
        put("entryKind", EntryKind.of(e.entryKind))
        // Format 15: where this phone keeps the bill's photo. An absolute path, right for the phone
        // that wrote it; a restore of the photos re-points it by file name (BackupImages), and a path
        // with no file behind it shows no photo.
        put("billPhotoPath", e.billPhotoPath ?: JSONObject.NULL)
        put("isDeleted", e.isDeleted)
        put("deletedAt", e.deletedAt ?: JSONObject.NULL)
        put("uid", e.uid ?: JSONObject.NULL)
    }

    private fun jsonToEntry(o: JSONObject) = LedgerEntry(
        id = o.getLong("id"),
        partyId = o.getLong("partyId"),
        amount = o.getDouble("amount"),
        isGiven = o.getBoolean("isGiven"),
        note = o.optNullableString("note"),
        entryNumber = o.optString("entryNumber", ""),
        timestamp = o.optLong("timestamp", System.currentTimeMillis()),
        isQarzeHasna = o.optBoolean("isQarzeHasna", false),
        recovery = o.optInt("recovery", Recovery.CERTAIN),
        itemName = o.optNullableString("itemName"),
        quantity = o.optNullableDouble("quantity"),
        unit = o.optNullableString("unit"),
        productId = o.optNullableLong("productId"),
        billItemId = o.optNullableLong("billItemId"),
        // optNullableString, not optString (18 Oct): the file writes a missing
        // creator as null, and Android's optString reads null as the word
        // "null" — every restored entry was getting createdBy = "null".
        createdBy = o.optNullableString("createdBy")?.takeIf { it.isNotBlank() },
        // Absent from every backup written before this column existed; an
        // older file restores with no method, which is what those entries
        // actually recorded.
        paymentMethod = o.optNullableString("paymentMethod"),
        // Absent before format 16: no reference was recorded (as none could be).
        paymentRef = o.optNullableString("paymentRef"),
        // Absent before format 7 — those entries never recorded either.
        rateType = o.optNullableString("rateType"),
        pairedEntryId = o.optNullableLong("pairedEntryId"),
        // Absent before v24: season read from the date.
        season = o.optNullableString("season"),
        // Absent before format 8: written by hand.
        saleId = o.optNullableLong("saleId"),
        // Absent before format 20: every entry was counted as an ordinary one. A word this release does not know is NORMAL.
        entryKind = EntryKind.of(o.optNullableString("entryKind")),
        // Absent before format 15: no photo is linked (as every restore before it did).
        billPhotoPath = o.optNullableString("billPhotoPath")?.takeIf { it.isNotBlank() },
        isDeleted = o.optBoolean("isDeleted", false),
        deletedAt = o.optNullableLong("deletedAt"),
        // Absent before format 12: a new identity, given once here.
        uid = o.uid()
    )

    private fun entryItemToJson(i: EntryItem) = JSONObject().apply {
        put("uid", i.uid ?: JSONObject.NULL)
        put("id", i.id)
        put("entryId", i.entryId)
        put("lineNo", i.lineNo)
        put("itemName", i.itemName ?: JSONObject.NULL)
        put("quantity", i.quantity ?: JSONObject.NULL)
        put("unit", i.unit ?: JSONObject.NULL)
        put("rate", i.rate ?: JSONObject.NULL)
        put("isBonus", i.isBonus)
        put("productId", i.productId ?: JSONObject.NULL)
        put("billItemId", i.billItemId ?: JSONObject.NULL)
        put("crop", i.crop ?: JSONObject.NULL)
        put("pest", i.pest ?: JSONObject.NULL)
        put("dose", i.dose ?: JSONObject.NULL)
    }

    // The real id is kept, like every other table: a line's identity must
    // survive a phone move exactly as its entry's does.
    private fun jsonToEntryItem(o: JSONObject) = EntryItem(
        uid = o.uid(),
        id = o.getLong("id"),
        entryId = o.getLong("entryId"),
        lineNo = o.optInt("lineNo", 0),
        itemName = o.optNullableString("itemName"),
        quantity = o.optNullableDouble("quantity"),
        unit = o.optNullableString("unit"),
        rate = o.optNullableDouble("rate"),
        isBonus = o.optBoolean("isBonus", false),
        productId = o.optNullableLong("productId"),
        billItemId = o.optNullableLong("billItemId"),
        // Absent before v25: no advice recorded.
        crop = o.optNullableString("crop"),
        pest = o.optNullableString("pest"),
        dose = o.optNullableString("dose")
    )

    private fun chequeToJson(c: Cheque) = JSONObject().apply {
        put("id", c.id)
        put("partyId", c.partyId)
        put("amount", c.amount)
        put("isReceived", c.isReceived)
        put("chequeNumber", c.chequeNumber ?: JSONObject.NULL)
        put("bankName", c.bankName ?: JSONObject.NULL)
        put("dueDate", c.dueDate)
        put("status", c.status)
        put("settledAt", c.settledAt ?: JSONObject.NULL)
        put("ledgerEntryId", c.ledgerEntryId ?: JSONObject.NULL)
        put("note", c.note ?: JSONObject.NULL)
        put("createdAt", c.createdAt)
        put("isDeleted", c.isDeleted)
        put("deletedAt", c.deletedAt ?: JSONObject.NULL)
        put("uid", c.uid ?: JSONObject.NULL)
        // Format 18 (schema 43): where the cheque's picture is kept.
        put("photoPath", c.photoPath ?: JSONObject.NULL)
    }

    private fun jsonToCheque(o: JSONObject) = Cheque(
        id = o.getLong("id"),
        partyId = o.getLong("partyId"),
        amount = o.getDouble("amount"),
        isReceived = o.getBoolean("isReceived"),
        chequeNumber = o.optNullableString("chequeNumber"),
        bankName = o.optNullableString("bankName"),
        dueDate = o.getLong("dueDate"),
        status = o.optInt("status", ChequeStatus.PENDING),
        settledAt = o.optNullableLong("settledAt"),
        ledgerEntryId = o.optNullableLong("ledgerEntryId"),
        note = o.optNullableString("note"),
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        isDeleted = o.optBoolean("isDeleted", false),
        deletedAt = o.optNullableLong("deletedAt"),
        uid = o.uid(),
        // Absent before format 18.
        photoPath = o.optNullableString("photoPath")?.takeIf { it.isNotBlank() }
    )

    private fun cashToJson(c: CashEntry) = JSONObject().apply {
        put("id", c.id)
        put("amount", c.amount)
        put("isIncome", c.isIncome)
        put("category", c.category)
        put("note", c.note ?: JSONObject.NULL)
        put("timestamp", c.timestamp)
        put("isDeleted", c.isDeleted)
        put("deletedAt", c.deletedAt ?: JSONObject.NULL)
        put("paymentMethod", c.paymentMethod ?: JSONObject.NULL)
        // Format 16 (28 Oct 2026): the reference the payment came with.
        put("paymentRef", c.paymentRef ?: JSONObject.NULL)
        put("saleId", c.saleId ?: JSONObject.NULL)
        put("uid", c.uid ?: JSONObject.NULL)
        // Format 18 (schema 43): where the receipt's picture is kept.
        put("photoPath", c.photoPath ?: JSONObject.NULL)
        // Format 21 (schema 46): who wrote it, null for the owner.
        put("createdBy", c.createdBy ?: JSONObject.NULL)
    }

    private fun jsonToCash(o: JSONObject) = CashEntry(
        id = o.getLong("id"),
        amount = o.getDouble("amount"),
        isIncome = o.getBoolean("isIncome"),
        category = o.optString("category", "Other"),
        note = o.optNullableString("note"),
        timestamp = o.optLong("timestamp", System.currentTimeMillis()),
        isDeleted = o.optBoolean("isDeleted", false),
        deletedAt = o.optNullableLong("deletedAt"),
        // Absent before format 8.
        paymentMethod = o.optNullableString("paymentMethod"),
        // Absent before format 16.
        paymentRef = o.optNullableString("paymentRef"),
        saleId = o.optNullableLong("saleId"),
        uid = o.uid(),
        // Absent before format 18.
        photoPath = o.optNullableString("photoPath")?.takeIf { it.isNotBlank() },
        // Absent before format 21; a blank name is no name (JSON's "null" string never becomes one — see createdBy on entries).
        createdBy = o.optNullableString("createdBy")?.takeIf { it.isNotBlank() }
    )


    private fun planToJson(p: PaymentPlan) = JSONObject().apply {
        put("uid", p.uid ?: JSONObject.NULL)
        put("id", p.id)
        put("partyId", p.partyId)
        put("totalAmount", p.totalAmount)
        put("installmentAmount", p.installmentAmount ?: JSONObject.NULL)
        put("note", p.note ?: JSONObject.NULL)
        put("nextDueDate", p.nextDueDate ?: JSONObject.NULL)
        put("createdAt", p.createdAt)
        put("isClosed", p.isClosed)
        put("closedAt", p.closedAt ?: JSONObject.NULL)
        put("isDeleted", p.isDeleted)
        put("deletedAt", p.deletedAt ?: JSONObject.NULL)
    }

    private fun jsonToPlan(o: JSONObject) = PaymentPlan(
        uid = o.uid(),
        id = o.getLong("id"),
        partyId = o.getLong("partyId"),
        totalAmount = o.getDouble("totalAmount"),
        installmentAmount = o.optNullableDouble("installmentAmount"),
        note = o.optNullableString("note"),
        nextDueDate = o.optNullableLong("nextDueDate"),
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        isClosed = o.optBoolean("isClosed", false),
        closedAt = o.optNullableLong("closedAt"),
        isDeleted = o.optBoolean("isDeleted", false),
        deletedAt = o.optNullableLong("deletedAt")
    )

    private fun installmentToJson(i: Installment) = JSONObject().apply {
        put("uid", i.uid ?: JSONObject.NULL)
        put("id", i.id)
        put("planId", i.planId)
        put("amount", i.amount)
        put("ledgerEntryId", i.ledgerEntryId)
        put("paidAt", i.paidAt)
        put("note", i.note ?: JSONObject.NULL)
        put("isDeleted", i.isDeleted)
    }

    private fun jsonToInstallment(o: JSONObject) = Installment(
        uid = o.uid(),
        id = o.getLong("id"),
        planId = o.getLong("planId"),
        amount = o.getDouble("amount"),
        ledgerEntryId = o.optLong("ledgerEntryId", 0),
        paidAt = o.optLong("paidAt", System.currentTimeMillis()),
        note = o.optNullableString("note"),
        isDeleted = o.optBoolean("isDeleted", false)
    )


    private fun billToJson(b: SupplierBill) = JSONObject().apply {
        put("id", b.id)
        put("partyId", b.partyId)
        put("billNumber", b.billNumber ?: JSONObject.NULL)
        put("totalAmount", b.totalAmount)
        put("billDate", b.billDate)
        put("dueDate", b.dueDate ?: JSONObject.NULL)
        put("ledgerEntryId", b.ledgerEntryId ?: JSONObject.NULL)
        put("isPaidInFull", b.isPaidInFull)
        put("note", b.note ?: JSONObject.NULL)
        put("createdAt", b.createdAt)
        put("isDeleted", b.isDeleted)
        put("deletedAt", b.deletedAt ?: JSONObject.NULL)
        put("photoPath", b.photoPath ?: JSONObject.NULL)
        put("uid", b.uid ?: JSONObject.NULL)
    }

    private fun jsonToBill(o: JSONObject) = SupplierBill(
        id = o.getLong("id"),
        partyId = o.getLong("partyId"),
        billNumber = o.optNullableString("billNumber"),
        totalAmount = o.getDouble("totalAmount"),
        billDate = o.optLong("billDate", System.currentTimeMillis()),
        dueDate = o.optNullableLong("dueDate"),
        ledgerEntryId = o.optNullableLong("ledgerEntryId"),
        isPaidInFull = o.optBoolean("isPaidInFull", false),
        note = o.optNullableString("note"),
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        isDeleted = o.optBoolean("isDeleted", false),
        deletedAt = o.optNullableLong("deletedAt"),
        // Absent in every backup written before v21: no photo.
        photoPath = o.optNullableString("photoPath"),
        uid = o.uid()
    )

    private fun billItemToJson(i: BillItem) = JSONObject().apply {
        put("uid", i.uid ?: JSONObject.NULL)
        put("id", i.id)
        put("billId", i.billId)
        put("productName", i.productName)
        put("batchNumber", i.batchNumber ?: JSONObject.NULL)
        put("expiryDate", i.expiryDate ?: JSONObject.NULL)
        put("quantity", i.quantity)
        put("unit", i.unit ?: JSONObject.NULL)
        put("rate", i.rate ?: JSONObject.NULL)
        put("note", i.note ?: JSONObject.NULL)
        put("productId", i.productId ?: JSONObject.NULL)
        put("isDeleted", i.isDeleted)
    }

    private fun jsonToBillItem(o: JSONObject) = BillItem(
        uid = o.uid(),
        id = o.getLong("id"),
        billId = o.getLong("billId"),
        productName = o.optString("productName", ""),
        batchNumber = o.optNullableString("batchNumber"),
        expiryDate = o.optNullableLong("expiryDate"),
        quantity = o.optDouble("quantity", 0.0),
        unit = o.optNullableString("unit"),
        rate = o.optNullableDouble("rate"),
        note = o.optNullableString("note"),
        productId = o.optNullableLong("productId"),
        isDeleted = o.optBoolean("isDeleted", false)
    )

    // JSONObject.optString returns "" for null, which would turn an absent
    // phone number into an empty string rather than leaving it absent.
    // [secrets] false: the owner's cost price stays out of a helper's copy (8 Nov review m42).
    private fun productToJson(p: Product, secrets: Boolean = true) = JSONObject().apply {
        put("uid", p.uid ?: JSONObject.NULL)
        put("id", p.id)
        put("name", p.name)
        put("nameKey", p.nameKey)
        put("normalisedName", p.normalisedName)
        put("category", p.category ?: JSONObject.NULL)
        put("defaultUnit", p.defaultUnit ?: JSONObject.NULL)
        put("company", p.company ?: JSONObject.NULL)
        put("productType", p.productType ?: JSONObject.NULL)
        put("formulation", p.formulation ?: JSONObject.NULL)
        put("registrationNumber", p.registrationNumber ?: JSONObject.NULL)
        put("technicalName", p.technicalName ?: JSONObject.NULL)
        put("salePrice", p.salePrice ?: JSONObject.NULL)
        put("creditPrice", p.creditPrice ?: JSONObject.NULL)
        put("note", p.note ?: JSONObject.NULL)
        put("barcode", p.barcode ?: JSONObject.NULL)
        put("costPrice", p.costPrice?.takeIf { secrets } ?: JSONObject.NULL)
        put("lowStockLevel", p.lowStockLevel ?: JSONObject.NULL)
        put("packUnit", p.packUnit ?: JSONObject.NULL)
        put("packSize", p.packSize ?: JSONObject.NULL)
        put("packPrice", p.packPrice ?: JSONObject.NULL)
        put("taxRate", p.taxRate ?: JSONObject.NULL)
        put("parentId", p.parentId ?: JSONObject.NULL)
        put("variant", p.variant ?: JSONObject.NULL)
        // Format 20 (schema 45): the names the shop also knows it by. Not a secret.
        put("otherNames", p.otherNames ?: JSONObject.NULL)
        put("createdAt", p.createdAt)
        put("isDeleted", p.isDeleted)
        put("deletedAt", p.deletedAt ?: JSONObject.NULL)
    }

    /**
     * The two keys are RECOMPUTED rather than trusted from the file.
     *
     * They are derived from the name, and a file that has been edited by hand
     * — or written by an older release whose folding differed — could carry a
     * key that no longer matches its own name. Recomputing costs nothing and
     * means the unique index can never be handed two rows it will reject
     * halfway through a restore.
     */
    private fun jsonToProduct(o: JSONObject): Product {
        val name = o.optString("name", "")
        return Product(
            uid = o.uid(),
            id = o.getLong("id"),
            name = name,
            nameKey = ProductName.key(name),
            normalisedName = ProductName.normalised(name),
            category = o.optNullableString("category"),
            defaultUnit = o.optNullableString("defaultUnit"),
            // Absent from every backup written before these columns existed.
            // optNullableString returns null for a key that is not there, so
            // an older file restores exactly as it always did.
            company = o.optNullableString("company"),
            productType = o.optNullableString("productType"),
            formulation = o.optNullableString("formulation"),
            registrationNumber = o.optNullableString("registrationNumber"),
            technicalName = o.optNullableString("technicalName"),
            salePrice = o.optNullableDouble("salePrice"),
            creditPrice = o.optNullableDouble("creditPrice"),
            note = o.optNullableString("note"),
            // Absent before format 8.
            barcode = o.optNullableString("barcode"),
            costPrice = o.optNullableDouble("costPrice"),
            lowStockLevel = o.optNullableDouble("lowStockLevel"),
            // Absent before format 12.
            packUnit = o.optNullableString("packUnit"),
            packSize = o.optNullableDouble("packSize"),
            packPrice = o.optNullableDouble("packPrice"),
            taxRate = o.optNullableDouble("taxRate"),
            parentId = o.optNullableLong("parentId"),
            variant = o.optNullableString("variant"),
            // Absent before format 20.
            otherNames = ProductFind.cleanOtherNames(o.optNullableString("otherNames")),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            isDeleted = o.optBoolean("isDeleted", false),
            deletedAt = o.optNullableLong("deletedAt")
        )
    }

    private fun invoiceToJson(i: Invoice) = JSONObject().apply {
        put("id", i.id)
        put("invoiceNumber", i.invoiceNumber)
        put("customerName", i.customerName)
        put("customerPhone", i.customerPhone ?: JSONObject.NULL)
        put("invoiceDate", i.invoiceDate)
        put("dueDate", i.dueDate ?: JSONObject.NULL)
        put("taxPercent", i.taxPercent ?: JSONObject.NULL)
        put("discountPercent", i.discountPercent ?: JSONObject.NULL)
        put("discountAmount", i.discountAmount ?: JSONObject.NULL)
        put("additionalChargeLabel", i.additionalChargeLabel ?: JSONObject.NULL)
        put("additionalChargeAmount", i.additionalChargeAmount ?: JSONObject.NULL)
        put("receivedAmount", i.receivedAmount ?: JSONObject.NULL)
        put("note", i.note ?: JSONObject.NULL)
        put("templateId", i.templateId)
        put("sourceEntryId", i.sourceEntryId ?: JSONObject.NULL)
        put("createdAt", i.createdAt)
        put("isDeleted", i.isDeleted)
        put("deletedAt", i.deletedAt ?: JSONObject.NULL)
        put("uid", i.uid ?: JSONObject.NULL)
        put("kind", i.kind)
        put("status", i.status)
        put("sourceDocId", i.sourceDocId ?: JSONObject.NULL)
    }

    private fun jsonToInvoice(o: JSONObject) = Invoice(
        id = o.getLong("id"),
        invoiceNumber = o.optString("invoiceNumber", ""),
        customerName = o.optString("customerName", ""),
        customerPhone = o.optNullableString("customerPhone"),
        invoiceDate = o.optLong("invoiceDate", System.currentTimeMillis()),
        dueDate = o.optNullableLong("dueDate"),
        taxPercent = o.optNullableDouble("taxPercent"),
        discountPercent = o.optNullableDouble("discountPercent"),
        // Absent from every backup made before v31: reads as null, a percent discount.
        discountAmount = o.optNullableDouble("discountAmount"),
        additionalChargeLabel = o.optNullableString("additionalChargeLabel"),
        additionalChargeAmount = o.optNullableDouble("additionalChargeAmount"),
        receivedAmount = o.optNullableDouble("receivedAmount"),
        note = o.optNullableString("note"),
        templateId = o.optInt("templateId", 1),
        // Absent before v22: not made from a sale.
        sourceEntryId = o.optNullableLong("sourceEntryId"),
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        isDeleted = o.optBoolean("isDeleted", false),
        deletedAt = o.optNullableLong("deletedAt"),
        uid = o.uid(),
        // Absent before format 17 (schema 40): every older row is an open invoice made from nothing.
        kind = o.optString("kind", "").ifBlank { DocKind.INVOICE },
        status = o.optString("status", "").ifBlank { DocStatus.OPEN },
        sourceDocId = o.optNullableLong("sourceDocId")
    )

    private fun invoiceItemToJson(i: InvoiceItem) = JSONObject().apply {
        put("uid", i.uid ?: JSONObject.NULL)
        put("id", i.id)
        put("invoiceId", i.invoiceId)
        put("itemName", i.itemName)
        put("quantity", i.quantity)
        put("unit", i.unit ?: JSONObject.NULL)
        put("rate", i.rate)
        put("isDeleted", i.isDeleted)
    }

    private fun jsonToInvoiceItem(o: JSONObject) = InvoiceItem(
        uid = o.uid(),
        id = o.getLong("id"),
        invoiceId = o.getLong("invoiceId"),
        itemName = o.optString("itemName", ""),
        quantity = o.optDouble("quantity", 0.0),
        unit = o.optNullableString("unit"),
        rate = o.optDouble("rate", 0.0),
        isDeleted = o.optBoolean("isDeleted", false)
    )

    private fun dismissedDuplicateToJson(d: DismissedDuplicate) = JSONObject().apply {
        put("partyIdsKey", d.partyIdsKey)
        put("dismissedAt", d.dismissedAt)
    }

    private fun jsonToDismissedDuplicate(o: JSONObject) = DismissedDuplicate(
        partyIdsKey = o.optString("partyIdsKey", ""),
        dismissedAt = o.optLong("dismissedAt", System.currentTimeMillis())
    )

    // ---------- Business Profile (the shop's ShopSettings) ----------

    /** The shop Profile's TEXT for a backup (no image flags: see exportSmall). */
    fun profileToJson(s: ShopSettings) = JSONObject().apply {
        put("businessName", s.businessName() ?: JSONObject.NULL)
        put("businessNameUrdu", s.businessNameUrdu() ?: JSONObject.NULL)
        put("businessAddress", s.businessAddress() ?: JSONObject.NULL)
        put("businessPhone", s.businessPhone() ?: JSONObject.NULL)
        put("businessEmail", s.businessEmail() ?: JSONObject.NULL)
        put("bankName", s.bankName() ?: JSONObject.NULL)
        put("bankAccountTitle", s.bankAccountTitle() ?: JSONObject.NULL)
        put("bankIban", s.bankIban() ?: JSONObject.NULL)
        put("bankJazzCash", s.bankJazzCash() ?: JSONObject.NULL)
        put("termsAndConditions", s.termsAndConditions() ?: JSONObject.NULL)
        put("strn", s.strn() ?: JSONObject.NULL)
        put("currency", s.currency())
        put("calendar", s.calendar().key)
        put("country", s.country().code)
        put("trade", s.tradeKey() ?: JSONObject.NULL)
        put("tradeOtherName", s.otherTradeName() ?: JSONObject.NULL)
        put("tradeOtherItems", s.otherTradeItems() ?: JSONObject.NULL)
        put("photoOnStatement", s.photoOnStatement())
    }

    fun restoreProfile(s: ShopSettings, p: BusinessProfileData) {
        s.setBusinessName(p.businessName)
        s.setBusinessNameUrdu(p.businessNameUrdu)
        s.setBusinessAddress(p.businessAddress)
        s.setBusinessPhone(p.businessPhone)
        s.setBusinessEmail(p.businessEmail)
        s.setBankName(p.bankName)
        s.setBankAccountTitle(p.bankAccountTitle)
        s.setBankIban(p.bankIban)
        s.setBankJazzCash(p.bankJazzCash)
        s.setTermsAndConditions(p.termsAndConditions)
        s.setStrn(p.strn)
        s.setCurrency(p.currency)
        s.setCalendar(ShopCalendar.Kind.of(p.calendar))
        // A copy from before the country existed keeps the shop's own choice (or Pakistan).
        p.country?.let { s.setCountry(ShopCountry.of(it)) }
        s.refreshDisplay()
        p.trade?.let { s.setTradeKey(it) }
        s.setOtherTrade(p.tradeOtherName, p.tradeOtherItems)
        s.setPhotoOnStatement(p.photoOnStatement)
    }

    private fun jsonToBusinessProfile(o: JSONObject) = BusinessProfileData(
        businessName = o.optNullableString("businessName"),
        businessAddress = o.optNullableString("businessAddress"),
        bankName = o.optNullableString("bankName"),
        bankAccountTitle = o.optNullableString("bankAccountTitle"),
        bankIban = o.optNullableString("bankIban"),
        bankJazzCash = o.optNullableString("bankJazzCash"),
        termsAndConditions = o.optNullableString("termsAndConditions"),
        strn = o.optNullableString("strn"),
        currency = o.optNullableString("currency"),
        calendar = o.optNullableString("calendar"),
        trade = o.optNullableString("trade"),
        photoOnStatement = o.optBoolean("photoOnStatement", false),
        businessPhone = o.optNullableString("businessPhone"),
        tradeOtherName = o.optNullableString("tradeOtherName"),
        tradeOtherItems = o.optNullableString("tradeOtherItems"),
        businessNameUrdu = o.optNullableString("businessNameUrdu"),
        businessEmail = o.optNullableString("businessEmail"),
        country = o.optNullableString("country")
    )

    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

    /** A row's uid as the file carries it; a file from before format 12 has none, and the row is given one now. */
    private fun JSONObject.uid(): String = optNullableString("uid") ?: Uid.next()

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (isNull(key)) null else optLong(key)

    private fun JSONObject.optNullableDouble(key: String): Double? =
        if (isNull(key)) null else optDouble(key)

    // ---------- The counter (format 8) ----------

    private fun saleToJson(s: Sale) = JSONObject().apply {
        put("id", s.id); put("saleNumber", s.saleNumber)
        put("partyId", s.partyId ?: JSONObject.NULL); put("customerName", s.customerName ?: JSONObject.NULL)
        put("timestamp", s.timestamp); put("subtotal", s.subtotal); put("discountAmount", s.discountAmount)
        put("taxPercent", s.taxPercent ?: JSONObject.NULL); put("taxAmount", s.taxAmount); put("roundOff", s.roundOff)
        put("grandTotal", s.grandTotal); put("paidTotal", s.paidTotal); put("status", s.status)
        put("note", s.note ?: JSONObject.NULL); put("ledgerEntryId", s.ledgerEntryId ?: JSONObject.NULL)
        put("invoiceId", s.invoiceId ?: JSONObject.NULL); put("createdBy", s.createdBy ?: JSONObject.NULL)
        put("voidReason", s.voidReason ?: JSONObject.NULL); put("voidedAt", s.voidedAt ?: JSONObject.NULL)
        put("isDeleted", s.isDeleted); put("deletedAt", s.deletedAt ?: JSONObject.NULL)
        put("uid", s.uid ?: JSONObject.NULL)
        // Format 21 (schema 46): the held ticket it was rung up from.
        put("heldUid", s.heldUid ?: JSONObject.NULL)
    }

    private fun jsonToSale(o: JSONObject) = Sale(
        id = o.getLong("id"), saleNumber = o.optString("saleNumber", ""),
        partyId = o.optNullableLong("partyId"), customerName = o.optNullableString("customerName"),
        timestamp = o.getLong("timestamp"), subtotal = o.getDouble("subtotal"),
        discountAmount = o.optDouble("discountAmount", 0.0), taxPercent = o.optNullableDouble("taxPercent"),
        taxAmount = o.optDouble("taxAmount", 0.0), roundOff = o.optDouble("roundOff", 0.0),
        grandTotal = o.getDouble("grandTotal"), paidTotal = o.optDouble("paidTotal", 0.0),
        status = o.optString("status", SaleStatus.DONE), note = o.optNullableString("note"),
        ledgerEntryId = o.optNullableLong("ledgerEntryId"), invoiceId = o.optNullableLong("invoiceId"),
        createdBy = o.optNullableString("createdBy"), voidReason = o.optNullableString("voidReason"),
        voidedAt = o.optNullableLong("voidedAt"), isDeleted = o.optBoolean("isDeleted", false),
        deletedAt = o.optNullableLong("deletedAt"), uid = o.uid(),
        // Absent before format 21.
        heldUid = o.optNullableString("heldUid")?.takeIf { it.isNotBlank() }
    )

    // [secrets] false (a helper's copy, a Waris snapshot): the line's cost stays out, as the product's cost price does (m42).
    private fun saleItemToJson(i: SaleItem, secrets: Boolean = true) = JSONObject().apply {
        put("uid", i.uid ?: JSONObject.NULL)
        put("id", i.id); put("saleId", i.saleId); put("lineNo", i.lineNo)
        put("productId", i.productId ?: JSONObject.NULL); put("itemName", i.itemName)
        put("quantity", i.quantity); put("unit", i.unit ?: JSONObject.NULL); put("rate", i.rate)
        put("discount", i.discount); put("billItemId", i.billItemId ?: JSONObject.NULL)
        put("costRate", i.costRate?.takeIf { secrets } ?: JSONObject.NULL); put("isDeleted", i.isDeleted)
        put("taxRate", i.taxRate ?: JSONObject.NULL); put("taxAmount", i.taxAmount ?: JSONObject.NULL)
        put("taxIncluded", i.taxIncluded ?: JSONObject.NULL)
    }

    private fun jsonToSaleItem(o: JSONObject) = SaleItem(
        uid = o.uid(),
        id = o.getLong("id"), saleId = o.getLong("saleId"), lineNo = o.optInt("lineNo", 0),
        productId = o.optNullableLong("productId"), itemName = o.optString("itemName", ""),
        quantity = o.getDouble("quantity"), unit = o.optNullableString("unit"), rate = o.getDouble("rate"),
        discount = o.optDouble("discount", 0.0), billItemId = o.optNullableLong("billItemId"),
        costRate = o.optNullableDouble("costRate"), isDeleted = o.optBoolean("isDeleted", false),
        // Absent before format 12: no tax of its own.
        taxRate = o.optNullableDouble("taxRate"), taxAmount = o.optNullableDouble("taxAmount"),
        taxIncluded = if (o.isNull("taxIncluded")) null else o.optBoolean("taxIncluded")
    )

    private fun salePaymentToJson(p: SalePayment) = JSONObject().apply {
        put("uid", p.uid ?: JSONObject.NULL)
        put("id", p.id); put("saleId", p.saleId); put("method", p.method); put("amount", p.amount)
        put("reference", p.reference ?: JSONObject.NULL)
        put("ledgerEntryId", p.ledgerEntryId ?: JSONObject.NULL); put("cashEntryId", p.cashEntryId ?: JSONObject.NULL)
    }

    private fun jsonToSalePayment(o: JSONObject) = SalePayment(
        uid = o.uid(),
        id = o.getLong("id"), saleId = o.getLong("saleId"), method = o.optString("method", PaymentMethod.CASH),
        amount = o.getDouble("amount"), reference = o.optNullableString("reference"),
        ledgerEntryId = o.optNullableLong("ledgerEntryId"), cashEntryId = o.optNullableLong("cashEntryId")
    )

    private fun auditToJson(a: AuditLog) = JSONObject().apply {
        put("id", a.id); put("timestamp", a.timestamp); put("user", a.user ?: JSONObject.NULL)
        put("action", a.action); put("refTable", a.refTable ?: JSONObject.NULL)
        put("refId", a.refId ?: JSONObject.NULL); put("detail", a.detail ?: JSONObject.NULL)
        put("hash", a.hash ?: JSONObject.NULL)
    }

    private fun jsonToAudit(o: JSONObject) = AuditLog(
        id = o.getLong("id"), timestamp = o.getLong("timestamp"), user = o.optNullableString("user"),
        action = o.optString("action", ""), refTable = o.optNullableString("refTable"),
        refId = o.optNullableLong("refId"), detail = o.optNullableString("detail"),
        // Absent before format 12: written before the seal.
        hash = o.optNullableString("hash")
    )

    // ---------- Returns (format 9) ----------

    private fun returnToJson(r: SaleReturn) = JSONObject().apply {
        put("id", r.id); put("returnNumber", r.returnNumber); put("saleId", r.saleId ?: JSONObject.NULL)
        put("partyId", r.partyId ?: JSONObject.NULL); put("customerName", r.customerName ?: JSONObject.NULL)
        put("timestamp", r.timestamp); put("total", r.total); put("refundMethod", r.refundMethod); put("refundAmount", r.refundAmount)
        put("reason", r.reason); put("note", r.note ?: JSONObject.NULL); put("status", r.status)
        put("ledgerEntryId", r.ledgerEntryId ?: JSONObject.NULL); put("refundEntryId", r.refundEntryId ?: JSONObject.NULL)
        put("refundCashId", r.refundCashId ?: JSONObject.NULL); put("createdBy", r.createdBy ?: JSONObject.NULL)
        put("voidReason", r.voidReason ?: JSONObject.NULL); put("voidedAt", r.voidedAt ?: JSONObject.NULL)
        put("isDeleted", r.isDeleted); put("deletedAt", r.deletedAt ?: JSONObject.NULL)
        put("uid", r.uid ?: JSONObject.NULL)
    }

    private fun jsonToReturn(o: JSONObject) = SaleReturn(
        id = o.getLong("id"), returnNumber = o.optString("returnNumber", ""), saleId = o.optNullableLong("saleId"),
        partyId = o.optNullableLong("partyId"), customerName = o.optNullableString("customerName"),
        timestamp = o.getLong("timestamp"), total = o.getDouble("total"), refundMethod = o.optString("refundMethod", SaleReturn.ACCOUNT),
        refundAmount = o.optDouble("refundAmount", 0.0), reason = o.optString("reason", ""), note = o.optNullableString("note"),
        status = o.optString("status", SaleStatus.DONE), ledgerEntryId = o.optNullableLong("ledgerEntryId"),
        refundEntryId = o.optNullableLong("refundEntryId"), refundCashId = o.optNullableLong("refundCashId"),
        createdBy = o.optNullableString("createdBy"), voidReason = o.optNullableString("voidReason"),
        voidedAt = o.optNullableLong("voidedAt"), isDeleted = o.optBoolean("isDeleted", false), deletedAt = o.optNullableLong("deletedAt"),
        uid = o.uid()
    )

    private fun returnItemToJson(i: SaleReturnItem) = JSONObject().apply {
        put("uid", i.uid ?: JSONObject.NULL)
        put("id", i.id); put("returnId", i.returnId); put("lineNo", i.lineNo); put("saleItemId", i.saleItemId ?: JSONObject.NULL)
        put("productId", i.productId ?: JSONObject.NULL); put("itemName", i.itemName); put("quantity", i.quantity)
        put("unit", i.unit ?: JSONObject.NULL); put("rate", i.rate); put("billItemId", i.billItemId ?: JSONObject.NULL); put("isDeleted", i.isDeleted)
    }

    private fun jsonToReturnItem(o: JSONObject) = SaleReturnItem(
        uid = o.uid(),
        id = o.getLong("id"), returnId = o.getLong("returnId"), lineNo = o.optInt("lineNo", 0), saleItemId = o.optNullableLong("saleItemId"),
        productId = o.optNullableLong("productId"), itemName = o.optString("itemName", ""), quantity = o.getDouble("quantity"),
        unit = o.optNullableString("unit"), rate = o.getDouble("rate"), billItemId = o.optNullableLong("billItemId"), isDeleted = o.optBoolean("isDeleted", false)
    )

    // ---------- Stock adjustments and purchase orders (format 11) ----------

    private fun adjustmentToJson(a: StockAdjustment) = JSONObject().apply {
        put("id", a.id); put("productId", a.productId); put("timestamp", a.timestamp); put("quantity", a.quantity)
        put("unit", a.unit ?: JSONObject.NULL); put("reason", a.reason); put("note", a.note ?: JSONObject.NULL)
        put("bookQty", a.bookQty ?: JSONObject.NULL); put("countedQty", a.countedQty ?: JSONObject.NULL)
        put("createdBy", a.createdBy ?: JSONObject.NULL); put("isDeleted", a.isDeleted)
        put("uid", a.uid ?: JSONObject.NULL); put("billItemId", a.billItemId ?: JSONObject.NULL)
    }

    private fun jsonToAdjustment(o: JSONObject) = StockAdjustment(
        id = o.getLong("id"), productId = o.getLong("productId"), timestamp = o.getLong("timestamp"), quantity = o.getDouble("quantity"),
        unit = o.optNullableString("unit"), reason = o.optString("reason", AdjustReason.OTHER), note = o.optNullableString("note"),
        bookQty = o.optNullableDouble("bookQty"), countedQty = o.optNullableDouble("countedQty"),
        createdBy = o.optNullableString("createdBy"), isDeleted = o.optBoolean("isDeleted", false), uid = o.uid(),
        billItemId = o.optNullableLong("billItemId")
    )

    private fun orderToJson(p: PurchaseOrder) = JSONObject().apply {
        put("id", p.id); put("orderNumber", p.orderNumber); put("partyId", p.partyId); put("timestamp", p.timestamp)
        put("status", p.status); put("note", p.note ?: JSONObject.NULL); put("billId", p.billId ?: JSONObject.NULL)
        put("createdBy", p.createdBy ?: JSONObject.NULL); put("isDeleted", p.isDeleted)
        put("uid", p.uid ?: JSONObject.NULL)
    }

    private fun jsonToOrder(o: JSONObject) = PurchaseOrder(
        id = o.getLong("id"), orderNumber = o.optString("orderNumber", ""), partyId = o.getLong("partyId"), timestamp = o.getLong("timestamp"),
        status = o.optString("status", OrderStatus.DRAFT), note = o.optNullableString("note"), billId = o.optNullableLong("billId"),
        createdBy = o.optNullableString("createdBy"), isDeleted = o.optBoolean("isDeleted", false), uid = o.uid()
    )

    private fun orderItemToJson(i: PurchaseOrderItem) = JSONObject().apply {
        put("uid", i.uid ?: JSONObject.NULL)
        put("id", i.id); put("orderId", i.orderId); put("lineNo", i.lineNo); put("productId", i.productId ?: JSONObject.NULL)
        put("itemName", i.itemName); put("quantity", i.quantity); put("unit", i.unit ?: JSONObject.NULL)
        put("rate", i.rate ?: JSONObject.NULL); put("isDeleted", i.isDeleted)
    }

    private fun jsonToOrderItem(o: JSONObject) = PurchaseOrderItem(
        uid = o.uid(),
        id = o.getLong("id"), orderId = o.getLong("orderId"), lineNo = o.optInt("lineNo", 0), productId = o.optNullableLong("productId"),
        itemName = o.optString("itemName", ""), quantity = o.getDouble("quantity"), unit = o.optNullableString("unit"),
        rate = o.optNullableDouble("rate"), isDeleted = o.optBoolean("isDeleted", false)
    )

    // ---------- Loyalty and shifts (format 12) ----------

    private fun loyaltyToJson(l: LoyaltyEntry) = JSONObject().apply {
        put("id", l.id); put("partyId", l.partyId); put("saleId", l.saleId ?: JSONObject.NULL); put("points", l.points)
        put("timestamp", l.timestamp); put("note", l.note ?: JSONObject.NULL); put("isDeleted", l.isDeleted)
        put("uid", l.uid ?: JSONObject.NULL)
    }

    private fun jsonToLoyalty(o: JSONObject) = LoyaltyEntry(
        id = o.getLong("id"), partyId = o.getLong("partyId"), saleId = o.optNullableLong("saleId"), points = o.getDouble("points"),
        timestamp = o.getLong("timestamp"), note = o.optNullableString("note"), isDeleted = o.optBoolean("isDeleted", false), uid = o.uid()
    )

    private fun shiftToJson(s: Shift) = JSONObject().apply {
        put("id", s.id); put("user", s.user ?: JSONObject.NULL); put("device", s.device ?: JSONObject.NULL)
        put("openedAt", s.openedAt); put("openingFloat", s.openingFloat); put("closedAt", s.closedAt ?: JSONObject.NULL)
        put("counted", s.counted ?: JSONObject.NULL); put("expected", s.expected ?: JSONObject.NULL)
        put("note", s.note ?: JSONObject.NULL); put("uid", s.uid ?: JSONObject.NULL)
    }

    // ---------- The book's seals (format 19) ----------

    private fun sealToJson(s: BookSeal) = JSONObject().apply {
        put("id", s.id); put("sealedAt", s.sealedAt); put("fromId", s.fromId); put("toId", s.toId)
        put("leafCount", s.leafCount); put("leaves", s.leaves?.let { B64.encode(it) } ?: JSONObject.NULL)
        put("auditHead", s.auditHead ?: JSONObject.NULL); put("prevDigest", s.prevDigest ?: JSONObject.NULL)
        put("digest", s.digest); put("tokens", s.tokens ?: JSONObject.NULL)
    }

    private fun jsonToSeal(o: JSONObject) = BookSeal(
        id = o.getLong("id"), sealedAt = o.getLong("sealedAt"), fromId = o.getLong("fromId"), toId = o.getLong("toId"),
        leafCount = o.optInt("leafCount", 0), leaves = o.optNullableString("leaves")?.let { B64.decode(it) },
        auditHead = o.optNullableString("auditHead"), prevDigest = o.optNullableString("prevDigest"),
        digest = o.getString("digest"), tokens = o.optNullableString("tokens")
    )

    private fun jsonToShift(o: JSONObject) = Shift(
        id = o.getLong("id"), user = o.optNullableString("user"), device = o.optNullableString("device"),
        openedAt = o.getLong("openedAt"), openingFloat = o.optDouble("openingFloat", 0.0), closedAt = o.optNullableLong("closedAt"),
        counted = o.optNullableDouble("counted"), expected = o.optNullableDouble("expected"),
        note = o.optNullableString("note"), uid = o.uid()
    )
}
