package com.innovation313.roshankhata.data

import java.util.TimeZone

/** The earliest open promised date of one party (payment plans). */
data class PartyPromise(val partyId: Long, val due: Long)

/**
 * "Who do I remind TODAY?" — the order of the Follow-up list, in one
 * testable place (1 Oct).
 *
 *  1. A promise that has come due (its day is today or earlier) comes first,
 *     the oldest promise at the top. The customer named this day themselves,
 *     so a reminder on it is the least awkward one there is.
 *  2. Then late by their own habit ([PaymentHabit]), furthest behind first.
 *  3. Then, unchanged from before: quiet longest, bigger balance first.
 *  A customer whose reminder was already opened today ([ReminderLog]) goes
 *  below all of these and is not counted in "today".
 *
 * "Today" counts rows 1 and 2 only. A promise still in the future is shown
 * on the row but is NOT a reason to remind.
 */
object FollowUpRank {


    fun isDue(due: Long?, now: Long, tz: TimeZone): Boolean =
        due != null && DayMath.dayNumber(due, tz) <= DayMath.dayNumber(now, tz)

    fun remindToday(
        partyId: Long,
        habits: Map<Long, PaymentHabit.Habit>,
        promises: Map<Long, Long>,
        now: Long,
        tz: TimeZone,
        remindedToday: Set<Long> = emptySet(),
        /** In his usual paying days today (PaymentRadar.likelyNow, 2 Nov 2026): a reminder now lands when he pays. */
        likely: Set<Long> = emptySet()
    ): Boolean = partyId !in remindedToday &&
        (isDue(promises[partyId], now, tz) || lateByHabit(partyId, habits, promises, now, tz) || partyId in likely)

    /**
     * Late by his own habit — unless he named a day that has not come yet
     * (a plan or "after the harvest"). Reminding a farmer before the date he
     * gave only spoils the relationship (2 Oct).
     */
    fun lateByHabit(
        partyId: Long,
        habits: Map<Long, PaymentHabit.Habit>,
        promises: Map<Long, Long>,
        now: Long,
        tz: TimeZone
    ): Boolean {
        if (habits[partyId]?.isLate != true) return false
        val promise = promises[partyId] ?: return true
        return isDue(promise, now, tz)
    }

    fun order(
        debtors: List<PartyWithBalance>,
        habits: Map<Long, PaymentHabit.Habit>,
        promises: Map<Long, Long>,
        now: Long = System.currentTimeMillis(),
        tz: TimeZone = TimeZone.getDefault(),
        remindedToday: Set<Long> = emptySet(),
        likely: Set<Long> = emptySet()
    ): List<PartyWithBalance> = debtors.sortedWith(
        // Already reminded today: to the bottom, whatever else is true.
        compareBy<PartyWithBalance> { it.id in remindedToday }
            .thenByDescending { isDue(promises[it.id], now, tz) }
            .thenBy { p -> promises[p.id]?.takeIf { isDue(it, now, tz) } ?: Long.MAX_VALUE }
            // Then those in their usual paying days (the Payment Radar): the likeliest money today.
            .thenByDescending { it.id in likely }
            .thenByDescending { lateByHabit(it.id, habits, promises, now, tz) }
            .thenByDescending { p ->
                habits[p.id]?.takeIf { lateByHabit(p.id, habits, promises, now, tz) }
                    ?.let { it.waitingDays - it.typicalDays } ?: 0
            }
            .thenBy { it.lastActivity }
            .thenByDescending { it.balance }
    )
}
