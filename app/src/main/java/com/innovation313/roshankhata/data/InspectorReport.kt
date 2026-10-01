package com.innovation313.roshankhata.data

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.innovation313.roshankhata.R
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
 * LAYOUT (redesigned 30 Sep 2026, from the owner's approved mockup and a
 * study of how Tally, Zoho Inventory, Vyapar and batch-expiry ERPs lay out a
 * stock register): a masthead with an "as of" date, six summary cards, then
 * the tables — current stock with In / Out / On hand (Tally's stock-flow
 * columns), ONE batch-and-expiry table where there used to be two listing the
 * same rows, company-wise stock, and a label checklist. No watermark: behind a
 * table it read as a trial copy, not a document. The "not a backup" and "how
 * to read" notes moved from two boxes at the top to one box before the
 * signatures, and the wording is unchanged.
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

    // The app's own green and gold, then quiet neutrals. Red is kept for
    // expired stock alone, amber for what needs attention soon, green for
    // what is in order — and each also differs in lightness, so the page
    // still reads when printed in black and white.
    private const val NAVY = 0xFF094C2E.toInt()
    private const val GOLD = 0xFFE1AF3F.toInt()
    private const val ON_NAVY = 0xFFCFE3D6.toInt()
    private const val INK = 0xFF1A1F1C.toInt()
    private const val NOTE_INK = 0xFF33403A.toInt()
    private const val MUTED = 0xFF4F5B55.toInt()
    private const val DASH = 0xFF767676.toInt()
    private const val HAIR = 0xFFD5DDD8.toInt()
    private const val HEAD_BG = 0xFFEAF1EC.toInt()
    private const val ZEBRA = 0xFFF6F9F7.toInt()
    private const val GROUP_BG = 0xFFF1F5F2.toInt()
    private const val RED_FG = 0xFFA61B12.toInt()
    private const val RED_BG = 0xFFFDECEA.toInt()
    private const val RED_EDGE = 0xFFF1B8B2.toInt()
    private const val AMBER_FG = 0xFF7A4A00.toInt()
    private const val AMBER_BG = 0xFFFFF1D1.toInt()
    private const val AMBER_EDGE = 0xFFEBCB85.toInt()
    private const val OK_FG = 0xFF1E6B45.toInt()
    private const val OK_BG = 0xFFE6F2EA.toInt()

    private val timeFmt = SimpleDateFormat("HH:mm", Locale.ENGLISH)
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
        val windowDays: Int,
        /**
         * The same products as [incompleteProducts], with each of the four
         * label fields as recorded or not — the checklist section prints a
         * tick or "Missing" per field instead of a comma-separated sentence.
         */
        val labelChecks: List<LabelCheck> = emptyList(),
        /**
         * Traded products counted by the Type on their label (Fertilizer,
         * Pesticide, Seed — free text, grouped ignoring case and spaces), the
         * unset ones last under one honest label. Printed as one line under
         * the summary cards (1 Oct).
         */
        val typeCounts: List<Pair<String, Int>> = emptyList()
    )

    /** One in-stock product and which of the four label fields it has. */
    data class LabelCheck(
        val name: String,
        val company: Boolean,
        val technical: Boolean,
        val formulation: Boolean,
        val registration: Boolean
    ) {
        val missing: Int get() = listOf(company, technical, formulation, registration).count { !it }
    }

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
        val incompleteRows = products
            .filter { it.id in idsInStock }
            .filter {
                it.company.isNullOrBlank() ||
                    it.registrationNumber.isNullOrBlank() ||
                    it.technicalName.isNullOrBlank() ||
                    it.formulation.isNullOrBlank()
            }
        val labelChecks = incompleteRows.map { p ->
            LabelCheck(
                name = p.name,
                company = !p.company.isNullOrBlank(),
                technical = !p.technicalName.isNullOrBlank(),
                formulation = !p.formulation.isNullOrBlank(),
                registration = !p.registrationNumber.isNullOrBlank()
            )
        }
        val incomplete = incompleteRows
            .map { p ->
                p.name to listOfNotNull(
                    context.getString(R.string.pdf_insp_miss_company).takeIf { p.company.isNullOrBlank() },
                    context.getString(R.string.pdf_insp_miss_technical).takeIf { p.technicalName.isNullOrBlank() },
                    context.getString(R.string.pdf_insp_miss_form).takeIf { p.formulation.isNullOrBlank() },
                    context.getString(R.string.pdf_insp_miss_reg).takeIf { p.registrationNumber.isNullOrBlank() }
                ).joinToString(", ")
            }

        // Company-wise: the same stock, grouped under the brand on the label.
        // A product with no company recorded is not dropped — it goes under one
        // honest heading, because a compliance total that quietly omits stock is
        // worse than one that admits what it could not label. Untouched products
        // are left out, matching section 1.
        val productsById = products.associateBy { it.id }
        val noCompany = context.getString(R.string.pdf_insp_no_company)
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

        val typeCounts = typeCounts(
            stock.filter { !it.isUntouched }.map { productsById[it.productId]?.productType },
            context.getString(R.string.pdf_insp_type_not_set)
        )

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
            windowDays = windowDays,
            labelChecks = labelChecks,
            typeCounts = typeCounts
        )
    }

    /**
     * "Fertilizer 3, Pesticide 5, Not set 2": one count per Type as typed.
     * "pesticide" and " Pesticide " are one group, shown as first typed;
     * biggest group first, the unset group always last.
     */
    fun typeCounts(types: List<String?>, notSet: String): List<Pair<String, Int>> {
        val shown = LinkedHashMap<String, String>()
        val counts = HashMap<String, Int>()
        var unset = 0
        for (t in types) {
            val clean = t?.trim()?.replace(Regex("""\s+"""), " ")?.takeIf { it.isNotEmpty() }
            if (clean == null) { unset++; continue }
            val key = clean.lowercase()
            shown.getOrPut(key) { clean }
            counts[key] = (counts[key] ?: 0) + 1
        }
        val known = shown.entries
            .sortedWith(compareByDescending<Map.Entry<String, String>> { counts[it.key] ?: 0 }.thenBy { it.key })
            .map { it.value to (counts[it.key] ?: 0) }
        return if (unset > 0) known + (notSet to unset) else known
    }

    /**
     * Two passes, and the page number is the whole reason.
     *
     * The footer now reads "Page 2 of 3" on EVERY page, not just the last —
     * the owner's point, and a fair one: a register handed over loose with
     * no numbers on it cannot be shown to be complete, and pages 1 and 2 of
     * his three carried nothing at the foot at all. But a total cannot be
     * known before the first footer is drawn, because it depends on where
     * the rows happen to fall, and PdfDocument will not reopen a finished
     * page to write it in afterwards.
     *
     * So the register is laid out twice: once to count, once for real. The
     * first document is thrown away unwritten. Every measurement in [draw]
     * comes from the data and the fixed page size — the footer's own width
     * changes nothing above it — so the second pass breaks its pages in
     * exactly the same places as the first, and the count holds.
     */
    private fun render(context: Context, d: ReportData): File? {
        val pageCount = try {
            val (counting, n) = draw(context, d, null)
            counting.close()
            n
        } catch (e: Exception) {
            // A failed count must not cost the owner his register: fall
            // through with no total and print the plain "Page 2" footer.
            null
        }

        val (doc, _) = draw(context, d, pageCount)

        val dir = File(context.cacheDir, "statements").apply { mkdirs() }
        val file = File(dir, "RoshanKhata_StockRegister_${fileFmt.format(Date())}.pdf")

        return try {
            FileOutputStream(file).use { doc.writeTo(it) }
            PdfBranding.applyLinks(doc, file)
            doc.close()
            file
        } catch (e: Exception) {
            doc.close()
            null
        }
    }

    /** What one table cell holds: text (null prints a dash), a coloured chip, or a tick. */
    private sealed class Cell {
        class Text(val text: String?, val paint: Paint) : Cell()
        class Chip(val text: String, val fg: Int, val bg: Int) : Cell()
        object Tick : Cell()
    }

    /** A column: its width, and whether its contents sit left, right or centred. */
    private class Col(val title: String, val w: Float, val align: Paint.Align = Paint.Align.LEFT, val indent: Float = 0f)

    /**
     * Lays the whole register out and returns the finished document with the
     * number of pages it came to. [totalPages] is null on the counting pass,
     * and the footer then omits the "of N" it cannot yet know.
     */
    private fun draw(context: Context, d: ReportData, totalPages: Int?): Pair<PdfDocument, Int> {
        val doc = PdfDocument()
        val now = Date()
        val rtl = PdfRtl.isRtl(context)

        // Manrope, the same bundled face the invoices use, so every document
        // the shop hands over looks like it came from one place. Tabular
        // figures keep a column of quantities lined up digit under digit.
        val tf = InvoiceFonts.manrope(context)
        val tfBold = InvoiceFonts.bold(tf)
        fun paint(color: Int, size: Float, bold: Boolean = false, spaced: Float = 0f) = Paint().apply {
            this.color = color
            textSize = size
            typeface = if (bold) tfBold else tf
            isAntiAlias = true
            fontFeatureSettings = "tnum"
            // Letter spacing pulls joined scripts (Urdu, Arabic) apart, so it
            // is only ever applied to a left-to-right page.
            if (!rtl && spaced > 0f) letterSpacing = spaced
        }

        val bandName = paint(Color.WHITE, 19f, bold = true)
        val bandSmall = paint(ON_NAVY, 9f)
        val bandTitle = paint(GOLD, 8.5f, bold = true, spaced = 0.12f)
        val bandAsOf = paint(Color.WHITE, 13f, bold = true)
        val contName = paint(Color.WHITE, 11f, bold = true)
        val contTitle = paint(GOLD, 8.5f, bold = true, spaced = 0.08f)
        val monogram = paint(NAVY, 16f, bold = true).apply { textAlign = Paint.Align.CENTER }
        val secNo = paint(OK_FG, 9f, bold = true)
        val secTitle = paint(NAVY, 13f, bold = true)
        val note = paint(MUTED, 8.5f)
        val head = paint(NAVY, 8f, bold = true)
        val body = paint(INK, 9.5f)
        val bodyBold = paint(INK, 9.5f, bold = true)
        val strong = paint(NAVY, 9.5f, bold = true)
        val negative = paint(RED_FG, 9.5f, bold = true)
        val dash = paint(DASH, 9.5f)
        val muted = paint(MUTED, 9f)
        val chipText = paint(INK, 8f, bold = true)
        val cardLabel = paint(MUTED, 8f)
        val footerText = paint(MUTED, 8f)
        val groupTag = paint(MUTED, 7.5f, bold = true, spaced = 0.08f)
        val groupName = paint(NAVY, 10.5f, bold = true)
        val noteTitle = paint(NAVY, 8.5f, bold = true)
        val noteBody = paint(NOTE_INK, 8.5f)

        fun fill(color: Int) = Paint().apply { this.color = color; isAntiAlias = true }
        fun stroke(color: Int, width: Float) = Paint().apply {
            this.color = color; style = Paint.Style.STROKE; strokeWidth = width; isAntiAlias = true
        }
        val navyFill = fill(NAVY)
        val goldFill = fill(GOLD)
        val headFill = fill(HEAD_BG)
        val zebraFill = fill(ZEBRA)
        val groupFill = fill(GROUP_BG)
        val hair = stroke(HAIR, 0.6f)
        val headRule = stroke(NAVY, 1.1f)
        val signRule = stroke(INK, 0.8f)
        val tickPaint = stroke(OK_FG, 1.6f).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }

        val right = PAGE_W - MARGIN
        val usable = right - MARGIN

        var page = PdfRtl.startPage(context, doc, PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        var canvas: Canvas = page.canvas
        var y: Float
        var pageNo = 1

        val brandLogo = PdfBranding.logo(context)
        val shopLogo = BusinessProfile.loadLogo(context)
        val businessName = d.businessName ?: context.getString(R.string.app_name)

        // Shorten text that would collide with the column to its right.
        fun clip(text: String, paint: Paint, maxW: Float): String {
            if (paint.measureText(text) <= maxW) return text
            var end = text.length
            while (end > 1 && paint.measureText(text.substring(0, end) + "\u2026") > maxW) end--
            return text.substring(0, end) + "\u2026"
        }

        // Word wrap for the few places a sentence must be read whole.
        fun wrap(text: String, paint: Paint, maxW: Float): List<String> {
            val lines = mutableListOf<String>()
            var line = ""
            text.split(' ').filter { it.isNotEmpty() }.forEach { word ->
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(candidate) <= maxW || line.isEmpty()) {
                    line = candidate
                } else {
                    lines += line
                    line = word
                }
            }
            if (line.isNotEmpty()) lines += line
            return lines.map { clip(it, paint, maxW) }
        }

        /**
         * The shop's initials, for the square beside its name. The register
         * carries HIS name; the app's own mark is kept to the footer. With no
         * business name set, the app's logo stands in.
         */
        val initials = d.businessName?.trim()
            ?.split(Regex("\\s+"))
            ?.filter { it.isNotEmpty() }
            ?.take(2)
            ?.joinToString("") { String(Character.toChars(it.codePointAt(0))).uppercase(Locale.getDefault()) }
            ?.takeIf { it.isNotEmpty() }

        /**
         * The full masthead on page 1, a slim one after it — a page that gets
         * separated from the first must still say whose register it is, and
         * "continued" so nobody takes it for the start of a fresh one.
         */
        fun header(first: Boolean = true): Float {
            if (!first) {
                canvas.drawRect(0f, 0f, PAGE_W.toFloat(), 34f, navyFill)
                canvas.drawRect(0f, 34f, PAGE_W.toFloat(), 36f, goldFill)
                PdfRtl.drawText(canvas, clip(businessName, contName, usable / 2f), MARGIN, 22f, contName)
                val cont = context.getString(R.string.pdf_insp_title_cont).uppercase(Locale.getDefault())
                val c = clip(cont, contTitle, usable / 2f - 10f)
                PdfRtl.drawText(canvas, c, right - contTitle.measureText(c), 21.5f, contTitle)
                return 58f
            }
            canvas.drawRect(0f, 0f, PAGE_W.toFloat(), 84f, navyFill)
            canvas.drawRect(0f, 84f, PAGE_W.toFloat(), 87f, goldFill)

            val square = RectF(MARGIN, 20f, MARGIN + 44f, 64f)
            canvas.drawRoundRect(square, 10f, 10f, goldFill)
            if (shopLogo != null) {
                // The shop's own logo, on white so any logo colours read.
                canvas.drawRoundRect(square, 10f, 10f, fill(Color.WHITE))
                val box = RectF(square.left + 4f, square.top + 4f, square.right - 4f, square.bottom - 4f)
                val scale = minOf(box.width() / shopLogo.width, box.height() / shopLogo.height)
                val w = shopLogo.width * scale
                val h = shopLogo.height * scale
                PdfRtl.drawBitmap(canvas, shopLogo,
                    android.graphics.Rect(0, 0, shopLogo.width, shopLogo.height),
                    RectF(box.centerX() - w / 2f, box.centerY() - h / 2f, box.centerX() + w / 2f, box.centerY() + h / 2f),
                    Paint().apply { isAntiAlias = true; isFilterBitmap = true }
                )
            } else if (initials != null) {
                PdfRtl.drawText(canvas, initials, square.centerX(), square.centerY() + 5.5f, monogram)
            } else {
                brandLogo?.let { mark ->
                    PdfRtl.drawBitmap(canvas, mark,
                        android.graphics.Rect(0, 0, mark.width, mark.height),
                        RectF(square.left + 5f, square.top + 5f, square.right - 5f, square.bottom - 5f),
                        Paint().apply { isAntiAlias = true; isFilterBitmap = true }
                    )
                }
            }

            // Right-hand block first, so the shop's lines know how much room is left.
            val title = context.getString(R.string.pdf_insp_title).uppercase(Locale.getDefault())
            val asOf = context.getString(R.string.pdf_insp_as_of, dayFmt.format(now))
            val generated = context.getString(R.string.pdf_insp_generated, timeFmt.format(now))
            val rightW = maxOf(bandTitle.measureText(title), bandAsOf.measureText(asOf), bandSmall.measureText(generated))
            PdfRtl.drawText(canvas, title, right - bandTitle.measureText(title), 34f, bandTitle)
            PdfRtl.drawText(canvas, asOf, right - bandAsOf.measureText(asOf), 52f, bandAsOf)
            PdfRtl.drawText(canvas, generated, right - bandSmall.measureText(generated), 66f, bandSmall)

            val textX = MARGIN + 56f
            val textW = right - rightW - 24f - textX
            PdfRtl.drawText(canvas, clip(businessName, bandName, textW), textX, 38f, bandName)
            var lineY = 53f
            d.businessAddress?.takeIf { it.isNotBlank() }?.let {
                PdfRtl.drawText(canvas, clip(it, bandSmall, textW), textX, lineY, bandSmall)
                lineY += 12f
            }
            d.strn?.takeIf { it.isNotBlank() }?.let {
                PdfRtl.drawText(canvas, clip(context.getString(R.string.pdf_insp_ntn, it), bandSmall, textW), textX, lineY, bandSmall)
            }
            return 105f
        }

        /** Drawn on the way OUT of every page, so no page can leave without it. */
        fun footer() {
            val fy = PAGE_H - 30f
            canvas.drawLine(MARGIN, fy - 16f, right, fy - 16f, hair)
            var footerX = MARGIN
            brandLogo?.let { mark ->
                val size = 16f
                PdfRtl.drawBitmap(canvas,
                    mark,
                    android.graphics.Rect(0, 0, mark.width, mark.height),
                    RectF(footerX, fy - 12f, footerX + size, fy + 4f),
                    Paint().apply { isAntiAlias = true; isFilterBitmap = true }
                )
                footerX += size + 6f
            }
            val label = if (totalPages != null) {
                context.getString(R.string.pdf_insp_footer_of, businessName, pageNo, totalPages)
            } else {
                context.getString(R.string.pdf_insp_footer, businessName, pageNo)
            }
            PdfRtl.drawText(canvas, label, footerX, fy, footerText)
        }

        fun newPage() {
            footer()
            doc.finishPage(page)
            pageNo++
            page = PdfRtl.startPage(context, doc, PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            canvas = page.canvas
            y = header(first = false)
        }

        y = header()

        // ---- Table machinery: one definition, used by every table ----
        val cellPad = 6f
        val headH = 20f
        val contentBottom = PAGE_H - 56f

        fun tableHead(cols: List<Col>): List<Float> {
            canvas.drawRect(MARGIN, y, right, y + headH, headFill)
            canvas.drawLine(MARGIN, y + headH, right, y + headH, headRule)
            val lefts = mutableListOf<Float>()
            var x = MARGIN
            cols.forEach { c ->
                lefts += x
                val t = clip(c.title, head, c.w - cellPad * 2)
                val tx = when (c.align) {
                    Paint.Align.RIGHT -> x + c.w - cellPad - head.measureText(t)
                    Paint.Align.CENTER -> x + (c.w - head.measureText(t)) / 2f
                    else -> x + cellPad + c.indent
                }
                PdfRtl.drawText(canvas, t, tx, y + 13.3f, head)
                x += c.w
            }
            y += headH
            return lefts
        }

        /**
         * One row. A null text prints an em dash in grey: the register's own
         * rule is that a dash means NEVER RECORDED, never "none".
         */
        fun tableRow(cols: List<Col>, lefts: List<Float>, rowH: Float, cells: List<Cell>, shaded: Boolean) {
            if (shaded) canvas.drawRect(MARGIN, y, right, y + rowH, zebraFill)
            val baseline = y + rowH / 2f + 3.4f
            cols.forEachIndexed { i, c ->
                val inner = c.w - cellPad * 2 - c.indent
                fun xFor(w: Float) = when (c.align) {
                    Paint.Align.RIGHT -> lefts[i] + c.w - cellPad - w
                    Paint.Align.CENTER -> lefts[i] + (c.w - w) / 2f
                    else -> lefts[i] + cellPad + c.indent
                }
                when (val cell = cells[i]) {
                    is Cell.Text -> {
                        val p = if (cell.text.isNullOrBlank()) dash else cell.paint
                        val t = clip(cell.text?.takeIf { it.isNotBlank() } ?: "\u2014", p, inner)
                        PdfRtl.drawText(canvas, t, xFor(p.measureText(t)), baseline, p)
                    }
                    is Cell.Chip -> {
                        val cp = Paint(chipText).apply { color = cell.fg }
                        val t = clip(cell.text, cp, inner - 12f)
                        val w = cp.measureText(t) + 12f
                        val x = xFor(w)
                        val top = y + rowH / 2f - 6.5f
                        canvas.drawRoundRect(RectF(x, top, x + w, top + 13f), 6.5f, 6.5f, fill(cell.bg))
                        PdfRtl.drawText(canvas, t, x + 6f, top + 9.4f, cp)
                    }
                    Cell.Tick -> {
                        val cx = xFor(10f) + 5f
                        val cy = y + rowH / 2f
                        val path = Path().apply {
                            moveTo(cx - 4.5f, cy)
                            lineTo(cx - 1.2f, cy + 3.3f)
                            lineTo(cx + 5f, cy - 3.8f)
                        }
                        canvas.drawPath(path, tickPaint)
                    }
                }
            }
            canvas.drawLine(MARGIN, y + rowH, right, y + rowH, hair)
            y += rowH
        }

        // ---- Sections ----
        //
        // Numbers are COUNTED, not written in: two sections are conditional,
        // and a register printing 1, 2, 4 leaves the reader hunting for a 3.
        var sectionNo = 0

        /**
         * Keeps a heading with what it introduces: [blockH] is the heading,
         * the table head and the first rows, which must not be split.
         */
        fun startSection(blockH: Float) {
            if (y + 22f + blockH > contentBottom) newPage() else y += 22f
        }

        fun heading(title: String, noteText: String?, number: Int) {
            val no = String.format(Locale.ENGLISH, "%02d", number)
            val baseline = y + 12f
            PdfRtl.drawText(canvas, no, MARGIN, baseline, secNo)
            val tx = MARGIN + secNo.measureText(no) + 8f
            PdfRtl.drawText(canvas, title, tx, baseline, secTitle)
            noteText?.let {
                val room = right - (tx + secTitle.measureText(title) + 24f)
                if (room > 60f) {
                    val t = clip(it, note, room)
                    PdfRtl.drawText(canvas, t, right - note.measureText(t), baseline, note)
                }
            }
            y += 22f
        }

        fun cont(title: String): String = context.getString(R.string.pdf_insp_sec_cont, title)

        /** A page break inside a table: new page, the heading again marked continued, the column titles again. */
        fun breakTable(title: String, number: Int, cols: List<Col>): List<Float> {
            newPage()
            heading(cont(title), null, number)
            return tableHead(cols)
        }

        // ---- Summary cards ----
        class Card(val label: String, val value: Int, val fg: Int, val bg: Int, val edge: Int)
        val neutral = Triple(NAVY, Color.WHITE, HAIR)
        fun card(label: String, value: Int, alert: Triple<Int, Int, Int>?): Card {
            val (fg, bg, edge) = if (value > 0 && alert != null) alert else neutral
            return Card(label, value, fg, bg, edge)
        }
        val redTone = Triple(RED_FG, RED_BG, RED_EDGE)
        val amberTone = Triple(AMBER_FG, AMBER_BG, AMBER_EDGE)
        val cards = listOf(
            card(context.getString(R.string.pdf_insp_sum_products), d.stock.size, null),
            card(context.getString(R.string.pdf_insp_sum_batches), d.batches.size, null),
            card(context.getString(R.string.pdf_insp_sum_expired), d.expired.size, redTone),
            card(context.getString(R.string.pdf_insp_sum_expiring, d.windowDays), d.expiringSoon.size, amberTone),
            card(context.getString(R.string.pdf_insp_sum_no_expiry), d.noExpiryCount, amberTone),
            card(context.getString(R.string.pdf_insp_sum_incomplete), d.incompleteProducts.size, amberTone)
        )
        val gap = 8f
        val cardW = (usable - gap * (cards.size - 1)) / cards.size
        val cardH = 50f
        cards.forEachIndexed { i, c ->
            val x = MARGIN + i * (cardW + gap)
            val box = RectF(x, y, x + cardW, y + cardH)
            canvas.drawRoundRect(box, 8f, 8f, fill(c.bg))
            canvas.drawRoundRect(box, 8f, 8f, stroke(c.edge, 0.8f))
            val valuePaint = paint(c.fg, 16f, bold = true)
            PdfRtl.drawText(canvas, c.value.toString(), x + 10f, y + 21f, valuePaint)
            val labelPaint = if (c.fg == NAVY) cardLabel else paint(c.fg, 8f, bold = true)
            wrap(c.label, labelPaint, cardW - 20f).take(2).forEachIndexed { li, line ->
                PdfRtl.drawText(canvas, line, x + 10f, y + 33f + li * 9.5f, labelPaint)
            }
        }
        y += cardH

        // One line, by Type: only when at least one product has a Type, so a
        // shop that never filled the field is not shown "Not set 12".
        if (d.typeCounts.any { it.first != context.getString(R.string.pdf_insp_type_not_set) }) {
            val text = context.getString(
                R.string.pdf_insp_by_type,
                d.typeCounts.joinToString("  ·  ") { (type, n) -> "$type $n" }
            )
            y += 14f
            PdfRtl.drawText(canvas, clip(text, cardLabel, usable), MARGIN, y, cardLabel)
        }

        // ---- 1. Current stock: In / Out / On hand ----
        //
        // Tally's stock-flow columns. In is what the bills brought (plus
        // returns), Out is what was sold. Where the two are counted in
        // different units no subtraction is attempted and On hand says so —
        // but In and Out still show, each in its own unit, so the owner sees
        // both figures on the same line instead of a second line under it.
        val traded = d.stock.filter { !it.isUntouched }
        startSection(if (traded.isEmpty()) 36f else 82f)
        val n1 = ++sectionNo
        val s1 = context.getString(R.string.pdf_insp_sec_current)
        heading(s1, context.getString(R.string.pdf_insp_current_note), n1)

        if (traded.isEmpty()) {
            PdfRtl.drawText(canvas, context.getString(R.string.pdf_insp_no_movement), MARGIN, y + 4f, muted)
            y += 10f
        } else {
            // 24 + 150 + 110 + 130 + 64 + 90 + 64 + 64 + 66 = 762
            val cols = listOf(
                Col("#", 24f),
                Col(context.getString(R.string.pdf_insp_col_product), 150f),
                Col(context.getString(R.string.pdf_insp_col_company), 110f),
                Col(context.getString(R.string.pdf_insp_col_technical), 130f),
                Col(context.getString(R.string.pdf_insp_col_form), 64f),
                Col(context.getString(R.string.pdf_insp_col_reg), 90f),
                Col(context.getString(R.string.pdf_insp_col_in), 64f, Paint.Align.RIGHT),
                Col(context.getString(R.string.pdf_insp_col_out), 64f, Paint.Align.RIGHT),
                Col(context.getString(R.string.pdf_insp_col_onhand), 66f, Paint.Align.RIGHT)
            )
            var lefts = tableHead(cols)
            val rowH = 20f
            traded.forEachIndexed { index, s ->
                if (y + rowH > contentBottom) lefts = breakTable(s1, n1, cols)
                val onHand = s.onHand
                val onHandCell = when {
                    onHand == null -> Cell.Text(context.getString(R.string.pdf_insp_units_differ), muted)
                    onHand < 0 -> Cell.Text(Format.qty(onHand, s.unit), negative)
                    onHand == 0.0 -> Cell.Text(Format.qty(onHand, s.unit), muted)
                    else -> Cell.Text(Format.qty(onHand, s.unit), strong)
                }
                val p = d.productsById[s.productId]
                tableRow(cols, lefts, rowH, listOf(
                    Cell.Text((index + 1).toString(), muted),
                    Cell.Text(s.name, bodyBold),
                    Cell.Text(p?.company, body),
                    Cell.Text(p?.technicalName, body),
                    Cell.Text(p?.formulation, body),
                    Cell.Text(p?.registrationNumber, body),
                    Cell.Text(Format.qty(s.boughtQty + s.returnedQty, s.boughtUnit ?: s.soldUnit), body),
                    Cell.Text(Format.qty(s.soldQty, s.soldUnit ?: s.boughtUnit), body),
                    onHandCell
                ), shaded = index % 2 == 1)
            }
            // No grand total when products are counted in different units —
            // Tally leaves the quantity total blank for exactly this reason,
            // and a sum of kilos and litres would be a number that means nothing.
            val units = traded.mapNotNull { it.unit?.trim()?.lowercase()?.takeIf { u -> u.isNotEmpty() } }.toSet()
            if (units.size > 1) {
                PdfRtl.drawText(canvas, context.getString(R.string.pdf_insp_no_total), MARGIN, y + 13f, note)
                y += 16f
            }
        }

        // ---- 2. Batch & expiry, one table ----
        //
        // Batch-wise and expiry used to be two sections listing the same rows.
        // Now one: expired first, then expiring within the window, then the
        // rest, each group keeping the DAO's own order (product, then soonest
        // expiry) so this can never disagree with the expiry screen.
        startSection(if (d.batches.isEmpty()) 36f else 82f)
        val n2 = ++sectionNo
        val s2 = context.getString(R.string.pdf_insp_sec_batch_expiry)
        heading(s2, context.getString(R.string.pdf_insp_batch_expiry_note, d.windowDays), n2)

        if (d.batches.isEmpty()) {
            PdfRtl.drawText(canvas, context.getString(R.string.pdf_insp_no_batches), MARGIN, y + 4f, muted)
            y += 10f
        } else {
            val expiredIds = d.expired.map { it.itemId }.toSet()
            val soonIds = d.expiringSoon.map { it.itemId }.toSet()
            val ordered = d.expired + d.expiringSoon +
                d.batches.filter { it.itemId !in expiredIds && it.itemId !in soonIds }

            // 140 + 76 + 150 + 60 + 76 + 76 + 110 + 74 = 762
            val cols = listOf(
                Col(context.getString(R.string.pdf_insp_col_product), 140f),
                Col(context.getString(R.string.pdf_insp_col_batch), 76f),
                Col(context.getString(R.string.pdf_insp_col_supplier), 150f),
                Col(context.getString(R.string.pdf_insp_col_bill), 60f),
                Col(context.getString(R.string.pdf_insp_col_bill_date), 76f),
                Col(context.getString(R.string.pdf_insp_col_expiry), 76f),
                Col(context.getString(R.string.pdf_insp_col_status), 110f),
                Col(context.getString(R.string.pdf_insp_col_left), 74f, Paint.Align.RIGHT)
            )
            var lefts = tableHead(cols)
            val rowH = 20f
            ordered.forEachIndexed { index, b ->
                if (y + rowH > contentBottom) lefts = breakTable(s2, n2, cols)
                val exp = b.expiryDate
                val isExpired = b.itemId in expiredIds
                val isSoon = b.itemId in soonIds
                val status: Cell = when {
                    isExpired -> Cell.Chip(
                        b.daysLeft?.let { context.getString(R.string.pdf_insp_expired_ago, -it) }
                            ?: context.getString(R.string.pdf_insp_expired),
                        RED_FG, RED_BG
                    )
                    isSoon -> Cell.Chip(context.getString(R.string.pdf_insp_days_left, b.daysLeft ?: 0), AMBER_FG, AMBER_BG)
                    exp == null -> Cell.Text(null, body)
                    else -> Cell.Chip(context.getString(R.string.pdf_insp_status_ok), OK_FG, OK_BG)
                }
                tableRow(cols, lefts, rowH, listOf(
                    Cell.Text(b.productName, bodyBold),
                    Cell.Text(b.batchNumber, body),
                    Cell.Text(b.partyName, body),
                    Cell.Text(b.billNumber, body),
                    Cell.Text(dayFmt.format(Date(b.billDate)), body),
                    Cell.Text(exp?.let { dayFmt.format(Date(it)) }, if (isExpired || isSoon) bodyBold else body),
                    status,
                    Cell.Text(Format.qty(b.remaining, b.unit), bodyBold)
                ), shaded = index % 2 == 1)
            }
        }

        // ---- 3. Company-wise stock ----
        //
        // The same on-hand figures as section 1, under the brand on the label.
        // The company is a tinted band that says COMPANY out loud, and the
        // products under it are indented, so a heading can never be read as
        // a product.
        if (d.companyStock.isNotEmpty()) {
            startSection(22f + headH + 20f + 36f)
            val n3 = ++sectionNo
            val s3 = context.getString(R.string.pdf_insp_sec_company)
            heading(s3, null, n3)

            // 330 + 250 + 182 = 762
            val cols = listOf(
                Col(context.getString(R.string.pdf_insp_col_product), 330f, indent = 14f),
                Col(context.getString(R.string.pdf_insp_col_technical), 250f),
                Col(context.getString(R.string.pdf_insp_col_onhand), 182f, Paint.Align.RIGHT)
            )
            var lefts = tableHead(cols)
            val rowH = 18f
            val noCompany = context.getString(R.string.pdf_insp_no_company)

            d.companyStock.forEach { (company, list) ->
                // A band stranded above a break reads as a company with no stock.
                if (y + 20f + rowH > contentBottom) lefts = breakTable(s3, n3, cols)
                canvas.drawRect(MARGIN, y, right, y + 20f, groupFill)
                val tag = context.getString(R.string.pdf_insp_company_tag)
                PdfRtl.drawText(canvas, tag, MARGIN + cellPad, y + 13.5f, groupTag)
                val nameX = MARGIN + cellPad + groupTag.measureText(tag) + 10f
                val namePaint = if (company == noCompany) Paint(groupName).apply { color = AMBER_FG } else groupName
                PdfRtl.drawText(canvas, clip(company, namePaint, 420f), nameX, y + 13.5f, namePaint)
                val count = context.resources.getQuantityString(R.plurals.pdf_insp_products, list.size, list.size)
                PdfRtl.drawText(canvas, count, right - cellPad - note.measureText(count), y + 13.5f, note)
                y += 20f

                list.forEach { s ->
                    if (y + rowH > contentBottom) lefts = breakTable(s3, n3, cols)
                    val onHand = s.onHand
                    val qty = when {
                        onHand == null -> Cell.Text(context.getString(R.string.pdf_insp_units_differ), muted)
                        onHand < 0 -> Cell.Text(Format.qty(onHand, s.unit), negative)
                        onHand == 0.0 -> Cell.Text(Format.qty(onHand, s.unit), muted)
                        else -> Cell.Text(Format.qty(onHand, s.unit), bodyBold)
                    }
                    tableRow(cols, lefts, rowH, listOf(
                        Cell.Text(s.name, body),
                        Cell.Text(d.productsById[s.productId]?.technicalName, body),
                        qty
                    ), shaded = false)
                }
            }
        }

        // ---- 4. Label details checklist ----
        //
        // One tick or "Missing" per field, so the owner sees at a glance
        // which bottle to pick up and which box to fill before the next visit.
        if (d.labelChecks.isNotEmpty()) {
            startSection(22f + headH + 40f)
            val n4 = ++sectionNo
            val s4 = context.getString(R.string.pdf_insp_sec_checklist)
            heading(s4, context.getString(R.string.pdf_insp_missing_note), n4)

            // 240 + 115 * 4 + 62 = 762
            val cols = listOf(
                Col(context.getString(R.string.pdf_insp_col_product), 240f),
                Col(context.getString(R.string.pdf_insp_col_company), 115f, Paint.Align.CENTER),
                Col(context.getString(R.string.pdf_insp_col_technical), 115f, Paint.Align.CENTER),
                Col(context.getString(R.string.pdf_insp_col_form), 115f, Paint.Align.CENTER),
                Col(context.getString(R.string.pdf_insp_col_reg), 115f, Paint.Align.CENTER),
                Col(context.getString(R.string.pdf_insp_col_missing), 62f, Paint.Align.RIGHT)
            )
            var lefts = tableHead(cols)
            val rowH = 20f
            val missingChip = Cell.Chip(context.getString(R.string.pdf_insp_chip_missing), AMBER_FG, AMBER_BG)
            fun mark(has: Boolean): Cell = if (has) Cell.Tick else missingChip

            d.labelChecks.take(40).forEachIndexed { index, c ->
                if (y + rowH > contentBottom) lefts = breakTable(s4, n4, cols)
                tableRow(cols, lefts, rowH, listOf(
                    Cell.Text(c.name, bodyBold),
                    mark(c.company),
                    mark(c.technical),
                    mark(c.formulation),
                    mark(c.registration),
                    Cell.Text(context.getString(R.string.pdf_insp_missing_of, c.missing, 4), bodyBold)
                ), shaded = index % 2 == 1)
            }
            if (d.labelChecks.size > 40) {
                PdfRtl.drawText(canvas,
                    context.getString(R.string.pdf_insp_and_more, d.labelChecks.size - 40),
                    MARGIN + cellPad, y + 13f, muted
                )
                y += 16f
            }
        }

        // ---- How to read, then the signatures ----
        //
        // The same words as the two boxes that used to open page 1, gathered
        // into one note placed where it is read: just before the owner signs.
        val noteW = usable - 28f
        val para1 = wrap(
            context.getString(R.string.pdf_insp_how_1) + " " + context.getString(R.string.pdf_insp_how_2),
            noteBody, noteW
        )
        val para2 = wrap(
            context.getString(R.string.pdf_insp_not_backup) + " " +
                context.getString(R.string.pdf_rep_not_backup_restore, context.getString(R.string.app_name)),
            noteBody, noteW
        )
        val lineH = 11f
        val noteH = 12f + lineH + (para1.size + para2.size) * lineH + 4f + 8f
        val signH = 62f
        val closing = 22f + noteH + 14f + signH
        // The download banner sits at PAGE_H - 96; the closing block ends above it.
        if (y + closing > PAGE_H - 104f) newPage()
        y += 22f

        val noteBox = RectF(MARGIN, y, right, y + noteH)
        canvas.drawRoundRect(noteBox, 8f, 8f, fill(ZEBRA))
        canvas.drawRoundRect(noteBox, 8f, 8f, stroke(HAIR, 0.8f))
        var ny = y + 12f + 8f
        PdfRtl.drawText(canvas, clip(context.getString(R.string.pdf_insp_how_to_read), noteTitle, noteW), MARGIN + 14f, ny, noteTitle)
        ny += lineH
        para1.forEach { PdfRtl.drawText(canvas, it, MARGIN + 14f, ny, noteBody); ny += lineH }
        ny += 4f
        para2.forEach { PdfRtl.drawText(canvas, it, MARGIN + 14f, ny, noteBody); ny += lineH }
        y += noteH + 14f

        val boxW = (usable - 20f) / 2f
        val signs = listOf(
            R.string.pdf_insp_dealer_sign to MARGIN,
            R.string.pdf_insp_inspector_sign to MARGIN + boxW + 20f
        )
        val dateLabel = context.getString(R.string.pdf_insp_date_blank)
        signs.forEach { (label, x) ->
            val box = RectF(x, y, x + boxW, y + signH)
            canvas.drawRoundRect(box, 8f, 8f, stroke(HAIR, 0.8f))
            canvas.drawLine(x + 14f, y + 38f, x + boxW - 14f, y + 38f, signRule)
            val l = clip(context.getString(label), note, boxW / 2f)
            PdfRtl.drawText(canvas, l, x + 14f, y + 52f, note)
            PdfRtl.drawText(canvas, dateLabel, x + boxW - 14f - note.measureText(dateLabel), y + 52f, note)
        }
        y += signH

        // ---- Download banner, just above the last page's footer ----
        PdfBranding.drawDownloadBanner(context, doc, canvas, MARGIN, PAGE_H - 96f, usable)

        footer()
        doc.finishPage(page)
        return doc to pageNo
    }
}
