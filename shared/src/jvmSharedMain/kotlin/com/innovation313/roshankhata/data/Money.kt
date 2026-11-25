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

    /**
     * Half the shop's smallest coin (3 Nov 2026 audit, M08: it was half a paisa for every currency). Below this
     * there is no amount a person could settle: 0.005 for Rs, 0.0005 for KWD, 0.5 for Rp.
     */
    private val EPSILON: Double get() = 0.5 / Math.pow(10.0, Currency.decimals().toDouble())

    /**
     * The same line as a figure, for a test the four calls below cannot say (a tolerance in a check, a "more than"
     * between two amounts). Every money figure's zero test reads this or the calls, never a literal 0.005 or 0.004
     * (25 Nov 2026, formula book D4): a literal is half a paisa for every currency, so a dinar's 0.004 read as nothing
     * and a rupiah's 0.3 as a debt.
     */
    val halfCoin: Double get() = EPSILON

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

    // ---- The money contract (24 Nov 2026, plan item A5) ------------------------------------------------------
    // One rule for turning a Double into a figure the shop could pay: the DECIMAL text of the Double
    // (BigDecimal.valueOf, so 1.005 is 1.005, not 1.00499999999999989...), the shop's own decimals
    // (Currency.decimals: 2 for Rs, 3 for KWD, none for Rp) and HALF_UP, the way a person rounds. Before this the
    // app had four others side by side: the binary value (NumberWords: 1.005 printed 1.01 and was spoken as 1.00),
    // banker's rounding to whole cents (Calc.evalDiscount: a tie went to the even cent and KWD lost its third
    // decimal), a fixed 100 (BookMerge) and a fixed 2 (EntryInvoice's rate). SQL sums are not rounded by this: they
    // add Doubles and the screens round the sum once, as Format.money does with the same rule.

    /** [value] as the shop would write it: the decimal text, [decimals] places, half up. Not-a-number and infinity pass through. */
    fun round(value: Double, decimals: Int = Currency.decimals()): Double =
        if (!value.isFinite()) value
        else java.math.BigDecimal.valueOf(value).setScale(decimals, java.math.RoundingMode.HALF_UP).toDouble()

    /** [value] in the smallest coin (paisa, fils, rupiah) as a whole number, by the same rule: two sums are equal when these are. */
    fun minor(value: Double, decimals: Int = Currency.decimals()): Long =
        java.math.BigDecimal.valueOf(value).setScale(decimals, java.math.RoundingMode.HALF_UP).unscaledValue().toLong()

    /** Whether two figures are the same amount of money to the smallest coin. */
    fun same(a: Double, b: Double, decimals: Int = Currency.decimals()): Boolean = minor(a, decimals) == minor(b, decimals)

    /** [percent] per cent of [base], worked out in decimals and rounded once, to the shop's coin: 10% of 333.3 is 33.33. */
    fun percentOf(base: Double, percent: Double, decimals: Int = Currency.decimals()): Double =
        java.math.BigDecimal.valueOf(base).multiply(java.math.BigDecimal.valueOf(percent))
            .divide(java.math.BigDecimal(100), decimals, java.math.RoundingMode.HALF_UP).toDouble()
}
