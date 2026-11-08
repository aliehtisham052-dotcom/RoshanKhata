package com.innovation313.roshankhata.screens

import com.innovation313.roshankhata.data.auditAmount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuditNamesTest {
    @Test
    fun `an audit amount reads back as the number it was written from`() {
        assertEquals(4555225.0, AuditNames.amount(auditAmount(4555225.0))!!, 0.0)
        assertEquals(800.0, AuditNames.amount("800.00")!!, 0.0)
        assertEquals(-12.5, AuditNames.amount("-12.50")!!, 0.0)
    }

    @Test
    fun `words that are not an amount are none`() {
        assertNull(AuditNames.amount("Send khata link"))
    }
}
