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
 * The other half of what an inspection asks for: not what is on the shelf
 * today, but what came in and what went out over a period.
 *
 * The stock register ([InspectorReport]) is deliberately a fact about TODAY and
 * refuses a date range, because "what was on the shelf last March" cannot be
 * reconstructed. A sales-and-purchase register is the opposite: it is a fact
 * ABOUT a period, and it can be reconstructed honestly, because every ledger
 * entry and every supplier bill already carries its own date. So this document
 * takes the two dates the stock register correctly refuses.
 *
 * WHAT COUNTS AS A SALE, IN WORDS. A sale is a ledger "I Gave" entry that
 * carried goods — a quantity written or a product linked — and never a bare
 * cash movement or a benevolent loan. The definition lives in one place, the
 * DAO's WHERE clause, and is printed on the page so the owner is not signing a
 * total whose meaning he has to guess.
 *
 * Same header band, same "not a valuation" honesty, same footer as the stock
 * register and the ledger report — one family of documents, so the owner
 * recognises this one as his own too.
 */
object RegisterReport {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f

    private const val NAVY = 0xFF094C2E.toInt()
    private const val GOLD = 0xFFE1AF3F.toInt()
    private const val GREY = 0xFF7A7A7A.toInt()
    private const val WARN_BG = 0xFFFFF6E0.toInt()
    private const val WARN_FG = 0xFF5C4A16.toInt()

    private val dateFmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.ENGLISH)
    private val dayFmt = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
    private val rowDayFmt = SimpleDateFormat("dd MMM", Locale.ENGLISH)
    private val fileFmt = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.ENGLISH)

    data class ReportData(
        val businessName: String?,
        val businessAddress: String?,
        val strn: String?,
        val from: Long,
        val to: Long,
        val sales: List<SaleRegisterRow>,
        val purchases: List<PurchaseRegisterRow>
    ) {
        val salesTotal: Double get() = sales.sumOf { it.amount }
        /** Only lines that had a rate; the rest are counted separately below. */
        val purchaseTotal: Double get() = purchases.sumOf { it.lineAmount ?: 0.0 }
        val purchasesMissingRate: Int get() = purchases.count { it.rate == null }
        val isEmpty: Boolean get() = sales.isEmpty() && purchases.isEmpty()
    }

    suspend fun build(context: Context, dao: KhataDao, from: Long, to: Long): File? {
        return try {
            render(context, gather(context, dao, from, to))
        } catch (e: Exception) {
            null
        }
    }

    /** One read of the book, shared by the screen and the document. */
    suspend fun gather(context: Context, dao: KhataDao, from: Long, to: Long): ReportData {
        return ReportData(
            businessName = BusinessProfile.businessName(context),
            businessAddress = BusinessProfile.businessAddress(context),
            strn = BusinessProfile.strn(context),
            from = from,
            to = to,
            sales = dao.salesRegister(from, to),
            purchases = dao.purchaseRegister(from, to)
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
        val tableHeaderFg = Paint().apply { color = Color.WHITE; textSize = 9f; isFakeBoldText = true; isAntiAlias = true }
        val navyFill = Paint().apply { color = NAVY }
        val warnFill = Paint().apply { color = WARN_BG }
        val tableHeaderFill = Paint().apply { color = GREY }
        val rule = Paint().apply { color = 0xFFDDDDDD.toInt(); strokeWidth = 0.6f }
        val zebra = Paint().apply { color = 0x08000000 }

        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        var canvas: Canvas = page.canvas
        var y: Float
        var pageNo = 1

        val brandLogo = PdfBranding.logo(context)
        val businessName = d.businessName ?: "Roshan Khata"
        val period = "${dayFmt.format(Date(d.from))}  \u2014  ${dayFmt.format(Date(d.to))}"

        fun header(): Float {
            PdfBranding.drawWatermark(context, canvas, PAGE_W, PAGE_H, NAVY)
            canvas.drawRect(0f, 0f, PAGE_W.toFloat(), 88f, navyFill)
            canvas.drawText(businessName, MARGIN, 26f, title)
            canvas.drawText("Sales & Purchase Register", MARGIN, 44f, tagline)
            val identity = buildString {
                d.businessAddress?.let { append(it) }
                d.strn?.let {
                    if (isNotEmpty()) append(" \u00B7 ")
                    append("NTN/STRN: $it")
                }
            }
            if (identity.isNotEmpty()) canvas.drawText(identity, MARGIN, 60f, tagline)
            canvas.drawText("Period: $period", MARGIN, 78f, tagline)
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

        fun clip(text: String, paint: Paint, maxW: Float): String {
            if (paint.measureText(text) <= maxW) return text
            var s = text
            while (s.isNotEmpty() && paint.measureText("$s\u2026") > maxW) s = s.dropLast(1)
            return "$s\u2026"
        }

        // ---- What this document is, and is not ----
        canvas.drawRect(MARGIN, y, PAGE_W - MARGIN, y + 46f, warnFill)
        canvas.drawText(
            "This is the shop's own record of what it was told, printed for the period above.",
            MARGIN + 8f, y + 17f, warnText
        )
        canvas.drawText(
            "A sale is a credit/goods entry that carried goods \u2014 cash-only and qarz-e-hasna are not counted.",
            MARGIN + 8f, y + 31f, warnText
        )
        y += 62f

        // ---- Summary ----
        canvas.drawText("Summary", MARGIN, y, section)
        y += 20f
        fun line(label: String, value: String, paint: Paint = body) {
            canvas.drawText(label, MARGIN, y, body)
            val w = paint.measureText(value)
            canvas.drawText(value, PAGE_W - MARGIN - w, y, paint)
            y += 16f
        }
        line("Sales lines in period", d.sales.size.toString())
        line("Total sales", Format.money(d.salesTotal), bodyBold)
        line("Purchase lines in period", d.purchases.size.toString())
        line("Total purchases (priced lines)", Format.money(d.purchaseTotal), bodyBold)
        if (d.purchasesMissingRate > 0) {
            line("Purchase lines with no rate recorded", d.purchasesMissingRate.toString(), mutedBig)
        }

        y += 10f
        canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, rule)
        y += 20f

        // ---- 1. Sales register ----
        canvas.drawText("1. Sales register", MARGIN, y, section)
        y += 18f

        if (d.sales.isEmpty()) {
            canvas.drawText("No goods sold in this period.", MARGIN, y, mutedBig)
            y += 18f
        } else {
            val xDate = MARGIN + 4f
            val xName = MARGIN + 66f
            val xAmtRight = PAGE_W - MARGIN - 4f
            val nameMaxW = xAmtRight - 130f - xName

            fun salesHeader() {
                canvas.drawRect(MARGIN, y, PAGE_W - MARGIN, y + 18f, tableHeaderFill)
                canvas.drawText("DATE", xDate, y + 12.5f, tableHeaderFg)
                canvas.drawText("CUSTOMER / PRODUCT / BATCH", xName, y + 12.5f, tableHeaderFg)
                canvas.drawText("AMOUNT", xAmtRight - tableHeaderFg.measureText("AMOUNT"), y + 12.5f, tableHeaderFg)
                y += 18f
            }
            salesHeader()

            d.sales.forEachIndexed { index, s ->
                if (y + 30f > PAGE_H - 60f) {
                    newPage()
                    canvas.drawText("1. Sales register (continued)", MARGIN, y, section)
                    y += 18f
                    salesHeader()
                }
                if (index % 2 == 1) canvas.drawRect(MARGIN, y, PAGE_W - MARGIN, y + 30f, zebra)

                val baseline = y + 13f
                canvas.drawText(rowDayFmt.format(Date(s.date)), xDate, baseline, muted)
                canvas.drawText(clip(s.partyName, body, nameMaxW), xName, baseline, body)

                val amt = Format.money(s.amount)
                canvas.drawText(amt, xAmtRight - body.measureText(amt), baseline, body)

                val sub = buildList {
                    s.itemName?.takeIf { it.isNotBlank() }?.let { add(it) }
                    s.quantity?.let { add(Format.qty(it, s.unit)) }
                    s.batchNumber?.takeIf { it.isNotBlank() }?.let { add("Batch $it") }
                    s.company?.takeIf { it.isNotBlank() }?.let { add(it) }
                    s.refNumber?.takeIf { it.isNotBlank() }?.let { add(it) }
                }.joinToString(" \u00B7 ").ifEmpty { "\u2014" }
                canvas.drawText(clip(sub, muted, xAmtRight - xName), xName, baseline + 12f, muted)

                y += 30f
            }

            // Total row.
            if (y + 20f > PAGE_H - 60f) newPage()
            canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, rule)
            y += 16f
            canvas.drawText("Total sales", MARGIN + 4f, y, bodyBold)
            val st = Format.money(d.salesTotal)
            canvas.drawText(st, PAGE_W - MARGIN - 4f - bodyBold.measureText(st), y, bodyBold)
            y += 8f
        }

        // ---- 2. Purchase register ----
        if (y > PAGE_H - 160f) newPage() else { y += 14f; canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, rule); y += 20f }

        canvas.drawText("2. Purchase register", MARGIN, y, section)
        y += 18f

        if (d.purchases.isEmpty()) {
            canvas.drawText("No purchases recorded in this period.", MARGIN, y, mutedBig)
            y += 18f
        } else {
            val xDate = MARGIN + 4f
            val xName = MARGIN + 66f
            val xAmtRight = PAGE_W - MARGIN - 4f
            val nameMaxW = xAmtRight - 130f - xName

            fun purchaseHeader() {
                canvas.drawRect(MARGIN, y, PAGE_W - MARGIN, y + 18f, tableHeaderFill)
                canvas.drawText("DATE", xDate, y + 12.5f, tableHeaderFg)
                canvas.drawText("SUPPLIER / PRODUCT / BATCH", xName, y + 12.5f, tableHeaderFg)
                canvas.drawText("AMOUNT", xAmtRight - tableHeaderFg.measureText("AMOUNT"), y + 12.5f, tableHeaderFg)
                y += 18f
            }
            purchaseHeader()

            d.purchases.forEachIndexed { index, s ->
                if (y + 30f > PAGE_H - 60f) {
                    newPage()
                    canvas.drawText("2. Purchase register (continued)", MARGIN, y, section)
                    y += 18f
                    purchaseHeader()
                }
                if (index % 2 == 1) canvas.drawRect(MARGIN, y, PAGE_W - MARGIN, y + 30f, zebra)

                val baseline = y + 13f
                canvas.drawText(rowDayFmt.format(Date(s.date)), xDate, baseline, muted)
                canvas.drawText(clip(s.supplierName, body, nameMaxW), xName, baseline, body)

                val amount = s.lineAmount
                if (amount != null) {
                    val a = Format.money(amount)
                    canvas.drawText(a, xAmtRight - body.measureText(a), baseline, body)
                } else {
                    canvas.drawText("\u2014", xAmtRight - mutedBig.measureText("\u2014"), baseline, mutedBig)
                }

                val sub = buildList {
                    add(s.itemName)
                    add(Format.qty(s.quantity, s.unit))
                    s.rate?.let { add("@ ${Format.money(it)}") }
                    s.batchNumber?.takeIf { it.isNotBlank() }?.let { add("Batch $it") }
                    s.expiryDate?.let { add("exp ${rowDayFmt.format(Date(it))}") }
                    s.billNumber?.takeIf { it.isNotBlank() }?.let { add("Bill $it") }
                }.joinToString(" \u00B7 ")
                canvas.drawText(clip(sub, muted, xAmtRight - xName), xName, baseline + 12f, muted)

                y += 30f
            }

            if (y + 20f > PAGE_H - 60f) newPage()
            canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, rule)
            y += 16f
            canvas.drawText("Total purchases (priced lines)", MARGIN + 4f, y, bodyBold)
            val pt = Format.money(d.purchaseTotal)
            canvas.drawText(pt, PAGE_W - MARGIN - 4f - bodyBold.measureText(pt), y, bodyBold)
            y += 8f
            if (d.purchasesMissingRate > 0) {
                y += 12f
                canvas.drawText(
                    "${d.purchasesMissingRate} purchase line(s) had no rate recorded and are not in the total above.",
                    MARGIN + 4f, y, muted
                )
                y += 6f
            }
        }

        // ---- Signature strip ----
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
        canvas.drawText("Roshan Khata \u00B7 Generated ${dateFmt.format(Date())} \u00B7 Page $pageNo", footerX, y, muted)

        doc.finishPage(page)

        val dir = File(context.cacheDir, "statements").apply { mkdirs() }
        val file = File(dir, "RoshanKhata_Register_${fileFmt.format(Date())}.pdf")

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
