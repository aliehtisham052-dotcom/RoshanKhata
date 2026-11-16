package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * One window rule (16 Nov 2026, audit D-04 / D-05, [DaySpans]): a moment at exactly 00:00:00.000 is in the day that starts at
 * it; a day is the calendar's, 23 or 25 hours where the clocks move; the bell's "yesterday's drawer" is the working day
 * Close the day files a count under. The zones are passed in, never set on the machine, so nothing leaks into other tests.
 */
class DaySpansTest {

    private val ny = TimeZone.getTimeZone("America/New_York")
    private val beirut = TimeZone.getTimeZone("Asia/Beirut")
    private val karachi = TimeZone.getTimeZone("Asia/Karachi")
    private val hour = 60L * 60 * 1000

    private fun midnight(tz: TimeZone, y: Int, m: Int, d: Int): Long = Calendar.getInstance(tz).apply {
        clear(); set(y, m - 1, d, 0, 0, 0)
    }.timeInMillis.let { DayMath.startOfDay(it + 12 * hour, tz) }

    @Test fun `a moment at exactly midnight lands in the day that starts at it, through the six sums' shape`() {
        for (tz in listOf(karachi, ny, beirut)) {
            val day = midnight(tz, 2026, 11, 16)
            val next = DaySpans.nextDayStart(day, tz)
            val (a, b) = DaySpans.leftOpen(day, next)
            // The six sums read (a, b]: what they count must be exactly [day, next).
            fun counted(t: Long) = t > a && t <= b
            assertTrue("${tz.id}: midnight is this day's", counted(day))
            assertFalse("${tz.id}: the next midnight is the next day's", counted(next))
            assertTrue("${tz.id}: the last millisecond is this day's", counted(next - 1))
            assertFalse("${tz.id}: the millisecond before midnight is yesterday's", counted(day - 1))
            assertEquals(day, DaySpans.previousDayStart(next, tz))
        }
    }

    @Test fun `a day is the calendar's, 23 or 25 hours where the clocks move`() {
        // New York: 8 March 2026 has 23 hours, 1 November 2026 has 25.
        val spring = midnight(ny, 2026, 3, 8)
        assertEquals(23 * hour, DaySpans.nextDayStart(spring, ny) - spring)
        assertEquals(23 * hour, LedgerAlerts.tomorrowStart(spring, ny) - spring)
        val fall = midnight(ny, 2026, 11, 1)
        assertEquals(25 * hour, DaySpans.nextDayStart(fall, ny) - fall)
        assertEquals(25 * hour, LedgerAlerts.tomorrowStart(fall, ny) - fall)
        assertEquals(midnight(ny, 2026, 11, 4), DaySpans.dayStartAfter(fall + 5 * hour, 3, ny))
        assertEquals(fall, DaySpans.dayStartAfter(fall + 5 * hour, 0, ny))
        // An ordinary day is 24.
        val plain = midnight(karachi, 2026, 11, 16)
        assertEquals(24 * hour, DaySpans.nextDayStart(plain, karachi) - plain)
    }

    @Test fun `a working day holds exactly the moments Close the day files under it`() {
        val quarter = 15L * 60 * 1000
        for ((tz, y, m, d) in listOf(
            Quad(ny, 2026, 3, 8), Quad(ny, 2026, 11, 1), Quad(beirut, 2026, 3, 29), Quad(beirut, 2026, 10, 25), Quad(karachi, 2026, 11, 16)
        )) {
            val start = midnight(tz, y, m, d)
            val day = DayCloseMath.dayOf(start + 12 * hour, tz)
            val (from, to) = DaySpans.workingDay(day, tz)
            var t = start - 2 * 24 * hour
            while (t < start + 3 * 24 * hour) {
                val filed = DayCloseMath.businessDayOf(t, tz) == day
                assertEquals("${tz.id} $y-$m-$d at $t", filed, t >= from && t < to)
                t += quarter
            }
            assertEquals("${tz.id} $y-$m-$d edge", day, DayCloseMath.businessDayOf(from, tz))
            assertEquals("${tz.id} $y-$m-$d edge", day - 1, DayCloseMath.businessDayOf(from - 1, tz))
        }
    }

    @Test fun `yesterday's drawer is the working day before, not the calendar's`() {
        val sat = midnight(karachi, 2026, 11, 21)
        // 02:00 on Saturday is still Friday's working day: the drawer not yet counted is Thursday's, never Friday's.
        val (dayAt2, spanAt2) = LedgerAlerts.yesterdaysDrawer(sat + 2 * hour, karachi)
        assertEquals(DayCloseMath.businessDayOf(sat + 2 * hour, karachi) - 1, dayAt2)
        assertEquals(DayCloseMath.dayOf(sat - 2 * 24 * hour + 12 * hour, karachi), dayAt2)
        // 05:00 on Saturday: Friday's working day, Friday 04:00 to Saturday 04:00.
        val (dayAt5, spanAt5) = LedgerAlerts.yesterdaysDrawer(sat + 5 * hour, karachi)
        assertEquals(DayCloseMath.dayOf(sat - 12 * hour, karachi), dayAt5)
        assertEquals((sat - 24 * hour + 4 * hour) to (sat + 4 * hour), spanAt5)
        assertTrue(spanAt2.second <= spanAt5.first)
        // The day the clocks go back in New York: the working day holds its 25 hours.
        val fall = midnight(ny, 2026, 11, 2)
        val (_, span) = LedgerAlerts.yesterdaysDrawer(fall + 10 * hour, ny)
        assertEquals(25 * hour, span.second - span.first)
    }

    private data class Quad(val tz: TimeZone, val y: Int, val m: Int, val d: Int)
}
