package com.innovation313.roshankhata.data

import java.math.BigDecimal
import java.math.BigInteger

/**
 * How one ticket's money is shared over its lines, to the coin (BC-252, 24 Nov 2026, importance 10).
 *
 * A ticket's discount used to be taken off line by line and each line rounded on its own: Rs 100 off three lines of
 * Rs 1,000 left 966.67 on each, and 3 x 966.67 is 2,900.01 beside the ticket's own 2,900.00 that the sales total adds.
 * The shares of one ticket (or of one khata entry spread over its goods) are now cut from the ticket's own whole
 * figure by the largest-remainder rule: every line takes the whole coins its weight earns, and the coins left over go
 * one each to the lines with the largest fractions (the first line wins a tie). The lines then add up to the ticket
 * exactly, in any currency (2 decimals Rs, 3 KWD, none Rp), and the same call always gives the same cut.
 *
 * Plain arithmetic on whole coins ([Money.minor]); no database. [Money.round] turns a share back into a Double.
 */
object TicketSpread {

    /**
     * [total] smallest coins shared over [weights] (each at least 0, together more than 0), the largest remainder way.
     * The answer has one share per weight, none below 0, and adds up to [total] exactly. A weight of 0 gets 0.
     */
    fun split(total: Long, weights: List<Long>): List<Long> {
        require(total >= 0) { "a ticket cannot share less than nothing" }
        require(weights.all { it >= 0 }) { "a weight is never negative" }
        val sum = weights.fold(BigInteger.ZERO) { a, w -> a + BigInteger.valueOf(w) }
        require(sum.signum() > 0) { "nothing to share over" }
        val t = BigInteger.valueOf(total)
        val base = LongArray(weights.size)
        val remainder = Array(weights.size) { BigInteger.ZERO }
        var given = 0L
        for (i in weights.indices) {
            val qr = (t * BigInteger.valueOf(weights[i])).divideAndRemainder(sum)
            base[i] = qr[0].toLong()
            remainder[i] = qr[1]
            given += base[i]
        }
        val left = (total - given).toInt()   // fewer than one coin per line
        // Largest fraction first; a tie goes to the earlier line, so the cut never depends on the order the engine reads.
        val order = weights.indices.sortedWith(compareByDescending<Int> { remainder[it] }.thenBy { it })
        for (k in 0 until left) base[order[k]] += 1
        return base.toList()
    }

    /** [coins] smallest coins as a Double figure of the shop's coin ([decimals] places). */
    fun figure(coins: Long, decimals: Int = Currency.decimals()): Double = BigDecimal.valueOf(coins, decimals).toDouble()

    /**
     * What each line of a ticket brought in, in coins: [taxable] (the ticket's goods less its discount) shared over the
     * lines by what each is worth ([weights], each line's own total before the discount). Null when there is nothing
     * sound to share (a negative weight, or none worth anything): the caller keeps the line's own figure then.
     */
    fun shares(taxable: Long, weights: List<Long>): List<Long>? {
        if (taxable < 0 || weights.isEmpty() || weights.any { it < 0 } || weights.sum() <= 0L) return null
        return split(taxable, weights)
    }

    /**
     * A khata entry of [coins] spread over its priced goods, each worth [worths] coins ([LineMath.lineTotal] in coins).
     * An amount under the goods' worth is a discount, cut over the goods ([shares], they add up to [coins], no rest);
     * an amount over it leaves every good its own worth and the exact remainder as [Cut.rest], the line of no item.
     * Either way [Cut.shares] and [Cut.rest] add up to [coins] to the coin.
     */
    fun cut(coins: Long, worths: List<Long>): Cut {
        val c = coins.coerceAtLeast(0L)
        val worth = worths.sum()
        val discounted = worth > 0L && c < worth && worths.none { it < 0 }
        val shares = if (discounted) split(c, worths) else worths
        return Cut(shares, if (discounted) 0L else c - worth, discounted)
    }

    class Cut(val shares: List<Long>, val rest: Long, val discounted: Boolean)
}
