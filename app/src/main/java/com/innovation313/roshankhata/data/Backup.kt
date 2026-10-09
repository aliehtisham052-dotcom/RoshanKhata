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
     * Raised to 4 when products arrived, to 5 when invoices and the Business
     * Profile text joined, to 6 when the "not a duplicate" decisions did, and
     * to 7 when goods moved into their own lines (entryItems). A version-6-or-
     * older file has no lines; restore builds them from its entries by the
     * same rule the database migration uses (EntryItem.fromLegacy), so the
     * restored book matches one that was migrated on the phone.
     *
     * The bump matters in one direction only: a file written today, opened by
     * an older release, is refused with "update the app first" rather than
     * imported without its newest data. Silently dropping a table the old code
     * cannot hold would look like a successful restore and lose data.
     * Reading OLD files is unaffected — every array is read optionally, so a
     * version-4 or -5 file still restores cleanly.
     */
    const val FORMAT_VERSION = 7

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
        val w = java.io.StringWriter()
        exportTo(context, dao, w)
        return w.toString()
    }

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
    suspend fun exportTo(context: Context, dao: KhataDao, out: java.io.Writer) {
        val small = exportSmall(context, dao).toString(2)
        // The small document ends in "\n}" — drop that brace and add the
        // three streamed tables before closing the object ourselves.
        out.write(small.trimEnd().removeSuffix("}").trimEnd())
        suspend fun <T> table(name: String, page: suspend (Long, Int) -> List<T>, id: (T) -> Long, json: (T) -> JSONObject) {
            out.write(",\n  \"$name\": [")
            var after = 0L
            var first = true
            while (true) {
                val rows = page(after, PAGE)
                if (rows.isEmpty()) break
                for (r in rows) {
                    out.write(if (first) "\n    " else ",\n    ")
                    first = false
                    out.write(json(r).toString())
                }
                after = id(rows.last())
                if (rows.size < PAGE) break
            }
            out.write(if (first) "]" else "\n  ]")
        }
        table("parties", dao::partiesPageForBackup, { it.id }, ::partyToJson)
        table("entries", dao::entriesPageForBackup, { it.id }, ::entryToJson)
        table("entryItems", dao::entryItemsPageForBackup, { it.id }, ::entryItemToJson)
        out.write("\n}")
        out.flush()
    }

    /** The backup to a file, buffered, for the paths that hand a file on (Downloads, Drive, the helper's copy). */
    suspend fun exportToFile(context: Context, dao: KhataDao, file: File): File {
        file.parentFile?.mkdirs()
        java.io.BufferedWriter(java.io.OutputStreamWriter(java.io.FileOutputStream(file), Charsets.UTF_8), 64 * 1024)
            .use { exportTo(context, dao, it) }
        return file
    }

    private suspend fun exportSmall(context: Context, dao: KhataDao): JSONObject {
        val root = JSONObject()
        root.put("format", "RoshanKhata")
        root.put("version", FORMAT_VERSION)
        root.put("exportedAt", System.currentTimeMillis())
        // Whose book this is. Additive — an older app ignores unknown keys,
        // and a backup without it (every backup made before multi-business)
        // simply has no name to check. Restore uses it for one thing only:
        // warning the owner before this book lands in a differently-named
        // shop. It never blocks — moving a book on purpose stays possible.
        BusinessProfile.businessName(context)?.let { root.put("businessName", it) }

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
                })
            }
        })
        root.put("staffAttendance", JSONArray().apply {
            dao.allAttendanceForBackup().forEach { m ->
                put(JSONObject().apply { put("id", m.id); put("staffId", m.staffId); put("day", m.day); put("status", m.status) })
            }
        })
        root.put("staffPayments", JSONArray().apply {
            dao.allStaffPaymentsForBackup().forEach { sp ->
                put(JSONObject().apply {
                    put("id", sp.id); put("staffId", sp.staffId); put("amount", sp.amount); put("kind", sp.kind)
                    put("timestamp", sp.timestamp); put("note", sp.note ?: JSONObject.NULL); put("isDeleted", sp.isDeleted)
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
                })
            }
        })
        root.put("dayCloses", JSONArray().apply {
            dao.allDayClosesForBackup().forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id); put("day", c.day); put("opening", c.opening)
                    put("cashIn", c.cashIn); put("cashOut", c.cashOut); put("counted", c.counted)
                    put("note", c.note ?: JSONObject.NULL); put("closedAt", c.closedAt)
                })
            }
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
        root.put("dismissedDuplicates", JSONArray().apply {
            dao.allDismissedDuplicatesForBackup().forEach { put(dismissedDuplicateToJson(it)) }
        })

        // The Business Profile — TEXT ONLY. The three image flags
        // (QR/signature/stamp saved) are deliberately excluded: restoring a
        // "QR is saved = true" onto a phone whose image file does not exist
        // (because the image backup is the separate opt-in that has not run)
        // would make a statement claim a QR it cannot draw. Those flags come
        // back with the images, in Part B, or they stay false and honest.
        root.put("businessProfile", businessProfileToJson(context))

        return root
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
    /** The streamed backup's cache file (P6): the one copy every other path copies from. */
    suspend fun exportToCache(context: Context, dao: KhataDao): File {
        val dir = File(context.cacheDir, "backups").apply { mkdirs() }
        return exportToFile(context, dao, File(dir, suggestedFileName()))
    }

    /** [saveToDownloads], from a file — streamed, never held as one string. */
    fun saveToDownloads(context: Context, source: File): String? {
        val name = suggestedFileName()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
                resolver.openOutputStream(uri)?.use { out ->
                    source.inputStream().buffered().use { it.copyTo(out) }
                } ?: return null
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                "Downloads/$name"
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                val file = File(dir, name)
                source.copyTo(file, overwrite = true)
                file.absolutePath
            }
        } catch (e: Exception) {
            null
        }
    }

    /** [writeInternalCopy], from a file. */
    fun writeInternalCopy(context: Context, source: File): File? {
        return try {
            val dir = File(context.filesDir, "backups").apply { mkdirs() }
            val file = File(dir, suggestedFileName())
            source.copyTo(file, overwrite = true)
            dir.listFiles()
                ?.sortedByDescending { it.lastModified() }
                ?.drop(5)
                ?.forEach { it.delete() }
            file
        } catch (e: Exception) {
            null
        }
    }

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
            file.inputStream().buffered(64 * 1024).use { parseStream(it) }
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
         * here reached the owner inside an Urdu dialog. [reason] words it in
         * the app's language where the dialog is built; [detail] is the
         * exception's own text, kept for diagnosis.
         */
        data class Failed(val why: Why, val count: Int = 0, val detail: String? = null) : ImportResult() {
            fun reason(context: android.content.Context): String {
                val text = when (why) {
                    Why.UNREADABLE -> context.getString(com.innovation313.roshankhata.R.string.backup_err_unreadable)
                    Why.NOT_OURS -> context.getString(com.innovation313.roshankhata.R.string.backup_err_not_ours)
                    Why.TOO_NEW -> context.getString(com.innovation313.roshankhata.R.string.backup_err_too_new)
                    Why.INCOMPLETE -> Digits.string(context.resources, com.innovation313.roshankhata.R.string.backup_err_incomplete, count)
                }
                return if (detail.isNullOrBlank()) text else "$text\n($detail)"
            }
        }

        enum class Why { UNREADABLE, NOT_OURS, TOO_NEW, INCOMPLETE }
    }

    /**
     * Reads and validates a backup WITHOUT touching the database.
     *
     * The parse happens first, in full, so a corrupt or unrelated file is
     * caught before anything is wiped. Wiping first and discovering the file
     * was rubbish afterwards would destroy the owner's books to import nothing.
     */
    fun parse(context: Context, uri: Uri): Pair<ImportResult, ParsedBackup?> {
        // Streamed straight off the Uri (P7, 7 Oct): a backup the size of a
        // wholesaler's book is never held as one string.
        return try {
            context.contentResolver.openInputStream(uri)
                ?.buffered(64 * 1024)
                ?.use { parseStream(it) }
        } catch (e: Exception) {
            null
        } ?: (ImportResult.Failed(ImportResult.Why.UNREADABLE) to null)
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
    fun parseStream(input: java.io.InputStream): Pair<ImportResult, ParsedBackup?> {
        return try {
            val rest = JSONObject()
            val parties = ArrayList<Party>()
            val entries = ArrayList<LedgerEntry>()
            val entryItems = ArrayList<EntryItem>()
            var hadEntryItems = false
            android.util.JsonReader(java.io.InputStreamReader(input, Charsets.UTF_8)).use { r ->
                r.isLenient = true
                r.beginObject()
                while (r.hasNext()) {
                    when (val key = r.nextName()) {
                        "parties" -> readRows(r) { parties += jsonToParty(it) }
                        "entries" -> readRows(r) { entries += jsonToEntry(it) }
                        "entryItems" -> { hadEntryItems = true; readRows(r) { entryItems += jsonToEntryItem(it) } }
                        else -> rest.put(key, readValue(r))
                    }
                }
                r.endObject()
            }
            parseRoot(rest, parties, entries, if (hadEntryItems) entryItems else null)
        } catch (e: Exception) {
            ImportResult.Failed(ImportResult.Why.UNREADABLE, detail = e.message) to null
        }
    }

    /** An array of objects, each handed to [row] as it is read; a null array is nothing. */
    private fun readRows(r: android.util.JsonReader, row: (JSONObject) -> Unit) {
        if (r.peek() == android.util.JsonToken.NULL) { r.nextNull(); return }
        r.beginArray()
        while (r.hasNext()) row(readValue(r) as JSONObject)
        r.endArray()
    }

    /** One JSON value off the reader as the org.json type the old parser expects. */
    private fun readValue(r: android.util.JsonReader): Any = when (r.peek()) {
        android.util.JsonToken.BEGIN_OBJECT -> {
            val o = JSONObject()
            r.beginObject()
            while (r.hasNext()) o.put(r.nextName(), readValue(r))
            r.endObject()
            o
        }
        android.util.JsonToken.BEGIN_ARRAY -> {
            val a = JSONArray()
            r.beginArray()
            while (r.hasNext()) a.put(readValue(r))
            r.endArray()
            a
        }
        android.util.JsonToken.STRING -> r.nextString()
        android.util.JsonToken.NUMBER -> {
            val n = r.nextString()
            if (n.any { it == '.' || it == 'e' || it == 'E' }) n.toDouble() else (n.toLongOrNull() ?: n.toDouble())
        }
        android.util.JsonToken.BOOLEAN -> r.nextBoolean()
        android.util.JsonToken.NULL -> { r.nextNull(); JSONObject.NULL }
        else -> throw IllegalStateException("unexpected token ${r.peek()}")
    }

    /** The old parser, given the three big tables already read. [entryItems] null = the key was absent (a backup from before goods lines). */
    private fun parseRoot(
        root: JSONObject,
        parties: List<Party>,
        entries: List<LedgerEntry>,
        entryItems: List<EntryItem>?
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

            val cash = root.optJSONArray("cashbook")?.let { arr ->
                (0 until arr.length()).map { jsonToCash(arr.getJSONObject(it)) }
            } ?: emptyList()

            // Absent before v29: no staff.
            val staff = root.optJSONArray("staff")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    Staff(id = o.getLong("id"), name = o.getString("name"), phone = o.optNullableString("phone"),
                        monthlySalary = o.getDouble("monthlySalary"), isActive = o.optBoolean("isActive", true),
                        createdAt = o.optLong("createdAt", 0L))
                }
            } ?: emptyList()
            val attendance = root.optJSONArray("staffAttendance")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    StaffAttendance(id = o.getLong("id"), staffId = o.getLong("staffId"), day = o.getLong("day"), status = o.getInt("status"))
                }
            } ?: emptyList()
            val staffPayments = root.optJSONArray("staffPayments")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    StaffPayment(id = o.getLong("id"), staffId = o.getLong("staffId"), amount = o.getDouble("amount"),
                        kind = o.getInt("kind"), timestamp = o.getLong("timestamp"), note = o.optNullableString("note"),
                        isDeleted = o.optBoolean("isDeleted", false))
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
                        createdAt = o.optLong("createdAt", 0L)
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
                        note = o.optNullableString("note"), closedAt = o.optLong("closedAt", 0L)
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
            val entryIds = entries.map { it.id }.toSet()
            val orphans = entries.count { it.productId != null && it.productId !in productIds } +
                entryItems.count { it.entryId !in entryIds } +
                entryItems.count { it.productId != null && it.productId !in productIds } +
                entryItems.count { it.billItemId != null && it.billItemId !in billItemIds } +
                entries.count { it.pairedEntryId != null && it.pairedEntryId !in entryIds } +
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
                staffPayments = staffPayments
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
        val staffPayments: List<StaffPayment> = emptyList()
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
        val businessPhone: String? = null
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
            invoiceItems = data.invoiceItems,
            dismissedDuplicates = data.dismissedDuplicates,
            entryItems = data.entryItems,
            dayCloses = data.dayCloses,
            schemes = data.schemes,
            staff = data.staff,
            attendance = data.attendance,
            staffPayments = data.staffPayments
        )

        data.businessProfile?.let { restoreBusinessProfile(context, it) }
    }

    // ---------- Mapping ----------

    private fun partyToJson(p: Party) = JSONObject().apply {
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
    }

    private fun jsonToParty(o: JSONObject) = Party(
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
        // Carried from the day the column exists, so a backup taken before
        // staff logins arrive is still a complete record of its own rows.
        put("createdBy", e.createdBy ?: JSONObject.NULL)
        put("paymentMethod", e.paymentMethod ?: JSONObject.NULL)
        put("rateType", e.rateType ?: JSONObject.NULL)
        put("pairedEntryId", e.pairedEntryId ?: JSONObject.NULL)
        put("season", e.season ?: JSONObject.NULL)
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
        createdBy = o.optString("createdBy").takeIf { it.isNotBlank() },
        // Absent from every backup written before this column existed; an
        // older file restores with no method, which is what those entries
        // actually recorded.
        paymentMethod = o.optNullableString("paymentMethod"),
        // Absent before format 7 — those entries never recorded either.
        rateType = o.optNullableString("rateType"),
        pairedEntryId = o.optNullableLong("pairedEntryId"),
        // Absent before v24: season read from the date.
        season = o.optNullableString("season"),
        isDeleted = o.optBoolean("isDeleted", false),
        deletedAt = o.optNullableLong("deletedAt")
    )

    private fun entryItemToJson(i: EntryItem) = JSONObject().apply {
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
        put("photoPath", b.photoPath ?: JSONObject.NULL)
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
        photoPath = o.optNullableString("photoPath")
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
        put("company", p.company ?: JSONObject.NULL)
        put("productType", p.productType ?: JSONObject.NULL)
        put("formulation", p.formulation ?: JSONObject.NULL)
        put("registrationNumber", p.registrationNumber ?: JSONObject.NULL)
        put("technicalName", p.technicalName ?: JSONObject.NULL)
        put("salePrice", p.salePrice ?: JSONObject.NULL)
        put("creditPrice", p.creditPrice ?: JSONObject.NULL)
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
        put("sourceEntryId", i.sourceEntryId ?: JSONObject.NULL)
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
        // Absent before v22: not made from a sale.
        sourceEntryId = o.optNullableLong("sourceEntryId"),
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

    private fun dismissedDuplicateToJson(d: DismissedDuplicate) = JSONObject().apply {
        put("partyIdsKey", d.partyIdsKey)
        put("dismissedAt", d.dismissedAt)
    }

    private fun jsonToDismissedDuplicate(o: JSONObject) = DismissedDuplicate(
        partyIdsKey = o.optString("partyIdsKey", ""),
        dismissedAt = o.optLong("dismissedAt", System.currentTimeMillis())
    )

    // ---------- Business Profile (SharedPreferences, not Room) ----------

    private fun businessProfileToJson(context: Context) = JSONObject().apply {
        put("businessName", BusinessProfile.businessName(context) ?: JSONObject.NULL)
        put("businessAddress", BusinessProfile.businessAddress(context) ?: JSONObject.NULL)
        put("businessPhone", BusinessProfile.businessPhone(context) ?: JSONObject.NULL)
        put("bankName", BusinessProfile.bankName(context) ?: JSONObject.NULL)
        put("bankAccountTitle", BusinessProfile.bankAccountTitle(context) ?: JSONObject.NULL)
        put("bankIban", BusinessProfile.bankIban(context) ?: JSONObject.NULL)
        put("bankJazzCash", BusinessProfile.bankJazzCash(context) ?: JSONObject.NULL)
        put("termsAndConditions", BusinessProfile.termsAndConditions(context) ?: JSONObject.NULL)
        put("strn", BusinessProfile.strn(context) ?: JSONObject.NULL)
        put("currency", BusinessProfile.currency(context))
        put("calendar", BusinessProfile.calendar(context).key)
        put("trade", BusinessProfile.trade(context) ?: JSONObject.NULL)
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
        currency = o.optNullableString("currency"),
        calendar = o.optNullableString("calendar"),
        trade = o.optNullableString("trade"),
        photoOnStatement = o.optBoolean("photoOnStatement", false),
        businessPhone = o.optNullableString("businessPhone")
    )

    // Written through the existing setters so trimming/normalisation stays in
    // one place. Only the text fields — never the image flags. A null field is
    // passed straight to its setter, which stores an empty string (its own
    // "unset"); this is a full REPLACE of the text profile, matching how the
    // ledger restore replaces rather than merges.
    private fun restoreBusinessProfile(context: Context, p: BusinessProfileData) {
        BusinessProfile.setBusinessName(context, p.businessName)
        BusinessProfile.setBusinessAddress(context, p.businessAddress)
        BusinessProfile.setBusinessPhone(context, p.businessPhone)
        BusinessProfile.setBankName(context, p.bankName)
        BusinessProfile.setBankAccountTitle(context, p.bankAccountTitle)
        BusinessProfile.setBankIban(context, p.bankIban)
        BusinessProfile.setBankJazzCash(context, p.bankJazzCash)
        BusinessProfile.setTermsAndConditions(context, p.termsAndConditions)
        BusinessProfile.setStrn(context, p.strn)
        BusinessProfile.setCurrency(context, p.currency)
        BusinessProfile.setCalendar(context, ShopCalendar.Kind.of(p.calendar))
        ShopCalendar.refresh(context)
        p.trade?.let { BusinessProfile.setTrade(context, Trade.of(it)) }
        BusinessProfile.setPhotoOnStatement(context, p.photoOnStatement)
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (isNull(key)) null else optLong(key)

    private fun JSONObject.optNullableDouble(key: String): Double? =
        if (isNull(key)) null else optDouble(key)
}
