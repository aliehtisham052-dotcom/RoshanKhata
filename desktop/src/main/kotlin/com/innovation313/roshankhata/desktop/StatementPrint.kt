package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.EntryWithItems
import com.innovation313.roshankhata.data.Money
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.WP
import com.innovation313.roshankhata.words.Words
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.print.PageFormat
import java.awt.print.Paper
import java.awt.print.Printable
import java.awt.print.PrinterJob

/**
 * A customer's statement on the computer (KMP Phase 6e), printed through
 * Windows' own print dialog — to the shop's printer, or to a PDF with
 * "Microsoft Print to PDF". The same sheet as the phone's statement PDF:
 * the shop's name in the band, whose statement and the period, what was
 * given and what was paid, every entry with its running balance, and the
 * closing balance in the customer's own terms.
 *
 * Java's text layout shapes Urdu and Arabic; for a right-to-left language
 * the columns are mirrored, as PdfRtl mirrors the phone's PDF.
 */
class StatementPrint(
    private val words: Words,
    private val rtl: Boolean,
    private val shopName: String,
    private val partyName: String,
    private val partyPhone: String?,
    entries: List<EntryWithItems>
) : Printable {

    private class Row(val e: EntryWithItems, val running: Double)

    private val rows: List<Row>
    private val totalGave: Double
    private val totalGot: Double
    private val closing: Double

    init {
        var running = 0.0
        rows = entries.sortedWith(compareBy({ it.entry.timestamp }, { it.entry.id })).map {
            running += if (it.entry.isGiven) it.entry.amount else -it.entry.amount
            Row(it, running)
        }
        totalGave = entries.filter { it.entry.isGiven }.sumOf { it.entry.amount }
        totalGot = entries.filter { !it.entry.isGiven }.sumOf { it.entry.amount }
        closing = running
    }

    // A4 in points, the phone's margins.
    private val pageW = 595.0
    private val margin = 36.0
    private val rowsFirstPage = 20
    private val rowsPerPage = 26

    private val navy = Color(0x1B, 0x2A, 0x4A)
    private val gold = Color(0xC9, 0xA2, 0x4A)
    private val red = Color(0xA3, 0x2D, 0x2D)
    private val green = Color(0x0A, 0x5A, 0x44)
    private val muted = Color(0x6B, 0x6B, 0x66)
    private val line = Color(0xE4, 0xE2, 0xDC)

    private fun pageCount(): Int =
        if (rows.size <= rowsFirstPage) 1 else 1 + (rows.size - rowsFirstPage + rowsPerPage - 1) / rowsPerPage

    /**
     * Text at [x] measured from the start side: from the left in English, from
     * the right in Urdu. [end]: [x] is where the text ends, not where it starts
     * (pageW - margin is the far edge).
     */
    private fun Graphics2D.text(s: String, x: Double, y: Double, end: Boolean = false) {
        val w = fontMetrics.stringWidth(s)
        val left = when {
            !rtl && !end -> x
            !rtl && end -> x - w
            rtl && !end -> pageW - x - w
            else -> pageW - x
        }
        drawString(s, left.toFloat(), y.toFloat())
    }

    override fun print(graphics: Graphics, format: PageFormat, pageIndex: Int): Int {
        if (pageIndex >= pageCount()) return Printable.NO_SUCH_PAGE
        val g = graphics as Graphics2D
        g.translate(format.imageableX, format.imageableY)
        val scale = format.imageableWidth / pageW
        g.scale(scale, scale)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        val first = pageIndex == 0
        // The band: the shop's name, and whose statement this is.
        g.color = navy
        g.fillRect(0, 0, pageW.toInt(), 78)
        g.color = Color.WHITE
        g.font = Font(Font.DIALOG, Font.BOLD, 18)
        g.text(shopName, margin, 32.0)
        g.font = Font(Font.DIALOG, Font.PLAIN, 11)
        val phone = partyPhone?.takeIf { it.isNotBlank() && it != partyName }
        g.text(words.text(W.pdf_stmt_title, partyName) + (phone?.let { "  ·  " + Format.ltr(it) } ?: ""), margin, 54.0)

        var y = 96.0
        if (first) {
            g.color = muted
            g.font = Font(Font.DIALOG, Font.PLAIN, 10)
            val period = if (rows.isNotEmpty()) {
                words.text(
                    W.pdf_stmt_period,
                    Format.dateOnly(rows.first().e.entry.timestamp),
                    Format.dateOnly(rows.last().e.entry.timestamp)
                ) + "  ·  " + words.plural(WP.pdf_stmt_entries, rows.size, rows.size)
            } else words.text(W.pdf_stmt_no_entries)
            g.text(period, margin, y)
            if (pageCount() > 1) g.text(words.text(W.pdf_stmt_page, 1, pageCount()), pageW - margin, y, end = true)
            y += 12.0
            // What was given and what was paid, in the table's own colours.
            val gap = 10.0
            val boxW = (pageW - 2 * margin - gap) / 2
            fun box(index: Int, label: String, value: String, tint: Color, ink: Color) {
                val start = margin + index * (boxW + gap)
                val left = if (rtl) pageW - start - boxW else start
                g.color = tint
                g.fillRoundRect(left.toInt(), y.toInt(), boxW.toInt(), 42, 10, 10)
                g.color = ink
                g.stroke = BasicStroke(1f)
                g.drawRoundRect(left.toInt(), y.toInt(), boxW.toInt(), 42, 10, 10)
                g.font = Font(Font.DIALOG, Font.PLAIN, 9)
                g.text(label, start + 10, y + 15)
                g.font = Font(Font.DIALOG, Font.BOLD, 14)
                g.text(value, start + 10, y + 34)
            }
            box(0, words.text(W.pdf_stmt_you_owe), Format.money(totalGave), Color(0xFD, 0xEC, 0xEA), red)
            box(1, words.text(W.pdf_stmt_you_paid), Format.money(totalGot), Color(0xEA, 0xF7, 0xEF), green)
            y += 60.0
        } else {
            g.color = muted
            g.font = Font(Font.DIALOG, Font.PLAIN, 9)
            g.text(words.text(W.pdf_stmt_page, pageIndex + 1, pageCount()), pageW - margin, y, end = true)
            y += 14.0
        }

        // The table's columns, from the start side.
        val xDate = margin
        val xGave = margin + 230
        val xGot = margin + 330
        val xBal = pageW - margin
        g.color = Color.BLACK
        g.font = Font(Font.DIALOG, Font.BOLD, 10)
        g.text(words.text(W.pdf_label_date), xDate, y)
        g.text(words.text(W.pdf_stmt_you_owe), xGave, y)
        g.text(words.text(W.pdf_stmt_you_paid), xGot, y)
        g.text(words.text(W.pdf_stmt_balance), xBal, y, end = true)
        y += 6
        g.color = line
        g.drawLine(margin.toInt(), y.toInt(), (pageW - margin).toInt(), y.toInt())
        y += 16

        val from = if (first) 0 else rowsFirstPage + (pageIndex - 1) * rowsPerPage
        val until = minOf(rows.size, if (first) rowsFirstPage else from + rowsPerPage)
        for (row in rows.subList(minOf(from, rows.size), until)) {
            val e = row.e.entry
            g.font = Font(Font.DIALOG, Font.PLAIN, 10)
            g.color = Color.BLACK
            g.text(Format.statementStamp(e.timestamp), xDate, y)
            g.color = if (e.isGiven) red else green
            g.text(Format.money(e.amount), if (e.isGiven) xGave else xGot, y)
            g.color = Color.BLACK
            g.font = Font(Font.DIALOG, Font.BOLD, 10)
            g.text(Format.money(row.running), xBal, y, end = true)
            y += 12
            val detail = buildList {
                add(e.entryNumber)
                Format.goods(row.e.items)?.let { add(it) }
                e.note?.takeIf { it.isNotBlank() }?.let { add(it) }
                if (e.isQarzeHasna) add(words.text(W.pdf_stmt_qarze_hasna))
            }.joinToString("  ·  ")
            g.color = muted
            g.font = Font(Font.DIALOG, Font.PLAIN, 8)
            g.text(detail, xDate, y)
            y += 8
            g.color = line
            g.drawLine(margin.toInt(), y.toInt(), (pageW - margin).toInt(), y.toInt())
            y += 16
        }

        // The closing balance, in the customer's terms, on the last page.
        if (pageIndex == pageCount() - 1) {
            y += 8
            g.color = navy
            g.fillRect(margin.toInt(), y.toInt(), (pageW - 2 * margin).toInt(), 52)
            g.color = gold
            g.fillRect((if (rtl) pageW - margin - 4 else margin).toInt(), y.toInt(), 4, 52)
            val label = when {
                Money.isPositive(closing) -> words.text(W.pdf_stmt_you_will_pay)
                Money.isNegative(closing) -> words.text(W.pdf_stmt_you_will_receive)
                else -> words.text(W.pdf_stmt_settled)
            }
            g.font = Font(Font.DIALOG, Font.PLAIN, 11)
            g.text(label, margin + 16, y + 20)
            g.color = Color.WHITE
            g.font = Font(Font.DIALOG, Font.BOLD, 18)
            g.text(Format.money(closing), margin + 16, y + 43)
        }
        return Printable.PAGE_EXISTS
    }

    companion object {
        /** Windows' print dialog for any A4 sheet (a statement, a report), then the print off the window's thread. False when cancelled. */
        fun show(statement: Printable, jobName: String): Boolean {
            val job = PrinterJob.getPrinterJob()
            job.jobName = jobName
            val format = job.defaultPage().apply {
                paper = Paper().apply {
                    setSize(595.0, 842.0)
                    setImageableArea(0.0, 0.0, 595.0, 842.0)
                }
            }
            job.setPrintable(statement, job.validatePage(format))
            if (!job.printDialog()) return false
            Thread({ runCatching { job.print() } }, "statement-print").start()
            return true
        }
    }
}
