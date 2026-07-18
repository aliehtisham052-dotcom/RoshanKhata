package com.innovation313.roshankhata

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.widget.TextViewCompat

/**
 * The first screen a new user sees: pick your language, in your own script.
 *
 * The choice is applied through AppCompat's per-app locales (persisted by the
 * autoStoreLocales holder in the manifest, and by the OS itself on Android 13+),
 * so every screen simply reads its strings from the right values-xx file.
 * Roman Urdu rides on the BCP-47 tag ur-Latn (values-b+ur+Latn).
 *
 * Shown once on first run; afterwards the app goes straight to the gate. It can
 * be reopened any time from More → Language.
 */
class LanguageActivity : AppCompatActivity() {

    companion object {
        private const val PREFS = "language"
        private const val KEY_CHOSEN = "chosen"
        /** True once the user has picked a language on first run. */
        fun isChosen(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_CHOSEN, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_language)

        // Language tag per button. English clears to the default (base values/).
        val choices = mapOf(
            R.id.langEnglish to "en",
            R.id.langRomanUrdu to "ur-Latn",
            R.id.langUrdu to "ur",
            R.id.langSindhi to "sd",
            R.id.langPersian to "fa",
            R.id.langArabic to "ar"
        )

        for ((id, tag) in choices) {
            val btn = findViewById<Button>(id)
            btn.setOnClickListener { choose(tag) }
            // Colour and shape both come from bg_lang_key now. A tint here
            // would repaint that drawable and flatten its corners back to a
            // rectangle, and the cornerRadius this used to set was being
            // applied to null: the LangKey style descends from
            // Widget.AppCompat.Button, so these never inflate as MaterialButtons.
            btn.setTextColor(0xFF1A1A18.toInt())
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                btn, 11, 15, 1, TypedValue.COMPLEX_UNIT_SP
            )
        }
    }

    private fun choose(tag: String) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit().putBoolean(KEY_CHOSEN, true).apply()

        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))

        // setApplicationLocales recreates activities as needed; move on to the
        // gate (lock-or-home) with a clean stack.
        startActivity(
            Intent(this, GateActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}
