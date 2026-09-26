package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The text-size arithmetic, exactly. Everything the owner sees on screen
 * follows from these numbers, so they are pinned here rather than eyeballed.
 */
class TextSizeTest {

    private fun eff(system: Float, level: Int) = TextSize.effective(system, level)

    @Test
    fun normalNeverTouchesThePhonesOwnScale() {
        for (system in floatArrayOf(0.85f, 1.0f, 1.15f, 1.3f, 2.0f)) {
            assertEquals(system, eff(system, TextSize.NORMAL), 0f)
        }
    }

    @Test
    fun onAnOrdinaryPhoneTheLevelsAreExactPercentages() {
        assertEquals(0.90f, eff(1.0f, TextSize.SMALL), 0f)
        assertEquals(1.15f, eff(1.0f, TextSize.LARGE), 0f)
        assertEquals(1.30f, eff(1.0f, TextSize.LARGEST), 0f)
    }

    @Test
    fun itMultipliesThePhonesScaleRatherThanReplacingIt() {
        // Phone already at 115%, owner asks for Large here: 1.15 x 1.15.
        assertEquals(1.32f, eff(1.15f, TextSize.LARGE), 0f)
    }

    @Test
    fun enlargingStopsAtTheCeiling() {
        assertEquals(TextSize.CEILING, eff(1.3f, TextSize.LARGEST), 0f)   // 1.69 -> 1.5
        assertEquals(TextSize.CEILING, eff(1.2f, TextSize.LARGEST), 0f)   // 1.56 -> 1.5
    }

    @Test
    fun aPhoneAlreadyAboveTheCeilingIsNeverShrunkByAskingForBigger() {
        assertEquals(2.0f, eff(2.0f, TextSize.LARGEST), 0f)
        assertEquals(2.0f, eff(2.0f, TextSize.LARGE), 0f)
    }

    @Test
    fun shrinkingStopsAtTheFloorButNeverEnlargesASmallPhone() {
        assertEquals(TextSize.FLOOR, eff(0.9f, TextSize.SMALL), 0f)      // 0.81 -> 0.85
        assertEquals(0.8f, eff(0.8f, TextSize.SMALL), 0f)                // already below: kept
    }

    @Test
    fun anOutOfRangeStoredLevelIsTreatedAsTheNearestRealOne() {
        assertEquals(eff(1.0f, TextSize.LARGEST), eff(1.0f, 99), 0f)
        assertEquals(eff(1.0f, TextSize.SMALL), eff(1.0f, -5), 0f)
    }
}
