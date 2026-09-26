package com.innovation313.roshankhata

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * The first screen a new user sees: pick your language, in your own script.
 * Drawn as artwork with real touch areas on the six buttons it shows; see
 * activity_language.xml for how they are kept on those buttons.
 *
 * The choice is applied through AppCompat's per-app locales (persisted by the
 * autoStoreLocales holder in the manifest, and by the OS itself on Android 13+),
 * so every screen simply reads its strings from the right values-xx file.
 * Roman Urdu rides on the BCP-47 tag ur-Latn (values-b+ur+Latn).
 *
 * Shown once on first run; afterwards the app goes straight to the gate. It can
 * be reopened any time from More → Language.
 */
class LanguageActivity : BaseActivity() {

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

        // Edge to edge with NO padding, unlike the other screens: the artwork
        // is the whole screen and runs under both bars. The artwork is deep
        // green top to bottom, so light icons on both bars, with the system's
        // grey scrim off so the footer is not cut by a bar.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        // Language tag per touch area. English clears to the default (base
        // values/). The areas sit over the buttons drawn in the artwork, so
        // they are plain Views: there is no button of their own to style.
        val choices = mapOf(
            R.id.langEnglish to "en",
            R.id.langRomanUrdu to "ur-Latn",
            R.id.langUrdu to "ur",
            R.id.langSindhi to "sd",
            R.id.langPersian to "fa",
            R.id.langArabic to "ar"
        )

        for ((id, tag) in choices) {
            findViewById<View>(id).setOnClickListener { choose(tag) }
        }
    }

    private fun choose(tag: String) {
        val firstRun = !isChosen(this)

        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit().putBoolean(KEY_CHOSEN, true).apply()

        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))

        if (!firstRun) {
            // Opened from More to change language. The app is already running
            // and the lock, if any, was cleared on the way in — so just go
            // back where they came from.
            finish()
            return
        }

        // First run: the one-time welcome, which offers to connect a Google
        // account for backup before the ledger opens. Not back through the
        // gate — the owner has just watched the splash, and the welcome sends
        // them on to the ledger itself once seen (or skipped).
        startActivity(
            Intent(this, WelcomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}
