package com.innovation313.roshankhata.data

/**
 * What the shop will get and will give across a set of parties — one rule for
 * every screen that shows the two totals (30 Oct 2026, the review: the same two
 * `filter { Money.isPositive } / sumOf` lines were written by hand on the phone's
 * Home and Khata delete, and the computer's Khata, delete question and Home).
 *
 * A balance counts only past [Money]'s half-paisa line, so a settled customer's
 * residue is in neither total. [net] is what Home calls the net balance.
 */
object PartyTotals {

    data class Sides(val get: Double, val give: Double) {
        val net: Double get() = get - give
    }

    fun of(balances: Iterable<Double>): Sides {
        var get = 0.0
        var give = 0.0
        for (b in balances) {
            if (Money.isPositive(b)) get += b
            else if (Money.isNegative(b)) give += -b
        }
        return Sides(get, give)
    }
}
