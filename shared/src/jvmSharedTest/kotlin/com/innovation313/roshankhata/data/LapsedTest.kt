package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Customers who stopped coming (3 Nov 2026): a regular past twice his usual gap, never sooner than a month. */
class LapsedTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_000 * day

    private fun habit(id: Long, days: Int, firstDaysAgo: Int, lastDaysAgo: Int, total: Double = 1_000.0) =
        BuyingHabit(id, "C$id", null, days, now - firstDaysAgo * day, now - lastDaysAgo * day, total)

    @Test
    fun `a weekly buyer gone six weeks has stopped, one gone ten days has not`() {
        // Ten buying days over 63 days: every 7 days.
        val gone = habit(1, 10, 105, 42)
        val late = habit(2, 10, 73, 10)
        val list = Lapsed.of(listOf(gone, late), now)
        assertEquals(listOf(1L), list.map { it.habit.partyId })
        assertEquals(7, list.single().usualGapDays)
        assertEquals(42, list.single().sinceDays)
    }

    @Test
    fun `a monthly buyer is missed only past two months, and an old account is not on the list`() {
        val monthly = habit(1, 6, 200, 50)   // every 30 days, 50 days gone: within twice his gap
        val monthlyGone = habit(2, 6, 220, 70) // every 30 days, 70 days gone
        val ancient = habit(3, 6, 1_000, 800)  // past two years: an old account
        val twice = habit(4, 2, 40, 35)        // two buying days are not a habit
        val list = Lapsed.of(listOf(monthly, monthlyGone, ancient, twice), now)
        assertEquals(listOf(2L), list.map { it.habit.partyId })
    }

    @Test
    fun `the biggest buyers come first`() {
        val small = habit(1, 5, 100, 60, total = 2_000.0)
        val big = habit(2, 5, 100, 60, total = 90_000.0)
        val list = Lapsed.of(listOf(small, big), now)
        assertEquals(listOf(2L, 1L), list.map { it.habit.partyId })
        assertTrue(list.all { it.sinceDays >= Lapsed.FLOOR_DAYS })
    }
}
