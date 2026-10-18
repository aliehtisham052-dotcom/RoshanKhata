package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the profit figure is allowed to claim.
 *
 * The cost of a sale line comes from one of two places or from nowhere; each
 * test pins one of those doors. The owner's own example runs first: forty
 * bags of Urea bought at 4,000 and sold at 4,200 is 8,000 of profit, and
 * nothing here may say otherwise.
 */
class ProfitTest {

    private val day = 24L * 60 * 60 * 1000
    private val names = mapOf(1L to "Urea", 2L to "DAP")

    private fun sale(
        productId: Long? = 1L, qty: Double? = 1.0, rate: Double? = 100.0, unit: String? = "bag",
        at: Long = 10 * day, bonus: Boolean = false, batch: Long? = null, given: Boolean = true
    ) = Profit.SaleLine(productId, names[productId], qty, unit, rate, bonus, batch, at, given)

    private fun buy(
        id: Long, productId: Long? = 1L, qty: Double, rate: Double, unit: String? = "bag", on: Long = 1 * day
    ) = Profit.PurchaseLine(id, productId, qty, unit, rate, on)

    @Test
    fun `the owner's example - 40 bags, bought 4000, sold 4200, profit 8000`() {
        val r = Profit.compute(
            sales = listOf(sale(qty = 40.0, rate = 4200.0)),
            purchases = listOf(buy(1, qty = 100.0, rate = 4000.0)),
            productNames = names, salesTotal = 168_000.0, expenses = 0.0
        )
        assertEquals(8_000.0, r.grossProfit, 0.001)
        assertEquals(1, r.countedLines)
        assertEquals(0, r.skippedLines)
        assertEquals(100, r.coveragePercent)
        assertEquals("Urea", r.products.single().name)
        assertEquals(8_000.0, r.products.single().profit, 0.001)
    }

    @Test
    fun `a chosen batch is costed at that batch's own rate, not the average`() {
        val purchases = listOf(
            buy(1, qty = 10.0, rate = 100.0),
            buy(2, qty = 10.0, rate = 200.0)
        )
        val cost = Profit.costFor(sale(batch = 2L), purchases.associateBy { it.id }, purchases.groupBy { it.productId!! })
        assertEquals(200.0, cost!!, 0.001)
    }

    @Test
    fun `without a batch the cost is the weighted average of purchases up to the sale date`() {
        val purchases = listOf(
            buy(1, qty = 10.0, rate = 100.0, on = 1 * day),
            buy(2, qty = 30.0, rate = 200.0, on = 2 * day)
        )
        // (10*100 + 30*200) / 40 = 175
        val cost = Profit.costFor(sale(at = 5 * day), purchases.associateBy { it.id }, purchases.groupBy { it.productId!! })
        assertEquals(175.0, cost!!, 0.001)
    }

    @Test
    fun `a purchase recorded after the sale cannot have been its cost`() {
        val purchases = listOf(
            buy(1, qty = 10.0, rate = 100.0, on = 1 * day),
            buy(2, qty = 10.0, rate = 900.0, on = 20 * day)
        )
        val cost = Profit.costFor(sale(at = 5 * day), purchases.associateBy { it.id }, purchases.groupBy { it.productId!! })
        assertEquals(100.0, cost!!, 0.001)
    }

    @Test
    fun `bought in bags and sold in kilos is not costed - no guess`() {
        val purchases = listOf(buy(1, qty = 10.0, rate = 100.0, unit = "bag"))
        val cost = Profit.costFor(sale(unit = "kg"), purchases.associateBy { it.id }, purchases.groupBy { it.productId!! })
        assertNull(cost)
    }

    @Test
    fun `a blank unit on either side disagrees with nothing`() {
        val purchases = listOf(buy(1, qty = 10.0, rate = 100.0, unit = null))
        val cost = Profit.costFor(sale(unit = "kg"), purchases.associateBy { it.id }, purchases.groupBy { it.productId!! })
        assertEquals(100.0, cost!!, 0.001)
    }

    @Test
    fun `a chosen batch in a different unit falls back to the average, not the batch`() {
        val purchases = listOf(
            buy(1, qty = 10.0, rate = 100.0, unit = "bag"),
            buy(2, qty = 10.0, rate = 5.0, unit = "kg")
        )
        val cost = Profit.costFor(sale(unit = "kg", batch = 1L), purchases.associateBy { it.id }, purchases.groupBy { it.productId!! })
        assertEquals(5.0, cost!!, 0.001)
    }

    @Test
    fun `a line with no product, no rate, or no priced purchase is skipped and the coverage says so`() {
        val r = Profit.compute(
            sales = listOf(
                sale(qty = 10.0, rate = 150.0),                 // costed: 10 * (150 - 100)
                sale(productId = null, qty = 5.0, rate = 50.0), // no product
                sale(qty = 5.0, rate = null),                   // no rate
                sale(productId = 2L, qty = 5.0, rate = 50.0)    // DAP never bought
            ),
            purchases = listOf(buy(1, qty = 100.0, rate = 100.0)),
            productNames = names, salesTotal = 3_000.0, expenses = 0.0
        )
        assertEquals(500.0, r.grossProfit, 0.001)
        assertEquals(1, r.countedLines)
        assertEquals(3, r.skippedLines)
        assertEquals(1_500.0, r.coveredRevenue, 0.001)
        assertEquals(50, r.coveragePercent)
    }

    @Test
    fun `a bonus line earns nothing and still costs what it cost`() {
        val r = Profit.compute(
            sales = listOf(sale(qty = 10.0, rate = 150.0), sale(qty = 2.0, rate = null, bonus = true)),
            purchases = listOf(buy(1, qty = 100.0, rate = 100.0)),
            productNames = names, salesTotal = 1_500.0, expenses = 0.0
        )
        // 10 * 50 - 2 * 100
        assertEquals(300.0, r.grossProfit, 0.001)
        assertEquals(2, r.countedLines)
    }

    @Test
    fun `goods brought back reverse the profit the sale made`() {
        val r = Profit.compute(
            sales = listOf(sale(qty = 10.0, rate = 150.0), sale(qty = 4.0, rate = 150.0, given = false)),
            purchases = listOf(buy(1, qty = 100.0, rate = 100.0)),
            productNames = names, salesTotal = 1_500.0, expenses = 0.0
        )
        assertEquals(300.0, r.grossProfit, 0.001)
        assertEquals(6.0, r.products.single().soldQty, 0.001)
    }

    @Test
    fun `expenses stay on their own line and products are ranked by profit`() {
        val r = Profit.compute(
            sales = listOf(sale(productId = 1L, qty = 10.0, rate = 150.0), sale(productId = 2L, qty = 10.0, rate = 400.0)),
            purchases = listOf(buy(1, productId = 1L, qty = 100.0, rate = 100.0), buy(2, productId = 2L, qty = 100.0, rate = 300.0)),
            productNames = names, salesTotal = 5_500.0, expenses = 700.0
        )
        assertEquals(1_500.0, r.grossProfit, 0.001)
        assertEquals(800.0, r.netAfterExpenses, 0.001)
        assertEquals(listOf("DAP", "Urea"), r.products.map { it.name })
        assertEquals(1_000.0, r.products[0].profit, 0.001)
        assertEquals(500.0, r.products[1].profit, 0.001)
    }

    @Test
    fun `a loss is a negative figure, not zero`() {
        val r = Profit.compute(
            sales = listOf(sale(qty = 10.0, rate = 80.0)),
            purchases = listOf(buy(1, qty = 100.0, rate = 100.0)),
            productNames = names, salesTotal = 800.0, expenses = 0.0
        )
        assertEquals(-200.0, r.grossProfit, 0.001)
        assertTrue(r.products.single().profit < 0)
    }

    @Test
    fun `nothing costed is empty, and coverage is capped at 100`() {
        val empty = Profit.compute(emptyList(), emptyList(), names, 0.0, 0.0)
        assertTrue(empty.isEmpty)
        assertNull(empty.coveragePercent)

        val discounted = Profit.compute(
            sales = listOf(sale(qty = 10.0, rate = 150.0)),
            purchases = listOf(buy(1, qty = 100.0, rate = 100.0)),
            productNames = names, salesTotal = 1_400.0, expenses = 0.0
        )
        assertEquals(100, discounted.coveragePercent)
    }
}
