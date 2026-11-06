package com.innovation313.roshankhata.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Where the box being typed in goes (6 Nov 2026, the owner: "bilkul mid mein"): the middle of what can
 * be seen, never just the keyboard's edge. A seen part 0..400, a 16 px gap, 24 px of stillness.
 */
class KeyboardFollowShiftTest {

    private fun shift(top: Int, bottom: Int) = KeyboardFollow.shift(top, bottom, top = 0, bottom = 400, gap = 16, still = 24)

    @Test
    fun `a box under the keyboard comes up to the middle`() {
        // 48 px tall at 500..548: its top goes to (400 - 48) / 2 = 176, so the content goes up 324.
        assertEquals(324, shift(500, 548))
    }

    @Test
    fun `a box low but in sight comes up to the middle too, not only off the edge`() {
        assertEquals(330 - 176, shift(330, 378))
    }

    @Test
    fun `a box above what can be seen comes down to the middle`() {
        assertEquals(-60 - 176, shift(-60, -12))
    }

    @Test
    fun `a box already in the middle stays still`() {
        assertEquals(0, shift(176, 224))
        assertEquals(0, shift(190, 238))
        assertEquals(0, shift(160, 208))
    }

    @Test
    fun `a box a little further than the stillness from the middle moves`() {
        assertEquals(25, shift(201, 249))
        assertEquals(-25, shift(151, 199))
    }

    @Test
    fun `a box near the middle of a small seen part but cut by its edge moves`() {
        // 20 px tall in a 40 px part: taller than the part less both gaps, so its top goes under the top.
        assertEquals(30 - 16, KeyboardFollow.shift(30, 50, top = 0, bottom = 40, gap = 16, still = 24))
    }

    @Test
    fun `a box taller than what can be seen puts its top just under the top`() {
        assertEquals(300 - 16, shift(300, 800))
    }
}
