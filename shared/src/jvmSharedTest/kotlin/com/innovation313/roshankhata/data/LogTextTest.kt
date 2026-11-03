package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 3 Nov 2026 audit, L25: a crash record keeps the kind of failure and where, never what was typed. */
class LogTextTest {

    @Test
    fun `a logged error keeps its kind and place but not what was typed`() {
        val trace = LogText.trace(IllegalStateException("save failed", NumberFormatException("For input string: \"0300 1234567\"")))
        assertTrue(trace.startsWith("java.lang.IllegalStateException: save failed"))
        assertTrue(trace.contains("Caused by: java.lang.NumberFormatException"))
        assertTrue(trace.contains("\tat "))
        assertFalse(trace.contains("1234567"))
    }

    @Test
    fun `short figures stay, long ones and codes go`() {
        assertEquals("HTTP 503 from port 47313", LogText.clean("HTTP 503 from port 47313"))
        assertEquals("phone #", LogText.clean("phone 03001234567"))
        assertEquals("key …", LogText.clean("key a1B2c3D4e5F6g7H8i9"))
    }
}
