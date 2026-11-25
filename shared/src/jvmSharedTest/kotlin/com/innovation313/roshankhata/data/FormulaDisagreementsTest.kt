package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs

/**
 * The formula book's disagreements D3, D4, D5, D8 and D10 (25 Nov 2026, BC-293..BC-298): two places that work out one
 * figure, held to one rule. Invented figures and names only.
 */
class FormulaDisagreementsTest {

    private fun <T> inCurrency(sign: String, body: () -> T): T {
        val before = Currency.symbol
        Currency.symbol = sign
        try { return body() } finally { Currency.symbol = before }
    }

    // ---- D3 (BC-293): a line returned in parts gives back what it was counted at, to the coin ----

    private fun back(spread: Double?, rate: Double, sold: Double, qty: Double, before: Double, sub: Double? = null, off: Double? = null) =
        ReturnedLine(
            quantity = qty, soldQuantity = sold, rate = rate, discount = 0.0, costRate = null, taxAmount = null, taxIncluded = null,
            saleSubtotal = sub, saleDiscount = off, saleItemId = 7, saleId = 3, spread = spread, backBefore = before
        )

    @Test fun `a discounted line of three returned one bag at a time gives back the whole 966_67`() {
        // Rs 100 off three lines of 1,000: the spread counted this line at 966.67 (SoldLines.spread).
        val parts = (0..2).map { back(966.67, 1000.0 / 3, 3.0, 1.0, it.toDouble(), 3000.0, 100.0).revenue }
        assertEquals(966.67, LineMath.round(parts.sum()), 0.0)
        assertEquals(listOf(322.22, 322.23, 322.22), parts)
    }

    @Test fun `a line of 1,000 for three returned in thirds is 1,000 again, not 999_99`() {
        val parts = (0..2).map { back(null, 1000.0 / 3, 3.0, 1.0, it.toDouble()).revenue }
        assertEquals(1000.0, LineMath.round(parts.sum()), 0.0)
    }

    @Test fun `a line returned whole or a first part reads as before`() {
        assertEquals(966.67, back(966.67, 1000.0 / 3, 3.0, 3.0, 0.0, 3000.0, 100.0).revenue, 0.0)
        assertEquals(322.22, back(966.67, 1000.0 / 3, 3.0, 1.0, 0.0, 3000.0, 100.0).revenue, 0.0)
    }

    // ---- D4 (BC-294): every zero test is the money contract's half coin, per currency ----

    @Test fun `half a coin is the shop's own`() {
        assertEquals(0.005, inCurrency("Rs") { Money.halfCoin }, 1e-12)
        assertEquals(0.0005, inCurrency("KWD") { Money.halfCoin }, 1e-12)
        assertEquals(0.5, inCurrency("Rp") { Money.halfCoin }, 1e-12)
    }

    @Test fun `a dinar instalment of 0_004 left is still coming in`() = inCurrency("KWD") {
        val (items, _) = CashFlow.items(
            listOf(CashFlow.Plan(1, "Customer A", 0.004, null, 1_000L)), emptyList(), emptyList(), emptyList(), emptyMap()
        )
        assertEquals(1, items.size)
        assertEquals(0.004, items.single().amount, 0.0)
    }

    @Test fun `a rupiah plan with 0_3 left is nothing`() = inCurrency("Rp") {
        val (items, _) = CashFlow.items(
            listOf(CashFlow.Plan(1, "Customer A", 0.3, null, 1_000L)), emptyList(), emptyList(), emptyList(), emptyMap()
        )
        assertTrue(items.isEmpty())
    }

    private val line = SaleReturnItem(returnId = 0, itemName = "Item one", quantity = 1.0, rate = 1.0)

    private fun check(refund: Double, method: String, party: Boolean) =
        ReturnMath.check(listOf(line), method, refund, 1.0, party, "damaged", emptyMap())

    @Test fun `a dinar refund 0_004 over the return is refused`() = inCurrency("KWD") {
        assertEquals(ReturnMath.Problem.BAD_REFUND, check(1.004, PaymentMethod.CASH, true))
        assertNull(check(1.0, PaymentMethod.CASH, true))
    }

    @Test fun `a walk-in dinar refund 0_004 short is not the whole return`() = inCurrency("KWD") {
        assertEquals(ReturnMath.Problem.WALK_IN_NEEDS_CASH, check(0.996, PaymentMethod.CASH, false))
    }

    @Test fun `a rupee refund within half a paisa still passes`() = inCurrency("Rs") {
        assertNull(check(1.004, PaymentMethod.CASH, true))
    }

    private val names = object : Words {
        override fun text(name: String, vararg args: Any): String = "<$name>"
        override fun plural(name: String, count: Int, vararg args: Any): String = "<$name>"
        override fun list(name: String): List<String> = emptyList()
    }
    private val money: (Double) -> String = { abs(it).toString() }

    private fun day(profit: Double?, opening: Double?, counted: Double?) = ZReport(
        from = 0L, to = 1L, saleCount = 1, salesTotal = 1000.0, byMethod = emptyMap(), onAccount = 0.0,
        discounts = 0.0, tax = 0.0, voidCount = 0, voidedTotal = 0.0, returnCount = 0, returnsTotal = 0.0,
        refundsByMethod = emptyMap(), khataCashIn = 0.0, khataCashOut = 0.0, cashbookIn = 0.0, cashbookOut = 0.0,
        opening = opening, counted = counted, profit = profit
    )

    @Test fun `a dinar drawer 0_004 short carries its minus, and a shift asks why`() = inCurrency("KWD") {
        val variance = day(null, 10.0, 9.996).lines(names, money).first { it.first == "<${W.z_variance}>" }.second
        assertTrue(variance, variance.startsWith("−"))
        assertTrue(ShiftMath.needsReason(10.0, 9.996, null))
    }

    // ---- D5 (BC-295): one median of whole days ----

    @Test fun `the season's median of 3 and 4 days is 4, as the Radar's`() {
        val utc = TimeZone.getTimeZone("UTC")
        val s = SeasonBook.Season(SeasonBook.Crop.RABI, 2025)
        val end = SeasonBook.harvestEnd(s, utc)
        val day = 24L * 60 * 60 * 1000
        val rows = listOf(
            SeasonBook.PartySeason(1, s, 100.0, 100.0, end + 3 * day),
            SeasonBook.PartySeason(2, s, 100.0, 100.0, end + 4 * day)
        )
        assertEquals(Median.of(listOf(3, 4)), SeasonBook.summaries(rows, utc).single().medianDaysAfterHarvest)
        assertEquals(4, SeasonBook.summaries(rows, utc).single().medianDaysAfterHarvest)
    }

    // ---- D8 (BC-296, BC-297): one rule for a day and for "up to the close" ----

    @Test fun `today on the day the clocks go forward ends at the next midnight`() {
        val london = TimeZone.getTimeZone("Europe/London")
        val noon = Calendar.getInstance(london).apply { clear(); set(2026, Calendar.MARCH, 29, 12, 0) }.timeInMillis
        val (from, to) = DaySpans.calendarDay(noon, london)
        val nextMidnight = Calendar.getInstance(london).apply { clear(); set(2026, Calendar.MARCH, 30, 0, 0) }.timeInMillis
        assertEquals(DayMath.startOfDay(noon, london), from)
        assertEquals(nextMidnight, to)
        assertEquals(23L * 60 * 60 * 1000, to - from)
    }

    @Test fun `a shift's stretch keeps a line written at its closing moment, as the drawer does`() {
        val shift = Shift(user = "Cashier A", openedAt = 1_000L, openingFloat = 0.0)
        val (from, to) = ShiftMath.span(shift, 5_000L)
        val atClose = 5_000L
        assertTrue(atClose >= from && atClose < to)
        assertTrue(1_000L >= from && 999L < from)
        // The drawer's (from, to] sums count the same moment for a stretch ending at the close.
        assertTrue(atClose > 1_000L && atClose <= 5_000L)
        assertEquals(Long.MAX_VALUE, DaySpans.through(Long.MAX_VALUE))
    }

    // ---- D10 (BC-298): the day report's profit is the counter's, said with the counter's lines ----

    @Test fun `the counter's profit stands before the line of every sale`() {
        val keys = day(150.0, null, null).lines(names, money).map { it.first }
        val profit = keys.indexOf("<${W.z_profit}>")
        val all = keys.indexOf("<${W.z_sales_all}>")
        assertTrue(keys.toString(), profit in 0 until all)
    }
}
