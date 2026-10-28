package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words

/**
 * An entry's goods as its receipt says them — one definition for the phone's receipt card and the computer's
 * (moved from the phone's GoodsText on 25 Oct 2026, Marhala 4, part 6, so the two can never disagree).
 *
 * A single item reads exactly as a single item always has ("2 bori — Urea"), so older entries look as they did.
 */
object ReceiptGoods {

    /** "Cotton · Bollworm · 250 ml per acre" (v25), or null when none was written. */
    fun advice(item: EntryItem): String? =
        listOfNotNull(item.crop, item.pest, item.dose)
            .map { it.trim() }.filter { it.isNotEmpty() }
            .takeIf { it.isNotEmpty() }?.joinToString(" · ")

    fun rateLabel(words: Words, rateType: String?): String? = when (rateType) {
        RateType.CREDIT -> words.text(W.rate_type_credit)
        RateType.CASH -> words.text(W.rate_type_cash)
        else -> null
    }

    /**
     * The receipt: one numbered line per item with its rate and what it came
     * to ("1. 2 bori — Urea × Rs 4,800 = Rs 9,600", a free item marked as
     * such), then — when there is more than one item or any rate — the
     * items' total and the rate type, and any difference from the amount
     * actually written, in words. The amount is the owner's; this only shows
     * how it compares.
     */
    fun text(words: Words, items: List<EntryItem>, rateType: String?, amount: Double): String? {
        if (items.isEmpty()) return null
        val priced = items.any { it.rate != null || it.isBonus }
        val advised = items.any { advice(it) != null }
        if (items.size == 1 && !priced && !advised) return Format.goods(items)

        val lines = items.mapIndexed { i, item ->
            val goods = Format.goods(item.itemName, item.quantity, item.unit).orEmpty()
            val rate = item.rate
            val price = when {
                item.isBonus -> " (" + words.text(W.bonus_tag) + ")"
                rate != null -> {
                    val total = LineMath.lineTotal(item.quantity, rate)
                    " × " + Format.money(rate) + (total?.let { " = " + Format.money(it) }.orEmpty())
                }
                else -> ""
            }
            "${i + 1}. $goods$price" +
                (advice(item)?.let { "\n    " + words.text(W.advice_line, it) }.orEmpty())
        }.toMutableList()
        // In writing on the receipt, so it reads as what it is.
        if (advised) lines += words.text(W.advice_disclaimer)

        val total = LineMath.linesTotal(items)
        val summary = listOfNotNull(
            total?.let { words.text(W.receipt_items_total, Format.money(it)) },
            rateLabel(words, rateType)
        ).joinToString(" · ")
        if (summary.isNotEmpty()) lines += summary

        if (total != null) {
            when (val check = EntryLines.compare(total, amount)) {
                is EntryLines.AmountCheck.Equal -> Unit
                is EntryLines.AmountCheck.Less -> lines += words.text(
                    W.lines_amount_less, Format.money(total), Format.money(amount), Format.money(check.by)
                )
                is EntryLines.AmountCheck.More -> lines += words.text(
                    W.lines_amount_more, Format.money(total), Format.money(amount), Format.money(check.by)
                )
            }
        }
        return lines.joinToString("\n")
    }
}
