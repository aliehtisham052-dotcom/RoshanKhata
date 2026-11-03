package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/**
 * 3 Nov 2026 audit, M41: a typed quantity, rate or price reads as the language writes it. Indonesian marks the
 * fraction with a comma and groups with dots, as the pad's own boxes already read it (Calc, 22 Oct 2026).
 */
class DigitsDecimalCommaTest {

    private fun <T> inLocale(locale: Locale, block: () -> T): T {
        val before = Locale.getDefault()
        Locale.setDefault(locale)
        return try { block() } finally { Locale.setDefault(before) }
    }

    @Test
    fun `an Indonesian box reads its comma and its dots`() = inLocale(Locale("in", "ID")) {
        assertEquals(2.5, Digits.parse("2,5")!!, 1e-9)
        assertEquals("four thousand, not four", 4000.0, Digits.parse("4.000")!!, 1e-9)
        assertEquals(1250.5, Digits.parse("1.250,50")!!, 1e-9)
        assertEquals("the app's own figures read as written", 1.125, Digits.parse("1.125")!!, 1e-9)
        assertEquals(1250.5, Digits.parse("1250.5")!!, 1e-9)
        assertEquals(-2.5, Digits.parse("-2,5")!!, 1e-9)
        assertNull(Digits.parse("2,5,1"))
        assertNull(Digits.parse(""))
    }

    @Test
    fun `everywhere else a box reads as it always did`() = inLocale(Locale.ENGLISH) {
        assertNull("a comma is no decimal mark here", Digits.parse("2,5"))
        assertEquals(4.0, Digits.parse("4.000")!!, 1e-9)
        assertEquals(12.5, Digits.parse("١٢٫٥")!!, 1e-9)
    }
}
