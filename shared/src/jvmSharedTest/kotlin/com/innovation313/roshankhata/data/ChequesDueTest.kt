package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * "A cheque is due" (14 Nov 2026): the one rule the bell, the daily count and the Cheque reminders automation
 * share. Made-up names and sums.
 */
class ChequesDueTest {

    private val karachi = TimeZone.getTimeZone("Asia/Karachi")
    private val berlin = TimeZone.getTimeZone("Europe/Berlin")

    private fun at(tz: TimeZone, y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0): Long =
        Calendar.getInstance(tz).apply { clear(); set(y, m - 1, d, h, min) }.timeInMillis

    private fun cheque(
        id: Long, dueDate: Long, amount: Double = 1000.0, received: Boolean = true,
        status: Int = ChequeStatus.PENDING, party: Long = id
    ) = Cheque(id = id, partyId = party, amount = amount, isReceived = received, dueDate = dueDate, status = status)

    @Test
    fun `today's cheque is due and not late, yesterday's is both`() {
        val now = at(karachi, 2026, 11, 14, 8)
        val end = ChequesDue.endOfToday(now, karachi)
        val today = cheque(1, at(karachi, 2026, 11, 14))
        val yesterday = cheque(2, at(karachi, 2026, 11, 13))
        assertTrue(ChequesDue.isDue(today, end))
        assertFalse("written for today, not past its day", ChequesDue.isLate(today, now, karachi))
        assertTrue(ChequesDue.isDue(yesterday, end))
        assertTrue(ChequesDue.isLate(yesterday, now, karachi))
    }

    @Test
    fun `tomorrow's cheque is not due`() {
        val now = at(karachi, 2026, 11, 14, 23, 59)
        val end = ChequesDue.endOfToday(now, karachi)
        assertFalse(ChequesDue.isDue(cheque(1, at(karachi, 2026, 11, 15)), end))
        assertTrue("the last minute of today is still today", ChequesDue.isDue(cheque(2, at(karachi, 2026, 11, 14, 23, 59)), end))
    }

    @Test
    fun `a settled cheque is never due whatever day it says`() {
        val now = at(karachi, 2026, 11, 14, 8)
        val end = ChequesDue.endOfToday(now, karachi)
        val old = at(karachi, 2026, 9, 1)
        assertFalse(ChequesDue.isDue(cheque(1, old, status = ChequeStatus.CLEARED), end))
        assertFalse(ChequesDue.isDue(cheque(2, old, status = ChequeStatus.BOUNCED), end))
        assertFalse(ChequesDue.isDue(cheque(3, old, status = ChequeStatus.CANCELLED), end))
        assertTrue(ChequesDue.isDue(cheque(4, old), end))
    }

    @Test
    fun `on the days the clocks move tomorrow's cheque is still tomorrow's`() {
        // Berlin put its clocks back on 25 Oct 2026 (a 25-hour day) and forward on 29 Mar 2026 (a 23-hour day).
        // `startOfDay + 24 h` stops an hour short of tomorrow on the long day and would miss a cheque dated 23:30; on the
        // short day it lands an hour into the NEXT day and would read tomorrow's midnight cheque as due today.
        val longDay = at(berlin, 2026, 10, 25, 9)
        val longEnd = ChequesDue.endOfToday(longDay, berlin)
        assertEquals("the first moment of 26 Oct", at(berlin, 2026, 10, 26), longEnd)
        assertFalse(ChequesDue.isDue(cheque(1, at(berlin, 2026, 10, 26)), longEnd))
        assertTrue(ChequesDue.isDue(cheque(2, at(berlin, 2026, 10, 25, 23, 30)), longEnd))

        val shortDay = at(berlin, 2026, 3, 29, 9)
        val shortEnd = ChequesDue.endOfToday(shortDay, berlin)
        assertEquals("the first moment of 30 Mar", at(berlin, 2026, 3, 30), shortEnd)
        assertTrue(ChequesDue.isDue(cheque(3, at(berlin, 2026, 3, 29, 23, 30)), shortEnd))
        assertFalse(ChequesDue.isDue(cheque(4, at(berlin, 2026, 3, 30)), shortEnd))
    }

    @Test
    fun `the summary counts each direction with its sum and says how many are past their day`() {
        val now = at(karachi, 2026, 11, 14, 8)
        val cheques = listOf(
            cheque(1, at(karachi, 2026, 11, 14), 5000.0, received = true),
            cheque(2, at(karachi, 2026, 11, 10), 2500.0, received = true),
            cheque(3, at(karachi, 2026, 11, 14), 700.0, received = false),
            cheque(4, at(karachi, 2026, 11, 15), 9999.0, received = true),
            cheque(5, at(karachi, 2026, 11, 1), 123.0, received = false, status = ChequeStatus.CLEARED)
        )
        val s = ChequesDue.summarise(cheques, emptyMap(), now, karachi)
        assertEquals(2, s.received)
        assertEquals(7500.0, s.receivedTotal, 0.001)
        assertEquals(1, s.issued)
        assertEquals(700.0, s.issuedTotal, 0.001)
        assertEquals("only the 10 Nov one is past its day", 1, s.late)
        assertEquals(3, s.count)
    }

    @Test
    fun `nothing due is the empty summary`() {
        val now = at(karachi, 2026, 11, 14, 8)
        assertEquals(ChequesDue.Summary.NONE, ChequesDue.summarise(emptyList(), emptyMap(), now, karachi))
        assertEquals(ChequesDue.Summary.NONE, ChequesDue.summarise(listOf(cheque(1, at(karachi, 2026, 11, 20))), emptyMap(), now, karachi))
        assertEquals(0, ChequesDue.Summary.NONE.count)
    }

    @Test
    fun `names are the oldest cheque's first, said once, three at most, none blank`() {
        val now = at(karachi, 2026, 11, 14, 8)
        val names = mapOf(1L to "Test Customer A", 2L to "Test Customer B", 3L to "  ", 4L to "Test Supplier C", 5L to "Test Customer E")
        val cheques = listOf(
            cheque(10, at(karachi, 2026, 11, 14), party = 5),
            cheque(11, at(karachi, 2026, 11, 12), party = 2),
            cheque(12, at(karachi, 2026, 11, 13), party = 3),         // blank name: left out
            cheque(13, at(karachi, 2026, 11, 11), party = 2),         // the same party again: said once
            cheque(14, at(karachi, 2026, 11, 9), party = 1),
            cheque(15, at(karachi, 2026, 11, 13), party = 4),
            cheque(16, at(karachi, 2026, 11, 13), party = 99)         // a party with no name known: left out
        )
        val s = ChequesDue.summarise(cheques, names, now, karachi)
        assertEquals(listOf("Test Customer A", "Test Customer B", "Test Supplier C"), s.names)
    }

    @Test
    fun `the bell's old rule and this one agree on a plain day`() {
        // The rule the bell used before this file was `status == PENDING && dueDate < startOfDay + 24 h`. On a day with
        // 24 hours the two must name the same cheques, or moving the bell to this rule would have changed it.
        val now = at(karachi, 2026, 11, 14, 8)
        val t0 = DayMath.startOfDay(now, karachi)
        val oldEnd = t0 + 24L * 60 * 60 * 1000
        val end = ChequesDue.endOfToday(now, karachi)
        assertEquals(oldEnd, end)
        for (offsetHours in listOf(-72L, -24L, -1L, 0L, 1L, 12L, 23L, 24L, 25L, 48L)) {
            val c = cheque(1, t0 + offsetHours * 60 * 60 * 1000)
            assertEquals("offset $offsetHours h", c.dueDate < oldEnd, ChequesDue.isDue(c, end))
        }
    }
}
