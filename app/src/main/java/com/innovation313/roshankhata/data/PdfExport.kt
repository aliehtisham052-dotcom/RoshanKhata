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
    private const val GREY = 0xFF6F6F6F.toInt()
    /** The band's second lines: a paler gold that still reads on the green. */
    private const val GOLD_SOFT = 0xFFF1DFA8.toInt()
    private const val GREEN_SOFT = 0xFFE7F2EA.toInt()
    private const val BOX_EDGE = 0xFFDAD6CB.toInt()
    private const val ROW_WASH = 0xFFF8F7F2.toInt()

    /** [text] cut with an ellipsis to fit [maxW] at [paint]'s size. */
    private fun fitted(text: String, paint: Paint, maxW: Float): String {
        if (paint.measureText(text) <= maxW) return text
        var end = text.length
        while (end > 1 && paint.measureText(text, 0, end) + paint.measureText("\u2026") > maxW) end--
        return text.substring(0, end).trimEnd() + "\u2026"
    }

    /** A figure is never cut: its size comes down (to [min]) until it fits. */
    private fun shrinkToFit(text: String, paint: Paint, maxW: Float, min: Float) {
        while (paint.textSize > min && paint.measureText(text) > maxW) paint.textSize -= 0.5f
    }

    private fun latinOnly(word: String): Boolean = word.all { it.code < 0x0250 }

    /**
     * "s/o Muhammad · Khichi": the line under the customer's name on his page in the app
     * (PartyDetailActivity.loadParty), whichever parts are known; null when neither is.
     */
    fun identityOf(context: Context, party: Party?): String? = party?.let { p ->
        listOfNotNull(
            p.fatherName?.trim()?.takeIf { it.isNotEmpty() }?.let { context.getString(R.string.son_of_prefix, it) },
            p.village?.trim()?.takeIf { it.isNotEmpty() }
        ).joinToString("  ·  ").ifEmpty { null }
    }

    /**
     * What a statement would print but the Business Profile does not hold yet, said to the owner
     * when the sheet is ready (2 Nov 2026, the owner's review: a statement went out with no way to
     * pay and no shop phone, because the profile was empty and those parts print only when filled).
     * The sheet never prints a placeholder — the customer would read "[YOUR BANK]". The way to pay
     * matters only on a customer's statement that owes, as [buildStatement] prints it. Null when
     * nothing is missing, and on a helper's read-only phone, which cannot change the profile.
     */
    class Gaps(val note: String, val payMissing: Boolean)

    fun gapsOf(context: Context, isCustomer: Boolean, closingBalance: Double): Gaps? {
        if (ViewerMode.isOn(context)) return null
        fun blank(s: String?) = s.isNullOrBlank()
        val missing = buildList {
            if (isCustomer && Money.isPositive(closingBalance) &&
                blank(BusinessProfile.bankName(context)) && blank(BusinessProfile.bankAccountTitle(context)) &&
                blank(BusinessProfile.bankIban(context)) && blank(BusinessProfile.bankJazzCash(context))
            ) add(R.string.pdf_stmt_gap_pay)
            if (blank(BusinessProfile.businessPhone(context))) add(R.string.pdf_stmt_gap_phone)
            if (blank(BusinessProfile.businessAddress(context))) add(R.string.pdf_stmt_gap_address)
        }
        if (missing.isEmpty()) return null
        return Gaps(
            context.getString(R.string.pdf_stmt_gap_title) + "\n" +
                missing.joinToString("\n") { "• " + context.getString(it) },
            payMissing = R.string.pdf_stmt_gap_pay in missing
        )
    }

    data class StatementRow(
        val entry: LedgerEntry,
        val runningBalance: Double,
        /** Goods lines, in order — empty for a money-only entry. */
        val items: List<EntryItem> = emptyList()
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
        partyPhoto: Bitmap? = null,
        /** A supplier's statement is read by the supplier: the columns say it from their side (21 Oct 2026). */
        isCustomer: Boolean = true,
        /** The day the customer promised to pay (Party.harvestPromise); said under what is owed when it is today or later. */
        payBy: Long? = null,
        /** "s/o Muhammad · Khichi" under the customer's name ([identityOf]); null prints nothing. */
        partyIdentity: String? = null,
        /**
         * Told as each page of the statement is really finished — page n of all (3 Nov 2026, the screen's page
         * stack): only on the pass that counts, once the total is known; from the thread that writes.
         */
        onPage: ((done: Int, all: Int) -> Unit)? = null
    ): File? {
        // Oldest first, as every statement reads (21 Oct 2026, the critical review): the screen's
        // newest-first list printed the period backwards and put the opening line above the newest
        // entry. Each row keeps its own running balance, so the order changes nothing else.
        val rows = rows.sortedWith(compareBy<StatementRow> { it.entry.timestamp }.thenBy { it.entry.id })

        // "You gave" / "You got" is the SHOPKEEPER's phrasing, and this sheet
        // is read by the CUSTOMER — who would take "You gave Rs 8,000" to mean
        // they had paid it. Same figures, told from the side of the person
        // holding the page. Read once here because the summary box and the
        // table header must say the same words, on every page.
        // A supplier holding the page: what the shop gave them was paid TO them, and what came
        // from them is what they supplied (the customer's words read backwards on their page).
        // "Taken" / "Paid" (2 Nov 2026, the owner's review): each row is one dealing, not what is
        // owed, and the (-) / (+) marks read against a balance printed without a sign. "Taken"
        // fits goods and cash lent alike; the goods themselves are named on the row's second line.
        val owedLabel = context.getString(if (isCustomer) R.string.pdf_stmt_taken else R.string.pdf_stmt_paid_to_you)
        val paidLabel = context.getString(if (isCustomer) R.string.pdf_stmt_paid else R.string.pdf_stmt_you_supplied)
        val totalOwedLabel = context.getString(if (isCustomer) R.string.pdf_stmt_total_taken else R.string.pdf_stmt_paid_to_you)
        val totalPaidLabel = context.getString(if (isCustomer) R.string.pdf_stmt_total_paid else R.string.pdf_stmt_you_supplied)
        // The shop's own way back: a customer holding the sheet must be able to reach the shop.
        val shopPhone = BusinessProfile.businessPhone(context)?.trim()?.takeIf { it.isNotEmpty() }
        val shopAddress = BusinessProfile.businessAddress(context)?.trim()?.takeIf { it.isNotEmpty() }
        val madeOn = Format.dateOnly(System.currentTimeMillis())
        // The fourth box's name: where the account stands, from the reader's side.
        fun closingWord(): String = when {
            Money.isPositive(closingBalance) -> context.getString(R.string.pdf_stmt_still_to_pay)
            Money.isPositive(-closingBalance) -> context.getString(R.string.pdf_stmt_you_will_receive)
            else -> context.getString(R.string.pdf_stmt_settled_short)
        }

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
        val washFill = Paint().apply { color = ROW_WASH }
        val dash = Paint().apply { color = 0xFFB5B2AA.toInt(); textSize = 10f; isAntiAlias = true }
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
        var page = PdfRtl.startPage(context, doc, PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
        var c = page.canvas
        var y: Float

        val brandLogo = PdfBranding.logo(context)

        // Every page names the shop and says which page it is (2 Nov 2026, the owner's review
        // against the mockup): a page lost from a printed or forwarded statement still says whose
        // it is and where it belongs. It sits just above the foot line, clear of the content, whose
        // page breaks leave room for it. On the counting pass the total is not known yet; the line
        // is the same height either way, so nothing moves.
        fun endPage() {
            val shop = businessName?.takeIf { it.isNotBlank() } ?: context.getString(R.string.app_name)
            val words = shop + "  ·  " +
                Digits.string(context.resources, R.string.pdf_stmt_page, pageNo, totalPages ?: pageNo)
            val p = Paint().apply { color = GREY; textSize = 7f; isAntiAlias = true }
            PdfRtl.drawText(c, fitted(words, p, PAGE_W - 2 * MARGIN), MARGIN, PAGE_H - MARGIN - 26f, p)
            doc.finishPage(page)
            if (totalPages != null) onPage?.invoke(pageNo, totalPages)
        }

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
            // A gold rule under the band, the app's own pairing of colours.
            c.drawRect(0f, 78f, PAGE_W.toFloat(), 80.5f, goldFill)

            // The document's own name and the day it was made, on the band's far side (2 Nov 2026):
            // the corner used to repeat the period printed again below, and nothing said when the
            // sheet was made.
            val docTitle = Paint(subtitle).apply {
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
                letterSpacing = if (latinOnly(context.getString(R.string.pdf_stmt_doc_title))) 0.08f else 0f
            }
            PdfRtl.drawText(c, context.getString(R.string.pdf_stmt_doc_title), PAGE_W - MARGIN, 32f, docTitle)
            val madePaint = Paint(subtitle).apply { textSize = 8f; color = GOLD_SOFT; textAlign = Paint.Align.RIGHT }
            PdfRtl.drawText(c, context.getString(R.string.pdf_stmt_made_on, madeOn), PAGE_W - MARGIN, 46f, madePaint)

            // The shop's name, and under it how to reach the shop: its address and phone.
            val nameRoom = PAGE_W - 2 * MARGIN - 150f
            val shopName = businessName?.takeIf { it.isNotBlank() } ?: ""
            PdfRtl.drawText(c, fitted(shopName, title, nameRoom), MARGIN, 34f, title)
            val reach = listOfNotNull(shopAddress, shopPhone?.let { Format.ltr(it) }).joinToString("  \u00b7  ")
            if (reach.isNotEmpty()) {
                val reachPaint = Paint(subtitle).apply { textSize = 9f; color = GOLD_SOFT }
                PdfRtl.drawText(c, fitted(reach, reachPaint, nameRoom), MARGIN, 52f, reachPaint)
            }

            // No app logo in this band, deliberately: this sheet is the
            // SHOP's claim about money, and a customer receiving it should see
            // the shop's identity, not the app's. The app's mark is one quiet
            // line in the page footer instead.

            var yy = 98f

            if (first) {
                // Who the statement is for, and which stretch of the ledger it covers (2 Nov 2026):
                // the customer's name used to sit in the band as a gold sub-line, the period twice.
                val small = Paint(periodPaint).apply { textSize = 7f; letterSpacing = 0.06f }
                val nameX = partyPhoto?.let { MARGIN + 44f } ?: MARGIN
                partyPhoto?.let { photo ->
                    // The customer's photo, beside their name, when the owner turned it on.
                    PdfRtl.drawBitmap(c, photo, null, Rect(MARGIN.toInt(), (yy - 6).toInt(), MARGIN.toInt() + 36, (yy + 30).toInt()), null)
                }
                PdfRtl.drawText(c, context.getString(R.string.pdf_stmt_for), nameX, yy, small)
                val who = Paint(header).apply { textSize = 13f; color = Color.BLACK }
                PdfRtl.drawText(c, fitted(partyName, who, 300f), nameX, yy + 15f, who)
                val phone = partyPhone?.takeIf { it.isNotBlank() && it != partyName }
                val factsPaint = Paint(periodPaint).apply { textSize = 8.5f }
                phone?.let { PdfRtl.drawText(c, it, nameX, yy + 27f, factsPaint) }
                // Father's name and village under it (2 Nov 2026, the mockup's line): in a village
                // two customers often share a name, and this is how the shop tells them apart.
                val identity = partyIdentity?.takeIf { it.isNotBlank() }
                val identityY = if (phone != null) yy + 38f else yy + 27f
                identity?.let { PdfRtl.drawText(c, fitted(it, factsPaint, 300f), nameX, identityY, factsPaint) }

                val right = PAGE_W - MARGIN
                val smallR = Paint(small).apply { textAlign = Paint.Align.RIGHT }
                PdfRtl.drawText(c, context.getString(R.string.pdf_stmt_period_label), right, yy, smallR)
                val periodText = if (rows.isNotEmpty()) {
                    val from = Format.dateOnly(rows.first().entry.timestamp)
                    val till = Format.dateOnly(rows.last().entry.timestamp)
                    if (from == till) from else "$from \u2013 $till"
                } else context.getString(R.string.pdf_stmt_no_entries)
                PdfRtl.drawText(c, periodText, right, yy + 13f, Paint(header).apply { textSize = 9f; color = Color.BLACK; textAlign = Paint.Align.RIGHT })
                // The page is said once, at every page's foot (endPage).
                val countLine = Digits.quantity(context.resources, R.plurals.pdf_stmt_entries, rows.size, rows.size)
                PdfRtl.drawText(c, countLine, right, yy + 25f, Paint(periodPaint).apply { textSize = 8f; textAlign = Paint.Align.RIGHT })

                yy += if (identity != null && phone != null) 46f else 34f
                c.drawLine(MARGIN, yy, PAGE_W - MARGIN, yy, lineFill)
                yy += 10f

                // Four boxes: where the account stood, what was taken, what was paid, and where it
                // stands now — the last in the brand's green, the figure the customer looks for.
                val gap = 8f
                val boxW = (PAGE_W - 2 * MARGIN - 3 * gap) / 4f
                val boxH = 40f

                fun summaryBox(index: Int, label: String, value: String, valueColor: Int, strong: Boolean = false) {
                    val left = MARGIN + index * (boxW + gap)
                    val r = RectF(left, yy, left + boxW, yy + boxH)
                    if (strong) c.drawRoundRect(r, 5f, 5f, Paint().apply { color = GREEN_SOFT })
                    boxStroke.color = if (strong) NAVY else BOX_EDGE
                    boxStroke.strokeWidth = if (strong) 1.2f else 0.8f
                    c.drawRoundRect(r, 5f, 5f, boxStroke)
                    val lp = Paint(boxLabel).apply { if (strong) { color = NAVY; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) } }
                    PdfRtl.drawText(c, fitted(label, lp, boxW - 16f), left + 8f, yy + 14f, lp)
                    boxValue.color = valueColor
                    val vp = Paint(boxValue).apply { textSize = 12f }
                    shrinkToFit(value, vp, boxW - 16f, 8f)
                    PdfRtl.drawText(c, value, left + 8f, yy + 31f, vp)
                }

                summaryBox(0, context.getString(R.string.pdf_stmt_opening), Format.signedIfNegative(openingBalance), Color.BLACK)
                summaryBox(1, totalOwedLabel, Format.money(totalGave), RED)
                summaryBox(2, totalPaidLabel, Format.money(totalGot), GREEN)
                summaryBox(3, closingWord(), Format.money(kotlin.math.abs(closingBalance)), NAVY, strong = true)

                yy += boxH + 16f
            }

            // The column heads on the brand's green bar, white words, on every page.
            c.drawRoundRect(RectF(MARGIN, yy - 11f, PAGE_W - MARGIN, yy + 6f), 3f, 3f, navyFill)
            val headW = Paint(header).apply { color = Color.WHITE; textSize = 9f }
            // "Date & details": each row's second line carries its number, goods and note.
            PdfRtl.drawText(c, fitted(context.getString(R.string.pdf_stmt_date_details), headW, xGave - xDate - 12f), xDate + 6f, yy, headW)
            PdfRtl.drawText(c, fitted(owedLabel, headW, xGot - xGave - 6f), xGave, yy, headW)
            PdfRtl.drawText(c, fitted(paidLabel, headW, xBal - xGot - 6f), xGot, yy, headW)
            PdfRtl.drawText(c, fitted(context.getString(R.string.pdf_stmt_balance), headW, PAGE_W - MARGIN - xBal - 4f), xBal, yy, headW)
            return yy + 22f
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
            PdfRtl.drawText(c, label, xDate, y, italic)
            PdfRtl.drawText(c, Format.signedIfNegative(openingBalance), xBal, y, italic)
            y += 8f
            c.drawLine(MARGIN, y, PAGE_W - MARGIN, y, lineFill)
            y += 16f
        }

        var rowIndex = 0
        for (row in rows) {
            // Two lines per entry (details + reference), so break before we run off.
            if (y > PAGE_H - 110f) {
                endPage()
                pageNo++
                page = PdfRtl.startPage(context, doc, 
                    PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create()
                )
                c = page.canvas
                y = drawHeader(first = false)
            }

            val e = row.entry

            // Every other row on a faint wash, so the eye keeps its line across the page.
            if (rowIndex++ % 2 == 1) c.drawRect(MARGIN, y - 11f, PAGE_W - MARGIN, y + 15f, washFill)

            // A "12:00 AM" on every row is not a time anyone recorded — it is
            // what a date with no time of day comes out as.
            PdfRtl.drawText(c, Format.statementStamp(e.timestamp), xDate, y, body)

            if (e.isGiven) {
                body.color = RED
                PdfRtl.drawText(c, Format.money(e.amount), xGave, y, body)
                PdfRtl.drawText(c, "\u2014", xGot, y, dash)
            } else {
                body.color = GREEN
                PdfRtl.drawText(c, Format.money(e.amount), xGot, y, body)
                PdfRtl.drawText(c, "\u2014", xGave, y, dash)
            }
            body.color = Color.BLACK

            // The running balance is the column a reader actually follows
            // down the page, so it gets the weight — bold, like the summary.
            PdfRtl.drawText(c, Format.signedIfNegative(row.runningBalance), xBal, y, header)

            // Second line: reference number, note, any goods that moved, and
            // the payment's own reference with its way (schema 39): the id the
            // customer can look up on his side ("Bank transfer · Ref: TID 778…").
            y += 12f
            val detail = buildList {
                add(e.entryNumber)
                Format.goods(row.items)?.let { add(it) }
                e.note?.takeIf { it.isNotBlank() }?.let { add(it) }
                if (!e.paymentRef.isNullOrBlank()) PaymentMethod.saidOn(context, e.paymentMethod, e.paymentRef)?.let { add(it) }
                if (e.isQarzeHasna) add(context.getString(R.string.pdf_stmt_qarze_hasna))
            }.joinToString("  ·  ")

            // Wrapped, not cut: three items and a long note take up to three lines (L124).
            val detailLines = InvoiceTemplateKit.wrapLines(detail, muted, PAGE_W - MARGIN - xDate, 3)
            detailLines.forEachIndexed { i, line ->
                if (i > 0) y += 10f
                PdfRtl.drawText(c, line, xDate, y, muted)
            }

            y += 8f
            c.drawLine(MARGIN, y, PAGE_W - MARGIN, y, lineFill)
            y += 16f
        }

        // Closing balance — stated in the customer's terms, not accounting signs. Room for the
        // bar, its pay-by line and the call-us line above the page line and the foot (130 let the
        // call-us line run into the foot on a full page).
        if (y > PAGE_H - 190f) {
            endPage()
            pageNo++
            page = PdfRtl.startPage(context, doc, PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            c = page.canvas
            y = drawHeader(first = false)
        }

        // A period that ended before the account's last entry (3 Nov 2026 audit, M06): its own last balance said
        // here, plainly, because the figure below is the whole account today — an October statement with a
        // November sale behind it read "still to pay 2,000" under rows that ended at 1,300.
        rows.lastOrNull()?.runningBalance?.let { atEnd ->
            if (Money.isNotZero(atEnd - closingBalance)) {
                y += 4f
                val endLine = context.getString(
                    R.string.pdf_stmt_period_end_balance,
                    Format.dateOnly(rows.last().entry.timestamp), Format.signedIfNegative(atEnd)
                )
                PdfRtl.drawText(c, fitted(endLine, muted, PAGE_W - 2 * MARGIN), MARGIN, y + 8f, muted)
                y += 14f
            }
        }

        y += 8f
        // How to pay (2 Nov 2026): the shop's bank and wallet beside what is owed, with the
        // number to quote — only on a customer's statement that has something to pay.
        // The IBAN has a line of its own and is never cut (the first proof, 2 Nov 2026: "Test Bank \u00b7
        // PK00TEST000\u2026" \u2014 a customer cannot pay into an account number that ends in dots).
        val bankWho = listOfNotNull(BusinessProfile.bankName(context), BusinessProfile.bankAccountTitle(context))
            .map { it.trim() }.filter { it.isNotEmpty() }.joinToString("  \u00b7  ").takeIf { it.isNotEmpty() }
        val iban = BusinessProfile.bankIban(context)?.trim()?.takeIf { it.isNotEmpty() }
        val wallet = BusinessProfile.bankJazzCash(context)?.trim()?.takeIf { it.isNotEmpty() }
        val quote = rows.lastOrNull { it.entry.isGiven }?.entry?.entryNumber?.takeIf { it.isNotBlank() }
        val howToPay = isCustomer && Money.isPositive(closingBalance) && (bankWho != null || iban != null || wallet != null)
        val dueDay = payBy?.takeIf { isCustomer && Money.isPositive(closingBalance) && it >= DayMath.startOfDay(System.currentTimeMillis()) }
        val barH = if (dueDay != null) 60f else 52f
        // The box grows to hold every line it has: head, bank, IBAN, wallet, the number to quote.
        val payLines = listOfNotNull(bankWho ?: iban, iban.takeIf { bankWho != null }, wallet, quote).size
        val boxH = if (howToPay) maxOf(barH, 25f + 11f * (payLines - 1) + 8f) else barH
        val barRight = if (howToPay) PAGE_W - MARGIN - 212f else PAGE_W - MARGIN
        c.drawRect(MARGIN, y, barRight, y + barH, navyFill)
        c.drawRect(MARGIN, y, MARGIN + 4f, y + barH, goldFill)

        // "Amount you still have to pay" on a customer's statement (2 Nov 2026, the mockup's words):
        // the sum after everything paid, not a new demand.
        val closingLabel = when {
            // Money's half-paisa line, not > 0 (3 Nov 2026 audit, M05): 1000.10 - 1000.00 - 0.10 is 2e-14, and a
            // settled account read "Amount you still have to pay Rs 0.00" beside the "Settled" box.
            Money.isPositive(closingBalance) && isCustomer -> context.getString(R.string.pdf_stmt_amount_still_to_pay)
            Money.isPositive(closingBalance) -> context.getString(R.string.pdf_stmt_you_will_pay)
            Money.isNegative(closingBalance) -> context.getString(R.string.pdf_stmt_you_will_receive)
            else -> context.getString(R.string.pdf_stmt_settled)
        }

        subtitle.color = GOLD
        subtitle.textSize = 10f
        PdfRtl.drawText(c, fitted(closingLabel, subtitle, barRight - MARGIN - 24f), MARGIN + 16f, y + 18f, subtitle)

        title.textSize = 18f
        title.color = Color.WHITE
        PdfRtl.drawText(c, Format.money(closingBalance), MARGIN + 16f, y + 40f, title)
        dueDay?.let {
            val duePaint = Paint(subtitle).apply { textSize = 8.5f; color = GOLD_SOFT }
            PdfRtl.drawText(c, context.getString(R.string.pdf_stmt_pay_by, Format.dateOnly(it)), MARGIN + 16f, y + 53f, duePaint)
        }

        if (howToPay) {
            val boxL = PAGE_W - MARGIN - 200f
            val box = RectF(boxL, y, PAGE_W - MARGIN, y + boxH)
            boxStroke.color = BOX_EDGE; boxStroke.strokeWidth = 0.8f
            c.drawRoundRect(box, 5f, 5f, boxStroke)
            val headP = Paint(header).apply { textSize = 8f }
            PdfRtl.drawText(c, context.getString(R.string.pdf_stmt_how_to_pay), boxL + 8f, y + 13f, headP)
            var ly = y + 25f
            // A name may be cut; an account or wallet number only gets smaller, never shorter.
            fun payLine(text: String, number: Boolean) {
                val p = Paint(body).apply { textSize = 8f; color = Color.BLACK }
                if (number) shrinkToFit(text, p, 184f, 5.5f)
                PdfRtl.drawText(c, if (number) text else fitted(text, p, 184f), boxL + 8f, ly, p)
                ly += 11f
            }
            when {
                bankWho != null -> {
                    payLine(context.getString(R.string.pdf_stmt_bank, bankWho), number = false)
                    iban?.let { payLine(it, number = true) }
                }
                iban != null -> payLine(context.getString(R.string.pdf_stmt_bank, iban), number = true)
            }
            wallet?.let { payLine(context.getString(R.string.pdf_stmt_wallet, it), number = true) }
            quote?.let {
                val q = Paint(muted).apply { textSize = 7f }
                PdfRtl.drawText(c, fitted(context.getString(R.string.pdf_stmt_mention_ref, it), q, 184f), boxL + 8f, ly, q)
            }
        }

        y += boxH + 14f

        // If something does not match, who to call — the shop's own number.
        shopPhone?.let {
            val q = Paint(muted).apply { textSize = 8f }
            PdfRtl.drawText(c, fitted(context.getString(R.string.pdf_stmt_query, Format.ltr(it)), q, PAGE_W - 2 * MARGIN), MARGIN, y, q)
            y += 20f
        } ?: run { y += 4f }

        // The payment code goes in only when there is actually something for
        // the customer to pay. Printing "scan to pay me" on a statement where
        // we owe them would be tone-deaf.
        if (paymentQr != null && Money.isPositive(closingBalance)) {
            if (y > PAGE_H - 210f) {
                endPage()
                pageNo++
                page = PdfRtl.startPage(context, doc, 
                    PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create()
                )
                c = page.canvas
                y = drawHeader(first = false)
            }

            header.textSize = 11f
            PdfRtl.drawText(c, context.getString(R.string.pdf_label_scan_to_pay), MARGIN, y, header)
            y += 8f

            val qrSize = 120
            // Fitted, not stretched: a 600 x 800 crop stays the shape it was (L129).
            InvoiceTemplateKit.drawBitmapFit(c, paymentQr, RectF(MARGIN, y, MARGIN + qrSize, y + qrSize))

            // Say the amount in words next to the code: a personal
            // EasyPaisa/JazzCash QR carries no amount, so the customer types
            // it in themselves and needs to see it plainly.
            val textX = MARGIN + qrSize + 16f
            body.textSize = 10f
            PdfRtl.drawText(c, context.getString(R.string.pdf_stmt_amount_to_pay), textX, y + 30f, body)

            title.textSize = 16f
            title.color = NAVY
            PdfRtl.drawText(c, Format.money(closingBalance), textX, y + 52f, title)

            muted.textSize = 8f
            PdfRtl.drawText(c,
                context.getString(R.string.pdf_stmt_qr_hint_1),
                textX,
                y + 72f,
                muted
            )
            PdfRtl.drawText(c,
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
            if (y > PAGE_H - 180f) {
                endPage()
                pageNo++
                page = PdfRtl.startPage(context, doc, 
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
            PdfRtl.drawBitmap(c,
                signature,
                null,
                Rect(sigLeft.toInt(), y.toInt(), (sigLeft + sigW).toInt(), y.toInt() + drawH),
                null
            )
            y += drawH + 6f

            c.drawLine(sigLeft, y, PAGE_W - MARGIN, y, muted)
            y += 14f
            muted.textSize = 9f
            PdfRtl.drawText(c,
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

        endPage()
        return doc to pageNo
        }

        val (counting, pageCount) = render(null)
        counting.close()
        val (doc, _) = render(pageCount)

        // Written to cache: a statement is a throwaway artefact, not something
        // to quietly accumulate in the user's storage.
        val dir = File(context.cacheDir, "statements").apply { mkdirs() }
        // Letters and digits of every script, with their vowel marks (23 Oct 2026, the review:
        // an Urdu or Hindi name was stripped to nothing and every file was "Statement_Party.pdf").
        // The words around it are the app's own, in its language.
        val fileSafe = { s: String -> s.replace(Regex("[^\\p{L}\\p{M}\\p{N} ]"), "").trim().replace(Regex("\\s+"), "_").take(60) }
        val safeName = fileSafe(partyName).ifEmpty { fileSafe(context.getString(R.string.party)) }
        val prefix = fileSafe(context.getString(R.string.statement_pdf)).ifEmpty { "Statement" }
        val file = File(dir, "${prefix}_${safeName.ifEmpty { "Party" }}.pdf")

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
