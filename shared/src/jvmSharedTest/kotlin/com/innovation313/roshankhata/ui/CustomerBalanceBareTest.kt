package com.innovation313.roshankhata.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The customer page's figure has no sign; the side's words and colour say who owes (8 Nov 2026). */
class CustomerBalanceBareTest {

    @Test
    fun `money to collect and money owed out show the same plain amount`() {
        assertEquals(Format.money(800.0), Format.customerBalanceBare(800.0))
        assertEquals(Format.money(800.0), Format.customerBalanceBare(-800.0))
    }

    @Test
    fun `a settled account carries no sign either way`() {
        assertEquals(Format.money(0.0), Format.customerBalanceBare(0.0000001))
        assertEquals(Format.money(0.0), Format.customerBalanceBare(-0.0000001))
        assertFalse(Format.customerBalanceBare(-0.0000001).startsWith("-"))
    }
}
