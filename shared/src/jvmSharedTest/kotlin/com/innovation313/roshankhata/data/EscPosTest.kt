package com.innovation313.roshankhata.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Every byte the printer receives, for pictures small enough to check by hand. */
class EscPosTest {

    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()

    private fun u(vararg b: Int) = b.map { it.toByte() }.toByteArray()

    @Test
    fun `one black dot then seven white makes 0x80, framed by init, raster header, feed and cut`() {
        val px = IntArray(8) { if (it == 0) black else white }
        val out = EscPos.raster(px, 8, 1)
        assertArrayEquals(
            u(0x1B, 0x40, 0x1D, 0x76, 0x30, 0x00, 1, 0, 1, 0, 0x80, 0x1B, 0x64, 0x04, 0x1D, 0x56, 0x42, 0x00),
            out
        )
    }

    @Test
    fun `a width that is not a multiple of eight pads the last byte with paper`() {
        val px = IntArray(10) { black }
        val out = EscPos.raster(px, 10, 1)
        // header says 2 bytes a row; the dark-majority picture is inverted, so black reads as paper
        assertEquals(2, out[6].toInt())
        assertEquals(0x00, out[10].toInt() and 0xFF)
        assertEquals(0x00, out[11].toInt() and 0xFF)
    }

    @Test
    fun `a mostly white picture prints its dark dots as they are`() {
        val px = IntArray(16) { if (it < 3) black else white }
        val out = EscPos.raster(px, 16, 1)
        assertEquals(0xE0, out[10].toInt() and 0xFF)
        assertEquals(0x00, out[11].toInt() and 0xFF)
    }

    @Test
    fun `a dark-theme card is inverted so the text prints black on white`() {
        // dark ground, three light dots of "text"
        val px = IntArray(16) { if (it < 3) white else black }
        val out = EscPos.raster(px, 16, 1)
        assertEquals(0xE0, out[10].toInt() and 0xFF)
        assertEquals(0x00, out[11].toInt() and 0xFF)
    }

    @Test
    fun `transparent prints as paper`() {
        val px = IntArray(8) { if (it == 0) black else 0x00000000 }
        val out = EscPos.raster(px, 8, 1)
        assertEquals(0x80, out[10].toInt() and 0xFF)
    }

    @Test
    fun `a tall picture goes in bands of 256 rows`() {
        val px = IntArray(8 * 300) { white }
        val out = EscPos.raster(px, 8, 300)
        // first band header: 256 rows = 0x00, 0x01
        assertEquals(0x00, out[8].toInt() and 0xFF)
        assertEquals(0x01, out[9].toInt() and 0xFF)
        // second band header right after 256 data bytes: 44 rows
        val second = 2 + 8 + 256
        assertArrayEquals(u(0x1D, 0x76, 0x30, 0x00, 1, 0, 44, 0), out.copyOfRange(second, second + 8))
        assertEquals(2 + 8 + 256 + 8 + 44 + 3 + 4, out.size)
    }
}
