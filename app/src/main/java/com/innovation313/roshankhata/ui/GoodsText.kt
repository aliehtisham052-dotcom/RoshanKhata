package com.innovation313.roshankhata.ui

import com.innovation313.roshankhata.data.string

import com.innovation313.roshankhata.data.Digits

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

    /** "Cotton · Bollworm · 250 ml per acre" (v25), or null when none was written (shared: ReceiptGoods). */
    fun advice(item: com.innovation313.roshankhata.data.EntryItem): String? =
        com.innovation313.roshankhata.data.ReceiptGoods.advice(item)

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
            if (items.size > 1) Digits.string(context.resources, R.string.entry_items_count, items.size) else null,
            rateLabel(context, rateType)
        ).joinToString(" · ")
        return listOfNotNull(body, tail.ifEmpty { null }).joinToString("\n").ifEmpty { null }
    }

    /**
     * The receipt: one numbered line per item with its rate and what it came to, the items' total and the rate
     * type, and any difference from the amount written — the shared ReceiptGoods since 25 Oct 2026, so the
     * computer's receipt card says exactly this.
     */
    fun receipt(context: Context, items: List<EntryItem>, rateType: String?, amount: Double): String? =
        com.innovation313.roshankhata.data.ReceiptGoods.text(com.innovation313.roshankhata.data.AndroidWords(context), items, rateType, amount)
}
