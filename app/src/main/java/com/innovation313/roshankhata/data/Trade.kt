package com.innovation313.roshankhata.data

import android.content.Context
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
enum class Trade(@StringRes val labelRes: Int) {
    AGRI(R.string.trade_agri),
    MEDICAL(R.string.trade_medical),
    MOBILE(R.string.trade_mobile),
    KIRYANA(R.string.trade_kiryana),
    HARDWARE(R.string.trade_hardware),
    DAIRY(R.string.trade_dairy),
    TAILOR(R.string.trade_tailor),
    AUTO(R.string.trade_auto),
    FURNITURE(R.string.trade_furniture),
    SALON(R.string.trade_salon),
    SOLAR(R.string.trade_solar),
    GENERAL(R.string.trade_general);

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
