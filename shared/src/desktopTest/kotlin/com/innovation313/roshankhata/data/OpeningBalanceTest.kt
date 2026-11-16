package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * An opening balance is owed, never sold (schema 45, 16 Nov 2026, the audit's D-02). Customers brought in from another
 * app's file came in as ordinary "I gave" entries stamped with the import's time, so Sale Insights, the sales report,
 * profit, the day report, the 21:00 notice, the best customers and Hudhud's sale answers all counted the whole old
 * book as that day's sales. Now every such balance is [EntryKind.OPENING]: it stays in the balance, the statement and
 * dues by age, and no sale figure reads it. The owner can mark one by hand (EDIT_BOOK, written in Who did what) and
 * take it back. Made-up names and figures.
 */
class OpeningBalanceTest {

    private lateinit var dir: File
    private lateinit var db: KhataDatabase
    private val dao get() = db.khataDao()
    private val now = System.currentTimeMillis()
    private val day = 24L * 60 * 60 * 1000
    private val from = now - 60_000
    private val to = now + 60_000

    @Before
    fun open() {
        dir = Files.createTempDirectory("rk-opening").toFile()
        db = DesktopDatabase.open(dir.resolve("khata.db"))
    }

    @After
    fun close() {
        db.close()
        dir.deleteRecursively()
    }

    private suspend fun gave(party: Long, amount: Double, at: Long = now, items: List<EntryItem> = emptyList(), qarz: Boolean = false): Long =
        dao.insertEntryWithItems(LedgerEntry(partyId = party, amount = amount, isGiven = true, entryNumber = "", timestamp = at, isQarzeHasna = qarz), items)

    private suspend fun imported(vararg rows: CsvImport.Row): Map<String, Long> {
        dao.importOpeningBalances(rows.toList(), note = "Opening balance", now = now)
        return dao.partiesWithBalanceOnce().associate { it.name to it.id }
    }

    @Test
    fun `the CSV import writes each balance as an opening balance`() = runBlocking {
        val ids = imported(CsvImport.Row("Ali Raza", "0300 1234567", 50_000.0), CsvImport.Row("Bashir Ahmad", null, -2_000.0))
        val ali = dao.latestEntriesWithItems(ids.getValue("Ali Raza"), 10).single().entry
        val bashir = dao.latestEntriesWithItems(ids.getValue("Bashir Ahmad"), 10).single().entry
        assertEquals(EntryKind.OPENING, ali.entryKind)
        assertEquals(EntryKind.OPENING, bashir.entryKind)
        // An entry written by hand is an ordinary one.
        val handWritten = gave(ids.getValue("Ali Raza"), 700.0)
        assertEquals(EntryKind.NORMAL, dao.getEntry(handWritten)!!.entryKind)
    }

    @Test
    fun `an opening balance stays in the balance and out of every sale figure`() = runBlocking {
        val ids = imported(CsvImport.Row("Ali Raza", null, 50_000.0), CsvImport.Row("Tariq Butt", null, -2_000.0))
        val ali = ids.getValue("Ali Raza")
        gave(ali, 700.0)

        val balances = dao.partiesWithBalanceOnce().associate { it.name to it.balance }
        assertEquals("the balance keeps it", 50_700.0, balances.getValue("Ali Raza"), 0.001)
        assertEquals(-2_000.0, balances.getValue("Tariq Butt"), 0.001)
        assertEquals(50_700.0, dao.liveBalance(ali), 0.001)

        assertEquals("Sale Insights, the day report, profit, the 21:00 notice", 700.0, dao.salesTotalBetween(from, to), 0.001)
        assertEquals(1, dao.salesCountBetween(from, to))
        assertEquals(700.0, dao.topCustomersBetween(from, to, 5).single().total, 0.001)
        assertEquals("the sales report's khata rows", listOf(700.0), dao.khataSaleRowsBetween(from, to).map { it.amount })
        assertEquals("a customer's advance brought in is no money received today", 0.0, dao.customerReceivedBetween(from, to), 0.001)
        // The day book shows it, said as an opening balance, not as a sale.
        val book = DayBook.rows(dao, from, to)
        assertEquals(2, book.count { it.opening })
        assertTrue(book.single { it.amount == 700.0 }.let { !it.opening })
    }

    @Test
    fun `the first sale and the buying days are not the import's`() = runBlocking {
        val earlier = now - 40 * day
        dao.importOpeningBalances(listOf(CsvImport.Row("Ali Raza", null, 9_000.0)), note = "Opening balance", now = earlier)
        val ali = dao.partiesWithBalanceOnce().single().id
        assertNull("only an opening balance: no sale yet", dao.firstSaleAt())
        gave(ali, 500.0, at = now - 20 * day)
        gave(ali, 600.0, at = now - 10 * day)
        assertEquals(now - 20 * day, dao.firstSaleAt())
        assertTrue("two buying days, not three: customers who stopped coming", dao.buyingHabits().none { it.partyId == ali })
        gave(ali, 650.0, at = now - 5 * day)
        assertEquals(3, dao.buyingHabits().single { it.partyId == ali }.days)
    }

    @Test
    fun `the owner marks a plain entry an opening balance and takes it back, both written down`() = runBlocking {
        val ali = dao.insertParty(Party(name = "Ali Raza"))
        val old = gave(ali, 12_000.0)
        assertEquals(12_000.0, dao.salesTotalBetween(from, to), 0.001)

        assertNull(dao.setEntryKind(old, EntryKind.OPENING))
        assertEquals(EntryKind.OPENING, dao.getEntry(old)!!.entryKind)
        assertEquals(0.0, dao.salesTotalBetween(from, to), 0.001)
        assertEquals(12_000.0, dao.liveBalance(ali), 0.001)
        assertNull("already of that kind: nothing written", dao.setEntryKind(old, EntryKind.OPENING))

        assertNull(dao.setEntryKind(old, EntryKind.NORMAL))
        assertEquals(12_000.0, dao.salesTotalBetween(from, to), 0.001)

        val rows = dao.allAuditForBackup().filter { it.action == AuditAction.ENTRY_KIND_CHANGED }
        assertEquals(listOf("NORMAL>OPENING", "OPENING>NORMAL"), rows.map { it.detail })
        assertTrue(rows.all { it.refTable == "transactions" && it.refId == old })
        assertEquals(listOf(EntryKind.OPENING, EntryKind.NORMAL), rows.map { EntryKind.changedTo(it.detail) })
    }

    @Test
    fun `only a plain amount can be an opening balance`() = runBlocking {
        val ali = dao.insertParty(Party(name = "Ali Raza"))
        val withGoods = gave(ali, 3_000.0, items = listOf(EntryItem(entryId = 0, itemName = "Urea", quantity = 1.0, unit = "bag", rate = 3_000.0)))
        val loan = gave(ali, 5_000.0, qarz = true)
        val sale = dao.insertEntryNumbered(LedgerEntry(partyId = ali, amount = 800.0, isGiven = true, entryNumber = "", rateType = RateType.CASH))
        val paid = dao.recordCashForSale(sale)!!
        assertEquals(EntryKind.Refusal.GOODS, dao.setEntryKind(withGoods, EntryKind.OPENING))
        assertEquals(EntryKind.Refusal.LOAN, dao.setEntryKind(loan, EntryKind.OPENING))
        assertEquals(EntryKind.Refusal.PAIRED, dao.setEntryKind(sale, EntryKind.OPENING))
        assertEquals(EntryKind.Refusal.PAIRED, dao.setEntryKind(paid, EntryKind.OPENING))
        for (id in listOf(withGoods, loan, sale, paid)) assertEquals(EntryKind.NORMAL, dao.getEntry(id)!!.entryKind)
        assertTrue("nothing refused is written down", dao.allAuditForBackup().none { it.action == AuditAction.ENTRY_KIND_CHANGED })
    }

    @Test
    fun `an edit keeps the kind, and goods given make it an ordinary sale again`() = runBlocking {
        val ids = imported(CsvImport.Row("Ali Raza", null, 4_000.0))
        val e = dao.latestEntriesWithItems(ids.getValue("Ali Raza"), 10).single().entry
        // A form that builds the row afresh reads NORMAL; the book keeps what it was.
        dao.updateEntryWithItems(e.copy(amount = 4_500.0, entryKind = EntryKind.NORMAL), emptyList())
        assertEquals(EntryKind.OPENING, dao.getEntry(e.id)!!.entryKind)
        assertEquals(4_500.0, dao.getEntry(e.id)!!.amount, 0.001)
        assertEquals(0.0, dao.salesTotalBetween(from, to), 0.001)
        dao.updateEntryWithItems(e.copy(amount = 4_500.0), listOf(EntryItem(entryId = e.id, itemName = "DAP", quantity = 1.0, unit = "bag", rate = 4_500.0)))
        assertEquals(EntryKind.NORMAL, dao.getEntry(e.id)!!.entryKind)
        assertEquals(4_500.0, dao.salesTotalBetween(from, to), 0.001)
    }

    @Test
    fun `the refusal names each reason, one guard at a time`() {
        // Each guard alone (lesson 129): the DAO test above reaches some through others.
        val plain = LedgerEntry(partyId = 1, amount = 100.0, isGiven = true, entryNumber = "RK-000001")
        assertNull(EntryKind.refusal(plain, hasGoods = false, owner = null))
        assertNull("no owner is said as an empty word on the computer", EntryKind.refusal(plain, false, ""))
        assertEquals(EntryKind.Refusal.BINNED, EntryKind.refusal(plain.copy(isDeleted = true), false, null))
        assertEquals(EntryKind.Refusal.GOODS, EntryKind.refusal(plain, true, null))
        assertEquals("an older entry's one goods line on the row itself", EntryKind.Refusal.GOODS, EntryKind.refusal(plain.copy(itemName = "Urea"), false, null))
        assertEquals(EntryKind.Refusal.GOODS, EntryKind.refusal(plain.copy(productId = 3), false, null))
        assertEquals(EntryKind.Refusal.LOAN, EntryKind.refusal(plain.copy(isQarzeHasna = true), false, null))
        assertEquals(EntryKind.Refusal.LINKED, EntryKind.refusal(plain.copy(saleId = 4), false, null))
        for (owner in listOf("bill", "plan", "cheque", "sale", "return")) assertEquals(owner, EntryKind.Refusal.LINKED, EntryKind.refusal(plain, false, owner))
        assertEquals(EntryKind.Refusal.PAIRED, EntryKind.refusal(plain.copy(pairedEntryId = 9), false, null))
    }

    @Test
    fun `a stored word this release does not know counts as ordinary`() {
        assertEquals(EntryKind.NORMAL, EntryKind.of(null))
        assertEquals(EntryKind.NORMAL, EntryKind.of(""))
        assertEquals(EntryKind.NORMAL, EntryKind.of("LENT_LATER"))
        assertEquals(EntryKind.OPENING, EntryKind.of(" opening "))
        // The DAO's SQL spells the two as literals: they must stay these words.
        assertEquals("NORMAL", EntryKind.NORMAL)
        assertEquals("OPENING", EntryKind.OPENING)
        assertEquals(EntryKind.OPENING, EntryKind.changedTo("NORMAL>OPENING · Manager One"))
        assertNull(EntryKind.changedTo("season"))
        assertEquals("NORMAL>OPENING", EntryKind.auditDetail("normal", "OPENING"))
    }
}
