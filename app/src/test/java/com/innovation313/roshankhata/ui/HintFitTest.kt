package com.innovation313.roshankhata.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When a box's hint wraps instead of being cut (30 Oct 2026). */
class HintFitTest {

    @Test
    fun `a hint wider than the room inside the padding wraps`() {
        assertTrue(HintFit.overruns(hintWidth = 300f, width = 280, paddingStart = 12, paddingEnd = 12))
    }

    @Test
    fun `a hint that fits is left as designed`() {
        assertFalse(HintFit.overruns(hintWidth = 200f, width = 280, paddingStart = 12, paddingEnd = 12))
        assertFalse(HintFit.overruns(hintWidth = 256f, width = 280, paddingStart = 12, paddingEnd = 12))
    }

    @Test
    fun `a box not yet laid out is not judged`() {
        assertFalse(HintFit.overruns(hintWidth = 300f, width = 0, paddingStart = 0, paddingEnd = 0))
    }
}
