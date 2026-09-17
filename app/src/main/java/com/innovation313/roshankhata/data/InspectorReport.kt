package com.innovation313.roshankhata.data

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.innovation313.roshankhata.ui.Format
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The document an agriculture-department inspector asks for, in one file.
 *
 * A pesticide and seed dealer is inspected without warning, and the questions
 * are always the same three: what is on your shelf, which batch is it from and
 * when does it expire, and what is this product legally. Answering those from a
 * phone today means scrolling three screens and reading figures aloud. This
 * builds the same answers as a register that can be handed over, printed, or
 * filed.
 *
 * WHAT IT IS NOT. It is not a valuation, not a backup, and not a legal
 * certificate — it is this app's own record of what it was told, printed
 * honestly. Where the app was never told something, the register prints a dash
 * rather than a guess, because a compliance document that invents a
 * registration number is far worse than one that admits a blank.
 *
 * THE ONE FIGURE THAT NEEDS A CAVEAT, AND IT IS PRINTED ON THE PAGE. What is
 * left of a batch is the bill line minus the sales TAGGED to that exact batch,
 * in the same unit — the identical rule the expiry screen uses, kept identical
 * on purpose, because two screens disagreeing about one batch is worse than
 * either being approximate. An untagged sale subtracts from nothing, so a
 * remaining figure can only ever be too HIGH. The owner signs his name under
 * this, so the document says that in plain words instead of leaving him to
 * discover it in front of an inspector.
 *
 * Same header band, same "not a backup" warning placed first, same footer as
 * [BusinessReport] and [LedgerReport] — one family of documents, so a
 * shopkeeper recognises the third one as his own.
 */
object InspectorReport {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f

    private const val NAVY = 0xFF094C2E.toInt()
    private const val GOLD = 0xFFE1AF3F.toInt()
    private const val RED = 0xFFC0392B.toInt()
    private const val GREY = 0xFF7A7A7A.toInt()
    private const val WARN_BG = 0xFFFFF6E0.toInt()
    private const val WARN_FG = 0xFF5C4A16.toInt()

    private val dateFmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.ENGLISH)
    private val dayFmt = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
    private val fileFmt = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.ENGLISH)

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /**
     * Everything the register prints, gathered before a single line is drawn.
     *
     * Kept as one object so the counts on the app's own screen and the counts
     * in the PDF come from the same read. A preview that disagrees with the
     * document it produced would be the app's worst kind of bug here: the owner
     * would only find out while someone official was holding the paper.
     */
    data class ReportData(
        val businessName: String?,
        val businessAddress: String?,
        val strn: String?,
        val stock: List<Stock.ProductStock>,
        val productsById: Map<Long, Product>,
        /** Only batches with something left — an empty batch is not stock. */
        val batches: List<InspectorBatch>,
        val expired: List<InspectorBatch>,
        val expiringSoon: List<InspectorBatch>,
        /** In stock but with no expiry date ever recorded. */
        val noExpiryCount: Int,
        /** Products carrying stock whose label details were never filled in. */
        val incompleteProducts: List<String>,
        val windowDays: Int
    )

    suspend fun build(context: Context, dao: KhataDao, windowDays: Int): File? {
        return try {
            render(context, gather(context, dao, windowDays))
        } catch (e: Exception) {
            null
        }
    }

    /**
     * One read of the book, shared by the screen and the document.
     *
     * The batch list arrives already ordered by product and then by soonest
     * expiry, so nothing is re-sorted here beyond splitting out the two expiry
     * groups — re-sorting would risk the register and the expiry screen
     * disagreeing about which batch of a product is the oldest.
     */
    suspend fun gather(context: Context, dao: KhataDao, windowDays: Int): ReportData {
        val products = dao.productsOnce()
        val stock = Stock.combine(
            products = products,
            bought = dao.boughtPerProduct(),
            sold = dao.soldPerProduct(),
            returned = dao.returnedPerProduct()
        ).sortedBy { it.name.lowercase() }

        // Sold-out batches are dropped: a register of stock should list what is
        // on the shelf. What was bought and sold over a period is a purchase
        // and sales register — a different document, and not this one.
        val live = dao.allBatchesOnce().filter { it.remaining > 0 }

        val cutoff = System.currentTimeMillis() + windowDays * DAY_MS
        val expired = live.filter { it.hasExpired }
        val expiringSoon = live.filter { b ->
            val exp = b.expiryDate
            exp != null && !b.hasExpired && exp <= cutoff
        }

        // Which products actually in stock are missing the four label fields an
        // inspector reads off the bottle. Counted only for stock on the shelf:
        // nagging about a product the shop no longer carries would train the
        // owner to ignore the whole section.
        val idsInStock = live.mapNotNull { it.productId }.toSet()
        val incomplete = products
            .filter { it.id in idsInStock }
            .filter {
                it.company.isNullOrBlank() ||
                    it.registrationNumber.isNullOrBlank() ||
                    it.technicalName.isNullOrBlank() ||
                    it.formulation.isNullOrBlank()
            }
            .map { it.name }

        return ReportData(
            businessName = BusinessProfile.businessName(context),
            businessAddress = BusinessProfile.businessAddress(context),
            strn = BusinessProfile.strn(context),
            stock = stock,
            productsById = products.associateBy { it.id },
            batches = live,
            expired = expired,
            expiringSoon = expiringSoon,
            noExpiryCount = live.count { it.expiryDate == null },
            incompleteProducts = incomplete,
            windowDays = windowDays
        )
    }

    private fun render(context: Context, d: ReportData): File? {
        val doc = PdfDocument()

        val title = Paint().apply { color = Color.WHITE; textSize = 20f; isFakeBoldText = true; isAntiAlias = true }
        val tagline = Paint().apply { color = GOLD; textSize = 10f; isAntiAlias = true }
        val section = Paint().apply { color = NAVY; textSize = 14f; isFakeBoldText = true; isAntiAlias = true }
        val body = Paint().apply { color = Color.BLACK; textSize = 11f; isAntiAlias = true }
        val bodyBold = Paint().apply { color = Color.BLACK; textSize = 11f; isFakeBoldText = true; isAntiAlias = true }
        val muted = Paint().apply { color = GREY; textSize = 9f; isAntiAlias = true }
        val mutedBig = Paint().apply { color = GREY; textSize = 11f; isAntiAlias = true }
        val warnText = Paint().apply { color = WARN_FG; textSize = 10f; isAntiAlias = true }
        val red = Paint().apply { color = RED; textSize = 11f; isFakeBoldText = true; isAntiAlias = true }
        val tableHeaderFg = Paint().apply { color = Color.WHITE; textSize = 9f; isFakeBoldText = true; isAntiAlias = true }
        val navyFill = Paint().apply { color = NAVY }
        val warnFill = Paint().apply { color = WARN_BG }
        val tableHeaderFill = Paint().apply { color = GREY }
        val rule = Paint().apply { color = 0xFFDDDDDD.toInt(); strokeWidth = 0.6f }
        // Translucent, not opaque — the same fix the ledger report needed: an
        // opaque stripe sliced the watermark wherever a table crossed it.
        val zebra = Paint().apply { color = 0x08000000 }

        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        var canvas: Canvas = page.canvas
        var y: Float
        var pageNo = 1

        val brandLogo = PdfBranding.logo(context)
        val businessName = d.businessName ?: "Roshan Khata"

        fun header(): Float {
            PdfBranding.drawWatermark(context, canvas, PAGE_W, PAGE_H, NAVY)
            canvas.drawRect(0f, 0f, PAGE_W.toFloat(), 88f, navyFill)
            canvas.drawText(businessName, MARGIN, 26f, title)
            canvas.drawText("Stock & Compliance Register", MARGIN, 44f, tagline)
            // The shop's own identity lines, because this is the first thing
            // asked for and the owner should not have to write them on by hand.
            val identity = buildString {
                d.businessAddress?.let { append(it) }
                d.strn?.let {
                    if (isNotEmpty()) append(" \u00B7 ")
                    append("NTN/STRN: $it")
                }
            }
            if (identity.isNotEmpty()) canvas.drawText(identity, MARGIN, 60f, tagline)
            canvas.drawText("Generated ${dateFmt.format(Date())}", MARGIN, 78f, tagline)
            return 112f
        }

        fun newPage() {
            doc.finishPage(page)
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            canvas = page.canvas
            y = header()
        }

        y = header()

        // Shorten text that would collide with the column to its right.
        fun clip(text: String, paint: Paint, maxW: Float): String {
            if (paint.measureText(text) <= maxW) return text
            var end = text.length
            while (end > 1 && paint.measureText(text.substring(0, end) + "\u2026") > maxW) end--
            return text.substring(0, end) + "\u2026"
        }

        // ---- The warning first, same placement and reason as the other two ----
        canvas.drawRect(MARGIN, y, PAGE_W - MARGIN, y + 40f, warnFill)
        canvas.drawText(
            "This is a register to read and print \u2014 it is NOT a backup.",
            MARGIN + 10f, y + 16f,
            Paint(warnText).apply { isFakeBoldText = true }
        )
        canvas.drawText(
            "Roshan Khata cannot restore your records from a PDF.",
            MARGIN + 10f, y + 30f, warnText
        )
        y += 52f

        // ---- What this document can and cannot say ----
        //
        // Printed, not buried in the app. An owner who signs a stock figure
        // needs to know which direction it can be wrong in, and an inspector
        // reading a dash should see immediately that it means "not recorded",
        // not "none".
        canvas.drawRect(MARGIN, y, PAGE_W - MARGIN, y + 52f, warnFill)
        canvas.drawText(
            "How to read this: quantities are \u201Cnot more than\u201D figures.",
            MARGIN + 10f, y + 15f,
            Paint(warnText).apply { isFakeBoldText = true }
        )
        canvas.drawText(
            "A sale not tagged to a batch does not reduce that batch here, so stock",
            MARGIN + 10f, y + 28f, warnText
        )
        canvas.drawText(
            "shown can be higher than the shelf. A dash means never recorded.",
            MARGIN + 10f, y + 41f, warnText
        )
        y += 66f

        // ---- Summary ----
        canvas.drawText("Summary", MARGIN, y, section)
        y += 20f

        fun line(label: String, value: String, paint: Paint = body) {
            canvas.drawText(label, MARGIN, y, body)
            val w = paint.measureText(value)
            canvas.drawText(value, PAGE_W - MARGIN - w, y, paint)
            y += 16f
        }

        line("Products on the books", d.stock.size.toString())
        line("Batches in stock", d.batches.size.toString(), bodyBold)
        line("Expired, still in stock", d.expired.size.toString(), if (d.expired.isEmpty()) body else red)
        line("Expiring within ${d.windowDays} days", d.expiringSoon.size.toString(), if (d.expiringSoon.isEmpty()) body else red)
        line("In stock with no expiry recorded", d.noExpiryCount.toString())
        line("Products missing label details", d.incompleteProducts.size.toString())

        y += 10f
        canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, rule)
        y += 20f

        // ---- 1. Current stock, product by product ----
        canvas.drawText("1. Current stock", MARGIN, y, section)
        y += 18f

        val traded = d.stock.filter { !it.isUntouched }
        if (traded.isEmpty()) {
            canvas.drawText("No stock movement recorded yet.", MARGIN, y, mutedBig)
            y += 18f
        } else {
            val xName = MARGIN + 4f
            val xQtyRight = PAGE_W - MARGIN - 4f
            val nameMaxW = xQtyRight - 150f - xName

            fun stockHeader() {
                canvas.drawRect(MARGIN, y, PAGE_W - MARGIN, y + 18f, tableHeaderFill)
                canvas.drawText("PRODUCT / COMPANY / LABEL DETAILS", xName, y + 12.5f, tableHeaderFg)
                canvas.drawText(
                    "ON HAND", xQtyRight - tableHeaderFg.measureText("ON HAND"), y + 12.5f, tableHeaderFg
                )
                y += 18f
            }

            stockHeader()

            traded.forEachIndexed { index, s ->
                if (y + 30f > PAGE_H - 60f) {
                    newPage()
                    canvas.drawText("1. Current stock (continued)", MARGIN, y, section)
                    y += 18f
                    stockHeader()
                }
                if (index % 2 == 1) canvas.drawRect(MARGIN, y, PAGE_W - MARGIN, y + 30f, zebra)

                val baseline = y + 13f
                canvas.drawText(clip(s.name, body, nameMaxW), xName, baseline, body)

                // The honest figure, or the honest refusal to give one. Bought
                // in bags and sold in kilos cannot be subtracted, and the
                // register says which two numbers it has instead of inventing
                // a conversion nobody told this app.
                val onHand = s.onHand
                if (onHand != null) {
                    val text = Format.qty(onHand, s.unit)
                    val paint = if (onHand > 0) body else mutedBig
                    canvas.drawText(text, xQtyRight - paint.measureText(text), baseline, paint)
                } else {
                    val text = "units differ"
                    canvas.drawText(text, xQtyRight - mutedBig.measureText(text), baseline, mutedBig)
                }

                // The label line: everything an inspector reads off the bottle,
                // or a dash where the app was never told.
                val p = d.productsById[s.productId]
                val label = listOf(
                    p?.company,
                    p?.technicalName,
                    p?.formulation,
                    p?.registrationNumber?.let { "Reg# $it" }
                ).filter { !it.isNullOrBlank() }.joinToString(" \u00B7 ")
                val sub = if (label.isEmpty()) "\u2014 label details not recorded" else label
                canvas.drawText(clip(sub, muted, PAGE_W - MARGIN - xName - 90f), xName, baseline + 12f, muted)

                // Where a figure could not be given, the two sides that could.
                if (onHand == null) {
                    val split = "in ${Format.qty(s.boughtQty + s.returnedQty, s.boughtUnit)} / out ${Format.qty(s.soldQty, s.soldUnit)}"
                    val w = muted.measureText(split)
                    canvas.drawText(split, xQtyRight - w, baseline + 12f, muted)
                }

                y += 30f
            }
        }

        // ---- 2. Batch-wise stock ----
        if (y > PAGE_H - 140f) newPage() else { y += 14f; canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, rule); y += 20f }

        canvas.drawText("2. Batch-wise stock", MARGIN, y, section)
        y += 14f
        canvas.drawText(
            "Every batch with stock left, oldest expiry first, with the bill it came on.",
            MARGIN, y, muted
        )
        y += 16f

        if (d.batches.isEmpty()) {
            canvas.drawText(
                "No batches in stock. Batch and expiry are recorded on a supplier bill.",
                MARGIN, y, mutedBig
            )
            y += 18f
        } else {
            val xName = MARGIN + 4f
            val xExpiryRight = PAGE_W - MARGIN - 96f
            val xQtyRight = PAGE_W - MARGIN - 4f
            val nameMaxW = xExpiryRight - 82f - xName

            fun batchHeader() {
                canvas.drawRect(MARGIN, y, PAGE_W - MARGIN, y + 18f, tableHeaderFill)
                canvas.drawText("PRODUCT / BATCH / SUPPLIER", xName, y + 12.5f, tableHeaderFg)
                canvas.drawText(
                    "EXPIRY", xExpiryRight - tableHeaderFg.measureText("EXPIRY"), y + 12.5f, tableHeaderFg
                )
                canvas.drawText(
                    "LEFT", xQtyRight - tableHeaderFg.measureText("LEFT"), y + 12.5f, tableHeaderFg
                )
                y += 18f
            }

            batchHeader()

            d.batches.forEachIndexed { index, b ->
                if (y + 30f > PAGE_H - 60f) {
                    newPage()
                    canvas.drawText("2. Batch-wise stock (continued)", MARGIN, y, section)
                    y += 18f
                    batchHeader()
                }
                if (index % 2 == 1) canvas.drawRect(MARGIN, y, PAGE_W - MARGIN, y + 30f, zebra)

                val baseline = y + 13f
                canvas.drawText(clip(b.productName, body, nameMaxW), xName, baseline, body)

                // Expiry, and how it reads: expired is the one word that must
                // not be missed on a page someone official is holding.
                val exp = b.expiryDate
                val expiryText = when {
                    exp == null -> "\u2014"
                    b.hasExpired -> "EXPIRED"
                    else -> dayFmt.format(Date(exp))
                }
                val expiryPaint = when {
                    exp == null -> mutedBig
                    b.hasExpired -> red
                    (b.daysLeft ?: Int.MAX_VALUE) <= d.windowDays -> red
                    else -> body
                }
                canvas.drawText(
                    expiryText, xExpiryRight - expiryPaint.measureText(expiryText), baseline, expiryPaint
                )

                val left = Format.qty(b.remaining, b.unit)
                canvas.drawText(left, xQtyRight - bodyBold.measureText(left), baseline, bodyBold)

                val sub = buildString {
                    append("Batch ")
                    append(b.batchNumber?.takeIf { it.isNotBlank() } ?: "\u2014")
                    b.company?.takeIf { it.isNotBlank() }?.let { append(" \u00B7 $it") }
                    b.registrationNumber?.takeIf { it.isNotBlank() }?.let { append(" \u00B7 Reg# $it") }
                    append(" \u00B7 ")
                    append(b.partyName)
                    b.billNumber?.takeIf { it.isNotBlank() }?.let { append(" \u00B7 Bill $it") }
                    append(" \u00B7 ")
                    append(dayFmt.format(Date(b.billDate)))
                }
                canvas.drawText(clip(sub, muted, PAGE_W - MARGIN - xName - 8f), xName, baseline + 12f, muted)

                y += 30f
            }
        }

        // ---- 3. Expiry report ----
        if (y > PAGE_H - 140f) newPage() else { y += 14f; canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, rule); y += 20f }

        canvas.drawText("3. Expiry report", MARGIN, y, section)
        y += 14f
        canvas.drawText(
            "Expired stock first, then what expires within ${d.windowDays} days.",
            MARGIN, y, muted
        )
        y += 18f

        if (d.expired.isEmpty() && d.expiringSoon.isEmpty()) {
            canvas.drawText(
                "Nothing expired and nothing expiring within ${d.windowDays} days.",
                MARGIN, y, mutedBig
            )
            y += 16f
            if (d.noExpiryCount > 0) {
                canvas.drawText(
                    "${d.noExpiryCount} batch(es) in stock carry no expiry date, so they cannot be checked.",
                    MARGIN, y, muted
                )
                y += 16f
            }
        } else {
            fun expiryRow(b: InspectorBatch) {
                if (y + 28f > PAGE_H - 60f) {
                    newPage()
                    canvas.drawText("3. Expiry report (continued)", MARGIN, y, section)
                    y += 20f
                }
                val baseline = y + 12f
                canvas.drawText(clip(b.productName, body, 300f), MARGIN + 4f, baseline, body)

                val days = b.daysLeft
                val right = when {
                    b.hasExpired -> "EXPIRED " + (days?.let { "${-it} days ago" } ?: "")
                    else -> "${days ?: 0} days left"
                }
                canvas.drawText(right, PAGE_W - MARGIN - red.measureText(right), baseline, red)

                val sub = buildString {
                    append(Format.qty(b.remaining, b.unit))
                    append(" left \u00B7 Batch ")
                    append(b.batchNumber?.takeIf { it.isNotBlank() } ?: "\u2014")
                    b.expiryDate?.let { append(" \u00B7 exp ${dayFmt.format(Date(it))}") }
                    append(" \u00B7 from ")
                    append(b.partyName)
                }
                canvas.drawText(clip(sub, muted, PAGE_W - 2 * MARGIN - 8f), MARGIN + 12f, baseline + 12f, muted)
                y += 28f
            }

            d.expired.forEach { expiryRow(it) }
            d.expiringSoon.forEach { expiryRow(it) }
        }

        // ---- 4. What the register could not say ----
        //
        // The gaps, listed by name rather than counted. A count tells the owner
        // there is a problem; the names tell him which bottle to pick up and
        // which four fields to type in before the next visit.
        if (d.incompleteProducts.isNotEmpty()) {
            if (y > PAGE_H - 140f) newPage() else { y += 14f; canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, rule); y += 20f }

            canvas.drawText("4. Label details still missing", MARGIN, y, section)
            y += 14f
            canvas.drawText(
                "These products are in stock without company, technical name, formulation or Reg#.",
                MARGIN, y, muted
            )
            y += 18f

            d.incompleteProducts.take(40).forEach { name ->
                if (y + 15f > PAGE_H - 60f) {
                    newPage()
                    canvas.drawText("4. Label details still missing (continued)", MARGIN, y, section)
                    y += 20f
                }
                canvas.drawText("\u2022  ${clip(name, body, PAGE_W - 2 * MARGIN - 20f)}", MARGIN + 4f, y + 11f, body)
                y += 15f
            }
            if (d.incompleteProducts.size > 40) {
                canvas.drawText(
                    "\u2026 and ${d.incompleteProducts.size - 40} more.", MARGIN + 4f, y + 11f, mutedBig
                )
                y += 15f
            }
        }

        // ---- Signature strip ----
        //
        // A register that gets handed over gets signed. Leaving the line off
        // means it gets written on the back of the page in pen anyway.
        if (y > PAGE_H - 120f) newPage() else y += 24f
        canvas.drawLine(MARGIN, y + 24f, MARGIN + 180f, y + 24f, rule)
        canvas.drawLine(PAGE_W - MARGIN - 180f, y + 24f, PAGE_W - MARGIN, y + 24f, rule)
        canvas.drawText("Dealer's signature", MARGIN, y + 38f, muted)
        canvas.drawText(
            "Inspector's signature",
            PAGE_W - MARGIN - muted.measureText("Inspector's signature"),
            y + 38f,
            muted
        )

        // ---- Footer ----
        y = PAGE_H - 34f
        var footerX = MARGIN
        brandLogo?.let { mark ->
            val size = 18f
            canvas.drawBitmap(
                mark,
                android.graphics.Rect(0, 0, mark.width, mark.height),
                android.graphics.RectF(footerX, y - 13f, footerX + size, y + 5f),
                Paint().apply { isAntiAlias = true; isFilterBitmap = true }
            )
            footerX += size + 6f
        }
        canvas.drawText("Roshan Khata \u00B7 Page $pageNo", footerX, y, muted)

        doc.finishPage(page)

        val dir = File(context.cacheDir, "statements").apply { mkdirs() }
        val file = File(dir, "RoshanKhata_StockRegister_${fileFmt.format(Date())}.pdf")

        return try {
            FileOutputStream(file).use { doc.writeTo(it) }
            doc.close()
            file
        } catch (e: Exception) {
            doc.close()
            null
        }
    }
}
