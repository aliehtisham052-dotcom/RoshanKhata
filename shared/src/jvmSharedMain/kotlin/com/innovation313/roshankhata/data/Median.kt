package com.innovation313.roshankhata.data

/**
 * THE ONE MEDIAN of the payment rules (BC-264, 24 Nov 2026). The Payment Radar floored an even count's middle two
 * ([3, 4] gave 3) while the Follow-up rhythm ([PaymentHabit]) rounded them half up ([3, 4] gave 4), so the two could name
 * different paying days for one customer. Both now call this. A whole number of days, the middle two of an even count
 * meeting half up (3 and 4 are 4, 10 and 20 are 15, -2 and -1 are -1), never a floor that drifts a day early.
 */
object Median {

    /** The median of [values] (not empty), in whole days: the middle one, or the middle two half up. */
    fun of(values: List<Int>): Int {
        require(values.isNotEmpty()) { "no median of nothing" }
        val s = values.sorted()
        val mid = s.size / 2
        return if (s.size % 2 == 1) s[mid] else Math.floorDiv(s[mid - 1].toLong() + s[mid].toLong() + 1L, 2L).toInt()
    }
}
