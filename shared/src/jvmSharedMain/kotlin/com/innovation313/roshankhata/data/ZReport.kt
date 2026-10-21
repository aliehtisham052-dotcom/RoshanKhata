package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words

/**
 * The day's figures from the counter and the books (POS Phase 4, the Z
 * report): what was rung up and how it was paid, what came back, what was
 * voided, the discounts and tax given, the cash that moved through the
 * khata and the cashbook, and what the drawer should hold against what was
 * counted. Everything here is summed from the rows themselves (sales,
 * returns, entries, cashbook), never from a stored total — the rule every
 * figure in the app keeps.
 *
 * The window is [from, to): a calendar day by default, or whatever the
 * owner asks for. [countedCash] and [opening] come from the day's close
 * where one was made, so the report says what the count found.
 */
data class ZReport(
    val from: Long,
    val to: Long,
    val saleCount: Int,
    val salesTotal: Double,
    val byMethod: Map<String, Double>,
    val onAccount: Double,
    val discounts: Double,
    val tax: Double,
    val voidCount: Int,
    val voidedTotal: Double,
    val returnCount: Int,
    val returnsTotal: Double,
    val refundsByMethod: Map<String, Double>,
    val khataCashIn: Double,
    val khataCashOut: Double,
    val cashbookIn: Double,
    val cashbookOut: Double,
    val opening: Double?,
    val counted: Double?,
    val profit: Double?
) {
    /** Cash in the drawer by the books: opening + every cash in − every cash out. */
    val expectedCash: Double? get() = opening?.let { LineMath.round(it + khataCashIn + cashbookIn - khataCashOut - cashbookOut) }
    val variance: Double? get() = if (counted != null && expectedCash != null) LineMath.round(counted - expectedCash!!) else null

    companion object {
        suspend fun compute(dao: KhataDao, from: Long, to: Long): ZReport {
            val sales = dao.salesBetween(from, to)
            val done = sales.filter { it.status == SaleStatus.DONE }
            val voided = sales.filter { it.status == SaleStatus.VOID }
            val byMethod = LinkedHashMap<String, Double>()
            var discounts = 0.0; var tax = 0.0; var onAccount = 0.0
            var cost = 0.0; var costKnown = true
            for (s in done) {
                val full = dao.saleWithLines(s.id) ?: continue
                discounts += full.sale.discountAmount + full.items.sumOf { it.discount }
                // Tax added on top, and tax inside prices that hold it (schema 36): both are the government's.
                tax += full.sale.taxAmount + full.items.filter { it.taxIncluded == true }.sumOf { it.taxAmount ?: 0.0 }
                onAccount += full.sale.creditAmount
                for (p in full.payments) byMethod[p.method] = (byMethod[p.method] ?: 0.0) + p.amount
                for (i in full.items) {
                    val c = i.costRate
                    if (c == null) costKnown = false else cost += c * i.quantity
                }
            }
            val returns = dao.returnsBetween(from, to)
            val doneReturns = returns.filter { it.status == SaleStatus.DONE }
            val refunds = LinkedHashMap<String, Double>()
            for (r in doneReturns) if (r.refundAmount > 0.0) refunds[r.refundMethod] = (refunds[r.refundMethod] ?: 0.0) + r.refundAmount
            val salesTotal = LineMath.round(done.sumOf { it.grandTotal })
            val returnsTotal = LineMath.round(doneReturns.sumOf { it.total })
            // The day's close, if one was made for this window's day.
            val close = dao.dayCloseFor(DayCloseMath.dayOf(from))
            return ZReport(
                from = from, to = to, saleCount = done.size, salesTotal = salesTotal,
                byMethod = byMethod.mapValues { LineMath.round(it.value) }, onAccount = LineMath.round(onAccount),
                discounts = LineMath.round(discounts), tax = LineMath.round(tax),
                voidCount = voided.size, voidedTotal = LineMath.round(voided.sumOf { it.grandTotal }),
                returnCount = doneReturns.size, returnsTotal = returnsTotal,
                refundsByMethod = refunds.mapValues { LineMath.round(it.value) },
                khataCashIn = dao.khataCashBetween(false, from - 1, to - 1), khataCashOut = dao.khataCashBetween(true, from - 1, to - 1),
                cashbookIn = dao.cashbookCashBetween(true, from - 1, to - 1), cashbookOut = dao.cashbookCashBetween(false, from - 1, to - 1),
                opening = close?.opening, counted = close?.counted,
                profit = if (costKnown && done.isNotEmpty()) LineMath.round(salesTotal - tax - cost) else null
            )
        }
    }

    /** The report as lines of text — the receipt printer, a share, a dialog. */
    fun lines(words: Words, money: (Double) -> String): List<Pair<String, String>> {
        fun m(key: String) = words.text(
            when (key) {
                PaymentMethod.CASH -> W.payment_method_cash
                PaymentMethod.BANK -> W.payment_method_bank
                PaymentMethod.CHEQUE -> W.payment_method_cheque
                SaleReturn.ACCOUNT -> W.pos_refund_account
                else -> W.payment_method_online
            }
        )
        val out = ArrayList<Pair<String, String>>()
        out += words.text(W.z_sales_count) to saleCount.toString()
        out += words.text(W.z_sales_total) to money(salesTotal)
        byMethod.forEach { (k, v) -> out += ("  " + m(k)) to money(v) }
        if (onAccount > 0.0) out += ("  " + words.text(W.pos_to_account)) to money(onAccount)
        if (discounts > 0.0) out += words.text(W.pos_discount) to money(discounts)
        if (tax > 0.0) out += words.text(W.pos_tax) to money(tax)
        if (voidCount > 0) out += words.text(W.z_voids, voidCount.toString()) to money(voidedTotal)
        if (returnCount > 0) {
            out += words.text(W.z_returns, returnCount.toString()) to money(returnsTotal)
            refundsByMethod.forEach { (k, v) -> out += ("  " + m(k)) to money(v) }
        }
        out += words.text(W.z_cash_in) to money(khataCashIn + cashbookIn)
        out += words.text(W.z_cash_out) to money(khataCashOut + cashbookOut)
        opening?.let { out += words.text(W.dc_opening) to money(it) }
        expectedCash?.let { out += words.text(W.z_expected) to money(it) }
        counted?.let { out += words.text(W.dc_counted) to money(it) }
        variance?.let { out += words.text(W.z_variance) to money(it) }
        profit?.let { out += words.text(W.z_profit) to money(it) }
        return out
    }
}
