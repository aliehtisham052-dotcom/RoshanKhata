package com.innovation313.roshankhata.data

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hudhud's bird calls are numbers (14 Nov 2026): short, quiet, clean at both ends (no click on any phone) and at the
 * pitch each is meant to be — the hoopoe's low "hoo-po-po", the bright "ready" and "glad".
 */
class HudhudChirpTest {

    private val rate = HudhudChirp.SAMPLE_RATE

    /** The energy of [pcm] from [from] to [to] (in samples) at [hz], by the Goertzel step. */
    private fun energyAt(pcm: ShortArray, from: Int, to: Int, hz: Double): Double {
        val k = 2.0 * cos(2.0 * PI * hz / rate)
        var s1 = 0.0
        var s2 = 0.0
        for (i in from until to) {
            val s0 = pcm[i] + k * s1 - s2
            s2 = s1
            s1 = s0
        }
        return s1 * s1 + s2 * s2 - k * s1 * s2
    }

    @Test fun `each call is short and the length is what it says`() {
        for (kind in HudhudChirp.Kind.values()) {
            val pcm = HudhudChirp.samples(kind)
            assertEquals(kind.name, HudhudChirp.durationMillis(kind) * rate / 1000, pcm.size)
            assertTrue("$kind is ${HudhudChirp.durationMillis(kind)} ms", HudhudChirp.durationMillis(kind) in 150..500)
        }
    }

    @Test fun `each call is quiet but not silent`() {
        for (kind in HudhudChirp.Kind.values()) {
            val pcm = HudhudChirp.samples(kind)
            val peak = HudhudChirp.peakOf(pcm)
            assertTrue("$kind peaks at $peak", peak in 6_000..HudhudChirp.PEAK)
            val rms = sqrt(pcm.sumOf { it.toDouble() * it } / pcm.size)
            assertTrue("$kind rms $rms", rms > 1_500)
        }
    }

    @Test fun `no call starts or ends with a click`() {
        for (kind in HudhudChirp.Kind.values()) {
            val pcm = HudhudChirp.samples(kind)
            val ms = rate / 1000 // the first and the last millisecond
            val first = pcm.take(ms).maxOf { abs(it.toInt()) }
            val last = pcm.takeLast(ms).maxOf { abs(it.toInt()) }
            // The first sample is exactly nought and the first millisecond has only just begun to rise;
            // the sound ends in a short quiet, so the player lets go of silence, never of a wave.
            assertEquals("$kind first sample", 0, pcm.first().toInt())
            assertTrue("$kind starts at $first", first < HudhudChirp.PEAK / 6)
            assertEquals("$kind ends in quiet ($last)", 0, last)
        }
    }

    @Test fun `a note rises and falls smoothly, the gap between two notes is silent`() {
        val pcm = HudhudChirp.samples(HudhudChirp.Kind.HOOP)
        // The first note is 110 ms and the gap after it 55 ms: its last and the gap's middle are quiet.
        val noteEnd = 110 * rate / 1000
        val gapMiddle = noteEnd + 27 * rate / 1000
        assertTrue(abs(pcm[noteEnd - 1].toInt()) < 200)
        assertEquals(0, pcm[gapMiddle].toInt())
    }

    @Test fun `the hoopoe's call is low and the others are bright`() {
        val hoop = HudhudChirp.samples(HudhudChirp.Kind.HOOP)
        // The first note (about 0-110 ms) sits near 450 Hz and has next to nothing at 2 kHz.
        val n = 110 * rate / 1000
        assertTrue(energyAt(hoop, 0, n, 450.0) > 20 * energyAt(hoop, 0, n, 2_000.0))
        val glad = HudhudChirp.samples(HudhudChirp.Kind.GLAD)
        val g = 55 * rate / 1000
        assertTrue(energyAt(glad, 0, g, 2_500.0) > 20 * energyAt(glad, 0, g, 450.0))
    }

    @Test fun `a call is the same every time on every phone`() {
        for (kind in HudhudChirp.Kind.values()) {
            assertArrayEquals(HudhudChirp.samples(kind), HudhudChirp.samples(kind))
        }
    }
}
