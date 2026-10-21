package com.innovation313.roshankhata.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * First expiry first out against a real database (21 Oct 2026): a ticket's
 * line is written once per batch, soonest expiry first, with its money exact;
 * an expired lot is refused until it is written off; packs and the average
 * cost reach the shelf's figure and the line's cost.
 */
@RunWith(AndroidJUnit4::class)
class FefoDaoTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: KhataDatabase
    private lateinit var dao: KhataDao
    private var engro = 0L
    private val day = 24L * 60 * 60 * 1000
    private val now = System.currentTimeMillis()

    @Before
    fun open(): Unit = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java).build()
        dao = db.khataDao()
        engro = dao.insertParty(Party(name = "Engro", isCustomer = false))
    }

    @After
    fun close() = db.close()

    private suspend fun product(name: String, price: Double, pack: Pair<String, Double>? = null) = dao.insertProduct(
        Product(name = name, nameKey = ProductName.key(name), normalisedName = ProductName.normalised(name), defaultUnit = "bottle",
            salePrice = price, packUnit = pack?.first, packSize = pack?.second)
    )

    private suspend fun bill(productId: Long, name: String, vararg lots: Triple<String, Long?, Double>, unit: String = "bottle", rate: Double = 500.0) {
        dao.insertSupplierBill(null, SupplierBill(partyId = engro, billDate = now - 30 * day, totalAmount = 0.0),
            lots.map { (batch, expiry, qty) -> BillItem(billId = 0, productName = name, batchNumber = batch, expiryDate = expiry, quantity = qty, unit = unit, rate = rate, productId = productId) })
    }

    private suspend fun sell(productId: Long, qty: Double, unit: String? = null): Long {
        val p = dao.productById(productId)!!
        val draft = CounterDraft()
        val line = draft.add(p, qty = qty)
        if (unit != null && unit != line.unit) line.switchUnit(draft.unitRate(p))
        draft.payRestInCash()
        return Counter.post(dao, draft)
    }

    @Test
    fun aLineIsWrittenOncePerBatchSoonestFirst() = runBlocking {
        val confidor = product("Confidor", 900.0)
        bill(confidor, "Confidor", Triple("LATE", now + 200 * day, 10.0), Triple("SOON", now + 20 * day, 3.0))
        val id = sell(confidor, 5.0)
        val items = dao.saleItems(id)
        val soon = dao.batchesOfProduct(confidor).first { it.batchNumber == "SOON" }.id
        val late = dao.batchesOfProduct(confidor).first { it.batchNumber == "LATE" }.id
        assertEquals(listOf(soon to 3.0, late to 2.0), items.map { it.billItemId to it.quantity })
        assertEquals(4_500.0, LineMath.round(items.sumOf { it.lineTotal }), 0.0)
        assertEquals(4_500.0, dao.getSale(id)!!.grandTotal, 0.0)
        val lots = Fefo.lots(dao, dao.productById(confidor)!!)
        assertEquals(listOf(0.0, 8.0), lots.map { it.left })
        // The weighted average purchase cost is the line's cost.
        assertEquals(500.0, items.first().costRate!!, 0.0)
    }

    @Test
    fun anExpiredLotIsRefusedUntilItIsWrittenOff() = runBlocking {
        val karate = product("Karate", 700.0)
        bill(karate, "Karate", Triple("OLD", now - 5 * day, 2.0))
        try { sell(karate, 1.0); fail("an expired lot was sold") } catch (e: Counter.ExpiredStock) { assertEquals("Karate", e.item) }
        assertTrue("nothing was written", dao.walkInSoldPerProduct().isEmpty())
        val old = dao.batchesOfProduct(karate).single().id
        assertEquals(2.0, Fefo.writeOff(dao, old, "OLD")!!, 0.0)
        assertEquals(0.0, Fefo.lotOf(dao, old)!!.second.left, 0.0)
        assertEquals(0.0, Stock.of(dao).single { it.productId == karate }.onHand!!, 0.0)
        // With the lot gone, a sale is no longer of expired stock: it goes untagged (and shows the shelf short).
        val id = sell(karate, 1.0)
        assertEquals(null, dao.saleItems(id).single().billItemId)
    }

    @Test
    fun aPackSoldAndBoughtCountsInTheProductsOwnUnit() = runBlocking {
        val soap = product("Soap", 100.0, "carton" to 12.0)
        bill(soap, "Soap", Triple("S1", now + 60 * day, 2.0), unit = "carton", rate = 1_080.0)
        assertEquals(24.0, Stock.of(dao).single { it.productId == soap }.onHand!!, 0.0)
        val id = sell(soap, 1.0, unit = "carton")
        val line = dao.saleItems(id).single()
        assertEquals("carton", line.unit)
        assertEquals(1_200.0, line.rate, 0.0)               // no pack price: 12 x 100
        assertEquals(1_080.0, line.costRate!!, 0.0)         // 90 a piece x 12
        assertEquals(12.0, Stock.of(dao).single { it.productId == soap }.onHand!!, 0.0)
        assertEquals(12.0, Fefo.lots(dao, dao.productById(soap)!!).single().left, 0.0)
    }
}
