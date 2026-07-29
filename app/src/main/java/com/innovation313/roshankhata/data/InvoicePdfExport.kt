package com.innovation313.roshankhata.data

import android.content.Context
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
     * Which editable value a box on the rendered page corresponds to.
     *
     * This is what lets the editor put a real input control exactly over the
     * text it edits, so the owner types on the invoice itself rather than on
     * a form somewhere below it.
     */
    enum class Field { CUSTOMER, PHONE, NOTE, ITEM_NAME, ITEM_QTY, ITEM_RATE }

    /**
     * Where one editable value landed on the page, in the PDF's own point
     * coordinates. [itemIndex] is the position in the items list for the
     * ITEM_* fields and -1 for the rest.
     */
    data class FieldBox(val field: Field, val itemIndex: Int, val rect: RectF)

    /** A rendered page plus the map of what on it can be typed into. */
    data class Preview(
        val bitmap: android.graphics.Bitmap,
        val boxes: List<FieldBox>,
        val pageWidth: Int,
        val pageHeight: Int
    )

    /**
     * Notes where a piece of text was drawn, measured from the same Paint
     * that drew it — so a box always covers exactly the glyphs it belongs
     * to, at whatever alignment that Paint uses, and cannot drift from the
     * drawing as the templates change.
     *
     * Given a minimum width because an empty field still needs somewhere to
     * tap: with nothing typed yet, the measured width would be zero and the
     * box would be impossible to hit.
     */
    private fun record(
        into: MutableList<FieldBox>?,
        field: Field,
        itemIndex: Int,
        x: Float,
        y: Float,
        paint: Paint,
        text: String
    ) {
        if (into == null) return
        val w = paint.measureText(text).coerceAtLeast(40f)
        val left = when (paint.textAlign) {
            Paint.Align.RIGHT -> x - w
            Paint.Align.CENTER -> x - w / 2f
            else -> x
        }
        val fm = paint.fontMetrics
        into.add(FieldBox(field, itemIndex, RectF(left, y + fm.ascent, left + w, y + fm.descent)))
    }

    fun build(context: Context, invoice: Invoice, items: List<InvoiceItem>): File? =
        build(context, invoice, items, null)

    private fun build(
        context: Context,
        invoice: Invoice,
        items: List<InvoiceItem>,
        boxes: MutableList<FieldBox>?
    ): File? {
        return when (invoice.templateId) {
            10 -> buildThermalReceipt(context, invoice, items, boxes)
            else -> buildTealCorporate(context, invoice, items, boxes)
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
    fun renderPreview(context: Context, invoice: Invoice, items: List<InvoiceItem>): Preview? {
        val boxes = mutableListOf<FieldBox>()
        val file = build(context, invoice, items, boxes) ?: return null
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
                        // Only page one is previewed, so a box drawn onto a
                        // later page must not be offered as tappable here —
                        // it would sit at coordinates belonging to a page the
                        // owner cannot see.
                        val onFirstPage = boxes.filter {
                            it.rect.top >= 0f && it.rect.bottom <= page.height.toFloat()
                        }
                        Preview(bmp, onFirstPage, page.width, page.height)
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
     * The full-featured template: bank/payment box, discount and tax lines,
     * amount in words, terms, a stamp-and-signature block. The one to reach
     * for when an invoice needs to look like a complete, formal bill.
     */
    private fun buildTealCorporate(
        context: Context,
        invoice: Invoice,
        items: List<InvoiceItem>,
        boxes: MutableList<FieldBox>?
    ): File? {
        val doc = PdfDocument()

        val tealDark = 0xFF0F2A2A.toInt()
        val teal = 0xFF0C6B6B.toInt()
        val grey = 0xFF6A7A79.toInt()
        val lineGrey = 0xFFE4EDEC.toInt()

        val bandTitle = Paint().apply {
            color = Color.WHITE; textSize = 15f; isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bandSub = Paint().apply {
            color = Color.WHITE; alpha = 220; textSize = 9f; isAntiAlias = true
        }
        val invWord = Paint().apply {
            color = Color.WHITE; textSize = 21f; isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val invSmall = Paint().apply {
            color = Color.WHITE; alpha = 210; textSize = 8f; isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        val lbl = Paint().apply {
            color = teal; textSize = 8f; isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val billName = Paint().apply {
            color = tealDark; textSize = 12f; isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val metaLine = Paint().apply { color = grey; textSize = 10f; isAntiAlias = true }
        val metaLineBold = Paint().apply {
            color = tealDark; textSize = 10f; isAntiAlias = true
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val thHeader = Paint().apply {
            color = Color.WHITE; textSize = 8.5f; isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val tdBody = Paint().apply { color = tealDark; textSize = 10f; isAntiAlias = true }
        val totalsLbl = Paint().apply { color = grey; textSize = 10.5f; isAntiAlias = true }
        val totalsVal = Paint().apply {
            color = tealDark; textSize = 10.5f; isAntiAlias = true
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
        }
        val grandLbl = Paint().apply {
            color = Color.WHITE; textSize = 13f; isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val grandVal = Paint().apply {
            color = Color.WHITE; textSize = 13f; isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val words = Paint().apply {
            color = grey; textSize = 9.5f; isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        }
        val terms = Paint().apply { color = grey; textSize = 8.5f; isAntiAlias = true }
        val caption = Paint().apply {
            color = grey; textSize = 8.5f; isAntiAlias = true; textAlign = Paint.Align.CENTER
        }
        val lineFill = Paint().apply { color = lineGrey; strokeWidth = 0.6f }
        val dashFill = Paint().apply {
            color = 0xFFCFDDDB.toInt(); strokeWidth = 0.6f
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(3f, 3f), 0f)
        }
        val bandPaint = Paint()

        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W_A4, PAGE_H_A4, pageNo).create())
        var c = page.canvas

        fun drawBand(): Float {
            bandPaint.shader = LinearGradient(
                0f, 0f, PAGE_W_A4.toFloat(), 0f, teal, 0xFF12908C.toInt(), Shader.TileMode.CLAMP
            )
            c.drawRect(0f, 0f, PAGE_W_A4.toFloat(), 78f, bandPaint)
            bandPaint.shader = null

            val name = BusinessProfile.businessName(context)?.takeIf { it.isNotBlank() } ?: "Roshan Khata"
            c.drawText(name, MARGIN, 30f, bandTitle)
            BusinessProfile.businessAddress(context)?.let {
                c.drawText(it, MARGIN, 44f, bandSub)
            }

            c.drawText("INVOICE", PAGE_W_A4 - MARGIN, 34f, invWord)
            c.drawText(invoice.invoiceNumber, PAGE_W_A4 - MARGIN, 46f, invSmall)

            return 96f
        }

        var y = drawBand()

        // Bill To (left) / Invoice meta (right)
        c.drawText("BILL TO", MARGIN, y, lbl)
        y += 13f
        c.drawText(invoice.customerName, MARGIN, y, billName)
        record(boxes, Field.CUSTOMER, -1, MARGIN, y, billName, invoice.customerName)
        run {
            val phone = invoice.customerPhone?.takeIf { it.isNotBlank() }
            y += 13f
            if (phone != null) c.drawText(phone, MARGIN, y, metaLine)
            // Recorded whether or not there is a phone yet: an empty field
            // still needs somewhere to tap to start typing one.
            record(boxes, Field.PHONE, -1, MARGIN, y, metaLine, phone.orEmpty())
        }

        var yr = 96f + 13f
        val xRight = PAGE_W_A4 - MARGIN
        val rMeta = Paint(metaLine).apply { textAlign = Paint.Align.RIGHT }
        c.drawText("Date: " + Format.dateOnly(invoice.invoiceDate), xRight, yr, rMeta)
        invoice.dueDate?.let {
            yr += 13f
            c.drawText("Due: " + Format.dateOnly(it), xRight, yr, rMeta)
        }

        y = maxOf(y, yr) + 20f

        // Items table
        val xNo = MARGIN
        val xItem = MARGIN + 22f
        val xQty = 400f
        val xRate = 460f
        val xAmt = PAGE_W_A4 - MARGIN

        c.drawRect(MARGIN, y - 12f, PAGE_W_A4 - MARGIN, y + 4f, Paint().apply { color = tealDark })
        c.drawText("#", xNo, y, thHeader)
        c.drawText("TAFSEEL", xItem, y, thHeader)
        c.drawText("QTY", xQty, y, Paint(thHeader).apply { textAlign = Paint.Align.RIGHT })
        c.drawText("RATE", xRate, y, Paint(thHeader).apply { textAlign = Paint.Align.RIGHT })
        c.drawText("AMOUNT", xAmt, y, Paint(thHeader).apply { textAlign = Paint.Align.RIGHT })
        y += 18f

        items.forEachIndexed { index, item ->
            if (y > PAGE_H_A4 - 260f) {
                doc.finishPage(page)
                pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W_A4, PAGE_H_A4, pageNo).create())
                c = page.canvas
                y = drawBand()
            }
            c.drawText((index + 1).toString(), xNo, y, tdBody)
            c.drawText(item.itemName, xItem, y, tdBody)
            record(boxes, Field.ITEM_NAME, index, xItem, y, tdBody, item.itemName)

            val rightBody = Paint(tdBody).apply { textAlign = Paint.Align.RIGHT }
            val qtyText = Format.qty(item.quantity, item.unit)
            c.drawText(qtyText, xQty, y, rightBody)
            record(boxes, Field.ITEM_QTY, index, xQty, y, rightBody, qtyText)

            val rateText = numberOnly(item.rate)
            c.drawText(rateText, xRate, y, rightBody)
            record(boxes, Field.ITEM_RATE, index, xRate, y, rightBody, rateText)
            c.drawText(numberOnly(item.lineTotal), xAmt, y, Paint(tdBody).apply { textAlign = Paint.Align.RIGHT })
            y += 8f
            c.drawLine(MARGIN, y, PAGE_W_A4 - MARGIN, y, lineFill)
            y += 16f
        }

        y += 6f

        // Split row: payment info (left) / totals (right)
        val bankName = BusinessProfile.bankName(context)
        val bankTitle = BusinessProfile.bankAccountTitle(context)
        val bankIban = BusinessProfile.bankIban(context)
        val bankJazz = BusinessProfile.bankJazzCash(context)
        val hasBank = bankName != null || bankTitle != null || bankIban != null || bankJazz != null

        val totals = InvoiceMath.totals(items, invoice.discountPercent, invoice.taxPercent)
        val totalsW = 210f
        val totalsX = PAGE_W_A4 - MARGIN - totalsW
        var ty = y

        if (hasBank) {
            if (y > PAGE_H_A4 - 220f) {
                doc.finishPage(page)
                pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W_A4, PAGE_H_A4, pageNo).create())
                c = page.canvas
                y = drawBand()
                ty = y
            }
            var by = y
            c.drawText("PAYMENT INFO", MARGIN, by, lbl)
            by += 14f
            val bankRow = Paint(metaLine)
            val bankRowB = Paint(metaLineBold)
            bankName?.let { c.drawText("Bank: $it", MARGIN, by, bankRow); by += 13f }
            bankTitle?.let { c.drawText("Title: $it", MARGIN, by, bankRow); by += 13f }
            bankIban?.let { c.drawText("IBAN: $it", MARGIN, by, bankRowB); by += 13f }
            bankJazz?.let { c.drawText("JazzCash: $it", MARGIN, by, bankRowB); by += 13f }
        }

        c.drawText("Sub Total", totalsX, ty, totalsLbl)
        c.drawText(Format.money(totals.subtotal), xAmt, ty, totalsVal)
        ty += 15f
        if (totals.discountAmount > 0) {
            c.drawText("Discount (${Format.plain(invoice.discountPercent ?: 0.0)}%)", totalsX, ty, totalsLbl)
            c.drawText("-" + Format.money(totals.discountAmount), xAmt, ty, totalsVal)
            ty += 15f
        }
        if (totals.taxAmount > 0) {
            c.drawText("Tax (${Format.plain(invoice.taxPercent ?: 0.0)}%)", totalsX, ty, totalsLbl)
            c.drawText(Format.money(totals.taxAmount), xAmt, ty, totalsVal)
            ty += 15f
        }
        ty += 4f
        c.drawRect(totalsX, ty - 14f, xAmt, ty + 8f, Paint().apply { color = teal })
        c.drawText("TOTAL", totalsX + 10f, ty + 1f, grandLbl)
        c.drawText(Format.money(totals.grandTotal), xAmt - 10f, ty + 1f, grandVal)
        ty += 26f

        y = maxOf(if (hasBank) y + 60f else y, ty) + 6f

        c.drawText(NumberWords.rupeesInWords(totals.grandTotal), MARGIN, y, words)
        y += 20f

        c.drawLine(MARGIN, y, PAGE_W_A4 - MARGIN, y, dashFill)
        y += 20f

        run {
            val note = invoice.note?.takeIf { it.isNotBlank() }
            if (note != null) c.drawText(note, MARGIN, y, terms)
            record(boxes, Field.NOTE, -1, MARGIN, y, terms, note.orEmpty())
        }

        // A long note or a full bank box can still push this far down —
        // guarded the same way every other section on this page already is.
        if (y > PAGE_H_A4 - 140f) {
            doc.finishPage(page)
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W_A4, PAGE_H_A4, pageNo).create())
            c = page.canvas
            y = drawBand()
        }

        // Stamp + signature, bottom-right
        val stamp = BusinessProfile.loadStamp(context)
        val sigX = PAGE_W_A4 - MARGIN - 100f
        if (stamp != null) {
            val size = 56
            c.drawBitmap(stamp, null, Rect((sigX + 22).toInt(), (y - 40).toInt(), (sigX + 22 + size).toInt(), (y - 40 + size).toInt()), null)
        }
        c.drawLine(sigX, y + 22f, PAGE_W_A4 - MARGIN, y + 22f, Paint().apply { color = tealDark; strokeWidth = 0.8f })
        c.drawText("Authorised Signature", sigX + 50f, y + 34f, caption)

        y += 50f

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
    private fun buildThermalReceipt(
        context: Context,
        invoice: Invoice,
        items: List<InvoiceItem>,
        boxes: MutableList<FieldBox>?
    ): File? {
        val pageW = 226 // ~80mm at 72dpi
        val pad = 16f

        val totals = InvoiceMath.totals(items, invoice.discountPercent, invoice.taxPercent)

        // Height is computed, not guessed — a thermal roll has no fixed page,
        // and the alternative (a fixed too-short page) would silently cut the
        // receipt off mid-item.
        var estimatedH = 236f
        estimatedH += items.size * 26f
        if (totals.discountAmount > 0) estimatedH += 12f
        if (totals.taxAmount > 0) estimatedH += 12f
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

        y += 16f
        c.drawLine(pad, y, pageW - pad, y, dash)
        y += 16f

        c.drawText("Rasid #:", pad, y, mono)
        c.drawText(invoice.invoiceNumber, pageW - pad, y, monoR)
        y += 13f
        c.drawText("Tareekh:", pad, y, mono)
        c.drawText(Format.dateOnly(invoice.invoiceDate), pageW - pad, y, monoR)
        y += 13f
        c.drawText("Waqt:", pad, y, mono)
        c.drawText(timeFmt.format(Date(invoice.createdAt)), pageW - pad, y, monoR)

        // This template had no customer line at all — a counter receipt
        // often does not need one. It is drawn now because without it there
        // is nowhere on the receipt to type a name, and the editor works by
        // typing on the invoice itself.
        y += 13f
        c.drawText("Gahak:", pad, y, mono)
        c.drawText(invoice.customerName, pageW - pad, y, monoR)
        record(boxes, Field.CUSTOMER, -1, pageW - pad, y, monoR, invoice.customerName)

        run {
            val phone = invoice.customerPhone?.takeIf { it.isNotBlank() }
            y += 13f
            c.drawText("Phone:", pad, y, mono)
            if (phone != null) c.drawText(phone, pageW - pad, y, monoR)
            record(boxes, Field.PHONE, -1, pageW - pad, y, monoR, phone.orEmpty())
        }

        y += 16f
        c.drawLine(pad, y, pageW - pad, y, dash)
        y += 16f

        items.forEachIndexed { index, item ->
            c.drawText(item.itemName, pad, y, monoBold)
            record(boxes, Field.ITEM_NAME, index, pad, y, monoBold, item.itemName)
            y += 11f

            // One drawn line, two editable values. The split is measured from
            // the same Paint that draws it, so each box covers its own half
            // rather than both sharing one ambiguous target.
            val qtyText = Format.qty(item.quantity, item.unit)
            val rateText = numberOnly(item.rate)
            val joiner = " × "
            c.drawText(qtyText + joiner + rateText, pad, y, monoGrey)
            record(boxes, Field.ITEM_QTY, index, pad, y, monoGrey, qtyText)
            val rateX = pad + monoGrey.measureText(qtyText + joiner)
            record(boxes, Field.ITEM_RATE, index, rateX, y, monoGrey, rateText)

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
        y += 4f
        c.drawLine(pad, y, pageW - pad, y, dash)
        y += 15f
        c.drawText("TOTAL", pad, y, grandPaint)
        c.drawText(Format.money(totals.grandTotal), pageW - pad, y, grandPaintR)
        y += 12f
        c.drawLine(pad, y, pageW - pad, y, dash)
        y += 22f

        BusinessProfile.loadStamp(context)?.let { stamp ->
            val size = 46
            val left = (cx - size / 2)
            c.drawBitmap(stamp, null, Rect(left.toInt(), y.toInt(), (left + size).toInt(), y.toInt() + size), null)
            y += size + 10f
        }

        c.drawLine(pad, y, pageW - pad, y, dash)
        y += 16f
        c.drawText("Shukriya!", cx, y, shopSub)
        y += 11f
        c.drawText("Dobara tashreef layein", cx, y, shopSub)

        doc.finishPage(page)
        return writeAndClose(doc, outputFile(context, invoice))
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
