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

    private const val NAVY = 0xFF152238.toInt()
    private const val GOLD = 0xFFD4A438.toInt()
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

        fun header(): Float {
            canvas.drawRect(0f, 0f, PAGE_W.toFloat(), 74f, navyFill)
            canvas.drawText(d.businessName ?: "Roshan Khata", MARGIN, 34f, title)
            canvas.drawText("Innovation \u2014 Waqt Hai Badalne Ka", MARGIN, 52f, tagline)
            canvas.drawText(
                "Report generated ${dateFmt.format(Date())}",
                MARGIN,
                66f,
                tagline
            )
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
            "This is a report to read and print \u2014 it is NOT a backup.",
            MARGIN + 10f,
            y + 18f,
            Paint(warnText).apply { isFakeBoldText = true; textSize = 11f }
        )
        canvas.drawText(
            "Roshan Khata cannot restore your records from a PDF. Keep the backup",
            MARGIN + 10f,
            y + 32f,
            warnText
        )
        canvas.drawText(
            "file (RoshanKhata_Backup...txt) safe \u2014 that is the one that brings",
            MARGIN + 10f,
            y + 44f,
            warnText
        )
        y += 66f
        canvas.drawText("your data back.", MARGIN + 10f, y, warnText)
        y += 24f

        // ---- Summary ----
        val owedToMe = d.parties.filter { it.balance > 0 }.sumOf { it.balance }
        val owedByMe = d.parties.filter { it.balance < 0 }.sumOf { -it.balance }

        canvas.drawText("Summary", MARGIN, y, section)
        y += 20f

        fun line(label: String, value: String, paint: Paint = body) {
            canvas.drawText(label, MARGIN, y, body)
            val w = paint.measureText(value)
            canvas.drawText(value, PAGE_W - MARGIN - w, y, paint)
            y += 16f
        }

        line("You will receive", Format.money(owedToMe), green)
        line("You will pay", Format.money(owedByMe), red)
        line("Net position", Format.money(owedToMe - owedByMe), bodyBold)
        y += 6f
        line("Cash in (cashbook)", Format.money(d.cashIn))
        line("Cash out (cashbook)", Format.money(d.cashOut))
        y += 6f
        line("Customers and suppliers", d.parties.size.toString())

        y += 14f
        canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, rule)
        y += 20f

        // ---- Accounts ----
        canvas.drawText("Accounts", MARGIN, y, section)
        y += 20f

        if (d.parties.isEmpty()) {
            canvas.drawText("No accounts yet.", MARGIN, y, muted)
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
                    p.balance > 0 -> Format.money(p.balance) to green
                    p.balance < 0 -> Format.money(-p.balance) to red
                    else -> "Settled" to muted
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

            canvas.drawText("Cheques not yet cleared", MARGIN, y, section)
            y += 14f
            canvas.drawText(
                "These are not money until they clear. They are not in the balances above.",
                MARGIN,
                y,
                muted
            )
            y += 18f

            d.pendingCheques.forEach { c ->
                if (y > PAGE_H - 60f) newPage()
                val label = buildString {
                    append(c.chequeNumber ?: "Cheque")
                    c.bankName?.let { append(" \u00B7 $it") }
                    append(" \u00B7 due ")
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

            canvas.drawText("Payment plans still running", MARGIN, y, section)
            y += 18f

            d.openPlans.forEach { p ->
                if (y > PAGE_H - 60f) newPage()
                canvas.drawText(p.partyName, MARGIN, y, body)
                val txt = "${Format.money(p.paidSoFar)} of ${Format.money(p.totalAmount)}"
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

            canvas.drawText("Stock expiring soon", MARGIN, y, section)
            y += 18f

            d.expiringBatches.forEach { e ->
                if (y > PAGE_H - 60f) newPage()

                canvas.drawText(e.productName, MARGIN, y, body)

                val days = e.daysLeft
                val txt = if (e.hasExpired) "EXPIRED" else "$days days"
                val paint = if (e.hasExpired || days <= 14) red else body
                val w = paint.measureText(txt)
                canvas.drawText(txt, PAGE_W - MARGIN - w, y, paint)
                y += 13f

                val sub = buildString {
                    e.batchNumber?.let { append("Batch $it \u00B7 ") }
                    append(e.partyName)
                }
                canvas.drawText(sub, MARGIN + 10f, y, muted)
                y += 16f
            }
        }

        // ---- Footer ----
        if (y > PAGE_H - 60f) newPage()
        y = PAGE_H - 34f
        canvas.drawText(
            "Roshan Khata \u00B7 Innovation-313 \u00B7 Page $pageNo",
            MARGIN,
            y,
            muted
        )

        doc.finishPage(page)

        val dir = File(context.cacheDir, "statements").apply { mkdirs() }
        val file = File(dir, "RoshanKhata_Report_${fileFmt.format(Date())}.pdf")

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
