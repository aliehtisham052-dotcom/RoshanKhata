package com.innovation313.roshankhata.data

import android.content.Context

/**
 * The owner's own words for a shop the trade list does not name (11 Oct).
 *
 * Picking "Other" asks what the shop is ("Flower shop") and, optionally,
 * three things it sells ("Roses, garlands, bouquets"). They are kept in the
 * shop's own profile, beside its trade, and read by [Trade.label] and
 * [TradeVocab]: the hints say "Item (Roses, garlands, bouquets…)" and a new
 * book is offered them first. The owner's own products take over as soon as
 * he has some, exactly as for every other trade.
 *
 * The picker sets them on [pending] before it hands back [Trade.OTHER]; the
 * caller's ordinary setTrade / setTradeOf then stores them with the trade,
 * so every place a trade is chosen (first run, Profile, a new shop) keeps
 * them without knowing about them.
 */
object OtherTrade {

    class Words(val name: String?, val items: List<String>)

    /** Written by the picker the moment Other is confirmed; taken by the next save of OTHER. */
    @Volatile
    var pending: Words? = null

    fun name(context: Context): String? = BusinessProfile.otherTradeName(context)

    fun items(context: Context): List<String> = split(BusinessProfile.otherTradeItems(context))

    /** "Roses, garlands; bouquets" -> three clean words, at most. */
    fun split(text: String?): List<String> =
        text.orEmpty().split(',', '،', ';', '\n', '·')
            .map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(3)
}
