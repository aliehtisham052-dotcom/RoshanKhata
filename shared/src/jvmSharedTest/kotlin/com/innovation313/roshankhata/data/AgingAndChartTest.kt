package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/**
 * Dues by age and the report's chart (21 Oct 2026), held without a database:
 * a payment clears the oldest debt first, so what is still owed is the newest
 * of what was given; a supplier I owe is aged on their "I got" entries; the
 * chart has every hour, day or month of its window, an empty one as zero.
 */
class AgingAndChartTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_000L * day

    private fun party(id: Long, balance: Double, customer: Boolean = true) =
        PartyWithBalance(id, "P$id", null, customer, null, balance, now)

    @Test
    fun aPaymentClearsTheOldestSoWhatIsLeftIsTheNewest() {
        // Gave 10,000 a hundred days ago and 4,000 ten days ago; got 9,000 back. Owes 5,000:
        // 4,000 of it from ten days ago, 1,000 from a hundred days ago.
        val moves = listOf(
            AgingMove(1, 10_000.0, true, now - 100 * day),
            AgingMove(1, 4_000.0, true, now - 10 * day),
            AgingMove(1, 9_000.0, false, now - 5 * day)
        )
        val r = Aging.compute(listOf(party(1, 5_000.0)), moves, now)
        val row = r.receivable.single()
        assertEquals(listOf(4_000.0, 0.0, 0.0, 1_000.0), row.buckets)
        assertEquals(5_000.0, row.balance, 0.0)
        assertEquals(now - 100 * day, row.oldest)
        assertTrue(r.payable.isEmpty())
        assertEquals(listOf(4_000.0, 0.0, 0.0, 1_000.0), r.receivableTotals)
    }

    @Test
    fun aSupplierIOweIsAgedOnTheirIGotEntriesAndTheOldestMoneyComesFirst() {
        val moves = listOf(
            AgingMove(2, 30_000.0, false, now - 45 * day),   // goods on credit from the supplier
            AgingMove(2, 10_000.0, true, now - 20 * day),    // paid them
            AgingMove(3, 2_000.0, true, now - 95 * day),     // a customer 95 days late
            AgingMove(4, 9_000.0, true, now - 3 * day)       // a customer who bought this week
        )
        val r = Aging.compute(listOf(party(2, -20_000.0, customer = false), party(3, 2_000.0), party(4, 9_000.0)), moves, now)
        assertEquals(listOf(0.0, 20_000.0, 0.0, 0.0), r.payable.single().buckets)
        // The 95-day customer leads although the other owes more: old money first.
        assertEquals(listOf(3L, 4L), r.receivable.map { it.partyId })
        assertEquals(0, Aging.bucketOf(now - 30 * day, now))
        assertEquals(1, Aging.bucketOf(now - 31 * day, now))
        assertEquals(3, Aging.bucketOf(now - 91 * day, now))
        // A settled party is in neither list.
        assertTrue(Aging.compute(listOf(party(5, 0.0)), emptyList(), now).let { it.receivable.isEmpty() && it.payable.isEmpty() })
    }

    @Test
    fun piecesNeverLoseMoney() {
        val p = Aging.pieces(listOf(10L to 100.0), 150.0)
        assertEquals(150.0, p.sumOf { it.second }, 0.0)
        assertTrue(Aging.pieces(emptyList(), 0.0).isEmpty())
    }

    private fun line(ts: Long, amount: Double) = SoldLine(1, ts, null, null, null, null, "X", null, 1.0, null, amount, 0.0, null)

    @Test
    fun theChartHasEverySliceOfItsWindow() {
        val tz = TimeZone.getTimeZone("Asia/Karachi")
        val today = DayCloseMath.startOf(DayCloseMath.dayOf(now, tz), tz)
        // One day: 24 hours, the sales in their hours.
        val oneDay = SalesReport(today, today + day, listOf(line(today + 9 * 3_600_000L + 5, 500.0), line(today + 9 * 3_600_000L + 9, 250.0), line(today + 17 * 3_600_000L, 100.0)), 0.0, 0.0, emptyList())
        val hours = oneDay.bars(tz)
        assertEquals(24, hours.size)
        assertEquals(750.0, hours[9].revenue, 0.0)
        assertEquals(100.0, hours[17].revenue, 0.0)
        assertEquals(850.0, hours.sumOf { it.revenue }, 0.0)
        // Five days: five bars, the empty middle days as zero.
        val week = SalesReport(today - 4 * day, today + day, listOf(line(today - 4 * day + 1, 10.0), line(today + 1, 30.0)), 0.0, 0.0, emptyList())
        val days = week.bars(tz)
        assertEquals(listOf(10.0, 0.0, 0.0, 0.0, 30.0), days.map { it.revenue })
        // Ninety days: a bar a month, three or four of them, nothing lost.
        val quarter = SalesReport(today - 89 * day, today + day, listOf(line(today - 89 * day + 1, 7.0), line(today + 1, 3.0)), 0.0, 0.0, emptyList())
        val months = quarter.bars(tz)
        assertTrue(months.size in 3..4)
        assertEquals(10.0, months.sumOf { it.revenue }, 0.0)
        assertNull(months.firstOrNull { it.revenue < 0 })
    }
}
