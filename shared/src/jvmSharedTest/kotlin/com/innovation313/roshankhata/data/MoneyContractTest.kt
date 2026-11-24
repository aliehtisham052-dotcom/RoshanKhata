package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.ui.Calc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The money contract (24 Nov 2026, plan item A5): ONE rule turns a Double into a figure the shop could pay.
 * The vectors are made-up amounts, in the three kinds of currency the app has (2, 3 and no decimals), and are
 * the same on the phone and the computer because the rule is in :shared.
 */
class MoneyContractTest {

    private fun <T> withSign(sign: String, body: () -> T): T {
        val before = Currency.symbol
        Currency.symbol = sign
        try { return body() } finally { Currency.symbol = before }
    }

    @Test fun `the shop's own decimals are two, three or none`() {
        assertEquals(2, withSign("Rs") { Currency.decimals() })
        assertEquals(3, withSign("KWD") { Currency.decimals() })
        assertEquals(0, withSign("Rp") { Currency.decimals() })
    }

    @Test fun `a figure is rounded by its decimal text, half up`() = withSign("Rs") {
        // 1.005 is written 1.005, whatever its binary value is: the screen prints 1.01 and so does every sum.
        assertEquals(1.01, Money.round(1.005), 0.0)
        assertEquals(2.68, Money.round(2.675), 0.0)
        assertEquals(0.3, Money.round(0.1 + 0.2), 0.0)
        assertEquals(333.33, Money.round(1000.0 / 3), 0.0)
        assertEquals(5850.3, Money.round(3 * 1950.10), 0.0)
        // half up means away from zero for a minus too: the round-off down is as big as the round-off up
        assertEquals(-1.01, Money.round(-1.005), 0.0)
    }

    @Test fun `three-decimal and no-decimal coins keep their own places`() {
        assertEquals(0.126, withSign("KWD") { Money.round(0.1255) }, 0.0)
        assertEquals(0.125, withSign("KWD") { Money.round(0.125) }, 0.0)
        assertEquals(16928.0, withSign("Rp") { Money.round(16927.5) }, 0.0)
        assertEquals(-3.0, withSign("Rp") { Money.round(-2.5) }, 0.0)
    }

    @Test fun `the smallest coin as a whole number`() {
        assertEquals(1234L, withSign("KWD") { Money.minor(1.234) })
        assertEquals(123457L, withSign("Rs") { Money.minor(1234.565) })
        assertEquals(-250L, withSign("Rs") { Money.minor(-2.5) })
        assertEquals(16928L, withSign("Rp") { Money.minor(16927.5) })
    }

    @Test fun `two sums are the same money when the coin says so`() = withSign("Rs") {
        assertTrue(Money.same(0.1 + 0.2, 0.3))
        assertTrue(Money.same(5850.299999999999, 5850.3))
        assertFalse(Money.same(10.00, 10.01))
        // a fils apart is not the same in KWD, though the old fixed hundred called it the same
        assertFalse(withSign("KWD") { Money.same(1.001, 1.002) })
    }

    @Test fun `a percent is worked out in decimals and rounded once`() {
        assertEquals(33.33, withSign("Rs") { Money.percentOf(333.3, 10.0) }, 0.0)
        assertEquals(250.0, withSign("Rs") { Money.percentOf(2500.0, 10.0) }, 0.0)
        // a tie goes up (the old banker's rounding went to the even cent: 0.02)
        assertEquals(0.03, withSign("Rs") { Money.percentOf(2.5, 1.0) }, 0.0)
        // KWD keeps its third decimal (the old code gave 0.12)
        assertEquals(0.125, withSign("KWD") { Money.percentOf(1.0, 12.5) }, 0.0)
        // Rp has none: 10% of 1,555 is 155.5, which is 156 in rupiah
        assertEquals(156.0, withSign("Rp") { Money.percentOf(1555.0, 10.0) }, 0.0)
    }

    @Test fun `the discount box uses the same rule`() = withSign("Rs") {
        assertEquals(0.03, Calc.evalDiscount("1%", 2.5)!!, 0.0)
        assertEquals(33.33, Calc.evalDiscount("10%", 333.3)!!, 0.0)
        assertEquals(0.125, withSign("KWD") { Calc.evalDiscount("12.5%", 1.0)!! }, 0.0)
    }

    @Test fun `big and odd figures do not break it`() = withSign("Rs") {
        assertEquals(Calc.MAX_ABS, Money.round(Calc.MAX_ABS), 0.0)
        assertTrue(Money.round(Double.NaN).isNaN())
        assertEquals(Double.POSITIVE_INFINITY, Money.round(Double.POSITIVE_INFINITY), 0.0)
        assertEquals(0.0, Money.round(0.0), 0.0)
        assertTrue(Money.isZero(Money.round(2e-14)))
    }
}
