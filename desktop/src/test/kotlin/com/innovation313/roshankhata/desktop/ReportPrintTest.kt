package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.EntryWithParty
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
 * The Khata Report drawn as a printer draws it, in English and Urdu — the phone's document (Marhala 4,
 * part 7). The pictures go beside the statement's (build/statement-proof) onto the desktop-test
 * pre-release, to be looked at (CLAUDE.md lesson 16).
 */
class ReportPrintTest {

    private fun entries(n: Int) = (1..n).map { i ->
        EntryWithParty(
            id = i.toLong(), partyId = (i % 5).toLong(), partyName = if (i % 2 == 0) "Ali Raza" else "بلال ٹریڈرز",
            amount = 500.0 + i * 41, isGiven = i % 3 != 0, note = if (i % 4 == 0) "urea 2 bags" else null,
            entryNumber = "RK-%06d".format(i), timestamp = 1_760_000_000_000L + i * 3_600_000L
        )
    }

    private fun format() = PageFormat().apply {
        paper = Paper().apply { setSize(595.0, 842.0); setImageableArea(0.0, 0.0, 595.0, 842.0) }
    }

    private fun render(tag: String, rows: List<EntryWithParty>): List<BufferedImage> {
        val previous = Locale.getDefault()
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        try {
            val r = ReportPrint(desktopWords(locale), tag == "ur", "Bhatti Traders", "All", rows)
            val pages = ArrayList<BufferedImage>()
            var i = 0
            while (true) {
                val img = BufferedImage(1190, 1684, BufferedImage.TYPE_INT_RGB)
                val g = img.createGraphics()
                g.color = java.awt.Color.WHITE
                g.fillRect(0, 0, img.width, img.height)
                g.scale(2.0, 2.0)
                val result = r.print(g, format(), i)
                g.dispose()
                if (result == Printable.NO_SUCH_PAGE) break
                pages += img
                i++
            }
            return pages
        } finally {
            Locale.setDefault(previous)
        }
    }

    private fun plain(n: Int) = entries(n).map { it.copy(note = null) }

    @Test
    fun `the phone's page breaks - 24 rows under the summary, 32 on each page after`() {
        // Marhala 4, part 7: the phone's LedgerReport layout — band, the not-a-backup note, the summary,
        // then 20-point rows down to 60 points above the foot.
        assertEquals("an empty report is one page", 1, render("en", emptyList()).size)
        assertEquals(1, render("en", plain(24)).size)
        assertEquals(2, render("en", plain(25)).size)
        assertEquals(2, render("en", plain(56)).size)
        assertEquals(3, render("en", plain(57)).size)
    }

    @Test
    fun `the preview counts the pages the printer prints`() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        try {
            val r = ReportPrint(desktopWords(Locale.ENGLISH), false, "Bhatti Traders", "All", entries(90))
            assertEquals(render("en", entries(90)).size, r.pageCount())
            assertEquals(r.pageCount(), Sheets.of(r).count)
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `the proof pictures, English and Urdu`() {
        val dir = File("build/statement-proof").apply { mkdirs() }
        for (tag in listOf("en", "ur")) {
            val pages = render(tag, entries(34))
            pages.forEachIndexed { i, img -> ImageIO.write(img, "png", File(dir, "report-$tag-${i + 1}.png")) }
            val img = pages.first()
            var ink = 0
            for (y in 300 until 1500 step 4) for (x in 0 until img.width step 4) if (img.getRGB(x, y) and 0xFFFFFF != 0xFFFFFF) ink++
            assertTrue("$tag report page 1 looks empty ($ink inked points)", ink > 500)
        }
    }
}
