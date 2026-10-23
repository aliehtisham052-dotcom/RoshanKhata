package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.Locale

/** The poster's day names and AM/PM come from the app's own tables (23 Oct review, R53). */
class DateWordsPosterTest {
    @Test
    fun `weekday and am-pm from the app's own tables`() {
        assertEquals("جمعو", DateWords.weekday(Locale("sd"), Calendar.FRIDAY))
        assertEquals("اتوار", DateWords.weekday(Locale("ur"), Calendar.SUNDAY))
        assertEquals("Jumma", DateWords.weekday(Locale.forLanguageTag("ur-Latn"), Calendar.FRIDAY))
        assertEquals("Sabtu", DateWords.weekday(Locale("in"), Calendar.SATURDAY))
        assertNull(DateWords.weekday(Locale.ENGLISH, Calendar.MONDAY))
        assertEquals("منجهند", DateWords.amPm(Locale("sd"))!![1])
        assertNull(DateWords.amPm(Locale.ENGLISH))
    }
}
