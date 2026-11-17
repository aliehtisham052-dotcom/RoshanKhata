package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * A cashbook line and a pending cheque corrected, day and time included (17 Nov 2026, audit L023): neither platform
 * could change a written cashbook line, and a cheque's correction was guarded only by the screen. Both are DAO
 * transactions now, gated EDIT_BOOK at the data layer and written in Who did what. Made-up names and figures.
 */
class EditCashChequeTest {

    private lateinit var dir: File
    private lateinit var db: KhataDatabase
    private val dao get() = db.khataDao()

    @Before
    fun open() {
        Access.resetForTest()
        dir = Files.createTempDirectory("rk-edit-cash").toFile()
        db = DesktopDatabase.open(dir.resolve("khata.db"))
    }

    @After
    fun close() {
        Access.resetForTest()
        db.close()
        dir.deleteRecursively()
    }

    private fun cashier() = Access.signIn(Staff(id = 4, name = "Test Cashier", monthlySalary = 1.0, role = Role.CASHIER))

    @Test
    fun `a cashbook line is corrected, its way, picture and identity kept, and the change is written down`() = runBlocking {
        val id = dao.insertCashEntryAudited(CashEntry(amount = 500.0, isIncome = false, category = "Tea", timestamp = 1_000L,
            paymentMethod = PaymentMethod.CASH, photoPath = "bills/test.jpg"))
        val before = dao.cashEntryById(id)!!
        val asked = before.copy(amount = 650.5, category = "Rent", note = "Test note", timestamp = 2_000L,
            paymentMethod = PaymentMethod.BANK, paymentRef = "TID-0001", isIncome = true, photoPath = null)
        assertNull(dao.editCashEntryAudited(asked))
        val after = dao.cashEntryById(id)!!
        assertEquals(650.5, after.amount, 0.0)
        assertEquals("Rent", after.category)
        assertEquals("Test note", after.note)
        assertEquals("the day and time are corrected", 2_000L, after.timestamp)
        assertEquals(PaymentMethod.BANK, after.paymentMethod)
        assertEquals("TID-0001", after.paymentRef)
        assertFalse("never which way it went", after.isIncome)
        assertEquals("never its picture", "bills/test.jpg", after.photoPath)
        assertEquals(before.uid, after.uid)
        val logged = dao.auditInOrder().last()
        assertEquals(AuditAction.CASH_EDITED, logged.action)
        assertEquals(id, logged.refId)
        assertEquals("650.50", logged.detail)
    }

    @Test
    fun `a line a ticket wrote, or one in the bin, is not corrected here`() = runBlocking {
        val ticket = dao.insertCashEntry(CashEntry(amount = 300.0, isIncome = true, category = CashCategory.SALE, saleId = 9L))
        assertEquals(CashEdit.LINKED, dao.editCashEntryAudited(dao.cashEntryById(ticket)!!.copy(amount = 1.0)))
        assertEquals(300.0, dao.cashEntryById(ticket)!!.amount, 0.0)
        val own = dao.insertCashEntryAudited(CashEntry(amount = 80.0, isIncome = false, category = "Tea"))
        assertTrue(dao.softDeleteOwnCashEntry(own))
        assertEquals(CashEdit.GONE, dao.editCashEntryAudited(dao.cashEntryById(own)!!.copy(amount = 1.0)))
        assertTrue(dao.auditInOrder().none { it.action == AuditAction.CASH_EDITED })
    }

    @Test
    fun `a cashier corrects nothing alone, with a manager's PIN it goes`() = runBlocking {
        val id = dao.insertCashEntryAudited(CashEntry(amount = 120.0, isIncome = true, category = "Other"))
        val party = dao.insertParty(Party(name = "Test Customer", isCustomer = true))
        val cheque = dao.insertCheque(Cheque(partyId = party, amount = 2_000.0, isReceived = true, dueDate = 5_000L))
        cashier()
        try { dao.editCashEntryAudited(dao.cashEntryById(id)!!.copy(amount = 1.0)); fail("a cashier corrected a line alone") }
        catch (e: CounterRefused) { assertEquals(CounterRefused.Why.NOT_ALLOWED, e.why) }
        try { dao.editPendingCheque(dao.getCheque(cheque)!!.copy(dueDate = 1L)); fail("a cashier corrected a cheque alone") }
        catch (e: CounterRefused) { assertEquals(CounterRefused.Why.NOT_ALLOWED, e.why) }
        assertEquals(120.0, dao.cashEntryById(id)!!.amount, 0.0)
        assertNull(dao.editCashEntryAudited(dao.cashEntryById(id)!!.copy(amount = 125.0), allowedBy = "Test Manager"))
        assertTrue(dao.editPendingCheque(dao.getCheque(cheque)!!.copy(dueDate = 6_000L), allowedBy = "Test Manager"))
        assertEquals(125.0, dao.cashEntryById(id)!!.amount, 0.0)
        assertEquals(6_000L, dao.getCheque(cheque)!!.dueDate)
    }

    @Test
    fun `a cheque's day is corrected only while it is pending, its settlement kept`() = runBlocking {
        val party = dao.insertParty(Party(name = "Test Supplier", isCustomer = false))
        val other = dao.insertParty(Party(name = "Test Customer", isCustomer = true))
        val id = dao.insertCheque(Cheque(partyId = party, amount = 750.0, isReceived = false, dueDate = 10_000L, chequeNumber = "000111"))
        val stored = dao.getCheque(id)!!
        assertTrue(dao.editPendingCheque(stored.copy(partyId = other, amount = 800.0, isReceived = true, dueDate = 20_000L,
            chequeNumber = "000222", bankName = "Test Bank", note = "Test", status = ChequeStatus.CLEARED, createdAt = 1L)))
        val after = dao.getCheque(id)!!
        assertEquals(20_000L, after.dueDate)
        assertEquals(other, after.partyId)
        assertEquals(800.0, after.amount, 0.0)
        assertEquals("000222", after.chequeNumber)
        assertEquals("the status is never the form's", ChequeStatus.PENDING, after.status)
        assertEquals(stored.createdAt, after.createdAt)
        assertEquals(stored.uid, after.uid)
        assertEquals(AuditAction.CHEQUE_EDITED, dao.auditInOrder().last().action)

        assertTrue(dao.settleCheque(id, ChequeStatus.BOUNCED))
        assertFalse("a bounced cheque's day stays", dao.editPendingCheque(dao.getCheque(id)!!.copy(dueDate = 30_000L)))
        assertEquals(20_000L, dao.getCheque(id)!!.dueDate)
        assertEquals(1, dao.auditInOrder().count { it.action == AuditAction.CHEQUE_EDITED })
    }
}
