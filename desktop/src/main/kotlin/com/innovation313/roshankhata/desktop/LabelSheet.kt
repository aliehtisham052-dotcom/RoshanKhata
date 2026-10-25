package com.innovation313.roshankhata.desktop

import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.oned.Code128Writer
import com.google.zxing.oned.EAN13Writer
import com.google.zxing.oned.EAN8Writer
import com.innovation313.roshankhata.data.Barcodes
import com.innovation313.roshankhata.data.Product
import com.innovation313.roshankhata.ui.Format
import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.print.PageFormat
import java.awt.print.Printable

/**
 * Barcode labels on A4 (POS Phase 2): the common 50 × 25 mm label, four
 * across and eleven down, each with the product's name, its price and its
 * code as bars a scanner reads — an EAN-13 / EAN-8 when the code is one,
 * Code 128 for anything else. Printed on plain paper and cut, or on a
 * sheet of labels. Opens in the app's preview like every other sheet.
 */
class LabelSheet(private val products: List<Product>, private val copies: Int = 1) : Printable {

    private val cols = 4
    private val rows = 11
    private val labelW = 50 * PT_PER_MM
    private val labelH = 25 * PT_PER_MM
    private val marginX = (595.0 - cols * labelW) / 2
    private val marginY = (842.0 - rows * labelH) / 2
    private val labels: List<Product> = products.flatMap { p -> List(copies.coerceAtLeast(1)) { p } }
    private val perPage = cols * rows

    fun pageCount(): Int = (labels.size + perPage - 1) / perPage

    override fun print(g: Graphics, pf: PageFormat, pageIndex: Int): Int {
        if (pageIndex >= pageCount()) return Printable.NO_SUCH_PAGE
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        val from = pageIndex * perPage
        labels.drop(from).take(perPage).forEachIndexed { i, p ->
            val x = marginX + (i % cols) * labelW
            val y = marginY + (i / cols) * labelH
            drawLabel(g2, p, x, y)
        }
        return Printable.PAGE_EXISTS
    }

    private fun drawLabel(g: Graphics2D, p: Product, x: Double, y: Double) {
        val pad = 2.0 * PT_PER_MM
        g.color = Color(0xDDDDDD)
        g.drawRect(x.toInt(), y.toInt(), labelW.toInt(), labelH.toInt())
        g.color = Color.BLACK
        // The name, one line, cut to the label, on the reading side; the price at the far end. In the app's
        // print face, which draws Hindi and Bengali on a stock Windows (21 Oct 2026: SANS_SERIF printed boxes).
        val rtl = com.innovation313.roshankhata.ui.BidiPort.isRtlLocale(java.util.Locale.getDefault())
        // A name in another script than the face's (a Hindi name on an Urdu shop's labels) takes the face that
        // draws it, a character at a time (PrintFonts.attributed, 23 Oct 2026): one face printed it as boxes.
        g.font = Font(PrintFonts.family, Font.BOLD, 8)
        val fm = g.fontMetrics
        // The price first, at the far end; the name takes what room is left (25 Oct 2026, the audit: a long name
        // was fitted to the whole label and the price, the figure a shelf label is for, was left off).
        val priceText = p.salePrice?.let { Format.money(it) }
        val priceW = priceText?.let { PrintFonts.width(g, it) } ?: 0.0
        val name = fitted(g, p.name, labelW - 2 * pad - (if (priceText != null) priceW + pad else 0.0))
        val nameW = PrintFonts.width(g, name)
        val nameX = if (rtl) x + labelW - pad - nameW else x + pad
        PrintFonts.draw(g, name, nameX.toFloat(), (y + pad + fm.ascent).toFloat())
        priceText?.let { text ->
            val priceX = if (rtl) x + pad else x + labelW - pad - priceW
            PrintFonts.draw(g, text, priceX.toFloat(), (y + pad + fm.ascent).toFloat())
        }
        // The bars: as wide as the label allows, the code's digits under them. One module is one column of the
        // code's own matrix, every bar one rectangle at its exact place, drawn without smoothing so the printer
        // sets each edge at its own dots (25 Oct 2026, the audit: each column was cut to a whole point and widened
        // to one, so bars that should match came out one and two points wide — a scanner reads those widths).
        val code = p.barcode ?: return
        val matrix = encode(code, 0, 1) ?: return
        val barsTop = y + pad + fm.height + 1
        val barsH = labelH - (barsTop - y) - pad - 8
        val scale = (labelW - 2 * pad) / matrix.width
        val smoothing = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
        for ((from, until) in barRuns(matrix)) {
            g.fill(java.awt.geom.Rectangle2D.Double(x + pad + from * scale, barsTop, (until - from) * scale, barsH))
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, smoothing)
        g.font = Font(Font.MONOSPACED, Font.PLAIN, 7)
        val cw = g.fontMetrics.stringWidth(code)
        g.drawString(code, (x + (labelW - cw) / 2).toFloat(), (y + labelH - pad).toFloat())
    }

    /** [s] as it fits in [room] points in [g]'s font (with the faces PrintFonts gives it), cut whole characters at a time with "…". */
    internal fun fitted(g: Graphics2D, s: String, room: Double): String {
        if (PrintFonts.width(g, s) <= room) return s
        var cut = s
        while (cut.isNotEmpty() && PrintFonts.width(g, cut.trimEnd() + "…") > room) {
            // One character off the end, never half of a pair (an emoji, a rare letter).
            cut = cut.substring(0, cut.offsetByCodePoints(cut.length, -1))
        }
        return cut.trimEnd() + "…"
    }

    /** The code's dark modules as runs [from, until) of matrix columns: each run one bar. */
    internal fun barRuns(matrix: BitMatrix): List<Pair<Int, Int>> {
        val out = mutableListOf<Pair<Int, Int>>()
        var col = 0
        while (col < matrix.width) {
            if (matrix[col, 0]) {
                val from = col
                while (col < matrix.width && matrix[col, 0]) col++
                out += from to col
            } else col++
        }
        return out
    }

    private fun encode(code: String, width: Int, height: Int): BitMatrix? = runCatching {
        when {
            code.length == 13 && Barcodes.isValidEan(code) -> EAN13Writer().encode(code, BarcodeFormat.EAN_13, width, height)
            code.length == 8 && Barcodes.isValidEan(code) -> EAN8Writer().encode(code, BarcodeFormat.EAN_8, width, height)
            else -> Code128Writer().encode(code, BarcodeFormat.CODE_128, width, height)
        }
    }.getOrNull()

    companion object {
        const val PT_PER_MM = 72.0 / 25.4
    }
}
