package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Every shape a Pakistani number arrives in becomes wa.me's bare digits. */
class WhatsAppNumberTest {
    @Test
    fun shapes() {
        assertEquals("923001234567", WhatsAppNumber.of("0300 1234567"))
        assertEquals("923001234567", WhatsAppNumber.of("+92 300-1234567"))
        assertEquals("923001234567", WhatsAppNumber.of("0092 300 1234567"))
        assertEquals("923001234567", WhatsAppNumber.of("3001234567"))
        assertNull(WhatsAppNumber.of("--"))
    }

    @Test
    fun `a local number takes the shop's own country - 21 Oct`() {
        val before = ShopCountry.current
        try {
            ShopCountry.current = ShopCountry.BANGLADESH
            assertEquals("8801712345678", WhatsAppNumber.of("01712-345678"))
            ShopCountry.current = ShopCountry.INDIA
            assertEquals("919876543210", WhatsAppNumber.of("98765 43210"))
            ShopCountry.current = ShopCountry.SAUDI_ARABIA
            assertEquals("966501234567", WhatsAppNumber.of("0501234567"))
            assertEquals("966501234567", WhatsAppNumber.of("٠٥٠١٢٣٤٥٦٧"))
            ShopCountry.current = ShopCountry.INDONESIA
            assertEquals("628123456789", WhatsAppNumber.of("0812-3456-789"))
            // An international number is never re-prefixed, whatever the shop's country.
            assertEquals("923001234567", WhatsAppNumber.of("+92 300 1234567"))
            // A shop in a country the app does not know gets no link for a local number, not a wrong one.
            ShopCountry.current = ShopCountry.OTHER
            assertNull(WhatsAppNumber.of("0300 1234567"))
            assertEquals("4420712345678", WhatsAppNumber.of("+44 20 7123 4567 8"))
        } finally {
            ShopCountry.current = before
        }
    }
}
