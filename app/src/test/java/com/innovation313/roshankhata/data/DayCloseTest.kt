package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DayCloseTest {
    @Test
    fun `expected is opening plus in minus out, to the paisa`() {
        assertEquals(48500.0, DayCloseMath.expected(10000.0, 52500.10, 14000.10), 0.0)
    }

    @Test
    fun `a short drawer is negative`() {
        val d = DayClose(day = 1, opening = 10000.0, cashIn = 40000.0, cashOut = 1500.0, counted = 48000.0)
        assertEquals(48500.0, d.expected, 0.0)
        assertEquals(-500.0, d.difference, 0.0)
    }

    @Test
    fun `a close in the small hours belongs to the working day before`() {
        val utc = java.util.TimeZone.getTimeZone("UTC")
        val day = 20_000L
        val start = day * 24L * 60 * 60 * 1000
        assertEquals(day - 1, DayCloseMath.businessDayOf(start + 20 * 60_000L, utc))
        assertEquals(day - 1, DayCloseMath.businessDayOf(start + 3 * 3_600_000L + 59 * 60_000L, utc))
        assertEquals(day, DayCloseMath.businessDayOf(start + 4 * 3_600_000L, utc))
        assertEquals(day, DayCloseMath.businessDayOf(start + 22 * 3_600_000L + 30 * 60_000L, utc))
    }
}
