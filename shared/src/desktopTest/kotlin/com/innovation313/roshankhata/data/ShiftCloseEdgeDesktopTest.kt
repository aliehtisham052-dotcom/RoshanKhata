package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * D8 (BC-297, 25 Nov 2026): a stretch "up to the close" keeps what was written at the closing moment, in the shift's
 * drawer as in the day's ([DayCloseMath.drawerByTheBooks], (from, to]). A cashier's cash line and his ticket written in
 * the very millisecond his shift closed were in the day's drawer and in no shift. CI is the first run of this class
 * (Room); the span itself is held in FormulaDisagreementsTest. Made-up names and figures.
 */
class ShiftCloseEdgeDesktopTest {

    private lateinit var dir: File
    private lateinit var db: KhataDatabase
    private val dao get() = db.khataDao()
    private val cashier = Staff(id = 7, name = "Cashier A", monthlySalary = 0.0, role = Role.CASHIER)

    @Before
    fun open() {
        Access.resetForTest()
        dir = Files.createTempDirectory("rk-shift-edge").toFile()
        db = DesktopDatabase.open(dir.resolve("khata.db"))
    }

    @After
    fun close() {
        Access.resetForTest()
        db.close()
        dir.deleteRecursively()
    }

    @Test
    fun `a cash line and a ticket written at the closing millisecond are in the shift's drawer`() = runBlocking {
        val opened = System.currentTimeMillis() - 60_000
        val closing = opened + 30_000
        val shift = dao.openShift(Shift(user = "Cashier A", openingFloat = 0.0, openedAt = opened))
        Access.signIn(cashier)
        dao.insertCashEntryAudited(CashEntry(amount = 200.0, isIncome = true, category = "Test", timestamp = closing))
        dao.postSale(
            Sale(timestamp = closing, subtotal = 0.0, grandTotal = 0.0, paidTotal = 0.0, createdBy = "Cashier A"),
            listOf(SaleItem(saleId = 0, itemName = "Soap", quantity = 1.0, rate = 400.0)),
            listOf(SalePayment(saleId = 0, method = PaymentMethod.CASH, amount = 400.0))
        )
        Access.signOut()
        val open = dao.openShiftOf("Cashier A") ?: error("no open shift")
        val d = ShiftMath.drawer(dao, open, closing)
        assertEquals(200.0, d.handIn, 0.001)
        assertEquals(400.0, d.taken, 0.001)
        // The day's drawer for the same stretch counts the same cash line ((from, to]).
        assertEquals(200.0, dao.cashbookCashBetween(true, opened - 1, closing), 0.001)
        assertEquals(shift, open.id)
    }
}
