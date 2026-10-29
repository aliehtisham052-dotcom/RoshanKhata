package com.innovation313.roshankhata.data

import android.content.Context
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.innovation313.roshankhata.R

/**
 * What kind of shop keeps this book (4 Oct 2026, worldwide phase 2).
 *
 * Roshan Khata grew up in a pesticide-and-seed shop, so a few of its
 * features speak that trade: crop seasons, spray advice, the agriculture
 * inspector's register. A kiryana or a mobile shop has no use for them.
 * The trade is the one switch those features will read (phase 3): the
 * core — udhar, payments, invoices, backup — is the same for every shop.
 *
 * Stored by [name] in the shop's Profile. A book that never chose one is
 * [AGRI], because every book written before this existed was one, and
 * nothing may change under an existing owner's feet. The twelve names
 * match the trade cards the owner already designs from.
 */
enum class Trade(
    /** The full name, as Profile -> Shop type shows it. */
    @StringRes val labelRes: Int,
    /** The short name on the picker's card. */
    @StringRes val nameRes: Int,
    /** What such a shop sells, under the name on the card. */
    @StringRes val exampleRes: Int,
    @DrawableRes val iconRes: Int,
    /** The icon chip's fill and glyph (day and night values). */
    @ColorRes val chipRes: Int,
    @ColorRes val inkRes: Int
) {
    AGRI(R.string.trade_agri, R.string.trade_name_agri, R.string.trade_eg_agri, R.drawable.ic_trade_agri, R.color.trade_agri_bg, R.color.trade_agri_fg),
    MEDICAL(R.string.trade_medical, R.string.trade_name_medical, R.string.trade_eg_medical, R.drawable.ic_trade_medical, R.color.trade_medical_bg, R.color.trade_medical_fg),
    MOBILE(R.string.trade_mobile, R.string.trade_name_mobile, R.string.trade_eg_mobile, R.drawable.ic_trade_mobile, R.color.trade_mobile_bg, R.color.trade_mobile_fg),
    KIRYANA(R.string.trade_kiryana, R.string.trade_name_kiryana, R.string.trade_eg_kiryana, R.drawable.ic_trade_kiryana, R.color.trade_kiryana_bg, R.color.trade_kiryana_fg),
    HARDWARE(R.string.trade_hardware, R.string.trade_name_hardware, R.string.trade_eg_hardware, R.drawable.ic_trade_hardware, R.color.trade_hardware_bg, R.color.trade_hardware_fg),
    DAIRY(R.string.trade_dairy, R.string.trade_name_dairy, R.string.trade_eg_dairy, R.drawable.ic_trade_dairy, R.color.trade_dairy_bg, R.color.trade_dairy_fg),
    TAILOR(R.string.trade_tailor, R.string.trade_name_tailor, R.string.trade_eg_tailor, R.drawable.ic_trade_tailor, R.color.trade_tailor_bg, R.color.trade_tailor_fg),
    AUTO(R.string.trade_auto, R.string.trade_name_auto, R.string.trade_eg_auto, R.drawable.ic_trade_auto, R.color.trade_auto_bg, R.color.trade_auto_fg),
    FURNITURE(R.string.trade_furniture, R.string.trade_name_furniture, R.string.trade_eg_furniture, R.drawable.ic_trade_furniture, R.color.trade_furniture_bg, R.color.trade_furniture_fg),
    SALON(R.string.trade_salon, R.string.trade_name_salon, R.string.trade_eg_salon, R.drawable.ic_trade_salon, R.color.trade_salon_bg, R.color.trade_salon_fg),
    SOLAR(R.string.trade_solar, R.string.trade_name_solar, R.string.trade_eg_solar, R.drawable.ic_trade_solar, R.color.trade_solar_bg, R.color.trade_solar_fg),
    // Twelve more (11 Oct): the shops most common the world over that the
    // first twelve did not cover. Each has its own words in every language
    // (TradeVocab) and its own icon; none turns on an agri-only feature.
    CLOTHING(R.string.trade_clothing, R.string.trade_name_clothing, R.string.trade_eg_clothing, R.drawable.ic_trade_clothing, R.color.trade_clothing_bg, R.color.trade_clothing_fg),
    FOOTWEAR(R.string.trade_footwear, R.string.trade_name_footwear, R.string.trade_eg_footwear, R.drawable.ic_trade_footwear, R.color.trade_footwear_bg, R.color.trade_footwear_fg),
    ELECTRONICS(R.string.trade_electronics, R.string.trade_name_electronics, R.string.trade_eg_electronics, R.drawable.ic_trade_electronics, R.color.trade_electronics_bg, R.color.trade_electronics_fg),
    RESTAURANT(R.string.trade_restaurant, R.string.trade_name_restaurant, R.string.trade_eg_restaurant, R.drawable.ic_trade_restaurant, R.color.trade_restaurant_bg, R.color.trade_restaurant_fg),
    BAKERY(R.string.trade_bakery, R.string.trade_name_bakery, R.string.trade_eg_bakery, R.drawable.ic_trade_bakery, R.color.trade_bakery_bg, R.color.trade_bakery_fg),
    MEAT(R.string.trade_meat, R.string.trade_name_meat, R.string.trade_eg_meat, R.drawable.ic_trade_meat, R.color.trade_meat_bg, R.color.trade_meat_fg),
    PRODUCE(R.string.trade_produce, R.string.trade_name_produce, R.string.trade_eg_produce, R.drawable.ic_trade_produce, R.color.trade_produce_bg, R.color.trade_produce_fg),
    STATIONERY(R.string.trade_stationery, R.string.trade_name_stationery, R.string.trade_eg_stationery, R.drawable.ic_trade_stationery, R.color.trade_stationery_bg, R.color.trade_stationery_fg),
    COSMETICS(R.string.trade_cosmetics, R.string.trade_name_cosmetics, R.string.trade_eg_cosmetics, R.drawable.ic_trade_cosmetics, R.color.trade_cosmetics_bg, R.color.trade_cosmetics_fg),
    BUILDING(R.string.trade_building, R.string.trade_name_building, R.string.trade_eg_building, R.drawable.ic_trade_building, R.color.trade_building_bg, R.color.trade_building_fg),
    FUEL(R.string.trade_fuel, R.string.trade_name_fuel, R.string.trade_eg_fuel, R.drawable.ic_trade_fuel, R.color.trade_fuel_bg, R.color.trade_fuel_fg),
    WHOLESALE(R.string.trade_wholesale, R.string.trade_name_wholesale, R.string.trade_eg_wholesale, R.drawable.ic_trade_wholesale, R.color.trade_wholesale_bg, R.color.trade_wholesale_fg),
    GENERAL(R.string.trade_general, R.string.trade_name_general, R.string.trade_eg_general, R.drawable.ic_trade_general, R.color.trade_general_bg, R.color.trade_general_fg),
    /**
     * Any shop the list does not name (11 Oct). The owner writes what it is
     * ("Flower shop") and, if he likes, three things it sells; those become
     * every hint and the first suggestions, until his own products take
     * over. See [OtherTrade].
     */
    OTHER(R.string.trade_other, R.string.trade_name_other, R.string.trade_eg_other, R.drawable.ic_trade_other, R.color.trade_other_bg, R.color.trade_other_fg);

    /** The full name; for [OTHER], the owner's own words once written. */
    fun label(context: Context): String =
        if (this == OTHER) OtherTrade.name(context) ?: context.getString(labelRes)
        else context.getString(labelRes)

    /** The line under it; for [OTHER], the three goods the owner wrote, once written. */
    fun example(context: Context): String =
        if (this == OTHER) OtherTrade.items(context).takeIf { it.isNotEmpty() }?.joinToString(" \u00b7 ")
            ?: context.getString(exampleRes)
        else context.getString(exampleRes)

    companion object {
        val DEFAULT = AGRI

        /** The stored name back to a trade; anything unknown or blank is [DEFAULT]. */
        fun of(name: String?): Trade =
            entries.firstOrNull { it.name == name?.trim()?.uppercase() } ?: DEFAULT

        /** The active shop's trade. */
        fun current(context: Context): Trade = of(BusinessProfile.trade(context))
    }
}
