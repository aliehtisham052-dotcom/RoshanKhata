package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.SeasonBook.Crop
import com.innovation313.roshankhata.data.SeasonBook.Line
import com.innovation313.roshankhata.data.SeasonBook.Season
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * The season book's edge E1 (25 Nov 2026, the formula book): a credit dated before the Seasons window but tagged to a
 * season in it is that season's credit, and a Qarz-e-Hasna loan or an imported OPENING balance is no season's credit.
 * The screen reads from [SeasonBook.readFrom] (the window's first day, or the first tagged credit of a shown season) with
 * the balance carried in; that must give the whole book's figures for every season it shows.
 */
class SeasonEdgeTest {
    private val tz = TimeZone.getTimeZone("Asia/Karachi")

    private fun at(y: Int, m: Int, d: Int): Long = Calendar.getInstance(tz).apply {
        clear(); set(y, m - 1, d, 12, 0)
    }.timeInMillis

    /** What the two queries give the screen: the lines from [SeasonBook.readFrom] on, the balance before it carried in. */
    private fun windowed(lines: List<Line>, first: Season): List<SeasonBook.PartySeason> {
        val tags = lines.filter { it.isGiven && it.seasonKey != null && it.timestamp < SeasonBook.seasonStart(first, tz) }
            .groupBy { it.seasonKey }.map { (k, l) -> SeasonBook.TagStart(k, l.minOf { it.timestamp }) }
        val since = SeasonBook.readFrom(first, tags, tz)
        val carried = lines.filter { it.timestamp < since }.groupBy { it.partyId }
            .mapValues { (_, l) -> l.sumOf { if (it.isGiven) it.amount else -it.amount } }
        return SeasonBook.book(lines.filter { it.timestamp >= since }, tz, carried).filter { it.season >= first }
    }

    @Test fun `a credit tagged to this season but written before the window is this season's`() {
        val k26 = Season(Crop.KHARIF, 2026)
        // Written in March (Rabi), tagged Kharif 2026 by the owner.
        val lines = listOf(Line(1, at(2026, 3, 20), 5000.0, true, k26.key, id = 1))
        val first = SeasonBook.window(at(2026, 11, 5), tz)
        val rows = windowed(lines, first)
        assertEquals(listOf(5000.0), rows.filter { it.season == k26 }.map { it.given })
    }

    @Test fun `a loan and an opening balance are owed but are no season's credit`() {
        val lines = listOf(
            Line(1, at(2026, 5, 1), 3000.0, true, id = 1, seasonal = false),   // Qarz-e-Hasna
            Line(1, at(2026, 5, 2), 2000.0, true, id = 2),                     // a Kharif sale
            Line(1, at(2026, 6, 1), 1000.0, false, id = 3)                     // a payment: the oldest first, the loan
        )
        val rows = SeasonBook.book(lines, tz)
        assertEquals(1, rows.size)
        assertEquals(2000.0, rows.single().given, 0.0)
        assertEquals("the payment went to the older loan", 0.0, rows.single().cleared, 0.0)
    }

    @Test fun `the window's reading equals the whole book with tags and loans`() {
        val rnd = java.util.Random(313)
        repeat(400) { round ->
            val lines = (1..rnd.nextInt(40) + 1).map { i ->
                val y = 2023 + rnd.nextInt(4)
                val given = rnd.nextInt(5) < 3
                val tagged = given && rnd.nextInt(4) == 0
                Line(
                    partyId = (1 + rnd.nextInt(4)).toLong(),
                    timestamp = at(y, 1 + rnd.nextInt(12), 1 + rnd.nextInt(28)),
                    amount = (1 + rnd.nextInt(200)) * 50.0,
                    isGiven = given,
                    seasonKey = if (tagged) Season(if (rnd.nextBoolean()) Crop.RABI else Crop.KHARIF, 2023 + rnd.nextInt(4)).key else null,
                    id = i.toLong(),
                    seasonal = !(given && rnd.nextInt(8) == 0)
                )
            }
            val now = at(2026, 1 + rnd.nextInt(12), 15)
            val first = SeasonBook.window(now, tz)
            val whole = SeasonBook.book(lines, tz).filter { it.season >= first }
            val windowed = windowed(lines, first)
            fun key(r: SeasonBook.PartySeason) = r.partyId to r.season
            val w = whole.associateBy(::key); val v = windowed.associateBy(::key)
            assertEquals("round $round: seasons", w.keys, v.keys)
            for (k in w.keys) {
                assertEquals("round $round $k given", w.getValue(k).given, v.getValue(k).given, 0.001)
                assertEquals("round $round $k cleared", w.getValue(k).cleared, v.getValue(k).cleared, 0.001)
                assertTrue("round $round $k open alike", (w.getValue(k).clearedAt == null) == (v.getValue(k).clearedAt == null))
            }
        }
    }
}
