package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.DateWords
import com.innovation313.roshankhata.data.PurchaseRegisterRow
import com.innovation313.roshankhata.data.SaleRegisterRow
import com.innovation313.roshankhata.data.ShopCountry
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words
import java.awt.Color
import java.util.Date

/**
 * The sales and purchase register on paper, the document an inspection or a supplier asks for (Marhala 4,
 * part 7 — the phone's data/RegisterReport.kt, page for page; the computer printed a bare three-column table
 * with none of it): the shop, its address and tax number and the period on the green band; what the paper is
 * and what counts as a sale; the summary; the sales, each line with what was sold, how much, its batch, the
 * company and the entry's number, and the purchases with their rate, batch, expiry and bill, each under its
 * total; the dealer's and the inspector's signature lines; and at every foot when it was made and the page.
 */
class RegisterPrint(
    private val words: Words,
    rtl: Boolean,
    private val shopName: String,
    /** The shop's address and tax number, as one line ([identityLine]); null for neither. */
    private val identity: String?,
    private val from: Long,
    private val to: Long,
    private val sales: List<SaleRegisterRow>,
    private val purchases: List<PurchaseRegisterRow>,
    made: Long = System.currentTimeMillis()
) : PhoneSheet(rtl) {

    private val madeAt = DateWords.formatter("dd MMM yyyy, HH:mm").format(Date(made))
    private val salesTotal = sales.sumOf { it.amount }
    private val purchaseTotal = purchases.sumOf { it.lineAmount ?: 0.0 }
    private val missingRate = purchases.count { it.rate == null }

    companion object {
        /**
         * The band's identity line: the address and the tax number, the phone's rule for its name — a
         * Pakistani shop's register says "NTN/STRN", another country's its own name, else "Tax No.".
         */
        fun identityLine(words: Words, address: String?, taxNumber: String?, country: ShopCountry): String? {
            val tax = taxNumber?.trim()?.takeIf { it.isNotEmpty() }?.let { n ->
                when (country) {
                    ShopCountry.PAKISTAN -> words.text(W.pdf_reg_ntn, n)
                    ShopCountry.OTHER -> words.text(W.tax_number_label) + ": " + n
                    else -> country.taxIdName + ": " + n
                }
            }
            return listOfNotNull(address?.trim()?.takeIf { it.isNotEmpty() }, tax).joinToString(" · ").ifEmpty { null }
        }
    }

    override fun Pen.head(): Double {
        val day = DateWords.formatter("dd MMM yyyy")
        box(0.0, 0.0, PAGE_W, 88.0, BAND)
        text(fit(shopName, font(20f, true), PAGE_W - 2 * MARGIN), MARGIN, 26.0, font(20f, true), Color.WHITE)
        text(words.text(W.pdf_reg_title), MARGIN, 44.0, font(10f), GOLD)
        identity?.let { text(fit(it, font(10f), PAGE_W - 2 * MARGIN), MARGIN, 60.0, font(10f), GOLD) }
        text(words.text(W.pdf_reg_period, day.format(Date(from)) + "  —  " + day.format(Date(to))), MARGIN, 78.0, font(10f), GOLD)
        return 112.0
    }

    override fun footer(page: Int): String = words.text(W.pdf_reg_footer, words.text(W.app_name), madeAt, page)

    override fun Pen.write() {
        note(listOf(words.text(W.pdf_reg_note_1), words.text(W.pdf_reg_note_2)))

        section(words.text(W.pdf_rep_summary))
        line(words.text(W.pdf_reg_sales_lines), sales.size.toString())
        line(words.text(W.pdf_reg_total_sales), Format.money(salesTotal), font(11f, true))
        line(words.text(W.pdf_reg_purchase_lines), purchases.size.toString())
        line(words.text(W.pdf_reg_total_purchases), Format.money(purchaseTotal), font(11f, true))
        if (missingRate > 0) line(words.text(W.pdf_reg_no_rate_lines), missingRate.toString(), font(11f), GREY)
        divider()

        val rowDay = DateWords.formatter("dd MMM")
        val xDate = MARGIN + 4.0
        val xName = MARGIN + 66.0
        val xAmount = PAGE_W - MARGIN - 4.0
        val nameRoom = xAmount - 130.0 - xName
        val dateWord = words.text(W.pdf_reg_col_date)
        val amountWord = words.text(W.pdf_reg_col_amount)

        // A register's rows: date, the party, the amount, and what it was on the line under the name.
        fun rows(count: Int, continued: String, column: String, row: (Int) -> Triple<Long, String, Pair<String?, String>>) {
            val head = listOf(Triple(dateWord, xDate, false), Triple(column, xName, false), Triple(amountWord, xAmount, true))
            tableHead(head)
            for (i in 0 until count) {
                if (turnFor(30.0)) {
                    text(continued, MARGIN, y, font(14f, true), BAND)
                    y += 18.0
                    tableHead(head)
                }
                if (i % 2 == 1) box(MARGIN, y, PAGE_W - MARGIN, y + 30.0, ZEBRA)
                val (date, who, rest) = row(i)
                val (amount, sub) = rest
                val base = y + 13.0
                text(rowDay.format(Date(date)), xDate, base, font(9f), GREY)
                text(fit(who, font(11f), nameRoom), xName, base, font(11f), Color.BLACK)
                if (amount != null) text(amount, xAmount, base, font(11f), Color.BLACK, end = true)
                else text("—", xAmount, base, font(11f), GREY, end = true)
                text(fit(sub, font(9f), xAmount - xName), xName, base + 12.0, font(9f), GREY)
                y += 30.0
            }
        }

        fun total(label: String, value: String) {
            turnFor(20.0)
            rule(MARGIN, PAGE_W - MARGIN, y)
            y += 16.0
            text(label, MARGIN + 4.0, y, font(11f, true), Color.BLACK)
            text(value, PAGE_W - MARGIN - 4.0, y, font(11f, true), Color.BLACK, end = true)
            y += 8.0
        }

        // ---- 1. Sales ----
        text(words.text(W.pdf_reg_section_sales), MARGIN, y, font(14f, true), BAND)
        y += 18.0
        if (sales.isEmpty()) {
            text(words.text(W.pdf_reg_no_sales), MARGIN, y, font(11f), GREY)
            y += 18.0
        } else {
            rows(sales.size, words.text(W.pdf_reg_section_sales_cont), words.text(W.pdf_reg_col_customer)) { i ->
                val s = sales[i]
                val sub = listOfNotNull(
                    s.itemName?.takeIf { it.isNotBlank() },
                    s.quantity?.let { Format.qty(it, s.unit) },
                    s.batchNumber?.takeIf { it.isNotBlank() }?.let { words.text(W.pdf_reg_batch, it) },
                    s.company?.takeIf { it.isNotBlank() },
                    s.refNumber?.takeIf { it.isNotBlank() }
                ).joinToString(" · ").ifEmpty { "—" }
                Triple(s.date, s.partyName, Format.money(s.amount) to sub)
            }
            total(words.text(W.pdf_reg_total_sales), Format.money(salesTotal))
        }

        // ---- 2. Purchases ----
        if (y > PAGE_H - 160.0) newPage() else {
            y += 14.0
            rule(MARGIN, PAGE_W - MARGIN, y)
            y += 20.0
        }
        text(words.text(W.pdf_reg_section_purchases), MARGIN, y, font(14f, true), BAND)
        y += 18.0
        if (purchases.isEmpty()) {
            text(words.text(W.pdf_reg_no_purchases), MARGIN, y, font(11f), GREY)
            y += 18.0
        } else {
            rows(purchases.size, words.text(W.pdf_reg_section_purchases_cont), words.text(W.pdf_reg_col_supplier)) { i ->
                val p = purchases[i]
                val sub = listOfNotNull(
                    p.itemName,
                    Format.qty(p.quantity, p.unit),
                    p.rate?.let { "@ " + Format.money(it) },
                    p.batchNumber?.takeIf { it.isNotBlank() }?.let { words.text(W.pdf_reg_batch, it) },
                    p.expiryDate?.let { words.text(W.pdf_reg_expiry, rowDay.format(Date(it))) },
                    p.billNumber?.takeIf { it.isNotBlank() }?.let { words.text(W.pdf_reg_bill, it) }
                ).joinToString(" · ")
                Triple(p.date, p.supplierName, p.lineAmount?.let { Format.money(it) } to sub)
            }
            total(words.text(W.pdf_reg_total_purchases), Format.money(purchaseTotal))
            if (missingRate > 0) {
                y += 12.0
                text(words.text(W.pdf_reg_missing_rate, missingRate), MARGIN + 4.0, y, font(9f), GREY)
                y += 6.0
            }
        }

        // ---- The signatures ----
        if (y > PAGE_H - 160.0) newPage() else y += 24.0
        rule(MARGIN, MARGIN + 180.0, y + 24.0, Color(0xBB, 0xBB, 0xBB))
        rule(PAGE_W - MARGIN - 180.0, PAGE_W - MARGIN, y + 24.0, Color(0xBB, 0xBB, 0xBB))
        text(words.text(W.pdf_reg_dealer_sign), MARGIN, y + 38.0, font(9f), GREY)
        text(words.text(W.pdf_reg_inspector_sign), PAGE_W - MARGIN, y + 38.0, font(9f), GREY, end = true)
        y += 44.0
    }
}
