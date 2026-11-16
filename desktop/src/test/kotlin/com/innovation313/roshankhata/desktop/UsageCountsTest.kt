package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.DayMath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The computer's own counts of app openings and calculator sums (17 Nov 2026, [UsageCounts]): the phone's UsageLog rule —
 * a figure per day, 40 days kept, counted from the first day counted — plus the whole count kept apart, so "since
 * counting" stays right after old days are swept and a window from 1970 is not walked day by day.
 */
class UsageCountsTest {

    private lateinit var dir: File
    private val day = 24L * 60 * 60 * 1000

    @Before
    fun open() { dir = Files.createTempDirectory("rk-usage").toFile() }

    @After
    fun close() { dir.deleteRecursively() }

    @Test
    fun `nothing counted is nothing said`() {
        val c = UsageCounts(File(dir, "usage.properties"))
        assertNull(c.since())
        assertEquals(0, c.count(UsageCounts.What.APP_OPENS, 0L, Long.MAX_VALUE))
        assertFalse(File(dir, "usage.properties").exists())
    }

    @Test
    fun `counts are per day and per kind, and kept on this computer's file`() {
        val file = File(dir, "usage.properties")
        val today = DayMath.startOfDay(System.currentTimeMillis()) + 10 * 60 * 60 * 1000L
        val c = UsageCounts(file)
        c.add(UsageCounts.What.CALCULATIONS, today - 2 * day)
        c.add(UsageCounts.What.CALCULATIONS, today)
        c.add(UsageCounts.What.CALCULATIONS, today)
        c.add(UsageCounts.What.APP_OPENS, today)
        val todayFrom = DayMath.startOfDay(today)
        assertEquals(2, c.count(UsageCounts.What.CALCULATIONS, todayFrom, todayFrom + day, now = today))
        assertEquals(1, c.count(UsageCounts.What.APP_OPENS, todayFrom, todayFrom + day, now = today))
        assertEquals(1, c.count(UsageCounts.What.CALCULATIONS, todayFrom - 2 * day, todayFrom - day, now = today))
        assertEquals(DayMath.startOfDay(today - 2 * day), c.since())
        // The whole count, asked from the beginning of time: every sum since counting began.
        assertEquals(3, c.count(UsageCounts.What.CALCULATIONS, 0L, Long.MAX_VALUE, now = today))
        // Read back by the next start of the app.
        val again = UsageCounts(file)
        assertEquals(2, again.count(UsageCounts.What.CALCULATIONS, todayFrom, todayFrom + day, now = today))
        assertEquals(c.since(), again.since())
        // Nothing of the book: only the counts' own keys are in the file.
        val keys = file.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.substringBefore('=') }
        assertTrue(keys.toString(), keys.all { k -> k == "since" || k == "swept" || Regex("(all_)?(open|calc)_\\d*").matches(k) })
    }

    @Test
    fun `days older than forty go, the whole count since counting stays`() {
        val c = UsageCounts(File(dir, "usage.properties"))
        val first = DayMath.startOfDay(System.currentTimeMillis()) - 60 * day + 10 * 60 * 60 * 1000L
        c.add(UsageCounts.What.CALCULATIONS, first)
        val now = first + 60 * day
        c.add(UsageCounts.What.CALCULATIONS, now)
        // The sixty-day-old day is swept from the days...
        val oldFrom = DayMath.startOfDay(first)
        assertEquals(0, c.count(UsageCounts.What.CALCULATIONS, oldFrom, oldFrom + day, now = now))
        // ...and still in the whole count.
        assertEquals(2, c.count(UsageCounts.What.CALCULATIONS, 0L, Long.MAX_VALUE, now = now))
    }
}
