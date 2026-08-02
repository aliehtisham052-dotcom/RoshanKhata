package com.innovation313.roshankhata.data

import android.content.Context

/**
 * Which optional invoice fields a shop actually wants to see, at all —
 * the owner's own request after Vyapar's Transaction settings screen: a
 * shop that never charges tax should be able to turn that field off
 * entirely, not just leave it blank on every invoice.
 *
 * Every feature defaults to ON — this exists so a shop can hide what it
 * does not use, not so a fresh install starts with things missing. Turning
 * a feature off only hides it from the entry screens; it never deletes a
 * value already saved on a past invoice, and never touches the templates'
 * own "only print this line if it has a value" behaviour.
 */
object InvoiceFeatureSettings {

    private const val PREFS = "invoice_feature_settings"
    private const val KEY_DISCOUNT = "discount_enabled"
    private const val KEY_TAX = "tax_enabled"
    private const val KEY_EXTRA_CHARGE = "extra_charge_enabled"
    private const val KEY_RECEIVED = "received_enabled"
    private const val KEY_NOTE = "note_enabled"
    private const val KEY_DUE_DATE = "due_date_enabled"

    private fun prefs(context: Context) =
        // Per business, Business 1 on the legacy file: which fields an
        // invoice offers is the shop's own habit, not the phone's — a
        // pesticide shop that charges no tax and a cloth shop that does can
        // now live on one phone without sharing one setting.
        context.getSharedPreferences(PREFS + Businesses.suffix(context), Context.MODE_PRIVATE)

    fun discountEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_DISCOUNT, true)
    fun setDiscountEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_DISCOUNT, value).apply()
    }

    fun taxEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_TAX, true)
    fun setTaxEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_TAX, value).apply()
    }

    fun extraChargeEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_EXTRA_CHARGE, true)
    fun setExtraChargeEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_EXTRA_CHARGE, value).apply()
    }

    fun receivedEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_RECEIVED, true)
    fun setReceivedEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_RECEIVED, value).apply()
    }

    fun noteEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_NOTE, true)
    fun setNoteEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_NOTE, value).apply()
    }

    fun dueDateEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_DUE_DATE, true)
    fun setDueDateEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_DUE_DATE, value).apply()
    }

    /** True if every feature is off — the "More options" button has nothing left to open. */
    fun anyOptionalFieldEnabled(context: Context): Boolean =
        discountEnabled(context) || taxEnabled(context) || extraChargeEnabled(context) ||
            receivedEnabled(context) || noteEnabled(context)
}
