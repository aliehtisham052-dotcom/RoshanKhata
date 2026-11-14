package com.innovation313.roshankhata.ui

import android.icu.text.NumberFormat
import android.icu.util.ULocale
import com.innovation313.roshankhata.data.Currency
import java.math.BigDecimal
import android.icu.util.Currency as IcuCurrency

/**
 * A sum of money as words for the voice (14 Nov 2026, the owner: "dirham, euro, dollar — har mulk ki currency ke hisaab
 * se bole"): "1250.50" in the shop's own currency as the language says it — "1250.50 UAE dirhams", "1250.50 درهم
 * إماراتي" — from the phone's own ICU tables, which hold every currency's name in every language (CLDR). Nothing is
 * bundled, so the app does not grow, and each language says its own words.
 *
 * Latin digits (`-u-nu-latn`): the voice reads them in its own language, while some voices spell a phone's default
 * Arabic-Indic or Persian digits one by one. No grouping mark (the 13 Nov bug: a comma was taken for a pause).
 */
object HudhudMoneyWords {

    /** The ISO code of one of the app's own currency signs ("USD" for "$"); null for a sign the owner typed himself. */
    fun isoOf(sign: String): String? = Currency.CHOICES.firstOrNull { it.first == sign }?.second

    /**
     * [amount] (a plain figure: "1250" or "1250.50") with [iso]'s name in [languageKey]'s language ("ur", "en", "in"…);
     * null when the phone's tables have no name for it in that language (ICU would print the code instead) or the
     * figure cannot be read.
     */
    fun say(amount: String, iso: String, languageKey: String): String? = runCatching {
        val tag = if (languageKey == "in") "id" else languageKey
        val value = BigDecimal(amount)
        val fraction = value.scale().coerceAtLeast(0)
        val format = NumberFormat.getInstance(ULocale.forLanguageTag("$tag-u-nu-latn"), NumberFormat.PLURALCURRENCYSTYLE)
        format.currency = IcuCurrency.getInstance(iso)
        format.isGroupingUsed = false
        format.minimumFractionDigits = fraction
        format.maximumFractionDigits = fraction
        format.format(value).trim().takeIf { it.isNotEmpty() && !it.contains(iso) }
    }.getOrNull()
}
