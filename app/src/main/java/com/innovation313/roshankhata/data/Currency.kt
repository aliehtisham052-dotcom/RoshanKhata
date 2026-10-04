package com.innovation313.roshankhata.data

import android.content.Context

/**
 * Which money sign a shop's figures wear (4 Oct 2026, worldwide phase 1).
 *
 * Every figure the app prints goes through [com.innovation313.roshankhata.ui.Format.money],
 * and that method has no Context. So the shop's choice is read from its Profile
 * once into [symbol] and refreshed at the moments it can change: app start,
 * every screen coming forward (a business switch, a restore and a viewer
 * load all pass through a screen), the background worker, and the Profile
 * screen saving a new choice.
 *
 * Only the SIGN changes. Amounts are stored as plain numbers and stay what
 * they are — choosing AED does not convert Rs 500 into AED 500, it prints
 * the same 500 with a different sign. The choice is per shop, backed up
 * with the Profile, and defaults to Rs so every existing book prints
 * exactly as it did before this existed.
 */
object Currency {

    const val DEFAULT = "Rs"

    /** Longest sign allowed when the owner types their own. */
    const val MAX_LENGTH = 5

    /**
     * Sign shown first, then the ISO code the picker names it by. Order:
     * Pakistan and its neighbours first (where most of the app's shops are),
     * then the Gulf, then the rest alphabetically by code.
     */
    val CHOICES: List<Pair<String, String>> = listOf(
        "Rs" to "PKR",
        "₹" to "INR",
        "৳" to "BDT",
        "AED" to "AED",
        "SAR" to "SAR",
        "QAR" to "QAR",
        "OMR" to "OMR",
        "KWD" to "KWD",
        "BHD" to "BHD",
        "$" to "USD",
        "€" to "EUR",
        "£" to "GBP",
        "CA$" to "CAD",
        "A$" to "AUD",
        "EGP" to "EGP",
        "Rp" to "IDR",
        "RM" to "MYR",
        "₦" to "NGN",
        "KSh" to "KES",
        "R" to "ZAR",
        "₺" to "TRY"
    )

    @Volatile
    var symbol: String = DEFAULT
        private set

    /** Re-read the active shop's choice. Cheap: SharedPreferences is in memory after first load. */
    fun refresh(context: Context) {
        symbol = BusinessProfile.currency(context)
    }

    /** Save the shop's choice and print with it from now on. */
    fun set(context: Context, value: String?) {
        BusinessProfile.setCurrency(context, value)
        refresh(context)
    }

    /** A typed sign, trimmed to what fits; blank falls back to [DEFAULT]. */
    fun clean(value: String?): String {
        val v = value?.trim()?.take(MAX_LENGTH).orEmpty()
        return v.ifEmpty { DEFAULT }
    }

    /** "Rs — PKR" for the picker and the Profile row; a custom sign shows alone. */
    fun label(value: String): String {
        val code = CHOICES.firstOrNull { it.first == value }?.second
        return if (code == null || code == value) value else "$value — $code"
    }
}
