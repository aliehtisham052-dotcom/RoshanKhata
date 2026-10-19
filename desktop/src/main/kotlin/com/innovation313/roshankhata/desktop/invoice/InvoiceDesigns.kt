package com.innovation313.roshankhata.desktop.invoice

import com.innovation313.roshankhata.data.Invoice
import com.innovation313.roshankhata.data.InvoiceItem
import com.innovation313.roshankhata.desktop.gfx.PdfDocument
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.NumberWords
import com.innovation313.roshankhata.words.W
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.print.PageFormat
import java.awt.print.Printable

/** The phone's NumberWords call shape: the language from the number_words_language string. */
fun NumberWords.amountInWords(context: Context, amount: Double): String =
    amountInWords(context.getString(W.number_words_language), amount)

/** The phone's InvoiceLabels: "Discount (5%)" for a percent, plain "Discount" for an amount. */
fun Invoice.discountLabel(word: String): String =
    if (discountAmount != null) word else "$word (${Format.plain(discountPercent ?: 0.0)}%)"

/**
 * The ten invoice designs on the computer (Phase 10): the phone's own drawing
 * code (InvoicePdfExport, InvoiceTemplateKit — generated copies) run on the
 * gfx stand-in, so a Windows invoice is the phone's invoice, design for design.
 */
object InvoiceDesigns {

    /** The phone's order and names (InvoiceEditorActivity.templateIds). */
    val NAMES = listOf(
        1 to W.invoice_template_teal, 2 to W.invoice_template_black_gold, 3 to W.invoice_template_gradient,
        4 to W.invoice_template_green_retail, 5 to W.invoice_template_minimal_slate, 6 to W.invoice_template_indigo_tech,
        7 to W.invoice_template_warm_orange, 8 to W.invoice_template_classic_cream, 9 to W.invoice_template_crimson_bold,
        10 to W.invoice_template_thermal
    )

    /** The invoice drawn in its design ([Invoice.templateId]); null if drawing failed. */
    fun pages(context: Context, invoice: Invoice, items: List<InvoiceItem>): PdfDocument? =
        runCatching { InvoicePdfExport.build(context, invoice, items) }.getOrNull()?.takeIf { it.finished.isNotEmpty() }
}

/**
 * A finished set of pages to the printer (or "Microsoft Print to PDF"). Each
 * page keeps its own size in points: an A4 design fills an A4 sheet at 1:1,
 * and anything larger than the printable area is scaled down to fit, never
 * cut off. The narrow thermal design prints at its true width.
 */
class DocumentPrint(private val doc: PdfDocument) : Printable {
    override fun print(graphics: Graphics, format: PageFormat, pageIndex: Int): Int {
        val page = doc.finished.getOrNull(pageIndex) ?: return Printable.NO_SUCH_PAGE
        val g = graphics as Graphics2D
        val w = page.info.pageWidth.toDouble()
        val h = page.info.pageHeight.toDouble()
        val fullSheet = w <= format.width + 1 && h <= format.height + 1 && w >= format.width * 0.9
        if (fullSheet) {
            // A4 on A4, at the paper's own corner: the design's 40pt margins already sit inside the printable area.
        } else {
            val scale = minOf(1.0, format.imageableWidth / w, format.imageableHeight / h)
            g.translate(format.imageableX, format.imageableY)
            if (scale < 1.0) g.scale(scale, scale)
        }
        g.clipRect(0, 0, w.toInt(), h.toInt())
        page.canvas.replay(g)
        return Printable.PAGE_EXISTS
    }
}
