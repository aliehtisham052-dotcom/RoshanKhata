package com.innovation313.roshankhata.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The walkthrough's dots must leave room for Skip.
 *
 * This is the test the bug asked for. The dots were sized once, for a tour of
 * eight steps. Nobody re-checked the arithmetic as steps were added, and at
 * thirteen the row ran past the card and clipped Skip — the control a
 * shopkeeper uses to get out of a tour they did not ask for.
 *
 * So the arithmetic is checked here instead of by eye, at every step count
 * the tour could plausibly reach and on the narrowest screens Android still
 * ships.
 */
class CoachDotsTest {

    /** Screens the app has to work on, narrowest first. */
    private val screens = listOf(320f, 360f, 392f, 411f, 480f)

    /** The count that shipped broken, on the phone it was reported from. */
    @Test
    fun `thirteen steps fit on a normal phone`() {
        val s = CoachDots.sizesFor(13, 360f)
        assertTrue(
            "13 dots must fit beside Skip",
            CoachDots.widthOf(13, s) <= CoachDots.budgetFor(360f) + 0.01f
        )
    }

    /**
     * Every count the tour might reach, on every screen. If someone adds a
     * fourteenth step, this is what tells them before the owner does.
     */
    @Test
    fun `dots fit beside skip at every step count`() {
        for (screen in screens) {
            val budget = CoachDots.budgetFor(screen)
            for (count in 1..20) {
                val s = CoachDots.sizesFor(count, screen)
                val width = CoachDots.widthOf(count, s)
                assertTrue(
                    "$count dots on a ${screen}dp screen: $width > $budget",
                    width <= budget + 0.01f
                )
            }
        }
    }

    /** A short tour is not shrunk. Eight steps looked right and still should. */
    @Test
    fun `a short tour keeps the full size dots`() {
        assertEquals(CoachDots.PREFERRED, CoachDots.sizesFor(8, 360f))
        assertEquals(CoachDots.PREFERRED, CoachDots.sizesFor(1, 320f))
    }

    /** A longer tour is shrunk rather than allowed to overflow. */
    @Test
    fun `a long tour is shrunk`() {
        val long = CoachDots.sizesFor(16, 320f)
        assertTrue(long.dotDp < CoachDots.PREFERRED.dotDp)
        assertTrue(long.gapDp < CoachDots.PREFERRED.gapDp)
    }

    /** Dots never shrink to nothing — there is a floor, and it is visible. */
    @Test
    fun `dots stay visible however long the tour`() {
        val s = CoachDots.sizesFor(60, 320f)
        assertTrue("a dot must stay visible", s.dotDp >= 3f)
        assertTrue("the active dot must stay visible", s.activeDp >= 8f)
    }

    /** No steps, no width. The arithmetic must not go negative. */
    @Test
    fun `no steps take no room`() {
        assertEquals(0f, CoachDots.widthOf(0, CoachDots.PREFERRED), 0.0f)
    }

    /**
     * Skip's allowance is real room, not a token. A translated "Skip" is
     * routinely wider than the English one.
     */
    @Test
    fun `skip keeps a real allowance`() {
        assertTrue(CoachDots.SKIP_DP >= 60f)
        assertTrue(CoachDots.budgetFor(360f) < 360f - CoachDots.SKIP_DP)
    }
}
