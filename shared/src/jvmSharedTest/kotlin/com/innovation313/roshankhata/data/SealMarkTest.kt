package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SealMarkTest {
    @Test
    fun aSealIsTakenOnceWithItsWords() {
        SealMark.drawn("10 NOV 2026", "STATEMENT", now = 1_000L)
        val m = SealMark.take(now = 2_000L)
        assertEquals("10 NOV 2026", m?.middle)
        assertEquals("STATEMENT", m?.below)
        assertNull(SealMark.take(now = 2_001L))
    }

    @Test
    fun anOldSealNeverLandsOnANewPaper() {
        SealMark.drawn("1 NOV 2026", "REPORT", now = 0L)
        assertNull(SealMark.take(maxAgeMs = 5_000L, now = 6_000L))
    }

    @Test
    fun clearForgetsTheLastSeal() {
        SealMark.drawn("10 NOV 2026", "INV-000005", now = 0L)
        SealMark.clear()
        assertNull(SealMark.take(now = 1L))
    }
}
