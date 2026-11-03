package com.innovation313.roshankhata.data

import kotlin.math.roundToInt

/** A customer's buying, summed by [KhataDao.buyingHabits]: on how many days he bought, first and last, and how much. */
data class BuyingHabit(
    val partyId: Long,
    val name: String,
    val phone: String?,
    /** The days he bought on (a khata "I gave", not Qarz-e-Hasna), each day once. */
    val days: Int,
    val firstAt: Long,
    val lastAt: Long,
    val total: Double
)

/**
 * Customers who stopped coming (3 Nov 2026, the competitor study's "segments" done the app's way: worked out from
 * the khata, not grouped by hand). A regular is one who bought on at least [MIN_DAYS] days; his usual gap is the
 * time from his first purchase to his last spread over the gaps between them. He has stopped coming when his last
 * purchase is more than twice that gap ago, and never sooner than [FLOOR_DAYS] — a weekly customer is not missed
 * after ten days. One gone longer than [MAX_DAYS] is an old account, not a regular who stopped. The biggest buyers
 * come first: they are the ones worth a call. Nothing is sent to anyone; the owner calls or writes himself.
 */
object Lapsed {

    const val MIN_DAYS = 3
    const val FLOOR_DAYS = 30
    const val MAX_DAYS = 730
    private const val DAY = 24L * 60 * 60 * 1000

    data class Customer(val habit: BuyingHabit, val usualGapDays: Int, val sinceDays: Int)

    /** The days between his purchases on average, or null with fewer than two buying days. */
    fun usualGapDays(h: BuyingHabit): Double? =
        if (h.days < 2) null else (h.lastAt - h.firstAt).toDouble() / DAY / (h.days - 1)

    fun of(habits: List<BuyingHabit>, now: Long): List<Customer> = habits.mapNotNull { h ->
        if (h.days < MIN_DAYS) return@mapNotNull null
        val gap = usualGapDays(h) ?: return@mapNotNull null
        val since = (now - h.lastAt).toDouble() / DAY
        if (since <= maxOf(2 * gap, FLOOR_DAYS.toDouble()) || since > MAX_DAYS) null
        else Customer(h, gap.roundToInt().coerceAtLeast(1), since.toInt())
    }.sortedByDescending { it.habit.total }

    suspend fun list(dao: KhataDao, now: Long = System.currentTimeMillis()): List<Customer> = of(dao.buyingHabits(), now)
}
