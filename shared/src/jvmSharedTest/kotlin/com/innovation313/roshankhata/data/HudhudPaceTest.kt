package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The speaking pace of the voice settings' slider (14 Nov 2026): always on one of its steps, always inside its range. */
class HudhudPaceTest {

    /** What a slider accepts: a step of a tenth up from its start, within a hair (Material's own tolerance, 1e-4 of a step). */
    private fun onAStep(value: Float): Boolean {
        val steps = (value - HudhudPace.MIN) / HudhudPace.STEP
        return Math.abs(Math.round(steps) - steps) < 1e-4
    }

    @Test fun `a pace on a step stays where it is`() {
        for (tenths in 6..15) {
            val pace = tenths / 10f
            assertEquals("$pace", pace, HudhudPace.snap(pace), 0f)
        }
    }

    @Test fun `a pace a hair off a step lands on it`() {
        // 1.0 less a tenth, again and again, as the voice's "slower" does: not exactly 0.8, 0.7, 0.6.
        var pace = 1f
        repeat(6) { pace -= 0.1f; assertTrue(onAStep(HudhudPace.snap(pace))) }
        assertEquals(0.8f, HudhudPace.snap(1f - 0.1f - 0.1f), 0f)
        assertEquals(1.2f, HudhudPace.snap(1f + 0.1f + 0.1f), 0f)
    }

    @Test fun `a pace between two steps goes to the nearer`() {
        assertEquals(0.9f, HudhudPace.snap(0.93f), 0f)
        assertEquals(1.0f, HudhudPace.snap(0.96f), 0f)
        assertEquals(1.3f, HudhudPace.snap(1.27f), 0f)
    }

    @Test fun `a pace outside the range is held to its ends`() {
        assertEquals(0.6f, HudhudPace.snap(0.1f), 0f)
        assertEquals(0.6f, HudhudPace.snap(-4f), 0f)
        assertEquals(1.5f, HudhudPace.snap(3f), 0f)
        assertEquals(1.5f, HudhudPace.snap(Float.POSITIVE_INFINITY), 0f)
    }

    @Test fun `a number that is no number is the style's own pace`() {
        assertEquals(1f, HudhudPace.snap(Float.NaN), 0f)
    }

    @Test fun `every pace the slider can give is written in digits 0-9 with a plain point`() {
        val labels = (6..15).map { HudhudPace.label(HudhudPace.snap(it / 10f)) }
        assertEquals(listOf("0.6×", "0.7×", "0.8×", "0.9×", "1.0×", "1.1×", "1.2×", "1.3×", "1.4×", "1.5×"), labels)
    }

    @Test fun `a locale that writes its own digits does not change the label`() {
        val before = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale("ar", "EG"))
            assertEquals("0.9×", HudhudPace.label(0.9f))
            java.util.Locale.setDefault(java.util.Locale("bn", "BD"))
            assertEquals("1.1×", HudhudPace.label(1.1f))
        } finally {
            java.util.Locale.setDefault(before)
        }
    }
}
