package com.innovation313.roshankhata.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRatingRuleTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_000 * day

    @Test fun realUseAndTimeAsksOnce() {
        assertTrue(AppRating.due(AppRating.Use(5, 3, now - 4 * day, 0), now))
    }

    @Test fun tooLittleUseOrTooNewDoesNotAsk() {
        assertFalse(AppRating.due(AppRating.Use(4, 3, now - 4 * day, 0), now))
        assertFalse(AppRating.due(AppRating.Use(9, 2, now - 4 * day, 0), now))
        assertFalse(AppRating.due(AppRating.Use(9, 5, now - 1 * day, 0), now))
        assertFalse(AppRating.due(AppRating.Use(9, 5, 0, 0), now))
    }

    @Test fun notAgainWithin120Days() {
        assertFalse(AppRating.due(AppRating.Use(50, 20, now - 200 * day, now - 119 * day), now))
        assertTrue(AppRating.due(AppRating.Use(50, 20, now - 300 * day, now - 121 * day), now))
    }
}
