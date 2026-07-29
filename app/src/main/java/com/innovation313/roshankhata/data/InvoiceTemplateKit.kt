package com.innovation313.roshankhata.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.NumberWords

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

    /** A template's own colour scheme. [gradient] false gives a flat header band instead of teal-into-tealEnd. */
    data class Palette(
        val ink: Int,
        val primary: Int,
        val primaryEnd: Int,
        val muted: Int,
        val ruleColor: Int,
        val boxFill: Int,
        val zebra: Int,
        val gradient: Boolean = true
    )

    /** A template's own fonts — headings/labels vs monospace for numbers and money, matching the spec's per-template font pairs. */
    data class Fonts(val heading: Typeface, val mono: Typeface, val monoBold: Typeface)

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
            bold -> Typeface.create(fonts.heading, Typeface.BOLD)
            italic -> Typeface.create(fonts.heading, Typeface.ITALIC)
            else -> fonts.heading
        }
    }

    fun solid(colour: Int): Paint = Paint().apply { isAntiAlias = true; color = colour }

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
        subtitle: String = "RASID",
        heading: String = "INVOICE"
    ): Float {
        val band = Paint().apply {
            isAntiAlias = true
            shader = if (palette.gradient) {
                LinearGradient(0f, 0f, pageW.toFloat(), 0f, palette.primary, palette.primaryEnd, Shader.TileMode.CLAMP)
            } else null
            if (!palette.gradient) color = palette.primary
        }
        c.drawRect(0f, 0f, pageW.toFloat(), 96f, band)

        val shopName = BusinessProfile.businessName(context)?.takeIf { it.isNotBlank() } ?: "Roshan Khata"

        val tile = RectF(left, 26f, left + 40f, 66f)
        c.drawRoundRect(tile, 9f, 9f, solid(0x33FFFFFF))
        val initials = shopName.trim().split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.take(1).uppercase() }
        c.drawText(
            initials.ifEmpty { "R" }, tile.centerX(), tile.centerY() + 5f,
            paint(fonts, 14f, Color.WHITE, bold = true, align = Paint.Align.CENTER)
        )

        c.drawText(shopName, left + 52f, 44f, paint(fonts, 16f, Color.WHITE, bold = true))
        BusinessProfile.businessAddress(context)?.let {
            c.drawText(it, left + 52f, 58f, paint(fonts, 9f, 0xFFD9EFEE.toInt()))
        }
        // Only for a shop registered for sales tax — blank prints nothing,
        // deliberately, since FBR's own guidance is that an invoice with no
        // STRN should not be charging sales tax in the first place.
        BusinessProfile.strn(context)?.let {
            c.drawText("STRN: $it", left + 52f, 70f, paint(fonts, 8f, 0xFFBEE3E1.toInt()))
        }

        c.drawText(heading, right, 44f, paint(fonts, 22f, Color.WHITE, bold = true, align = Paint.Align.RIGHT))
        c.drawText(subtitle, right, 58f, paint(fonts, 8f, 0xFFBEE3E1.toInt(), align = Paint.Align.RIGHT))

        return 124f
    }

    /** Bill To (left) and Invoice Details (right, meta rows for number/date/due date). Returns the y position work should continue from. */
    fun drawBillToAndMeta(
        c: Canvas,
        palette: Palette,
        fonts: Fonts,
        left: Float,
        right: Float,
        y: Float,
        invoice: Invoice
    ): Float {
        c.drawText("BILL TO", left, y, paint(fonts, 8f, palette.primary, bold = true))
        c.drawText(invoice.customerName, left, y + 17f, paint(fonts, 13f, palette.ink, bold = true))
        invoice.customerPhone?.takeIf { it.isNotBlank() }?.let {
            c.drawText(it, left, y + 31f, paint(fonts, 10f, palette.muted))
        }

        c.drawText("INVOICE DETAILS", right, y, paint(fonts, 8f, palette.primary, bold = true, align = Paint.Align.RIGHT))
        val metaLabel = paint(fonts, 9.5f, palette.muted, align = Paint.Align.RIGHT)
        val metaValue = paint(fonts, 9.5f, palette.ink, bold = true, mono = true, align = Paint.Align.RIGHT)
        var my = y + 17f
        fun metaRow(label: String, value: String) {
            c.drawText(label, right - 104f, my, metaLabel)
            c.drawText(value, right, my, metaValue)
            my += 14f
        }
        metaRow("Invoice No", invoice.invoiceNumber)
        metaRow("Tareekh", Format.dateOnly(invoice.invoiceDate))
        invoice.dueDate?.let { metaRow("Due Date", Format.dateOnly(it)) }

        return maxOf(y + 46f, my + 8f)
    }

    /**
     * Table header + rows, an optional [extraColumn] inserted between the
     * item name and Qty. [pageBreakAt] is where a row no longer fits;
     * [onNewPage] starts a fresh page and must return its Canvas plus the y
     * to draw the header band at (typically 0f, since the caller usually
     * redraws the band itself first and passes its result). Returns the
     * canvas actually left open (may differ from the one passed in, if a
     * page break happened) and the final y position.
     */
    fun drawItemsTable(
        c: Canvas,
        palette: Palette,
        fonts: Fonts,
        left: Float,
        right: Float,
        startY: Float,
        pageBreakAt: Float,
        items: List<InvoiceItem>,
        extraColumn: ExtraColumn?,
        onNewPage: () -> Pair<Canvas, Float>
    ): Pair<Canvas, Float> {
        var canvas = c
        var y = startY
        val rowH = 22f
        val xNo = left + 8f
        val xItem = left + 30f
        val xExtra = 300f
        val xQty = if (extraColumn != null) 372f + 40f else 372f
        val xRate = if (extraColumn != null) 452f + 20f else 452f
        val xAmt = right - 8f
        val rulePaint = Paint().apply { color = palette.ruleColor; strokeWidth = 0.7f }

        fun tableHeader(atY: Float): Float {
            canvas.drawRect(left, atY, right, atY + 20f, solid(palette.ink))
            val th = paint(fonts, 8f, Color.WHITE, bold = true)
            val thR = paint(fonts, 8f, Color.WHITE, bold = true, align = Paint.Align.RIGHT)
            canvas.drawText("#", xNo, atY + 13.5f, th)
            canvas.drawText("TAFSEEL", xItem, atY + 13.5f, th)
            extraColumn?.let { canvas.drawText(it.label.uppercase(), xExtra, atY + 13.5f, thR) }
            canvas.drawText("QTY", xQty, atY + 13.5f, thR)
            canvas.drawText("RATE", xRate, atY + 13.5f, thR)
            canvas.drawText("AMOUNT", xAmt, atY + 13.5f, thR)
            return atY + 20f
        }

        y = tableHeader(y)

        val body = paint(fonts, 10f, palette.ink)
        val bodyR = paint(fonts, 10f, palette.ink, align = Paint.Align.RIGHT, mono = true)

        items.forEachIndexed { index, item ->
            if (y + rowH > pageBreakAt) {
                val fresh = onNewPage()
                canvas = fresh.first
                y = tableHeader(fresh.second)
            }
            if (index % 2 == 1) canvas.drawRect(left, y, right, y + rowH, solid(palette.zebra))
            val baseline = y + 14.5f
            canvas.drawText((index + 1).toString(), xNo, baseline, body)
            canvas.drawText(item.itemName, xItem, baseline, body)
            extraColumn?.let { canvas.drawText(it.valueOf(item), xExtra, baseline, bodyR) }
            canvas.drawText(Format.qty(item.quantity, item.unit), xQty, baseline, bodyR)
            canvas.drawText(numberOnlyMoney(item.rate), xRate, baseline, bodyR)
            canvas.drawText(numberOnlyMoney(item.lineTotal), xAmt, baseline, bodyR)
            y += rowH
            canvas.drawLine(left, y, right, y, rulePaint)
        }

        return canvas to y
    }

    /**
     * Payment info (bank rows + QR, only when at least one is set) beside
     * the totals stack ending in a filled TOTAL bar, with Received/Balance
     * Due below it when a received amount was recorded. Returns the y
     * position work should continue from.
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
        val bankRows = listOfNotNull(
            BusinessProfile.bankName(context)?.let { "Bank" to it },
            BusinessProfile.bankAccountTitle(context)?.let { "Title" to it },
            BusinessProfile.bankIban(context)?.let { "IBAN" to it },
            BusinessProfile.bankJazzCash(context)?.let { "JazzCash" to it }
        )

        val totalsW = 220f
        val totalsX = right - totalsW

        var ty = blockTop
        val tLabel = paint(fonts, 10f, palette.muted)
        val tValue = paint(fonts, 10f, palette.ink, align = Paint.Align.RIGHT, mono = true)
        fun totalRow(label: String, value: String) {
            c.drawText(label, totalsX + 10f, ty + 12f, tLabel)
            c.drawText(value, right - 10f, ty + 12f, tValue)
            ty += 17f
        }
        totalRow("Sub Total", Format.money(totals.subtotal))
        if (totals.discountAmount > 0) {
            totalRow("Discount (${Format.plain(invoice.discountPercent ?: 0.0)}%)", "-" + Format.money(totals.discountAmount))
        }
        if (totals.taxAmount > 0) {
            totalRow("Tax (${Format.plain(invoice.taxPercent ?: 0.0)}%)", Format.money(totals.taxAmount))
        }
        if (totals.additionalCharge > 0) {
            totalRow(invoice.additionalChargeLabel?.takeIf { it.isNotBlank() } ?: "Additional Charges", Format.money(totals.additionalCharge))
        }

        ty += 5f
        val totalBar = RectF(totalsX, ty, right, ty + 30f)
        c.drawRoundRect(totalBar, 7f, 7f, solid(palette.primary))
        c.drawText("TOTAL", totalsX + 12f, ty + 20f, paint(fonts, 12f, Color.WHITE, bold = true))
        c.drawText(Format.money(totals.grandTotal), right - 12f, ty + 20f, paint(fonts, 13f, Color.WHITE, bold = true, align = Paint.Align.RIGHT))
        ty += 40f

        if (invoice.receivedAmount != null) {
            totalRow("Received", Format.money(totals.received))
            c.drawText("BALANCE DUE", totalsX + 10f, ty + 10f, paint(fonts, 10f, palette.muted, bold = true))
            c.drawText(Format.money(totals.balanceDue), right - 10f, ty + 10f, paint(fonts, 10f, palette.ink, bold = true, mono = true, align = Paint.Align.RIGHT))
            ty += 17f
        }

        var by = blockTop
        val qr = BusinessProfile.loadQr(context)
        val boxRight = totalsX - 16f
        if (bankRows.isNotEmpty() || qr != null) {
            val qrSize = 56f
            val textRight = if (qr != null) boxRight - qrSize - 14f else boxRight - 12f
            val textBoxH = 26f + bankRows.size * 14f
            val qrBoxH = if (qr != null) qrSize + 34f else 0f
            val boxH = maxOf(textBoxH, qrBoxH)

            c.drawRoundRect(RectF(left, blockTop, boxRight, blockTop + boxH), 8f, 8f, solid(palette.boxFill))
            c.drawText("PAYMENT INFO", left + 12f, blockTop + 16f, paint(fonts, 8f, palette.primary, bold = true))
            var ry = blockTop + 32f
            bankRows.forEach { (label, value) ->
                c.drawText(label, left + 12f, ry, paint(fonts, 9.5f, palette.muted))
                c.drawText(value, textRight, ry, paint(fonts, 9.5f, palette.ink, bold = true, mono = true, align = Paint.Align.RIGHT))
                ry += 14f
            }
            qr?.let {
                val qrLeft = boxRight - qrSize - 10f
                val qrTop = blockTop + 22f
                c.drawBitmap(it, null, RectF(qrLeft, qrTop, qrLeft + qrSize, qrTop + qrSize), null)
                c.drawText("Scan to Pay", qrLeft + qrSize / 2f, qrTop + qrSize + 10f, paint(fonts, 6.5f, palette.muted, align = Paint.Align.CENTER))
            }
            by = blockTop + boxH
        }

        return maxOf(by, ty) + 14f
    }

    /** The tinted "AMOUNT IN WORDS" strip. Returns the y position work should continue from. */
    fun drawAmountInWords(c: Canvas, palette: Palette, fonts: Fonts, left: Float, right: Float, y: Float, grandTotal: Double): Float {
        c.drawRoundRect(RectF(left, y, right, y + 34f), 7f, 7f, solid(palette.boxFill))
        c.drawText("AMOUNT IN WORDS", left + 12f, y + 13f, paint(fonts, 7.5f, palette.primary, bold = true))
        c.drawText(NumberWords.rupeesInWords(grandTotal), left + 12f, y + 27f, paint(fonts, 10f, palette.ink, italic = true))
        return y + 50f
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
        c.drawText("Authorized Signatory", sigBox.centerX(), y + sigH - 10f, paint(fonts, 8.5f, palette.muted, align = Paint.Align.CENTER))

        val footerLines = listOfNotNull(invoice.note?.takeIf { it.isNotBlank() }, BusinessProfile.termsAndConditions(context))
        if (footerLines.isNotEmpty()) {
            c.drawText("TERMS", left, y + 12f, paint(fonts, 7.5f, palette.primary, bold = true))
            var fy = y + 27f
            footerLines.forEach {
                c.drawText(it, left, fy, paint(fonts, 8.5f, palette.muted))
                fy += 12f
            }
        }

        return y + sigH + 16f
    }

    private fun numberOnlyMoney(value: Double): String = Format.money(value).removePrefix("Rs ")

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
        c.drawBitmap(bmp, null, Rect(left.toInt(), top.toInt(), (left + w).toInt(), (top + h).toInt()), null)
    }
}
