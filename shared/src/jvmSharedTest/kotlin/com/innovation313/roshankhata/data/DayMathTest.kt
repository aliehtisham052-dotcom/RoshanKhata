package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * DayMath against the two shapes it replaced (29 Oct 2026, the cleanup): the
 * day number `floorDiv(millis + offset, DAY)` and the Calendar midnight. Asia/Karachi
 * has had no clock change since 2009, so the figures below are fixed.
 */
class DayMathTest {

    private val karachi = TimeZone.getTimeZone("Asia/Karachi")

    /** 12 Oct 2026 00:00:00.000 PKT (UTC+5). */
    private val midnight = Calendar.getInstance(karachi).apply {
        clear(); set(2026, Calendar.OCTOBER, 12, 0, 0, 0)
    }.timeInMillis

    /** What every copy computed before, written out as it was. */
    private fun oldDayNumber(millis: Long, tz: TimeZone) = (millis + tz.getOffset(millis)).floorDiv(24L * 60 * 60 * 1000)
    private fun oldStartOfDay(millis: Long, tz: TimeZone) = Calendar.getInstance(tz).apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun `midnight and the last millisecond are one day, the next millisecond the next`() {
        val lastMs = midnight + DayMath.DAY - 1
        assertEquals(DayMath.dayNumber(midnight, karachi), DayMath.dayNumber(lastMs, karachi))
        assertEquals(DayMath.dayNumber(midnight, karachi) + 1, DayMath.dayNumber(lastMs + 1, karachi))
        assertEquals(DayMath.dayNumber(midnight, karachi) - 1, DayMath.dayNumber(midnight - 1, karachi))
    }

    @Test
    fun `the day number is days since the epoch in the zone`() {
        // 12 Oct 2026 is day 20738 since 1 Jan 1970 (Karachi's midnight is 19:00 UTC the day before).
        assertEquals(20738L, DayMath.dayNumber(midnight, karachi))
        assertEquals(20737L, DayMath.dayNumber(midnight, TimeZone.getTimeZone("UTC")))
    }

    @Test
    fun `startOfDay is local midnight at every edge of the day`() {
        for (at in listOf(midnight, midnight + 1, midnight + 12 * 3_600_000L, midnight + DayMath.DAY - 1)) {
            assertEquals(midnight, DayMath.startOfDay(at, karachi))
        }
        assertEquals(midnight + DayMath.DAY, DayMath.startOfDay(midnight + DayMath.DAY, karachi))
        assertEquals(midnight - DayMath.DAY, DayMath.startOfDay(midnight - 1, karachi))
    }

    @Test
    fun `the shared rule equals the copies it replaced, hour by hour across a week`() {
        for (tz in listOf(karachi, TimeZone.getTimeZone("UTC"), TimeZone.getTimeZone("Asia/Kolkata"))) {
            var at = midnight - 3 * DayMath.DAY
            while (at < midnight + 4 * DayMath.DAY) {
                assertEquals(oldDayNumber(at, tz), DayMath.dayNumber(at, tz))
                assertEquals(oldStartOfDay(at, tz), DayMath.startOfDay(at, tz))
                at += 59 * 60_000L + 999
            }
        }
    }

    @Test
    fun `the two shapes agree on a day without a clock change`() {
        val at = midnight + 7 * 3_600_000L + 123
        assertEquals(DayCloseMath.startOf(DayMath.dayNumber(at, karachi), karachi), DayMath.startOfDay(at, karachi))
        // The 04:00 rule stays its own: 02:30 on the 12th is the 11th's working day.
        assertEquals(DayMath.dayNumber(midnight, karachi) - 1, DayCloseMath.businessDayOf(midnight + 150 * 60_000L, karachi))
    }

    @Test
    fun `the callers that wrap it keep their shape`() {
        val at = midnight + 9 * 3_600_000L
        val tzWas = TimeZone.getDefault()
        TimeZone.setDefault(karachi)
        try {
            assertEquals(DayMath.startOfDay(at, karachi), Payroll.dayOf(at))
            assertEquals(DayMath.dayNumber(at, karachi), DayCloseMath.dayOf(at))
        } finally {
            TimeZone.setDefault(tzWas)
        }
    }
}
