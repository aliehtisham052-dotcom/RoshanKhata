package com.innovation313.roshankhata.data

import java.util.TimeZone

/** One ledger line, as much of it as the payment habit needs. Read by Room. */
data class LedgerPoint(
    val partyId: Long,
    val timestamp: Long,
    val amount: Double,
    val isGiven: Boolean
)

/**
 * "Aslam usually pays every month — it has been seven weeks."
 *
 * The Follow-up screen ranked debtors by how long their account had been
 * quiet, against one fixed 30-day line. But customers differ: a farmer who
 * settles after every harvest is not late at 60 days, and a shop that pays
 * weekly IS late at 20. This reads each customer's own rhythm from the
 * ledger and says when they have fallen behind it — on the phone, from the
 * owner's own entries, nothing sent anywhere (1 Oct).
 *
 * Plain arithmetic, not a model, so it runs on every phone and its answer
 * can be checked by hand:
 *
 *  - Payment days: the distinct days the customer paid (an I-Got entry).
 *    Two entries on one day are one payment, or a split payment would teach
 *    the habit "every 0 days".
 *  - Habit: the MEDIAN gap between those days. Median, not average, so one
 *    long gap (an illness, a bad season) does not redefine the customer.
 *    Only with at least [MIN_PAYMENT_DAYS] payment days; fewer is not a
 *    habit and nothing is shown.
 *  - Waiting: days since the LATER of the last payment and the start of the
 *    current debt. Someone who cleared their account in March and took new
 *    credit two days ago has waited two days, not since March.
 *  - Late: waiting beyond half again the habit, AND by at least
 *    [MIN_DAYS_OVER] days, so a weekly payer is not flagged for a day or two.
 */
object PaymentHabit {

    data class Habit(
        /** The customer's usual days between payments. */
        val typicalDays: Int,
        /** Days waited so far on the current debt. */
        val waitingDays: Int,
        val isLate: Boolean
    )

    const val MIN_PAYMENT_DAYS = 3
    const val LATE_FACTOR = 1.5
    const val MIN_DAYS_OVER = 7

    private const val DAY = 24L * 60 * 60 * 1000
    private const val EPS = 0.005

    /**
     * Every party's habit, for parties that owe and have enough history.
     * [points] in any order; they are grouped and sorted here.
     */
    fun forAll(
        points: List<LedgerPoint>,
        now: Long = System.currentTimeMillis(),
        tz: TimeZone = TimeZone.getDefault()
    ): Map<Long, Habit> {
        val out = HashMap<Long, Habit>()
        for ((partyId, lines) in points.groupBy { it.partyId }) {
            of(lines, now, tz)?.let { out[partyId] = it }
        }
        return out
    }

    /** One party's habit, or null when they owe nothing or have too little history. */
    fun of(
        lines: List<LedgerPoint>,
        now: Long = System.currentTimeMillis(),
        tz: TimeZone = TimeZone.getDefault()
    ): Habit? {
        var balance = 0.0
        var debtStart: Long? = null
        val paymentDays = sortedSetOf<Long>()

        for (p in lines.sortedBy { it.timestamp }) {
            val before = balance
            balance += if (p.isGiven) p.amount else -p.amount
            if (!p.isGiven) paymentDays += dayOf(p.timestamp, tz)
            when {
                balance <= EPS -> debtStart = null
                before <= EPS -> debtStart = p.timestamp
            }
        }

        val start = debtStart ?: return null           // owes nothing
        if (paymentDays.size < MIN_PAYMENT_DAYS) return null

        val days = paymentDays.toList()
        val typical = median(days.zipWithNext { a, b -> (b - a).toInt() }).coerceAtLeast(1)

        val from = maxOf(days.last(), dayOf(start, tz))
        val waiting = (dayOf(now, tz) - from).toInt().coerceAtLeast(0)
        val late = waiting > typical * LATE_FACTOR && waiting - typical >= MIN_DAYS_OVER

        return Habit(typical, waiting, late)
    }

    /** Local calendar day number, so "today" and "yesterday" are the owner's own. */
    private fun dayOf(millis: Long, tz: TimeZone): Long =
        (millis + tz.getOffset(millis)).floorDiv(DAY)

    private fun median(values: List<Int>): Int {
        val s = values.sorted()
        val mid = s.size / 2
        return if (s.size % 2 == 1) s[mid] else Math.round((s[mid - 1] + s[mid]) / 2.0).toInt()
    }
}
