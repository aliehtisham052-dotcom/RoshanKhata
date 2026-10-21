package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.EntryItem
import com.innovation313.roshankhata.data.EntryWithItems
import com.innovation313.roshankhata.data.LedgerEntry
import com.innovation313.roshankhata.words.desktopWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.awt.print.PageFormat
import java.awt.print.Paper
import java.awt.print.Printable
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO

/**
 * The statement drawn the way a printer draws it, into a picture, in English
 * and Urdu. The pictures are kept (build/statement-proof) and desktop.yml
 * puts them on the desktop-test pre-release: a statement is checked by
 * looking at it (CLAUDE.md lesson 16), not only by counting pages.
 */
class StatementPrintTest {

    private fun entries(n: Int): List<EntryWithItems> = (1..n).map { i ->
        val e = LedgerEntry(
            id = i.toLong(), partyId = 7, amount = 1000.0 + i * 37, isGiven = i % 3 != 0,
            note = if (i % 4 == 0) "cash" else null, entryNumber = "RK-%06d".format(i),
            timestamp = 1_760_000_000_000L + i * 86_400_000L
        )
        val items = if (i % 2 == 0) listOf(EntryItem(id = i.toLong(), entryId = i.toLong(), itemName = "Urea", quantity = 2.0, unit = "bag")) else emptyList()
        EntryWithItems(e, items)
    }

    private fun format() = PageFormat().apply {
        paper = Paper().apply { setSize(595.0, 842.0); setImageableArea(0.0, 0.0, 595.0, 842.0) }
    }

    private fun render(tag: String, rows: List<EntryWithItems>): List<BufferedImage> {
        val previous = Locale.getDefault()
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        try {
            val s = StatementPrint(desktopWords(locale), tag == "ur", "Al-Noor Seeds", "Ali Raza", "0300 1234567", rows)
            val pages = ArrayList<BufferedImage>()
            var i = 0
            while (true) {
                val img = BufferedImage(1190, 1684, BufferedImage.TYPE_INT_RGB)
                val g = img.createGraphics()
                g.color = java.awt.Color.WHITE
                g.fillRect(0, 0, img.width, img.height)
                g.scale(2.0, 2.0)
                val r = s.print(g, format(), i)
                g.dispose()
                if (r == Printable.NO_SUCH_PAGE) break
                pages += img
                i++
            }
            return pages
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `a statement prints every entry over the right number of pages`() {
        assertEquals(1, render("en", entries(5)).size)
        assertEquals(1, render("en", emptyList()).size)
        val long = entries(61)
        val s = StatementPrint(desktopWords(Locale.ENGLISH), false, "Al-Noor Seeds", "Ali Raza", null, long)
        assertEquals(s.pageCount(), render("en", long).size)
    }

    /**
     * 21 Oct 2026: the page counts were fixed numbers (20 rows, then 26) that
     * did not fit the paper, and entries were drawn below it — a 60-entry
     * statement lost seven. Here every row is on a page exactly once, every
     * page's rows end above the bottom margin, and the closing box fits.
     */
    @Test
    fun `every entry is on a page and nothing runs off the paper`() {
        val bottom = StatementPrint.PAGE_H - 36.0
        for (period in listOf<Long?>(null, 1_759_000_000_000L)) for (n in listOf(0, 1, 15, 16, 17, 18, 35, 36, 37, 60, 61, 200)) {
            val s = StatementPrint(desktopWords(Locale.ENGLISH), false, "Al-Noor Seeds", "Ali Raza", null, entries(n), periodFrom = period)
            val pages = s.pages
            assertEquals((0 until n).toList(), pages.flatMap { it.toList() })
            pages.forEachIndexed { i, r ->
                if (!r.isEmpty()) {
                    val lastBottom = s.firstRowY(i == 0) + (r.count() - 1) * StatementPrint.ROW_H + StatementPrint.ROW_BOTTOM
                    assertTrue("n=$n page ${i + 1} runs off the paper ($lastBottom)", lastBottom <= bottom)
                }
            }
            val closingBottom = s.firstRowY(pages.size == 1) + pages.last().count() * StatementPrint.ROW_H + StatementPrint.CLOSING_H
            assertTrue("n=$n the closing box runs off the paper ($closingBottom)", closingBottom <= bottom)
            assertEquals(pages.size, s.pageCount())
        }
    }

    @Test
    fun `the proof pictures, English and Urdu`() {
        val dir = File("build/statement-proof").apply { mkdirs() }
        for (tag in listOf("en", "ur")) {
            val pages = render(tag, entries(24))
            pages.forEachIndexed { i, img -> ImageIO.write(img, "png", File(dir, "statement-$tag-${i + 1}.png")) }
            // Ink on the page, beyond the band: the rows were drawn.
            val img = pages.first()
            var ink = 0
            for (y in 400 until 1200 step 4) for (x in 0 until img.width step 4) if (img.getRGB(x, y) and 0xFFFFFF != 0xFFFFFF) ink++
            assertTrue("$tag page 1 looks empty ($ink inked points)", ink > 500)
        }
    }
}
