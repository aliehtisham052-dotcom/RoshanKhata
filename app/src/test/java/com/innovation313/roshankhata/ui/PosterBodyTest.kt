package com.innovation313.roshankhata.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the body of a poster stands when its foot is not full (19 Nov 2026, the owner's photos: New stock, Offer and
 * Hours with the lowest third of the picture empty, because his shop showed no number and no address). Five designs
 * were laid out at fixed heights for a full foot. These hold the arithmetic that hands the room a foot does not need
 * to the body: a full foot is exactly the old design, the parts keep their order and never leave the picture, and
 * with any foot the body ends near the lowest place it may reach. Pure numbers, no drawing.
 */
class PosterBodyTest {

    private val delta = 0.01f

    /** The most of the picture that may stand empty between a body and the lowest place it may reach. */
    private val bare = 150f

    /** Every foot a shop can have: a number or none, 0 to 2 address rows, the app's mark on or off. */
    private class State(val pill: Boolean, val address: Int, val mark: Boolean) {
        override fun toString() = "pill=$pill address=$address mark=$mark"
    }

    private val states = listOf(true, false).flatMap { p -> (0..2).flatMap { a -> listOf(true, false).map { m -> State(p, a, m) } } }

    private fun foot(s: State, pillTop: Float, addressCy: Float) = PosterBody.foot(s.pill, s.address, s.mark, pillTop, addressCy)

    private fun inside(b: PosterBody.Box, what: String) {
        assertTrue("$what leaves the picture's sides: ${b.left}..${b.right}", b.left >= 40f && b.right <= 1040f)
        assertTrue("$what leaves the picture's top", b.top >= 0f)
        assertEquals("$what is not centred", 540f, b.centerX, delta)
    }

    // ------------------------------------------------------------------------------------------ the foot

    @Test
    fun `a full foot has no room to give and stands where the design put it`() {
        for (lines in 1..2) for (mark in listOf(true, false)) {
            val f = PosterBody.foot(true, lines, mark, 1530f, 1770f)
            assertEquals(0f, f.slack, delta)
            assertEquals(1530f, f.pillTop, delta)
            assertEquals(1770f, f.addressCy, delta)
            assertEquals(1500f, f.limit, delta)
        }
    }

    @Test
    fun `a call button with no address under it sinks, and the body follows it`() {
        val f = PosterBody.foot(true, 0, false, 1530f, 1770f)
        assertEquals(1586f, f.pillTop, delta)
        assertEquals(56f, f.slack, delta)
        // The button (180 tall at most) still ends above the lowest place a body may reach.
        assertTrue(f.pillTop + 180f <= PosterBody.FLOOR)
    }

    @Test
    fun `with no number the body may come down to the address, and with nothing to the floor`() {
        val address = PosterBody.foot(false, 1, false, 1530f, 1770f)
        assertEquals(1692f, address.limit, delta)
        assertEquals(192f, address.slack, delta)
        assertEquals(PosterBody.FLOOR, PosterBody.foot(false, 0, false, 1530f, 1770f).limit, delta)
        assertEquals(PosterBody.FLOOR_MARK, PosterBody.foot(false, 0, true, 1530f, 1770f).limit, delta)
    }

    @Test
    fun `a foot never asks the body to stand higher than the design drew it`() {
        for (s in states) for ((pillTop, addressCy) in listOf(1530f to 1770f, 1494f to 1734f, 1530f to 1784f, 1536f to 1790f, 1500f to 1744f)) {
            assertTrue("$s", foot(s, pillTop, addressCy).slack >= 0f)
        }
    }

    // ------------------------------------------------------------------------------------------ New stock

    @Test
    fun `New stock with a full foot is the design as it was drawn`() {
        val b = PosterBody.newStock(0f)
        assertEquals(300f, b.kickerCy, delta)
        assertEquals(470f, b.headCy, delta)
        assertEquals(180f, b.hero.left, delta); assertEquals(560f, b.hero.top, delta)
        assertEquals(900f, b.hero.right, delta); assertEquals(1230f, b.hero.bottom, delta)
        assertEquals(1230f, b.stripTop, delta)
        assertEquals(1460f, b.messageCy, delta)
        assertEquals(1f, b.scale, delta)
    }

    @Test
    fun `New stock fills whatever its foot leaves, in order and inside the picture`() {
        for (s in states) {
            val f = foot(s, 1530f, 1770f)
            val b = PosterBody.newStock(f.slack)
            inside(b.hero, "the crate ($s)")
            assertEquals("the crate lost its shape ($s)", 720f / 670f, b.hero.width / b.hero.height, 0.001f)
            assertTrue("$s", b.kickerCy < b.headCy)
            // The headline (up to 170 tall around its line) stays clear of the crate, as drawn: 90 between them.
            assertTrue("the headline runs into the crate ($s)", b.hero.top - b.headCy >= 90f)
            assertTrue("$s", b.stripTop >= b.hero.bottom)
            // The item strip is 170 tall at most; the message stands under it.
            assertTrue("the message runs into the strip ($s)", b.messageCy - b.stripTop >= 230f)
            assertTrue("the message runs into the foot ($s)", b.messageCy + 40f <= f.limit)
            assertTrue("the picture's foot is bare ($s): ${f.limit - b.messageCy}", f.limit - (b.messageCy + 40f) <= bare)
        }
    }

    // ------------------------------------------------------------------------------------------ Hours

    @Test
    fun `Hours with a full foot is the design as it was drawn`() {
        for (rows in 1..6) {
            val b = PosterBody.hours(0f, rows)
            val h = minOf(48f + rows * 112f, 540f)
            assertEquals(240f, b.clock.left, delta); assertEquals(126f, b.clock.top, delta)
            assertEquals(840f, b.clock.right, delta); assertEquals(726f, b.clock.bottom, delta)
            assertEquals(812f, b.headCy, delta)
            assertEquals(1191f - h / 2f, b.panelTop, delta)
            assertEquals(h, b.panelHeight, delta)
        }
    }

    @Test
    fun `Hours fills whatever its foot leaves, in order and inside the picture`() {
        for (s in states) for (rows in 1..6) {
            val f = foot(s, 1494f, 1734f)
            val b = PosterBody.hours(f.slack, rows)
            inside(b.clock, "the clock ($s, $rows rows)")
            assertEquals("the clock is no longer round", b.clock.width, b.clock.height, delta)
            // The shop's name (to 144) above, the headline under: as drawn, 86 from the clock to the headline's line.
            assertTrue("$s", b.clock.top >= 126f)
            assertTrue("the headline runs into the clock ($s, $rows)", b.headCy - b.clock.bottom >= 86f)
            assertTrue("the rows are squeezed ($s, $rows)", b.panelHeight >= minOf(48f + rows * 112f, 540f))
            assertTrue("the panel runs into the foot ($s, $rows)", b.panelTop + b.panelHeight <= f.limit + delta)
        }
    }

    @Test
    fun `Hours with the usual three rows leaves no bare foot`() {
        for (s in states) {
            val f = foot(s, 1494f, 1734f)
            val b = PosterBody.hours(f.slack, 3)
            // As drawn, three rows end 81 above the button; that much is the design's own air.
            assertTrue("bare foot ($s): ${f.limit - (b.panelTop + b.panelHeight)}", f.limit - (b.panelTop + b.panelHeight) <= bare)
            assertTrue("the headline runs into the panel ($s)", b.panelTop - b.headCy >= 150f)
        }
    }

    // ------------------------------------------------------------------------------------------ Offer

    @Test
    fun `Offer with a full foot is the design as it was drawn`() {
        val days = PosterBody.offer(0f, days = true)
        assertEquals(345f, days.headCy, delta)
        assertEquals(180f, days.coin.left, delta); assertEquals(430f, days.coin.top, delta)
        assertEquals(900f, days.coin.right, delta); assertEquals(1150f, days.coin.bottom, delta)
        assertEquals(1170f, days.roomTop, delta)
        assertEquals(1386f, days.roomBottom, delta)
        assertEquals(1406f, days.tagTop, delta)
        assertEquals(1500f, PosterBody.offer(0f, days = false).roomBottom, delta)
    }

    @Test
    fun `Offer fills whatever its foot leaves, in order and inside the picture`() {
        for (s in states) for (days in listOf(true, false)) {
            val f = foot(s, 1530f, 1784f)
            val b = PosterBody.offer(f.slack, days)
            inside(b.coin, "the coin ($s)")
            assertEquals("the coin is no longer round", b.coin.width, b.coin.height, delta)
            assertTrue("the headline runs into the coin ($s)", b.coin.top - b.headCy >= 85f)
            assertTrue("$s", b.roomTop >= b.coin.bottom + 20f)
            assertTrue("the words have less room than drawn ($s)", b.roomBottom - b.roomTop >= 216f)
            val end = if (days) b.tagTop + 78f else b.roomBottom
            assertTrue("the body runs into the foot ($s, days=$days): $end > ${f.limit}", end <= f.limit + delta)
            assertTrue("bare foot ($s, days=$days): ${f.limit - end}", f.limit - end <= bare)
        }
    }

    // ------------------------------------------------------------------------------------------ Closed

    /** The lowest the Closed body may reach: above the "for urgent work" line when there is a button. */
    private fun closedLimit(s: State): Float {
        val f = foot(s, 1536f, 1790f)
        return if (s.pill) f.pillTop - 106f else f.limit
    }

    @Test
    fun `Closed with a full foot and the owner's own line is the design as it was drawn`() {
        val b = PosterBody.closed(1430f, rows = 0, sorry = true)
        assertEquals(126f, b.sign.left, delta); assertEquals(276f, b.sign.top, delta)
        assertEquals(954f, b.sign.right, delta); assertEquals(906f, b.sign.bottom, delta)
        assertEquals(1008f, b.panelTop, delta)
        assertEquals(352f, b.panelHeight, delta)
        assertEquals("no room, no line of regret", 0f, b.sorryCy, delta)
    }

    @Test
    fun `Closed fills whatever its foot leaves, in order and inside the picture`() {
        for (s in states) for (rows in 0..2) {
            val limit = closedLimit(s)
            val b = PosterBody.closed(limit, rows, sorry = true)
            inside(b.sign, "the sign ($s, $rows rows)")
            assertEquals("the sign lost its shape", 828f / 630f, b.sign.width / b.sign.height, 0.001f)
            // The sign hangs under the shop's name (to 156, its posts 18 above it).
            assertTrue("$s", b.sign.top >= 276f)
            assertTrue("the panel runs into the sign ($s, $rows)", b.panelTop - b.sign.bottom >= 100f)
            val end = if (b.sorryCy > 0f) b.sorryCy + 30f else b.panelTop + b.panelHeight
            if (b.sorryCy > 0f) assertTrue("the line of regret runs into the panel", b.sorryCy - (b.panelTop + b.panelHeight) >= 60f)
            assertTrue("the body runs into the foot ($s, $rows): $end > $limit", end <= limit + delta)
            assertTrue("bare foot ($s, $rows): ${limit - end}", limit - end <= bare)
        }
    }

    @Test
    fun `the line of regret is said only where there is room for it`() {
        assertEquals(0f, PosterBody.closed(1430f, rows = 1, sorry = true).sorryCy, delta)
        assertTrue(PosterBody.closed(PosterBody.FLOOR, rows = 1, sorry = true).sorryCy > 0f)
        assertEquals(0f, PosterBody.closed(PosterBody.FLOOR, rows = 1, sorry = false).sorryCy, delta)
    }

    // ------------------------------------------------------------------------------------------ the shop's own announcement

    @Test
    fun `the announcement panel is as tall as its words ask, inside its room, whatever the foot`() {
        for (s in states) for (words in listOf(60f, 180f, 420f, 700f, 1100f, 1600f)) {
            val f = foot(s, PosterBody.CUSTOM_PILL_TOP, PosterBody.CUSTOM_ADDRESS_CY)
            val b = PosterBody.custom(f.limit, words)
            val room = f.limit - PosterBody.CUSTOM_TOP
            assertTrue("above its room ($s, $words)", b.panelTop >= PosterBody.CUSTOM_TOP - delta)
            assertTrue("below its room ($s, $words)", b.panelBottom <= f.limit + delta)
            val need = PosterBody.CUSTOM_HEAD + words + PosterBody.CUSTOM_FOOT
            assertTrue("the words do not fit their panel ($s, $words)", b.panelBottom - b.panelTop >= minOf(need, room) - delta)
            assertTrue("the words start above their place", b.wordsTop >= b.panelTop + PosterBody.CUSTOM_HEAD - delta)
            if (need <= room) assertTrue("the words leave the panel ($s, $words)",
                b.wordsTop + words <= b.panelBottom - PosterBody.CUSTOM_FOOT + delta)
            // A short line does not get the whole room as one bare box (the old fixed panel): at most half of what is over.
            if (need < room) assertTrue("a bare box around a short line ($s, $words)",
                b.panelBottom - b.panelTop <= need + 0.5f * (room - need) + delta)
            // What is left over stands above and below the panel in about equal parts: never all of it under it.
            val above = b.panelTop - PosterBody.CUSTOM_TOP
            val below = f.limit - b.panelBottom
            assertTrue("all the spare room is under the panel ($s, $words): $above / $below", below <= above * 1.6f + delta)
        }
    }

    // ------------------------------------------------------------------------------------------ more room, never a jump

    @Test
    fun `more room moves every part down or leaves it, never up`() {
        var s = 0f
        var ns = PosterBody.newStock(0f); var h = PosterBody.hours(0f, 3); var o = PosterBody.offer(0f, true)
        while (s <= 340f) {
            val ns2 = PosterBody.newStock(s); val h2 = PosterBody.hours(s, 3); val o2 = PosterBody.offer(s, true)
            assertTrue(ns2.messageCy >= ns.messageCy && ns2.stripTop >= ns.stripTop && ns2.hero.bottom >= ns.hero.bottom && ns2.headCy >= ns.headCy)
            assertTrue(h2.panelTop >= h.panelTop && h2.headCy >= h.headCy && h2.clock.bottom >= h.clock.bottom)
            assertTrue(o2.tagTop >= o.tagTop && o2.roomTop >= o.roomTop && o2.coin.bottom >= o.coin.bottom && o2.headCy >= o.headCy)
            ns = ns2; h = h2; o = o2
            s += 7f
        }
    }
}
