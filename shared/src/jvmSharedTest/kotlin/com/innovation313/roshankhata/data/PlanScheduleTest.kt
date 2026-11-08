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

class FefoShelfCapTest {
    @Test
    fun `batches never hold more than the shelf, the soonest taken first`() {
        val lots = listOf(Fefo.Lot(1, "A", 100L, 10.0), Fefo.Lot(2, "B", 200L, 10.0))
        // 20 by the batches, 12 on the shelf: 8 went out untagged, from the soonest lot.
        val capped = Fefo.capByShelf(lots, 12.0)
        assertEquals(2.0, capped[0].left, 1e-9)
        assertEquals(10.0, capped[1].left, 1e-9)
        assertEquals(lots, Fefo.capByShelf(lots, 25.0))
        assertEquals(lots, Fefo.capByShelf(lots, null))
        assertEquals(0.0, Fefo.capByShelf(lots, 0.0).sumOf { it.left }, 1e-9)
    }
}

class MergeMatchTest {
    private fun party(id: Long, name: String, phone: String?, deleted: Boolean = false) =
        Party(id = id, name = name, phone = phone, isCustomer = true, isDeleted = deleted)

    @Test
    fun `two customers of one name and no phone are not guessed into one`() {
        val here = listOf(party(1, "Aslam", null), party(2, "Aslam", null))
        assertEquals(null, BookMerge.matchParty(party(9, "Aslam", null), here))
        // The same row (same id and name) is still the same customer.
        assertEquals(2L, BookMerge.matchParty(party(2, "Aslam", null), here)?.id)
    }

    @Test
    fun `a binned customer is not matched`() {
        val here = listOf(party(1, "Bilal", "03001234567", deleted = true))
        assertEquals(null, BookMerge.matchParty(party(5, "Bilal", "03001234567"), here))
    }

    @Test
    fun `the same name and phone is the same customer`() {
        val here = listOf(party(1, "Bilal", "0300 1234567"), party(2, "Bilal", "0333 7654321"))
        assertEquals(2L, BookMerge.matchParty(party(8, "bilal", "+92 333 7654321"), here)?.id)
    }
}
