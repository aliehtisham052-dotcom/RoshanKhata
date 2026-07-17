package com.innovation313.roshankhata

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

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
            R.id.langPashto to "ps",
            R.id.langPersian to "fa",
            R.id.langArabic to "ar",
            R.id.langBengali to "bn",
            R.id.langHindi to "hi",
            R.id.langFrench to "fr",
            R.id.langTurkish to "tr",
            R.id.langMalay to "ms"
        )

        for ((id, tag) in choices) {
            findViewById<Button>(id).setOnClickListener { choose(tag) }
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
