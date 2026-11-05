package com.innovation313.roshankhata

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.RadioButton
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.button.MaterialButton
import com.innovation313.roshankhata.data.SetupProgress
import java.util.Locale

/**
 * The first screen a new user sees: pick your language, in your own script.
 *
 * Redrawn 5 Nov 2026 from the owner's approved setup mockup ("make the language section
 * accurate"): the 9 Oct page put nine names of one size in a 3 x 3 grid under the splash's
 * painting, so Arabic-script names looked smaller than English ones, Indonesian was called
 * "Indonesia" (the country), and nothing said which language the phone itself uses. Now a
 * compact brand band, then a list: each language's own name in its own script at a size that
 * reads alike (Naskh for the four in Arabic letters, whichever language the app is in), its
 * name in the app's language under it, the phone's own language tagged and marked to begin
 * with, and a radio.
 *
 * The choice is applied through AppCompat's per-app locales (persisted by the
 * autoStoreLocales holder in the manifest, and by the OS itself on Android 13+),
 * so every screen simply reads its strings from the right values-xx file.
 * Roman Urdu rides on the BCP-47 tag ur-Latn (values-b+ur+Latn). Indonesian
 * is the tag "id", whose strings Android keeps in values-in (its old code).
 * Hindi is "hi" (values-hi) and Bengali "bn" (values-bn).
 *
 * Step 1 of the setup ([SetupProgress]); afterwards a setting, from More → Language. Two steps,
 * so nothing is decided by a stray tap (1 Oct): a tap only marks a language, Continue applies
 * it. During the setup Back leaves the app with nothing saved beyond what Continue saved
 * (5 Nov: Back used to clear a mark first, which with the phone's language marked to begin
 * with read as Back doing nothing).
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

        /** The picker's tag for [locale]: "ur-Latn" for Urdu in Latin letters, "id" for Indonesian's old "in". */
        private fun tagOf(locale: Locale): String {
            val language = locale.language.let { if (it == "in") "id" else it }
            return if (language == "ur" && locale.script.equals("Latn", ignoreCase = true)) "ur-Latn" else language
        }
    }

    private enum class Script { LATIN, ARABIC, DEVANAGARI, BENGALI }

    /** One language: its row, its tag, its own name in its own script, and its name in the app's language. */
    private class Lang(val id: Int, val tag: String, val native: String, val gloss: Int, val script: Script)

    private val langs = listOf(
        Lang(R.id.langEnglish, "en", "English", R.string.lang_gloss_en, Script.LATIN),
        Lang(R.id.langRomanUrdu, "ur-Latn", "Roman Urdu", R.string.lang_gloss_ur_latn, Script.LATIN),
        Lang(R.id.langUrdu, "ur", "اردو", R.string.lang_gloss_ur, Script.ARABIC),
        Lang(R.id.langSindhi, "sd", "سنڌي", R.string.lang_gloss_sd, Script.ARABIC),
        Lang(R.id.langPersian, "fa", "فارسی", R.string.lang_gloss_fa, Script.ARABIC),
        Lang(R.id.langArabic, "ar", "العربية", R.string.lang_gloss_ar, Script.ARABIC),
        Lang(R.id.langHindi, "hi", "हिन्दी", R.string.lang_gloss_hi, Script.DEVANAGARI),
        Lang(R.id.langBengali, "bn", "বাংলা", R.string.lang_gloss_bn, Script.BENGALI),
        Lang(R.id.langIndonesian, "id", "Bahasa Indonesia", R.string.lang_gloss_id, Script.LATIN)
    )

    private lateinit var continueBtn: MaterialButton
    private var marked: String? = null
    private var setupRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_language)
        setupRunning = !SetupProgress.isDone(this)
        fitBars()

        if (setupRunning) {
            com.innovation313.roshankhata.ui.StepDots.show(
                findViewById(R.id.langStepDots), findViewById(R.id.tvLangStep), 1)
        }

        val ui = tagOf(resources.configuration.locales[0])
        val phone = phoneTag()
        for (lang in langs) bindRow(lang, ui, phone)

        continueBtn = findViewById(R.id.btnLangContinue)
        continueBtn.setOnClickListener { marked?.let { choose(it) } }

        // During the setup Back leaves (the app opens on this step again next time); from More
        // the default stands: Back returns to the app with the language unchanged.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })

        // A mark survives rotation. The language in use is marked when there is one (from More,
        // or walking back from step 2); on a new phone, the phone's own language when the app
        // speaks it, else English - the language the page itself is then in.
        val restored = savedInstanceState?.getString(STATE_MARKED)
        mark(restored ?: if (isChosen(this)) currentTag() else phone ?: "en")
        com.innovation313.roshankhata.ui.TextFit.relax(findViewById(R.id.langRoot))
        // The marked language in sight when it is far down a short screen.
        langs.firstOrNull { it.tag == marked }?.let { findViewById<View>(it.id) }?.let { row ->
            row.post { row.requestRectangleOnScreen(android.graphics.Rect(0, 0, row.width, row.height), true) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        marked?.let { outState.putString(STATE_MARKED, it) }
    }

    private fun bindRow(lang: Lang, ui: String, phone: String?) {
        val row = findViewById<View>(lang.id)
        val native = row.findViewById<TextView>(R.id.tvLangNative)
        native.text = lang.native
        // Each script at a size that reads like the others: Arabic letters sit small at the same
        // sp, so they are drawn larger - and always in Naskh: under Urdu the phone otherwise picks
        // its tall Nastaliq for all four, Sindhi and Arabic included.
        when (lang.script) {
            Script.LATIN -> Unit
            Script.ARABIC -> {
                native.textLocale = Locale.forLanguageTag("ar")
                native.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
            }
            Script.DEVANAGARI -> {
                native.textLocale = Locale.forLanguageTag("hi")
                native.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17.5f)
            }
            Script.BENGALI -> {
                native.textLocale = Locale.forLanguageTag("bn")
                native.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17.5f)
            }
        }
        // Its name in the app's language, unless that is the same language (or the same word).
        val gloss = getString(lang.gloss)
        val showGloss = lang.tag != ui && !gloss.equals(lang.native, ignoreCase = true)
        row.findViewById<TextView>(R.id.tvLangGloss).apply {
            text = gloss
            visibility = if (showGloss) View.VISIBLE else View.GONE
        }
        val isPhone = lang.tag == phone
        row.findViewById<View>(R.id.tvLangPhone).visibility = if (isPhone) View.VISIBLE else View.GONE
        row.contentDescription = listOfNotNull(
            lang.native, gloss.takeIf { showGloss }, getString(R.string.lang_phone_language).takeIf { isPhone }
        ).joinToString(", ")
        // Read by TalkBack as a radio button: "Urdu, selected".
        row.setAccessibilityDelegate(object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = RadioButton::class.java.name
                info.isCheckable = true
                info.isChecked = host.isSelected
            }
        })
        row.setOnClickListener { mark(lang.tag) }
    }

    /** Marks [tag] (or clears the mark) without applying anything. */
    private fun mark(tag: String?) {
        marked = tag
        for (lang in langs) {
            val row = findViewById<View>(lang.id)
            val on = lang.tag == tag
            row.isSelected = on
            row.findViewById<View>(R.id.ivLangTick).visibility = if (on) View.VISIBLE else View.INVISIBLE
        }
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

    /** The phone's own language as a picker tag, when it is one of the nine; else null. */
    private fun phoneTag(): String? {
        val system = Resources.getSystem().configuration.locales
        if (system.isEmpty) return null
        val tag = tagOf(system[0])
        return langs.firstOrNull { it.tag == tag }?.tag
    }

    /** The app's language as a picker tag; English when none is set. */
    private fun currentTag(): String {
        val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val first = tags.substringBefore(',')
        if (first.isEmpty()) return "en"
        // Locale still answers "in" for Indonesian on many Android versions;
        // the picker's tag is the BCP-47 "id".
        val language = Locale.forLanguageTag(first).language.let { if (it == "in") "id" else it }
        return langs.firstOrNull { it.tag.equals(first, ignoreCase = true) }?.tag
            ?: langs.firstOrNull { it.tag == language }?.tag
            ?: "en"
    }

    /**
     * Behind the bars like the setup's other pages: the band takes the status bar, Continue's
     * panel the gesture bar, each on top of its own padding.
     */
    private fun fitBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = ContextCompat.getColor(this, R.color.page_bg)
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }
        val root = findViewById<View>(R.id.langRoot)
        val header = findViewById<View>(R.id.langHeader)
        val panel = findViewById<View>(R.id.langPanel)
        val h = intArrayOf(header.paddingStart, header.paddingTop, header.paddingEnd, header.paddingBottom)
        val p = intArrayOf(panel.paddingStart, panel.paddingTop, panel.paddingEnd, panel.paddingBottom)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val rtl = v.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
            val start = if (rtl) bars.right else bars.left
            val end = if (rtl) bars.left else bars.right
            header.setPaddingRelative(h[0] + start, h[1] + bars.top, h[2] + end, h[3])
            panel.setPaddingRelative(p[0] + start, p[1], p[2] + end, p[3] + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun choose(tag: String) {
        if (setupRunning) {
            // Before the language: an update must never mistake this setup for an old, finished one.
            SetupProgress.markStarted(this)
        }
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit().putBoolean(KEY_CHOSEN, true).apply()

        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        com.innovation313.roshankhata.ui.AppLanguage.remember(this)

        if (!setupRunning) {
            // Opened from More to change language. The app is already running
            // and the lock, if any, was cleared on the way in — so just go
            // back where they came from.
            finish()
            return
        }

        // The setup: step 2, the shop type. (Until 9 Oct a Google backup page came first; that
        // offer now waits for the first customer, on Home.)
        startActivity(
            Intent(this, ShopTypeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}
