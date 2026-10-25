package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.ShopCalendar
import com.innovation313.roshankhata.words.W
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Business Profile on Windows offered Gregorian or Hijri only, so a Solar
 * Hijri shop (Iran, Afghanistan) turned Gregorian on its next Save (25 Oct
 * 2026, the audit). Every calendar the book can hold has its own choice.
 */
class CalendarChoiceTest {

    @Test
    fun `every calendar the phone offers is a choice here`() {
        assertEquals(
            listOf(W.calendar_gregorian, W.calendar_hijri, W.calendar_jalali),
            ShopCalendar.Kind.values().map { calendarWord(it) }
        )
    }
}
