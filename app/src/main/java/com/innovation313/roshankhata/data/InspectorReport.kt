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

    // LANDSCAPE A4, and the reason is the table itself. This register answers
    // six or eight questions about every product — company, technical name,
    // formulation, Reg#, batch, supplier, bill, expiry — and on a 595pt
    // portrait page those cannot each own a column, which is why they were
    // once strung onto one line with dots between them. A register is read by
    // running a finger DOWN a column: "which of these has no Reg#", "which
    // came from this supplier". A dotted line cannot be read that way.
    // 842pt across leaves 762 of usable width, which is what the columns below
    // are measured against.
    private const val PAGE_W = 842
    private const val PAGE_H = 595
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
        /**
         * Name paired with exactly which of the four is absent. The name alone
         * was useless: the owner reads "urea" under a heading saying company,
         * technical name, formulation or Reg# are missing, sees he has filled
         * three of them, and cannot tell which one the register still wants.
         */
        val incompleteProducts: List<Pair<String, String>>,
        /**
         * The same on-hand figures as [stock], regrouped under the company
         * whose name is on the label — the "company-wise stock" an inspection
         * asks for. Companies in name order; a product with no company recorded
         * falls under one honest "not recorded" heading rather than being
         * dropped. Only products that have actually moved appear.
         */
        val companyStock: List<Pair<String, List<Stock.ProductStock>>>,
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
            .map { p ->
                p.name to listOfNotNull(
                    "company".takeIf { p.company.isNullOrBlank() },
                    "technical name".takeIf { p.technicalName.isNullOrBlank() },
                    "formulation".takeIf { p.formulation.isNullOrBlank() },
                    "Reg#".takeIf { p.registrationNumber.isNullOrBlank() }
                ).joinToString(", ")
            }

        // Company-wise: the same stock, grouped under the brand on the label.
        // A product with no company recorded is not dropped — it goes under one
        // honest heading, because a compliance total that quietly omits stock is
        // worse than one that admits what it could not label. Untouched products
        // are left out, matching section 1.
        val productsById = products.associateBy { it.id }
        val noCompany = "\u2014 company not recorded"
        val companyStock = stock
            .filter { !it.isUntouched }
            .groupBy { productsById[it.productId]?.company?.trim()?.takeIf { c -> c.isNotEmpty() } ?: noCompany }
            .toList()
            .sortedWith(compareBy(
                // The unlabelled group sits last, not jumbled into the alphabet.
                { it.first == noCompany },
                { it.first.lowercase() }
            ))
            .map { (company, list) -> company to list.sortedBy { it.name.lowercase() } }

        return ReportData(
            businessName = BusinessProfile.businessName(context),
            businessAddress = BusinessProfile.businessAddress(context),
            strn = BusinessProfile.strn(context),
            stock = stock,
            productsById = productsById,
            batches = live,
            expired = expired,
            expiringSoon = expiringSoon,
            noExpiryCount = live.count { it.expiryDate == null },
            incompleteProducts = incomplete,
            companyStock = companyStock,
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
        // The company band in section 5: pale enough to read black text on,
        // solid enough that it cannot be taken for a zebra stripe.
        val groupFill = Paint().apply { color = 0xFFE8EDEA.toInt() }
        val groupTag = Paint().apply { color = GREY; textSize = 8f; isFakeBoldText = true; isAntiAlias = true }
        val groupName = Paint().apply { color = NAVY; textSize = 12f; isFakeBoldText = true; isAntiAlias = true }
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

        /**
         * The full masthead on page 1, a slim one after it.
         *
         * The owner's objection was fair: the tall block repeated on every
         * page read as the app announcing itself, when this document is about
         * his stock and carries HIS name. A register that gets separated from
         * its first page must still identify itself, so the line stays — but
         * as one line, not four, and it says "continued" so nobody mistakes a
         * later page for the start of a fresh register.
         *
         * Note the name printed here is the SHOP's, from Business Profile.
         * "Roshan Khata" only appears when that has been left blank.
         */
        fun header(first: Boolean = true): Float {
            PdfBranding.drawWatermark(context, canvas, PAGE_W, PAGE_H, NAVY)
            if (!first) {
                canvas.drawRect(0f, 0f, PAGE_W.toFloat(), 34f, navyFill)
                canvas.drawText(businessName, MARGIN, 22f, tagline)
                val cont = "Stock & Compliance Register (continued)"
                canvas.drawText(cont, PAGE_W - MARGIN - tagline.measureText(cont), 22f, tagline)
                return 58f
            }
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
            y = header(first = false)
        }

        y = header()

        // Shorten text that would collide with the column to its right.
        fun clip(text: String, paint: Paint, maxW: Float): String {
            if (paint.measureText(text) <= maxW) return text
            var end = text.length
            while (end > 1 && paint.measureText(text.substring(0, end) + "\u2026") > maxW) end--
            return text.substring(0, end) + "\u2026"
        }

        // ---- The table machinery ----
        //
        // One definition of a column, used by every table below, so the three
        // registers cannot drift into three different-looking tables. A column
        // owns its width and its alignment; the row helper does the measuring,
        // the clipping and the padding, because those were the three things
        // hand-written per section before and the reason text ran into the
        // figure beside it.
        //
        // PAD is inside the column, so the vertical rule never touches ink.
        val cellPad = 5f
        val ruleLight = Paint().apply { color = 0xFFE4E4E4.toInt(); strokeWidth = 0.5f }

        /** [w] is the column's full width; [right] right-aligns its contents. */
        class Col(val title: String, val w: Float, val right: Boolean = false)

        /**
         * Draws the grey title strip, and returns each column's left edge so a
         * row can be written against the same measurements the header used.
         * Called again after every page break — a table whose headings are on
         * page 1 only is unreadable on page 2, which is the page an inspector
         * is usually holding.
         */
        fun tableHead(cols: List<Col>): List<Float> {
            canvas.drawRect(MARGIN, y, PAGE_W - MARGIN, y + 18f, tableHeaderFill)
            val lefts = mutableListOf<Float>()
            var x = MARGIN
            cols.forEach { c ->
                lefts += x
                val t = clip(c.title, tableHeaderFg, c.w - cellPad * 2)
                val tx = if (c.right) x + c.w - cellPad - tableHeaderFg.measureText(t) else x + cellPad
                canvas.drawText(t, tx, y + 12.5f, tableHeaderFg)
                x += c.w
            }
            y += 18f
            return lefts
        }

        /**
         * One row of cells, already paired with the paint each should use.
         *
         * A null cell prints an em dash in grey: the register's own rule is
         * that a dash means NEVER RECORDED, never "none", and leaving the box
         * blank would quietly say the opposite.
         */
        fun tableRow(
            cols: List<Col>,
            lefts: List<Float>,
            rowH: Float,
            cells: List<Pair<String?, Paint>>
        ) {
            val baseline = y + rowH / 2 + 4f
            cols.forEachIndexed { i, c ->
                val (raw, paint) = cells[i]
                val usePaint = if (raw.isNullOrBlank()) mutedBig else paint
                val text = clip(raw?.takeIf { it.isNotBlank() } ?: "\u2014", usePaint, c.w - cellPad * 2)
                val x = if (c.right) {
                    lefts[i] + c.w - cellPad - usePaint.measureText(text)
                } else {
                    lefts[i] + cellPad
                }
                canvas.drawText(text, x, baseline, usePaint)
                // The separator sits on the column's LEFT edge, so the first
                // one is skipped: a line there would double the page margin.
                if (i > 0) canvas.drawLine(lefts[i], y, lefts[i], y + rowH, ruleLight)
            }
            y += rowH
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

        // Section numbers are COUNTED, not written in by hand.
        //
        // They were literals, and two sections are conditional: with no
        // incomplete products and nothing expiring, the page printed 1, 2, 3,
        // 5 and the reader is left hunting for a section 4 that was never
        // missing — on a document whose whole purpose is to look complete to
        // someone official. Numbering from a counter means whatever survives
        // the conditions is numbered 1..n with no gap.
        var sectionNo = 0
        fun sec(name: String): String { sectionNo++; return "$sectionNo. $name" }

        // ---- 1. Current stock, product by product ----
        val s1 = sec("Current stock")
        canvas.drawText(s1, MARGIN, y, section)
        y += 18f

        val traded = d.stock.filter { !it.isUntouched }
        if (traded.isEmpty()) {
            canvas.drawText("No stock movement recorded yet.", MARGIN, y, mutedBig)
            y += 18f
        } else {
            // 190 + 130 + 150 + 70 + 110 + 112 = 762, the full usable width.
            // Product and technical name get the most room because they are
            // the two that genuinely run long; formulation is EC/WP/SL/WG and
            // never needs more than a few characters.
            val cols = listOf(
                Col("PRODUCT", 190f),
                Col("COMPANY", 130f),
                Col("TECHNICAL NAME", 150f),
                Col("FORM.", 70f),
                Col("REG#", 110f),
                Col("ON HAND", 112f, right = true)
            )
            var lefts = tableHead(cols)
            val rowH = 20f

            traded.forEachIndexed { index, s ->
                if (y + rowH > PAGE_H - 60f) {
                    newPage()
                    canvas.drawText("$s1 (continued)", MARGIN, y, section)
                    y += 18f
                    lefts = tableHead(cols)
                }
                if (index % 2 == 1) canvas.drawRect(MARGIN, y, PAGE_W - MARGIN, y + rowH, zebra)

                // The honest figure, or the honest refusal to give one. Bought
                // in bags and sold in kilos cannot be subtracted, and the
                // register says which two numbers it has instead of inventing
                // a conversion nobody told this app.
                val onHand = s.onHand
                val qtyText: String
                val qtyPaint: Paint
                if (onHand != null) {
                    qtyText = Format.qty(onHand, s.unit)
                    qtyPaint = if (onHand > 0) body else mutedBig
                } else {
                    qtyText = "units differ"
                    qtyPaint = mutedBig
                }

                val p = d.productsById[s.productId]
                tableRow(
                    cols, lefts, rowH,
                    listOf(
                        s.name to body,
                        p?.company to body,
                        p?.technicalName to body,
                        p?.formulation to body,
                        p?.registrationNumber to body,
                        qtyText to qtyPaint
                    )
                )

                // Where a figure could not be given, the two sides that could.
                // This is the one case that still needs a second line, because
                // it is two numbers in one column rather than a missing one.
                if (onHand == null) {
                    val split = "in ${Format.qty(s.boughtQty + s.returnedQty, s.boughtUnit)} \u00B7 out ${Format.qty(s.soldQty, s.soldUnit)}"
                    val t = clip(split, muted, cols.last().w + cols[4].w - cellPad * 2)
                    canvas.drawText(t, PAGE_W - MARGIN - cellPad - muted.measureText(t), y + 10f, muted)
                    y += 13f
                }
            }
        }

        // ---- 2. Batch-wise stock ----
        if (y > PAGE_H - 140f) newPage() else { y += 14f; canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, rule); y += 20f }

        val s2 = sec("Batch-wise stock")
        canvas.drawText(s2, MARGIN, y, section)
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
            // 140 + 80 + 100 + 130 + 70 + 78 + 82 + 82 = 762.
            val cols = listOf(
                Col("PRODUCT", 140f),
                Col("BATCH", 80f),
                Col("COMPANY", 100f),
                Col("SUPPLIER", 130f),
                Col("BILL", 70f),
                Col("BILL DATE", 78f),
                Col("EXPIRY", 82f, right = true),
                Col("LEFT", 82f, right = true)
            )
            var lefts = tableHead(cols)
            val rowH = 20f

            d.batches.forEachIndexed { index, b ->
                if (y + rowH > PAGE_H - 60f) {
                    newPage()
                    canvas.drawText("$s2 (continued)", MARGIN, y, section)
                    y += 18f
                    lefts = tableHead(cols)
                }
                if (index % 2 == 1) canvas.drawRect(MARGIN, y, PAGE_W - MARGIN, y + rowH, zebra)

                // Expiry, and how it reads: expired is the one word that must
                // not be missed on a page someone official is holding.
                val exp = b.expiryDate
                val expiryText = when {
                    exp == null -> null
                    b.hasExpired -> "EXPIRED"
                    else -> dayFmt.format(Date(exp))
                }
                val expiryPaint = when {
                    exp == null -> mutedBig
                    b.hasExpired -> red
                    (b.daysLeft ?: Int.MAX_VALUE) <= d.windowDays -> red
                    else -> body
                }

                tableRow(
                    cols, lefts, rowH,
                    listOf(
                        b.productName to body,
                        b.batchNumber to body,
                        b.company to body,
                        b.partyName to body,
                        b.billNumber to body,
                        dayFmt.format(Date(b.billDate)) to body,
                        expiryText to expiryPaint,
                        Format.qty(b.remaining, b.unit) to bodyBold
                    )
                )
            }
        }

        // ---- 3. Expiry report ----
        if (y > PAGE_H - 140f) newPage() else { y += 14f; canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, rule); y += 20f }

        val s3 = sec("Expiry report")
        canvas.drawText(s3, MARGIN, y, section)
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
            // 170 + 90 + 150 + 90 + 150 + 112 = 762.
            val cols = listOf(
                Col("PRODUCT", 170f),
                Col("BATCH", 90f),
                Col("SUPPLIER", 150f),
                Col("EXPIRY", 90f),
                Col("STATUS", 150f),
                Col("LEFT", 112f, right = true)
            )
            var lefts = tableHead(cols)
            val rowH = 20f

            fun expiryRow(index: Int, b: InspectorBatch) {
                if (y + rowH > PAGE_H - 60f) {
                    newPage()
                    canvas.drawText("$s3 (continued)", MARGIN, y, section)
                    y += 18f
                    lefts = tableHead(cols)
                }
                if (index % 2 == 1) canvas.drawRect(MARGIN, y, PAGE_W - MARGIN, y + rowH, zebra)

                val days = b.daysLeft
                val status = when {
                    b.hasExpired -> "EXPIRED" + (days?.let { " \u00B7 ${-it} days ago" } ?: "")
                    else -> "${days ?: 0} days left"
                }

                tableRow(
                    cols, lefts, rowH,
                    listOf(
                        b.productName to body,
                        b.batchNumber to body,
                        b.partyName to body,
                        b.expiryDate?.let { dayFmt.format(Date(it)) } to body,
                        status to red,
                        Format.qty(b.remaining, b.unit) to bodyBold
                    )
                )
            }

            // Numbered across BOTH lists so the zebra does not restart and
            // print two shaded rows against each other where they meet.
            (d.expired + d.expiringSoon).forEachIndexed { i, b -> expiryRow(i, b) }
        }

        // ---- 4. What the register could not say ----
        //
        // The gaps, listed by name rather than counted. A count tells the owner
        // there is a problem; the names tell him which bottle to pick up and
        // which four fields to type in before the next visit.
        if (d.incompleteProducts.isNotEmpty()) {
            if (y > PAGE_H - 140f) newPage() else { y += 14f; canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, rule); y += 20f }

            val s4 = sec("Label details still missing")
            canvas.drawText(s4, MARGIN, y, section)
            y += 14f
            canvas.drawText(
                "Each product below is in stock with one or more label fields never recorded.",
                MARGIN, y, muted
            )
            y += 18f

            // 300 + 462 = 762. A bare list of names was the fault the owner
            // caught: with three of the four filled in he could not tell what
            // the register still wanted. The second column answers exactly
            // that, so the page doubles as the list of what to go and type.
            val cols4 = listOf(
                Col("PRODUCT", 300f),
                Col("STILL NOT RECORDED", 462f)
            )
            var lefts4 = tableHead(cols4)
            val rowH4 = 18f

            d.incompleteProducts.take(40).forEachIndexed { index, (name, missing) ->
                if (y + rowH4 > PAGE_H - 60f) {
                    newPage()
                    canvas.drawText("$s4 (continued)", MARGIN, y, section)
                    y += 18f
                    lefts4 = tableHead(cols4)
                }
                if (index % 2 == 1) canvas.drawRect(MARGIN, y, PAGE_W - MARGIN, y + rowH4, zebra)
                tableRow(cols4, lefts4, rowH4, listOf(name to body, missing to red))
            }
            if (d.incompleteProducts.size > 40) {
                canvas.drawText(
                    "\u2026 and ${d.incompleteProducts.size - 40} more.", MARGIN + cellPad, y + 12f, mutedBig
                )
                y += 16f
            }
        }

        // ---- 5. Company-wise stock ----
        //
        // The same on-hand figures as section 1, gathered under the brand on
        // the label. An inspector asks "how much Bayer, how much FMC"; this is
        // that column read the other way round. Nothing new is computed — a row
        // whose units could not be subtracted still says so, exactly as above.
        if (d.companyStock.isNotEmpty()) {
            if (y > PAGE_H - 160f) newPage() else { y += 14f; canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, rule); y += 20f }

            val s5 = sec("Company-wise stock")
            canvas.drawText(s5, MARGIN, y, section)
            y += 18f

            // 330 + 250 + 182 = 762. The company is the heading above each
            // group, so it is not repeated as a column; the technical name
            // takes its place, because within ONE brand that is what tells two
            // products apart on an inspector's list.
            val cols = listOf(
                Col("PRODUCT", 330f),
                Col("TECHNICAL NAME", 250f),
                Col("ON HAND", 182f, right = true)
            )
            var lefts = tableHead(cols)
            val rowH = 18f

            d.companyStock.forEach { (company, list) ->
                if (y + 20f + rowH > PAGE_H - 60f) {
                    newPage()
                    canvas.drawText("$s5 (continued)", MARGIN, y, section)
                    y += 18f
                    lefts = tableHead(cols)
                }
                // THE BRAND HEADING, and it must not be mistakable for a row.
                //
                // The owner's objection, and it was exact: "Foji" and "urea"
                // sat at the same x in the same PRODUCT column, one merely
                // bold — so the page appeared to claim a company was a
                // product. A heading is a divider, not a value, so it now gets
                // its own tinted band across the full width, its label says
                // the word COMPANY out loud, and the count says "products".
                // The rows under it are indented past the band's label, which
                // is the second half of the same signal.
                canvas.drawRect(MARGIN, y, PAGE_W - MARGIN, y + 20f, groupFill)
                canvas.drawText("COMPANY", MARGIN + cellPad, y + 13.5f, groupTag)
                canvas.drawText(
                    clip(company, groupName, 420f),
                    MARGIN + cellPad + 52f, y + 13.5f, groupName
                )
                val count = if (list.size == 1) "1 product" else "${list.size} products"
                canvas.drawText(count, PAGE_W - MARGIN - cellPad - muted.measureText(count), y + 13.5f, muted)
                y += 20f

                list.forEach { s ->
                    if (y + rowH > PAGE_H - 60f) {
                        newPage()
                        canvas.drawText("$s5 (continued)", MARGIN, y, section)
                        y += 18f
                        lefts = tableHead(cols)
                    }
                    val onHand = s.onHand
                    val qtyText: String
                    val qtyPaint: Paint
                    if (onHand != null) {
                        qtyText = Format.qty(onHand, s.unit)
                        qtyPaint = if (onHand > 0) body else mutedBig
                    } else {
                        qtyText = "units differ"
                        qtyPaint = mutedBig
                    }
                    tableRow(
                        cols, lefts, rowH,
                        listOf(
                            // Indented, so a product can never be read as a
                            // heading or a heading as a product.
                            "    ${s.name}" to body,
                            d.productsById[s.productId]?.technicalName to body,
                            qtyText to qtyPaint
                        )
                    )
                }
                y += 6f
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
        canvas.drawText("$businessName \u00B7 Page $pageNo", footerX, y, muted)

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
