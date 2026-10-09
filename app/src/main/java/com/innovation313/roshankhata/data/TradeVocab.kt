package com.innovation313.roshankhata.data

import android.content.Context
import com.innovation313.roshankhata.R

/**
 * What the active shop calls its goods, in the app's own language
 * (9 Oct 2026).
 *
 * The owner's catch: a dairy's entry form still read "Item (Urea, Feed…)",
 * and a kiryana's rate list still offered "pesticide, fertilizer, seed".
 * Every example in every hint was a string written for the first shop this
 * app was built in, and the shop type — chosen on the Profile since 4 Oct —
 * reached only three features.
 *
 * This is the one place a trade's words live. Each trade has, in strings.xml
 * and so in every one of the app's languages:
 *   items     — three example goods, for the hints ("Item (Milk, Yogurt, Ghee…)")
 *   units     — which of UnitWords' units this trade sells in, first in its list
 *   types     — the product types its rate list offers
 *   starters  — the goods a NEW book of this trade is first offered, until
 *               the owner has products of their own
 *
 * The hints are built here, from those lists, so a screen never carries a
 * trade's example again. The language follows the app's locale by itself,
 * because these are resources: an owner on Sindhi sees Sindhi examples in
 * Sindhi letters, one on English sees English. Nothing here is saved; a
 * trade switch changes every hint on the next screen.
 */
object TradeVocab {

    private fun arr(context: Context, trade: Trade, part: String): List<String> {
        val id = context.resources.getIdentifier("vocab_${trade.name.lowercase()}_$part", "array", context.packageName)
        return if (id == 0) emptyList() else context.resources.getStringArray(id).toList()
    }

    /** Three example goods of the active trade, in the app's language. */
    fun items(context: Context, trade: Trade = Trade.current(context)): List<String> = arr(context, trade, "items")

    /** The product types the active trade's rate list offers. */
    fun types(context: Context, trade: Trade = Trade.current(context)): List<String> = arr(context, trade, "types")

    /** The goods a new book of this trade is offered before it has its own. */
    fun starters(context: Context, trade: Trade = Trade.current(context)): List<String> = arr(context, trade, "starters")

    /** The trade's units as UnitWords KEYS, in the order its list should lead with. */
    fun unitKeys(context: Context, trade: Trade = Trade.current(context)): List<String> = arr(context, trade, "units")

    /** The trade's units as the user's own labels (UnitWords turns a key into the word their language uses). */
    fun unitLabels(context: Context, trade: Trade = Trade.current(context)): List<String> =
        unitKeys(context, trade).map { UnitWords.label(context, it) }

    private fun join(list: List<String>): String = list.joinToString(", ")

    /** "Item (Milk, Yogurt, Ghee…)" */
    fun itemHint(context: Context): String = context.getString(R.string.vocab_item_hint, join(items(context)))

    /** "Unit (litre, kg, packet…)" */
    fun unitHint(context: Context): String = context.getString(R.string.vocab_unit_hint, join(unitLabels(context).take(3)))

    /** "Unit — litre, kg, packet (optional)" */
    fun unitOptionalHint(context: Context): String = context.getString(R.string.vocab_unit_optional, join(unitLabels(context).take(3)))

    /** "Type — Milk, Packaged, Feed" */
    fun typeHint(context: Context): String = context.getString(R.string.vocab_type_hint, join(types(context).take(3)))

    /** The goods paragraph on the entry form, with this trade's example: "2 litres of milk, 1 kg ghee". */
    fun goodsHelp(context: Context): String {
        val items = items(context)
        val units = unitLabels(context)
        val example = buildString {
            if (items.isNotEmpty()) append("5 ").append(units.getOrNull(0) ?: "").append(' ').append(items[0])
            if (items.size > 1) append(", 2 ").append(units.getOrNull(1) ?: units.getOrNull(0) ?: "").append(' ').append(items[1])
        }.replace("  ", " ").trim()
        return context.getString(R.string.vocab_goods_help, example)
    }
}
