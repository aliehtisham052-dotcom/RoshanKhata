package com.innovation313.roshankhata.data

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.ui.Format
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A printable report of the whole business.
 *
 * This is NOT a backup, and the document says so on its own first page in
 * plain words. A PDF cannot be read back into the app — restoring from one
 * would mean retyping every entry by hand. If an owner mistook this for their
 * backup, deleted the real one, and then lost their phone, this file would be
 * of no use to them whatsoever. It would only prove exactly what they had lost.
 *
 * What it IS for: showing the books to someone. A partner, an accountant, a
 * lender, a family member. Printing them, filing them, keeping a paper copy in
 * a drawer the way a bahi-khata always was.
 */
object BusinessReport {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f

    private const val NAVY = 0xFF094C2E.toInt()
    private const val GOLD = 0xFFE1AF3F.toInt()
    private const val RED = 0xFFC0392B.toInt()
    private const val GREEN = 0xFF1E8449.toInt()
    private const val GREY = 0xFF7A7A7A.toInt()
    private const val WARN_BG = 0xFFFFF6E0.toInt()
    private const val WARN_FG = 0xFF5C4A16.toInt()

    private val dateFmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.ENGLISH)
    private val fileFmt = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.ENGLISH)

    data class ReportData(
        val businessName: String?,
        val parties: List<PartyWithBalance>,
        val cashIn: Double,
        val cashOut: Double,
        val pendingCheques: List<Cheque>,
        val openPlans: List<PlanProgress>,
        val expiringBatches: List<ExpiringBatch>
    )

    suspend fun build(context: Context, dao: KhataDao): File? {
        return try {
            val data = gather(context, dao)
            render(context, data)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun gather(context: Context, dao: KhataDao): ReportData {
        val cutoff = System.currentTimeMillis() + ExpiryWindow.WARN_MS

        return ReportData(
            businessName = BusinessProfile.businessName(context),
            parties = dao.partiesWithBalanceOnce(),
            cashIn = dao.cashIncomeOnce(),
            cashOut = dao.cashExpenseOnce(),
            pendingCheques = dao.pendingChequesOnce(),
            openPlans = dao.openPlansOnce(),
            expiringBatches = dao.expiringBatchesOnce(cutoff)
        )
    }

    private fun render(context: Context, d: ReportData): File? {
        val doc = PdfDocument()

        val title = Paint().apply {
            color = Color.WHITE
            textSize = 22f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val tagline = Paint().apply {
            color = GOLD
            textSize = 11f
            isAntiAlias = true
        }
        val section = Paint().apply {
            color = NAVY
            textSize = 14f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val body = Paint().apply {
            color = Color.BLACK
            textSize = 11f
            isAntiAlias = true
        }
        val bodyBold = Paint().apply {
            color = Color.BLACK
            textSize = 11f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val muted = Paint().apply {
            color = GREY
            textSize = 10f
            isAntiAlias = true
        }
        val warnText = Paint().apply {
            color = WARN_FG
            textSize = 10f
            isAntiAlias = true
        }
        val red = Paint().apply {
            color = RED
            textSize = 11f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val green = Paint().apply {
            color = GREEN
            textSize = 11f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val navyFill = Paint().apply { color = NAVY }
        val warnFill = Paint().apply { color = WARN_BG }
        val rule = Paint().apply {
            color = 0xFFDDDDDD.toInt()
            strokeWidth = 0.6f
        }

        var page = doc.startPage(
            PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create()
        )
        var canvas: Canvas = page.canvas
        var y: Float
        var pageNo = 1

        // The brand name, read once: it appears in the warning and in the
        // footer of every page, and it is itself translated (روشن کھاتہ).
        val appName = context.getString(R.string.app_name)

        val brandLogo = PdfBranding.logo(context)

        fun header(): Float {
            // Background first — every band, rule and figure below lands on
            // top of the watermark, never under it.
            PdfBranding.drawWatermark(context, canvas, PAGE_W, PAGE_H, NAVY)
            canvas.drawRect(0f, 0f, PAGE_W.toFloat(), 74f, navyFill)
            canvas.drawText(d.businessName ?: context.getString(R.string.app_name), MARGIN, 34f, title)
            canvas.drawText(PdfBranding.BRAND_LINE, MARGIN, 52f, tagline)
            canvas.drawText(
                context.getString(R.string.pdf_biz_generated, dateFmt.format(Date())),
                MARGIN,
                66f,
                tagline
            )
            PdfBranding.drawInHeader(canvas, brandLogo, PAGE_W, MARGIN, 74f)
            return 100f
        }

        fun newPage() {
            doc.finishPage(page)
            pageNo++
            page = doc.startPage(
                PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create()
            )
            canvas = page.canvas
            y = header()
        }

        y = header()

        // ---- The warning goes FIRST, before anything else ----
        //
        // Not in a footnote, not at the end. If someone is going to mistake this
        // document for their backup, they will do it in the first ten seconds,
        // and the consequence is that they delete the real one.
        canvas.drawRect(MARGIN, y, PAGE_W - MARGIN, y + 54f, warnFill)
        canvas.drawText(
            context.getString(R.string.pdf_rep_not_backup),
            MARGIN + 10f,
            y + 18f,
            Paint(warnText).apply { isFakeBoldText = true; textSize = 11f }
        )
        canvas.drawText(
            context.getString(R.string.pdf_biz_not_backup_2, appName),
            MARGIN + 10f,
            y + 32f,
            warnText
        )
        canvas.drawText(
            context.getString(R.string.pdf_biz_not_backup_3),
            MARGIN + 10f,
            y + 44f,
            warnText
        )
        y += 66f
        canvas.drawText(context.getString(R.string.pdf_biz_not_backup_4), MARGIN + 10f, y, warnText)
        y += 24f

        // ---- Summary ----
        val owedToMe = d.parties.filter { Money.isPositive(it.balance) }.sumOf { it.balance }
        val owedByMe = d.parties.filter { Money.isNegative(it.balance) }.sumOf { -it.balance }

        canvas.drawText(context.getString(R.string.pdf_rep_summary), MARGIN, y, section)
        y += 20f

        fun line(label: String, value: String, paint: Paint = body) {
            canvas.drawText(label, MARGIN, y, body)
            val w = paint.measureText(value)
            canvas.drawText(value, PAGE_W - MARGIN - w, y, paint)
            y += 16f
        }

        line(context.getString(R.string.pdf_biz_you_will_receive), Format.money(owedToMe), green)
        line(context.getString(R.string.pdf_biz_you_will_pay), Format.money(owedByMe), red)
        line(context.getString(R.string.pdf_biz_net_position), Format.money(owedToMe - owedByMe), bodyBold)
        y += 6f
        line(context.getString(R.string.pdf_biz_cash_in), Format.money(d.cashIn))
        line(context.getString(R.string.pdf_biz_cash_out), Format.money(d.cashOut))
        y += 6f
        line(context.getString(R.string.pdf_biz_people_count), d.parties.size.toString())

        y += 14f
        canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, rule)
        y += 20f

        // ---- Accounts ----
        canvas.drawText(context.getString(R.string.pdf_biz_accounts), MARGIN, y, section)
        y += 20f

        if (d.parties.isEmpty()) {
            canvas.drawText(context.getString(R.string.pdf_biz_no_accounts), MARGIN, y, muted)
            y += 18f
        } else {
            d.parties.sortedByDescending { it.balance }.forEach { p ->
                if (y > PAGE_H - 70f) newPage()

                canvas.drawText(p.name, MARGIN, y, body)

                p.phone?.let {
                    val w = muted.measureText(it)
                    canvas.drawText(it, MARGIN + 200f, y, muted)
                }

                val (text, paint) = when {
                    Money.isPositive(p.balance) -> Format.money(p.balance) to green
                    Money.isNegative(p.balance) -> Format.money(-p.balance) to red
                    else -> context.getString(R.string.pdf_biz_settled) to muted
                }
                val w = paint.measureText(text)
                canvas.drawText(text, PAGE_W - MARGIN - w, y, paint)

                y += 15f
            }
        }

        // ---- Pending cheques ----
        if (d.pendingCheques.isNotEmpty()) {
            if (y > PAGE_H - 120f) newPage()
            y += 14f
            canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, rule)
            y += 20f

            canvas.drawText(context.getString(R.string.pdf_biz_cheques), MARGIN, y, section)
            y += 14f
            canvas.drawText(
                context.getString(R.string.pdf_biz_cheques_note),
                MARGIN,
                y,
                muted
            )
            y += 18f

            d.pendingCheques.forEach { c ->
                if (y > PAGE_H - 60f) newPage()
                val label = buildString {
                    append(c.chequeNumber ?: context.getString(R.string.pdf_biz_cheque))
                    c.bankName?.let { append(" \u00B7 $it") }
                    append(context.getString(R.string.pdf_biz_due))
                    append(Format.dateOnly(c.dueDate))
                }
                canvas.drawText(label, MARGIN, y, body)
                val amt = Format.money(c.amount)
                val w = body.measureText(amt)
                canvas.drawText(amt, PAGE_W - MARGIN - w, y, body)
                y += 15f
            }
        }

        // ---- Open payment plans ----
        if (d.openPlans.isNotEmpty()) {
            if (y > PAGE_H - 120f) newPage()
            y += 14f
            canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, rule)
            y += 20f

            canvas.drawText(context.getString(R.string.pdf_biz_plans), MARGIN, y, section)
            y += 18f

            d.openPlans.forEach { p ->
                if (y > PAGE_H - 60f) newPage()
                canvas.drawText(p.partyName, MARGIN, y, body)
                val txt = context.getString(
                    R.string.pdf_biz_paid_of,
                    Format.money(p.paidSoFar),
                    Format.money(p.totalAmount)
                )
                val w = body.measureText(txt)
                canvas.drawText(txt, PAGE_W - MARGIN - w, y, body)
                y += 15f
            }
        }

        // ---- Expiring stock ----
        //
        // On the report because it is money the owner is about to lose, and a
        // paper copy in the drawer is one more chance for someone to notice.
        if (d.expiringBatches.isNotEmpty()) {
            if (y > PAGE_H - 120f) newPage()
            y += 14f
            canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, rule)
            y += 20f

            canvas.drawText(context.getString(R.string.pdf_biz_expiring), MARGIN, y, section)
            y += 18f

            d.expiringBatches.forEach { e ->
                if (y > PAGE_H - 60f) newPage()

                canvas.drawText(e.productName, MARGIN, y, body)

                val days = e.daysLeft
                val txt = if (e.hasExpired) context.getString(R.string.pdf_biz_expired)
                    else context.getString(R.string.pdf_biz_days_left, days)
                val paint = if (e.hasExpired || days <= 14) red else body
                val w = paint.measureText(txt)
                canvas.drawText(txt, PAGE_W - MARGIN - w, y, paint)
                y += 13f

                val sub = buildString {
                    e.batchNumber?.let { append(context.getString(R.string.pdf_biz_batch, it)) }
                    append(e.partyName)
                }
                canvas.drawText(sub, MARGIN + 10f, y, muted)
                y += 16f
            }
        }

        // ---- Footer ----
        if (y > PAGE_H - 104f) newPage()
        PdfBranding.drawDownloadBanner(context, doc, canvas, MARGIN, PAGE_H - 96f, PAGE_W - 2 * MARGIN)
        y = PAGE_H - 34f
        canvas.drawText(
            context.getString(R.string.pdf_rep_footer_page, appName, pageNo),
            MARGIN,
            y,
            muted
        )

        doc.finishPage(page)

        val dir = File(context.cacheDir, "statements").apply { mkdirs() }
        val file = File(dir, "RoshanKhata_Report_${fileFmt.format(Date())}.pdf")

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
}
