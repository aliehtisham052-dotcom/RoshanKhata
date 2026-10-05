package com.innovation313.roshankhata

import com.innovation313.roshankhata.data.DateWords
import com.innovation313.roshankhata.data.ShopCalendar
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Locale

/**
 * Hijri on a real Android (android.icu's Umm al-Qura tables), against the
 * hijri-converter library's Umm al-Qura dates computed on 5 Oct 2026.
 */
class ShopCalendarOnDeviceTest {

    @After
    fun back() = ShopCalendar.useForTest(ShopCalendar.Kind.GREGORIAN)

    private fun noon(y: Int, m: Int, d: Int): Long =
        Calendar.getInstance().apply { set(y, m - 1, d, 12, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

    @Test
    fun hijri_matches_umm_al_qura_reference_days() {
        val refs = listOf(
            intArrayOf(2026, 10, 5, 1448, 4, 24), intArrayOf(2026, 2, 18, 1447, 9, 1),
            intArrayOf(2026, 3, 20, 1447, 10, 1), intArrayOf(2026, 5, 27, 1447, 12, 10),
            intArrayOf(2025, 3, 1, 1446, 9, 1), intArrayOf(2024, 7, 7, 1446, 1, 1),
            intArrayOf(2027, 1, 1, 1448, 7, 23)
        )
        for (r in refs) {
            assertEquals("${r[0]}-${r[1]}-${r[2]}", Triple(r[3], r[4], r[5]),
                ShopCalendar.ymd(ShopCalendar.Kind.HIJRI, noon(r[0], r[1], r[2])))
        }
    }

    @Test
    fun hijri_prints_in_urdu_with_the_time_as_before() {
        ShopCalendar.useForTest(ShopCalendar.Kind.HIJRI)
        val c = Calendar.getInstance().apply { set(2026, 1, 18, 12, 0, 0) }
        assertEquals("1 رمضان 1447", DateWords.formatter("d MMM yyyy", Locale("ur")).format(c.time))
    }
}
