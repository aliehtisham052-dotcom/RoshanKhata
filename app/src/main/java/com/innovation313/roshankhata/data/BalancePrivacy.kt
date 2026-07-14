package com.innovation313.roshankhata.data

import android.content.Context

/**
 * Hiding the net balance.
 *
 * The number at the top of the home screen is the single most revealing thing
 * in this app: what the whole business is worth, in one figure, legible from
 * across a counter. A shopkeeper's phone sits face-up while customers lean over
 * it. Nobody needs to be able to read that.
 *
 * Per-customer balances stay visible, because the owner needs them open on the
 * screen while they work — and a customer glancing at their OWN balance is not
 * a problem. The total is different: it is nobody's business but the owner's.
 *
 * The choice persists. A privacy setting that resets every time the app opens
 * is not a privacy setting; it is a chore, and it will be abandoned.
 */
object BalancePrivacy {

    private const val PREFS = "balance_privacy"
    private const val KEY_HIDDEN = "net_balance_hidden"

    /** The mask. Wide enough to give nothing away about the magnitude. */
    const val MASK = "\u2022 \u2022 \u2022 \u2022 \u2022 \u2022"

    fun isHidden(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_HIDDEN, false)

    fun setHidden(context: Context, hidden: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HIDDEN, hidden)
            .apply()
    }

    fun toggle(context: Context): Boolean {
        val next = !isHidden(context)
        setHidden(context, next)
        return next
    }
}
