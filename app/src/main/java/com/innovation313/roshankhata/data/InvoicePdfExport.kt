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
            2 -> buildBlackGold(context, invoice, items)
            3 -> buildModernGradient(context, invoice, items)
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
    /**
     * The full-featured design, built on [InvoiceTemplateKit] rather than
     * drawing its own header/table/totals/signature — this IS the base the
     * other eight templates are built on, so its own code is now the
     * shared pieces plus this template's own palette and fonts, not a
     * standalone 300-line function any more.
     *
     * Fonts and palette straight from the finalised mockup spec: Sora for
     * headings, IBM Plex Mono for numbers and money; #0F2A2A ink, #0C6B6B
     * into #12908C for the band, #F1F8F7 box fill, #E4EDEC rules.
     */
    private fun buildTealCorporate(context: Context, invoice: Invoice, items: List<InvoiceItem>): File? {
        val palette = InvoiceTemplateKit.Palette(
            ink = 0xFF0F2A2A.toInt(),
            primary = 0xFF0C6B6B.toInt(),
            primaryEnd = 0xFF12908C.toInt(),
            muted = 0xFF6A7A79.toInt(),
            ruleColor = 0xFFE4EDEC.toInt(),
            boxFill = 0xFFF1F8F7.toInt(),
            zebra = 0xFFF9FCFC.toInt()
        )
        val fonts = InvoiceTemplateKit.Fonts(
            heading = InvoiceFonts.sora(context),
            mono = InvoiceFonts.ibmPlexMono(context),
            monoBold = InvoiceFonts.ibmPlexMonoBold(context)
        )

        val doc = PdfDocument()
        val left = MARGIN
        val right = PAGE_W_A4 - MARGIN

        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W_A4, PAGE_H_A4, pageNo).create())
        var c = page.canvas

        fun newPage(): Pair<Canvas, Float> {
            doc.finishPage(page)
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W_A4, PAGE_H_A4, pageNo).create())
            c = page.canvas
            val bandY = InvoiceTemplateKit.drawHeaderBand(c, context, palette, fonts, PAGE_W_A4, left, right)
            return c to bandY
        }

        var y = InvoiceTemplateKit.drawHeaderBand(c, context, palette, fonts, PAGE_W_A4, left, right)
        y = InvoiceTemplateKit.drawBillToAndMeta(c, palette, fonts, left, right, y, invoice)

        val (tableCanvas, tableY) = InvoiceTemplateKit.drawItemsTable(
            c, palette, fonts, left, right, y, PAGE_H_A4 - 250f, items, extraColumn = null
        ) { newPage() }
        c = tableCanvas
        y = tableY + 18f

        val totals = InvoiceMath.totals(items, invoice.discountPercent, invoice.taxPercent, invoice.additionalChargeAmount, invoice.receivedAmount)

        if (y > PAGE_H_A4 - 240f) {
            val fresh = newPage()
            c = fresh.first
            y = fresh.second
        }
        y = InvoiceTemplateKit.drawPaymentAndTotals(c, context, palette, fonts, left, right, y, invoice, totals)
        y = InvoiceTemplateKit.drawAmountInWords(c, context, palette, fonts, left, right, y, totals.grandTotal)

        if (y > PAGE_H_A4 - 140f) {
            val fresh = newPage()
            c = fresh.first
            y = fresh.second
        }
        y = InvoiceTemplateKit.drawTermsAndSignature(c, context, palette, fonts, left, right, y, invoice)

        val finalState = drawMakerStrip(context, doc, page, c, y)
        page = finalState.first
        c = finalState.second

        return writeAndClose(doc, outputFile(context, invoice))
    }

    // ==================== T2 — Black & Gold Executive ====================

    /**
     * The premium design from the spec — near-black sheet, gold rules and
     * headings, Playfair Display for the serif headings a jeweller's
     * letterhead wants, IBM Plex Mono for weights and money.
     *
     * Two spec details worth stating rather than silently following:
     *
     * The mockup lists a "Wazan" (weight) column. It is NOT drawn here,
     * because there is no weight field on an invoice item to fill it —
     * drawing an always-empty column would look broken. Weight is already
     * expressible with what exists: quantity 12.5 with unit "gram" prints
     * as "12.5 gram" in the Qty column, which is what a jeweller actually
     * writes. A real weight field can be added later if the owner wants
     * one separate from quantity.
     *
     * The mockup also says "no tax field". That is a description of the
     * jeweller use-case, not a rule this template should enforce by
     * hiding a number: a tax amount that is IN the grand total but not
     * shown would make the arithmetic impossible to follow. A shop that
     * never charges tax turns it off in Invoice Settings, which hides it
     * everywhere including here — the correct place for that choice.
     */
    private fun buildBlackGold(context: Context, invoice: Invoice, items: List<InvoiceItem>): File? {
        val palette = InvoiceTemplateKit.Palette(
            ink = 0xFFEDE3C8.toInt(),          // cream text ON the dark sheet
            primary = 0xFFD4B15A.toInt(),      // gold
            primaryEnd = 0xFFB08D3A.toInt(),   // deeper gold, for the band's gradient
            muted = 0xFFA8926A.toInt(),
            ruleColor = 0xFF37311F.toInt(),
            boxFill = 0xFF1F1B14.toInt(),      // barely-lighter than the sheet, so the box reads without glowing
            zebra = 0xFF1A1710.toInt(),
            gradient = true,
            pageBackground = 0xFF141310.toInt(),
            // Gold band, near-black text on it — the one place white would
            // be wrong on this template.
            onPrimary = 0xFF141310.toInt(),
            onPrimaryMuted = 0xFF3A2F16.toInt(),
            // The table header is gold too, not the page's cream "ink".
            tableHeaderFill = 0xFFD4B15A.toInt(),
            onTableHeader = 0xFF141310.toInt()
        )
        val fonts = InvoiceTemplateKit.Fonts(
            heading = InvoiceFonts.playfairDisplay(context),
            mono = InvoiceFonts.ibmPlexMono(context),
            monoBold = InvoiceFonts.ibmPlexMonoBold(context)
        )

        val doc = PdfDocument()
        val left = MARGIN
        val right = PAGE_W_A4 - MARGIN

        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W_A4, PAGE_H_A4, pageNo).create())
        var c = page.canvas
        InvoiceTemplateKit.fillPage(c, palette, PAGE_W_A4, PAGE_H_A4)

        fun newPage(): Pair<Canvas, Float> {
            doc.finishPage(page)
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W_A4, PAGE_H_A4, pageNo).create())
            c = page.canvas
            // Before anything else — a continuation page left white would
            // be a jarring break in the middle of a dark document.
            InvoiceTemplateKit.fillPage(c, palette, PAGE_W_A4, PAGE_H_A4)
            val bandY = InvoiceTemplateKit.drawHeaderBand(c, context, palette, fonts, PAGE_W_A4, left, right)
            return c to bandY
        }

        var y = InvoiceTemplateKit.drawHeaderBand(c, context, palette, fonts, PAGE_W_A4, left, right)
        y = InvoiceTemplateKit.drawBillToAndMeta(c, palette, fonts, left, right, y, invoice)

        val (tableCanvas, tableY) = InvoiceTemplateKit.drawItemsTable(
            c, palette, fonts, left, right, y, PAGE_H_A4 - 250f, items, extraColumn = null
        ) { newPage() }
        c = tableCanvas
        y = tableY + 18f

        val totals = InvoiceMath.totals(items, invoice.discountPercent, invoice.taxPercent, invoice.additionalChargeAmount, invoice.receivedAmount)

        if (y > PAGE_H_A4 - 240f) {
            val fresh = newPage()
            c = fresh.first
            y = fresh.second
        }
        y = InvoiceTemplateKit.drawPaymentAndTotals(c, context, palette, fonts, left, right, y, invoice, totals)
        y = InvoiceTemplateKit.drawAmountInWords(c, context, palette, fonts, left, right, y, totals.grandTotal)

        if (y > PAGE_H_A4 - 140f) {
            val fresh = newPage()
            c = fresh.first
            y = fresh.second
        }
        y = InvoiceTemplateKit.drawTermsAndSignature(c, context, palette, fonts, left, right, y, invoice)

        val finalState = drawMakerStrip(context, doc, page, c, y)
        page = finalState.first
        c = finalState.second

        return writeAndClose(doc, outputFile(context, invoice))
    }

    // ==================== T3 — Modern Gradient ====================

    /**
     * The bright, modern design from the spec — violet through magenta into
     * pink across the header band, Manrope's rounded shapes for headings.
     * Aimed at a salon or a newer shop rather than a wholesale trader.
     *
     * The spec describes this one as "no bank clutter, discount only, no
     * tax". Neither is enforced by hiding data, for the same reason stated
     * on T2: a shop that does not use tax or bank details simply has them
     * unset, and every one of those blocks already prints only when it has
     * a value. So an owner who wants the clean look gets it automatically,
     * while an owner who deliberately entered a payment QR still sees it —
     * rather than the template silently dropping something they set on
     * purpose. Invoice Settings is where a feature gets turned off.
     */
    private fun buildModernGradient(context: Context, invoice: Invoice, items: List<InvoiceItem>): File? {
        val palette = InvoiceTemplateKit.Palette(
            ink = 0xFF1E1630.toInt(),
            primary = 0xFF6D28D9.toInt(),      // violet
            primaryEnd = 0xFFEC4899.toInt(),   // pink
            primaryMid = 0xFFA21CAF.toInt(),   // magenta, the middle stop
            muted = 0xFF6B6480.toInt(),
            ruleColor = 0xFFEDE4F5.toInt(),
            boxFill = 0xFFF7F0FB.toInt(),
            zebra = 0xFFFBF7FD.toInt(),
            // The table header takes the violet end of the sweep rather than
            // the page's near-black ink, keeping the whole sheet in one
            // family instead of a dark bar cutting across a bright design.
            tableHeaderFill = 0xFF6D28D9.toInt(),
            onPrimaryMuted = 0xFFF0D9FB.toInt()
        )
        val fonts = InvoiceTemplateKit.Fonts(
            heading = InvoiceFonts.manrope(context),
            mono = InvoiceFonts.ibmPlexMono(context),
            monoBold = InvoiceFonts.ibmPlexMonoBold(context)
        )

        val doc = PdfDocument()
        val left = MARGIN
        val right = PAGE_W_A4 - MARGIN

        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W_A4, PAGE_H_A4, pageNo).create())
        var c = page.canvas

        fun newPage(): Pair<Canvas, Float> {
            doc.finishPage(page)
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W_A4, PAGE_H_A4, pageNo).create())
            c = page.canvas
            val bandY = InvoiceTemplateKit.drawHeaderBand(c, context, palette, fonts, PAGE_W_A4, left, right)
            return c to bandY
        }

        var y = InvoiceTemplateKit.drawHeaderBand(c, context, palette, fonts, PAGE_W_A4, left, right)
        y = InvoiceTemplateKit.drawBillToAndMeta(c, palette, fonts, left, right, y, invoice)

        val (tableCanvas, tableY) = InvoiceTemplateKit.drawItemsTable(
            c, palette, fonts, left, right, y, PAGE_H_A4 - 250f, items, extraColumn = null
        ) { newPage() }
        c = tableCanvas
        y = tableY + 18f

        val totals = InvoiceMath.totals(items, invoice.discountPercent, invoice.taxPercent, invoice.additionalChargeAmount, invoice.receivedAmount)

        if (y > PAGE_H_A4 - 240f) {
            val fresh = newPage()
            c = fresh.first
            y = fresh.second
        }
        y = InvoiceTemplateKit.drawPaymentAndTotals(c, context, palette, fonts, left, right, y, invoice, totals)
        y = InvoiceTemplateKit.drawAmountInWords(c, context, palette, fonts, left, right, y, totals.grandTotal)

        if (y > PAGE_H_A4 - 140f) {
            val fresh = newPage()
            c = fresh.first
            y = fresh.second
        }
        y = InvoiceTemplateKit.drawTermsAndSignature(c, context, palette, fonts, left, right, y, invoice)

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
            textSize = 12f; typeface = Typeface.create(InvoiceFonts.sora(context), Typeface.BOLD)
        }
        val shopSub = Paint(center).apply { textSize = 8f; color = 0xFF777777.toInt() }
        val mono = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = InvoiceFonts.ibmPlexMono(context); textSize = 9f; color = Color.BLACK
        }
        val monoR = Paint(mono).apply { textAlign = Paint.Align.RIGHT }
        val monoGrey = Paint(mono).apply { color = 0xFF555555.toInt() }
        val monoGreyR = Paint(monoGrey).apply { textAlign = Paint.Align.RIGHT }
        val monoBold = Paint(mono).apply { typeface = InvoiceFonts.ibmPlexMonoBold(context) }
        val grandPaint = Paint(mono).apply {
            typeface = InvoiceFonts.ibmPlexMonoBold(context); textSize = 11f
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
            c.drawText(numberOnly(totals.balanceDue), pageW - pad, y, Paint(monoR).apply { typeface = InvoiceFonts.ibmPlexMonoBold(context) })
            y += 12f
        }
        c.drawLine(pad, y, pageW - pad, y, dash)
        y += 16f
        c.drawText("Rupay Alfaaz Mein:", pad, y, Paint(mono).apply { textSize = 8f; color = 0xFF555555.toInt() })
        y += 11f
        // Wrapped by hand, not left to overflow the receipt's own width —
        // the words for a large total are routinely longer than 226pt fits
        // on one line, unlike every other value on this narrow page.
        y = wrapMonoText(context, c, NumberWords.rupeesInWords(context, totals.grandTotal), pad, y, pageW - 2 * pad, 9f)

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
                y = wrapMonoText(context, c, it, pad, y, pageW - 2 * pad, 8f)
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
    private fun wrapMonoText(context: Context, c: Canvas, text: String, x: Float, startY: Float, maxWidth: Float, size: Float): Float {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = InvoiceFonts.ibmPlexMono(context); textSize = size; color = 0xFF333333.toInt()
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
