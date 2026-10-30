package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** PartyTotals against the hand-written lines it replaced (30 Oct 2026). */
class PartyTotalsTest {

    private fun old(balances: List<Double>): Pair<Double, Double> =
        balances.filter { Money.isPositive(it) }.sumOf { it } to balances.filter { Money.isNegative(it) }.sumOf { -it }

    @Test
    fun `get and give as the old lines summed them`() {
        val balances = listOf(5000.0, -1200.0, 0.0, 250.5, -0.004, 0.004, -80.25, 1e-15)
        val sides = PartyTotals.of(balances)
        val (get, give) = old(balances)
        assertEquals(get, sides.get, 1e-9)
        assertEquals(give, sides.give, 1e-9)
        assertEquals(5250.5, sides.get, 1e-9)
        assertEquals(1280.25, sides.give, 1e-9)
        assertEquals(3970.25, sides.net, 1e-9)
    }

    @Test
    fun `a settled residue is in neither total`() {
        val sides = PartyTotals.of(listOf(0.1 + 0.2 - 0.3, -0.004))
        assertEquals(0.0, sides.get, 0.0)
        assertEquals(0.0, sides.give, 0.0)
    }

    @Test
    fun `nobody gives zeros`() {
        val sides = PartyTotals.of(emptyList())
        assertEquals(0.0, sides.get, 0.0)
        assertEquals(0.0, sides.give, 0.0)
    }
}
