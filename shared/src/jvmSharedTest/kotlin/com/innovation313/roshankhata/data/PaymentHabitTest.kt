package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/**
 * The payment habit on the Follow-up screen. Every case is a ledger a
 * shopkeeper would recognise, read in UTC so a day is exactly a day.
 */
class PaymentHabitTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_000 * day          // "today" is day 1000

    private fun gave(d: Int, amount: Double) = LedgerPoint(1, d * day + 3_600_000, amount, true)
    private fun got(d: Int, amount: Double) = LedgerPoint(1, d * day + 7_200_000, amount, false)

    /** Takes 10,000 on credit, pays 2,000 every 30 days, last paid on day 940. */
    private fun monthlyPayer() = listOf(
        gave(800, 10_000.0),
        got(850, 2_000.0), got(880, 2_000.0), got(910, 2_000.0), got(940, 2_000.0)
    )

    @Test
    fun `a monthly payer sixty days quiet is late`() {
        val h = PaymentHabit.of(monthlyPayer(), now, utc)!!
        assertEquals(30, h.typicalDays)
        assertEquals(60, h.waitingDays)
        assertTrue(h.isLate)
    }

    @Test
    fun `the same payer inside their habit is not late`() {
        val h = PaymentHabit.of(monthlyPayer(), 960 * day, utc)!!
        assertEquals(20, h.waitingDays)
        assertFalse(h.isLate)
    }

    /** 1.5x the habit is 45 days; 44 is not yet late. */
    @Test
    fun `just under half again the habit is not late`() {
        assertFalse(PaymentHabit.of(monthlyPayer(), 984 * day, utc)!!.isLate)
        assertTrue(PaymentHabit.of(monthlyPayer(), 986 * day, utc)!!.isLate)
    }

    /** A weekly payer two days over is not flagged: the 7-day floor. */
    @Test
    fun `a weekly payer a few days over is not flagged`() {
        val weekly = listOf(
            gave(900, 5_000.0),
            got(907, 500.0), got(914, 500.0), got(921, 500.0), got(928, 500.0)
        )
        val h = PaymentHabit.of(weekly, (928 + 12) * day, utc)!!
        assertEquals(7, h.typicalDays)
        assertFalse("12 days on a 7-day habit is only 5 over", h.isLate)
        assertTrue(PaymentHabit.of(weekly, (928 + 15) * day, utc)!!.isLate)
    }

    @Test
    fun `fewer than three payment days is not a habit`() {
        val two = listOf(gave(800, 5_000.0), got(850, 1_000.0), got(880, 1_000.0))
        assertNull(PaymentHabit.of(two, now, utc))
    }

    @Test
    fun `two payments on one day count as one`() {
        val split = listOf(
            gave(800, 10_000.0),
            got(850, 500.0), got(850, 500.0),   // one visit, two entries
            got(880, 1_000.0)
        )
        assertNull("only two payment DAYS", PaymentHabit.of(split, now, utc))
    }

    @Test
    fun `a settled account has no habit to show`() {
        val settled = monthlyPayer() + got(950, 2_000.0)
        assertNull(PaymentHabit.of(settled, now, utc))
    }

    /**
     * Paid everything off on day 950, took new credit on day 995. Waiting is
     * five days, not fifty: the clock starts at the new debt.
     */
    @Test
    fun `new credit after a clear account restarts the clock`() {
        val ledger = monthlyPayer() + got(950, 2_000.0) + gave(995, 3_000.0)
        val h = PaymentHabit.of(ledger, now, utc)!!
        assertEquals(5, h.waitingDays)
        assertFalse(h.isLate)
    }

    /** One very long gap does not redefine a monthly customer. */
    @Test
    fun `median ignores one long gap`() {
        val ledger = listOf(
            gave(500, 20_000.0),
            got(600, 1_000.0), got(630, 1_000.0), got(660, 1_000.0), got(850, 1_000.0)
        )
        assertEquals(30, PaymentHabit.of(ledger, now, utc)!!.typicalDays)
    }

    @Test
    fun `forAll keeps parties apart`() {
        val other = monthlyPayer().map { it.copy(partyId = 2) }
        val result = PaymentHabit.forAll(monthlyPayer() + other, now, utc)
        assertEquals(setOf(1L, 2L), result.keys)
    }
}
