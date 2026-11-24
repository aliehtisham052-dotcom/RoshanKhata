package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Backups written by older builds open right (24 Nov 2026, plan item B2, data safety). Each format from 7 to the
 * current one is made from a rich export of today's book by taking out exactly what [BackupCodec]'s own converters
 * say was not there yet ("Absent before format N" — the table below is those lines, one for one), then stamped with that
 * format's number. Each such file must read on both paths (the list and the paged one) and restore, with:
 *  - every row that existed then restored (parties, entries, cash rows, tickets from format 8);
 *  - a field that came later reading as nothing / the old default — never as another row's value;
 *  - a field that was already there kept.
 * Formats 1 to 6 are read as the format-7 shape stamped lower (INFERRED: their real shapes are not in the repository,
 * only the rule that every key but "format" and "version" is optional). Made-up names and figures only.
 */
class OldBooksBackupFormatsTest {

    private lateinit var dir: File
    private lateinit var source: KhataDatabase

    @Before
    fun open() {
        Access.resetForTest()
        dir = Files.createTempDirectory("rk-old-formats").toFile()
        source = DesktopDatabase.open(dir.resolve("source.db"))
        val dao = source.khataDao()
        runBlocking {
            val noor = dao.insertParty(Party(name = "Noor Mills", isCustomer = false, note = "Ships on Fridays"))
            val ali = dao.insertParty(Party(name = "Ali Raza", isCustomer = true))
            dao.insertParty(Party(name = "Sana Bibi", isCustomer = true))
            dao.insertEntryWithItems(
                LedgerEntry(partyId = ali, amount = 250.0, isGiven = false, entryNumber = "", note = "probe-ref",
                    paymentMethod = PaymentMethod.BANK, paymentRef = "TID-0001", billPhotoPath = "bills/made-up.jpg"),
                emptyList()
            )
            dao.insertEntryWithItems(
                LedgerEntry(partyId = ali, amount = 900.0, isGiven = true, entryNumber = "", note = "probe-opening", entryKind = EntryKind.OPENING),
                emptyList()
            )
            dao.insertEntryWithItems(
                LedgerEntry(partyId = noor, amount = 400.0, isGiven = false, entryNumber = "", note = "Urea bags"),
                listOf(EntryItem(entryId = 0, itemName = "Urea", quantity = 4.0, unit = "bag", rate = 100.0))
            )
            dao.insertProduct(Product(
                name = "Urea", nameKey = ProductName.key("Urea"), normalisedName = ProductName.normalised("Urea"),
                salePrice = 4_500.0, barcode = "6001234567890", packUnit = "carton", packSize = 12.0, otherNames = "یوریا"
            ))
            for (i in 0 until 3) dao.insertCashEntryAudited(CashEntry(amount = 10.0 + i, isIncome = i % 2 == 0, category = "Tea"))
            for (s in 0 until 2) dao.postSale(
                Sale(subtotal = 0.0, grandTotal = 0.0, paidTotal = 0.0),
                listOf(SaleItem(saleId = 0, itemName = "Soap", quantity = 1.0, rate = 90.0)),
                listOf(SalePayment(saleId = 0, method = PaymentMethod.CASH, amount = 90.0))
            )
        }
    }

    @After
    fun close() {
        BackupCodec.pagedFromBytes = BackupCodec.PAGED_RESTORE_BYTES
        Access.resetForTest()
        runCatching { source.close() }
        dir.deleteRecursively()
    }

    // ---------- what each format did not have yet (BackupCodec's "Absent before …" lines) ----------

    /** (table key, field, the format that added it). */
    private val fields = listOf(
        Triple("entries", "paymentRef", 16), Triple("entries", "saleId", 8), Triple("entries", "entryKind", 20),
        Triple("entries", "billPhotoPath", 15), Triple("entries", "rateType", 7),
        Triple("parties", "note", 18),
        Triple("cheques", "photoPath", 18),
        Triple("cashbook", "paymentMethod", 8), Triple("cashbook", "paymentRef", 16), Triple("cashbook", "saleId", 8),
        Triple("cashbook", "photoPath", 18), Triple("cashbook", "createdBy", 21),
        Triple("products", "barcode", 8), Triple("products", "costPrice", 8), Triple("products", "packUnit", 12),
        Triple("products", "packSize", 12), Triple("products", "otherNames", 20),
        Triple("invoices", "kind", 17), Triple("invoices", "status", 17),
        Triple("sales", "heldUid", 21),
        Triple("saleItems", "taxRate", 12), Triple("saleItems", "taxAmount", 12), Triple("saleItems", "taxIncluded", 12),
        Triple("auditLog", "hash", 12),
        Triple("staff", "role", 10), Triple("staff", "pinHash", 10), Triple("staff", "pinSalt", 10)
    )

    /** (top-level key, the format that added the whole table). */
    private val tables = listOf(
        "sales" to 8, "saleItems" to 8, "salePayments" to 8, "auditLog" to 8,
        "saleReturns" to 9, "saleReturnItems" to 9,
        "stockAdjustments" to 11, "purchaseOrders" to 11, "purchaseOrderItems" to 11,
        "bookSettings" to 12, "loyalty" to 12, "shifts" to 12,
        "bookSeals" to 19
    )

    /** The tables that got a uid at format 14 (schema 38); every other table with one had it at 12. */
    private val uidAt14 = listOf(
        "parties", "products", "plans", "installments", "billItems", "invoiceItems", "entryItems", "dayCloses", "schemes",
        "staff", "staffAttendance", "saleItems", "salePayments", "saleReturnItems", "purchaseOrderItems"
    )

    private fun export(): JSONObject = runBlocking {
        val f = dir.resolve("now.txt")
        f.writer(Charsets.UTF_8).use { BackupCodec.exportTo(source.khataDao(), "Shop", JSONObject(), it) }
        JSONObject(f.readText())
    }

    /** [now] as a build writing format [v] wrote it. */
    private fun asWrittenAt(now: JSONObject, v: Int): JSONObject {
        val old = JSONObject(now.toString())
        for ((key, added) in tables) if (v < added) old.remove(key)
        for ((key, field, added) in fields) if (v < added) old.optJSONArray(key)?.let { rows(it).forEach { r -> r.remove(field) } }
        val uidless = if (v < 12) old.keys().asSequence().toList() else if (v < 14) uidAt14 else emptyList()
        for (key in uidless) old.optJSONArray(key)?.let { rows(it).forEach { r -> r.remove("uid") } }
        old.put("version", v)
        return old
    }

    private fun rows(a: JSONArray): List<JSONObject> = (0 until a.length()).map { a.getJSONObject(it) }

    private fun read(file: File, paged: Boolean): Pair<BackupCodec.ImportResult, BackupCodec.ParsedBackup?> {
        BackupCodec.pagedFromBytes = if (paged) 0L else Long.MAX_VALUE
        try {
            return BackupCodec.parseFile(file)
        } finally {
            BackupCodec.pagedFromBytes = BackupCodec.PAGED_RESTORE_BYTES
        }
    }

    private suspend fun count(db: KhataDatabase, table: String): Long =
        db.syncSql().read { rows("SELECT COUNT(*) FROM `$table`", types = "I").first()[0] as Long }

    // ---------- the test ----------

    @Test
    fun `the book of today is what the probes expect`() = runBlocking {
        // A precondition, so a failure below is the old format's and not the seed's.
        val dao = source.khataDao()
        val e = dao.allEntriesForBackup().first { it.note == "probe-ref" }
        assertEquals("TID-0001", e.paymentRef)
        assertEquals("bills/made-up.jpg", e.billPhotoPath)
        assertEquals(EntryKind.OPENING, dao.allEntriesForBackup().first { it.note == "probe-opening" }.entryKind)
        assertEquals("Ships on Fridays", dao.allPartiesForBackup().first { it.name == "Noor Mills" }.note)
        val p = dao.allProductsForBackup().single()
        assertEquals("6001234567890", p.barcode); assertEquals("carton", p.packUnit); assertEquals("یوریا", p.otherNames)
        assertEquals(2, dao.allSalesForBackup().size)
    }

    @Test
    fun `every format from 7 to now reads and restores on both paths, later fields reading as nothing`() = runBlocking {
        val now = export()
        val src = source.khataDao()
        val wantParties = src.allPartiesForBackup().size.toLong()
        val wantEntries = src.allEntriesForBackup().size.toLong()
        val wantCash = src.allCashForBackup().size.toLong()
        val wantSales = src.allSalesForBackup().size.toLong()
        for (v in 7..BackupCodec.FORMAT_VERSION) {
            val file = dir.resolve("format-$v.txt").apply { writeText(asWrittenAt(now, v).toString()) }
            for (paged in listOf(false, true)) {
                val label = "format $v, paged=$paged"
                val (result, parsed) = read(file, paged)
                assertTrue("$label: $result", result is BackupCodec.ImportResult.Ok)
                val db = DesktopDatabase.open(dir.resolve("restored-$v-$paged.db"))
                try {
                    BackupCodec.restoreLedger(db.khataDao(), parsed!!)
                    assertEquals("$label parties", wantParties, count(db, "parties"))
                    assertEquals("$label entries", wantEntries, count(db, "transactions"))
                    assertEquals("$label cashbook", wantCash, count(db, "cashbook"))
                    assertEquals("$label tickets", if (v >= 8) wantSales else 0L, count(db, "sales"))

                    val d = db.khataDao()
                    val ref = d.allEntriesForBackup().first { it.note == "probe-ref" }
                    assertEquals("$label paymentRef", if (v >= 16) "TID-0001" else null, ref.paymentRef)
                    assertEquals("$label billPhotoPath", if (v >= 15) "bills/made-up.jpg" else null, ref.billPhotoPath)
                    assertEquals("$label amount and way kept", 250.0, ref.amount, 0.0001)
                    assertEquals("$label entryKind", if (v >= 20) EntryKind.OPENING else EntryKind.NORMAL,
                        d.allEntriesForBackup().first { it.note == "probe-opening" }.entryKind)
                    assertEquals("$label party note", if (v >= 18) "Ships on Fridays" else null,
                        d.allPartiesForBackup().first { it.name == "Noor Mills" }.note)
                    val p = d.allProductsForBackup().single()
                    assertEquals("$label product name kept", "Urea", p.name)
                    assertEquals("$label barcode", if (v >= 8) "6001234567890" else null, p.barcode)
                    assertEquals("$label packUnit", if (v >= 12) "carton" else null, p.packUnit)
                    assertEquals("$label otherNames", if (v >= 20) "یوریا" else null, p.otherNames)
                    // Every row got an identity of its own where the file had none.
                    assertTrue("$label uids", d.allEntriesForBackup().all { !it.uid.isNullOrBlank() })
                    assertEquals("$label uids are unique", wantEntries, d.allEntriesForBackup().mapNotNull { it.uid }.toSet().size.toLong())
                    assertNotNull(parsed)
                } finally { db.close() }
            }
        }
    }

    @Test
    fun `formats 1 to 6 are read as the format 7 shape under a lower stamp`() = runBlocking {
        val seven = asWrittenAt(export(), 7)
        for (v in 1..6) {
            val file = dir.resolve("format-$v.txt").apply { writeText(JSONObject(seven.toString()).put("version", v).toString()) }
            for (paged in listOf(false, true)) {
                val (result, parsed) = read(file, paged)
                assertTrue("format $v, paged=$paged: $result", result is BackupCodec.ImportResult.Ok)
                assertEquals(source.khataDao().allPartiesForBackup().size, parsed!!.parties.size)
            }
        }
    }

    @Test
    fun `a file with no version at all is an old one that opens and one that is not ours still does not`() {
        val seven = asWrittenAt(export(), 7)
        val noVersion = JSONObject(seven.toString()).also { it.remove("version") }
        val file = dir.resolve("no-version.txt").apply { writeText(noVersion.toString()) }
        assertTrue(read(file, false).first is BackupCodec.ImportResult.Ok)
        val other = dir.resolve("not-ours.txt").apply { writeText(JSONObject(seven.toString()).put("format", "Other").toString()) }
        assertEquals(BackupCodec.ImportResult.Why.NOT_OURS, (read(other, false).first as BackupCodec.ImportResult.Failed).why)
        assertNull(read(other, false).second)
    }
}
