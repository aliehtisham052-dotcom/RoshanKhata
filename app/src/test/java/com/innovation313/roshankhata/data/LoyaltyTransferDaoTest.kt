package com.innovation313.roshankhata.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Loyalty and stock between shops against real databases (21 Oct 2026):
 * points earned on a customer's ticket, spent as its discount, given back by
 * a void, taken back by a return and returned by the return's void; and goods
 * sent from one shop's book to another's, products matched or added, and the
 * first half taken back when the second cannot be written.
 */
@RunWith(AndroidJUnit4::class)
class LoyaltyTransferDaoTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: KhataDatabase
    private lateinit var dao: KhataDao

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

    private suspend fun ticket(partyId: Long, rate: Double, points: Double = 0.0, discount: Double = 0.0): Long = dao.postSale(
        Sale(partyId = partyId, customerName = "Ali", subtotal = 0.0, discountAmount = discount, grandTotal = 0.0, paidTotal = 0.0),
        listOf(SaleItem(saleId = 0, itemName = "Seed", quantity = 1.0, rate = rate)),
        listOf(SalePayment(saleId = 0, method = PaymentMethod.CASH, amount = LineMath.round(rate - discount))),
        pointsUsed = points
    )

    @Test
    fun pointsAreEarnedSpentGivenBackAndTakenBack() = runBlocking {
        dao.setSetting(BookSettings.LOYALTY_POINTS_PER_100, "2")
        dao.setSetting(BookSettings.LOYALTY_POINT_VALUE, "1")
        val ali = dao.insertParty(Party(name = "Ali"))
        val first = ticket(ali, 5_000.0)
        assertEquals(100.0, dao.loyaltyBalance(ali), 0.0)

        // Spending 60 points: 60 off the ticket, and the ticket still earns on what it came to.
        val second = ticket(ali, 1_000.0, points = 60.0, discount = 60.0)
        assertEquals(940.0, dao.getSale(second)!!.grandTotal, 0.0)
        assertEquals(100.0 - 60.0 + 18.8, dao.loyaltyBalance(ali), 0.001)
        val receipt = Loyalty.onReceipt(dao, dao.getSale(second)!!)!!
        assertEquals(60.0, receipt.used, 0.0); assertEquals(18.8, receipt.earned, 0.0); assertEquals(60.0, receipt.usedValue, 0.0)

        // More points than the customer holds: refused, nothing written.
        try { ticket(ali, 1_000.0, points = 500.0, discount = 500.0); fail("spent points it did not have") } catch (e: IllegalArgumentException) { }
        // Points not inside the discount: refused.
        try { ticket(ali, 1_000.0, points = 10.0, discount = 0.0); fail("points spent without their discount") } catch (e: IllegalArgumentException) { }

        // The void gives the spent points back and takes the earned ones away.
        dao.voidSale(second, "wrong customer")
        assertEquals(100.0, dao.loyaltyBalance(ali), 0.001)

        // A return of half the first ticket takes back what that half earned (50); its void returns them.
        val item = dao.saleItems(first).single()
        val ret = dao.postReturn(
            SaleReturn(saleId = first, partyId = ali, customerName = "Ali", total = 0.0, refundMethod = SaleReturn.ACCOUNT, reason = "Wrong seed"),
            listOf(SaleReturnItem(returnId = 0, saleItemId = item.id, itemName = "Seed", quantity = 0.5, rate = 5_000.0))
        )
        assertEquals(50.0, dao.loyaltyBalance(ali), 0.001)
        dao.voidReturn(ret, "kept after all")
        assertEquals(100.0, dao.loyaltyBalance(ali), 0.001)
    }

    @Test
    fun withLoyaltyOffNothingIsEarnedAndAWalkInCannotSpend() = runBlocking {
        val ali = dao.insertParty(Party(name = "Ali"))
        ticket(ali, 5_000.0)
        assertEquals(0.0, dao.loyaltyBalance(ali), 0.0)
        try {
            dao.postSale(
                Sale(subtotal = 0.0, discountAmount = 10.0, grandTotal = 0.0, paidTotal = 0.0),
                listOf(SaleItem(saleId = 0, itemName = "Seed", quantity = 1.0, rate = 100.0)),
                listOf(SalePayment(saleId = 0, method = PaymentMethod.CASH, amount = 90.0)),
                pointsUsed = 10.0
            )
            fail("a walk-in spent points")
        } catch (e: IllegalArgumentException) { }
    }

    @Test
    fun goodsGoFromOneShopsBookToTheOthers() = runBlocking {
        val other = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java).build()
        try {
            val there = other.khataDao()
            val dap = dao.insertProduct(Product(name = "DAP", nameKey = "dap", normalisedName = "dap", defaultUnit = "bag", barcode = "8901234567890", salePrice = 3_500.0))
            val urea = dao.insertProduct(Product(name = "Urea", nameKey = "urea", normalisedName = "urea", defaultUnit = "bag", salePrice = 4_800.0))
            // The other shop knows DAP by its barcode under another name, and has no Urea.
            val dapThere = there.insertProduct(Product(name = "D.A.P", nameKey = "d.a.p", normalisedName = "dap", defaultUnit = "bag", barcode = "8901234567890"))
            val number = StockTransfer.send(dao, there, listOf(StockTransfer.Line(dao.productById(dap)!!, 10.0), StockTransfer.Line(dao.productById(urea)!!, 4.0)), "Main", "Branch")
            assertTrue(number.startsWith("TR-"))
            val here = Stock.of(dao).associateBy { it.productId }
            assertEquals(-10.0, here.getValue(dap).onHand!!, 0.0)
            assertEquals(-4.0, here.getValue(urea).onHand!!, 0.0)
            val inThere = Stock.of(there).associateBy { it.name }
            assertEquals(10.0, inThere.getValue("D.A.P").onHand!!, 0.0)
            val ureaThere = there.productByKey("urea")
            assertNotNull("Urea was added there", ureaThere)
            assertEquals(4_800.0, ureaThere!!.salePrice!!, 0.0)
            assertEquals(4.0, inThere.getValue("Urea").onHand!!, 0.0)
            assertEquals(dapThere, there.productByBarcode("8901234567890")!!.id)
            assertTrue(dao.auditInOrder().any { it.action == AuditAction.TRANSFER_OUT && it.detail!!.startsWith(number) })
            assertTrue(there.auditInOrder().any { it.action == AuditAction.TRANSFER_IN && it.detail!!.startsWith(number) })
        } finally { other.close() }
    }

    @Test
    fun whenThisBookCannotSendTheOtherTakesTheGoodsBackOut() = runBlocking {
        val other = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java).build()
        try {
            val there = other.khataDao()
            // A product this book does not have (removed from the list since): sending it out here fails.
            val ghost = Product(id = 999, name = "Ghost", nameKey = "ghost", normalisedName = "ghost", defaultUnit = "bag")
            try { StockTransfer.send(dao, there, listOf(StockTransfer.Line(ghost, 3.0)), "Main", "Branch"); fail("sent a product this book does not have") }
            catch (e: IllegalArgumentException) { }
            // The other book received it, then took it back out: nothing on its shelf, both rows kept.
            assertEquals(0.0, Stock.of(there).single { it.name == "Ghost" }.onHand!!, 0.0)
            assertEquals(2, there.auditInOrder().count { it.action == AuditAction.TRANSFER_IN || it.action == AuditAction.TRANSFER_OUT })
        } finally { other.close() }
    }
}
