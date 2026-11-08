package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.Product
import com.innovation313.roshankhata.data.ProductName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Font
import java.awt.image.BufferedImage
import java.awt.print.PageFormat
import java.awt.print.Paper
import java.awt.print.Printable

/** Barcode labels: names in any script drawn with the face that has them (PrintFonts), cut to the label whole. */
class LabelSheetTest {

    private fun product(name: String, code: String? = "5012345678900") =
        Product(name = name, nameKey = ProductName.key(name), normalisedName = ProductName.normalised(name), salePrice = 1250.0, barcode = code)

    private fun format() = PageFormat().apply {
        paper = Paper().apply { setSize(595.0, 842.0); setImageableArea(0.0, 0.0, 595.0, 842.0) }
    }

    @Test
    fun `labels in several scripts print a page each forty-four`() {
        val names = listOf("Urea 50kg", "یوریا کھاد", "यूरिया खाद", "ইউরিয়া সার", "اليوريا", "Pupuk Urea", "A very long product name that cannot fit on one label")
        val sheet = LabelSheet(names.map { product(it) } + List(38) { product("DAP $it", code = "ITEM-$it") })
        assertEquals(2, sheet.pageCount())
        for (page in 0 until 2) {
            val img = BufferedImage(1190, 1684, BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            g.scale(2.0, 2.0)
            assertEquals(Printable.PAGE_EXISTS, sheet.print(g, format(), page))
            g.dispose()
        }
        val g = BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB).createGraphics()
        assertEquals(Printable.NO_SUCH_PAGE, sheet.print(g, format(), 2))
        g.dispose()
    }

    @Test
    fun `a long name is cut whole characters at a time and fits`() {
        val g = BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB).createGraphics()
        g.font = Font(PrintFonts.family, Font.BOLD, 8)
        val sheet = LabelSheet(emptyList())
        val room = 120.0
        assertEquals("Urea", sheet.fitted(g, "Urea", room))
        for (name in listOf("A very long product name that cannot fit on one label at all", "یوریا کھاد پچاس کلو کی بوری، اعلیٰ معیار", "यूरिया खाद पचास किलो की बोरी उच्च गुणवत्ता")) {
            // The room is half the name's own width: how wide a script draws depends on the fonts the machine has
            // (on the Windows runner the Urdu line fitted 120 pt whole), so the cut is tested against the name itself.
            val half = PrintFonts.width(g, name) / 2
            val cut = sheet.fitted(g, name, half)
            assertTrue(cut, cut.endsWith("…"))
            assertTrue(cut, PrintFonts.width(g, cut) <= half)
            assertTrue(cut, name.startsWith(cut.removeSuffix("…").trimEnd()))
        }
        // A character outside the basic plane (two chars in Kotlin) is never split in half.
        val emoji = "Seeds " + "🌱".repeat(40)
        val cut = sheet.fitted(g, emoji, 60.0)
        val kept = cut.removeSuffix("…").trimEnd()
        assertTrue(cut, kept.isNotEmpty() && !Character.isHighSurrogate(kept.last()))
        g.dispose()
    }

    @Test
    fun `each bar is one run of the code's own modules`() {
        // Marhala 4, the audit: columns cut to whole points made bars of one width print one and two points wide.
        // Each bar is now one rectangle over a run of the matrix's dark modules; an EAN-13 has thirty of them.
        val m = com.google.zxing.oned.EAN13Writer().encode("5012345678900", com.google.zxing.BarcodeFormat.EAN_13, 0, 1)
        val runs = LabelSheet(emptyList()).barRuns(m)
        assertEquals(30, runs.size)
        assertEquals((0 until m.width).count { m[it, 0] }, runs.sumOf { it.second - it.first })
        assertTrue(runs.all { (a, b) -> b > a && (a == 0 || !m[a - 1, 0]) && (b == m.width || !m[b, 0]) })
    }
}
