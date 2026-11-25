package com.innovation313.roshankhata.data

import java.util.TimeZone

/**
 * Windows of time, one convention (16 Nov 2026, audit D-04 / D-05).
 *
 * Every window a screen or Hudhud asks of the book is half-open, `[from, to)`: a moment at exactly 00:00:00.000 belongs to the
 * day that starts at it, never to the day before ([Hudhud.span], [DayMath.startOfDay]). The DAO has 37 queries that read a
 * window that way and six cash sums written the other way, `(from, to]`, because the drawer counts "since the last count, up to
 * now" ([DayCloseMath.drawerByTheBooks]): [KhataDao.cashbookTotalBetween], [KhataDao.cashbookCashBetween],
 * [KhataDao.khataCashBetween], [KhataDao.billsAndStaffCashBetween], [KhataDao.staffPaidBetween] and
 * [KhataDao.cashbookExpensesBetween]. A caller that hands one of those six a `[from, to)` window counts the entry at the next
 * day's first moment and drops the one at this day's. The wrappers below take `[from, to)` and move both ends one millisecond
 * back inside ([leftOpen]); a new caller with a day, a week or a month uses them, and the drawer keeps calling the six directly.
 *
 * A day is the calendar's ([nextDayStart]): 23 or 25 hours on the day the clocks change, never a fixed 24 hours. A working day
 * ([workingDay]) is the drawer's: from 04:00 to 04:00, by [DayCloseMath.businessDayOf]'s own rule.
 */
object DaySpans {

    private const val HOUR = 60L * 60 * 1000

    /** `[from, to)` as the six `(from, to]` cash sums read it: one millisecond back at each end. */
    fun leftOpen(from: Long, to: Long): Pair<Long, Long> = (from - 1) to (to - 1)

    /** The first moment of the calendar day after the one holding [millis] (23 or 25 hours on: the calendar, not 24). */
    fun nextDayStart(millis: Long, tz: TimeZone = TimeZone.getDefault()): Long =
        DayMath.startOfDay(DayMath.startOfDay(millis, tz) + 36 * HOUR, tz)

    /** The first moment of the calendar day [days] days after the one holding [millis] (0: that day's own first moment). */
    fun dayStartAfter(millis: Long, days: Int, tz: TimeZone = TimeZone.getDefault()): Long {
        var t = DayMath.startOfDay(millis, tz)
        repeat(days) { t = nextDayStart(t, tz) }
        return t
    }

    /**
     * The calendar day holding [millis] as `[from, to)`: its first moment and the next day's first moment (D8, BC-296). One rule
     * for every "today" a screen shows: the computer's Home took midnight + 24 h, which on a day the clocks change ran an hour
     * into tomorrow or stopped an hour short of midnight.
     */
    fun calendarDay(millis: Long, tz: TimeZone = TimeZone.getDefault()): Pair<Long, Long> =
        DayMath.startOfDay(millis, tz) to nextDayStart(millis, tz)

    /**
     * The end to hand a `[from, to)` query so that [until] itself is in (D8, BC-297): a stretch "up to now" or "up to the
     * close" counts what was written at that very moment, as the drawer's six `(from, to]` sums do. A shift closed at the
     * same millisecond a cash line was written counted the line in the day's drawer and not in the shift's.
     */
    fun through(until: Long): Long = if (until == Long.MAX_VALUE) until else until + 1

    /** The first moment of the calendar day before the one holding [millis]. */
    fun previousDayStart(millis: Long, tz: TimeZone = TimeZone.getDefault()): Long =
        DayMath.startOfDay(DayMath.startOfDay(millis, tz) - 12 * HOUR, tz)

    /**
     * Working day [day] (a [DayCloseMath.businessDayOf] number) as `[from, to)`: from [DayCloseMath.DAY_ENDS_HOUR] hours after its
     * calendar midnight to as long after the next one. Exactly the moments [DayCloseMath.businessDayOf] files under [day], so a
     * close and the money it counts are one day.
     */
    fun workingDay(day: Long, tz: TimeZone = TimeZone.getDefault()): Pair<Long, Long> {
        val shift = DayCloseMath.DAY_ENDS_HOUR * HOUR
        return (DayCloseMath.startOf(day, tz) + shift) to (DayCloseMath.startOf(day + 1, tz) + shift)
    }
}

// The six (from, to] cash sums over a [from, to) window ([DaySpans]). Named "Over" so they are never mistaken for the DAO's own.

/** [KhataDao.cashbookTotalBetween] over `[from, to)`. */
suspend fun KhataDao.cashbookTotalOver(income: Boolean, from: Long, to: Long): Double =
    DaySpans.leftOpen(from, to).let { (a, b) -> cashbookTotalBetween(income, a, b) }

/** [KhataDao.cashbookCashBetween] over `[from, to)`. */
suspend fun KhataDao.cashbookCashOver(income: Boolean, from: Long, to: Long): Double =
    DaySpans.leftOpen(from, to).let { (a, b) -> cashbookCashBetween(income, a, b) }

/** [KhataDao.khataCashBetween] over `[from, to)`. */
suspend fun KhataDao.khataCashOver(given: Boolean, from: Long, to: Long): Double =
    DaySpans.leftOpen(from, to).let { (a, b) -> khataCashBetween(given, a, b) }

/** [KhataDao.billsAndStaffCashBetween] over `[from, to)`. */
suspend fun KhataDao.billsAndStaffCashOver(from: Long, to: Long): Double =
    DaySpans.leftOpen(from, to).let { (a, b) -> billsAndStaffCashBetween(a, b) }

/** [KhataDao.staffPaidBetween] over `[from, to)`. */
suspend fun KhataDao.staffPaidOver(from: Long, to: Long): Double =
    DaySpans.leftOpen(from, to).let { (a, b) -> staffPaidBetween(a, b) }

/** [KhataDao.cashbookExpensesBetween] over `[from, to)`. */
suspend fun KhataDao.cashbookExpensesOver(from: Long, to: Long): Double =
    DaySpans.leftOpen(from, to).let { (a, b) -> cashbookExpensesBetween(a, b) }
