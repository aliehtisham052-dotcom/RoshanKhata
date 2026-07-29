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
import android.graphics.pdf.PdfDocument
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.NumberWords
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders a saved [Invoice] as a printable PDF, in whichever of the ten
 * finalised template designs the invoice was made with. Two so far — see
 * the class doc on [Invoice] for why a template is a print choice only and
 * touches nothing about the ledger.
 *
 * Same mechanism as [PdfExport] (native android.graphics.pdf.PdfDocument,
 * hand-drawn on a Canvas, no external library), on purpose: one PDF engine
 * in this app, not two.
 *
 * The finalised HTML/CSS mockups use web fonts this app does not bundle
 * (Sora, Fraunces, IBM Plex Mono, Playfair Display). Android's own
 * sans-serif/serif/monospace families stand in for them here — chosen to
 * keep each template's actual character (T1's bold grotesque headings, T10's
 * monospace receipt feel), not to match the mockup pixel for pixel.
 */
object InvoicePdfExport {

    private const val PAGE_W_A4 = 595
    private const val PAGE_H_A4 = 842
    private const val MARGIN = 40f

    private val timeFmt = SimpleDateFormat("h:mm a", Locale.ENGLISH)

    /**
     * A money figure with thousand separators but no "Rs" — the mockups'
     * own convention for a line item (the currency is established once, at
     * the totals), reusing [Format.money]'s rounding rule rather than a
     * second one that could drift from it.
     */
    private fun numberOnly(value: Double): String = Format.money(value).removePrefix("Rs ")

    /**
     * Draws [bmp] centred inside [box], scaled to fit without stretching —
     * a signature is naturally wide and short, a stamp closer to square,
     * and forcing either into a shape it isn't is what made an earlier
     * version of this look cramped and distorted.
     */
    private fun drawBitmapFit(c: Canvas, bmp: Bitmap, box: RectF) {
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
        c.drawBitmap(
            bmp, null,
            Rect(left.toInt(), top.toInt(), (left + w).toInt(), (top + h).toInt()),
            null
        )
    }

    fun build(context: Context, invoice: Invoice, items: List<InvoiceItem>): File? {
        return when (invoice.templateId) {
            10 -> buildThermalReceipt(context, invoice, items)
            else -> buildTealCorporate(context, invoice, items)
        }
    }

    /**
     * A live preview while filling in the form — the owner asked for this
     * after comparing it to the Dukan Card screen, where typing updates the
     * design as you go.
     *
     * Deliberately not a second drawing routine: this renders the SAME PDF
     * [build] would produce and rasterises its first page with
     * [android.graphics.pdf.PdfRenderer]. Two drawing implementations for
     * one template is exactly the kind of drift this app's own history
     * warns about (the Zakat screen computing the same figure two ways and
     * disagreeing) — here there is only ever one, so the preview cannot show
     * something the actual PDF would not.
     *
     * @return null on any failure — a blank preview area is the correct
     *         result of nothing typed yet, not a crash.
     */
    fun renderPreviewBitmap(context: Context, invoice: Invoice, items: List<InvoiceItem>): android.graphics.Bitmap? {
        val file = build(context, invoice, items) ?: return null
        return try {
            android.os.ParcelFileDescriptor.open(
                file, android.os.ParcelFileDescriptor.MODE_READ_ONLY
            ).use { pfd ->
                android.graphics.pdf.PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount == 0) return null
                    renderer.openPage(0).use { page ->
                        // 2x the PDF's own point size — sharp enough on a phone
                        // screen without rendering something absurdly large for
                        // what is, after all, just a preview.
                        val bmp = android.graphics.Bitmap.createBitmap(
                            page.width * 2, page.height * 2, android.graphics.Bitmap.Config.ARGB_8888
                        )
                        bmp.eraseColor(Color.WHITE)
                        page.render(
                            bmp, null, null,
                            android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                        )
                        bmp
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun outputFile(context: Context, invoice: Invoice): File {
        val dir = File(context.cacheDir, "invoices").apply { mkdirs() }
        val safeName = invoice.customerName.replace(Regex("[^A-Za-z0-9 ]"), "").trim().replace(" ", "_")
        return File(dir, "${invoice.invoiceNumber}_${safeName.ifEmpty { "Customer" }}.pdf")
    }

    // ==================== T1 — Teal Corporate ====================

    /**
     * T1 "Teal Corporate" — the full-featured design, redrawn to the
     * finalised mockup rather than approximated: a gradient header band with
     * the shop monogram, Bill To and Invoice Details as two labelled
     * columns, a real items table with a dark header row and zebra rows, a
     * payment-info box beside a totals stack ending in a filled TOTAL bar,
     * amount-in-words in its own tinted strip, and a bordered signature box.
     *
     * Palette is the mockup's own (see the invoice-templates spec): #0F2A2A
     * ink, #0C6B6B primary into #12908C, #F1F8F7 box fill, #E4EDEC rules.
     */
    private fun buildTealCorporate(context: Context, invoice: Invoice, items: List<InvoiceItem>): File? {
        val doc = PdfDocument()

        val ink = 0xFF0F2A2A.toInt()
        val teal = 0xFF0C6B6B.toInt()
        val tealEnd = 0xFF12908C.toInt()
        val muted = 0xFF6A7A79.toInt()
        val ruleCol = 0xFFE4EDEC.toInt()
        val boxFill = 0xFFF1F8F7.toInt()
        val zebra = 0xFFF9FCFC.toInt()

        fun text(
            size: Float,
            colour: Int,
            bold: Boolean = false,
            align: Paint.Align = Paint.Align.LEFT,
            mono: Boolean = false,
            italic: Boolean = false
        ) = Paint().apply {
            isAntiAlias = true
            textSize = size
            color = colour
            textAlign = align
            typeface = Typeface.create(
                if (mono) Typeface.MONOSPACE else Typeface.DEFAULT,
                when {
                    bold -> Typeface.BOLD
                    italic -> Typeface.ITALIC
                    else -> Typeface.NORMAL
                }
            )
        }

        fun solid(colour: Int) = Paint().apply { isAntiAlias = true; color = colour }

        val left = MARGIN
        val right = PAGE_W_A4 - MARGIN

        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W_A4, PAGE_H_A4, pageNo).create())
        var c = page.canvas

        fun drawBand(): Float {
            val band = Paint().apply {
                isAntiAlias = true
                shader = LinearGradient(
                    0f, 0f, PAGE_W_A4.toFloat(), 0f, teal, tealEnd, Shader.TileMode.CLAMP
                )
            }
            c.drawRect(0f, 0f, PAGE_W_A4.toFloat(), 96f, band)

            val shopName = BusinessProfile.businessName(context)?.takeIf { it.isNotBlank() }
                ?: "Roshan Khata"

            // The monogram tile the mockup opens with — initials of the shop
            // name, so a shop with no logo image still gets a mark.
            val tile = RectF(left, 26f, left + 40f, 66f)
            c.drawRoundRect(tile, 9f, 9f, solid(0x33FFFFFF))
            val initials = shopName.trim().split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
                .take(2)
                .joinToString("") { it.take(1).uppercase() }
            c.drawText(
                initials.ifEmpty { "R" }, tile.centerX(), tile.centerY() + 5f,
                text(14f, Color.WHITE, bold = true, align = Paint.Align.CENTER)
            )

            c.drawText(shopName, left + 52f, 44f, text(16f, Color.WHITE, bold = true))
            BusinessProfile.businessAddress(context)?.let {
                c.drawText(it, left + 52f, 58f, text(9f, 0xFFD9EFEE.toInt()))
            }
            // Only for a shop registered for sales tax — blank prints nothing,
            // deliberately, since FBR's own guidance is that an invoice with
            // no STRN should not be charging sales tax in the first place.
            BusinessProfile.strn(context)?.let {
                c.drawText("STRN: $it", left + 52f, 70f, text(8f, 0xFFBEE3E1.toInt()))
            }

            c.drawText("INVOICE", right, 44f, text(22f, Color.WHITE, bold = true, align = Paint.Align.RIGHT))
            c.drawText("RASID", right, 58f, text(8f, 0xFFBEE3E1.toInt(), align = Paint.Align.RIGHT))

            return 124f
        }

        var y = drawBand()

        // ---- Bill To (left) and Invoice Details (right), two columns ----
        c.drawText("BILL TO", left, y, text(8f, teal, bold = true))
        c.drawText(invoice.customerName, left, y + 17f, text(13f, ink, bold = true))
        invoice.customerPhone?.takeIf { it.isNotBlank() }?.let {
            c.drawText(it, left, y + 31f, text(10f, muted))
        }

        c.drawText("INVOICE DETAILS", right, y, text(8f, teal, bold = true, align = Paint.Align.RIGHT))
        val metaLabel = text(9.5f, muted, align = Paint.Align.RIGHT)
        val metaValue = text(9.5f, ink, bold = true, mono = true, align = Paint.Align.RIGHT)
        var my = y + 17f
        fun metaRow(label: String, value: String) {
            c.drawText(label, right - 104f, my, metaLabel)
            c.drawText(value, right, my, metaValue)
            my += 14f
        }
        metaRow("Invoice No", invoice.invoiceNumber)
        metaRow("Tareekh", Format.dateOnly(invoice.invoiceDate))
        invoice.dueDate?.let { metaRow("Due Date", Format.dateOnly(it)) }

        y = maxOf(y + 46f, my + 8f)

        // ---- Items table ----
        val rowH = 22f
        val xNo = left + 8f
        val xItem = left + 30f
        val xQty = 372f
        val xRate = 452f
        val xAmt = right - 8f
        val rulePaint = Paint().apply { color = ruleCol; strokeWidth = 0.7f }

        fun tableHeader(atY: Float): Float {
            c.drawRect(left, atY, right, atY + 20f, solid(ink))
            val th = text(8f, Color.WHITE, bold = true)
            val thR = text(8f, Color.WHITE, bold = true, align = Paint.Align.RIGHT)
            c.drawText("#", xNo, atY + 13.5f, th)
            c.drawText("TAFSEEL", xItem, atY + 13.5f, th)
            c.drawText("QTY", xQty, atY + 13.5f, thR)
            c.drawText("RATE", xRate, atY + 13.5f, thR)
            c.drawText("AMOUNT", xAmt, atY + 13.5f, thR)
            return atY + 20f
        }

        fun newPage() {
            doc.finishPage(page)
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W_A4, PAGE_H_A4, pageNo).create())
            c = page.canvas
        }

        y = tableHeader(y)

        val body = text(10f, ink)
        val bodyR = text(10f, ink, align = Paint.Align.RIGHT, mono = true)

        items.forEachIndexed { index, item ->
            if (y + rowH > PAGE_H_A4 - 250f) {
                newPage()
                y = tableHeader(drawBand())
            }
            if (index % 2 == 1) c.drawRect(left, y, right, y + rowH, solid(zebra))
            val baseline = y + 14.5f
            c.drawText((index + 1).toString(), xNo, baseline, body)
            c.drawText(item.itemName, xItem, baseline, body)
            c.drawText(Format.qty(item.quantity, item.unit), xQty, baseline, bodyR)
            c.drawText(numberOnly(item.rate), xRate, baseline, bodyR)
            c.drawText(numberOnly(item.lineTotal), xAmt, baseline, bodyR)
            y += rowH
            c.drawLine(left, y, right, y, rulePaint)
        }

        y += 18f

        // ---- Payment info box (left) beside the totals stack (right) ----
        val totals = InvoiceMath.totals(items, invoice.discountPercent, invoice.taxPercent, invoice.additionalChargeAmount, invoice.receivedAmount)
        val bankRows = listOfNotNull(
            BusinessProfile.bankName(context)?.let { "Bank" to it },
            BusinessProfile.bankAccountTitle(context)?.let { "Title" to it },
            BusinessProfile.bankIban(context)?.let { "IBAN" to it },
            BusinessProfile.bankJazzCash(context)?.let { "JazzCash" to it }
        )

        if (y > PAGE_H_A4 - 240f) {
            newPage()
            y = drawBand()
        }

        val blockTop = y
        val totalsW = 220f
        val totalsX = right - totalsW

        var ty = blockTop
        val tLabel = text(10f, muted)
        val tValue = text(10f, ink, align = Paint.Align.RIGHT, mono = true)
        fun totalRow(label: String, value: String) {
            c.drawText(label, totalsX + 10f, ty + 12f, tLabel)
            c.drawText(value, right - 10f, ty + 12f, tValue)
            ty += 17f
        }
        totalRow("Sub Total", Format.money(totals.subtotal))
        if (totals.discountAmount > 0) {
            totalRow(
                "Discount (${Format.plain(invoice.discountPercent ?: 0.0)}%)",
                "-" + Format.money(totals.discountAmount)
            )
        }
        if (totals.taxAmount > 0) {
            totalRow(
                "Tax (${Format.plain(invoice.taxPercent ?: 0.0)}%)",
                Format.money(totals.taxAmount)
            )
        }
        if (totals.additionalCharge > 0) {
            totalRow(invoice.additionalChargeLabel?.takeIf { it.isNotBlank() } ?: "Additional Charges", Format.money(totals.additionalCharge))
        }

        ty += 5f
        val totalBar = RectF(totalsX, ty, right, ty + 30f)
        c.drawRoundRect(totalBar, 7f, 7f, solid(teal))
        c.drawText("TOTAL", totalsX + 12f, ty + 20f, text(12f, Color.WHITE, bold = true))
        c.drawText(
            Format.money(totals.grandTotal), right - 12f, ty + 20f,
            text(13f, Color.WHITE, bold = true, align = Paint.Align.RIGHT)
        )
        ty += 40f

        // Received / Balance Due — only when the owner actually recorded a
        // received amount. An invoice with nothing recorded should not
        // print "Balance Due: Rs 0" and read as settled when it is simply
        // unrecorded.
        if (invoice.receivedAmount != null) {
            totalRow("Received", Format.money(totals.received))
            c.drawText("BALANCE DUE", totalsX + 10f, ty + 10f, Paint(tLabel).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) })
            c.drawText(Format.money(totals.balanceDue), right - 10f, ty + 10f, Paint(tValue).apply { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) })
            ty += 17f
        }

        var by = blockTop
        val qr = BusinessProfile.loadQr(context)
        val boxRight = totalsX - 16f
        // A JazzCash/EasyPaisa QR with no formal bank account is the common
        // case for a small shop — the box earns its place from either half
        // being present, not only from bank fields.
        if (bankRows.isNotEmpty() || qr != null) {
            val qrSize = 56f
            val textRight = if (qr != null) boxRight - qrSize - 14f else boxRight - 12f
            val textBoxH = 26f + bankRows.size * 14f
            val qrBoxH = if (qr != null) qrSize + 34f else 0f
            val boxH = maxOf(textBoxH, qrBoxH)

            c.drawRoundRect(RectF(left, blockTop, boxRight, blockTop + boxH), 8f, 8f, solid(boxFill))
            c.drawText("PAYMENT INFO", left + 12f, blockTop + 16f, text(8f, teal, bold = true))
            var ry = blockTop + 32f
            bankRows.forEach { (label, value) ->
                c.drawText(label, left + 12f, ry, text(9.5f, muted))
                c.drawText(
                    value, textRight, ry,
                    text(9.5f, ink, bold = true, mono = true, align = Paint.Align.RIGHT)
                )
                ry += 14f
            }
            qr?.let {
                val qrLeft = boxRight - qrSize - 10f
                val qrTop = blockTop + 22f
                c.drawBitmap(it, null, RectF(qrLeft, qrTop, qrLeft + qrSize, qrTop + qrSize), null)
                c.drawText(
                    "Scan to Pay", qrLeft + qrSize / 2f, qrTop + qrSize + 10f,
                    text(6.5f, muted, align = Paint.Align.CENTER)
                )
            }
            by = blockTop + boxH
        }

        y = maxOf(by, ty) + 14f

        // ---- Amount in words, its own strip ----
        c.drawRoundRect(RectF(left, y, right, y + 34f), 7f, 7f, solid(boxFill))
        c.drawText("AMOUNT IN WORDS", left + 12f, y + 13f, text(7.5f, teal, bold = true))
        c.drawText(
            NumberWords.rupeesInWords(totals.grandTotal), left + 12f, y + 27f,
            text(10f, ink, italic = true)
        )
        y += 50f

        // ---- Terms (left) and the signature box (right) ----
        if (y > PAGE_H_A4 - 140f) {
            newPage()
            y = drawBand()
        }

        val sigW = 168f
        val sigH = 80f
        val sigBox = RectF(right - sigW, y, right, y + sigH)
        c.drawRoundRect(sigBox, 7f, 7f, Paint().apply {
            isAntiAlias = true
            color = teal
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        })

        // Signature and stamp side by side, each kept to its own aspect
        // ratio rather than squashed into a square — a signature is
        // naturally wide and short, a stamp closer to square, and forcing
        // either into the wrong shape is what made the earlier stamp-only
        // version look cramped.
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

        c.drawLine(
            sigBox.left + 20f, y + sigH - 24f, sigBox.right - 20f, y + sigH - 24f,
            Paint().apply { color = ink; strokeWidth = 0.7f }
        )
        c.drawText(
            "Authorized Signatory", sigBox.centerX(), y + sigH - 10f,
            text(8.5f, muted, align = Paint.Align.CENTER)
        )

        val footerLines = listOfNotNull(
            invoice.note?.takeIf { it.isNotBlank() },
            BusinessProfile.termsAndConditions(context)
        )
        if (footerLines.isNotEmpty()) {
            c.drawText("TERMS", left, y + 12f, text(7.5f, teal, bold = true))
            var fy = y + 27f
            footerLines.forEach {
                c.drawText(it, left, fy, text(8.5f, muted))
                fy += 12f
            }
        }

        y += sigH + 16f

        val finalState = drawMakerStrip(context, doc, page, c, y)
        page = finalState.first
        c = finalState.second

        return writeAndClose(doc, outputFile(context, invoice))
    }

    // ==================== T10 — Thermal Receipt ====================

    /**
     * The narrow 80mm-style receipt: monospace, centred, dashed dividers.
     * No bank box (a thermal counter sale is cash-in-hand, not invoiced to
     * an account) — discount and tax lines still show when set.
     */
    private fun buildThermalReceipt(context: Context, invoice: Invoice, items: List<InvoiceItem>): File? {
        val pageW = 226 // ~80mm at 72dpi
        val pad = 16f

        val totals = InvoiceMath.totals(items, invoice.discountPercent, invoice.taxPercent, invoice.additionalChargeAmount, invoice.receivedAmount)

        // Height is computed, not guessed — a thermal roll has no fixed page,
        // and the alternative (a fixed too-short page) would silently cut the
        // receipt off mid-item.
        var estimatedH = 210f
        estimatedH += items.size * 26f
        if (BusinessProfile.strn(context) != null) estimatedH += 11f
        if (invoice.dueDate != null) estimatedH += 13f
        if (totals.discountAmount > 0) estimatedH += 12f
        if (totals.taxAmount > 0) estimatedH += 12f
        if (totals.additionalCharge > 0) estimatedH += 12f
        if (invoice.receivedAmount != null) estimatedH += 24f
        if (BusinessProfile.loadSignature(context) != null) estimatedH += 36f
        // Amount-in-words wraps across roughly two lines for a typical
        // total; a generous fixed budget here is safer than measuring the
        // exact wrap in advance, given the page height has to be decided
        // before any drawing happens.
        estimatedH += 45f
        val bankCount = listOfNotNull(
            BusinessProfile.bankName(context), BusinessProfile.bankAccountTitle(context),
            BusinessProfile.bankIban(context), BusinessProfile.bankJazzCash(context)
        ).size
        if (bankCount > 0 || BusinessProfile.hasQr(context)) {
            estimatedH += 35f + bankCount * 12f
            if (BusinessProfile.hasQr(context)) estimatedH += 70f
        }
        val footerLineCount = listOfNotNull(invoice.note, BusinessProfile.termsAndConditions(context)).size
        if (footerLineCount > 0) estimatedH += 32f + footerLineCount * 24f
        estimatedH += 20f // the added maker's-mark line at the very end
        val pageH = estimatedH.toInt().coerceAtLeast(400)

        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, 1).create())
        val c = page.canvas

        val center = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER; color = Color.BLACK
        }
        val shopName = Paint(center).apply {
            textSize = 12f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val shopSub = Paint(center).apply { textSize = 8f; color = 0xFF777777.toInt() }
        val mono = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE; textSize = 9f; color = Color.BLACK
        }
        val monoR = Paint(mono).apply { textAlign = Paint.Align.RIGHT }
        val monoGrey = Paint(mono).apply { color = 0xFF555555.toInt() }
        val monoGreyR = Paint(monoGrey).apply { textAlign = Paint.Align.RIGHT }
        val monoBold = Paint(mono).apply { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
        val grandPaint = Paint(mono).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); textSize = 11f
        }
        val grandPaintR = Paint(grandPaint).apply { textAlign = Paint.Align.RIGHT }
        val dash = Paint().apply {
            color = 0xFFBBBBBB.toInt(); strokeWidth = 0.7f
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(2f, 2f), 0f)
        }

        val cx = pageW / 2f
        var y = 22f

        c.drawText(BusinessProfile.businessName(context)?.takeIf { it.isNotBlank() } ?: "Roshan Khata", cx, y, shopName)
        BusinessProfile.businessAddress(context)?.let {
            y += 12f
            c.drawText(it, cx, y, shopSub)
        }
        BusinessProfile.strn(context)?.let {
            y += 11f
            c.drawText("STRN: $it", cx, y, shopSub)
        }

        y += 16f
        c.drawLine(pad, y, pageW - pad, y, dash)
        y += 16f

        c.drawText("Rasid #:", pad, y, mono)
        c.drawText(invoice.invoiceNumber, pageW - pad, y, monoR)
        y += 13f
        c.drawText("Tareekh:", pad, y, mono)
        c.drawText(Format.dateOnly(invoice.invoiceDate), pageW - pad, y, monoR)
        invoice.dueDate?.let {
            y += 13f
            c.drawText("Due Date:", pad, y, mono)
            c.drawText(Format.dateOnly(it), pageW - pad, y, monoR)
        }
        y += 13f
        c.drawText("Waqt:", pad, y, mono)
        c.drawText(timeFmt.format(Date(invoice.createdAt)), pageW - pad, y, monoR)

        y += 16f
        c.drawLine(pad, y, pageW - pad, y, dash)
        y += 16f

        items.forEach { item ->
            c.drawText(item.itemName, pad, y, monoBold)
            y += 11f
            c.drawText(
                "${Format.qty(item.quantity, item.unit)} × ${numberOnly(item.rate)}",
                pad, y, monoGrey
            )
            c.drawText(numberOnly(item.lineTotal), pageW - pad, y, monoGreyR)
            y += 15f
        }

        c.drawLine(pad, y, pageW - pad, y, dash)
        y += 16f

        c.drawText("Sub Total", pad, y, mono)
        c.drawText(numberOnly(totals.subtotal), pageW - pad, y, monoR)
        y += 13f
        if (totals.discountAmount > 0) {
            c.drawText("Discount", pad, y, mono)
            c.drawText("-" + numberOnly(totals.discountAmount), pageW - pad, y, monoR)
            y += 13f
        }
        if (totals.taxAmount > 0) {
            c.drawText("Tax", pad, y, mono)
            c.drawText(numberOnly(totals.taxAmount), pageW - pad, y, monoR)
            y += 13f
        }
        if (totals.additionalCharge > 0) {
            c.drawText(invoice.additionalChargeLabel?.takeIf { it.isNotBlank() } ?: "Extra", pad, y, mono)
            c.drawText(numberOnly(totals.additionalCharge), pageW - pad, y, monoR)
            y += 13f
        }
        y += 4f
        c.drawLine(pad, y, pageW - pad, y, dash)
        y += 15f
        c.drawText("TOTAL", pad, y, grandPaint)
        c.drawText(Format.money(totals.grandTotal), pageW - pad, y, grandPaintR)
        y += 12f
        if (invoice.receivedAmount != null) {
            c.drawText("Received", pad, y, mono)
            c.drawText(numberOnly(totals.received), pageW - pad, y, monoR)
            y += 13f
            c.drawText("Balance Due", pad, y, monoBold)
            c.drawText(numberOnly(totals.balanceDue), pageW - pad, y, Paint(monoR).apply { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) })
            y += 12f
        }
        c.drawLine(pad, y, pageW - pad, y, dash)
        y += 16f
        c.drawText("Rupay Alfaaz Mein:", pad, y, Paint(mono).apply { textSize = 8f; color = 0xFF555555.toInt() })
        y += 11f
        // Wrapped by hand, not left to overflow the receipt's own width —
        // the words for a large total are routinely longer than 226pt fits
        // on one line, unlike every other value on this narrow page.
        y = wrapMonoText(c, NumberWords.rupeesInWords(totals.grandTotal), pad, y, pageW - 2 * pad, 9f)

        val bank = listOfNotNull(
            BusinessProfile.bankName(context)?.let { "Bank" to it },
            BusinessProfile.bankAccountTitle(context)?.let { "Title" to it },
            BusinessProfile.bankIban(context)?.let { "IBAN" to it },
            BusinessProfile.bankJazzCash(context)?.let { "JazzCash" to it }
        )
        val qr = BusinessProfile.loadQr(context)
        if (bank.isNotEmpty() || qr != null) {
            y += 6f
            c.drawLine(pad, y, pageW - pad, y, dash)
            y += 16f
            c.drawText("Payment Info", cx, y, Paint(shopSub).apply { color = Color.BLACK; isFakeBoldText = true })
            y += 13f
            bank.forEach { (label, value) ->
                c.drawText(label, pad, y, monoGrey)
                c.drawText(value, pageW - pad, y, monoGreyR)
                y += 12f
            }
            qr?.let {
                y += 4f
                val qrSize = 64f
                drawBitmapFit(c, it, RectF(cx - qrSize / 2f, y, cx + qrSize / 2f, y + qrSize))
                y += qrSize + 6f
            }
        }

        val footerLines = listOfNotNull(
            invoice.note?.takeIf { it.isNotBlank() },
            BusinessProfile.termsAndConditions(context)
        )
        if (footerLines.isNotEmpty()) {
            y += 6f
            c.drawLine(pad, y, pageW - pad, y, dash)
            y += 14f
            c.drawText("Terms", pad, y, Paint(mono).apply { textSize = 8f; isFakeBoldText = true })
            y += 12f
            footerLines.forEach {
                y = wrapMonoText(c, it, pad, y, pageW - 2 * pad, 8f)
                y += 3f
            }
        }

        y += 6f
        val availableW = pageW - 2 * pad
        BusinessProfile.loadSignature(context)?.let { signature ->
            val h = 30f
            drawBitmapFit(c, signature, RectF(pad, y, pageW - pad, y + h))
            y += h + 6f
        }
        BusinessProfile.loadStamp(context)?.let { stamp ->
            val h = 40f
            drawBitmapFit(c, stamp, RectF(cx - availableW / 4f, y, cx + availableW / 4f, y + h))
            y += h + 10f
        }

        c.drawLine(pad, y, pageW - pad, y, dash)
        y += 16f
        c.drawText("Shukriya!", cx, y, shopSub)
        y += 11f
        c.drawText("Dobara tashreef layein", cx, y, shopSub)
        y += 16f
        c.drawText("Roshan Khata — Har Hisaab Roshan", cx, y, Paint(shopSub).apply { textSize = 6.5f })

        doc.finishPage(page)
        return writeAndClose(doc, outputFile(context, invoice))
    }

    /**
     * Word-wraps [text] across lines no wider than [maxWidth], left-aligned
     * from [x] — for the thermal receipt's own narrow page, the one place
     * on this template where a value routinely needs more than one line,
     * unlike every fixed short label/value pair elsewhere on the receipt.
     * Returns the y position after the last line drawn.
     */
    private fun wrapMonoText(c: Canvas, text: String, x: Float, startY: Float, maxWidth: Float, size: Float): Float {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE; textSize = size; color = 0xFF333333.toInt()
        }
        var y = startY
        val words = text.split(" ")
        var line = StringBuilder()
        for (word in words) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                c.drawText(line.toString(), x, y, paint)
                y += size + 3f
                line = StringBuilder(word)
            } else {
                line = StringBuilder(candidate)
            }
        }
        if (line.isNotEmpty()) {
            c.drawText(line.toString(), x, y, paint)
            y += size + 3f
        }
        return y
    }

    // ==================== Shared ====================

    /**
     * The same maker's strip [PdfExport] carries — one mark, one place it is
     * built, so the two renderers cannot drift into two different footers.
     * Returns the page/canvas actually left open, since a strip that does
     * not fit starts one more page than the caller had.
     */
    private fun drawMakerStrip(
        context: Context,
        doc: PdfDocument,
        pageIn: PdfDocument.Page,
        canvasIn: Canvas,
        yIn: Float
    ): Pair<PdfDocument.Page, Canvas> {
        var page = pageIn
        var c = canvasIn
        var y = yIn
        val bandH = 42f
        if (y + bandH > PAGE_H_A4 - MARGIN) {
            doc.finishPage(page)
            page = doc.startPage(
                PdfDocument.PageInfo.Builder(PAGE_W_A4, PAGE_H_A4, doc.pages.size + 1).create()
            )
            c = page.canvas
            y = MARGIN
        }

        val muted = Paint().apply { color = 0xFF7A7A7A.toInt(); textSize = 10f; isAntiAlias = true }
        y += 6f
        c.drawLine(MARGIN, y, PAGE_W_A4 - MARGIN, y, muted)
        y += 14f

        val logoSize = 26f
        PdfBranding.logo(context)?.let {
            c.drawBitmap(
                it,
                Rect(0, 0, it.width, it.height),
                RectF(MARGIN, y - 8f, MARGIN + logoSize, y - 8f + logoSize),
                Paint().apply { isAntiAlias = true; isFilterBitmap = true }
            )
        }
        muted.isFakeBoldText = true
        c.drawText("Roshan Khata — Har Hisaab Roshan", MARGIN + logoSize + 8f, y + 6f, muted)

        doc.finishPage(page)
        return page to c
    }

    private fun writeAndClose(doc: PdfDocument, file: File): File? {
        return try {
            FileOutputStream(file).use { doc.writeTo(it) }
            file
        } catch (e: Exception) {
            null
        } finally {
            doc.close()
        }
    }
}
