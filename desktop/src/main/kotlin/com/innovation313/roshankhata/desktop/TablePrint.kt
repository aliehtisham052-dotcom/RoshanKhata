package com.innovation313.roshankhata.desktop

import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.print.PageFormat
import java.awt.print.Printable

/**
 * A plain A4 table for printing (a price list, any list): the shop in the
 * band, a heading, the columns' names, then the rows, [rowsPerPage] to a page.
 * The first column is text from the start side; the others are figures,
 * ending at their column's end. Mirrored for a right-to-left language.
 * Figure columns are as wide as their widest figure (60 to 95 points), so
 * the names keep the rest (25 Oct 2026, the audit: with five figure columns
 * the dues report's names had 56 points — "Muhammad…").
 */
class TablePrint(
    private val rtl: Boolean,
    private val shopName: String,
    private val heading: String,
    private val headers: List<String>,
    private val rows: List<List<String>>,
    /** A last row in bold under a rule (the dues report's totals), on the last page; null for none. */
    private val totals: List<String>? = null,
    private val pageWord: (page: Int, of: Int) -> String
) : Printable {

    private val pageW = 595.0
    private val margin = 36.0
    private val rowsPerPage = 32
    private val navy = Color(0x1B, 0x2A, 0x4A)
    private val line = Color(0xE4, 0xE2, 0xDC)

    private fun pages() = maxOf(1, (rows.size + rowsPerPage - 1) / rowsPerPage)

    private fun Graphics2D.text(raw: String, x: Double, y: Double, end: Boolean = false) {
        // The language's own digits when the owner chose them for printed sheets (21 Oct 2026).
        val s = com.innovation313.roshankhata.data.Digits.forPrint(raw)
        val w = PrintFonts.width(this, s)
        val left = when {
            !rtl && !end -> x
            !rtl && end -> x - w
            rtl && !end -> pageW - x - w
            else -> pageW - x
        }
        PrintFonts.draw(this, s, left.toFloat(), y.toFloat())
    }

    private fun Graphics2D.fit(s: String, width: Double): String {
        if (PrintFonts.width(this, s) <= width) return s
        var t = s
        while (t.isNotEmpty() && PrintFonts.width(this, "$t\u2026") > width) t = t.dropLast(1)
        return "$t\u2026"
    }

    override fun print(graphics: Graphics, format: PageFormat, pageIndex: Int): Int {
        if (pageIndex >= pages()) return Printable.NO_SUCH_PAGE
        val g = graphics as Graphics2D
        g.translate(format.imageableX, format.imageableY)
        val scale = format.imageableWidth / pageW
        g.scale(scale, scale)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        g.color = navy
        g.fillRect(0, 0, pageW.toInt(), 70)
        g.color = Color.WHITE
        g.font = Font(PrintFonts.family, Font.BOLD, 18)
        g.text(shopName, margin, 30.0)
        g.font = Font(PrintFonts.family, Font.PLAIN, 11)
        g.text(heading, margin, 52.0)

        var y = 92.0
        if (pages() > 1) {
            g.color = Color.GRAY
            g.font = Font(PrintFonts.family, Font.PLAIN, 9)
            g.text(pageWord(pageIndex + 1, pages()), pageW - margin, y, end = true)
        }
        y += 18
        // Figure columns as wide as their widest figure or name, ending at the far margin (measured once).
        val widths = figureWidths ?: (1 until headers.size).map { widthOf(g, it) }.also { figureWidths = it }
        val ends = columnEnds(widths)
        g.color = Color.BLACK
        g.font = Font(PrintFonts.family, Font.BOLD, 10)
        headers.forEachIndexed { i, h -> if (i == 0) g.text(h, margin, y) else g.text(h, ends[i - 1], y, end = true) }
        y += 6
        g.color = line
        g.drawLine(margin.toInt(), y.toInt(), (pageW - margin).toInt(), y.toInt())
        y += 16
        // The first column stops short of the first figure column, cut with an ellipsis.
        val firstWidth = (ends.firstOrNull()?.let { it - widths.first() + 8.0 } ?: (pageW - margin)) - margin
        val from = pageIndex * rowsPerPage
        for (r in rows.subList(minOf(from, rows.size), minOf(rows.size, from + rowsPerPage))) {
            g.color = Color.BLACK
            r.forEachIndexed { i, cell ->
                g.font = Font(PrintFonts.family, if (i == 0) Font.PLAIN else Font.BOLD, 10)
                if (i == 0) g.text(g.fit(cell, firstWidth), margin, y) else g.text(cell, ends[i - 1], y, end = true)
            }
            y += 7
            g.color = line
            g.drawLine(margin.toInt(), y.toInt(), (pageW - margin).toInt(), y.toInt())
            y += 15
        }
        // The totals under a darker rule, once, on the last page.
        val sums = totals
        if (sums != null && pageIndex == pages() - 1) {
            g.color = Color.BLACK
            g.drawLine(margin.toInt(), (y - 15).toInt(), (pageW - margin).toInt(), (y - 15).toInt())
            sums.forEachIndexed { i, cell ->
                g.font = Font(PrintFonts.family, Font.BOLD, 10)
                if (i == 0) g.text(g.fit(cell, firstWidth), margin, y) else if (i - 1 < ends.size) g.text(cell, ends[i - 1], y, end = true)
            }
        }
        return Printable.PAGE_EXISTS
    }

    /** Column [i]'s width (a figure column): its widest header or cell in bold 10 pt, plus a gap, 60 to 95 points. */
    private fun widthOf(g: Graphics2D, i: Int): Double {
        val keep = g.font
        g.font = Font(PrintFonts.family, Font.BOLD, 10)
        val widest = (listOfNotNull(headers.getOrNull(i), totals?.getOrNull(i)) + rows.mapNotNull { it.getOrNull(i) })
            .maxOfOrNull { PrintFonts.width(g, com.innovation313.roshankhata.data.Digits.forPrint(it)).toDouble() } ?: 0.0
        g.font = keep
        return (widest + 14.0).coerceIn(60.0, 95.0)
    }

    /** The names' column width on this sheet, as [print] lays it out (the tests read it). */
    internal fun nameColumnWidth(g: Graphics2D): Double {
        val widths = (1 until headers.size).map { widthOf(g, it) }
        val ends = columnEnds(widths)
        return (ends.firstOrNull()?.let { it - widths.first() + 8.0 } ?: (pageW - margin)) - margin
    }

    /** The figure columns' widths, measured on the first page drawn and kept for the rest. */
    private var figureWidths: List<Double>? = null

    /** Where each figure column ends, from the far margin back ([widths]: columns 1 to the last). */
    private fun columnEnds(widths: List<Double>): List<Double> {
        val ends = DoubleArray(widths.size)
        var end = pageW - margin
        for (k in widths.indices.reversed()) {
            ends[k] = end
            end -= widths[k]
        }
        return ends.toList()
    }
}
