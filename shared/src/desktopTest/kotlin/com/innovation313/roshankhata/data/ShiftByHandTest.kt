package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * A shift's drawer counts the cash its person moved by hand (17 Nov 2026, the audit's L108), on a real book: the khata
 * cash and cashbook cash a cashier wrote are in his drawer beside his tickets; the counter's own rows (a ticket's
 * payment, a return's refund, with or without a ticket) are counted once, by the ticket; another person's rows, the
 * owner's and rows with no writer are in no one's drawer; a supplier's goods on credit are no cash; the figure the close
 * keeps is the figure [ShiftMath.drawer] gives the screen. And the writer is filled where a row is written and kept by
 * an edit. Made-up names and figures.
 */
class ShiftByHandTest {

    private lateinit var dir: File
    private lateinit var db: KhataDatabase
    private val dao get() = db.khataDao()
    private val hour = 60L * 60 * 1000
    private val imran = Staff(id = 7, name = "Imran", monthlySalary = 0.0, role = Role.CASHIER)
    private val bilal = Staff(id = 8, name = "Bilal", monthlySalary = 0.0, role = Role.CASHIER)

    @Before
    fun open() {
        Access.resetForTest()
        dir = Files.createTempDirectory("rk-shift-hand").toFile()
        db = DesktopDatabase.open(dir.resolve("khata.db"))
    }

    @After
    fun close() {
        Access.resetForTest()
        db.close()
        dir.deleteRecursively()
    }

    private suspend fun entry(party: Long, amount: Double, given: Boolean, method: String? = PaymentMethod.CASH, kind: String = EntryKind.NORMAL) =
        dao.insertEntryWithItems(
            LedgerEntry(partyId = party, amount = amount, isGiven = given, entryNumber = "", paymentMethod = method, entryKind = kind),
            emptyList()
        )

    private suspend fun cash(amount: Double, income: Boolean, method: String? = null) =
        dao.insertCashEntryAudited(CashEntry(amount = amount, isIncome = income, category = "Test", paymentMethod = method))

    private suspend fun walkIn(by: String?, amount: Double): Long = dao.postSale(
        Sale(subtotal = 0.0, grandTotal = 0.0, paidTotal = 0.0, createdBy = by),
        listOf(SaleItem(saleId = 0, itemName = "Soap", quantity = 1.0, rate = amount)),
        listOf(SalePayment(saleId = 0, method = PaymentMethod.CASH, amount = amount))
    )

    @Test
    fun `a cashier's drawer holds the cash he moved by hand, and the counter's own rows once`() = runBlocking {
        val asif = dao.insertParty(Party(name = "Asif Traders", isCustomer = true))
        val mill = dao.insertParty(Party(name = "Noor Mills", isCustomer = false))
        // Before anyone signed in: the owner's own rows and one with no name — no one's drawer.
        cash(999.0, income = true)
        entry(asif, 888.0, given = false)

        val shift = dao.openShift(Shift(user = "Imran", openingFloat = 1_000.0, openedAt = System.currentTimeMillis() - hour))
        Access.signIn(imran)
        entry(asif, 300.0, given = false)                                 // cash received from a customer: in
        entry(asif, 250.0, given = false, method = PaymentMethod.BANK)    // a transfer: not in the drawer
        entry(mill, 100.0, given = true)                                  // cash paid to a supplier: out
        entry(mill, 700.0, given = false)                                 // goods on credit from a supplier: no cash
        entry(mill, 60.0, given = false, kind = EntryKind.MONEY)          // cash a supplier handed back: in
        cash(200.0, income = true)                                        // money in, no method = cash: in
        cash(500.0, income = true, method = PaymentMethod.BANK)           // a transfer: not in the drawer
        cash(50.0, income = false)                                        // money out in cash: out
        walkIn("Imran", 400.0)                                            // his ticket: taken, its cashbook row not again
        Access.signIn(bilal)
        cash(77.0, income = true)                                         // another cashier's: not Imran's
        entry(asif, 66.0, given = false)
        Access.signOut()
        // A return with no ticket, refunded in cash at Imran's counter: given back once, its cashbook row not again.
        dao.postReturn(
            SaleReturn(saleId = null, partyId = null, total = 0.0, refundMethod = PaymentMethod.CASH, refundAmount = 80.0, reason = "Torn", createdBy = "Imran"),
            listOf(SaleReturnItem(returnId = 0, saleItemId = null, itemName = "Soap", quantity = 1.0, rate = 80.0))
        )
        // And one to a customer, refunded by a cash "I gave" with no ticket: given back once, its khata row not again.
        dao.postReturn(
            SaleReturn(saleId = null, partyId = asif, total = 0.0, refundMethod = PaymentMethod.CASH, refundAmount = 40.0, reason = "Torn", createdBy = "Imran"),
            listOf(SaleReturnItem(returnId = 0, saleItemId = null, itemName = "Soap", quantity = 1.0, rate = 40.0))
        )

        val s = dao.getShift(shift)!!
        val d = ShiftMath.drawer(dao, s)
        assertEquals(400.0, d.taken, 0.001)
        assertEquals(120.0, d.back, 0.001)
        assertEquals("customer 300 + supplier's money 60 + cashbook 200", 560.0, d.handIn, 0.001)
        assertEquals("supplier 100 + cashbook 50", 150.0, d.handOut, 0.001)
        val expected = 1_000.0 + 400.0 - 120.0 + 560.0 - 150.0
        assertEquals(expected, ShiftMath.expected(dao, s), 0.001)
        // The close keeps the screen's figure.
        val closed = dao.closeShift(shift, expected, null)
        assertEquals(expected, closed.expected!!, 0.001)
        assertEquals(0.0, closed.difference!!, 0.001)
    }

    @Test
    fun `the owner's shift counts no row by hand, as before`() = runBlocking {
        val asif = dao.insertParty(Party(name = "Asif Traders", isCustomer = true))
        val shift = dao.openShift(Shift(user = null, openingFloat = 500.0, openedAt = System.currentTimeMillis() - hour))
        cash(200.0, income = true)
        entry(asif, 300.0, given = false)
        walkIn(null, 150.0)
        val d = ShiftMath.drawer(dao, dao.getShift(shift)!!)
        assertEquals(0.0, d.handIn, 0.0)
        assertEquals(0.0, d.handOut, 0.0)
        assertEquals(500.0 + 150.0, ShiftMath.expected(dao, dao.getShift(shift)!!), 0.001)
    }

    @Test
    fun `a row is written with the name signed in, a ticket's with its own, and an edit keeps it`() = runBlocking {
        val asif = dao.insertParty(Party(name = "Asif Traders", isCustomer = true))
        Access.signIn(imran)
        val id = entry(asif, 300.0, given = false)
        val cashId = cash(200.0, income = true)
        assertEquals("Imran", dao.getEntry(id)!!.createdBy)
        assertEquals("Imran", dao.allCashForBackup().single { it.id == cashId }.createdBy)
        // A ticket rung up by the owner while Imran is signed in (a held one resumed, say) keeps the ticket's writer.
        val sale = walkIn(null, 120.0)
        assertNull(dao.allCashForBackup().single { it.saleId == sale }.createdBy)
        // The owner corrects Imran's entry from a form that knows no writer: it stays Imran's.
        Access.signOut()
        dao.updateEntryWithItems(dao.getEntry(id)!!.copy(amount = 320.0, createdBy = null), emptyList())
        assertEquals("Imran", dao.getEntry(id)!!.createdBy)
        assertEquals(320.0, dao.getEntry(id)!!.amount, 0.0)
        // Signed out, a new row is the owner's: no name.
        assertNull(dao.getEntry(entry(asif, 10.0, given = false))!!.createdBy)
    }
}
