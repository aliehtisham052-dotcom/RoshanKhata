package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/**
 * WhenWords against the two rules it replaced (29 Oct 2026, the cleanup): the computer's
 * `whenWords` (java.time LocalDate: Today / Yesterday / weekday within a week / d MMM, then the
 * time) and the phone's report heading (Calendar midnights: Today · date / Yesterday · date / date).
 */
class WhenWordsTest {

    private val tz = TimeZone.getTimeZone("Asia/Karachi")

    /** Words that say which string was asked for. */
    private val words = object : Words {
        override fun text(name: String, vararg args: Any) = "<$name>"
        override fun plural(name: String, count: Int, vararg args: Any) = name
        override fun list(name: String) = emptyList<String>()
    }

    /** Mon 12 Oct 2026 14:30 PKT. */
    private val now = Calendar.getInstance(tz).apply { clear(); set(2026, Calendar.OCTOBER, 12, 14, 30, 0) }.timeInMillis
    private val day = 24L * 60 * 60 * 1000

    /** The computer's rule as Fluent.kt wrote it, java.time and all. */
    private fun oldWindows(at: Long, now: Long): String {
        val zone = tz.toZoneId()
        val d = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val time = DateWords.formatter("h:mm a").format(Date(at))
        val dayWord = when {
            d == today -> words.text(W.range_today)
            d == today.minusDays(1) -> words.text(W.range_yesterday)
            d.isAfter(today.minusDays(7)) -> DateWords.formatter("EEE").format(Date(at))
            else -> DateWords.formatter("d MMM").format(Date(at))
        }
        return "$dayWord $time"
    }

    /** The phone's heading as LedgerReportActivity wrote it: Calendar midnights compared as longs. */
    private fun oldPhoneHeading(at: Long, now: Long): String {
        fun midnight(ms: Long) = Calendar.getInstance(tz).apply {
            timeInMillis = ms; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val d = midnight(at)
        val today = midnight(now)
        val yesterday = Calendar.getInstance(tz).apply { timeInMillis = today; add(Calendar.DAY_OF_MONTH, -1) }.timeInMillis
        val date = DateWords.formatter("d MMM yyyy").format(Date(d))
        return when (d) {
            today -> words.text(W.range_today) + " · " + date
            yesterday -> words.text(W.range_yesterday) + " · " + date
            else -> date
        }
    }

    private fun newPhoneHeading(at: Long, now: Long): String {
        val d = DayMath.startOfDay(at, tz)
        val date = DateWords.formatter("d MMM yyyy").format(Date(d))
        return WhenWords.relativeDay(d, now, words, tz)?.let { "$it · $date" } ?: date
    }

    @Test
    fun `today, yesterday and nothing else are relative days`() {
        assertEquals("<${W.range_today}>", WhenWords.relativeDay(now, now, words, tz))
        assertEquals("<${W.range_today}>", WhenWords.relativeDay(now - 14 * 3_600_000L - 29 * 60_000L, now, words, tz))
        assertEquals("<${W.range_yesterday}>", WhenWords.relativeDay(now - day, now, words, tz))
        assertEquals("<${W.range_yesterday}>", WhenWords.relativeDay(now - 14 * 3_600_000L - 31 * 60_000L, now, words, tz))
        assertNull(WhenWords.relativeDay(now - 2 * day, now, words, tz))
        assertNull("tomorrow is not today", WhenWords.relativeDay(now + 10 * 3_600_000L, now, words, tz))
    }

    @Test
    fun `the day word is the weekday inside a week and the date beyond it`() {
        assertEquals(DateWords.formatter("EEE").format(Date(now - 2 * day)), WhenWords.dayWord(now - 2 * day, now, words, tz))
        assertEquals(DateWords.formatter("EEE").format(Date(now - 6 * day)), WhenWords.dayWord(now - 6 * day, now, words, tz))
        assertEquals(DateWords.formatter("d MMM").format(Date(now - 7 * day)), WhenWords.dayWord(now - 7 * day, now, words, tz))
        assertEquals(DateWords.formatter("d MMM").format(Date(now - 40 * day)), WhenWords.dayWord(now - 40 * day, now, words, tz))
    }

    @Test
    fun `the computer's whenWords is unchanged, hour by hour over six weeks`() {
        var at = now - 42 * day
        while (at <= now + day) {
            assertEquals(oldWindows(at, now), WhenWords.whenWords(at, now, words, tz))
            at += 3_600_000L + 7_000
        }
    }

    @Test
    fun `the phone's report heading is unchanged, hour by hour over six weeks`() {
        var at = now - 42 * day
        while (at <= now + day) {
            assertEquals(oldPhoneHeading(at, now), newPhoneHeading(at, now))
            at += 3_600_000L + 7_000
        }
    }
}
