package com.innovation313.roshankhata.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.awt.print.PageFormat
import java.awt.print.Printable

/**
 * The cashbook's day report on paper (capability matrix, plan B8, 24 Nov 2026): the sheet of label-and-figure lines
 * that Close the day and the day report print. It has to run on as many pages as the lines need (it once stopped at the
 * foot of the first), keep its heading on the first page only and mirror for a right-to-left language (the label on the
 * reading side, the figure at the far end). Made-up lines. NOT RUN where it was written; CI is its first run.
 */
class LinesPrintTest {

    private fun lines(n: Int) = (1..n).map { "Line $it" to "Rs ${it * 100}" }

    private fun page(sheet: Printable, index: Int): Pair<Int, BufferedImage> {
        val image = BufferedImage(595, 842, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, image.width, image.height)
        try {
            return sheet.print(g, PageFormat(), index) to image
        } finally {
            g.dispose()
        }
    }

    private fun ink(image: BufferedImage, x0: Int, x1: Int, y0: Int, y1: Int): Int {
        var n = 0
        for (y in y0 until y1) for (x in x0 until x1) if ((image.getRGB(x, y) and 0xFFFFFF) != 0xFFFFFF) n++
        return n
    }

    @Test
    fun `a short report is one page and a long one runs on as many pages as its lines need`() {
        val one = LinesPrint("Day report", "Al-Noor Agri Store", lines(10), rtl = false)
        assertEquals(Printable.PAGE_EXISTS, page(one, 0).first)
        assertEquals(Printable.NO_SUCH_PAGE, page(one, 1).first)
        // 33 lines fill the first page and 36 each next one: 120 lines are 4 pages.
        val long = LinesPrint("Day report", "Al-Noor Agri Store", lines(120), rtl = false)
        for (i in 0..3) assertEquals("page $i", Printable.PAGE_EXISTS, page(long, i).first)
        assertEquals(Printable.NO_SUCH_PAGE, page(long, 4).first)
        // The later pages carry lines, not a blank sheet.
        assertTrue("page 3 is blank", ink(page(long, 3).second, 0, 595, 70, 800) > 200)
    }

    @Test
    fun `the heading is on the first page only`() {
        val sheet = LinesPrint("Day report", "Al-Noor Agri Store", lines(80), rtl = false)
        assertTrue("no heading on page 0", ink(page(sheet, 0).second, 0, 595, 60, 110) > 200)
        // Page 1's lines start at y 80, so its first line is there, but the 20 pt title sits above them on page 0 only:
        // the strip above the first line (y 60-76) is empty on page 1.
        assertEquals("a heading on page 1", 0, ink(page(sheet, 1).second, 0, 595, 40, 68))
    }

    @Test
    fun `a right-to-left report puts the label on the right and the figure on the left`() {
        val label = "Cash in the drawer now"
        val ltr = page(LinesPrint("Day report", "Al-Noor Agri Store", listOf(label to "1"), rtl = false), 0).second
        val rtl = page(LinesPrint("Day report", "Al-Noor Agri Store", listOf(label to "1"), rtl = true), 0).second
        // The first line stands at y 134; its band is a little above and below that.
        val ltrLeft = ink(ltr, 0, 297, 120, 145)
        val ltrRight = ink(ltr, 297, 595, 120, 145)
        val rtlLeft = ink(rtl, 0, 297, 120, 145)
        val rtlRight = ink(rtl, 297, 595, 120, 145)
        assertTrue("left to right: the long label is on the left ($ltrLeft against $ltrRight)", ltrLeft > ltrRight)
        assertTrue("right to left: the long label is on the right ($rtlLeft against $rtlRight)", rtlRight > rtlLeft)
    }
}
