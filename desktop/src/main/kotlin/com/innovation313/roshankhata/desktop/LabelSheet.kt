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
        // The name, one line, cut to the label.
        g.font = Font(Font.SANS_SERIF, Font.BOLD, 8)
        val fm = g.fontMetrics
        var name = p.name
        while (fm.stringWidth(name) > labelW - 2 * pad && name.length > 2) name = name.dropLast(2) + "…"
        g.drawString(name, (x + pad).toFloat(), (y + pad + fm.ascent).toFloat())
        // The price on the end side of the name line.
        p.salePrice?.let { price ->
            g.font = Font(Font.SANS_SERIF, Font.BOLD, 8)
            val text = Format.money(price)
            val w = g.fontMetrics.stringWidth(text)
            if (fm.stringWidth(name) + w + 3 * pad < labelW) {
                g.drawString(text, (x + labelW - pad - w).toFloat(), (y + pad + fm.ascent).toFloat())
            }
        }
        // The bars: as wide as the label allows, the code's digits under them.
        val code = p.barcode ?: return
        val matrix = encode(code, ((labelW - 2 * pad) * 4).toInt(), 1) ?: return
        val barsTop = y + pad + fm.height + 1
        val barsH = labelH - (barsTop - y) - pad - 8
        val scale = (labelW - 2 * pad) / matrix.width
        for (col in 0 until matrix.width) {
            if (matrix[col, 0]) {
                g.fillRect((x + pad + col * scale).toInt(), barsTop.toInt(), kotlin.math.ceil(scale).toInt().coerceAtLeast(1), barsH.toInt())
            }
        }
        g.font = Font(Font.MONOSPACED, Font.PLAIN, 7)
        val cw = g.fontMetrics.stringWidth(code)
        g.drawString(code, (x + (labelW - cw) / 2).toFloat(), (y + labelH - pad).toFloat())
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
