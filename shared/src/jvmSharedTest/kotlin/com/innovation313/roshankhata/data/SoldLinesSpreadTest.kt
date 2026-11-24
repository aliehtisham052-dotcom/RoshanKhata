package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BC-252: the counter's lines of one ticket carry the ticket's discount cut to the coin, so they add up to the ticket's
 * own total ([SoldLines.spread]). Made-up tickets; the three coins of the app (2, 3 and no decimals).
 */
class SoldLinesSpreadTest {

    private fun <T> withSign(sign: String, body: () -> T): T {
        val before = Currency.symbol
        Currency.symbol = sign
        try { return body() } finally { Currency.symbol = before }
    }

    private fun line(sale: Long, rate: Double, sub: Double?, off: Double?, qty: Double = 1.0, taxAmount: Double? = null, inside: Boolean? = null, name: String = "X") =
        SoldLine(saleId = sale, timestamp = 1_000, customerName = null, createdBy = null, taxPercent = null, productId = null, itemName = name,
            category = null, quantity = qty, unit = null, rate = rate, discount = 0.0, costRate = null,
            taxAmount = taxAmount, taxIncluded = inside, saleSubtotal = sub, saleDiscount = off)

    @Test fun `three lines of 1000 with 100 off add up to 2900 and not 2900 01`() = withSign("Rs") {
        val lines = List(3) { line(1, 1000.0, 3000.0, 100.0) }
        // one line read alone, as before: 966.67 each, 2,900.01 together (the fault)
        assertEquals(2_900.01, Money.round(lines.sumOf { it.revenue }), 0.0)
        val spread = SoldLines.spread(lines)
        assertEquals(listOf(966.67, 966.67, 966.66), spread.map { it.revenue })
        assertEquals(2_900.0, Money.round(spread.sumOf { it.revenue }), 0.0)
    }

    @Test fun `a dinar ticket keeps its third decimal`() = withSign("KWD") {
        val lines = List(3) { line(1, 10.0, 30.0, 1.0) }
        assertEquals(29.001, Money.round(lines.sumOf { it.revenue }), 0.0)
        val spread = SoldLines.spread(lines)
        assertEquals(listOf(9.667, 9.667, 9.666), spread.map { it.revenue })
        assertEquals(29.0, Money.round(spread.sumOf { it.revenue }), 0.0)
    }

    @Test fun `a ticket in a coin with no decimals is whole`() = withSign("Rp") {
        val lines = List(3) { line(1, 1000.0, 3000.0, 100.0) }
        assertEquals(2_901.0, Money.round(lines.sumOf { it.revenue }), 0.0)
        val spread = SoldLines.spread(lines)
        assertEquals(listOf(967.0, 967.0, 966.0), spread.map { it.revenue })
        assertEquals(2_900.0, Money.round(spread.sumOf { it.revenue }), 0.0)
    }

    @Test fun `unequal lines take what they are worth and still add up`() = withSign("Rs") {
        // 700 + 300 + 33.33 with 7.77 off: no line divides evenly
        val lines = listOf(line(7, 700.0, 1033.33, 7.77), line(7, 300.0, 1033.33, 7.77), line(7, 33.33, 1033.33, 7.77))
        val spread = SoldLines.spread(lines)
        assertEquals(Money.round(1033.33 - 7.77), Money.round(spread.sumOf { it.revenue }), 0.0)
        // each line within a coin of its exact share
        lines.forEachIndexed { i, l -> assertEquals(l.rate * (1.0 - 7.77 / 1033.33), spread[i].revenue, 0.0100001) }
    }

    @Test fun `tax inside a price comes off the line after its share, and the lines still add up to the rest`() = withSign("Rs") {
        // two lines of 590 (18% inside: 90 each), 59 off the 1180 ticket
        val lines = listOf(line(1, 590.0, 1180.0, 59.0, taxAmount = 90.0, inside = true), line(1, 590.0, 1180.0, 59.0, taxAmount = 90.0, inside = true))
        val spread = SoldLines.spread(lines)
        assertEquals(listOf(470.5, 470.5), spread.map { it.revenue })    // 560.50 each less 90 of tax
        assertEquals(1180.0 - 59.0 - 180.0, Money.round(spread.sumOf { it.revenue }), 0.0)
    }

    @Test fun `a ticket with no discount, and a line read alone, are as they were`() = withSign("Rs") {
        val none = listOf(line(1, 100.0, 300.0, 0.0), line(1, 100.0, 300.0, null), line(1, 100.0, null, null))
        val spread = SoldLines.spread(none)
        spread.forEach { assertNull(it.spread) }
        spread.forEachIndexed { i, l -> assertSame(none[i], l) }
        // the review's tax-inside line stays 900 whether read alone or through the spread (a ticket of one line)
        val one = line(5, 1180.0, 1180.0, 118.0, taxAmount = 162.0, inside = true)
        assertEquals(900.0, one.revenue, 0.001)
        assertEquals(900.0, SoldLines.spread(listOf(one)).single().revenue, 0.0)
    }

    @Test fun `a discount past the goods leaves nothing, and a line's own discount weighs the line by what it cost`() = withSign("Rs") {
        // 400 off 300 of goods: the ticket cannot come to less than nothing
        val over = SoldLines.spread(List(3) { line(1, 100.0, 300.0, 400.0) })
        assertEquals(listOf(0.0, 0.0, 0.0), over.map { it.revenue })
        // lines of 1,000 less 100 (900), 1,000 and 1,000: the ticket's goods are 2,900, 100 off -> 2,800 over weights 900:1000:1000
        fun l(disc: Double) = line(2, 1000.0, 2900.0, 100.0).copy(discount = disc)
        val spread = SoldLines.spread(listOf(l(100.0), l(0.0), l(0.0)))
        assertEquals(2_800.0, Money.round(spread.sumOf { it.revenue }), 0.0)
        assertEquals(listOf(868.96, 965.52, 965.52), spread.map { it.revenue })
    }

    @Test fun `tickets are kept apart, in their own order`() = withSign("Rs") {
        val mixed = listOf(
            line(1, 1000.0, 3000.0, 100.0, name = "a1"), line(2, 50.0, 100.0, 1.0, name = "b1"),
            line(1, 1000.0, 3000.0, 100.0, name = "a2"), line(2, 50.0, 100.0, 1.0, name = "b2"),
            line(1, 1000.0, 3000.0, 100.0, name = "a3"), line(3, 20.0, 20.0, null, name = "c1")
        )
        val spread = SoldLines.spread(mixed)
        assertEquals(mixed.map { it.itemName }, spread.map { it.itemName })
        assertEquals(2_900.0, Money.round(spread.filter { it.saleId == 1L }.sumOf { it.revenue }), 0.0)
        assertEquals(99.0, Money.round(spread.filter { it.saleId == 2L }.sumOf { it.revenue }), 0.0)
        assertEquals(20.0, spread.single { it.saleId == 3L }.revenue, 0.0)
    }

    @Test fun `a line that already carries its spread is left alone, and a bad ticket falls back to the old share`() = withSign("Rs") {
        val set = line(1, 100.0, 300.0, 50.0).copy(spread = 12.34)
        assertEquals(12.34, SoldLines.spread(listOf(set, line(1, 100.0, 300.0, 50.0))).first().revenue, 0.0)
        // a negative line (its own discount is bigger than its price) makes the weights unsound: the old per-line figure stays
        val bad = listOf(line(9, 100.0, 100.0, 10.0), line(9, -5.0, 100.0, 10.0))
        SoldLines.spread(bad).forEachIndexed { i, l -> assertNull(l.spread); assertEquals(bad[i].revenue, l.revenue, 0.0) }
        assertTrue(SoldLines.spread(emptyList()).isEmpty())
    }
}
