package com.innovation313.roshankhata.data

import java.util.Calendar

/**
 * A plan's next payment day after an instalment is paid (21 Oct 2026, the
 * owner: "where there should be an edit option there must be one"). The
 * plan never moved its date on, so a plan paid on time read as late for
 * ever. Nothing is moved silently: after each payment the owner is shown the
 * suggested day and may change it or clear it, and the day can be changed
 * from the plan at any time.
 *
 * The suggestion: one calendar month after the day that was due (a monthly
 * qist is the common arrangement), stepped on until it is after today; a
 * plan that had no day gets one month from today. 31 Jan + 1 month is the
 * end of February, as a calendar counts it.
 */
object PlanSchedule {
    fun suggestNext(previousDue: Long?, now: Long = System.currentTimeMillis()): Long {
        val today = startOfDay(now)
        val c = Calendar.getInstance().apply { timeInMillis = startOfDay(previousDue ?: now) }
        val day = c.get(Calendar.DAY_OF_MONTH)
        var steps = 0
        do {
            c.add(Calendar.MONTH, 1)
            // Back to the plan's own day of the month where the month has it (31 Jan -> 28 Feb -> 31 Mar).
            c.set(Calendar.DAY_OF_MONTH, minOf(day, c.getActualMaximum(Calendar.DAY_OF_MONTH)))
            steps++
        } while (c.timeInMillis <= today && steps < 1200)
        return c.timeInMillis
    }

    private fun startOfDay(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
