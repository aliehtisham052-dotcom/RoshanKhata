package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * BC-261 (24 Nov 2026, money, importance 10): a khata sale has ONE cost. Insights' profit took the purchases dated up to
 * the sale and the sales report all of them (10 bags bought at 60, 10 more at 80 AFTER the sale: Insights 60, the report
 * 70). Both now call [Cost.forSale]. Also BC-271: the unit check is [Cost.forUnit] / [Units.agree] on both sides (a pack is
 * its size times the own unit's cost; "KG" and its Urdu word are one unit; a rate in another unit is never used).
 * Invented products and figures; plain Kotlin, no database.
 */
class CostAtSaleTest {

    private val day = 24L * 60 * 60 * 1000
    private fun product(unit: String? = "bag", cost: Double? = null, packUnit: String? = null, packSize: Double? = null) =
        Product(id = 1, name = "Urea", nameKey = "urea", normalisedName = "urea", defaultUnit = unit, costPrice = cost,
            packUnit = packUnit, packSize = packSize)

    private fun bought(qty: Double, rate: Double, on: Long, unit: String? = "bag") = Cost.Dated(qty, unit, rate, on)

    // ---- the one rule ------------------------------------------------------------------------------------------

    @Test fun `ten at 60 before the sale and ten at 80 after it cost the sale 60`() {
        val purchases = listOf(bought(10.0, 80.0, 20 * day), bought(10.0, 60.0, 1 * day))   // newest first, as the DAO gives them
        assertEquals(60.0, Cost.forSale(product(), 10 * day, "bag", purchases)!!, 0.0)
        // the sale after both: the average of the two
        assertEquals(70.0, Cost.forSale(product(), 30 * day, "bag", purchases)!!, 0.0)
    }

    @Test fun `any order of the purchases gives the same cost`() {
        val a = listOf(bought(10.0, 60.0, 1 * day), bought(30.0, 100.0, 2 * day), bought(10.0, 900.0, 20 * day))
        assertEquals(90.0, Cost.forSale(product(), 5 * day, "bag", a)!!, 0.0)                    // (600 + 3000) / 40
        assertEquals(90.0, Cost.forSale(product(), 5 * day, "bag", a.reversed())!!, 0.0)
    }

    @Test fun `a purchase on the very day of the sale counts, one a moment later does not`() {
        val p = listOf(bought(10.0, 50.0, 10 * day), bought(10.0, 70.0, 10 * day + 1))
        assertEquals(50.0, Cost.forSale(product(), 10 * day, "bag", p)!!, 0.0)
    }

    @Test fun `before any purchase there is no cost, not the later purchase's`() {
        assertNull(Cost.forSale(product(), 5 * day, "bag", listOf(bought(10.0, 80.0, 20 * day))))
        assertNull(Cost.forSale(product(), 5 * day, "bag", emptyList()))
    }

    @Test fun `the owner's cost price comes first and is said per the line's unit`() {
        val p = product(unit = "kg", cost = 50.0, packUnit = "bag", packSize = 50.0)
        assertEquals(50.0, Cost.forSale(p, 5 * day, "kg", emptyList())!!, 0.0)
        assertEquals(2_500.0, Cost.forSale(p, 5 * day, "bag", emptyList())!!, 0.0)       // a bag is 50 kilos
        assertNull("a tola is not a kilo", Cost.forSale(p, 5 * day, "tola", emptyList()))
    }

    @Test fun `bought in bags and sold in kilos has no cost, however the unit is spelled`() {
        val bags = listOf(bought(10.0, 100.0, 1 * day, unit = "bag"))
        assertNull(Cost.forSale(product(unit = null), 5 * day, "kg", bags))
        assertNull(Cost.forSale(product(unit = "bag"), 5 * day, "kg", bags))
        val kilos = listOf(bought(10.0, 5.0, 1 * day, unit = "KG"))
        assertEquals(5.0, Cost.forSale(product(unit = null), 5 * day, "کلو", kilos)!!, 0.0)   // one unit in two spellings
    }

    @Test fun `a pack sold costs its size times the own unit's cost, bought as packs or pieces`() {
        val soap = product(unit = "piece", packUnit = "carton", packSize = 12.0)
        val mixed = listOf(bought(2.0, 120.0, 2 * day, unit = "carton"), bought(12.0, 10.0, 1 * day, unit = "piece"))
        // 24 + 12 pieces for 240 + 120: 10 a piece, 120 a carton
        assertEquals(10.0, Cost.forSale(soap, 5 * day, "piece", mixed)!!, 0.0)
        assertEquals(120.0, Cost.forSale(soap, 5 * day, "carton", mixed)!!, 0.0)
    }

    @Test fun `with no priced line to average the newest rate that can be said is used, still not a later one`() {
        val p = listOf(bought(0.0, 90.0, 20 * day), bought(0.0, 55.0, 3 * day), bought(0.0, 40.0, 1 * day))
        assertEquals(55.0, Cost.forSale(product(), 10 * day, "bag", p)!!, 0.0)
    }

    // ---- both screens call it ----------------------------------------------------------------------------------

    private fun saleRow(at: Long, unit: String? = "bag", productId: Long? = 1L) =
        KhataSaleRow(entryId = 1, timestamp = at, amount = 100.0, partyName = "Aslam", createdBy = null, itemName = "Urea", quantity = 1.0,
            unit = unit, rate = 100.0, isBonus = false, productId = productId, category = null)

    private fun reportCost(p: Product, at: Long, purchases: List<Cost.Dated>, unit: String? = "bag"): Double? = runBlocking {
        KhataSales.lines(listOf(saleRow(at, unit))) { KhataSales.Shelf(p, purchases) }.single { it.productId == 1L }.costRate
    }

    private fun insightsCost(p: Product, at: Long, purchases: List<Cost.Dated>, unit: String? = "bag", batch: Long? = null): Double? {
        val lines = purchases.mapIndexed { i, d -> Profit.PurchaseLine(i + 1L, 1L, d.qty, d.unit, d.rate, d.boughtAt) }
        val sale = Profit.SaleLine(1L, "Urea", 1.0, unit, 100.0, false, batch, at, true, costPrice = p.costPrice, productUnit = p.defaultUnit,
            packUnit = p.packUnit, packSize = p.packSize)
        return Profit.costFor(sale, lines.groupBy { it.productId!! })
    }

    @Test fun `Insights and the sales report cost the same khata sale at the same figure`() {
        val purchases = listOf(bought(10.0, 80.0, 20 * day), bought(10.0, 60.0, 1 * day))
        // before: Insights 60, the report 70
        assertEquals(60.0, insightsCost(product(), 10 * day, purchases)!!, 0.0)
        assertEquals(60.0, reportCost(product(), 10 * day, purchases)!!, 0.0)
        assertEquals(70.0, reportCost(product(), 30 * day, purchases)!!, 0.0)
        assertEquals(70.0, insightsCost(product(), 30 * day, purchases)!!, 0.0)
    }

    @Test fun `the two agree on units, packs, cost price and no purchase yet`() {
        val soap = product(unit = "piece", packUnit = "carton", packSize = 12.0)
        val soapBuys = listOf(bought(24.0, 10.0, 1 * day, unit = "piece"))
        for (unit in listOf("piece", "carton", "tola", null)) {
            assertEquals("unit $unit", insightsCost(soap, 5 * day, soapBuys, unit), reportCost(soap, 5 * day, soapBuys, unit))
        }
        assertEquals(120.0, reportCost(soap, 5 * day, soapBuys, "carton")!!, 0.0)
        val priced = product(unit = "kg", cost = 42.0)
        assertEquals(42.0, reportCost(priced, 5 * day, emptyList(), "kg")!!, 0.0)
        assertEquals(42.0, insightsCost(priced, 5 * day, emptyList(), "kg")!!, 0.0)
        assertNull(reportCost(priced, 5 * day, emptyList(), "bag"))
        assertNull(insightsCost(priced, 5 * day, emptyList(), "bag"))
        // nothing bought yet by the day of the sale
        val later = listOf(bought(10.0, 80.0, 20 * day))
        assertNull(reportCost(product(), 5 * day, later))
        assertNull(insightsCost(product(), 5 * day, later))
    }

    /**
     * BC-279 (25 Nov 2026): Insights alone costed a line on the batch the owner tagged (200), the sales report, the counter and
     * the shelf's value on the contract's average (130): the same sale, two profits. Now the tag changes nothing in the cost.
     */
    @Test fun `a batch the owner chose changes no cost, in Insights or in the report`() {
        val purchases = listOf(bought(10.0, 60.0, 1 * day), bought(10.0, 200.0, 2 * day))
        assertEquals(130.0, insightsCost(product(), 10 * day, purchases, batch = 2L)!!, 0.0)
        assertEquals(130.0, reportCost(product(), 10 * day, purchases)!!, 0.0)
        assertEquals(insightsCost(product(), 10 * day, purchases, batch = null), insightsCost(product(), 10 * day, purchases, batch = 2L))
        // with the owner's own cost price: that, tagged or not
        val priced = product(cost = 90.0)
        assertEquals(90.0, insightsCost(priced, 10 * day, purchases, batch = 2L)!!, 0.0)
        assertEquals(90.0, reportCost(priced, 10 * day, purchases)!!, 0.0)
        // a tagged line with no purchase by the sale's day has no cost (the batch's later rate is not it)
        assertNull(insightsCost(product(), 0, purchases, batch = 2L))
    }

    @Test fun `a tagged line in another unit is costed like an untagged one, never on the batch`() {
        val bags = listOf(bought(10.0, 100.0, 1 * day, unit = "bag"), bought(10.0, 5.0, 1 * day, unit = "kg"))
        for (unit in listOf("kg", "bag", "tola", null)) {
            assertEquals("unit $unit", insightsCost(product(unit = null), 5 * day, bags, unit, batch = null), insightsCost(product(unit = null), 5 * day, bags, unit, batch = 1L))
            assertEquals("unit $unit", reportCost(product(unit = null), 5 * day, bags, unit), insightsCost(product(unit = null), 5 * day, bags, unit, batch = 1L))
        }
    }

    // ---- the guard: the old second rule is gone ----------------------------------------------------------------

    private fun source(path: String): String {
        val root = generateSequence(File(".").absoluteFile) { it.parentFile }
            .map { File(it, "shared/src/jvmSharedMain/kotlin/com/innovation313/roshankhata/data") }.first { it.isDirectory }
        return File(root, path).readText().replace("\r\n", "\n")
    }

    @Test fun `both callers go through Cost forSale and neither keeps a cost rule of its own`() {
        val profit = source("Profit.kt")
        val report = source("SalesReport.kt")
        assertEquals(true, "Cost.forSale(" in profit)
        assertEquals(true, "Cost.forSale(" in report)
        // no private unit comparison, no purchasesById-free average of its own, no perUnit * base / qty
        assertEquals(false, "unitsAgree" in profit)
        assertEquals(false, "Cost.perUnit(dao" in report)
        assertEquals(false, "perUnit * base / qty" in report)
        // BC-279: the chosen batch's rate is no cost rule of Insights any more
        assertEquals(false, "purchasesById" in profit)
        assertEquals(false, "batch.rate" in profit)
    }
}
