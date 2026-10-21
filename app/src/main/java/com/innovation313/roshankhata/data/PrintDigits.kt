package com.innovation313.roshankhata.data

import android.content.Context

/**
 * The phone's side of [Digits.nativeOnPrint]: whether printed sheets carry
 * the language's own digits (21 Oct 2026, the nine-language review). Kept on
 * this phone, not in the book: it is how this owner's customers read, not a
 * fact about the shop's ledger. Read as every screen opens.
 */
object PrintDigits {
    private const val PREFS = "print_digits"
    private const val KEY = "native_on_print"

    fun load(context: Context) {
        Digits.nativeOnPrint = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)
    }

    fun set(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
        Digits.nativeOnPrint = on
    }
}
