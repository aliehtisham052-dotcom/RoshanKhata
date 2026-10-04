package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Dates read in the app's language, with 0-9 for every figure ([DateWords]).
 */
class DateWordsTest {

    /** 4 October 2026, 17:05, in the zone the formatter itself will use. */
    private val moment: Long = Calendar.getInstance(TimeZone.getDefault(), Locale.ENGLISH).run {
        clear()
        set(2026, Calendar.OCTOBER, 4, 17, 5)
        timeInMillis
    }

    private fun day(tag: String) = DateWords.format("d MMM yyyy", moment, Locale.forLanguageTag(tag))

    @Test
    fun `english is exactly what the app printed before`() {
        assertEquals("4 Oct 2026, 5:05 PM", DateWords.format("d MMM yyyy, h:mm a", moment, Locale.ENGLISH))
        assertEquals("04 Oct, 17:05", DateWords.format("dd MMM, HH:mm", moment, Locale.ENGLISH))
    }

    @Test
    fun `each language names the month in its own words, and the figures stay 0-9`() {
        assertEquals("4 اکتوبر 2026", day("ur"))
        assertEquals("4 آڪٽوبر 2026", day("sd"))
        assertEquals("4 اکتبر 2026", day("fa"))
        assertEquals("4 أكتوبر 2026", day("ar"))
        assertEquals("4 अक्टू॰ 2026", day("hi"))
        assertEquals("4 অক্টো 2026", day("bn"))
        assertEquals("4 Okt 2026", day("id"))
    }

    @Test
    fun `roman urdu keeps the english month, not an arabic-script one`() {
        assertEquals("4 Oct 2026", day("ur-Latn"))
    }

    @Test
    fun `indonesian answers to android's old code too`() {
        assertEquals("id", DateWords.keyOf(Locale("in")))
        assertEquals("4 Okt 2026", DateWords.format("d MMM yyyy", moment, Locale("in")))
    }

    @Test
    fun `arabic and persian write their own before and after noon`() {
        assertEquals("5:05 م", DateWords.format("h:mm a", moment, Locale.forLanguageTag("ar")))
        assertEquals("5:05 ب.ظ.", DateWords.format("h:mm a", moment, Locale.forLanguageTag("fa")))
        assertEquals("5:05 PM", DateWords.format("h:mm a", moment, Locale.forLanguageTag("hi")))
    }

    @Test
    fun `a month alone and a month with its year`() {
        assertEquals("अक्टू॰", DateWords.format("MMM", moment, Locale.forLanguageTag("hi")))
        assertEquals("অক্টো 2026", DateWords.format("MMM yyyy", moment, Locale.forLanguageTag("bn")))
    }

    @Test
    fun `every language has twelve months and none is empty`() {
        for (tag in listOf("en", "ur", "ur-Latn", "sd", "fa", "ar", "hi", "bn", "id")) {
            val months = DateWords.months(Locale.forLanguageTag(tag))
            assertEquals(tag, 12, months.size)
            assertTrue(tag, months.all { it.isNotBlank() })
        }
    }
}
