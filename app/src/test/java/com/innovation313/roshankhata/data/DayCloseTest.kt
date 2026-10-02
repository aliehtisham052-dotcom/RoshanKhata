package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DayCloseTest {
    @Test
    fun `expected is opening plus in minus out, to the paisa`() {
        assertEquals(48500.0, DayCloseMath.expected(10000.0, 52500.10, 14000.10), 0.0)
    }

    @Test
    fun `a short drawer is negative`() {
        val d = DayClose(day = 1, opening = 10000.0, cashIn = 40000.0, cashOut = 1500.0, counted = 48000.0)
        assertEquals(48500.0, d.expected, 0.0)
        assertEquals(-500.0, d.difference, 0.0)
    }
}
