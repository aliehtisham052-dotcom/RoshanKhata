package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Hudhud's flights stay inside the sky on his sheet (14 Nov 2026, the owner's video: "wo screen se bahar jaata hai aur
 * doosri taraf se andar aata hai" must never happen again): the perches, the perch he goes to, the arc and its time.
 */
class HudhudMotionTest {

    @Test fun `the three perches run start, middle, end and mirror in right-to-left`() {
        val l = (0..2).map { HudhudMotion.perchLeft(it, 360f, 84f, 16f, rtl = false) }
        assertEquals(listOf(16f, 138f, 260f), l)
        val r = (0..2).map { HudhudMotion.perchLeft(it, 360f, 84f, 16f, rtl = true) }
        assertEquals(listOf(260f, 138f, 16f), r)
        // The middle is the middle.
        assertEquals((360f - 84f) / 2f, l[1], 0.001f)
    }

    @Test fun `every perch keeps the whole stage inside the sky, however narrow the sky is`() {
        for (width in listOf(0f, 60f, 100f, 200f, 360f, 800f)) {
            for (i in 0 until HudhudMotion.PERCHES) for (rtl in listOf(false, true)) {
                val left = HudhudMotion.perchLeft(i, width, 84f, 16f, rtl)
                assertTrue("left $left in width $width", left >= 16f - 0.001f)
                assertTrue("right edge in width $width", width < 116f || left + 84f <= width - 16f + 0.001f)
            }
        }
    }

    @Test fun `a flight always goes to another perch and avoids the last one while it can`() {
        val random = Random(313)
        for (home in 0 until HudhudMotion.PERCHES) {
            repeat(200) {
                val to = HudhudMotion.otherPerch(home, last = null, random = random)
                assertNotEquals(home, to)
                assertTrue(to in 0 until HudhudMotion.PERCHES)
            }
        }
        // From the middle the two others are left and right: after one, the other.
        repeat(50) { assertEquals(2, HudhudMotion.otherPerch(1, last = 0, random = random)) }
        repeat(50) { assertEquals(0, HudhudMotion.otherPerch(1, last = 2, random = random)) }
        // From the end, the last one is avoided: 0 -> never again 0 when 1 is there.
        repeat(50) { assertEquals(1, HudhudMotion.otherPerch(2, last = 0, random = random)) }
    }

    @Test fun `the arc starts and ends exactly on the perches and never rises above the room`() {
        for (rise in listOf(0f, 12f, 36f, 80f)) {
            for ((x0, x1) in listOf(16f to 260f, 260f to 16f, 138f to 260f, 16f to 138f)) {
                val arc = HudhudMotion.arc(x0, 100f, x1, 100f, rise)
                assertEquals(x0, arc.at(0f).first, 0.001f)
                assertEquals(100f, arc.at(0f).second, 0.001f)
                assertEquals(x1, arc.at(1f).first, 0.001f)
                assertEquals(100f, arc.at(1f).second, 0.001f)
                var apex = 100f
                for (step in 0..200) {
                    val (x, y) = arc.at(step / 200f)
                    assertTrue("x $x stays between the perches", x >= minOf(x0, x1) - 0.001f && x <= maxOf(x0, x1) + 0.001f)
                    assertTrue("y $y never below his perch", y <= 100f + 0.001f)
                    assertTrue("y $y never above the room ($rise)", y >= 100f - rise - 0.01f)
                    if (y < apex) apex = y
                }
                // It reaches the room it has (and no more), so a flight is as big as the sky allows.
                assertEquals(100f - rise, apex, 0.05f)
            }
        }
    }

    @Test fun `no room is a flat path and a negative rise is no room`() {
        val flat = HudhudMotion.arc(16f, 40f, 260f, 40f, -20f)
        for (step in 0..20) assertEquals(40f, flat.at(step / 20f).second, 0.001f)
    }

    @Test fun `a longer flight takes longer, between 0_7 and 1_4 seconds`() {
        val short = HudhudMotion.flightMillis(122f, 360f)
        val long = HudhudMotion.flightMillis(244f, 360f)
        val whole = HudhudMotion.flightMillis(5000f, 360f)
        assertTrue(short < long)
        assertEquals(1400L, whole)
        assertTrue(short in 700L..1400L)
        assertEquals(700L, HudhudMotion.flightMillis(0f, 360f))
        assertEquals(700L, HudhudMotion.flightMillis(100f, 0f))
    }

    @Test fun `pick stays in range`() {
        val random = Random(1)
        repeat(100) { assertTrue(HudhudMotion.pick(5, random) in 0..4) }
        assertEquals(0, HudhudMotion.pick(0, random))
    }
}
