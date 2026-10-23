package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.EntryWithParty
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.WP
import com.innovation313.roshankhata.words.Words
import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.print.PageFormat
import java.awt.print.Printable

/**
 * The whole-ledger report on paper (KMP Phase 7e): every entry across every
 * customer in the chosen period, oldest first, with what was given and what
 * was received, and the two totals at the end. Printed through Windows' own
 * dialog — a printer, or a PDF with "Microsoft Print to PDF". Columns mirror
 * for a right-to-left language, as [StatementPrint] does.
 */
class ReportPrint(
    private val words: Words,
    private val rtl: Boolean,
    private val shopName: String,
    private val period: String,
    entries: List<EntryWithParty>
) : Printable {

    private val rows = entries.sortedWith(compareBy({ it.timestamp }, { it.id }))
    private val totalGave = rows.filter { it.isGiven }.sumOf { it.amount }
    private val totalGot = rows.filter { !it.isGiven }.sumOf { it.amount }

    private val pageW = 595.0
    private val margin = 36.0
    private val rowsPerPage = 30

    private val navy = Color(0x1B, 0x2A, 0x4A)
    private val red = Color(0xA3, 0x2D, 0x2D)
    private val green = Color(0x0A, 0x5A, 0x44)
    private val muted = Color(0x6B, 0x6B, 0x66)
    private val line = Color(0xE4, 0xE2, 0xDC)

    fun pageCount(): Int = maxOf(1, (rows.size + rowsPerPage - 1) / rowsPerPage)

    /** Text at [x] from the start side; [end]: [x] is where the text ends. */
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

    /** Cuts [s] to fit [width] points in the current font, with an ellipsis. */
    private fun Graphics2D.fit(s: String, width: Double): String {
        if (PrintFonts.width(this, s) <= width) return s
        var t = s
        while (t.isNotEmpty() && PrintFonts.width(this, "$t…") > width) t = t.dropLast(1)
        return "$t…"
    }

    override fun print(graphics: Graphics, format: PageFormat, pageIndex: Int): Int {
        if (pageIndex >= pageCount()) return Printable.NO_SUCH_PAGE
        val g = graphics as Graphics2D
        g.translate(format.imageableX, format.imageableY)
        val scale = format.imageableWidth / pageW
        g.scale(scale, scale)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        // The band: the shop, the report's name and its period.
        g.color = navy
        g.fillRect(0, 0, pageW.toInt(), 70)
        g.color = Color.WHITE
        g.font = Font(PrintFonts.family, Font.BOLD, 18)
        g.text(shopName, margin, 30.0)
        g.font = Font(PrintFonts.family, Font.PLAIN, 11)
        g.text(words.text(W.ledger_report_title) + "  ·  " + period, margin, 52.0)

        var y = 92.0
        g.color = muted
        g.font = Font(PrintFonts.family, Font.PLAIN, 9)
        g.text(words.plural(WP.pdf_stmt_entries, rows.size, rows.size), margin, y)
        if (pageCount() > 1) g.text(words.text(W.pdf_stmt_page, pageIndex + 1, pageCount()), pageW - margin, y, end = true)
        y += 18

        // Columns from the start side: date, name (and note), gave, got.
        val xDate = margin
        val xName = margin + 92
        val xGaveEnd = pageW - margin - 90
        val xGotEnd = pageW - margin
        g.color = Color.BLACK
        g.font = Font(PrintFonts.family, Font.BOLD, 10)
        g.text(words.text(W.date_label), xDate, y)
        g.text(words.text(W.name), xName, y)
        g.text(words.text(W.i_gave), xGaveEnd, y, end = true)
        g.text(words.text(W.i_got), xGotEnd, y, end = true)
        y += 6
        g.color = line
        g.drawLine(margin.toInt(), y.toInt(), (pageW - margin).toInt(), y.toInt())
        y += 15

        val from = pageIndex * rowsPerPage
        for (e in rows.subList(minOf(from, rows.size), minOf(rows.size, from + rowsPerPage))) {
            g.font = Font(PrintFonts.family, Font.PLAIN, 9)
            g.color = Color.BLACK
            g.text(Format.dateOnly(e.timestamp), xDate, y)
            val who = e.partyName + (e.note?.takeIf { it.isNotBlank() }?.let { "  ·  $it" } ?: "")
            g.text(g.fit(who, xGaveEnd - 80 - xName), xName, y)
            g.color = if (e.isGiven) red else green
            g.font = Font(PrintFonts.family, Font.BOLD, 9)
            g.text(Format.money(e.amount), if (e.isGiven) xGaveEnd else xGotEnd, y, end = true)
            y += 7
            g.color = line
            g.drawLine(margin.toInt(), y.toInt(), (pageW - margin).toInt(), y.toInt())
            y += 15
        }

        // The totals, on the last page.
        if (pageIndex == pageCount() - 1) {
            y += 6
            g.color = navy
            g.fillRect(margin.toInt(), y.toInt(), (pageW - 2 * margin).toInt(), 40)
            g.color = Color.WHITE
            g.font = Font(PrintFonts.family, Font.BOLD, 11)
            g.text(words.text(W.i_gave) + ": " + Format.money(totalGave), margin + 14, y + 25)
            g.text(words.text(W.i_got) + ": " + Format.money(totalGot), pageW - margin - 14, y + 25, end = true)
        }
        return Printable.PAGE_EXISTS
    }
}
