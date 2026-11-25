package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Calendar
import java.util.TimeZone

/**
 * BC-281 (25 Nov 2026, stock, importance 9): "expiring within 60 days" had two ends — the bell and the daily sweep counted to
 * midnight + 60 x 24 h, the Expiry page, the Bills banner, the computer's badges and the automation to now + 60 x 24 h — so a
 * batch dated mid-day on day 60 was on the pages and not in the bell, and the bell's count changed with the hour. One end,
 * [ExpiryWindow.cutoff]: the last moment of the calendar day 60 days on. Invented dates; plain Kotlin.
 */
class ExpiryWindowCutoffTest {

    private fun at(tz: TimeZone, y: Int, m: Int, d: Int, h: Int, min: Int = 0): Long =
        Calendar.getInstance(tz).apply { clear(); set(y, m, d, h, min, 0) }.timeInMillis

    private val karachi = TimeZone.getTimeZone("Asia/Karachi")

    @Test fun `the end does not move with the hour of the day`() {
        val morning = ExpiryWindow.cutoff(at(karachi, 2026, Calendar.JANUARY, 10, 0, 1), karachi)
        val night = ExpiryWindow.cutoff(at(karachi, 2026, Calendar.JANUARY, 10, 23, 59), karachi)
        assertEquals(morning, night)
    }

    @Test fun `a batch dated any time on day 60 is inside, the first moment of day 61 is not`() {
        val now = at(karachi, 2026, Calendar.JANUARY, 10, 15)
        val cut = ExpiryWindow.cutoff(now, karachi)
        val day60 = at(karachi, 2026, Calendar.MARCH, 11, 0)           // 10 Jan + 60 days
        assertTrue("midnight starting day 60", day60 <= cut)
        assertTrue("noon on day 60", at(karachi, 2026, Calendar.MARCH, 11, 12) <= cut)
        assertTrue("last minute of day 60", at(karachi, 2026, Calendar.MARCH, 11, 23, 59) <= cut)
        assertTrue("day 61 starts after the window", at(karachi, 2026, Calendar.MARCH, 12, 0) > cut)
        // what is already past is inside too (an expired batch that still holds stock is on the page)
        assertTrue(at(karachi, 2025, Calendar.DECEMBER, 1, 0) <= cut)
    }

    @Test fun `counted by the calendar when the clocks move`() {
        val ny = TimeZone.getTimeZone("America/New_York")
        // 1 March 2026 + 61 days crosses the 8 March spring-forward: day 61 starts on 1 May 00:00, not 1 May 01:00
        val cut = ExpiryWindow.cutoff(at(ny, 2026, Calendar.MARCH, 1, 12), ny)
        assertEquals(at(ny, 2026, Calendar.APRIL, 30, 23, 59) + 59_999, cut)
        assertTrue(at(ny, 2026, Calendar.MAY, 1, 0) > cut)
        assertTrue(at(ny, 2026, Calendar.APRIL, 30, 23, 59) <= cut)
    }

    @Test fun `the length of the window is still sixty days`() {
        assertEquals(60, ExpiryWindow.WARN_DAYS)
    }

    /** The guard: no screen, sweep or notice makes the window's end by hand again. */
    @Test fun `nothing outside ExpiryWindow builds the end from WARN_MS`() {
        val root = generateSequence(File(".").absoluteFile) { it.parentFile }.first { File(it, "shared/src/jvmSharedMain").isDirectory }
        val hits = listOf("shared/src/jvmSharedMain", "app/src/main", "desktop/src/main").map { File(root, it) }.filter { it.isDirectory }
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "ExpiryWindow.kt" }.toList() }
            .filter { "WARN_MS" in it.readText() }.map { it.name }
        assertEquals("these still add WARN_MS to a clock reading: $hits", emptyList<String>(), hits)
    }
}
