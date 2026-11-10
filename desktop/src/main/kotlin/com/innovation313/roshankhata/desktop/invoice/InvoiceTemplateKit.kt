// GENERATED from app/src/main/java/com/innovation313/roshankhata/data/InvoiceTemplateKit.kt
// by scripts/gen_desktop_invoice.py. Do not edit: change the phone's file and re-run.
package com.innovation313.roshankhata.desktop.invoice

import com.innovation313.roshankhata.data.Currency
import com.innovation313.roshankhata.data.DateWords
import com.innovation313.roshankhata.data.DocKind
import com.innovation313.roshankhata.data.Invoice
import com.innovation313.roshankhata.data.InvoiceItem
import com.innovation313.roshankhata.data.InvoiceTotals
import com.innovation313.roshankhata.data.lineTotal
import com.innovation313.roshankhata.data.totals
import com.innovation313.roshankhata.desktop.gfx.Bitmap
import com.innovation313.roshankhata.desktop.gfx.Canvas
import com.innovation313.roshankhata.desktop.gfx.Color
import com.innovation313.roshankhata.desktop.gfx.LinearGradient
import com.innovation313.roshankhata.desktop.gfx.Paint
import com.innovation313.roshankhata.desktop.gfx.Path
import com.innovation313.roshankhata.desktop.gfx.PdfDocument
import com.innovation313.roshankhata.desktop.gfx.Rect
import com.innovation313.roshankhata.desktop.gfx.RectF
import com.innovation313.roshankhata.desktop.gfx.Shader
import com.innovation313.roshankhata.desktop.gfx.Typeface
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.NumberWords
import java.io.File
import java.util.Date

/**
 * The pieces every A4-style invoice template (T1 through T9 — T10 is a
 * different shape entirely, a narrow thermal receipt, and stays its own
 * function) shares: a header band, the Bill To / Invoice Details columns,
 * the items table, the payment-info-and-totals block, amount in words,
 * terms, and the signature box.
 *
 * Built by extracting T1's own drawing code into parameterised pieces
 * rather than writing this from scratch — T1 was already the finished,
 * mockup-matched version, so this is the SAME output for T1, just able to
 * take a different [Palette], [Fonts], and optional extra item column for
 * whichever template calls it next. A change made here (a spacing fix, a
 * new shared field) reaches every template built on it; a change made by
 * copy-pasting T1's function eight more times would not.
 */
object InvoiceTemplateKit {

    // ---------- The Roshan Khata seal (10 Nov 2026) ----------

    /** The seal's own room on a page: its diameter and the air kept above the maker's strip. */
    const val SEAL_SIZE = 92f
    const val SEAL_GAP = 12f
    /** The app's green as stamp ink, a little thinned so it reads as ink on paper. */
    const val SEAL_INK = 0xFF1B5E3A.toInt()

    /**
     * The round green seal the owner chose (10 Nov 2026): "ROSHAN KHATA" round the top, "DIGITAL RECORD" round the
     * bottom, a star each side, the open book, and in the middle what THIS paper is - its own date and number on an
     * invoice or a receipt, the day it was made and "STATEMENT" / "REPORT" / "REGISTER" on the others. It says only
     * what is true: the record was kept on Roshan Khata; nothing in it claims the figures were checked.
     *
     * Drawn as ink (no fill, the paper shows through) in its own square of [size], never over a figure and never on
     * the maker's strip: callers give it room above the strip on the last page. Letters are placed one by one round
     * the rim with plain rotations, so the computer's drawing stand-in draws it the same, and the whole seal is one
     * left-to-right unit on a right-to-left page ([PdfRtl.asOne]).
     */
    fun drawRecordSeal(
        c: Canvas, cx: Float, cy: Float, size: Float, middle: String, below: String,
        ink: Int = SEAL_INK, paper: Int = 0xFFFFFFFF.toInt()
    ) {
        // A stamp's own build (10 Nov 2026, the owner: the first one looked thin and plain): full-strength ink, a heavy
        // rim with a fine line inside it, the name over the top and DIGITAL RECORD under the bottom, a lightly tinted
        // centre with the book, and the paper's own date on a ribbon across the middle - the figure a reader looks for.
        val k = size / 320f
        val ring = Paint().apply { color = ink; style = Paint.Style.STROKE; isAntiAlias = true }
        val fill = Paint().apply { color = ink; style = Paint.Style.FILL; isAntiAlias = true }
        PdfRtl.asOne(c, cx - size / 2f, cx + size / 2f) {
            ring.strokeWidth = 10f * k; c.drawCircle(cx, cy, 151f * k, ring)
            ring.strokeWidth = 1.8f * k; c.drawCircle(cx, cy, 140f * k, ring)
            c.drawCircle(cx, cy, 98f * k, ring)
            fill.alpha = 0x1F; c.drawCircle(cx, cy, 92f * k, fill); fill.alpha = 0xFF

            val rim = Paint().apply {
                color = ink; isAntiAlias = true; textSize = 25f * k; textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            // Each line's letters centred on the band's middle (radius 119).
            arcWords(c, "ROSHAN KHATA", cx, cy, (119f - 0.36f * 25f) * k, rim, 5.5f * k, top = true)
            rim.textSize = 18f * k
            arcWords(c, "DIGITAL RECORD", cx, cy, (119f - 0.36f * 18f) * k, rim, 4.5f * k, top = false)

            // The book, filled: two pages meeting at the spine, a gap of paper between them.
            val bs = 1.05f * k; val bx = cx - 26f * bs; val by = cy - 76f * k
            fun page(edge: Float) = Path().apply {
                val spine = 26f + if (edge < 26f) -1.6f else 1.6f
                moveTo(bx + edge * bs, by + 6f * bs)
                cubicTo(bx + (edge + (spine - edge) * 0.35f) * bs, by + 1f * bs, bx + (edge + (spine - edge) * 0.7f) * bs, by + 1f * bs, bx + spine * bs, by + 7f * bs)
                lineTo(bx + spine * bs, by + 39f * bs)
                cubicTo(bx + (edge + (spine - edge) * 0.7f) * bs, by + 33f * bs, bx + (edge + (spine - edge) * 0.35f) * bs, by + 33f * bs, bx + edge * bs, by + 38f * bs)
                close()
            }
            c.drawPath(page(2f), fill); c.drawPath(page(50f), fill)

            // The ribbon: across the middle and out over the rim's band, its ends cut in a swallowtail.
            val top = cy - 15f * k; val bottom = cy + 27f * k; val half = 132f * k; val notch = 11f * k
            val ribbon = Path().apply {
                moveTo(cx - half, top); lineTo(cx + half, top); lineTo(cx + half - notch, (top + bottom) / 2f)
                lineTo(cx + half, bottom); lineTo(cx - half, bottom); lineTo(cx - half + notch, (top + bottom) / 2f); close()
            }
            c.drawPath(ribbon, fill)
            ring.color = paper; ring.strokeWidth = 1.4f * k
            c.drawLine(cx - half + notch + 6f * k, top + 4f * k, cx + half - notch - 6f * k, top + 4f * k, ring)
            c.drawLine(cx - half + notch + 6f * k, bottom - 4f * k, cx + half - notch - 6f * k, bottom - 4f * k, ring)
            ring.color = ink

            val mid = Paint().apply {
                color = paper; isAntiAlias = true; textAlign = Paint.Align.CENTER; textSize = 28f * k
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            while (mid.measureText(middle) > 200f * k && mid.textSize > 10f * k) mid.textSize -= 0.5f * k
            PdfRtl.drawText(c, middle, cx, (top + bottom) / 2f + mid.textSize * 0.36f, mid)
            mid.color = ink; mid.textSize = 21f * k
            while (mid.measureText(below) > 136f * k && mid.textSize > 10f * k) mid.textSize -= 0.5f * k
            PdfRtl.drawText(c, below, cx, cy + 62f * k, mid)
        }
    }

    /** [words] round a circle of [radius], centred at the top (upright, read left to right) or the bottom. */
    private fun arcWords(c: Canvas, words: String, cx: Float, cy: Float, radius: Float, p: Paint, track: Float, top: Boolean) {
        val widths = words.map { p.measureText(it.toString()) + track }
        val sweep = widths.sum() - track
        var along = -sweep / 2f
        val baseline = if (top) cy - radius else cy + radius + p.textSize * 0.72f
        words.forEachIndexed { i, ch ->
            val w = widths[i] - track
            val centre = along + w / 2f
            val degrees = Math.toDegrees((centre / radius).toDouble()).toFloat()
            val saved = c.save()
            c.rotate(if (top) degrees else -degrees, cx, cy)
            PdfRtl.drawText(c, ch.toString(), cx, baseline, p)
            c.restoreToCount(saved)
            along += widths[i]
        }
    }



    /**
     * A template's own colour scheme.
     *
     * [gradient] false gives a flat header band instead of primary-into-primaryEnd.
     *
     * [pageBackground] null leaves the page white, which is every template
     * so far; a dark design (T2's near-black, T8's cream) sets it and the
     * whole sheet is filled before anything else is drawn.
     *
     * [onPrimary]/[onPrimaryMuted] are the text colours used ON the header
     * band and the filled TOTAL bar — separate from [ink]/[muted], which
     * are for text on the page itself. A gold band wants near-black text
     * on it while its page text is cream; assuming white would be wrong
     * for exactly those templates.
     */
    data class Palette(
        val ink: Int,
        val primary: Int,
        val primaryEnd: Int,
        val muted: Int,
        val ruleColor: Int,
        val boxFill: Int,
        val zebra: Int,
        val gradient: Boolean = true,
        /**
         * False draws the header as plain text with a hairline rule under
         * it, instead of a filled colour band — for a design whose whole
         * point is using as little ink as possible on a shop's own printer.
         * A template that sets this also wants onPrimary/onPrimaryMuted set
         * to its normal page text colours, since there is no coloured band
         * for light text to sit on any more.
         */
        val bandFilled: Boolean = true,
        /**
         * True stacks the letterhead centred down the middle — shop name,
         * address, then INVOICE — instead of the usual shop-left /
         * INVOICE-right split. This is what makes a formal document read as
         * formal, so it belongs to the template rather than being faked
         * with spacing.
         */
        val centeredHeader: Boolean = false,
        /**
         * An optional middle stop, for a band that runs through three
         * colours rather than two — T3's violet into magenta into pink.
         * Null keeps the plain two-stop primary-to-primaryEnd sweep every
         * other template uses.
         */
        val primaryMid: Int? = null,
        val pageBackground: Int? = null,
        val onPrimary: Int = Color.WHITE,
        val onPrimaryMuted: Int = 0xFFBEE3E1.toInt(),
        val tableHeaderFill: Int? = null,
        val onTableHeader: Int = Color.WHITE,
        /**
         * Text colour ON the filled TOTAL bar, when it must differ from
         * [onPrimary]. A no-band design (bandFilled = false) points
         * onPrimary at its normal page ink — right for the header, and
         * unreadable on the TOTAL bar, which is still filled with
         * [primary]. Found by measuring, not by eye: dark-on-dark there
         * was 1.5:1. Null keeps onPrimary, which every banded design wants.
         */
        val onTotalBar: Int? = null
    )

    /** A template's own fonts — headings/labels vs monospace for numbers and money, matching the spec's per-template font pairs. */
    data class Fonts(
        val heading: Typeface,
        val mono: Typeface,
        val monoBold: Typeface,
        /** A real bold file for the heading face, where one exists (Manrope). */
        val headingBold: Typeface? = null
    )

    /**
     * Every word printed on an invoice, in the app's language.
     *
     * These used to be literals in this file — mostly English ("BILL TO",
     * "QTY") with three Roman Urdu words in the middle ("RASID", "TAFSEEL",
     * "Tareekh"), so an English invoice read in two languages at once. They
     * now come from string resources, resolved by Android for whichever
     * language the app is in, the same way the amount-in-words line already
     * works. A Roman Urdu app still prints "TAFSEEL" and "Tareekh" — there
     * they belong.
     */
    class Labels(private val context: Context) {
        val invoice: String = context.getString(R.string.pdf_label_invoice)
        val billTo: String = context.getString(R.string.pdf_label_bill_to)
        val invoiceDetails: String = context.getString(R.string.pdf_label_invoice_details)
        val invoiceNo: String = context.getString(R.string.pdf_label_invoice_no)
        val date: String = context.getString(R.string.pdf_label_date)
        val dueDate: String = context.getString(R.string.pdf_label_due_date)
        val time: String = context.getString(R.string.pdf_label_time)
        val item: String = context.getString(R.string.pdf_label_item)
        val qty: String = context.getString(R.string.pdf_label_qty)
        val rate: String = context.getString(R.string.pdf_label_rate)
        val amount: String = context.getString(R.string.pdf_label_amount)
        val subtotal: String = context.getString(R.string.pdf_label_subtotal)
        val discount: String = context.getString(R.string.pdf_label_discount)
        val tax: String = context.getString(R.string.pdf_label_tax)
        val extraCharges: String = context.getString(R.string.pdf_label_extra_charges)
        val total: String = context.getString(R.string.pdf_label_total)
        val received: String = context.getString(R.string.pdf_label_received)
        val balanceDue: String = context.getString(R.string.pdf_label_balance_due)
        val paymentInfo: String = context.getString(R.string.pdf_label_payment_info)
        val scanToPay: String = context.getString(R.string.pdf_label_scan_to_pay)
        val amountInWords: String = context.getString(R.string.pdf_label_amount_in_words)
        val signatory: String = context.getString(R.string.pdf_label_signatory)
        val terms: String = context.getString(R.string.pdf_label_terms)
        val bank: String = context.getString(R.string.pdf_label_bank)
        val accountTitle: String = context.getString(R.string.pdf_label_account_title)
        val thankYou: String = context.getString(R.string.pdf_label_thank_you)
        val visitAgain: String = context.getString(R.string.pdf_label_visit_again)

        /**
         * The heading a document of [kind] prints where an invoice prints
         * [invoice] (schema 40): QUOTATION, SALES ORDER, DELIVERY CHALLAN…
         * in the app's language. The invoice keeps its own word.
         */
        fun heading(kind: String): String = when (kind) {
            DocKind.QUOTATION -> context.getString(R.string.doc_kind_quotation).uppercase()
            DocKind.PROFORMA -> context.getString(R.string.doc_kind_proforma).uppercase()
            DocKind.SALES_ORDER -> context.getString(R.string.doc_kind_sales_order).uppercase()
            DocKind.CHALLAN -> context.getString(R.string.doc_kind_challan).uppercase()
            DocKind.CREDIT_NOTE -> context.getString(R.string.doc_kind_credit_note).uppercase()
            DocKind.DEBIT_NOTE -> context.getString(R.string.doc_kind_debit_note).uppercase()
            else -> invoice
        }

        /**
         * The details block's head for a document of [kind]: an invoice keeps
         * "INVOICE DETAILS"; any other kind says "DETAILS" alone, under its own
         * heading (30 Oct 2026, the proof pictures: a quotation and a challan
         * printed "INVOICE DETAILS" and "Invoice No").
         */
        fun detailsFor(kind: String): String =
            if (kind == DocKind.INVOICE) invoiceDetails else context.getString(R.string.details).uppercase()

        /** The number's label for a document of [kind]: "Invoice No" for an invoice, "No." for the rest (Q-, SO-, DC-…). */
        fun numberFor(kind: String): String =
            if (kind == DocKind.INVOICE) invoiceNo else context.getString(R.string.pdf_label_doc_no)
    }

    /**
     * Sub Total is only worth printing when something stands between it and
     * the grand total. With no discount, tax or extra charge the two are the
     * same figure, and printing it twice just makes the owner's customer
     * check the same number twice.
     */
    fun showSubtotal(totals: InvoiceTotals): Boolean =
        totals.discountAmount > 0 || totals.taxAmount > 0 || totals.additionalCharge > 0

    /**
     * One extra column a vertical needs between the item name and Qty —
     * T2's Wazan (weight), T6's Serial/IMEI, T9's Size/Colour. Null for a
     * template with no extra column, which is most of them.
     */
    data class ExtraColumn(val label: String, val valueOf: (InvoiceItem) -> String)

    fun paint(
        fonts: Fonts,
        size: Float,
        colour: Int,
        bold: Boolean = false,
        align: Paint.Align = Paint.Align.LEFT,
        mono: Boolean = false,
        italic: Boolean = false
    ): Paint = Paint().apply {
        isAntiAlias = true
        textSize = size
        color = colour
        textAlign = align
        typeface = when {
            mono && bold -> fonts.monoBold
            mono -> fonts.mono
            bold -> fonts.headingBold ?: Typeface.create(fonts.heading, Typeface.BOLD)
            italic -> Typeface.create(fonts.heading, Typeface.ITALIC)
            else -> fonts.heading
        }
        // Belt as well as braces on the heading faces. Sora, Manrope and
        // Playfair are VARIABLE-weight files with no bold sibling, and a
        // style request on such a face is honoured inconsistently — where
        // it is ignored, a "bold" label renders at the file's default
        // weight and a small caption on a coloured band comes out thin,
        // which is exactly what the owner kept reporting. Asking for the
        // fake bold explicitly emboldens regardless of what the style
        // request did. Not applied to mono, which has a real bold file
        // ([Fonts.monoBold]) — doubling up there would smear the digits.
        // A real bold file needs no smearing on top.
        if (bold && !mono && fonts.headingBold == null) isFakeBoldText = true
    }

    fun solid(colour: Int): Paint = Paint().apply { isAntiAlias = true; color = colour }

    /**
     * Fills the whole sheet, for a template whose page is not white — T2's
     * near-black, T8's cream. Must be called before anything else on a
     * fresh page, including after a page break, or that page keeps the
     * PDF's own white default while the first one is dark.
     */
    fun fillPage(c: Canvas, palette: Palette, pageW: Int, pageH: Int) {
        palette.pageBackground?.let { c.drawRect(0f, 0f, pageW.toFloat(), pageH.toFloat(), solid(it)) }
    }

    /**
     * The gradient (or flat) band across the top: monogram tile, shop
     * name/address/STRN, and "INVOICE"/[subtitle] on the right. Returns the
     * y position work should continue from.
     */
    fun drawHeaderBand(
        c: Canvas,
        context: Context,
        palette: Palette,
        fonts: Fonts,
        pageW: Int,
        left: Float,
        right: Float,
        subtitle: String = "",
        heading: String = Labels(context).invoice
    ): Float {
        if (palette.bandFilled) {
            val band = Paint().apply {
                isAntiAlias = true
                shader = if (palette.gradient) {
                    val mid = palette.primaryMid
                    if (mid != null) {
                        LinearGradient(
                            0f, 0f, pageW.toFloat(), 0f,
                            intArrayOf(palette.primary, mid, palette.primaryEnd),
                            floatArrayOf(0f, 0.5f, 1f),
                            Shader.TileMode.CLAMP
                        )
                    } else {
                        LinearGradient(0f, 0f, pageW.toFloat(), 0f, palette.primary, palette.primaryEnd, Shader.TileMode.CLAMP)
                    }
                } else null
                if (!palette.gradient) color = palette.primary
            }
            c.drawRect(0f, 0f, pageW.toFloat(), 96f, band)
        } else {
            // A single hairline where the band's lower edge would have been —
            // enough to separate the letterhead from the body without
            // covering a sixth of the page in ink.
            c.drawLine(left, 96f, right, 96f, Paint().apply {
                color = palette.ruleColor; strokeWidth = 1f; isAntiAlias = true
            })
        }

        val shopName = BusinessProfile.businessName(context)?.takeIf { it.isNotBlank() } ?: ""
        val address = BusinessProfile.businessAddress(context)
        // Only for a shop registered for sales tax — blank prints nothing,
        // deliberately, since FBR's own guidance is that an invoice with no
        // STRN should not be charging sales tax in the first place.
        val strn = BusinessProfile.strn(context)
        // STRN and the shop's phone share one line, so a phone never needs a
        // slot of its own in a header whose lines are already placed.
        val contact = listOfNotNull(
            strn?.let { "${BusinessProfile.taxLabel(context)}: $it" },
            BusinessProfile.businessPhone(context)?.let { Format.ltr(it) }
        ).joinToString("  \u00B7  ").ifEmpty { null }

        if (palette.centeredHeader) {
            val mid = pageW / 2f
            val full = right - left
            drawFit(c, shopName, mid, 36f, paint(fonts, 18f, palette.onPrimary, bold = true, align = Paint.Align.CENTER), full)
            address?.let { drawFit(c, it, mid, 51f, paint(fonts, 9f, palette.onPrimaryMuted, align = Paint.Align.CENTER), full) }
            contact?.let { drawFit(c, it, mid, 63f, paint(fonts, 8f, palette.onPrimaryMuted, align = Paint.Align.CENTER), full) }
            PdfRtl.drawText(c, heading, mid, 84f, paint(fonts, 16f, palette.onPrimary, bold = true, align = Paint.Align.CENTER))
        } else {
            val tile = RectF(left, 26f, left + 40f, 66f)
            // The shop's own logo in the tile when the owner has added one
            // (Business Profile, 30 Sep 2026); otherwise the Roshan Khata
            // mark stands in, on white so its green and gold read on any
            // band colour. The initials remain only as the fallback if no
            // image can be loaded at all.
            val mark = BusinessProfile.loadLogo(context) ?: PdfBranding.logo(context)
            if (palette.bandFilled) {
                c.drawRoundRect(tile, 9f, 9f, solid(if (mark != null) Color.WHITE else 0x33FFFFFF))
            } else {
                // Outlined rather than filled, same reason as the band itself.
                c.drawRoundRect(tile, 9f, 9f, Paint().apply {
                    isAntiAlias = true; color = palette.primary
                    style = Paint.Style.STROKE; strokeWidth = 1f
                })
            }
            if (mark != null) {
                val inset = 4f
                drawBitmapFit(c, mark, RectF(tile.left + inset, tile.top + inset, tile.right - inset, tile.bottom - inset))
            } else {
                val initials = shopName.trim().split(Regex("\\s+"))
                    .filter { it.isNotEmpty() }
                    .take(2)
                    .joinToString("") { it.take(1).uppercase() }
                PdfRtl.drawText(c,
                    initials.ifEmpty { "R" }, tile.centerX(), tile.centerY() + 5f,
                    paint(fonts, 14f, palette.onPrimary, bold = true, align = Paint.Align.CENTER)
                )
            }

            // The shop's name leaves room for the heading on the far side ("Proforma Invoice" met a long name, L123).
            val headW = paint(fonts, 22f, palette.onPrimary, bold = true).measureText(heading).coerceAtMost((right - left) * 0.45f)
            val nameRoom = right - left - 52f - headW - 14f
            drawFit(c, shopName, left + 52f, 44f, paint(fonts, 16f, palette.onPrimary, bold = true), nameRoom)
            address?.let {
                drawFit(c, it, left + 52f, 58f, paint(fonts, 9f, palette.onPrimaryMuted), nameRoom)
            }
            contact?.let {
                drawFit(c, it, left + 52f, 70f, paint(fonts, 8f, palette.onPrimaryMuted), nameRoom)
            }

            drawFit(c, heading, right, 44f, paint(fonts, 22f, palette.onPrimary, bold = true, align = Paint.Align.RIGHT), (right - left) * 0.45f)
            // No default subtitle any more: "RASID" under "INVOICE" said the
            // same word twice in two languages.
            if (subtitle.isNotBlank()) {
                PdfRtl.drawText(c, subtitle, right, 58f, paint(fonts, 8f, palette.onPrimaryMuted, align = Paint.Align.RIGHT))
            }
        }

        return 124f
    }

    /** Bill To (left) and Invoice Details (right, meta rows for number/date/due date). Returns the y position work should continue from. */
    fun drawBillToAndMeta(
        c: Canvas,
        context: Context,
        palette: Palette,
        fonts: Fonts,
        left: Float,
        right: Float,
        y: Float,
        invoice: Invoice
    ): Float {
        val labels = Labels(context)
        PdfRtl.drawText(c, labels.billTo, left, y, paint(fonts, 9f, palette.primary, bold = true))
        PdfRtl.drawText(c, invoice.customerName, left, y + 17f, paint(fonts, 13f, palette.ink, bold = true))
        invoice.customerPhone?.takeIf { it.isNotBlank() }?.let {
            PdfRtl.drawText(c, it, left, y + 31f, paint(fonts, 10f, palette.muted))
        }

        PdfRtl.drawText(c, labels.detailsFor(invoice.kind), right, y, paint(fonts, 9f, palette.primary, bold = true, align = Paint.Align.RIGHT))
        val metaLabel = paint(fonts, 10.5f, palette.muted, align = Paint.Align.RIGHT)
        val metaValue = paint(fonts, 10.5f, palette.ink, bold = true, mono = true, align = Paint.Align.RIGHT)
        var my = y + 17f
        fun metaRow(label: String, value: String) {
            PdfRtl.drawText(c, label, right - 104f, my, metaLabel)
            PdfRtl.drawText(c, value, right, my, metaValue)
            my += 14f
        }
        metaRow(labels.numberFor(invoice.kind), invoice.invoiceNumber)
        metaRow(labels.date, Format.dateOnly(invoice.invoiceDate))
        invoice.dueDate?.let { metaRow(labels.dueDate, Format.dateOnly(it)) }

        return maxOf(y + 46f, my + 8f)
    }

    /**
     * Table header + rows, an optional [extraColumn] inserted between the
     * item name and Qty. With [showPrices] false (a delivery challan,
     * [DocKind.showsPrices]) the Rate and Amount columns are not drawn at
     * all — the table ends at Qty — because a challan says what went out,
     * never what it costs. [pageBreakAt] is where a row no longer fits;
     * [onNewPage] starts a fresh page and must return its Canvas plus the y
     * to draw the header band at (typically 0f, since the caller usually
     * redraws the band itself first and passes its result). Returns the
     * canvas actually left open (may differ from the one passed in, if a
     * page break happened) and the final y position.
     */
    fun drawItemsTable(
        c: Canvas,
        context: Context,
        palette: Palette,
        fonts: Fonts,
        left: Float,
        right: Float,
        startY: Float,
        pageBreakAt: Float,
        items: List<InvoiceItem>,
        extraColumn: ExtraColumn?,
        showPrices: Boolean = true,
        onNewPage: () -> Pair<Canvas, Float>
    ): Pair<Canvas, Float> {
        var canvas = c
        var y = startY
        val rowH = 24f
        val xNo = left + 8f
        val xItem = left + 30f
        val xExtra = 300f
        // Without prices the quantity takes the amount's place at the right edge, so the table still ends at the margin.
        val xQty = if (!showPrices) right - 8f else if (extraColumn != null) 372f + 40f else 372f
        val xRate = if (extraColumn != null) 452f + 20f else 452f
        val xAmt = right - 8f
        val rulePaint = Paint().apply { color = palette.ruleColor; strokeWidth = 0.7f }
        val labels = Labels(context)

        fun tableHeader(atY: Float): Float {
            canvas.drawRect(left, atY, right, atY + 24f, solid(palette.tableHeaderFill ?: palette.ink))
            val th = paint(fonts, 10f, palette.onTableHeader, bold = true)
            val thR = paint(fonts, 10f, palette.onTableHeader, bold = true, align = Paint.Align.RIGHT)
            PdfRtl.drawText(canvas, "#", xNo, atY + 16f, th)
            PdfRtl.drawText(canvas, labels.item, xItem, atY + 16f, th)
            extraColumn?.let { PdfRtl.drawText(canvas, it.label.uppercase(), xExtra, atY + 16f, thR) }
            PdfRtl.drawText(canvas, labels.qty, xQty, atY + 16f, thR)
            if (showPrices) {
                PdfRtl.drawText(canvas, labels.rate, xRate, atY + 16f, thR)
                PdfRtl.drawText(canvas, labels.amount, xAmt, atY + 16f, thR)
            }
            return atY + 24f
        }

        y = tableHeader(y)

        val body = paint(fonts, 11f, palette.ink)
        val bodyR = paint(fonts, 11f, palette.ink, align = Paint.Align.RIGHT, mono = true)

        items.forEachIndexed { index, item ->
            if (y + rowH > pageBreakAt) {
                val fresh = onNewPage()
                canvas = fresh.first
                y = tableHeader(fresh.second)
            }
            if (index % 2 == 1) canvas.drawRect(left, y, right, y + rowH, solid(palette.zebra))
            val baseline = y + 16f
            PdfRtl.drawText(canvas, (index + 1).toString(), xNo, baseline, body)
            drawFit(canvas, item.itemName, xItem, baseline, body, (if (extraColumn != null) xExtra - 64f else xQty - 56f) - xItem)
            extraColumn?.let { PdfRtl.drawText(canvas, it.valueOf(item), xExtra, baseline, bodyR) }
            PdfRtl.drawText(canvas, Format.qty(item.quantity, item.unit), xQty, baseline, bodyR)
            if (showPrices) {
                PdfRtl.drawText(canvas, numberOnlyMoney(item.rate), xRate, baseline, bodyR)
                PdfRtl.drawText(canvas, numberOnlyMoney(item.lineTotal), xAmt, baseline, bodyR)
            }
            y += rowH
            canvas.drawLine(left, y, right, y, rulePaint)
        }

        return canvas to y
    }

    /**
     * Payment info (bank rows + QR, only when at least one is set) beside
     * the totals stack ending in a filled TOTAL bar, with Received/Balance
     * Due below it when a received amount was recorded. Returns the y
     * position work should continue from. A document that shows no prices
     * (a delivery challan, [DocKind.showsPrices]) draws none of it and
     * returns [blockTop] unchanged: no total, and no "how to pay" for a paper
     * that asks for no money.
     */
    fun drawPaymentAndTotals(
        c: Canvas,
        context: Context,
        palette: Palette,
        fonts: Fonts,
        left: Float,
        right: Float,
        blockTop: Float,
        invoice: Invoice,
        totals: InvoiceTotals
    ): Float {
        if (!DocKind.showsPrices(invoice.kind)) return blockTop
        val labels = Labels(context)
        // A credit note returns money: it carries no "how to pay" box and no payment QR (L120).
        val askingMoney = invoice.kind != DocKind.CREDIT_NOTE
        val bankRows = if (!askingMoney) emptyList() else listOfNotNull(
            BusinessProfile.bankName(context)?.let { labels.bank to it },
            BusinessProfile.bankAccountTitle(context)?.let { labels.accountTitle to it },
            BusinessProfile.bankIban(context)?.let { "IBAN" to it },
            BusinessProfile.bankJazzCash(context)?.let { (if (com.innovation313.roshankhata.data.ShopCountry.current == com.innovation313.roshankhata.data.ShopCountry.PAKISTAN) "JazzCash" else context.getString(R.string.pay_wallet_label)) to it }
        )

        val totalsW = 220f
        val totalsX = right - totalsW

        var ty = blockTop
        val tLabel = paint(fonts, 11f, palette.muted)
        val tValue = paint(fonts, 11f, palette.ink, align = Paint.Align.RIGHT, mono = true)
        fun totalRow(label: String, value: String) {
            PdfRtl.drawText(c, label, totalsX + 10f, ty + 12f, tLabel)
            PdfRtl.drawText(c, value, right - 10f, ty + 12f, tValue)
            ty += 17f
        }
        if (showSubtotal(totals)) totalRow(labels.subtotal, Format.money(totals.subtotal))
        if (totals.discountAmount > 0) {
            totalRow(invoice.discountLabel(labels.discount), "-" + Format.money(totals.discountAmount))
        }
        if (totals.taxAmount > 0) {
            totalRow("${labels.tax} (${Format.percent(invoice.taxPercent ?: 0.0)}%)", Format.money(totals.taxAmount))
        }
        if (totals.additionalCharge != 0.0) {
            // A round-off down is a minus line, never a missing one (M71).
            totalRow(invoice.additionalChargeLabel?.takeIf { it.isNotBlank() } ?: labels.extraCharges, Format.signedIfNegative(totals.additionalCharge))
        }

        ty += 5f
        val totalBar = RectF(totalsX, ty, right, ty + 30f)
        c.drawRoundRect(totalBar, 7f, 7f, solid(palette.primary))
        PdfRtl.drawText(c, labels.total, totalsX + 12f, ty + 20f, paint(fonts, 13f, palette.onTotalBar ?: palette.onPrimary, bold = true))
        PdfRtl.drawText(c, Format.money(totals.grandTotal), right - 12f, ty + 20f, paint(fonts, 14f, palette.onTotalBar ?: palette.onPrimary, bold = true, align = Paint.Align.RIGHT))
        ty += 40f

        if (invoice.receivedAmount != null) {
            totalRow(labels.received, Format.money(totals.received))
            PdfRtl.drawText(c, labels.balanceDue, totalsX + 10f, ty + 10f, paint(fonts, 10f, palette.muted, bold = true))
            // Its minus when more was received than the total (3 Nov 2026 audit, M04): an advance carried down a sales
            // order printed "Balance due Rs 5,000" where the shop owed the customer 5,000.
            PdfRtl.drawText(c, Format.signedIfNegative(totals.balanceDue), right - 10f, ty + 10f, paint(fonts, 10f, palette.ink, bold = true, mono = true, align = Paint.Align.RIGHT))
            ty += 17f
        }

        var by = blockTop
        val qr = if (askingMoney) BusinessProfile.loadQr(context) else null
        val boxRight = totalsX - 16f
        if (bankRows.isNotEmpty() || qr != null) {
            val qrSize = 56f
            val textRight = if (qr != null) boxRight - qrSize - 14f else boxRight - 12f
            // A long value (an account title) goes on a second line instead of over its label (L125).
            val valueP = paint(fonts, 10.5f, palette.ink, bold = true, mono = true, align = Paint.Align.RIGHT)
            val labelP = paint(fonts, 10.5f, palette.muted)
            val valueLines = bankRows.map { (label, value) ->
                val room = textRight - (left + 12f) - labelP.measureText(label) - 8f
                if (valueP.measureText(value) <= room) listOf(value)
                else wrapLines(value, valueP, textRight - (left + 12f), 2)
            }
            val textBoxH = 26f + valueLines.sumOf { 14 * it.size.coerceAtLeast(1) }
            val qrBoxH = if (qr != null) qrSize + 34f else 0f
            val boxH = maxOf(textBoxH, qrBoxH)

            c.drawRoundRect(RectF(left, blockTop, boxRight, blockTop + boxH), 8f, 8f, solid(palette.boxFill))
            PdfRtl.drawText(c, labels.paymentInfo, left + 12f, blockTop + 16f, paint(fonts, 9f, palette.primary, bold = true))
            var ry = blockTop + 32f
            bankRows.forEachIndexed { i, (label, _) ->
                PdfRtl.drawText(c, label, left + 12f, ry, labelP)
                val vl = valueLines[i]
                vl.forEachIndexed { k, line ->
                    PdfRtl.drawText(c, line, textRight, ry, valueP)
                    if (k < vl.size - 1) ry += 14f
                }
                ry += 14f
            }
            qr?.let {
                val qrLeft = boxRight - qrSize - 10f
                val qrTop = blockTop + 22f
                drawBitmapFit(c, it, RectF(qrLeft, qrTop, qrLeft + qrSize, qrTop + qrSize))
                PdfRtl.drawText(c, labels.scanToPay, qrLeft + qrSize / 2f, qrTop + qrSize + 10f, paint(fonts, 6.5f, palette.muted, align = Paint.Align.CENTER))
            }
            by = blockTop + boxH
        }

        return maxOf(by, ty) + 14f
    }

    /** The tinted "AMOUNT IN WORDS" strip. Returns the y position work should continue from. */
    fun drawAmountInWords(c: Canvas, context: Context, palette: Palette, fonts: Fonts, left: Float, right: Float, y: Float, grandTotal: Double): Float {
        c.drawRoundRect(RectF(left, y, right, y + 34f), 7f, 7f, solid(palette.boxFill))
        PdfRtl.drawText(c, Labels(context).amountInWords, left + 12f, y + 13f, paint(fonts, 8.5f, palette.primary, bold = true))
        val words = paint(fonts, 10.5f, palette.ink, italic = true)
        val lines = wrapLines(NumberWords.amountInWords(context, grandTotal), words, right - left - 24f, 3)
        val extra = (lines.size - 1).coerceAtLeast(0) * 12f
        if (extra > 0f) {
            c.drawRoundRect(RectF(left, y + 34f, right, y + 34f + extra), 0f, 0f, solid(palette.boxFill))
        }
        lines.forEachIndexed { i, line -> PdfRtl.drawText(c, line, left + 12f, y + 27f + i * 12f, words) }
        return y + 50f + extra
    }

    /**
     * Terms (left, per-invoice note + standing Terms & Conditions, only
     * when either is set) and the signature box (right, signature and/or
     * stamp side by side, each kept to its own aspect ratio). Returns the
     * y position work should continue from.
     */
    fun drawTermsAndSignature(
        c: Canvas,
        context: Context,
        palette: Palette,
        fonts: Fonts,
        left: Float,
        right: Float,
        y: Float,
        invoice: Invoice
    ): Float {
        val sigW = 168f
        val sigH = 80f
        val sigBox = RectF(right - sigW, y, right, y + sigH)
        c.drawRoundRect(sigBox, 7f, 7f, Paint().apply {
            isAntiAlias = true; color = palette.primary; style = Paint.Style.STROKE; strokeWidth = 0.8f
        })

        val imageBand = RectF(sigBox.left + 6f, y + 6f, sigBox.right - 6f, y + 48f)
        val signature = BusinessProfile.loadSignature(context)
        val stamp = BusinessProfile.loadStamp(context)
        when {
            signature != null && stamp != null -> {
                val gap = 4f
                val half = (imageBand.width() - gap) / 2f
                drawBitmapFit(c, signature, RectF(imageBand.left, imageBand.top, imageBand.left + half, imageBand.bottom))
                drawBitmapFit(c, stamp, RectF(imageBand.left + half + gap, imageBand.top, imageBand.right, imageBand.bottom))
            }
            signature != null -> drawBitmapFit(c, signature, imageBand)
            stamp != null -> drawBitmapFit(c, stamp, imageBand)
        }

        c.drawLine(sigBox.left + 20f, y + sigH - 24f, sigBox.right - 20f, y + sigH - 24f, Paint().apply { color = palette.ink; strokeWidth = 0.7f })
        PdfRtl.drawText(c, Labels(context).signatory, sigBox.centerX(), y + sigH - 10f, paint(fonts, 9f, palette.muted, align = Paint.Align.CENTER))

        var termsEnd = 0f
        val footerLines = listOfNotNull(invoice.note?.takeIf { it.isNotBlank() }, BusinessProfile.termsAndConditions(context))
        if (footerLines.isNotEmpty()) {
            PdfRtl.drawText(c, Labels(context).terms, left, y + 12f, paint(fonts, 8.5f, palette.primary, bold = true))
            var fy = y + 27f
            // Wrapped to the room left of the signature box, three lines each (M76).
            val termsP = paint(fonts, 9f, palette.muted)
            footerLines.forEach {
                wrapLines(it, termsP, right - sigW - 16f - left, 3).forEach { line ->
                    PdfRtl.drawText(c, line, left, fy, termsP)
                    fy += 12f
                }
            }
            termsEnd = fy
        }

        return maxOf(y + sigH, termsEnd) + 16f
    }

    /**
     * Word-wraps [text] into at most [maxLines] lines no wider than [maxW]; a word wider than the line is cut by
     * letters, and what does not fit the last line ends in an ellipsis. One helper for the documents' repeated
     * "one unwrapped line runs off the page" fault (8 Nov 2026 review, M76/M77/L124/L125).
     */
    fun wrapLines(text: String, p: Paint, maxW: Float, maxLines: Int): List<String> {
        val clean = text.replace('\n', ' ').trim()
        if (clean.isEmpty() || maxW <= 0f) return emptyList()
        if (p.measureText(clean) <= maxW) return listOf(clean)
        val lines = mutableListOf<String>()
        var line = ""
        fun clip(t: String): String {
            if (p.measureText(t) <= maxW) return t
            var e = t
            while (e.length > 1 && p.measureText(e + "\u2026") > maxW) e = e.dropLast(1)
            return e.trimEnd() + "\u2026"
        }
        for (word in clean.split(' ').filter { it.isNotEmpty() }) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (p.measureText(candidate) <= maxW) { line = candidate; continue }
            if (line.isNotEmpty()) { lines += line; line = "" }
            var w = word
            while (p.measureText(w) > maxW && w.length > 1 && lines.size < maxLines - 1) {
                var e = w.length
                while (e > 1 && p.measureText(w.take(e)) > maxW) e--
                lines += w.take(e); w = w.drop(e)
            }
            line = w
        }
        if (line.isNotEmpty()) lines += line
        if (lines.size > maxLines) {
            val head = lines.take(maxLines - 1)
            return head + clip(lines.drop(maxLines - 1).joinToString(" "))
        }
        return lines.map { clip(it) }
    }

    /** Draws one line that must stay within [maxW]: shrinks the paint down to 65% of its size, then clips with an ellipsis. */
    fun drawFit(c: Canvas, text: String, x: Float, y: Float, p: Paint, maxW: Float) {
        if (text.isEmpty() || maxW <= 0f) return
        val base = p.textSize
        while (p.measureText(text) > maxW && p.textSize > base * 0.65f) p.textSize -= 0.5f
        val shown = wrapLines(text, p, maxW, 1).firstOrNull() ?: return
        PdfRtl.drawText(c, shown, x, y, p)
        p.textSize = base
    }

    private fun numberOnlyMoney(value: Double): String = Format.money(value).removePrefix(Currency.symbol).trim()

    /** Draws [bmp] centred inside [box], scaled to fit without stretching. */
    fun drawBitmapFit(c: Canvas, bmp: Bitmap, box: RectF) {
        if (box.width() <= 0f || box.height() <= 0f) return
        val bmpAspect = bmp.width.toFloat() / bmp.height.toFloat()
        val boxAspect = box.width() / box.height()
        val w: Float
        val h: Float
        if (bmpAspect > boxAspect) {
            w = box.width(); h = w / bmpAspect
        } else {
            h = box.height(); w = h * bmpAspect
        }
        val left = box.centerX() - w / 2f
        val top = box.centerY() - h / 2f
        PdfRtl.drawBitmap(c, bmp, null, Rect(left.toInt(), top.toInt(), (left + w).toInt(), (top + h).toInt()), null)
    }
}
