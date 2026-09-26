package com.innovation313.roshankhata.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Light, dark, or whatever the phone is set to.
 *
 * Light is the default, and stays the default for everyone who updates:
 * an owner whose phone is already in dark mode should not open the app one
 * morning to find their ledger has changed colour without being asked.
 * Dark is theirs to choose from Home ⋮ -> Theme.
 *
 * Applied once in RoshanKhataApp, before any screen is built. Changing it
 * calls AppCompatDelegate.setDefaultNightMode, which rebuilds every open
 * screen itself.
 */
object ThemeMode {

    const val LIGHT = 0
    const val DARK = 1
    const val SYSTEM = 2

    private const val PREFS = "theme"
    private const val KEY = "mode"

    fun get(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY, LIGHT).coerceIn(LIGHT, SYSTEM)

    fun set(context: Context, mode: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY, mode.coerceIn(LIGHT, SYSTEM)).apply()
        apply(context)
    }

    fun apply(context: Context) {
        AppCompatDelegate.setDefaultNightMode(
            when (get(context)) {
                DARK -> AppCompatDelegate.MODE_NIGHT_YES
                SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                else -> AppCompatDelegate.MODE_NIGHT_NO
            }
        )
    }
}
