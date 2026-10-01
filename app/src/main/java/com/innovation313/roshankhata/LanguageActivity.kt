package com.innovation313.roshankhata

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.button.MaterialButton
import java.util.Locale

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
 *
 * Two steps, so nothing is decided by a stray tap (1 Oct): tapping a language
 * only marks it, and Continue applies it. Before, a tap saved the language and
 * jumped to the welcome screen at once, so a mistaken tap could not be taken
 * back — Back from the welcome closed the app with that language already kept.
 * Back here, on first run, first clears a mark; with nothing marked it leaves
 * the app without saving anything, so the picker comes up again next time.
 */
class LanguageActivity : BaseActivity() {

    companion object {
        private const val PREFS = "language"
        private const val KEY_CHOSEN = "chosen"
        private const val STATE_MARKED = "marked"
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

        hotspots = choices
        continueBtn = findViewById(R.id.btnLangContinue)

        for ((id, tag) in choices) {
            findViewById<View>(id).setOnClickListener { mark(tag) }
        }
        continueBtn.setOnClickListener { marked?.let { choose(it) } }

        // The screen runs under the navigation bar, so lift Continue clear of
        // it by the bar's own height on top of its 20dp.
        val baseMargin = (20 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(continueBtn) { v, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            (v.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = baseMargin + nav
            v.requestLayout()
            insets
        }

        // First run: Back undoes a mark before it leaves. From More the
        // default stands: Back returns to the app with the language unchanged.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!isChosen(this@LanguageActivity) && marked != null) {
                    mark(null)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // A mark survives rotation. Opened from More, the language in use is
        // marked to begin with, so Continue is ready and the ring shows where
        // the app stands now.
        val restored = savedInstanceState?.getString(STATE_MARKED)
        mark(restored ?: if (isChosen(this)) currentTag() else null)
    }

    private lateinit var hotspots: Map<Int, String>
    private lateinit var continueBtn: MaterialButton
    private var marked: String? = null

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        marked?.let { outState.putString(STATE_MARKED, it) }
    }

    /** Marks [tag] (or clears the mark) without applying anything. */
    private fun mark(tag: String?) {
        marked = tag
        for ((id, t) in hotspots) findViewById<View>(id).isSelected = t == tag
        continueBtn.isEnabled = tag != null
        // The label reads in the marked language: someone choosing Arabic
        // should be able to read the button that confirms it.
        continueBtn.text = if (tag == null) getString(R.string.continue_action)
        else stringIn(tag, R.string.continue_action)
    }

    private fun stringIn(tag: String, resId: Int): String {
        val config = Configuration(resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        return createConfigurationContext(config).getString(resId)
    }

    /** The app's language as a picker tag; English when none is set. */
    private fun currentTag(): String {
        val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val first = tags.substringBefore(',')
        if (first.isEmpty()) return "en"
        return hotspots.values.firstOrNull { it.equals(first, ignoreCase = true) }
            ?: hotspots.values.firstOrNull {
                it == Locale.forLanguageTag(first).language
            } ?: "en"
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
