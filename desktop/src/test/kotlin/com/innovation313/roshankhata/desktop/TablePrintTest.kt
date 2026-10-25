package com.innovation313.roshankhata.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.awt.print.PageFormat
import java.awt.print.Printable

/**
 * The dues report's PDF gave the names 56 points with five figure columns of
 * 95 each — "Muhammad…" — and had no total line (25 Oct 2026, the audit).
 * Made-up names and figures.
 */
class TablePrintTest {

    private val head = listOf("Name", "0-30", "31-60", "61-90", "90+", "Total")
    private val rows = listOf(
        listOf("Muhammad Aslam Kareem", "Rs 12,500", "–", "Rs 3,000", "–", "Rs 15,500"),
        listOf("Ghulam Rasool", "–", "Rs 2,400", "–", "Rs 800", "Rs 3,200")
    )

    @Test
    fun `figure columns take what their figures need and the names keep the rest`() {
        val g = BufferedImage(600, 850, BufferedImage.TYPE_INT_RGB).createGraphics()
        try {
            val sheet = TablePrint(false, "Al-Noor Agri Store", "Dues by age", head, rows) { p, of -> "$p / $of" }
            assertTrue("was 56 points", sheet.nameColumnWidth(g) > 150.0)
        } finally {
            g.dispose()
        }
    }

    @Test
    fun `a sheet with totals prints them on its last page`() {
        val g = BufferedImage(600, 850, BufferedImage.TYPE_INT_RGB).createGraphics()
        try {
            val sheet = TablePrint(true, "Al-Noor Agri Store", "Dues by age", head, rows,
                totals = listOf("Total", "Rs 12,500", "Rs 2,400", "Rs 3,000", "Rs 800", "Rs 18,700")) { p, of -> "$p / $of" }
            assertEquals(Printable.PAGE_EXISTS, sheet.print(g, PageFormat(), 0))
            assertEquals(Printable.NO_SUCH_PAGE, sheet.print(g, PageFormat(), 1))
        } finally {
            g.dispose()
        }
    }
}
