package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every shop's registers; only agri and medical sheets carry the inspector's details (31 Oct 2026). */
class RegisterStyleTest {

    @Test
    fun `the trade key decides the register's words`() {
        assertEquals(RegisterStyle.AGRI, RegisterStyle.of("AGRI"))
        assertEquals(RegisterStyle.MEDICAL, RegisterStyle.of(" medical "))
        assertEquals(RegisterStyle.PLAIN, RegisterStyle.of("KIRYANA"))
        assertEquals(RegisterStyle.PLAIN, RegisterStyle.of("WHOLESALE"))
        assertEquals(RegisterStyle.PLAIN, RegisterStyle.of(null))
        assertTrue(RegisterStyle.AGRI.compliance)
        assertTrue(RegisterStyle.MEDICAL.compliance)
        assertFalse(RegisterStyle.PLAIN.compliance)
    }
}
