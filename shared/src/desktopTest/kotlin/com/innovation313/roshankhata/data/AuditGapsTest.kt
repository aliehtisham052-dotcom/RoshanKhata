package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The writes scripts/check_dao_gates.py found with no row in Who did what (17 Nov 2026, BC-220..BC-225): a cheque
 * cleared, bounced, cancelled or put back to pending; an instalment of a plan received; the Recycle Bin's restore,
 * delete forever and Empty bin (the computer's Khata tab wrote nothing); a product spreadsheet brought in on the
 * phone. Each row is written by the DAO in the write's own transaction, with the signed-in name, so every caller on
 * both platforms is covered, and only when something really changed. Made-up names and figures.
 */
class AuditGapsTest {

    private lateinit var dir: File
    private lateinit var db: KhataDatabase
    private val dao get() = db.khataDao()

    @Before
    fun open() {
        Access.resetForTest()
        dir = Files.createTempDirectory("rk-audit-gaps").toFile()
        db = DesktopDatabase.open(dir.resolve("khata.db"))
    }

    @After
    fun close() {
        Access.resetForTest()
        db.close()
        dir.deleteRecursively()
    }

    private fun cashier(name: String) = Access.signIn(Staff(id = 4, name = name, monthlySalary = 1.0, role = Role.CASHIER))

    private suspend fun rows(vararg actions: String): List<AuditLog> = dao.auditInOrder().filter { it.action in actions }

    @Test
    fun `a cheque cleared is written down with who cleared it, and its payment carries the name`() = runBlocking {
        val party = dao.insertParty(Party(name = "Ali Raza", phone = null, isCustomer = true))
        val cheque = dao.insertCheque(Cheque(partyId = party, amount = 2_000.0, isReceived = true, dueDate = 1L))
        cashier("Imran")
        val payment = dao.clearCheque(cheque, "Cheque #123")!!
        val logged = rows(AuditAction.CHEQUE_CLEARED).single()
        assertEquals("Imran", logged.user)
        assertEquals("cheques", logged.refTable)
        assertEquals(cheque, logged.refId)
        assertEquals("2,000.00", logged.detail)
        assertEquals("the payment names who took it", "Imran", dao.getEntry(payment)!!.createdBy)

        assertNull("a second click posts nothing", dao.clearCheque(cheque, "again"))
        assertEquals("and writes nothing", 1, rows(AuditAction.CHEQUE_CLEARED).size)
    }

    @Test
    fun `a cheque bounced, put back and cancelled is written down each time, a refused change never`() = runBlocking {
        val party = dao.insertParty(Party(name = "Bashir Ahmad", phone = null, isCustomer = true))
        val cheque = dao.insertCheque(Cheque(partyId = party, amount = 750.0, isReceived = true, dueDate = 1L))
        cashier("Asad")
        assertTrue(dao.settleCheque(cheque, ChequeStatus.BOUNCED))
        assertTrue(dao.settleCheque(cheque, null))
        assertTrue(dao.settleCheque(cheque, ChequeStatus.CANCELLED))
        val settled = rows(AuditAction.CHEQUE_BOUNCED, AuditAction.CHEQUE_REOPENED, AuditAction.CHEQUE_CANCELLED)
        assertEquals(listOf(AuditAction.CHEQUE_BOUNCED, AuditAction.CHEQUE_REOPENED, AuditAction.CHEQUE_CANCELLED), settled.map { it.action })
        assertTrue(settled.all { it.user == "Asad" && it.refTable == "cheques" && it.refId == cheque && it.detail == "750.00" })

        // Cancelled cannot be bounced; a cleared cheque cannot go back: nothing changes, nothing is written.
        assertFalse(dao.settleCheque(cheque, ChequeStatus.BOUNCED))
        val cleared = dao.insertCheque(Cheque(partyId = party, amount = 300.0, isReceived = false, dueDate = 1L))
        assertNotNull(dao.clearCheque(cleared, "Cheque"))
        assertFalse(dao.settleCheque(cleared, null))
        assertFalse(dao.settleCheque(cleared, ChequeStatus.CANCELLED))
        assertEquals(3, rows(AuditAction.CHEQUE_BOUNCED, AuditAction.CHEQUE_REOPENED, AuditAction.CHEQUE_CANCELLED).size)
    }

    @Test
    fun `an instalment received is an entry added, with who took it`() = runBlocking {
        val party = dao.insertParty(Party(name = "Tariq Butt", phone = null, isCustomer = true))
        val plan = dao.insertPlan(PaymentPlan(partyId = party, totalAmount = 5_000.0, installmentAmount = 1_000.0))
        cashier("Imran")
        val entry = dao.recordPlanPayment(plan, party, 1_000.0, paidBefore = 0.0, entryNote = "Instalment", note = null)
        val logged = rows(AuditAction.ENTRY_ADDED).single()
        assertEquals("Imran", logged.user)
        assertEquals("transactions", logged.refTable)
        assertEquals(entry, logged.refId)
        assertEquals("1,000.00", logged.detail)
        assertEquals("Imran", dao.getEntry(entry)!!.createdBy)
    }

    @Test
    fun `the bin's restore is written down only when the row came back`() = runBlocking {
        val party = dao.insertParty(Party(name = "Ali Raza", phone = null, isCustomer = true))
        dao.insertEntryWithItems(LedgerEntry(partyId = party, amount = 400.0, isGiven = true, entryNumber = ""), emptyList())
        dao.softDeleteParties(listOf(party))
        cashier("Asad")
        assertTrue(dao.restoreFromBin(BinTable.PARTIES, party, dao.getParty(party)!!.deletedAt))
        assertFalse(dao.getParty(party)!!.isDeleted)
        assertEquals("his entries came back with him", 400.0, dao.liveBalance(party), 0.001)
        assertFalse("not in the bin any more", dao.restoreFromBin(BinTable.PARTIES, party))

        val cash = dao.insertCashEntry(CashEntry(amount = 50.0, isIncome = true, category = "Misc"))
        assertTrue(dao.softDeleteOwnCashEntry(cash))
        assertTrue(dao.restoreFromBin(BinTable.CASH, cash))
        assertFalse(dao.restoreFromBin(BinTable.CASH, cash))
        assertFalse("a table the bin does not know", dao.restoreFromBin("staff", cash))

        val restored = rows(AuditAction.BIN_RESTORED)
        assertEquals(listOf(BinTable.PARTIES to party, BinTable.CASH to cash), restored.map { it.refTable to it.refId })
        assertTrue(restored.all { it.user == "Asad" })
    }

    @Test
    fun `delete forever is written down only when the row went, a kept customer never`() = runBlocking {
        val party = dao.insertParty(Party(name = "Bashir Ahmad", phone = null, isCustomer = true))
        val entry = dao.insertEntryWithItems(LedgerEntry(partyId = party, amount = 900.0, isGiven = true, entryNumber = ""), emptyList())
        dao.softDeleteEntry(entry)
        cashier("Imran")
        assertTrue(dao.purgeFromBin(BinTable.ENTRIES, entry))
        assertNull(dao.getEntry(entry))
        assertFalse("already gone", dao.purgeFromBin(BinTable.ENTRIES, entry))

        // A customer with a cheque is kept by the bin: nothing went, nothing is written.
        val kept = dao.insertParty(Party(name = "Tariq Butt", phone = null, isCustomer = true))
        dao.insertCheque(Cheque(partyId = kept, amount = 100.0, isReceived = true, dueDate = 1L))
        dao.softDeleteParties(listOf(kept))
        assertFalse(dao.purgeFromBin(BinTable.PARTIES, kept))
        assertNotNull(dao.getParty(kept))

        val purged = rows(AuditAction.BIN_PURGED)
        assertEquals(listOf(BinTable.ENTRIES to entry), purged.map { it.refTable to it.refId })
        assertEquals("Imran", purged.single().user)
    }

    @Test
    fun `Empty bin is one row with who emptied it, and says how many customers it kept`() = runBlocking {
        val gone = dao.insertParty(Party(name = "Ali Raza", phone = null, isCustomer = true))
        val kept = dao.insertParty(Party(name = "Bashir Ahmad", phone = null, isCustomer = true))
        dao.insertCheque(Cheque(partyId = kept, amount = 100.0, isReceived = true, dueDate = 1L))
        dao.softDeleteParties(listOf(gone, kept))
        cashier("Asad")
        assertEquals(1, dao.emptyBin())
        assertNull(dao.getParty(gone))
        val emptied = rows(AuditAction.BIN_EMPTIED).single()
        assertEquals("Asad", emptied.user)
    }

    @Test
    fun `a product spreadsheet brought in is written down as an import, in no language`() = runBlocking {
        cashier("Imran")
        val first = ProductCsv.plan(listOf(ProductCsv.Row("Urea", salePrice = 4_500.0)), dao.productsOnce())
        assertEquals(1 to 0, dao.importProducts(first))
        val again = ProductCsv.plan(listOf(ProductCsv.Row("Urea", salePrice = 4_600.0)), dao.productsOnce())
        assertEquals(0 to 1, dao.importProducts(again))
        val logged = rows(AuditAction.IMPORT)
        assertEquals(listOf("1 0", "0 1"), logged.map { it.detail })
        assertTrue(logged.all { it.refTable == ProductImportNote.TABLE && it.user == "Imran" })
        assertEquals(1 to 0, ProductImportNote.parse(logged.first().detail))
    }

    @Test
    fun `an import's note is read back only when it is the two counts`() {
        assertEquals(3 to 2, ProductImportNote.parse(ProductImportNote.detail(3, 2)))
        assertNull(ProductImportNote.parse(null))
        assertNull("the computer's older rows, in words", ProductImportNote.parse("3 products added, 2 updated"))
        assertNull(ProductImportNote.parse("-1 2"))
        assertNull(ProductImportNote.parse("12"))
    }
}
