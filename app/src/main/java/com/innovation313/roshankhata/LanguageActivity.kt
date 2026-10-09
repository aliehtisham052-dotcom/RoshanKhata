package com.innovation313.roshankhata

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
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
 * The splash's own painting fills the top of the screen, anchored by its
 * tagline ([com.innovation313.roshankhata.ui.SplashArt]); under it a paper panel carries the heading, nine
 * real buttons of one size in a 3 x 3 grid, and Continue - see
 * activity_language.xml. (Until 9 Oct this was a second, dark-green painting
 * with the buttons placed on it by guidelines; the two first screens now
 * share one picture and the picker looks like the rest of the app.)
 *
 * The choice is applied through AppCompat's per-app locales (persisted by the
 * autoStoreLocales holder in the manifest, and by the OS itself on Android 13+),
 * so every screen simply reads its strings from the right values-xx file.
 * Roman Urdu rides on the BCP-47 tag ur-Latn (values-b+ur+Latn). Indonesian
 * is the tag "id", whose strings Android keeps in values-in (its old code).
 * Hindi is "hi" (values-hi) and Bengali "bn" (values-bn).
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
        /**
         * Clear space between the tagline and the panel's heading, in dp:
         * more than the 40dp fade at the region's foot, so the tagline sits
         * on the painting's own light and never in the fade.
         */
        private const val TAGLINE_GAP_DP = 52f
        /** True once the user has picked a language on first run. */
        fun isChosen(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_CHOSEN, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_language)

        // Edge to edge with NO padding, unlike the other screens: the painting
        // runs under the status bar and the panel under the navigation bar.
        // Both are light, so dark icons on both bars, with the system's grey
        // scrim off so the panel is not cut by a bar.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        // Language tag per button. English clears to the default (base
        // values/).
        val choices = mapOf(
            R.id.langEnglish to "en",
            R.id.langRomanUrdu to "ur-Latn",
            R.id.langUrdu to "ur",
            R.id.langSindhi to "sd",
            R.id.langPersian to "fa",
            R.id.langArabic to "ar",
            R.id.langHindi to "hi",
            R.id.langBengali to "bn",
            R.id.langIndonesian to "id"
        )

        // The four names in Arabic letters are drawn in one hand, Naskh,
        // whichever language the app is in. Left to the app's own language
        // they change with it: under Urdu the phone picks its tall Nastaliq
        // for all four, Sindhi and Arabic included.
        val naskh = Locale.forLanguageTag("ar")
        for (id in listOf(R.id.langUrdu, R.id.langSindhi, R.id.langPersian, R.id.langArabic)) {
            findViewById<TextView>(id).textLocale = naskh
        }

        com.innovation313.roshankhata.ui.SplashArt.anchor(findViewById(R.id.ivLangArt), TAGLINE_GAP_DP)
        // Step 1 of 2 on first run only; from More this is a setting, not a step.
        if (!isChosen(this)) {
            com.innovation313.roshankhata.ui.StepDots.show(
                findViewById(R.id.langStepDots), findViewById(R.id.tvLangStep), 1)
        }

        languages = choices
        continueBtn = findViewById(R.id.btnLangContinue)

        for ((id, tag) in choices) {
            findViewById<View>(id).setOnClickListener { mark(tag) }
        }
        continueBtn.setOnClickListener { marked?.let { choose(it) } }

        // The screen runs under the navigation bar, so the panel's bottom
        // padding takes the bar's own height on top of its 16dp.
        val panel = findViewById<View>(R.id.langPanel)
        val basePad = panel.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(panel) { v, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, basePad + nav)
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

    private lateinit var languages: Map<Int, String>
    private lateinit var continueBtn: MaterialButton
    private var marked: String? = null

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        marked?.let { outState.putString(STATE_MARKED, it) }
    }

    /** Marks [tag] (or clears the mark) without applying anything. */
    private fun mark(tag: String?) {
        marked = tag
        for ((id, t) in languages) findViewById<View>(id).isSelected = t == tag
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
        // Locale still answers "in" for Indonesian on many Android versions;
        // the picker's tag is the BCP-47 "id".
        val language = Locale.forLanguageTag(first).language.let { if (it == "in") "id" else it }
        return languages.values.firstOrNull { it.equals(first, ignoreCase = true) }
            ?: languages.values.firstOrNull { it == language }
            ?: "en"
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

        // First run: step 2, the shop type, then the ledger. (Until 9 Oct a
        // Google backup page came first; that offer now waits for the first
        // customer, on Home.)
        startActivity(
            Intent(this, ShopTypeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}
