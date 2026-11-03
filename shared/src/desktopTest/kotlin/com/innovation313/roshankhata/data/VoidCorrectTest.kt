package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * "Sale theek karein" (3 Nov 2026): a posted ticket voided and its goods back on the counter as a new ticket —
 * the same customer and lines, no held id, no payments — and once it is corrected and completed, the voided one
 * is still in the book with its reason. Made-up figures only.
 */
class VoidCorrectTest {

    private lateinit var dir: File
    private lateinit var db: KhataDatabase
    private val dao get() = db.khataDao()

    @Before
    fun open() {
        dir = Files.createTempDirectory("rk-void-correct").toFile()
        db = DesktopDatabase.open(dir.resolve("khata.db"))
    }

    @After
    fun close() {
        db.close()
        dir.deleteRecursively()
    }

    @Test
    fun `a voided ticket comes back to be corrected as a new one`() = runBlocking {
        val aslam = dao.insertParty(Party(name = "Aslam"))
        val first = CounterDraft()
        first.setParty(aslam, "Aslam")
        first.addFree("Urea").apply { rate = "1000"; qty = "3" }
        first.addPayment(PaymentMethod.CASH, 1_000.0)
        val saleId = Counter.post(dao, first)
        val number = dao.getSale(saleId)!!.saleNumber

        assertTrue(dao.voidSale(saleId, "3 bags typed, 5 sold"))
        val again = CounterDraft()
        again.reRing(dao.saleWithLines(saleId)!!)
        assertEquals(aslam, again.partyId)
        assertEquals(listOf("Urea"), again.lines.map { it.name })
        assertEquals("3", again.lines.single().qty)
        assertNull("a new ticket, not the voided one", again.heldId)
        assertTrue("what was paid went back with the void", again.payments.isEmpty())

        // Corrected and completed: a new number; the voided ticket stays as voided, with its reason.
        again.lines.single().qty = "5"
        again.addPayment(PaymentMethod.CASH, 5_000.0)
        val fixedId = Counter.post(dao, again)
        val fixed = dao.getSale(fixedId)!!
        assertNotEquals(number, fixed.saleNumber)
        assertEquals(5_000.0, fixed.grandTotal, 0.001)
        val voided = dao.getSale(saleId)!!
        assertEquals(SaleStatus.VOID, voided.status)
        assertEquals("3 bags typed, 5 sold", voided.voidReason)
    }

    @Test
    fun `an exchange keeps a cash refund in the drawer as the new ticket's first payment`() {
        val cash = SaleReturn(returnNumber = "R-000012", total = 400.0, refundMethod = PaymentMethod.CASH, refundAmount = 400.0, reason = "Wrong size")
        val walkIn = CounterDraft()
        walkIn.startExchange(cash)
        assertNull(walkIn.partyId)
        val p = walkIn.payments.single()
        assertEquals(PaymentMethod.CASH, p.method)
        assertEquals(400.0, p.amountValue, 0.001)
        assertEquals("the receipt names the return", "R-000012", p.reference)
        // New goods worth 1,000: only the difference is still to collect.
        walkIn.addFree("Shoes 9").rate = "1000"
        assertEquals(600.0, walkIn.remaining, 0.001)

        // Kept on a customer's account: the same customer, nothing paid; the khata nets the two.
        val onAccount = SaleReturn(returnNumber = "R-000013", partyId = 7, customerName = "Aslam", total = 400.0, reason = "Wrong size")
        val customer = CounterDraft()
        customer.startExchange(onAccount)
        assertEquals(7L, customer.partyId)
        assertTrue(customer.payments.isEmpty())
    }
}
