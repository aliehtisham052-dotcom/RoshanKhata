package com.innovation313.roshankhata.ui

import android.content.Context
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.EntryItem
import com.innovation313.roshankhata.data.EntryLines
import com.innovation313.roshankhata.data.LineMath
import com.innovation313.roshankhata.data.RateType

/**
 * An entry's goods as text, in the two places the owner reads them — one
 * definition, so the khata list and the receipt can never disagree.
 *
 * A single item reads exactly as a single item always has
 * ("2 bori — Urea"), so older entries look as they did.
 */
object GoodsText {

    private fun rateLabel(context: Context, rateType: String?): String? = when (rateType) {
        RateType.CREDIT -> context.getString(R.string.rate_type_credit)
        RateType.CASH -> context.getString(R.string.rate_type_cash)
        else -> null
    }

    /**
     * The khata list row. Several items: their names on one line, then
     * "Items: 3 · Credit rate" beneath — the full detail is on the receipt.
     */
    fun row(context: Context, items: List<EntryItem>, rateType: String?): String? {
        if (items.isEmpty()) return null
        val body = if (items.size == 1) {
            Format.goods(items)
        } else {
            items.mapNotNull { it.itemName?.trim()?.takeIf { n -> n.isNotEmpty() } }
                .joinToString(", ")
                .ifEmpty { null }
        }
        val tail = listOfNotNull(
            if (items.size > 1) context.getString(R.string.entry_items_count, items.size) else null,
            rateLabel(context, rateType)
        ).joinToString(" · ")
        return listOfNotNull(body, tail.ifEmpty { null }).joinToString("\n").ifEmpty { null }
    }

    /**
     * The receipt: one numbered line per item with its rate and what it came
     * to ("1. 2 bori — Urea × Rs 4,800 = Rs 9,600", a free item marked as
     * such), then — when there is more than one item or any rate — the
     * items' total and the rate type, and any difference from the amount
     * actually written, in words. The amount is the owner's; this only shows
     * how it compares.
     */
    fun receipt(context: Context, items: List<EntryItem>, rateType: String?, amount: Double): String? {
        if (items.isEmpty()) return null
        val priced = items.any { it.rate != null || it.isBonus }
        if (items.size == 1 && !priced) return Format.goods(items)

        val lines = items.mapIndexed { i, item ->
            val goods = Format.goods(item.itemName, item.quantity, item.unit).orEmpty()
            val price = when {
                item.isBonus -> " (" + context.getString(R.string.bonus_tag) + ")"
                item.rate != null -> {
                    val total = LineMath.lineTotal(item.quantity, item.rate)
                    " × " + Format.money(item.rate) + (total?.let { " = " + Format.money(it) }.orEmpty())
                }
                else -> ""
            }
            "${i + 1}. $goods$price"
        }.toMutableList()

        val total = LineMath.linesTotal(items)
        val summary = listOfNotNull(
            total?.let { context.getString(R.string.receipt_items_total, Format.money(it)) },
            rateLabel(context, rateType)
        ).joinToString(" · ")
        if (summary.isNotEmpty()) lines += summary

        if (total != null) {
            when (val check = EntryLines.compare(total, amount)) {
                is EntryLines.AmountCheck.Equal -> Unit
                is EntryLines.AmountCheck.Less -> lines += context.getString(
                    R.string.lines_amount_less, Format.money(total), Format.money(amount), Format.money(check.by)
                )
                is EntryLines.AmountCheck.More -> lines += context.getString(
                    R.string.lines_amount_more, Format.money(total), Format.money(amount), Format.money(check.by)
                )
            }
        }
        return lines.joinToString("\n")
    }
}
