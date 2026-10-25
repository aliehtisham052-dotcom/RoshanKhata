package com.innovation313.roshankhata.screens

import com.innovation313.roshankhata.words.W
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The words that say which way a balance runs (25 Oct 2026, the audit): the
 * computer's customer page, receipt, import and delete questions showed a
 * bare or unsigned figure, so "- Rs 5,000" read as money the shop owes.
 */
class BalanceSideWordTest {

    @Test
    fun `money to collect, money owed out and settled`() {
        assertEquals(W.you_will_get, balanceSideWord(5_000.0))
        assertEquals(W.you_will_give, balanceSideWord(-300.0))
        assertEquals(W.settled, balanceSideWord(0.0))
    }

    @Test
    fun `a settled book a hair off zero still reads settled`() {
        assertEquals(W.settled, balanceSideWord(0.0000001))
        assertEquals(W.settled, balanceSideWord(-0.0000001))
    }
}
