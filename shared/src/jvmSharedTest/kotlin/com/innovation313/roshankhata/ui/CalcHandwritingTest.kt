package com.innovation313.roshankhata.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The ways a shopkeeper writes a figure by hand (21 Oct 2026, the critical
 * review): each of these was refused, or read as a different number, until
 * Calc.normalize learned it.
 */
class CalcHandwritingTest {

    private fun pad(s: String) = Calc.evalPad(s)!!

    @Test
    fun `thousands commas are read as the app prints them`() {
        assertEquals(1500.0, pad("1,500"), 0.0)
        assertEquals(150000.0, pad("1,50,000"), 0.0)
        assertEquals(2700.0, pad("1,200+1,500"), 0.0)
    }

    @Test
    fun `x is times, = finishes the sum, the rupee marks are dropped`() {
        assertEquals(5400.0, pad("12x450"), 0.0)
        assertEquals(5400.0, pad("12 X 450"), 0.0)
        assertEquals(800.0, pad("500+300="), 0.0)
        assertEquals(500.0, pad("500/-"), 0.0)
        assertEquals(800.0, pad("500/-+300/-"), 0.0)
        assertEquals(750.0, pad("Rs 750"), 0.0)
        assertEquals(750.0, pad("Rs.750"), 0.0)
        assertEquals(750.0, pad("₨750"), 0.0)
    }

    @Test
    fun `division and percentages still mean what they did`() {
        assertEquals(250.0, pad("500/2"), 0.0)
        assertEquals(160.0, pad("500*32%"), 0.001)
        assertEquals(2380.0, pad("3500-32%"), 0.001)
        assertNull(Calc.evalPad("abc"))
    }

    @Test
    fun `a percent box reads 17 and 17 percent alike`() {
        assertEquals(17.0, Calc.evalPercent("17")!!, 0.0)
        assertEquals(17.0, Calc.evalPercent("17%")!!, 0.0)
        assertEquals(17.5, Calc.evalPercent("17.5 %")!!, 0.0)
        assertNull(Calc.evalPercent("seventeen"))
    }

    @Test
    fun `a discount may be rupees or a share of the line`() {
        assertEquals(250.0, Calc.evalDiscount("10%", 2500.0)!!, 0.0)
        assertEquals(250.0, Calc.evalDiscount("250", 2500.0)!!, 0.0)
        assertEquals(250.0, Calc.evalDiscount("-250", 2500.0)!!, 0.0)
        assertEquals(33.33, Calc.evalDiscount("10%", 333.3)!!, 0.0)
        assertEquals(300.0, Calc.evalDiscount("100+200", 2500.0)!!, 0.0)
        assertNull(Calc.evalDiscount("ten", 2500.0))
    }
}
