package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class PlanScheduleTest {
    private fun day(y: Int, m: Int, d: Int) = Calendar.getInstance().apply { clear(); set(y, m - 1, d) }.timeInMillis

    @Test
    fun `paid on time, the next day is a month after the one that was due`() {
        assertEquals(day(2026, 11, 10), PlanSchedule.suggestNext(day(2026, 10, 10), day(2026, 10, 10) + 3_600_000))
    }

    @Test
    fun `paid late, the day steps on past today and keeps its day of the month`() {
        assertEquals(day(2026, 11, 5), PlanSchedule.suggestNext(day(2026, 8, 5), day(2026, 10, 21)))
    }

    @Test
    fun `the 31st falls to the month's last day, then returns`() {
        assertEquals(day(2027, 2, 28), PlanSchedule.suggestNext(day(2027, 1, 31), day(2027, 1, 31)))
        assertEquals(day(2027, 3, 31), PlanSchedule.suggestNext(day(2027, 1, 31), day(2027, 2, 28)))
    }

    @Test
    fun `no day agreed - a month from today`() {
        assertEquals(day(2026, 11, 21), PlanSchedule.suggestNext(null, day(2026, 10, 21) + 5_000))
    }
}
