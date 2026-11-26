package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden figures of the Formula Book's F65–F69 ([PriceMath]; Marhala 9, lane B, 26 Nov 2026). Each is held to a worked figure
 * from the atom's own example (18,000 ÷ 80 = 225; the formula from University of Missouri Extension G648) or to the owner's example (800 cost, 1,000 sale: margin 20%, markup 25%),
 * exactly — the formulas work in decimal arithmetic, so no tolerance is needed for these — and to the edges where a figure
 * cannot be said (a sale at nothing, a cost of nothing, a price at or under the variable cost, nothing owed).
 */
class HudhudFormulaGoldenTest {

    @Test
    fun `F66 and F67 the owner's example 800 cost 1000 sale`() {
        assertEquals(20.0, PriceMath.marginPercent(800.0, 1000.0)!!, 0.0)
        assertEquals(25.0, PriceMath.markupPercent(800.0, 1000.0)!!, 0.0)
        assertEquals(200.0, PriceMath.profit(800.0, 1000.0)!!, 0.0)
    }

    @Test
    fun `markup is bigger than margin whenever there is a profit`() {
        for ((c, s) in listOf(800.0 to 1000.0, 1.0 to 2.0, 99.5 to 100.0, 10.0 to 1000.0)) {
            assertTrue("$c -> $s", PriceMath.markupPercent(c, s)!! > PriceMath.marginPercent(c, s)!!)
        }
    }

    @Test
    fun `a loss is a negative margin and markup`() {
        assertEquals(-25.0, PriceMath.marginPercent(1000.0, 800.0)!!, 0.0)
        assertEquals(-20.0, PriceMath.markupPercent(1000.0, 800.0)!!, 0.0)
    }

    @Test
    fun `fractions are worked in decimals not binary`() {
        // 0.1 + 0.2 in doubles is 0.30000000000000004; the margin of 0.1 -> 0.3 is two thirds of the sale price.
        assertEquals(66.66666666666667, PriceMath.marginPercent(0.1, 0.3)!!, 1e-12)
        assertEquals(200.0, PriceMath.markupPercent(0.1, 0.3)!!, 0.0)
    }

    @Test
    fun `no margin at a sale of nothing and no markup on a cost of nothing`() {
        assertNull(PriceMath.marginPercent(800.0, 0.0))
        assertNull(PriceMath.markupPercent(0.0, 1000.0))
        assertNull(PriceMath.marginPercent(-1.0, 1000.0))
        assertNull(PriceMath.markupPercent(800.0, Double.NaN))
        assertNull(PriceMath.marginPercent(800.0, Double.POSITIVE_INFINITY))
    }

    @Test
    fun `discount and markup do not undo each other`() {
        // 25% markup on 800 is 1,000; 25% off 1,000 is 750, not 800; the discount that comes back to 800 is the margin, 20%.
        val price = 800.0 * (1 + PriceMath.markupPercent(800.0, 1000.0)!! / 100)
        assertEquals(1000.0, price, 0.0)
        assertEquals(750.0, price * (1 - 25.0 / 100), 0.0)
        assertEquals(800.0, price * (1 - PriceMath.marginPercent(800.0, 1000.0)!! / 100), 0.0)
    }

    @Test
    fun `F68 contribution and F69 break-even, the source's own example`() {
        assertEquals(80.0, PriceMath.contribution(100.0, 20.0)!!, 0.0)
        val be = PriceMath.breakEvenUnits(18_000.0, 100.0, 20.0)!!
        assertEquals(225.0, be.exact, 0.0)
        assertEquals(225L, be.whole)
    }

    @Test
    fun `break-even rounds a part of a unit up and never breaks even at or under the variable cost`() {
        val be = PriceMath.breakEvenUnits(1_000.0, 100.0, 70.0)!!
        assertEquals(33.333333333333333, be.exact, 1e-9)
        assertEquals(34L, be.whole)
        assertEquals(0L, PriceMath.breakEvenUnits(0.0, 100.0, 70.0)!!.whole)
        assertNull(PriceMath.breakEvenUnits(1_000.0, 70.0, 70.0))
        assertNull(PriceMath.breakEvenUnits(1_000.0, 60.0, 70.0))
        assertNull(PriceMath.breakEvenUnits(-1.0, 100.0, 70.0))
        assertEquals(-10.0, PriceMath.contribution(60.0, 70.0)!!, 0.0)
    }

    @Test
    fun `F65 concentration is the largest share of what is owed to the shop`() {
        // Owed to the shop 6,000 + 3,000 + 1,000 = 10,000; the shop owes 5,000 to a supplier (not counted); one settled.
        val c = PriceMath.concentration(listOf(3_000.0, -5_000.0, 6_000.0, 0.0, 1_000.0))!!
        assertEquals(2, c.index)
        assertEquals(6_000.0, c.largest, 0.0)
        assertEquals(10_000.0, c.total, 0.0)
        assertEquals(0.6, c.share, 0.0)
    }

    @Test
    fun `F65 one customer owing is all of it, a tie keeps the first, nothing owed is none`() {
        assertEquals(1.0, PriceMath.concentration(listOf(-10.0, 500.0))!!.share, 0.0)
        val tie = PriceMath.concentration(listOf(400.0, 400.0))!!
        assertEquals(0, tie.index)
        assertEquals(0.5, tie.share, 0.0)
        assertNull(PriceMath.concentration(listOf(-100.0, 0.0)))
        assertNull(PriceMath.concentration(emptyList()))
        assertNull(PriceMath.concentration(listOf(Double.NaN, Double.POSITIVE_INFINITY)))
    }
}
