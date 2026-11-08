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
 * "What was sold" by one rule (Marhala 3, 25 Oct 2026, the audit's H12): the
 * sales report counted the counter's tickets alone, so a shop that sells on
 * its khata read "No sales" there while Sale Insights showed its month. Now
 * both count the same sales: every khata "I gave" to a customer (not a
 * Qarz-e-Hasna loan, not a return's refund, not a supplier's) and every
 * counter ticket, each once. Made-up names.
 */
class SalesRuleTest {

    private lateinit var dir: File
    private lateinit var db: KhataDatabase
    private val dao get() = db.khataDao()
    private val now = System.currentTimeMillis()

    @Before
    fun open() {
        dir = Files.createTempDirectory("rk-sales-rule").toFile()
        db = DesktopDatabase.open(dir.resolve("khata.db"))
    }

    @After
    fun close() {
        db.close()
        dir.deleteRecursively()
    }

    private suspend fun gave(party: Long, amount: Double, items: List<EntryItem> = emptyList(), qarz: Boolean = false, at: Long = now, by: String? = null): Long =
        dao.insertEntryWithItems(LedgerEntry(partyId = party, amount = amount, isGiven = true, entryNumber = "", timestamp = at, isQarzeHasna = qarz, createdBy = by), items)

    private fun item(name: String, qty: Double?, rate: Double?, bonus: Boolean = false, product: Long? = null, unit: String? = "bag") =
        EntryItem(entryId = 0, itemName = name, quantity = qty, unit = unit, rate = rate, isBonus = bonus, productId = product)

    @Test
    fun `Sale Insights' best sellers count the counter's walk-ins too`() = runBlocking {
        // 25 Oct 2026, the audit: top products read the khata alone, so a counter shop's best seller was missing.
        val aslam = dao.insertParty(Party(name = "Aslam"))
        gave(aslam, 6_000.0, listOf(item("Urea", 2.0, 3_000.0)))
        dao.postSale(Sale(timestamp = now, subtotal = 0.0, grandTotal = 0.0, paidTotal = 0.0),
            listOf(SaleItem(saleId = 0, itemName = "Rice", quantity = 4.0, unit = "bag", rate = 1_650.0)),
            listOf(SalePayment(saleId = 0, method = PaymentMethod.CASH, amount = 6_600.0)))
        // A customer's ticket is his khata entry's goods: counted there, once.
        dao.postSale(Sale(partyId = aslam, customerName = "Aslam", timestamp = now, subtotal = 0.0, grandTotal = 0.0, paidTotal = 0.0),
            listOf(SaleItem(saleId = 0, itemName = "Urea", quantity = 1.0, unit = "bag", rate = 3_000.0)), emptyList())
        val top = dao.topProductsBetween(now - 60_000, now + 60_000, 5)
        assertEquals(listOf("Rice", "Urea"), top.map { it.name })
        assertEquals(4.0, top[0].qty, 0.001)
        assertEquals("two khata lines, the ticket's among them", 3.0, top[1].qty, 0.001)
        assertEquals(2, top[1].lines)
    }

    @Test
    fun `the sales report and Sale Insights count the same sales`() = runBlocking {
        val aslam = dao.insertParty(Party(name = "Aslam"))
        val bilal = dao.insertParty(Party(name = "Bilal"))
        val kashif = dao.insertParty(Party(name = "Kashif"))
        val supplier = dao.insertParty(Party(name = "Seeds Co.", isCustomer = false))
        gave(aslam, 10_000.0)                                                     // no goods written
        gave(bilal, 6_000.0, listOf(item("Urea", 2.0, 3_000.0)), by = "Nadeem")  // goods at their value, written by a cashier
        gave(kashif, 5_000.0, listOf(item("DAP", 1.0, 3_500.0), item("Seed", 2.0, 1_000.0)))  // 500 off 5,500
        gave(kashif, 2_000.0, qarz = true)                                        // a loan, not a sale
        gave(supplier, 7_000.0)                                                   // paying a supplier, not a sale
        // A walk-in ticket and a customer's ticket: the customer's khata entry is the ticket's, counted once.
        dao.postSale(Sale(timestamp = now, subtotal = 0.0, grandTotal = 0.0, paidTotal = 0.0),
            listOf(SaleItem(saleId = 0, itemName = "Rice", quantity = 1.0, rate = 1_650.0)),
            listOf(SalePayment(saleId = 0, method = PaymentMethod.CASH, amount = 1_650.0)))
        dao.postSale(Sale(partyId = aslam, customerName = "Aslam", timestamp = now, subtotal = 0.0, grandTotal = 0.0, paidTotal = 0.0),
            listOf(SaleItem(saleId = 0, itemName = "Confidor", quantity = 2.0, rate = 1_850.0)), emptyList())

        val from = now - 60_000
        val to = now + 60_000
        val r = SalesReport.compute(dao, from, to)
        val insights = dao.salesTotalBetween(from, to)
        assertEquals(10_000.0 + 6_000 + 5_000 + 1_650 + 3_700, insights, 0.001)
        assertEquals("the report counts what Insights counts", insights, r.revenue, 0.02)
        assertEquals("three khata sales and two tickets", 5, r.saleCount)

        val byItem = r.byItem.associateBy { it.key }
        assertEquals(10_000.0, byItem.getValue("").revenue, 0.001)
        assertEquals(6_000.0, byItem.getValue("Urea").revenue, 0.001)
        assertEquals(2.0, byItem.getValue("Urea").quantity, 0.001)
        // 5,000 for 5,500 of goods: each line takes its share.
        assertEquals(3_500.0 * 5_000 / 5_500, byItem.getValue("DAP").revenue, 0.01)
        assertEquals(2_000.0 * 5_000 / 5_500, byItem.getValue("Seed").revenue, 0.01)
        assertTrue("the customer's ticket keeps its own line", "Confidor" in byItem)
        // Who wrote a khata sale is its own (lesson 21: never the owner by default).
        assertEquals(6_000.0, r.byCashier.associateBy { it.key }.getValue("Nadeem").revenue, 0.001)
    }

    @Test
    fun `a lone line with no rate takes the sale, and money no line can take is no item's`() = runBlocking {
        val aslam = dao.insertParty(Party(name = "Aslam"))
        gave(aslam, 8_000.0, listOf(item("Wheat seed", 4.0, null), item("Bonus pack", 1.0, null, bonus = true)))
        gave(aslam, 1_500.0, listOf(item("Spray", 1.0, 1_000.0)))
        // Two lines and no rate on either: the money cannot be told apart, so it is a line of no item.
        gave(aslam, 2_400.0, listOf(item("Gloves", 2.0, null), item("Mask", 3.0, null)))
        val r = SalesReport.compute(dao, now - 60_000, now + 60_000)
        val byItem = r.byItem.associateBy { it.key }
        assertEquals(8_000.0, byItem.getValue("Wheat seed").revenue, 0.001)
        assertEquals(4.0, byItem.getValue("Wheat seed").quantity, 0.001)
        assertEquals("free goods count their quantity at nothing", 0.0, byItem.getValue("Bonus pack").revenue, 0.001)
        assertEquals(1_000.0, byItem.getValue("Spray").revenue, 0.001)
        assertEquals(3.0, byItem.getValue("Mask").quantity, 0.001)
        assertEquals(0.0, byItem.getValue("Mask").revenue, 0.001)
        assertEquals("the rest of the 1,500 and the 2,400 are no item's", 500.0 + 2_400.0, byItem.getValue("").revenue, 0.001)
        assertEquals(11_900.0, r.revenue, 0.001)
        assertEquals(dao.salesTotalBetween(now - 60_000, now + 60_000), r.revenue, 0.001)
    }

    @Test
    fun `a khata line's cost is its product's, and a sale with no goods has none`() = runBlocking {
        val aslam = dao.insertParty(Party(name = "Aslam"))
        val urea = dao.insertProduct(Product(name = "Urea", nameKey = ProductName.key("Urea"), normalisedName = ProductName.normalised("Urea"),
            defaultUnit = "bag", costPrice = 4_200.0))
        gave(aslam, 9_000.0, listOf(item("Urea", 2.0, 4_500.0, product = urea)))
        val withCost = SalesReport.compute(dao, now - 60_000, now + 60_000)
        assertEquals(8_400.0, withCost.cost, 0.001)
        assertEquals(600.0, withCost.grossProfit, 0.001)
        assertTrue(withCost.costComplete)

        gave(aslam, 3_000.0)
        val mixed = SalesReport.compute(dao, now - 60_000, now + 60_000)
        // A sale whose goods were not written has no cost to know: no net is claimed for the window.
        assertNull(mixed.netOrNull)
        assertEquals(9_000.0, mixed.costKnownRevenue, 0.001)
    }
}
