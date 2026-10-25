package com.innovation313.roshankhata.desktop

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The Closed, Hours and Ramazan posters on Windows printed the defaults —
 * 9:00, a 1:00 to 2:30 break, the country's day off — with no way to change
 * them (25 Oct 2026, the audit). The weekly holidays and times are kept.
 */
class PosterTimesTest {

    @Test
    fun `weekly holidays are kept and read back`() {
        assertEquals(setOf(Calendar.FRIDAY, Calendar.SUNDAY), posterDays("6,1"))
        assertEquals(emptySet<Int>(), posterDays(""))
        assertNull("never kept: the default stands", posterDays(null))
        assertEquals(setOf(Calendar.MONDAY), posterDays("2, 9, x"))
    }

    @Test
    fun `a typed time is read as the poster's minutes`() {
        assertEquals(13 * 60 + 30, posterMinutes("13:30"))
        assertEquals("21:30", posterClock(21 * 60 + 30))
    }
}
