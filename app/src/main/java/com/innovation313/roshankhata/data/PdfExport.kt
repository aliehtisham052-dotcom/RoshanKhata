package com.innovation313.roshankhata.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.ui.Format
import java.io.File
import java.io.FileOutputStream

/**
 * Renders a party's ledger as a PDF statement.
 *
 * This document goes to a real customer, so it is built to be read and
 * trusted: every entry carries its reference number and running balance, and
 * the closing balance is stated plainly in words the customer will understand
 * ("You will pay" / "You will receive") rather than an accounting sign that
 * could be read the wrong way round.
 */
object PdfExport {

    // A4 at 72dpi
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f

    private const val NAVY = 0xFF094C2E.toInt()
    private const val GOLD = 0xFFE1AF3F.toInt()
    private const val RED = 0xFFC0392B.toInt()
    private const val GREEN = 0xFF1E8449.toInt()
    private const val GREY = 0xFF7A7A7A.toInt()

    data class StatementRow(
        val entry: LedgerEntry,
        val runningBalance: Double
    )

    /**
     * @return the written file, or null if nothing could be written.
     */
    fun buildStatement(
        context: Context,
        partyName: String,
        partyPhone: String?,
        rows: List<StatementRow>,
        closingBalance: Double,
        /**
         * What the account stood at BEFORE the first row printed here.
         *
         * A statement for a date range prints only that window's entries, but
         * the closing figure is the whole account's — so without this line a
         * customer reads "you owe 8,000" under entries that add up to 500 and
         * has no way to see where the rest came from. Zero on a full-ledger
         * statement, where the account genuinely starts at nothing.
         */
        openingBalance: Double = 0.0,
        businessName: String?,
        paymentQr: Bitmap? = null,
        /**
         * The customer's own photo, when the owner has chosen to include it.
         *
         * Null unless that setting is on, and it is off by default: a
         * statement gets forwarded, and the customer agreed to the shopkeeper
         * holding their photo — not to it travelling on to whoever the
         * statement is passed to next.
         */
        partyPhoto: Bitmap? = null
    ): File? {

        // "You gave" / "You got" is the SHOPKEEPER's phrasing, and this sheet
        // is read by the CUSTOMER — who would take "You gave Rs 8,000" to mean
        // they had paid it. Same figures, told from the side of the person
        // holding the page. Read once here because the summary box and the
        // table header must say the same words, on every page.
        val owedLabel = context.getString(R.string.pdf_stmt_you_owe)
        val paidLabel = context.getString(R.string.pdf_stmt_you_paid)

        val title = Paint().apply {
            color = Color.WHITE
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val subtitle = Paint().apply {
            color = GOLD
            textSize = 10f
            isAntiAlias = true
        }
        val header = Paint().apply {
            color = NAVY
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val body = Paint().apply {
            color = Color.BLACK
            textSize = 10f
            isAntiAlias = true
        }
        val muted = Paint().apply {
            color = GREY
            textSize = 8f
            isAntiAlias = true
        }
        val navyFill = Paint().apply { color = NAVY }
        val goldFill = Paint().apply { color = GOLD }
        val lineFill = Paint().apply {
            color = 0xFFDDDDDD.toInt()
            strokeWidth = 0.5f
        }

        // Column positions
        val xDate = MARGIN
        val xGave = 300f
        val xGot = 390f
        val xBal = 480f

        /**
         * Draws the whole statement once.
         *
         * [totalPages] is null on the first, throwaway pass — it exists only
         * to count the pages, which cannot be known until the rows have been
         * laid out. The second pass draws the same thing again with the count
         * in hand, so page one can say "Page 1 of 3". Same device as
         * InspectorReport, for the same reason.
         */
        fun render(totalPages: Int?): Pair<PdfDocument, Int> {
        val doc = PdfDocument()
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
        var c = page.canvas
        var y: Float

        val brandLogo = PdfBranding.logo(context)

        // The three figures a customer looks for first, computed once: what
        // was given in all, what came back in all, and where that leaves
        // things. The competitor statements this app is measured against put
        // these at the top, and they are right to — a statement that opens
        // with its own conclusion is easier to trust than one that makes the
        // reader add it up.
        val totalGave = rows.filter { it.entry.isGiven }.sumOf { it.entry.amount }
        val totalGot = rows.filter { !it.entry.isGiven }.sumOf { it.entry.amount }

        val periodPaint = Paint().apply {
            color = GREY
            textSize = 9f
            isAntiAlias = true
        }
        val boxLabel = Paint().apply {
            color = GREY
            textSize = 8f
            isAntiAlias = true
        }
        val boxValue = Paint().apply {
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val boxStroke = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
            isAntiAlias = true
        }

        /**
         * @param first On the first page the header carries the period line
         * and the three summary boxes; later pages repeat only the band and
         * the column headings, so the table continues without ceremony.
         */
        fun drawHeader(first: Boolean): Float {
            // Later sections (closing box, QR, signature) retune these shared
            // paints for their own text. A page break after that point comes
            // back through here — so the header resets what it depends on,
            // rather than trusting whoever drew last. Without this, a
            // continuation page after the QR section drew the business name
            // navy-on-navy: an invisible title.
            title.textSize = 20f
            title.color = Color.WHITE
            subtitle.color = GOLD
            header.textSize = 10f
            body.textSize = 10f
            muted.textSize = 8f

            // No watermark on a customer statement. The owner is sending a
            // bill to their own customer, not advertising this app for us; a
            // mark across the middle of the page made their document look
            // like our document. The other five reports keep theirs — those
            // stay inside the shop. The hint lives in the footer now.

            c.drawRect(0f, 0f, PAGE_W.toFloat(), 78f, navyFill)
            c.drawText(businessName?.takeIf { it.isNotBlank() } ?: context.getString(R.string.app_name), MARGIN, 32f, title)
            // Name and number on one line. They used to be two, which on a
            // customer saved under their phone number printed the same digits
            // twice.
            val phone = partyPhone?.takeIf { it.isNotBlank() && it != partyName }
            c.drawText(
                context.getString(R.string.pdf_stmt_title, partyName) +
                    (phone?.let { "  ·  $it" } ?: ""),
                MARGIN, 52f, subtitle
            )

            // The stretch of days this statement covers, read off the rows
            // themselves — first entry to last. Seen working in a
            // competitor's statement and adopted for the reason it works
            // there: a customer holding the sheet should not have to scan
            // the date column to learn what period they are looking at.
            // Right-aligned in the band, stopping short of the corner logo.
            if (rows.isNotEmpty()) {
                val fmt = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.ENGLISH)
                val from = fmt.format(java.util.Date(rows.minOf { it.entry.timestamp }))
                val till = fmt.format(java.util.Date(rows.maxOf { it.entry.timestamp }))
                val period = if (from == till) from else "$from \u2013 $till"
                val periodPaint = Paint(subtitle).apply { textAlign = Paint.Align.RIGHT }
                c.drawText(period, PAGE_W - MARGIN - 50f, 66f, periodPaint)
            }

            // The customer's photo, beside their name — the same face the
            // owner sees in the ledger, so the statement is plainly about this
            // person and not another of the same name.
            partyPhoto?.let { photo ->
                val size = 54
                val left = PAGE_W - MARGIN - 90 - size
                c.drawBitmap(
                    photo,
                    null,
                    Rect(left.toInt(), 12, left.toInt() + size, 12 + size),
                    null
                )
            }

            // No app logo in this band, deliberately: this sheet is the
            // SHOP's claim about money, and a customer receiving it should see
            // the shop's identity, not the app's. The app's mark is one quiet
            // line in the page footer instead.

            var yy = 94f

            if (first) {
                // Which stretch of the ledger this covers, and how many
                // entries — so a page that gets forwarded still says what it
                // is a statement OF.
                val period = if (rows.isNotEmpty()) {
                    context.getString(
                        R.string.pdf_stmt_period,
                        Format.dateOnly(rows.first().entry.timestamp),
                        Format.dateOnly(rows.last().entry.timestamp)
                    ) + "  ·  " + context.resources.getQuantityString(
                        R.plurals.pdf_stmt_entries, rows.size, rows.size
                    )
                } else {
                    context.getString(R.string.pdf_stmt_no_entries)
                }
                c.drawText(period, MARGIN, yy, periodPaint)
                if (totalPages != null && totalPages > 1) {
                    val pageLabel = Paint(periodPaint).apply {
                        textSize = 8f
                        textAlign = Paint.Align.RIGHT
                    }
                    c.drawText(
                        context.getString(R.string.pdf_stmt_page, pageNo, totalPages),
                        PAGE_W - MARGIN, yy, pageLabel
                    )
                }
                yy += 10f

                // Two boxes, not three: the third repeated the closing bar a
                // few centimetres below it. Tinted to match the table's own
                // colours so red means the same thing everywhere.
                val gap = 10f
                val boxW = (PAGE_W - 2 * MARGIN - gap) / 2f
                val boxH = 42f

                fun summaryBox(index: Int, label: String, value: String, tint: Int, valueColor: Int) {
                    val left = MARGIN + index * (boxW + gap)
                    val r = RectF(left, yy, left + boxW, yy + boxH)
                    val fill = Paint().apply { color = tint }
                    c.drawRoundRect(r, 5f, 5f, fill)
                    boxStroke.color = valueColor
                    c.drawRoundRect(r, 5f, 5f, boxStroke)
                    c.drawText(label, left + 10f, yy + 15f, boxLabel)
                    boxValue.color = valueColor
                    c.drawText(value, left + 10f, yy + 33f, boxValue)
                }

                summaryBox(0, owedLabel, Format.money(totalGave), 0xFFFDECEA.toInt(), RED)
                summaryBox(1, paidLabel, Format.money(totalGot), 0xFFEAF7EF.toInt(), GREEN)

                yy += boxH + 18f
            }

            c.drawText(context.getString(R.string.pdf_label_date), xDate, yy, header)
            c.drawText(owedLabel, xGave, yy, header)
            c.drawText(paidLabel, xGot, yy, header)
            c.drawText(context.getString(R.string.pdf_stmt_balance), xBal, yy, header)
            yy += 6f
            c.drawLine(MARGIN, yy, PAGE_W - MARGIN, yy, lineFill)
            return yy + 16f
        }

        y = drawHeader(first = true)

        // Where the account stood before the first row above — printed only
        // when there is something to carry in, since a brand-new customer
        // opening at zero is told nothing by a "Rs 0" line.
        if (Money.isNotZero(openingBalance)) {
            val opened = rows.firstOrNull()?.entry?.timestamp
            val label = if (opened != null) {
                context.getString(R.string.pdf_stmt_opening_before, Format.dateOnly(opened))
            } else {
                context.getString(R.string.pdf_stmt_opening)
            }
            val italic = Paint(muted).apply {
                textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            }
            c.drawText(label, xDate, y, italic)
            c.drawText(Format.money(openingBalance), xBal, y, italic)
            y += 8f
            c.drawLine(MARGIN, y, PAGE_W - MARGIN, y, lineFill)
            y += 16f
        }

        for (row in rows) {
            // Two lines per entry (details + reference), so break before we run off.
            if (y > PAGE_H - 110f) {
                doc.finishPage(page)
                pageNo++
                page = doc.startPage(
                    PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create()
                )
                c = page.canvas
                y = drawHeader(first = false)
            }

            val e = row.entry

            // A "12:00 AM" on every row is not a time anyone recorded — it is
            // what a date with no time of day comes out as.
            c.drawText(Format.statementStamp(e.timestamp), xDate, y, body)

            if (e.isGiven) {
                body.color = RED
                c.drawText(Format.money(e.amount), xGave, y, body)
            } else {
                body.color = GREEN
                c.drawText(Format.money(e.amount), xGot, y, body)
            }
            body.color = Color.BLACK

            // The running balance is the column a reader actually follows
            // down the page, so it gets the weight — bold, like the summary.
            c.drawText(Format.money(row.runningBalance), xBal, y, header)

            // Second line: reference number, note, and any goods that moved.
            y += 12f
            val detail = buildList {
                add(e.entryNumber)
                Format.goods(e.itemName, e.quantity, e.unit)?.let { add(it) }
                e.note?.takeIf { it.isNotBlank() }?.let { add(it) }
                if (e.isQarzeHasna) add(context.getString(R.string.pdf_stmt_qarze_hasna))
            }.joinToString("  ·  ")

            c.drawText(detail, xDate, y, muted)

            y += 8f
            c.drawLine(MARGIN, y, PAGE_W - MARGIN, y, lineFill)
            y += 16f
        }

        // Closing balance — stated in the customer's terms, not accounting signs.
        if (y > PAGE_H - 100f) {
            doc.finishPage(page)
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            c = page.canvas
            y = drawHeader(first = false)
        }

        y += 8f
        c.drawRect(MARGIN, y, PAGE_W - MARGIN, y + 52f, navyFill)
        c.drawRect(MARGIN, y, MARGIN + 4f, y + 52f, goldFill)

        val closingLabel = when {
            closingBalance > 0 -> context.getString(R.string.pdf_stmt_you_will_pay)
            closingBalance < 0 -> context.getString(R.string.pdf_stmt_you_will_receive)
            else -> context.getString(R.string.pdf_stmt_settled)
        }

        subtitle.color = GOLD
        c.drawText(closingLabel, MARGIN + 16f, y + 20f, subtitle)

        title.textSize = 18f
        c.drawText(Format.money(closingBalance), MARGIN + 16f, y + 42f, title)

        y += 70f

        // The payment code goes in only when there is actually something for
        // the customer to pay. Printing "scan to pay me" on a statement where
        // we owe them would be tone-deaf.
        if (paymentQr != null && closingBalance > 0) {
            if (y > PAGE_H - 190f) {
                doc.finishPage(page)
                pageNo++
                page = doc.startPage(
                    PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create()
                )
                c = page.canvas
                y = drawHeader(first = false)
            }

            header.textSize = 11f
            c.drawText(context.getString(R.string.pdf_label_scan_to_pay), MARGIN, y, header)
            y += 8f

            val qrSize = 120
            val dst = Rect(
                MARGIN.toInt(),
                y.toInt(),
                MARGIN.toInt() + qrSize,
                y.toInt() + qrSize
            )
            c.drawBitmap(paymentQr, null, dst, null)

            // Say the amount in words next to the code: a personal
            // EasyPaisa/JazzCash QR carries no amount, so the customer types
            // it in themselves and needs to see it plainly.
            val textX = MARGIN + qrSize + 16f
            body.textSize = 10f
            c.drawText(context.getString(R.string.pdf_stmt_amount_to_pay), textX, y + 30f, body)

            title.textSize = 16f
            title.color = NAVY
            c.drawText(Format.money(closingBalance), textX, y + 52f, title)

            muted.textSize = 8f
            c.drawText(
                context.getString(R.string.pdf_stmt_qr_hint_1),
                textX,
                y + 72f,
                muted
            )
            c.drawText(
                context.getString(R.string.pdf_stmt_qr_hint_2),
                textX,
                y + 82f,
                muted
            )

            y += qrSize + 24f
        }

        // The owner's signature, if they have set one.
        //
        // A statement is a claim about money, and a shopkeeper has always put
        // their name at the bottom of one. Drawn on the right, above a ruled
        // line, where a signature goes on paper.
        val signature = BusinessProfile.loadSignature(context)
        if (signature != null) {
            if (y > PAGE_H - 150f) {
                doc.finishPage(page)
                pageNo++
                page = doc.startPage(
                    PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create()
                )
                c = page.canvas
                y = drawHeader(first = false)
            }

            y += 20f
            val sigW = 150
            val sigH = 60
            val sigLeft = PAGE_W - MARGIN - sigW

            val ratio = signature.height.toFloat() / signature.width.toFloat()
            val drawH = (sigW * ratio).toInt().coerceAtMost(sigH)
            c.drawBitmap(
                signature,
                null,
                Rect(sigLeft.toInt(), y.toInt(), (sigLeft + sigW).toInt(), y.toInt() + drawH),
                null
            )
            y += drawH + 6f

            c.drawLine(sigLeft, y, PAGE_W - MARGIN, y, muted)
            y += 14f
            muted.textSize = 9f
            c.drawText(
                BusinessProfile.businessName(context) ?: context.getString(R.string.signature),
                sigLeft, y, muted
            )
            y += 22f
        }

        // One quiet line at the very FOOT of the last page — the app's mark,
        // its tagline, and a DOWNLOAD link — rather than a bordered advert
        // sitting directly under the customer's last entry. The address
        // becomes a real link once the file is written (see applyLinks).
        PdfBranding.drawFootLine(
            context, doc, c, MARGIN, PAGE_H - MARGIN, PAGE_W - MARGIN, brandLogo
        )

        doc.finishPage(page)
        return doc to pageNo
        }

        val (counting, pageCount) = render(null)
        counting.close()
        val (doc, _) = render(pageCount)

        // Written to cache: a statement is a throwaway artefact, not something
        // to quietly accumulate in the user's storage.
        val dir = File(context.cacheDir, "statements").apply { mkdirs() }
        val safeName = partyName.replace(Regex("[^A-Za-z0-9 ]"), "").trim().replace(" ", "_")
        val file = File(dir, "Statement_${safeName.ifEmpty { "Party" }}.pdf")

        return try {
            FileOutputStream(file).use { doc.writeTo(it) }
            PdfBranding.applyLinks(doc, file)
            file
        } catch (e: Exception) {
            null
        } finally {
            doc.close()
        }
    }
}
