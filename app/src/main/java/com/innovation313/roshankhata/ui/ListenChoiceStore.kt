package com.innovation313.roshankhata.ui

import android.content.Context

/**
 * What this phone remembers about where speech is understood (25 Nov 2026, D10): a [ListenChoice.Mode] in the sheet's
 * own preferences ("hudhud"), on this phone only — never in the book, a backup or a sync. The 16 Nov switch's key
 * ("listen_local_only") is kept in step (true for the phone, false for a yes) so [HudhudPrefs.listenOnPhoneOnly] still
 * says the truth for what was chosen; an undecided phone has no such key and is read as undecided here
 * ([ListenChoice.fromStored]).
 */
object ListenChoiceStore {

    private const val PREFS = "hudhud"
    private const val KEY_MODE = "listen_mode"
    // The same key HudhudPrefs writes for its switch.
    private const val KEY_LEGACY = "listen_local_only"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun mode(context: Context): ListenChoice.Mode {
        val p = prefs(context)
        val legacy = if (p.contains(KEY_LEGACY)) p.getBoolean(KEY_LEGACY, false) else null
        return ListenChoice.fromStored(p.getString(KEY_MODE, null), legacy)
    }

    fun set(context: Context, mode: ListenChoice.Mode) {
        prefs(context).edit().apply {
            putString(KEY_MODE, mode.name)
            when (mode) {
                ListenChoice.Mode.PHONE -> putBoolean(KEY_LEGACY, true)
                ListenChoice.Mode.ONLINE -> putBoolean(KEY_LEGACY, false)
                ListenChoice.Mode.UNDECIDED -> remove(KEY_LEGACY)
            }
        }.apply()
    }
}
