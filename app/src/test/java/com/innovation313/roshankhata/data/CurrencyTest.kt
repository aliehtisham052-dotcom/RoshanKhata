package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sign rules that need no device: cleaning a typed sign and naming a chosen one. */
class CurrencyTest {

    @Test
    fun blankOrNullFallsBackToRs() {
        assertEquals("Rs", Currency.clean(null))
        assertEquals("Rs", Currency.clean("   "))
    }

    @Test
    fun typedSignIsTrimmedAndCapped() {
        assertEquals("AED", Currency.clean("  AED "))
        assertEquals("ABCDE", Currency.clean("ABCDEFGH"))
    }

    @Test
    fun labelNamesListedSignsByCodeAndCustomOnesAlone() {
        assertEquals("Rs — PKR", Currency.label("Rs"))
        assertEquals("₹ — INR", Currency.label("₹"))
        assertEquals("AED", Currency.label("AED"))
        assertEquals("Tk", Currency.label("Tk"))
    }

    @Test
    fun listedSignsAreUniqueAndFit() {
        val signs = Currency.CHOICES.map { it.first }
        assertEquals(signs.size, signs.toSet().size)
        assertTrue(signs.all { it.length <= Currency.MAX_LENGTH })
    }
}
