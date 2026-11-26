package com.innovation313.roshankhata.data

import kotlin.math.abs

/**
 * The advisor's small rules, pure and named once (25 Nov 2026, plan H5), so the answer layer ([AnswerAdvisor]) and the tests read the same
 * lines: how two spans' sales stand, who comes first in the chase, which two facts the Today card tells. No book and no words here.
 */
object HudhudAdviceRules {

    /** Under this share of change (in percent of the span before) two spans are "about the same". */
    const val FLAT_PERCENT = 1.0

    /** The proactive lines on the Today card: never more than this. */
    const val TODAY_MAX = 2

    /** A shelf that lasts fewer whole days than this is told on the Today card. */
    const val COVER_WARN_DAYS = 7

    enum class Way { UP, DOWN, FLAT, NONE }

    /** [way] of the sales now against before; [change] is now less before; [percent] the change in percent of before (null: nothing before). */
    class Verdict(val way: Way, val change: Double, val percent: Double?)

    /**
     * The way two spans' sales stand, by the shop's money (half its smallest coin is nothing, [Money.isZero]): both nothing is
     * [Way.NONE]; no change is [Way.FLAT]; sales where there were none are up with no percentage; else the change in percent of the
     * span before, and under [FLAT_PERCENT] it is about the same.
     */
    fun verdict(now: Double, before: Double): Verdict {
        val change = now - before
        if (Money.isZero(now) && Money.isZero(before)) return Verdict(Way.NONE, 0.0, null)
        if (Money.isZero(change)) return Verdict(Way.FLAT, change, 0.0)
        if (Money.isZero(before)) return Verdict(if (change > 0) Way.UP else Way.DOWN, change, null)
        val pct = change / abs(before) * 100.0
        return Verdict(if (abs(pct) < FLAT_PERCENT) Way.FLAT else if (change > 0) Way.UP else Way.DOWN, change, pct)
    }

    /**
     * One customer who owes, as the chase ranks him: [bucket] the age bucket of his oldest piece still owed (0 youngest, 3 over 90 days:
     * [Aging.bucketOf]), [lightRank] the Payment Radar's light (0 none or Radar off, 1 green, 2 amber, 3 red), [balance] what he owes.
     */
    class Candidate(val id: Long, val name: String, val balance: Double, val bucket: Int, val lightRank: Int)

    /**
     * Who to ask first: the oldest bucket of money owed first (so "over 90 days" outranks "31 to 60"), then the Radar's light, then how
     * much is owed; a tie by name, so one book always lists the same names in the same order.
     */
    val CHASE_ORDER: Comparator<Candidate> =
        compareByDescending<Candidate> { it.bucket }.thenByDescending { it.lightRank }.thenByDescending { it.balance }.thenBy { it.name }

    /**
     * The first [max] of two lists told by turns, the first list first: a promise, a shelf, the next promise, the next shelf. Nothing
     * more than [max] ever, and a list that runs out leaves its turns to the other.
     */
    fun interleave(first: List<String>, second: List<String>, max: Int = TODAY_MAX): List<String> {
        val out = ArrayList<String>()
        val a = first.iterator()
        val b = second.iterator()
        while (out.size < max && (a.hasNext() || b.hasNext())) {
            if (a.hasNext()) out += a.next()
            if (out.size < max && b.hasNext()) out += b.next()
        }
        return out
    }

    /**
     * Who owes the shop the most (26 Nov 2026, one rule): the parties whose balance is above nought ([Money.isPositive]), the largest
     * first, the first [n]. A tie keeps the book's order (a stable sort), so the first is [PriceMath.concentration]'s largest too. The
     * "who owes most" figure, the concentration's rows and the joke's list all read it.
     */
    fun <T> owedMost(parties: List<T>, n: Int, balance: (T) -> Double): List<T> =
        parties.filter { Money.isPositive(balance(it)) }.sortedByDescending { balance(it) }.take(n)

    /**
     * Price pressure (26 Nov 2026, Marhala 10): the sales of the span rose ([Money.isPositive] of the change) while the margin fell, the
     * two margins compared as the card shows them (two decimals, half up) — a difference the card cannot show is no fall. No threshold
     * of our own.
     */
    fun pricePressure(salesNow: Double, salesBefore: Double, marginNow: Double, marginBefore: Double): Boolean {
        val a = java.math.BigDecimal.valueOf(marginNow).setScale(2, java.math.RoundingMode.HALF_UP)
        val b = java.math.BigDecimal.valueOf(marginBefore).setScale(2, java.math.RoundingMode.HALF_UP)
        return Money.isPositive(salesNow - salesBefore) && a < b
    }
}
