package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

/**
 * The day report's profit line (25 Oct 2026, the audit): the money format
 * drops the sign, so a day's loss printed as a positive "Profit". A loss now
 * carries its minus on the phone, the counter, the receipt and the PDF.
 */
class ZReportLinesTest {

    private val names = object : Words {
        override fun text(name: String, vararg args: Any): String = "<$name>"
        override fun plural(name: String, count: Int, vararg args: Any): String = "<$name>"
        override fun list(name: String): List<String> = emptyList()
    }

    /** The money format the app uses drops the sign; so does this one. */
    private val money: (Double) -> String = { "Rs " + abs(it).toLong() }

    private fun day(profit: Double?) = ZReport(
        from = 0L, to = 1L, saleCount = 1, salesTotal = 1000.0, byMethod = emptyMap(), onAccount = 0.0,
        discounts = 0.0, tax = 0.0, voidCount = 0, voidedTotal = 0.0, returnCount = 0, returnsTotal = 0.0,
        refundsByMethod = emptyMap(), khataCashIn = 0.0, khataCashOut = 0.0, cashbookIn = 0.0, cashbookOut = 0.0,
        opening = null, counted = null, profit = profit
    )

    private fun profitLine(profit: Double?) = day(profit).lines(names, money).firstOrNull { it.first == "<${W.z_profit}>" }?.second

    @Test
    fun `a day's loss keeps its minus`() {
        assertEquals("− Rs 1500", profitLine(-1500.0))
    }

    @Test
    fun `a profit and a break-even day read plainly`() {
        assertEquals("Rs 1500", profitLine(1500.0))
        assertEquals("Rs 0", profitLine(0.0))
    }

    @Test
    fun `no profit line when the cost is not known`() {
        assertEquals(null, profitLine(null))
    }
}
