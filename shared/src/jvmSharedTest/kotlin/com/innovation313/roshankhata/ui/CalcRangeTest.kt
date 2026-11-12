package com.innovation313.roshankhata.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 12 Nov 2026 handover: a 15-16 digit figure came out with its last digit wrong (a Double holds about 15-16
 * significant digits). Anything past [Calc.MAX_ABS] is refused, and [Calc.tooBig] says why, so the calculator
 * shows a message instead of a figure a little off.
 */
class CalcRangeTest {
    @Test fun thirteenDigitsAndThePaisaAreExact() {
        assertEquals(9_999_999_999_999.99, Calc.evalPad("9999999999999.99")!!, 0.0)
        assertEquals(9_999_999_999_999.0, Calc.evalPad("9999999999998+1")!!, 0.0)
        assertEquals(1_234_567_890_123.45, Calc.evalPad("1234567890123.45")!!, 0.0)
        assertFalse(Calc.tooBig("9999999999999.99"))
    }

    @Test fun sixteenDigitsAreRefusedAndSaid() {
        assertNull(Calc.evalPad("1234567890123456"))
        assertTrue(Calc.tooBig("1234567890123456"))
        assertNull(Calc.evalPad("9999999999999+1"))
        assertTrue(Calc.tooBig("9999999999999+1"))
        assertNull(Calc.evalAmount("99999999999999"))
    }

    @Test fun aStepPastTheLimitIsRefusedEvenWhenTheAnswerFits() {
        assertNull(Calc.evalPad("9999999999999*1000/1000"))
        assertTrue(Calc.tooBig("9999999999999*1000/1000"))
    }

    @Test fun ordinarySumsAndBadInputAreUnchanged() {
        assertEquals(800.0, Calc.evalPad("500+300")!!, 0.0)
        assertEquals(2380.0, Calc.evalPad("3500-32%")!!, 0.0)
        assertNull(Calc.evalPad("abc"))
        assertFalse(Calc.tooBig("abc"))
        assertFalse(Calc.tooBig(""))
        assertFalse(Calc.tooBig("500+"))
    }
}
