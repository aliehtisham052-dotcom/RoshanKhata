package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.SeasonBook.Crop
import com.innovation313.roshankhata.data.SeasonBook.Line
import com.innovation313.roshankhata.data.SeasonBook.Season
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** Fasal ka Hisaab: seasons by date, payments settle the oldest credit first. */
class SeasonBookTest {

    private val tz = TimeZone.getTimeZone("Asia/Karachi")
    private fun at(y: Int, m: Int, d: Int): Long =
        Calendar.getInstance(tz).apply { clear(); set(y, m - 1, d, 12, 0) }.timeInMillis

    @Test
    fun `months map to the season the credit was given for`() {
        assertEquals(Season(Crop.RABI, 2026), SeasonBook.seasonOf(at(2026, 11, 5), tz))
        assertEquals(Season(Crop.RABI, 2025), SeasonBook.seasonOf(at(2026, 2, 5), tz))
        assertEquals(Season(Crop.KHARIF, 2026), SeasonBook.seasonOf(at(2026, 6, 5), tz))
        assertEquals("R2026", Season(Crop.RABI, 2026).key)
        assertEquals(Season(Crop.KHARIF, 2026), Season.fromKey("K2026"))
        assertEquals(true, Season(Crop.KHARIF, 2026) < Season(Crop.RABI, 2026))
    }

    @Test
    fun `payments settle the oldest season first`() {
        val rows = SeasonBook.book(listOf(
            Line(1, at(2025, 11, 1), 10000.0, true),   // Rabi 2025-26
            Line(1, at(2026, 5, 1), 6000.0, true),     // Kharif 2026
            Line(1, at(2026, 5, 20), 12000.0, false),  // after wheat: clears Rabi, 2000 to Kharif
        ), tz).associateBy { it.season }
        val rabi = rows.getValue(Season(Crop.RABI, 2025))
        val kharif = rows.getValue(Season(Crop.KHARIF, 2026))
        assertEquals(10000.0, rabi.cleared, 0.0)
        assertEquals(at(2026, 5, 20), rabi.clearedAt)
        assertEquals(2000.0, kharif.cleared, 0.0)
        assertEquals(4000.0, kharif.outstanding, 0.0)
        assertNull(kharif.clearedAt)
    }

    @Test
    fun `the owner's own season overrides the date`() {
        val rows = SeasonBook.book(listOf(Line(1, at(2026, 3, 25), 5000.0, true, "K2026")), tz)
        assertEquals(Season(Crop.KHARIF, 2026), rows.single().season)
    }

    @Test
    fun `an advance pays the next credit at once`() {
        val rows = SeasonBook.book(listOf(
            Line(1, at(2026, 4, 1), 3000.0, false),
            Line(1, at(2026, 4, 10), 5000.0, true)
        ), tz)
        assertEquals(3000.0, rows.single().cleared, 0.0)
        assertEquals(2000.0, rows.single().outstanding, 0.0)
    }

    @Test
    fun `summary counts customers and the median days after harvest`() {
        val rows = SeasonBook.book(listOf(
            Line(1, at(2025, 11, 1), 1000.0, true), Line(1, at(2026, 6, 10), 1000.0, false),
            Line(2, at(2025, 12, 1), 2000.0, true), Line(2, at(2026, 6, 20), 2000.0, false),
            Line(3, at(2026, 1, 1), 4000.0, true)
        ), tz)
        val s = SeasonBook.summaries(rows, tz).single()
        assertEquals(3, s.customers)
        assertEquals(7000.0, s.given, 0.0)
        assertEquals(4000.0, s.outstanding, 0.0)
        assertEquals(2, s.clearedCustomers)
        assertEquals(20, s.medianDaysAfterHarvest)
    }
}
