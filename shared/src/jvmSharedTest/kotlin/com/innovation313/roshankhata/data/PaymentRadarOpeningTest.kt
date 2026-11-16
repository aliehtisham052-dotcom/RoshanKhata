package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * An opening balance (EntryKind.OPENING, schema 45, the owner's D-02) in the Payment Radar (16 Nov 2026): money owed from before the
 * book is debt — in the balance and in what has waited longest — but no measure of how fast a customer pays: its day is the day it
 * was brought in. It is left out of his pace, out of the shop's normal and out of the payment days of his rhythm. Invented figures.
 */
class PaymentRadarOpeningTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val day = 24L * 60 * 60 * 1000

    private fun at(y: Int, m: Int, d: Int, h: Int = 12): Long = Calendar.getInstance(utc).apply {
        clear(); set(y, m - 1, d, h, 0, 0)
    }.timeInMillis

    private fun gave(amount: Double, t: Long, party: Long = 1, opening: Boolean = false) = LedgerPoint(party, t, amount, true, isOpening = opening)
    private fun got(amount: Double, t: Long, party: Long = 1, opening: Boolean = false) = LedgerPoint(party, t, amount, false, isOpening = opening)

    @Test fun `an opening balance paid back is no lot of his pace, but it is owed until paid`() {
        val t = at(2026, 3, 1)
        // 50,000 brought in on the 1st and cleared over two months: the import day says nothing of his pace.
        val b = PaymentRadar.book(listOf(
            gave(50_000.0, t, opening = true),
            got(20_000.0, t + 30 * day), got(30_000.0, t + 60 * day),
            gave(1_000.0, t + 61 * day), got(1_000.0, t + 66 * day)
        ))
        assertEquals(listOf(5), b.repaidDays)
        assertEquals(0.0, b.balance, 0.001)
        // Still owed: it is the oldest amount unpaid, from the day it was brought in.
        val open = PaymentRadar.book(listOf(gave(50_000.0, t, opening = true), got(10_000.0, t + 30 * day)))
        assertEquals(t, open.openSince)
        assertEquals(40_000.0, open.balance, 0.001)
        assertTrue(open.repaidDays.isEmpty())
    }

    @Test fun `the shop's normal is not made by opening balances`() {
        val t = at(2026, 1, 1)
        // Ten customers each brought in with a balance and paying it after 120 days: no normal comes of it.
        val imported = (1L..10L).flatMap { p -> listOf(gave(5_000.0, t, p, opening = true), got(5_000.0, t + 120 * day, p)) }
        assertNull(PaymentRadar.shopTypicalDays(imported))
        // The same lots as real credit are a normal of 120 days.
        val credit = (1L..10L).flatMap { p -> listOf(gave(5_000.0, t, p), got(5_000.0, t + 120 * day, p)) }
        assertEquals(120, PaymentRadar.shopTypicalDays(credit))
    }

    @Test fun `an advance brought in is no payment day of his rhythm`() {
        val t = at(2026, 1, 1)
        val now = at(2026, 6, 1)
        // Two real payments and an advance brought in: fewer than three payment days, no rhythm.
        val lines = listOf(
            got(2_000.0, t, opening = true),
            gave(5_000.0, t + 10 * day), got(1_000.0, t + 40 * day), got(1_000.0, t + 70 * day)
        )
        assertNull(PaymentHabit.of(lines, now, utc))
        // Counted as a payment, the same lines would have made one.
        assertTrue(PaymentHabit.of(lines.map { it.copy(isOpening = false) }, now, utc) != null)
    }

    @Test fun `an advance brought in keeps no promise`() {
        val t = at(2026, 3, 1)
        val now = at(2026, 4, 1)
        // A promise for the 6th, and nothing paid but an advance brought in on the 3rd: the promise was not kept.
        val r = PaymentRadar.read(
            listOf(gave(5_000.0, t - 10 * day), got(1_000.0, t + 2 * day, opening = true)), now, shopDays = 30,
            promises = listOf(PaymentRadar.Promise(madeAt = t, day = t + 5 * day)), tz = utc
        )
        assertEquals(0, r.promisesKept)
        assertEquals(1, r.promisesBroken)
    }

    @Test fun `a customer read only from an opening balance has no pace and is still judged by what waits`() {
        val t = at(2026, 1, 1)
        val now = at(2026, 6, 1)
        val r = PaymentRadar.read(listOf(gave(50_000.0, t, opening = true)), now, shopDays = 30, tz = utc)
        assertNull(r.typicalDays)
        assertEquals(151, r.oldestOpenDays)
        assertEquals(PaymentRadar.Light.RED, r.light)
    }
}
