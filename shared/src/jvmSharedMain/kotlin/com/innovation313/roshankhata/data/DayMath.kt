package com.innovation313.roshankhata.data

import java.util.Calendar
import java.util.TimeZone

/**
 * "Which day is this" — the one rule for both platforms (29 Oct 2026, the
 * cleanup: thirteen copies of the same two lines lived in the phone, the
 * computer and the shared module, so a zone or DST fix would have landed in
 * one and missed twelve).
 *
 * Two shapes of the same question, kept exactly as the copies computed them:
 *
 *  - [dayNumber]: the local calendar day as a count of days since the epoch,
 *    `floorDiv(millis + offset, DAY)` — what [DayClose.day] and the reminder
 *    logs key on, and what a "today == that day" comparison wants.
 *  - [startOfDay]: that day's first millisecond, by the calendar (hour,
 *    minute, second and millisecond set to zero) — what every "from
 *    midnight" query and every day heading wants.
 *
 * The two agree on every ordinary day; on the day a zone changes its clocks
 * [startOfDay] is the real local midnight and [dayNumber]'s offset is the
 * one in force at the moment given, exactly as before. Neither reads the
 * shop's calendar ([ShopCalendar]): a day is a civil day of the zone.
 *
 * [DayCloseMath.businessDayOf] (the small hours belong to the evening before)
 * is a different rule and stays its own; it calls [dayNumber] for the plain day.
 */
object DayMath {
    const val DAY = 24L * 60 * 60 * 1000

    /** The local calendar day of [millis] as a day count since the epoch, in [tz]. */
    fun dayNumber(millis: Long, tz: TimeZone = TimeZone.getDefault()): Long =
        Math.floorDiv(millis + tz.getOffset(millis), DAY)

    /** The first millisecond of the local day holding [millis], in [tz]. */
    fun startOfDay(millis: Long, tz: TimeZone = TimeZone.getDefault()): Long =
        Calendar.getInstance(tz).apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
