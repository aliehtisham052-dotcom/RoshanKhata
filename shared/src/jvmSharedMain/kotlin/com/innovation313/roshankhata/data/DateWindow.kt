package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.words.W
import java.util.Calendar
import java.util.TimeZone

/*
 * "Show me a particular stretch of days" — the one arithmetic for both platforms
 * (29 Oct 2026, the cleanup: the phone's DateRangeFilter, the computer's Khata
 * filter and its report chips each worked today, yesterday, this week and this
 * month out on their own). The week starts where the shop's country starts it
 * (ShopCountry.weekStart, 21 Oct 2026): Saturday in Iran, Sunday in the Gulf.
 *
 * The two shapes a caller may want, kept per caller:
 *  - the phone's DateRangeFilter.Range is INCLUSIVE and closed — this week and
 *    this month run `from..now`;
 *  - the computer's ReportWindow leaves this week and this month OPEN
 *    (`to = null`: everything from the first day on, the list growing as the
 *    day goes). PartyWhen's week and month end at `now` like the phone's.
 */

/** The next day's first moment after the day [start] begins (by the calendar: a clock change makes a day 23 or 25 hours). */
fun dayAfter(start: Long, tz: TimeZone = TimeZone.getDefault()): Long =
    Calendar.getInstance(tz).apply { timeInMillis = start; add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis

/** The day before's first moment, by the calendar. */
fun dayBefore(start: Long, tz: TimeZone = TimeZone.getDefault()): Long =
    Calendar.getInstance(tz).apply { timeInMillis = start; add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis

/**
 * The phone's date filter (DateRangeFilter): who was dealt with today,
 * yesterday, this week or this month, by their last dealing.
 */
enum class PartyWhen(val word: String) {
    ALL(W.range_all), TODAY(W.range_today), YESTERDAY(W.range_yesterday), THIS_WEEK(W.range_this_week), THIS_MONTH(W.range_this_month);

    /**
     * The window as from..to (inclusive), or null for everything. A day ends at
     * its last millisecond by the calendar ([dayAfter] − 1, the phone's
     * 23:59:59.999), so a day of 23 or 25 hours is still that whole day.
     */
    fun window(now: Long = System.currentTimeMillis(), tz: TimeZone = TimeZone.getDefault()): LongRange? {
        val c = Calendar.getInstance(tz).apply { timeInMillis = now }
        fun start(): Long = c.apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return when (this) {
            ALL -> null
            TODAY -> start().let { it..dayAfter(it, tz) - 1 }
            YESTERDAY -> start().let { dayBefore(it, tz) until it }
            // The week starts where the shop's country starts it (21 Oct 2026): Saturday in Iran, Sunday in the Gulf.
            THIS_WEEK -> { c.firstDayOfWeek = ShopCountry.current.weekStart; c.set(Calendar.DAY_OF_WEEK, c.firstDayOfWeek); start()..now }
            THIS_MONTH -> { c.set(Calendar.DAY_OF_MONTH, 1); start()..now }
        }
    }
}

/**
 * A report's windows, the phone's chips (Marhala 4, part 7): today and yesterday whole, this week and this
 * month up to now (the shop's own week start), all, or days the owner picks. Each page offers its own list:
 * the Khata Report all of them but yesterday, the register no "all" (the phone's: a register is a fact about
 * a period).
 */
enum class ReportWindow(val word: String) {
    TODAY(W.range_today), YESTERDAY(W.range_yesterday), WEEK(W.range_this_week), MONTH(W.range_this_month),
    ALL(W.range_all), CUSTOM(W.range_custom);

    /** Its first and last moments now (an open end is null); null for all and for the owner's days. */
    fun window(now: Long = System.currentTimeMillis(), tz: TimeZone = TimeZone.getDefault()): Pair<Long, Long?>? = when (this) {
        TODAY -> PartyWhen.TODAY.window(now, tz)?.let { it.first to it.last }
        YESTERDAY -> PartyWhen.YESTERDAY.window(now, tz)?.let { it.first to it.last }
        WEEK -> PartyWhen.THIS_WEEK.window(now, tz)?.let { it.first to null }
        MONTH -> PartyWhen.THIS_MONTH.window(now, tz)?.let { it.first to null }
        ALL, CUSTOM -> null
    }
}
