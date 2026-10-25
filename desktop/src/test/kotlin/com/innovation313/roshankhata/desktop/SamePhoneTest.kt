package com.innovation313.roshankhata.desktop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A party saved under its own number showed the number twice in the bill's
 * supplier list ("+92300… · +92300…", 25 Oct 2026, the audit). Made-up numbers.
 */
class SamePhoneTest {

    @Test
    fun `a name that is the number shows it once`() {
        assertTrue(samePhone("+923000000101", "+923000000101"))
        assertTrue(samePhone("+92 300 0000101", "0300 0000101"))
    }

    @Test
    fun `a real name keeps its number beside it`() {
        assertFalse(samePhone("0300 0000101", "Aslam Kareem"))
        assertFalse(samePhone("0300 0000101", "Shop 3000000101"))
        assertFalse(samePhone("0300 0000101", "0333 0000202"))
    }
}
