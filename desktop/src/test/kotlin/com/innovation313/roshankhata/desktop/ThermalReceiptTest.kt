package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.EscPos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The receipt picture the thermal printer gets: 58 mm wide, ink on it, long names wrapped, ESC/POS framing. */
class ThermalReceiptTest {

    private val lines = listOf(
        ThermalReceipt.Line("Shop", 26f, bold = true, centre = true),
        ThermalReceipt.Line(""),
        ThermalReceipt.Line("علی احمد", 24f, bold = true),
        ThermalReceipt.Line("Rs 1,200", 34f, bold = true, centre = true)
    )

    @Test
    fun `the picture is one roll wide with ink on it`() {
        val img = ThermalReceipt.image(lines, rtl = true)
        assertEquals(EscPos.WIDTH_58MM, img.width)
        var dark = 0
        for (y in 0 until img.height) for (x in 0 until img.width) if ((img.getRGB(x, y) and 0xFF) < 128) dark++
        assertTrue("some ink", dark > 200)
        assertTrue("mostly paper", dark < img.width * img.height / 3)
    }

    @Test
    fun `a long note wraps instead of running off the paper`() {
        val short = ThermalReceipt.image(listOf(ThermalReceipt.Line("Note")), rtl = false).height
        val long = ThermalReceipt.image(listOf(ThermalReceipt.Line("Note ".repeat(40))), rtl = false).height
        assertTrue(long > short * 3)
    }

    @Test
    fun `the bytes start by resetting the printer and end with a cut`() {
        val b = ThermalReceipt.bytes(lines, rtl = false)
        assertEquals(0x1B.toByte(), b[0]); assertEquals(0x40.toByte(), b[1])
        assertEquals(0x56.toByte(), b[b.size - 3])
    }

    @Test
    fun `an 80 mm roll is wider, the logo sits on top, and a cash sale opens the drawer first`() {
        val wide = ThermalReceipt.Settings(wide = true, logo = true, drawer = true)
        val logo = java.awt.image.BufferedImage(400, 200, java.awt.image.BufferedImage.TYPE_INT_ARGB).apply {
            val g = createGraphics(); g.color = java.awt.Color(0x1A, 0x7F, 0x50); g.fillOval(40, 20, 320, 160); g.dispose()
        }
        val plain = ThermalReceipt.image(lines, rtl = false, width = wide.width)
        val topped = ThermalReceipt.image(lines, rtl = false, width = wide.width, logo = logo)
        assertEquals(EscPos.WIDTH_80MM, topped.width)
        assertTrue("the logo adds its height", topped.height > plain.height + 60)
        // The green logo prints as dots, not as nothing.
        var ink = 0
        for (y in 8 until 8 + 60) for (x in 0 until topped.width) if ((topped.getRGB(x, y) and 0xFF) < 128) ink++
        assertTrue("logo ink: $ink", ink > 1000)
        val cash = ThermalReceipt.bytes(lines, rtl = false, settings = wide, logo = logo, cash = true)
        assertEquals(0x1B.toByte(), cash[0]); assertEquals(0x70.toByte(), cash[1])
        val card = ThermalReceipt.bytes(lines, rtl = false, settings = wide, logo = logo, cash = false)
        assertEquals(0x40.toByte(), card[1])
        // The drawer setting off: no pulse even for cash.
        assertEquals(0x40.toByte(), ThermalReceipt.bytes(lines, rtl = false, settings = wide.copy(drawer = false), cash = true)[1])
    }
}
