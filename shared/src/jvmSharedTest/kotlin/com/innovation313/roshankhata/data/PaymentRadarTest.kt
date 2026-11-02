package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * The Payment Radar (2 Nov 2026): credit paid back oldest first, judged against the shop's own normal, and
 * the next payment's likely days. All in UTC so the days are the same on every CI machine. Made-up figures.
 */
class PaymentRadarTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val day = 24L * 60 * 60 * 1000

    private fun at(y: Int, m: Int, d: Int, h: Int = 12): Long = Calendar.getInstance(utc).apply {
        clear(); set(y, m - 1, d, h, 0, 0)
    }.timeInMillis

    private fun gave(amount: Double, t: Long, party: Long = 1) = RadarRow(party, true, amount, t)
    private fun got(amount: Double, t: Long, party: Long = 1) = RadarRow(party, false, amount, t)

    /** [n] credits of 1,000, each paid back in full after [days] days, a month apart, from 1 Jan 2026. */
    private fun steady(days: Int, n: Int = 4, party: Long = 1): List<RadarRow> = (0 until n).flatMap { i ->
        val t = at(2026, 1, 1) + i * 30L * day
        listOf(gave(1_000.0, t, party), got(1_000.0, t + days * day, party))
    }

    @Test
    fun `credit is paid back oldest first and a sale paid at once is not credit`() {
        val t = at(2026, 3, 1)
        val b = PaymentRadar.book(listOf(
            gave(1_000.0, t), gave(500.0, t + 2 * day),
            got(1_200.0, t + 10 * day),           // clears the first lot (10 days), 200 off the second
            got(300.0, t + 12 * day),             // clears the second (10 days after it was given)
            gave(700.0, t + 20 * day), got(700.0, t + 20 * day + 60_000) // the counter's paid-now sale
        ))
        assertEquals(listOf(10, 10), b.repaidDays)
        assertNull("nothing open", b.openSince)
        assertEquals(0.0, b.balance, 0.001)
    }

    @Test
    fun `money paid ahead is taken by the next credit`() {
        val t = at(2026, 3, 1)
        val b = PaymentRadar.book(listOf(got(500.0, t), gave(300.0, t + day), gave(400.0, t + 2 * day)))
        assertTrue("the first credit came out of the advance", b.repaidDays.isEmpty())
        assertEquals("200 of the second is still owed, since the 3rd", t + 2 * day, b.openSince)
        assertEquals(200.0, b.balance, 0.001)
    }

    @Test
    fun `a customer is judged against this shop's own normal`() {
        val now = at(2026, 6, 1)
        val quick = PaymentRadar.read(steady(10), now, shopDays = 15, tz = utc)
        assertEquals(PaymentRadar.Light.GREEN, quick.light)
        assertEquals(10, quick.typicalDays)
        assertEquals(4, quick.repaid)

        val slow = PaymentRadar.read(steady(30), now, shopDays = 15, tz = utc)
        assertEquals("30 days where the shop's normal is 15", PaymentRadar.Light.AMBER, slow.light)

        val verySlow = PaymentRadar.read(steady(60), now, shopDays = 15, tz = utc)
        assertEquals(PaymentRadar.Light.RED, verySlow.light)

        // The same 30 days at a wholesaler whose customers take 45 is on time.
        assertEquals(PaymentRadar.Light.GREEN, PaymentRadar.read(steady(30), now, shopDays = 45, tz = utc).light)
    }

    @Test
    fun `too little history says nothing, but a long unpaid amount is evidence on its own`() {
        val now = at(2026, 6, 1)
        val once = PaymentRadar.read(steady(10, n = 1), now, shopDays = 15, tz = utc)
        assertEquals(PaymentRadar.Light.NONE, once.light)
        assertNull(once.typicalDays)

        val waiting = PaymentRadar.read(listOf(gave(2_000.0, now - 40 * day)), now, shopDays = 15, tz = utc)
        assertEquals("40 days unpaid against a 15-day normal", PaymentRadar.Light.AMBER, waiting.light)
        assertEquals(40, waiting.oldestOpenDays)

        val long = PaymentRadar.read(listOf(gave(2_000.0, now - 100 * day)), now, shopDays = 15, tz = utc)
        assertEquals(PaymentRadar.Light.RED, long.light)
    }

    @Test
    fun `a promised day that has passed with money owed is at least amber`() {
        val now = at(2026, 6, 1)
        val rows = steady(10) + gave(800.0, now - 5 * day)
        val r = PaymentRadar.read(rows, now, shopDays = 15, promise = now - 2 * day, tz = utc)
        assertTrue(r.promiseOverdue)
        assertEquals(PaymentRadar.Light.AMBER, r.light)
        assertFalse(PaymentRadar.read(rows, now, shopDays = 15, promise = now + 3 * day, tz = utc).promiseOverdue)
    }

    @Test
    fun `a customer who pays near the same day each month is told the next one`() {
        val rows = listOf(
            gave(5_000.0, at(2026, 1, 2)),
            got(1_000.0, at(2026, 1, 10)), got(1_000.0, at(2026, 2, 11)),
            got(1_000.0, at(2026, 3, 9)), got(1_000.0, at(2026, 4, 10))
        )
        val r = PaymentRadar.read(rows, at(2026, 5, 2), shopDays = 15, tz = utc)
        assertEquals(10, r.payDay)
        assertEquals("from the 8th", at(2026, 5, 8, 0), r.nextFrom)
        assertEquals("to the 12th", at(2026, 5, 12, 0), r.nextTo)
    }

    @Test
    fun `otherwise the next payment follows his usual gap from the last one`() {
        val start = at(2026, 5, 1)
        val rows = listOf(gave(10_000.0, start)) + (1..4).map { got(500.0, start + it * 7 * day) }
        val now = start + 4 * 7 * day + 3 * day // three days after his last payment
        val r = PaymentRadar.read(rows, now, shopDays = 15, tz = utc)
        assertNull("weekly, not monthly", r.payDay)
        assertNotNull(r.nextFrom)
        val due = start + 5 * 7 * day
        assertTrue("the window holds the day a week after the last", r.nextFrom!! <= due && r.nextTo!! >= DayMath.startOfDay(due, utc))
        assertTrue("never before today", r.nextFrom!! >= DayMath.startOfDay(now, utc))
    }

    @Test
    fun `nothing owed means no next payment to expect`() {
        val r = PaymentRadar.read(steady(10), at(2026, 6, 1), shopDays = 15, tz = utc)
        assertNull(r.nextFrom)
        assertNull(r.oldestOpenDays)
    }

    @Test
    fun `the shop's normal needs enough repaid credit across its customers`() {
        assertNull(PaymentRadar.shopTypicalDays(steady(10, n = 3, party = 1) + steady(20, n = 3, party = 2) + steady(30, n = 1, party = 3)))
        val enough = steady(10, n = 4, party = 1) + steady(20, n = 4, party = 2)
        assertEquals("the median of 10s and 20s", 15, PaymentRadar.shopTypicalDays(enough))
    }

    @Test
    fun `the last day of the month and the first are one habit`() {
        val pays = listOf(at(2026, 1, 31), at(2026, 3, 1), at(2026, 3, 31), at(2026, 4, 30))
        assertNotNull(PaymentRadar.payDayOf(pays, utc))
    }
}
