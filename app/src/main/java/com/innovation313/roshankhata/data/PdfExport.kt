package com.innovation313.roshankhata.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
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
        val doc = PdfDocument()

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

        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
        var c = page.canvas
        var y: Float

        val brandLogo = PdfBranding.logo(context)

        fun drawHeader(): Float {
            c.drawRect(0f, 0f, PAGE_W.toFloat(), 78f, navyFill)
            c.drawText(businessName?.takeIf { it.isNotBlank() } ?: "Roshan Khata", MARGIN, 32f, title)
            c.drawText("Account Statement — $partyName", MARGIN, 52f, subtitle)
            partyPhone?.takeIf { it.isNotBlank() }?.let {
                c.drawText(it, MARGIN, 66f, subtitle)
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

            // The logo, top-right of the band. The statement leaves the shop and
            // becomes the app's best advertisement; the mark should ride with it.
            PdfBranding.drawInHeader(c, brandLogo, PAGE_W, MARGIN, 78f)

            var yy = 100f
            c.drawText("Date", xDate, yy, header)
            c.drawText("You Gave", xGave, yy, header)
            c.drawText("You Got", xGot, yy, header)
            c.drawText("Balance", xBal, yy, header)
            yy += 6f
            c.drawLine(MARGIN, yy, PAGE_W - MARGIN, yy, lineFill)
            return yy + 16f
        }

        y = drawHeader()

        for (row in rows) {
            // Two lines per entry (details + reference), so break before we run off.
            if (y > PAGE_H - 110f) {
                doc.finishPage(page)
                pageNo++
                page = doc.startPage(
                    PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create()
                )
                c = page.canvas
                y = drawHeader()
            }

            val e = row.entry

            c.drawText(Format.dateTime(e.timestamp), xDate, y, body)

            if (e.isGiven) {
                body.color = RED
                c.drawText(Format.money(e.amount), xGave, y, body)
            } else {
                body.color = GREEN
                c.drawText(Format.money(e.amount), xGot, y, body)
            }
            body.color = Color.BLACK

            c.drawText(Format.money(row.runningBalance), xBal, y, body)

            // Second line: reference number, note, and any goods that moved.
            y += 12f
            val detail = buildList {
                add(e.entryNumber)
                Format.goods(e.itemName, e.quantity, e.unit)?.let { add(it) }
                e.note?.takeIf { it.isNotBlank() }?.let { add(it) }
                if (e.isQarzeHasna) add("Qarz-e-Hasna")
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
            y = drawHeader()
        }

        y += 8f
        c.drawRect(MARGIN, y, PAGE_W - MARGIN, y + 52f, navyFill)
        c.drawRect(MARGIN, y, MARGIN + 4f, y + 52f, goldFill)

        val closingLabel = when {
            closingBalance > 0 -> "You will pay"
            closingBalance < 0 -> "You will receive"
            else -> "Settled — nothing outstanding"
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
                y = drawHeader()
            }

            header.textSize = 11f
            c.drawText("Scan to pay", MARGIN, y, header)
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
            c.drawText("Amount to pay:", textX, y + 30f, body)

            title.textSize = 16f
            title.color = NAVY
            c.drawText(Format.money(closingBalance), textX, y + 52f, title)

            muted.textSize = 8f
            c.drawText(
                "This code does not carry the amount —",
                textX,
                y + 72f,
                muted
            )
            c.drawText(
                "please enter it yourself when paying.",
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
                y = drawHeader()
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
                BusinessProfile.businessName(context) ?: "Signature",
                sigLeft, y, muted
            )
            y += 22f
        }

        c.drawText(
            // The app's name only. A statement goes to a customer, and a
            // second brand beside it reads as a second party to the
            // transaction — the customer starts wondering whose bill this is.
            "Generated by Roshan Khata",
            MARGIN,
            y,
            muted
        )

        doc.finishPage(page)

        // Written to cache: a statement is a throwaway artefact, not something
        // to quietly accumulate in the user's storage.
        val dir = File(context.cacheDir, "statements").apply { mkdirs() }
        val safeName = partyName.replace(Regex("[^A-Za-z0-9 ]"), "").trim().replace(" ", "_")
        val file = File(dir, "Statement_${safeName.ifEmpty { "Party" }}.pdf")

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
