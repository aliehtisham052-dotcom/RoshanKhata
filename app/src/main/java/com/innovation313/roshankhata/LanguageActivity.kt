package com.innovation313.roshankhata

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.widget.TextViewCompat
import com.google.android.material.button.MaterialButton

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
            btn.setTextColor(0xFF1A1A18.toInt())

            // The style sets these too. Repeating them here because this
            // screen is the first thing a new owner sees, and it has now
            // been white-on-white once and black-on-black once: if the tag
            // resolves to a MaterialButton these take effect, and if the
            // style somehow does not reach it, the keys are still legible.
            (btn as? MaterialButton)?.let { material ->
                material.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
                material.cornerRadius = (32 * resources.displayMetrics.density).toInt()
            }

            // The keys wrap to two lines now, so the text no longer has to
            // shrink far to fit — 12sp is the floor rather than 9sp, which was
            // small enough to strain on the one label that needed it.
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                btn, 12, 15, 1, TypedValue.COMPLEX_UNIT_SP
            )
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

        // First run: straight to the ledger, not back through the gate. The
        // gate shows the splash, and the owner has just watched it — sending
        // them back would play the same logo a second time on the one launch
        // where they are least in the mood for it.
        //
        // Skipping the gate skips its lock check too, which costs nothing on
        // a first run: App Lock cannot have been turned on yet, since this
        // screen is the first thing the app has shown.
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_UNLOCKED, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}
