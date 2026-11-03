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

    private fun gave(amount: Double, t: Long, party: Long = 1) = LedgerPoint(party, t, amount, true)
    private fun got(amount: Double, t: Long, party: Long = 1) = LedgerPoint(party, t, amount, false)

    /** [n] credits of 1,000, each paid back in full after [days] days, a month apart, from 1 Jan 2026. */
    private fun steady(days: Int, n: Int = 4, party: Long = 1): List<LedgerPoint> = (0 until n).flatMap { i ->
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
        // Past its day and the promise's two days of grace (3 Nov 2026 audit: the record and the light agree).
        val r = PaymentRadar.read(rows, now, shopDays = 15, promise = now - 4 * day, tz = utc)
        assertTrue(r.promiseOverdue)
        assertFalse("within the grace days, not yet", PaymentRadar.read(rows, now, shopDays = 15, promise = now - 1 * day, tz = utc).promiseOverdue)
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
    fun `promises kept and broken come from the promised days written, and a moved one is not counted`() {
        val payments = listOf(at(2026, 1, 11))
        val promises = listOf(
            PaymentRadar.Promise(at(2026, 1, 1), at(2026, 1, 10)),  // paid on the 11th: kept (two days' grace)
            PaymentRadar.Promise(at(2026, 2, 1), at(2026, 2, 10)),  // nothing came: broken
            PaymentRadar.Promise(at(2026, 3, 1), at(2026, 3, 10)),  // moved on the 5th, before its day: not counted
            PaymentRadar.Promise(at(2026, 3, 5), at(2026, 3, 20)),  // nothing came: broken
            PaymentRadar.Promise(at(2026, 5, 1), at(2026, 6, 10))   // still ahead: not counted
        )
        assertEquals(1 to 2, PaymentRadar.promiseRecord(promises, payments, at(2026, 5, 2), utc))
    }

    @Test
    fun `broken promises and falling behind his own rhythm raise the light`() {
        val now = at(2026, 6, 1)
        val owing = listOf(gave(5_000.0, at(2026, 4, 1)))
        val broken = listOf(
            PaymentRadar.Promise(at(2026, 4, 2), at(2026, 4, 10)),
            PaymentRadar.Promise(at(2026, 4, 20), at(2026, 5, 1)),
            PaymentRadar.Promise(at(2026, 5, 5), at(2026, 5, 15))
        )
        val r = PaymentRadar.read(owing, now, shopDays = 45, promises = broken, tz = utc)
        assertEquals(0, r.promisesKept)
        assertEquals(3, r.promisesBroken)
        assertEquals("three broken, none kept", PaymentRadar.Light.RED, r.light)

        // A weekly payer silent for a month: Follow-up calls him late, and so does the Radar.
        val start = at(2026, 4, 1)
        val weekly = listOf(gave(10_000.0, start)) + (1..3).map { got(500.0, start + it * 7 * day) }
        val behind = PaymentRadar.read(weekly, start + 51 * day, shopDays = 45, tz = utc)
        assertTrue(behind.behindHabit)
        assertEquals(PaymentRadar.Light.AMBER, behind.light)
    }

    @Test
    fun `the morning list's likely names are those inside their usual paying days`() {
        val rows = listOf(
            gave(5_000.0, at(2026, 1, 2)),
            got(1_000.0, at(2026, 1, 10)), got(1_000.0, at(2026, 2, 11)),
            got(1_000.0, at(2026, 3, 9)), got(1_000.0, at(2026, 4, 10))
        )
        assertEquals(setOf(1L), PaymentRadar.likelyNow(rows, 15, emptyMap(), at(2026, 5, 9), utc))
        assertTrue("not on the 20th", PaymentRadar.likelyNow(rows, 15, emptyMap(), at(2026, 5, 20), utc).isEmpty())
    }

    @Test
    fun `a sale paid on the spot does not pay an old debt`() {
        // 3 Nov 2026 audit, M26: through FIFO a cash buy cleared the oldest lot, and a 61-day debt read as 24 days.
        val now = at(2026, 11, 1)
        val old = LedgerPoint(1, at(2026, 9, 1), 2_000.0, true, id = 1)
        val sales = (0..2).flatMap { i ->
            val t = at(2026, 10, 1 + 7 * i)
            listOf(LedgerPoint(1, t, 1_000.0, true, id = 10L + i, pairedEntryId = 20L + i),
                LedgerPoint(1, t + 1, 1_000.0, false, id = 20L + i, pairedEntryId = 10L + i))
        }
        val r = PaymentRadar.read(listOf(old) + sales, now, shopDays = 15, tz = utc)
        assertEquals("the old debt is still the oldest", 61, r.oldestOpenDays)
        assertEquals(0, r.repaid)
    }

    @Test
    fun `a loan of goodwill is not judged as trade credit`() {
        // M34: a Qarz-e-Hasna loan of 30,000 read "pays very late" after 120 days.
        val now = at(2026, 11, 1)
        val loan = LedgerPoint(1, at(2026, 7, 1), 30_000.0, true, id = 1, isQarzeHasna = true)
        assertEquals(PaymentRadar.Light.NONE, PaymentRadar.read(listOf(loan), now, shopDays = 15, tz = utc).light)
    }

    @Test
    fun `a customer paying his instalments on time is not called late`() {
        // M27: 50,000 cleared 5,000 a month, never late, read RED from day 95.
        val start = at(2026, 1, 1)
        val rows = listOf(gave(50_000.0, start)) + (1..4).map { got(5_000.0, start + it * 30L * day) }
        val r = PaymentRadar.read(rows, start + 125 * day, shopDays = 20, tz = utc)
        assertFalse(r.behindHabit)
        assertTrue("not RED for an old lot he is paying down", r.light != PaymentRadar.Light.RED)
    }

    @Test
    fun `month-end payers are told the end of the month`() {
        // M28: the 31st, 1st, 2nd and 30th made "the 16th".
        val pays = listOf(at(2026, 1, 31), at(2026, 3, 1), at(2026, 4, 2), at(2026, 4, 30))
        val day = PaymentRadar.payDayOf(pays, utc)!!
        assertTrue("near the month's turn, not mid-month: $day", day >= 29 || day <= 2)
    }

    @Test
    fun `a promised day still ahead keeps him off the likely list`() {
        // M25: "usually pays around now" overrode his own promised day.
        val rows = listOf(
            gave(5_000.0, at(2026, 1, 2)),
            got(1_000.0, at(2026, 1, 10)), got(1_000.0, at(2026, 2, 11)),
            got(1_000.0, at(2026, 3, 9)), got(1_000.0, at(2026, 4, 10))
        )
        val now = at(2026, 5, 9)
        assertTrue(PaymentRadar.likelyNow(rows, 15, mapOf(1L to at(2026, 5, 30)), now, utc).isEmpty())
    }

    @Test
    fun `one payment keeps one promise and a promise taken back is no promise`() {
        // M29: three reschedules and one small payment read "3 of 3 kept".
        val payments = listOf(at(2026, 1, 5))
        val promises = listOf(
            PaymentRadar.Promise(at(2026, 1, 1), at(2026, 1, 6)),
            PaymentRadar.Promise(at(2026, 1, 2), at(2026, 1, 7)),
            PaymentRadar.Promise(at(2026, 1, 3), 0L)
        )
        val (kept, broken) = PaymentRadar.promiseRecord(promises, payments, at(2026, 3, 1), utc)
        assertEquals(1, kept)
        assertEquals(0, broken)
    }

    @Test
    fun `the last day of the month and the first are one habit`() {
        val pays = listOf(at(2026, 1, 31), at(2026, 3, 1), at(2026, 3, 31), at(2026, 4, 30))
        assertNotNull(PaymentRadar.payDayOf(pays, utc))
    }
}
