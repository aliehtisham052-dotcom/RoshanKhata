package com.innovation313.roshankhata.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full backup and restore.
 *
 * Plain JSON, deliberately. Not an opaque binary blob and not the raw SQLite
 * file: a shopkeeper's entire book of debts should be readable and rescuable
 * with nothing more than a text editor, even years from now, even if this app
 * no longer exists. Data the owner cannot recover without our cooperation is
 * not really theirs.
 *
 * The file carries a schema version so a future release can migrate an old
 * backup rather than reject it.
 */
object Backup {

    /**
     * Raised to 4 when products arrived, and to 5 when invoices and the
     * Business Profile text joined the file.
     *
     * The bump matters in one direction only: a file written today, opened by
     * an older release, is refused with "update the app first" rather than
     * imported without its invoices. Silently dropping a table the old code
     * cannot hold would look like a successful restore and lose data.
     * Reading OLD files is unaffected — every array is read optionally, so a
     * version-4 file (no invoices, no businessProfile) still restores cleanly.
     */
    const val FORMAT_VERSION = 5

    private val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.ENGLISH)

    /**
     * Named .txt, not .json.
     *
     * WhatsApp — which is how a Pakistani shopkeeper actually moves a file
     * between phones — refuses to send an extension it does not recognise. The
     * owner would watch the send appear to work and then find nothing at the
     * other end.
     *
     * The contents are unchanged: it is still JSON, and the app still reads it
     * back exactly the same way. Only the label differs, because the label is
     * what was standing between the owner and a backup that actually travelled.
     */
    fun suggestedFileName(): String =
        "RoshanKhata_Backup_${stamp.format(Date())}.txt"

    // ---------- Export ----------

    // Takes Context now, because the Business Profile (shop name, bank details,
    // STRN, terms) lives in SharedPreferences, not in Room — so it cannot be
    // read through the DAO like every other table. Its images (QR, signature,
    // stamp) are deliberately NOT here: those are the separate opt-in image
    // backup, kept out of the routine text file so it stays small.
    suspend fun export(context: Context, dao: KhataDao): String {
        val root = JSONObject()
        root.put("format", "RoshanKhata")
        root.put("version", FORMAT_VERSION)
        root.put("exportedAt", System.currentTimeMillis())

        // Everything, soft-deleted rows included — the Recycle Bin is the
        // owner's data too, and a backup that quietly dropped it would be
        // throwing away something they can still get back.
        root.put("parties", JSONArray().apply {
            dao.allPartiesForBackup().forEach { put(partyToJson(it)) }
        })
        root.put("entries", JSONArray().apply {
            dao.allEntriesForBackup().forEach { put(entryToJson(it)) }
        })
        root.put("cheques", JSONArray().apply {
            dao.allChequesForBackup().forEach { put(chequeToJson(it)) }
        })
        root.put("cashbook", JSONArray().apply {
            dao.allCashForBackup().forEach { put(cashToJson(it)) }
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
            dao.allProductsForBackup().forEach { put(productToJson(it)) }
        })
        root.put("invoices", JSONArray().apply {
            dao.allInvoicesForBackup().forEach { put(invoiceToJson(it)) }
        })
        root.put("invoiceItems", JSONArray().apply {
            dao.allInvoiceItemsForBackup().forEach { put(invoiceItemToJson(it)) }
        })

        // The Business Profile — TEXT ONLY. The three image flags
        // (QR/signature/stamp saved) are deliberately excluded: restoring a
        // "QR is saved = true" onto a phone whose image file does not exist
        // (because the image backup is the separate opt-in that has not run)
        // would make a statement claim a QR it cannot draw. Those flags come
        // back with the images, in Part B, or they stay false and honest.
        root.put("businessProfile", businessProfileToJson(context))

        return root.toString(2)
    }

    /**
     * Write the backup where the owner can actually find it again: Downloads.
     *
     * It used to go to the cache directory, which was a mistake with real
     * consequences. Android empties the cache whenever it feels the need for
     * space — so the one file standing between a shopkeeper and the loss of
     * their entire ledger could vanish without anyone touching it. A backup
     * that quietly disappears is worse than no backup, because the owner
     * believes they are covered.
     *
     * Downloads survives. It is visible in every file manager, WhatsApp,
     * Drive, and the phone's own Files app. And on Android 10+ this needs no
     * storage permission at all — MediaStore hands the app a place to write
     * without handing it the run of the user's storage.
     *
     * @return a human-readable location to show the owner, or null on failure.
     */
    fun saveToDownloads(context: Context, json: String): String? {
        val name = suggestedFileName()

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    // Declared as plain text, not application/json. Android and
                    // most apps treat an unknown MIME type as a file to be
                    // hidden — which is exactly why the owner could not see
                    // their own backup afterwards. It IS text; saying so makes
                    // it visible everywhere.
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values
                ) ?: return null

                resolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray())
                } ?: return null

                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)

                "Downloads/$name"
            } else {
                // Pre-Android 10: write to the public Downloads folder directly.
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                )
                dir.mkdirs()
                val file = File(dir, name)
                file.writeText(json)
                file.absolutePath
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * A second copy inside the app's own storage.
     *
     * Kept so the owner is never left with nothing: even if the Downloads copy
     * is deleted, moved, or lost with the phone's file manager, the app can
     * still offer its own most recent backup. This is filesDir, not cacheDir —
     * the system does not clear it behind the owner's back.
     */
    fun writeInternalCopy(context: Context, json: String): File? {
        return try {
            val dir = File(context.filesDir, "backups").apply { mkdirs() }
            val file = File(dir, suggestedFileName())
            file.writeText(json)

            // Keep the last few, then stop. An unbounded pile of backups would
            // quietly eat the phone's storage.
            dir.listFiles()
                ?.sortedByDescending { it.lastModified() }
                ?.drop(5)
                ?.forEach { it.delete() }

            file
        } catch (e: Exception) {
            null
        }
    }

    /** The app's own saved backups, newest first. */
    fun internalBackups(context: Context): List<File> {
        val dir = File(context.filesDir, "backups")
        if (!dir.exists()) return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    /** Shareable copy, for sending to Drive or another phone. */
    fun writeToCache(context: Context, json: String): File? {
        return try {
            val dir = File(context.cacheDir, "backups").apply { mkdirs() }
            val file = File(dir, suggestedFileName())
            file.writeText(json)
            file
        } catch (e: Exception) {
            null
        }
    }

    /** Read a backup the app saved itself. */
    fun parseFile(file: File): Pair<ImportResult, ParsedBackup?> {
        return try {
            parseText(file.readText())
        } catch (e: Exception) {
            ImportResult.Failed("The file could not be read.") to null
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

        data class Failed(val reason: String) : ImportResult()
    }

    /**
     * Reads and validates a backup WITHOUT touching the database.
     *
     * The parse happens first, in full, so a corrupt or unrelated file is
     * caught before anything is wiped. Wiping first and discovering the file
     * was rubbish afterwards would destroy the owner's books to import nothing.
     */
    fun parse(context: Context, uri: Uri): Pair<ImportResult, ParsedBackup?> {
        val text = try {
            context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
        } catch (e: Exception) {
            null
        } ?: return ImportResult.Failed("Could not read the file.") to null

        return parseText(text)
    }

    /**
     * Validate a backup's contents. Shared by every route in — a file picked
     * from storage and one restored from the app's own copy get identical
     * checks, so neither can slip past on a technicality the other would catch.
     */
    fun parseText(text: String): Pair<ImportResult, ParsedBackup?> {
        return try {
            val root = JSONObject(text)

            if (root.optString("format") != "RoshanKhata") {
                return ImportResult.Failed(
                    "This is not a Roshan Khata backup file."
                ) to null
            }

            val version = root.optInt("version", -1)
            if (version > FORMAT_VERSION) {
                return ImportResult.Failed(
                    "This backup was made by a newer version of Roshan Khata. " +
                        "Please update the app first."
                ) to null
            }

            val parties = root.optJSONArray("parties")?.let { arr ->
                (0 until arr.length()).map { jsonToParty(arr.getJSONObject(it)) }
            } ?: emptyList()

            val entries = root.optJSONArray("entries")?.let { arr ->
                (0 until arr.length()).map { jsonToEntry(arr.getJSONObject(it)) }
            } ?: emptyList()

            val cheques = root.optJSONArray("cheques")?.let { arr ->
                (0 until arr.length()).map { jsonToCheque(arr.getJSONObject(it)) }
            } ?: emptyList()

            val cash = root.optJSONArray("cashbook")?.let { arr ->
                (0 until arr.length()).map { jsonToCash(arr.getJSONObject(it)) }
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
            val orphans = entries.count { it.productId != null && it.productId !in productIds } +
                entries.count { it.billItemId != null && it.billItemId !in billItemIds } +
                billItems.count { it.productId != null && it.productId !in productIds } +
                entries.count { it.partyId !in partyIds } +
                cheques.count { it.partyId !in partyIds } +
                plans.count { it.partyId !in partyIds } +
                installments.count { it.planId !in planIds } +
                bills.count { it.partyId !in partyIds } +
                billItems.count { it.billId !in billIds } +
                invoiceItems.count { it.invoiceId !in invoiceIds }

            if (orphans > 0) {
                return ImportResult.Failed(
                    "This backup is incomplete — $orphans entries refer to " +
                        "customers that are missing from the file."
                ) to null
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
                bills, billItems, products, invoices, invoiceItems, businessProfile
            )
        } catch (e: Exception) {
            ImportResult.Failed("The file could not be read as a backup.") to null
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
        val businessProfile: BusinessProfileData? = null
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
        val photoOnStatement: Boolean
    )

    /**
     * Replaces everything. Only called after the user has confirmed.
     *
     * Takes Context now for the Business Profile, which lives in
     * SharedPreferences and so cannot ride inside the Room transaction. The
     * ledger is restored first, atomically; the profile is written after, and
     * only when the file actually carried one. If a file has no profile (an
     * old backup, or one taken before the owner set any details), the current
     * profile is LEFT ALONE rather than blanked — restoring "nothing" over a
     * shop's real name would be a silent loss, not a restore.
     */
    suspend fun restore(context: Context, dao: KhataDao, data: ParsedBackup) {
        // One transaction, all-or-nothing. If any step fails, the whole thing
        // rolls back and the existing ledger is left untouched — rather than the
        // old behaviour, where a failure partway through wiped data and restored
        // nothing, losing the customer the owner was trying to bring back.
        dao.restoreAll(
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
            invoiceItems = data.invoiceItems
        )

        data.businessProfile?.let { restoreBusinessProfile(context, it) }
    }

    // ---------- Mapping ----------

    private fun partyToJson(p: Party) = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("phone", p.phone ?: JSONObject.NULL)
        put("isCustomer", p.isCustomer)
        put("photoPath", p.photoPath ?: JSONObject.NULL)
        put("creditLimit", p.creditLimit ?: JSONObject.NULL)
        put("createdAt", p.createdAt)
        put("isDeleted", p.isDeleted)
        put("deletedAt", p.deletedAt ?: JSONObject.NULL)
    }

    private fun jsonToParty(o: JSONObject) = Party(
        id = o.getLong("id"),
        name = o.getString("name"),
        phone = o.optNullableString("phone"),
        isCustomer = o.optBoolean("isCustomer", true),
        photoPath = o.optNullableString("photoPath"),
        creditLimit = o.optNullableDouble("creditLimit"),
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        isDeleted = o.optBoolean("isDeleted", false),
        deletedAt = o.optNullableLong("deletedAt")
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
        put("isDeleted", e.isDeleted)
        put("deletedAt", e.deletedAt ?: JSONObject.NULL)
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
        isDeleted = o.optBoolean("isDeleted", false),
        deletedAt = o.optNullableLong("deletedAt")
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
        deletedAt = o.optNullableLong("deletedAt")
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
    }

    private fun jsonToCash(o: JSONObject) = CashEntry(
        id = o.getLong("id"),
        amount = o.getDouble("amount"),
        isIncome = o.getBoolean("isIncome"),
        category = o.optString("category", "Other"),
        note = o.optNullableString("note"),
        timestamp = o.optLong("timestamp", System.currentTimeMillis()),
        isDeleted = o.optBoolean("isDeleted", false),
        deletedAt = o.optNullableLong("deletedAt")
    )


    private fun planToJson(p: PaymentPlan) = JSONObject().apply {
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
        put("id", i.id)
        put("planId", i.planId)
        put("amount", i.amount)
        put("ledgerEntryId", i.ledgerEntryId)
        put("paidAt", i.paidAt)
        put("note", i.note ?: JSONObject.NULL)
        put("isDeleted", i.isDeleted)
    }

    private fun jsonToInstallment(o: JSONObject) = Installment(
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
        deletedAt = o.optNullableLong("deletedAt")
    )

    private fun billItemToJson(i: BillItem) = JSONObject().apply {
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
    private fun productToJson(p: Product) = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("nameKey", p.nameKey)
        put("normalisedName", p.normalisedName)
        put("category", p.category ?: JSONObject.NULL)
        put("defaultUnit", p.defaultUnit ?: JSONObject.NULL)
        put("note", p.note ?: JSONObject.NULL)
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
            id = o.getLong("id"),
            name = name,
            nameKey = ProductName.key(name),
            normalisedName = ProductName.normalised(name),
            category = o.optNullableString("category"),
            defaultUnit = o.optNullableString("defaultUnit"),
            note = o.optNullableString("note"),
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
        put("additionalChargeLabel", i.additionalChargeLabel ?: JSONObject.NULL)
        put("additionalChargeAmount", i.additionalChargeAmount ?: JSONObject.NULL)
        put("receivedAmount", i.receivedAmount ?: JSONObject.NULL)
        put("note", i.note ?: JSONObject.NULL)
        put("templateId", i.templateId)
        put("createdAt", i.createdAt)
        put("isDeleted", i.isDeleted)
        put("deletedAt", i.deletedAt ?: JSONObject.NULL)
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
        additionalChargeLabel = o.optNullableString("additionalChargeLabel"),
        additionalChargeAmount = o.optNullableDouble("additionalChargeAmount"),
        receivedAmount = o.optNullableDouble("receivedAmount"),
        note = o.optNullableString("note"),
        templateId = o.optInt("templateId", 1),
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        isDeleted = o.optBoolean("isDeleted", false),
        deletedAt = o.optNullableLong("deletedAt")
    )

    private fun invoiceItemToJson(i: InvoiceItem) = JSONObject().apply {
        put("id", i.id)
        put("invoiceId", i.invoiceId)
        put("itemName", i.itemName)
        put("quantity", i.quantity)
        put("unit", i.unit ?: JSONObject.NULL)
        put("rate", i.rate)
        put("isDeleted", i.isDeleted)
    }

    private fun jsonToInvoiceItem(o: JSONObject) = InvoiceItem(
        id = o.getLong("id"),
        invoiceId = o.getLong("invoiceId"),
        itemName = o.optString("itemName", ""),
        quantity = o.optDouble("quantity", 0.0),
        unit = o.optNullableString("unit"),
        rate = o.optDouble("rate", 0.0),
        isDeleted = o.optBoolean("isDeleted", false)
    )

    // ---------- Business Profile (SharedPreferences, not Room) ----------

    private fun businessProfileToJson(context: Context) = JSONObject().apply {
        put("businessName", BusinessProfile.businessName(context) ?: JSONObject.NULL)
        put("businessAddress", BusinessProfile.businessAddress(context) ?: JSONObject.NULL)
        put("bankName", BusinessProfile.bankName(context) ?: JSONObject.NULL)
        put("bankAccountTitle", BusinessProfile.bankAccountTitle(context) ?: JSONObject.NULL)
        put("bankIban", BusinessProfile.bankIban(context) ?: JSONObject.NULL)
        put("bankJazzCash", BusinessProfile.bankJazzCash(context) ?: JSONObject.NULL)
        put("termsAndConditions", BusinessProfile.termsAndConditions(context) ?: JSONObject.NULL)
        put("strn", BusinessProfile.strn(context) ?: JSONObject.NULL)
        put("photoOnStatement", BusinessProfile.photoOnStatement(context))
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
        photoOnStatement = o.optBoolean("photoOnStatement", false)
    )

    // Written through the existing setters so trimming/normalisation stays in
    // one place. Only the text fields — never the image flags. A null field is
    // passed straight to its setter, which stores an empty string (its own
    // "unset"); this is a full REPLACE of the text profile, matching how the
    // ledger restore replaces rather than merges.
    private fun restoreBusinessProfile(context: Context, p: BusinessProfileData) {
        BusinessProfile.setBusinessName(context, p.businessName)
        BusinessProfile.setBusinessAddress(context, p.businessAddress)
        BusinessProfile.setBankName(context, p.bankName)
        BusinessProfile.setBankAccountTitle(context, p.bankAccountTitle)
        BusinessProfile.setBankIban(context, p.bankIban)
        BusinessProfile.setBankJazzCash(context, p.bankJazzCash)
        BusinessProfile.setTermsAndConditions(context, p.termsAndConditions)
        BusinessProfile.setStrn(context, p.strn)
        BusinessProfile.setPhotoOnStatement(context, p.photoOnStatement)
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (isNull(key)) null else optLong(key)

    private fun JSONObject.optNullableDouble(key: String): Double? =
        if (isNull(key)) null else optDouble(key)
}
