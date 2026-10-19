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
}
