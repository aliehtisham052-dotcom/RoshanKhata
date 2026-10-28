package com.innovation313.roshankhata.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a box in calc mode shows under itself and puts back on Enter / "=" / leaving it. */
class CalcTextTest {

    @Test
    fun `a plain figure is not a sum and is left as typed`() {
        for (t in listOf("", "500", "1,500", "-250", "12.5", "10%", "17 %")) {
            assertFalse(t, CalcText.isSum(t))
            assertNull(t, CalcText.settled(t, CalcKind.MONEY))
            assertNull(t, CalcText.line(t, CalcKind.MONEY))
        }
    }

    @Test
    fun `a sum is worked out to the paisa, and to grams for a quantity`() {
        assertTrue(CalcText.isSum("500+300"))
        assertEquals("800", CalcText.settled("500+300", CalcKind.MONEY))
        assertEquals("5400", CalcText.settled("12x450", CalcKind.MONEY))
        assertEquals("33.33", CalcText.settled("100/3", CalcKind.MONEY))
        assertEquals("0.333", CalcText.settled("1/3", CalcKind.QUANTITY))
        assertEquals("2380", CalcText.settled("3500-32%", CalcKind.MONEY))
        assertEquals("1200", CalcText.settled("1,500-300", CalcKind.MONEY))
    }

    @Test
    fun `a percent box keeps its percent`() {
        assertEquals("17", CalcText.settled("10+7", CalcKind.PERCENT))
        assertEquals("= 17%", CalcText.line("10+7", CalcKind.PERCENT))
    }

    @Test
    fun `a sum that does not read is left for the box's own check`() {
        assertNull(CalcText.settled("500+", CalcKind.MONEY))
        assertNull(CalcText.settled("5++", CalcKind.MONEY))
    }
}
