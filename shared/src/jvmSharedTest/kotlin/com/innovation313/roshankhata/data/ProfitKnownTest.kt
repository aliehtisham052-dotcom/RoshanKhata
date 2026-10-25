package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sales report's profit (25 Oct 2026, the audit): with no cost price
 * known, "Net profit" was the cashbook's expenses alone, shown as a loss —
 * Rs 40,000 of sales and Rs 3,000 of rent read "Net profit − Rs 3,000".
 */
class ProfitKnownTest {

    private fun line(amount: Double, costRate: Double?) =
        SoldLine(1, 1_000, null, null, null, null, "Urea", null, 1.0, null, amount, 0.0, costRate)

    private fun report(vararg lines: SoldLine) = SalesReport(0, 10_000, lines.toList(), 3_000.0, 0.0, emptyList())

    @Test
    fun `no cost known shows no profit figure, not the expenses as a loss`() {
        val r = report(line(40_000.0, null))
        assertEquals("the old figure", -3_000.0, r.netProfit, 0.001)
        assertNull(r.grossOrNull)
        assertNull(r.netOrNull)
        assertFalse(r.costComplete)
    }

    @Test
    fun `part of the cost known gives the gross on that part, and no net`() {
        val r = report(line(10_000.0, 8_000.0), line(30_000.0, null))
        assertEquals(2_000.0, r.grossOrNull!!, 0.001)
        assertNull("the expenses are the whole shop's, the gross only a quarter of it", r.netOrNull)
    }

    @Test
    fun `every cost known gives the net`() {
        val r = report(line(10_000.0, 8_000.0), line(30_000.0, 25_000.0))
        assertTrue(r.costComplete)
        assertEquals(7_000.0, r.grossOrNull!!, 0.001)
        assertEquals(4_000.0, r.netOrNull!!, 0.001)
    }

    @Test
    fun `a day with no sales keeps its expenses as the day's result`() {
        val r = report()
        assertEquals(-3_000.0, r.netOrNull!!, 0.001)
    }
}
