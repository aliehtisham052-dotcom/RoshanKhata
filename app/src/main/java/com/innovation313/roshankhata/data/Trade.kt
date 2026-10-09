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
    GENERAL(R.string.trade_general, R.string.trade_name_general, R.string.trade_eg_general, R.drawable.ic_trade_general, R.color.trade_general_bg, R.color.trade_general_fg);

    fun label(context: Context): String = context.getString(labelRes)

    companion object {
        val DEFAULT = AGRI

        /** The stored name back to a trade; anything unknown or blank is [DEFAULT]. */
        fun of(name: String?): Trade =
            entries.firstOrNull { it.name == name?.trim()?.uppercase() } ?: DEFAULT

        /** The active shop's trade. */
        fun current(context: Context): Trade = of(BusinessProfile.trade(context))
    }
}
