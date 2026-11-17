package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * A big copy is restored a page at a time and comes back as the whole one does (17 Nov 2026, the audit's L001), on
 * real books: one backup read both ways — through the list path, and through the paged path forced by a lowered
 * [BackupCodec.pagedFromBytes] — answers the same, says the same loss ([RestoreCheck]), restores to the same book
 * ([LiveSync.fingerprint] and every table's row count), plain and locked; a pairing whose other half is gone is
 * cleared alike; a file with a hole is refused alike; a file before goods lines gets its lines alike; a merge reads the
 * same rows ([BackupCodec.listed]); and a file changed after it was read restores nothing. Made-up names and figures.
 */
class BackupPagedRestoreTest {

    private lateinit var dir: File
    private lateinit var here: KhataDatabase
    private val dao get() = here.khataDao()
    private val password = "made-up words 123"

    @Before
    fun open() {
        Access.resetForTest()
        dir = Files.createTempDirectory("rk-paged-restore").toFile()
        here = DesktopDatabase.open(dir.resolve("here.db"))
        runBlocking {
            val mill = dao.insertParty(Party(name = "Noor Mills", isCustomer = false))
            val customers = (0 until 4).map { dao.insertParty(Party(name = "Ali Raza $it", isCustomer = true)) }
            for ((n, c) in customers.withIndex()) {
                for (e in 0 until 6) dao.insertEntryWithItems(
                    LedgerEntry(partyId = c, amount = 100.0 + e, isGiven = e % 2 == 0, entryNumber = "", note = "Urea $e",
                        paymentMethod = if (e % 2 == 0) null else PaymentMethod.CASH),
                    if (e % 2 == 0) listOf(EntryItem(entryId = 0, itemName = "Urea", quantity = 1.0 + e, unit = "bag", rate = 50.0 + n)) else emptyList()
                )
            }
            // A cash sale and its payment, pointing at each other.
            val sale = dao.insertEntryWithItems(LedgerEntry(partyId = customers[0], amount = 700.0, isGiven = true, entryNumber = ""), emptyList())
            val paid = dao.insertEntryWithItems(LedgerEntry(partyId = customers[0], amount = 700.0, isGiven = false, entryNumber = "",
                paymentMethod = PaymentMethod.CASH, pairedEntryId = sale), emptyList())
            dao.setPairedEntry(sale, paid)
            // A pairing whose other half the bin purged for good (3 Nov audit, C1): cleared on the way in.
            dao.insertEntryWithItems(LedgerEntry(partyId = customers[1], amount = 50.0, isGiven = false, entryNumber = "", pairedEntryId = 999_999), emptyList())
            dao.insertEntryWithItems(LedgerEntry(partyId = mill, amount = 2_000.0, isGiven = false, entryNumber = ""), emptyList())
            for (i in 0 until 7) dao.insertCashEntryAudited(CashEntry(amount = 10.0 + i, isIncome = i % 2 == 0, category = "Tea"))
            for (s in 0 until 3) dao.postSale(
                Sale(subtotal = 0.0, grandTotal = 0.0, paidTotal = 0.0),
                listOf(SaleItem(saleId = 0, itemName = "Soap", quantity = 1.0, rate = 90.0), SaleItem(saleId = 0, itemName = "Tea", quantity = 2.0, rate = 40.0)),
                listOf(SalePayment(saleId = 0, method = PaymentMethod.CASH, amount = 170.0))
            )
            val ticket = dao.postSale(
                Sale(partyId = customers[2], customerName = "Ali Raza 2", subtotal = 0.0, grandTotal = 0.0, paidTotal = 0.0),
                listOf(SaleItem(saleId = 0, itemName = "DAP", quantity = 2.0, unit = "bag", rate = 1_000.0)), emptyList()
            )
            val line = dao.saleItems(ticket).single()
            dao.postReturn(
                SaleReturn(saleId = ticket, partyId = customers[2], total = 0.0, refundMethod = SaleReturn.ACCOUNT, reason = "Torn"),
                listOf(SaleReturnItem(returnId = 0, saleItemId = line.id, itemName = "DAP", quantity = 1.0, unit = "bag", rate = 1_000.0))
            )
        }
    }

    @After
    fun close() {
        BackupCodec.pagedFromBytes = BackupCodec.PAGED_RESTORE_BYTES
        Access.resetForTest()
        runCatching { here.close() }
        dir.deleteRecursively()
    }

    private fun export(name: String, password: String? = null): File = runBlocking {
        val file = dir.resolve(name)
        BackupSeal.writer(file.outputStream(), password, BackupSeal.MIN_ITERATIONS).use { BackupCodec.exportTo(dao, "Shop", JSONObject(), it) }
        file
    }

    /** [file] read as a copy under the threshold ([paged] false) or over it ([paged] true). */
    private fun read(file: File, paged: Boolean, passwords: List<String> = emptyList()): Pair<BackupCodec.ImportResult, BackupCodec.ParsedBackup?> {
        BackupCodec.pagedFromBytes = if (paged) 0L else Long.MAX_VALUE
        try {
            return BackupCodec.parseFile(file, passwords)
        } finally {
            BackupCodec.pagedFromBytes = BackupCodec.PAGED_RESTORE_BYTES
        }
    }

    private suspend fun counts(db: KhataDatabase): Map<String, Long> =
        (SyncSchema.tables.map { it.name } + listOf("audit_log", "book_seals")).associateWith { t ->
            db.syncSql().read { rows("SELECT COUNT(*) FROM `$t`", types = "I").first()[0] as Long }
        }

    /**
     * [freshLines]: a file from before goods lines has none, so each restore makes its lines anew (EntryItem.fromLegacy),
     * each with a new random uid — two restores of that file, even the same way, differ only in those uids. They are set
     * to the same made-up value in both books (entry and line number) before the fingerprint; every other field still counts.
     */
    private fun restoredBothWays(file: File, passwords: List<String> = emptyList(), freshLines: Boolean = false) = runBlocking {
        val (listResult, whole) = read(file, paged = false, passwords = passwords)
        val (pagedResult, paged) = read(file, paged = true, passwords = passwords)
        assertTrue("$listResult", listResult is BackupCodec.ImportResult.Ok)
        assertEquals(listResult, pagedResult)
        whole!!; paged!!
        assertNull(whole.paged)
        assertNotNull(paged.paged)
        assertTrue("a paged copy holds no entry list", paged.entries.isEmpty() && paged.cash.isEmpty() && paged.sales.isEmpty())
        assertEquals(whole.entries.size, paged.entryCount)
        assertEquals(whole.parties, paged.parties)

        // The loss said before a restore, the same both ways: against a book with rows of its own, and against itself.
        val target = DesktopDatabase.open(dir.resolve("target-${System.nanoTime()}.db"))
        try {
            val t = target.khataDao()
            val p = t.insertParty(Party(name = "Bilal Shah", isCustomer = true))
            t.insertEntryWithItems(LedgerEntry(partyId = p, amount = 300.0, isGiven = true, entryNumber = "", timestamp = 1_700_000_000_000), emptyList())
            t.insertCashEntryAudited(CashEntry(amount = 40.0, isIncome = false, category = "Tea", timestamp = 1_700_000_000_000))
            val lossWhole = RestoreCheck.of(t, whole)
            assertTrue("the target has rows the copy lacks", lossWhole.entries >= 1 && lossWhole.cash >= 1)
            assertEquals(lossWhole, RestoreCheck.of(t, paged))
        } finally { target.close() }
        assertEquals(RestoreCheck.of(dao, whole), RestoreCheck.of(dao, paged))

        // Restored both ways: the same book, table by table.
        val a = DesktopDatabase.open(dir.resolve("a-${System.nanoTime()}.db"))
        val b = DesktopDatabase.open(dir.resolve("b-${System.nanoTime()}.db"))
        try {
            BackupCodec.restoreLedger(a.khataDao(), whole)
            BackupCodec.restoreLedger(b.khataDao(), paged)
            if (freshLines) for (book in listOf(a, b)) book.syncSql().write { exec("UPDATE entry_items SET uid = entryId || '-' || lineNo") }
            assertEquals(LiveSync.fingerprint(a.syncSql()), LiveSync.fingerprint(b.syncSql()))
            assertEquals(counts(a), counts(b))
            assertTrue("the book is not empty", counts(b).getValue("transactions") > 20)
            // The pairing with no other half was cleared alike; the whole pairing kept.
            val pairs = b.khataDao().allEntriesForBackup().mapNotNull { it.pairedEntryId }
            assertTrue(999_999L !in pairs)
            assertEquals(2, pairs.size)
        } finally { a.close(); b.close() }
    }

    @Test
    fun `a copy restored a page at a time is the copy restored whole`() = restoredBothWays(export("plain.txt"))

    @Test
    fun `a locked copy too`() = restoredBothWays(export("locked.rkb", password), listOf(password))

    @Test
    fun `a merge reads a paged copy's every row`() {
        val file = export("merge.txt")
        val whole = read(file, paged = false).second!!
        val paged = read(file, paged = true).second!!
        assertEquals(whole, BackupCodec.listed(paged))
        assertTrue(BackupCodec.listed(whole) === whole)
    }

    @Test
    fun `a file with a hole is refused alike`() {
        val file = export("hole.txt")
        val json = JSONObject(file.readText())
        val entries = json.getJSONArray("entries")
        // An entry of a customer the file does not carry.
        entries.put(JSONObject(entries.getJSONObject(0).toString()).put("id", 888_888L).put("uid", "made-up-uid").put("partyId", 424_242L))
        file.writeText(json.toString())
        val (whole, _) = read(file, paged = false)
        val (paged, _) = read(file, paged = true)
        assertEquals(BackupCodec.ImportResult.Why.INCOMPLETE, (whole as BackupCodec.ImportResult.Failed).why)
        assertEquals(whole, paged)
    }

    @Test
    fun `a file from before goods lines gets its lines alike`() {
        val file = export("old.txt")
        val json = JSONObject(file.readText())
        json.remove("entryItems")
        // Each sale's goods as an older file held them: on the entry itself.
        val entries = json.getJSONArray("entries")
        for (i in 0 until entries.length()) {
            val e = entries.getJSONObject(i)
            if (e.optBoolean("isGiven")) e.put("itemName", "Urea").put("quantity", 2.0).put("unit", "bag")
        }
        json.put("version", 19)
        file.writeText(json.toString())
        restoredBothWays(file, freshLines = true)
    }

    @Test
    fun `a file changed after it was read restores nothing`() = runBlocking {
        val file = export("changed.txt")
        val paged = read(file, paged = true).second!!
        file.appendText(" ")
        val b = DesktopDatabase.open(dir.resolve("b.db"))
        try {
            val p = b.khataDao().insertParty(Party(name = "Bilal Shah", isCustomer = true))
            b.khataDao().insertEntryWithItems(LedgerEntry(partyId = p, amount = 300.0, isGiven = true, entryNumber = ""), emptyList())
            val before = LiveSync.fingerprint(b.syncSql())
            try {
                BackupCodec.restoreLedger(b.khataDao(), paged)
                fail("a changed file was restored")
            } catch (e: java.io.IOException) {
                // Said as a failure, never a book half replaced.
            }
            assertEquals(before, LiveSync.fingerprint(b.syncSql()))
            assertNotEquals(0L, counts(b).getValue("transactions"))
        } finally { b.close() }
    }

    @Test
    fun `a copy under the threshold is read whole, one over it a page at a time`() {
        val file = export("size.txt")
        BackupCodec.pagedFromBytes = file.length()
        try {
            assertNull("a file of exactly the threshold is read whole", BackupCodec.parseFile(file).second!!.paged)
            BackupCodec.pagedFromBytes = file.length() - 1
            assertNotNull(BackupCodec.parseFile(file).second!!.paged)
        } finally {
            BackupCodec.pagedFromBytes = BackupCodec.PAGED_RESTORE_BYTES
        }
        assertEquals(32L * 1024 * 1024, BackupCodec.PAGED_RESTORE_BYTES)
    }
}
