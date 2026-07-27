package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How far a photograph may be shrunk while it is being read.
 *
 * The arithmetic is worth pinning because getting it wrong is invisible in
 * both directions: too timid and the app still decodes fifty megapixels into
 * memory and dies on a mid-range phone, too eager and the saved photo is
 * quietly softer than it used to be and nobody notices until a bill cannot be
 * read back.
 */
class PhotoDecodeTest {

    private fun sample(w: Int, h: Int, edge: Int, shortEdge: Boolean = false) =
        PhotoDecode.sampleSize(w, h, edge, shortEdge)

    /** A real camera frame against a real bill target. */
    @Test
    fun `a large photo is sampled down`() {
        // 8160×6120 is a 50 MP phone camera. Fitting the long edge to 1280.
        assertEquals(4, sample(8160, 6120, 1280))
        // 4000×3000, a 12 MP frame.
        assertEquals(2, sample(4000, 3000, 1280))
    }

    /** Never below what the caller is about to scale to. */
    @Test
    fun `sampling never goes under the size asked for`() {
        for (w in listOf(500, 1000, 1300, 2560, 4000, 8160)) {
            val s = sample(w, (w * 3) / 4, 1280)
            assertTrue("$w sampled to ${w / s}, under 1280", w / s >= 1280 || w < 1280)
        }
    }

    /** A picture already small enough is read as it is. */
    @Test
    fun `a small photo is left alone`() {
        assertEquals(1, sample(1000, 800, 1280))
        assertEquals(1, sample(400, 400, 400, shortEdge = true))
    }

    /**
     * A customer photo is cropped square afterwards, so it is the SHORT side
     * that has to survive. Measuring the long side would let a wide frame be
     * halved until the crop had less than four hundred pixels to take.
     */
    @Test
    fun `a square crop is measured by the short side`() {
        // 4000×1000. Measured by the long side the decoder would halve three
        // times and hand back 500×125 — the square crop then has 125 pixels
        // where it needed 400. Measured by the short side it halves once.
        assertEquals(2, sample(4000, 1000, 400, shortEdge = true))
        assertEquals(8, sample(4000, 1000, 400, shortEdge = false))
    }

    /** Always a power of two — Android silently rounds anything else down. */
    @Test
    fun `the answer is always a power of two`() {
        for (w in 300..9000 step 137) {
            val s = sample(w, w, 1280)
            assertEquals("$w gave $s", 0, s and (s - 1))
            assertTrue(s >= 1)
        }
    }

    /** Nonsense in, nothing done. */
    @Test
    fun `a zero target changes nothing`() {
        assertEquals(1, sample(4000, 3000, 0))
    }
}
