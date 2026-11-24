package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Random

/**
 * Plan item A2, the money half (24 Nov 2026): SQL adds Doubles in whatever order the engine reads the rows, and a
 * screen rounds that sum once through [Money.round]. Every report then agrees with every other only if that one
 * rounding lands on the exact decimal sum of the same rows, whatever the order and the shop's coin. This holds it,
 * for 2 decimals (Rs), 3 (KWD) and none (Rp), on made-up amounts only.
 */
class ReportsAgreeMoneyTest {

    /** [n] made-up amounts of at most [decimals] places, below [maxMinor] smallest coins each, from a fixed seed. */
    private fun amounts(n: Int, decimals: Int, maxMinor: Long, seed: Long): List<BigDecimal> {
        val rnd = Random(seed)
        return List(n) { BigDecimal.valueOf((rnd.nextDouble() * maxMinor).toLong() + 1, decimals) }
    }

    /** What SQLite's SUM does: Doubles added one after another. */
    private fun sqlSum(rows: List<BigDecimal>): Double {
        var s = 0.0
        for (r in rows) s += r.toDouble()
        return s
    }

    private fun exact(rows: List<BigDecimal>): BigDecimal = rows.fold(BigDecimal.ZERO) { a, b -> a + b }

    private fun check(decimals: Int, maxMinor: Long, rows: Int, seed: Long) {
        val xs = amounts(rows, decimals, maxMinor, seed)
        val want = exact(xs).setScale(decimals, RoundingMode.HALF_UP)
        val forward = Money.round(sqlSum(xs), decimals)
        val backward = Money.round(sqlSum(xs.reversed()), decimals)
        val shuffled = Money.round(sqlSum(xs.shuffled(Random(seed + 1))), decimals)
        assertEquals("forward, $decimals decimals", want.toDouble(), forward, 0.0)
        assertEquals("the order the engine reads must not matter (reverse)", forward, backward, 0.0)
        assertEquals("the order the engine reads must not matter (shuffled)", forward, shuffled, 0.0)
        assertTrue(Money.same(sqlSum(xs), want.toDouble(), decimals))
    }

    @Test
    fun aSumOfTwentyThousandRupeeRowsRoundsToTheExactDecimalSumInAnyOrder() = check(2, 10_000_000, 20_000, 11)

    @Test
    fun aSumOfDinarRowsKeepsItsThirdDecimal() = check(3, 5_000_000, 20_000, 12)

    @Test
    fun aSumOfWholeCoinRowsIsWhole() = check(0, 200_000, 20_000, 13)

    @Test
    fun aBigWholesalerYearStillAgreesToThePaisa() = check(2, 50_000_000, 200_000, 14)

    @Test
    fun theClassicBinaryTrapsAreOneAmountToTheCoin() {
        assertTrue(Money.same(0.1 + 0.2, 0.3, 2))
        assertTrue(Money.same(1.005, 1.01, 2))          // decimal text, half up: not the binary 1.00499999...
        assertEquals(1.01, Money.round(1.005, 2), 0.0)
        assertEquals(2.675, Money.round(2.675, 3), 0.0)
        assertEquals(10.0, Money.round(9.999999999, 2), 0.0)
    }

    /**
     * A row-by-row rounded sum (Valuation rounds each product, a sold line rounds its own revenue) can differ from
     * the rounded sum of the rows by at most half a coin per row: this is the tolerance the Room test uses between a
     * per-line report and a SQL total, and it is exactly where it comes from.
     */
    @Test
    fun roundingEachRowAgainstRoundingTheSumDriftsByAtMostHalfACoinARow() {
        val rnd = Random(21)
        val rows = List(500) { BigDecimal.valueOf((rnd.nextDouble() * 9_000_000).toLong() + 1, 4) }   // 4 places: needs rounding
        val perRow = rows.sumOf { Money.round(it.toDouble(), 2) }
        val whole = Money.round(rows.sumOf { it.toDouble() }, 2)
        val driftMinor = Math.abs(Money.minor(perRow, 2) - Money.minor(whole, 2))
        assertTrue("drift $driftMinor coins over ${rows.size} rows", driftMinor <= rows.size / 2 + 1)
    }
}
