package com.innovation313.roshankhata.ui

import com.innovation313.roshankhata.data.Digits
import com.innovation313.roshankhata.data.ShopCountry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * A figure is printed with the marks it is typed with (25 Nov 2026, the covering array, BC-275): an Indonesian
 * shop with the app in English typed "1.250,50" (Digits.decimalComma) but saw "Rp 1,250.50" printed.
 */
class FormatShopMarksTest {

    private fun <T> with(locale: Locale, country: ShopCountry, body: () -> T): T {
        val l = Locale.getDefault(); val c = ShopCountry.current
        Locale.setDefault(locale); ShopCountry.current = country
        try { return body() } finally { Locale.setDefault(l); ShopCountry.current = c }
    }

    @Test fun `an Indonesian shop in English prints the marks it types`() = with(Locale.ENGLISH, ShopCountry.INDONESIA) {
        assertTrue(Digits.decimalComma())
        // The rupiah prints no decimals (Currency.decimals): 1,250.40 is "Rp 1.250"; a sign with decimals shows both marks.
        assertEquals("Rp 1.250", Format.money(1250.4, "Rp").replace(' ', ' ').takeLast(8))
        assertEquals("Rs 1.250,50", Format.money(1250.5, "Rs").replace(' ', ' ').takeLast(11))
    }

    @Test fun `a Pakistani shop in English keeps comma groups and a decimal point`() = with(Locale.ENGLISH, ShopCountry.PAKISTAN) {
        assertEquals("Rs 1,250.50", Format.money(1250.5, "Rs").replace(' ', ' ').takeLast(11))
    }

    @Test fun `the Indonesian language keeps its marks whatever the shop`() = with(Locale("in"), ShopCountry.PAKISTAN) {
        assertEquals("Rs 1.250,50", Format.money(1250.5, "Rs").replace(' ', ' ').takeLast(11))
    }
}
