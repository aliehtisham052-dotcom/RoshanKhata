package com.innovation313.roshankhata.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Solar Hijri (Jalali) against the jdatetime library (computed 5 Oct 2026),
 * Nowruz on both sides of the year included, and the date pattern printing.
 * Hijri needs Android's own tables and is checked on a device
 * (ShopCalendarOnDeviceTest).
 */
class ShopCalendarTest {

    @After
    fun back() = ShopCalendar.useForTest(ShopCalendar.Kind.GREGORIAN)

    private val jdatetime = listOf(
        intArrayOf(2026, 10, 5, 1405, 7, 13), intArrayOf(2026, 3, 20, 1404, 12, 29),
        intArrayOf(2026, 3, 21, 1405, 1, 1), intArrayOf(2025, 3, 20, 1403, 12, 30),
        intArrayOf(2025, 3, 21, 1404, 1, 1), intArrayOf(2024, 3, 20, 1403, 1, 1),
        intArrayOf(2024, 2, 29, 1402, 12, 10), intArrayOf(2023, 12, 31, 1402, 10, 10),
        intArrayOf(2000, 1, 1, 1378, 10, 11), intArrayOf(1990, 7, 15, 1369, 4, 24),
        intArrayOf(2030, 3, 21, 1409, 1, 1), intArrayOf(2029, 3, 19, 1407, 12, 29),
        intArrayOf(2029, 3, 20, 1408, 1, 1), intArrayOf(2026, 9, 22, 1405, 6, 31),
        intArrayOf(2026, 9, 23, 1405, 7, 1), intArrayOf(2027, 1, 1, 1405, 10, 11)
    )

    @Test
    fun `jalali matches jdatetime on every reference day`() {
        for (r in jdatetime) {
            assertEquals("${r[0]}-${r[1]}-${r[2]}", Triple(r[3], r[4], r[5]), ShopCalendar.jalali(r[0], r[1], r[2]))
        }
    }

    @Test
    fun `every day of 2020 to 2030 moves forward by exactly one jalali day`() {
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { set(2020, 0, 1, 12, 0, 0) }
        var prev = ShopCalendar.jalali(2020, 1, 1)
        repeat(365 * 11) {
            c.add(Calendar.DAY_OF_MONTH, 1)
            val now = ShopCalendar.jalali(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
            val ok = (now.first == prev.first && now.second == prev.second && now.third == prev.third + 1) ||
                (now.first == prev.first && now.second == prev.second + 1 && now.third == 1) ||
                (now.first == prev.first + 1 && now.second == 1 && now.third == 1 && prev.second == 12)
            assertTrue("$prev -> $now", ok)
            prev = now
        }
    }

    @Test
    fun `a jalali date prints with jalali month names and time untouched`() {
        ShopCalendar.useForTest(ShopCalendar.Kind.JALALI)
        val zone = TimeZone.getDefault()
        val c = Calendar.getInstance(zone).apply { set(2026, 9, 5, 14, 30, 0) }
        assertEquals("13 Mehr 1405", DateWords.formatter("d MMM yyyy", Locale.ENGLISH).format(c.time))
        assertEquals("13 Mehr 1405, 14:30", DateWords.formatter("dd MMM yyyy, HH:mm", Locale.ENGLISH).format(c.time))
        assertEquals("13 مهر 1405", DateWords.formatter("d MMM yyyy", Locale("fa")).format(c.time))
    }

    @Test
    fun `month and time labels without a day stay gregorian`() {
        ShopCalendar.useForTest(ShopCalendar.Kind.JALALI)
        assertFalse(DateWords.hasDay("MMM yyyy"))
        assertFalse(DateWords.hasDay("h:mm a"))
        assertFalse(DateWords.hasDay("'day' MMM"))
        assertTrue(DateWords.hasDay("dd MMM, HH:mm"))
        val c = Calendar.getInstance().apply { set(2026, 9, 5, 9, 0, 0) }
        assertEquals("Oct 2026", DateWords.formatter("MMM yyyy", Locale.ENGLISH).format(c.time))
    }

    @Test
    fun `gregorian is unchanged and unknown keys fall back to it`() {
        assertEquals(ShopCalendar.Kind.GREGORIAN, ShopCalendar.Kind.of(null))
        assertEquals(ShopCalendar.Kind.GREGORIAN, ShopCalendar.Kind.of("lunar-x"))
        val c = Calendar.getInstance().apply { set(2026, 9, 5, 9, 0, 0) }
        assertEquals("5 Oct 2026", DateWords.formatter("d MMM yyyy", Locale.ENGLISH).format(c.time))
    }
}
