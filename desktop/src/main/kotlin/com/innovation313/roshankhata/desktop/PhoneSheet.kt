package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.DateWords
import com.innovation313.roshankhata.data.Digits
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Line2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.awt.print.PageFormat
import java.awt.print.Printable
import java.util.Date

/**
 * "Roshan Khata · Generated 25 Oct 2026, 13:51": the line every printed sheet carries at its foot (Marhala 4,
 * part 7 — the audit: the computer's printouts never said when they were made).
 */
fun madeLine(words: Words, at: Long = System.currentTimeMillis()): String =
    words.text(W.app_name) + " \u00b7 " + words.text(W.pdf_rep_generated, DateWords.formatter("dd MMM yyyy, HH:mm").format(Date(at)))

/**
 * The phone's family of A4 reports, drawn on the computer (Marhala 4, part 7 — the audit: the computer's
 * reports were navy tables that never said when they were made, the phone's are one family a shopkeeper
 * recognises): the deep green band with the shop's name and the gold lines under it, the note on what the
 * paper is and is not, the summary, tables with a grey head, striped rows and a quiet second line, and the
 * app, the time of making and the page at every foot. The sizes, colours and spacing are the phone's own
 * (data/LedgerReport.kt, data/RegisterReport.kt). The download banner and the watermark are the phone's
 * alone.
 *
 * A document writes itself once with a [Pen] — measured on a scrap picture, turning pages as the phone does —
 * and each page is kept as its drawing, replayed whenever it is drawn (the preview, the PDF and the printer
 * each draw a page more than once). Everything is mirrored for a right-to-left language.
 */
abstract class PhoneSheet(protected val rtl: Boolean) : Printable {

    companion object {
        const val PAGE_W = 595.0
        const val PAGE_H = 842.0
        const val MARGIN = 40.0
        /** The phone's documents' green (its constant is called NAVY) and gold. */
        val BAND = Color(0x09, 0x4C, 0x2E)
        val GOLD = Color(0xE1, 0xAF, 0x3F)
        val GREY = Color(0x7A, 0x7A, 0x7A)
        val RED = Color(0xC0, 0x39, 0x2B)
        val GREEN = Color(0x1E, 0x84, 0x49)
        val WARN_BG = Color(0xFF, 0xF6, 0xE0)
        val WARN_FG = Color(0x5C, 0x4A, 0x16)
        val RULE = Color(0xDD, 0xDD, 0xDD)
        /** A wash, not an opaque grey: the phone's stripe. */
        val ZEBRA = Color(0, 0, 0, 8)
        /** Rows end this far above the page's foot (the footer sits below). */
        const val FOOT = 60.0

        fun font(size: Float, bold: Boolean = false): Font =
            Font(PrintFonts.family, if (bold) Font.BOLD else Font.PLAIN, 1).deriveFont(size)

        private fun hints(g: Graphics2D) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        }
    }

    /** What heads every page (the band); returns where the page's words start. */
    protected abstract fun Pen.head(): Double

    /** The words at the foot of page [page], counted from 1. */
    protected abstract fun footer(page: Int): String

    /** The document, from the first page's head on. */
    protected abstract fun Pen.write()

    private val laid: List<List<(Graphics2D) -> Unit>> by lazy {
        val scrap = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
        val g = scrap.createGraphics()
        try {
            hints(g)
            val pen = Pen(g)
            pen.y = pen.head()
            pen.write()
            pen.finish()
        } finally {
            g.dispose()
        }
    }

    fun pageCount(): Int = laid.size

    override fun print(graphics: Graphics, format: PageFormat, pageIndex: Int): Int {
        if (pageIndex >= laid.size) return Printable.NO_SUCH_PAGE
        val g = graphics as Graphics2D
        g.translate(format.imageableX, format.imageableY)
        val scale = format.imageableWidth / PAGE_W
        g.scale(scale, scale)
        hints(g)
        laid[pageIndex].forEach { it(g) }
        return Printable.PAGE_EXISTS
    }

    /** Places words and shapes on the page being written, measures them, and turns pages. */
    inner class Pen internal constructor(private val m: Graphics2D) {
        private val pages = ArrayList<List<(Graphics2D) -> Unit>>()
        private var ops = ArrayList<(Graphics2D) -> Unit>()

        /** Where the next line goes, in points from the top. */
        var y = 0.0

        /** The page being written, counted from 1. */
        val page: Int get() = pages.size + 1

        internal fun finish(): List<List<(Graphics2D) -> Unit>> {
            close()
            return pages
        }

        private fun close() {
            text(footer(page), MARGIN, PAGE_H - 34.0, font(9f), GREY)
            pages += ops
            ops = ArrayList()
        }

        /** Ends this page (its footer) and heads the next. */
        fun newPage() {
            close()
            y = head()
        }

        /** True, after turning the page, when [h] more points would run into the foot. */
        fun turnFor(h: Double): Boolean {
            if (y + h <= PAGE_H - FOOT) return false
            newPage()
            return true
        }

        /** [s]'s width in [font], as printed (the language's own digits when chosen for sheets). */
        fun width(s: String, font: Font): Double {
            m.font = font
            return PrintFonts.width(m, Digits.forPrint(s))
        }

        /** [s] cut to [maxW] points with an ellipsis, whole characters only. */
        fun fit(s: String, font: Font, maxW: Double): String {
            if (width(s, font) <= maxW) return s
            val points = s.codePointCount(0, s.length)
            var lo = 0
            var hi = points
            // The longest start that fits with its ellipsis.
            while (lo < hi) {
                val mid = (lo + hi + 1) / 2
                if (width(s.substring(0, s.offsetByCodePoints(0, mid)) + "…", font) <= maxW) lo = mid else hi = mid - 1
            }
            return s.substring(0, s.offsetByCodePoints(0, lo)).trimEnd() + "…"
        }

        /** [s] broken at spaces into lines of at most [maxW] points (a word longer than a line is cut). */
        fun wrap(s: String, font: Font, maxW: Double): List<String> {
            val out = ArrayList<String>()
            var line = ""
            for (word in s.split(' ').filter { it.isNotEmpty() }) {
                val next = if (line.isEmpty()) word else "$line $word"
                if (width(next, font) <= maxW) line = next
                else {
                    if (line.isNotEmpty()) out += line
                    line = if (width(word, font) <= maxW) word else fit(word, font, maxW)
                }
            }
            if (line.isNotEmpty()) out += line
            return out
        }

        /** [s] from the start side at [x] (or ending at [x] when [end]), its baseline at [atY]. */
        fun text(s: String, x: Double, atY: Double, font: Font, color: Color, end: Boolean = false) {
            if (s.isEmpty()) return
            val t = Digits.forPrint(s)
            ops += { g ->
                g.font = font
                g.color = color
                val w = PrintFonts.width(g, t)
                val left = when {
                    !rtl && !end -> x
                    !rtl && end -> x - w
                    rtl && !end -> PAGE_W - x - w
                    else -> PAGE_W - x
                }
                PrintFonts.draw(g, t, left.toFloat(), atY.toFloat())
            }
        }

        /** A filled box from [x1] to [x2] (from the start side), [y1] to [y2]. */
        fun box(x1: Double, y1: Double, x2: Double, y2: Double, color: Color) {
            val a = if (rtl) PAGE_W - x2 else x1
            val b = if (rtl) PAGE_W - x1 else x2
            ops += { g -> g.color = color; g.fill(Rectangle2D.Double(a, y1, b - a, y2 - y1)) }
        }

        /** A hairline from [x1] to [x2] (from the start side) at [atY]. */
        fun rule(x1: Double, x2: Double, atY: Double, color: Color = RULE, weight: Float = 0.6f) {
            val a = if (rtl) PAGE_W - x2 else x1
            val b = if (rtl) PAGE_W - x1 else x2
            ops += { g -> g.color = color; g.stroke = BasicStroke(weight); g.draw(Line2D.Double(a, atY, b, atY)) }
        }

        // ---- The family's pieces ----

        /** The note on what the paper is: a pale box, its lines wrapped, the first in bold when [boldFirst]. */
        fun note(lines: List<String>, boldFirst: Boolean = false) {
            val room = PAGE_W - 2 * MARGIN - 20.0
            val laidOut = lines.flatMapIndexed { i, s -> wrap(s, font(10f, boldFirst && i == 0), room).map { it to (boldFirst && i == 0) } }
            val h = 12.0 + 14.0 * laidOut.size
            box(MARGIN, y, PAGE_W - MARGIN, y + h, WARN_BG)
            laidOut.forEachIndexed { i, (s, bold) -> text(s, MARGIN + 10.0, y + 16.0 + 14.0 * i, font(10f, bold), WARN_FG) }
            y += h + 14.0
        }

        /** A section's title in the band's green. */
        fun section(title: String) {
            text(title, MARGIN, y, font(14f, true), BAND)
            y += 20.0
        }

        /** A summary line: [label] at the start, [value] at the end. */
        fun line(label: String, value: String, valueFont: Font = font(11f), valueColor: Color = Color.BLACK, labelColor: Color = Color.BLACK) {
            text(label, MARGIN, y, font(11f), labelColor)
            text(value, PAGE_W - MARGIN, y, valueFont, valueColor, end = true)
            y += 16.0
        }

        /** The grey head of a table: each column's word at its x, end-aligned where asked. */
        fun tableHead(columns: List<Triple<String, Double, Boolean>>) {
            box(MARGIN, y, PAGE_W - MARGIN, y + 18.0, GREY)
            columns.forEach { (word, x, end) -> text(word, x, y + 12.5, font(9f, true), Color.WHITE, end) }
            y += 18.0
        }

        /** The rule between the summary and a section. */
        fun divider() {
            y += 10.0
            rule(MARGIN, PAGE_W - MARGIN, y)
            y += 20.0
        }
    }
}
