package com.innovation313.roshankhata.data

import kotlin.math.abs

/**
 * When a rupee figure counts as nothing.
 *
 * Balances are Doubles, and a Double that ought to be zero often is not. Give
 * a customer 0.10 and 0.20 and take back 0.30 and the ledger holds
 * 0.00000000000000005551 — settled by every measure the owner has, and not
 * equal to zero by the one the code was using. The screen rounds it away and
 * prints Rs 0, so the shop saw a settled customer that the app quietly refused
 * to treat as settled: absent from the Clear filter, missing from the settled
 * count, and still shown as owing on their own screen.
 *
 * Half a paisa is the line. Nothing smaller than that can be paid, owed, or
 * printed, so nothing smaller than that is a balance — it is arithmetic
 * residue, and residue is zero.
 */
object Money {

    /** Half a paisa. Below this there is no amount a person could settle. */
    private const val EPSILON = 0.005

    fun isZero(value: Double): Boolean = abs(value) < EPSILON

    fun isNotZero(value: Double): Boolean = !isZero(value)

    /**
     * Positive beyond the noise — the shop is owed. Deliberately not
     * `value > 0`, which would call a residue of a millionth of a paisa a
     * debt and colour a settled customer red.
     */
    fun isPositive(value: Double): Boolean = value >= EPSILON

    /** Negative beyond the noise — the shop owes. */
    fun isNegative(value: Double): Boolean = value <= -EPSILON
}
