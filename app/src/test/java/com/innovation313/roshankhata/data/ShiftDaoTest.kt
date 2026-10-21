package com.innovation313.roshankhata.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Shifts against a real database (21 Oct 2026): a cashier's drawer should
 * hold the float plus their own cash sales less their own cash refunds —
 * never the owner's sales, never a card payment, never a voided ticket; a
 * short drawer is not closed without its reason; one open shift a person.
 * And a ticket rung up while a cashier is signed in carries their name.
 */
@RunWith(AndroidJUnit4::class)
class ShiftDaoTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: KhataDatabase
    private lateinit var dao: KhataDao
    private val hour = 60L * 60 * 1000
    private val imran = Staff(id = 7, name = "Imran", monthlySalary = 0.0, role = Role.CASHIER)

    @Before
    fun open(): Unit = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java).build()
        dao = db.khataDao()
    }

    @After
    fun close() {
        db.close()
        Access.resetForTest()
    }

    private suspend fun walkIn(by: String?, amount: Double, method: String = PaymentMethod.CASH): Long = dao.postSale(
        Sale(subtotal = 0.0, grandTotal = 0.0, paidTotal = 0.0, createdBy = by),
        listOf(SaleItem(saleId = 0, itemName = "Soap", quantity = 1.0, rate = amount)),
        listOf(SalePayment(saleId = 0, method = method, amount = amount))
    )

    @Test
    fun theDrawerIsTheFloatPlusTheirOwnCashLessTheirOwnCashRefunds() = runBlocking {
        val shift = dao.openShift(Shift(user = "Imran", openingFloat = 2_000.0, openedAt = System.currentTimeMillis() - hour))
        val first = walkIn("Imran", 500.0)
        walkIn("Imran", 300.0, PaymentMethod.BANK)       // a card: never in the drawer
        walkIn(null, 900.0)                               // the owner's own sale: not Imran's drawer
        val voided = walkIn("Imran", 1_000.0)
        dao.voidSale(voided, "rang twice")                // a voided ticket brought no cash
        val item = dao.saleItems(first).single()
        dao.postReturn(
            SaleReturn(saleId = first, total = 0.0, refundMethod = PaymentMethod.CASH, refundAmount = 500.0, reason = "Torn", createdBy = "Imran"),
            listOf(SaleReturnItem(returnId = 0, saleItemId = item.id, itemName = "Soap", quantity = 1.0, rate = 500.0))
        )
        val s = dao.getShift(shift)!!
        assertEquals(2_000.0 + 500.0 - 500.0, ShiftMath.expected(dao, s), 0.0)

        // A drawer 100 short is refused without its reason, and closed with one.
        try { dao.closeShift(shift, 1_900.0, null); fail("a short drawer closed without a reason") } catch (e: IllegalArgumentException) { }
        val closed = dao.closeShift(shift, 1_900.0, "Change given wrong")
        assertEquals(-100.0, closed.difference!!, 0.0)
        assertEquals(2_000.0, closed.expected!!, 0.0)
        assertNull(dao.openShiftOf("Imran"))
        val actions = dao.auditInOrder().map { it.action }
        assertTrue(actions.containsAll(listOf(AuditAction.SHIFT_OPENED, AuditAction.SHIFT_CLOSED)))
        assertTrue("the log still holds", AuditChain.verify(dao.auditInOrder()).intact)
    }

    @Test
    fun onePersonHasOneOpenShiftAndTheHoursAreSummedFromTheClosedOnes() = runBlocking {
        val now = System.currentTimeMillis()
        dao.openShift(Shift(user = null, openingFloat = 0.0, openedAt = now - 3 * hour))
        try { dao.openShift(Shift(user = null, openingFloat = 0.0)); fail("a second open shift") } catch (e: IllegalArgumentException) { }
        val owner = dao.openShiftOf(null)!!
        dao.closeShift(owner.id, 0.0, null, now = now)
        val hours = ShiftMath.hours(dao.shiftsBetween(now - 24 * hour, now + 1))
        assertEquals(3.0, hours[null]!!, 0.01)
    }

    @Test
    fun aTicketRungUpByASignedInCashierCarriesTheirName() = runBlocking {
        Access.signIn(imran)
        val draft = CounterDraft()
        draft.addFree("Soap")
        draft.lines.single().rate = "120"
        draft.payRestInCash()
        val id = Counter.post(dao, draft)
        assertEquals("Imran", dao.getSale(id)!!.createdBy)
        assertEquals(120.0, dao.cashTakenBy("Imran", 0L, Long.MAX_VALUE), 0.0)
        assertEquals(0.0, dao.cashTakenBy(null, 0L, Long.MAX_VALUE), 0.0)
    }
}
