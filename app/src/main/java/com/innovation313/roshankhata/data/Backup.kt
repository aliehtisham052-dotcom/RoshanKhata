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

    const val FORMAT_VERSION = 3

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

    suspend fun export(dao: KhataDao): String {
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
            val bills: Int = 0
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

            // An entry pointing at a party that is not in the file would be
            // orphaned on insert — better to refuse than to import a ledger
            // with holes in it.
            val partyIds = parties.map { it.id }.toSet()
            val planIds = plans.map { it.id }.toSet()
            val billIds = bills.map { it.id }.toSet()
            val orphans = entries.count { it.partyId !in partyIds } +
                cheques.count { it.partyId !in partyIds } +
                plans.count { it.partyId !in partyIds } +
                installments.count { it.planId !in planIds } +
                bills.count { it.partyId !in partyIds } +
                billItems.count { it.billId !in billIds }

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
                bills = bills.size
            ) to ParsedBackup(parties, entries, cheques, cash, plans, installments, bills, billItems)
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
        val billItems: List<BillItem> = emptyList()
    )

    /** Replaces everything. Only called after the user has confirmed. */
    suspend fun restore(dao: KhataDao, data: ParsedBackup) {
        // Children first, then parents: foreign keys will not allow a party to
        // be removed while its entries still point at it.
        dao.wipeBillItems()
        dao.wipeBills()
        dao.wipeInstallments()
        dao.wipePlans()
        dao.wipeEntries()
        dao.wipeCheques()
        dao.wipeCash()
        dao.wipeParties()

        dao.restoreParties(data.parties)
        dao.restoreEntries(data.entries)
        dao.restoreCheques(data.cheques)
        dao.restoreCash(data.cash)
        dao.restorePlans(data.plans)
        dao.restoreInstallments(data.installments)
        dao.restoreBills(data.bills)
        dao.restoreBillItems(data.billItems)
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
        isDeleted = o.optBoolean("isDeleted", false)
    )

    // JSONObject.optString returns "" for null, which would turn an absent
    // phone number into an empty string rather than leaving it absent.
    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (isNull(key)) null else optLong(key)

    private fun JSONObject.optNullableDouble(key: String): Double? =
        if (isNull(key)) null else optDouble(key)
}
