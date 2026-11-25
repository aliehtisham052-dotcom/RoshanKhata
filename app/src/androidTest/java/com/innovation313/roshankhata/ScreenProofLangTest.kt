package com.innovation313.roshankhata

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.innovation313.roshankhata.data.TextSize
import com.innovation313.roshankhata.data.Trade
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * The light second screen proof (25 Nov 2026, the covering array's picture gap): what only a PICTURE shows is the layout, so
 * each of the nine languages is drawn at 85%, 100% and 150% text (27 looks) on the owner's 360 dp width, six layout-heavy
 * screens a look: Home, the Khata list, one customer's page with entries, the add-entry form open, Cashbook with its drawer
 * line and the counter sale. Day and night alternate down the list, so every language gets both; each look takes the next of
 * the 25 trades (the picker's order), so trade x size and trade x theme pairs are drawn too. 162 pictures.
 *
 * ScreenProofPhoneTest keeps the four full looks of ~55 screens; the setup, the switching and the saving are ScreenProofKit's,
 * shared. Files: Download/screen-proof/lang-<language>-<size>-<day|night>-<trade>/lang-...-NN-screen.png, so a look is clear
 * from the name. scripts/gen_covering_array.py lists the 27 looks (needle: this file's `fun looks()`); a look added or changed
 * here changes its PROOFS rows there. Made-up shop only; Urdu, Sindhi, Arabic and Farsi are right to left, Roman Urdu is Latin.
 */
@RunWith(Parameterized::class)
class ScreenProofLangTest(
    private val look: String,
    private val language: String,
    private val level: Int,
    private val phoneScale: Float,
    private val dark: Boolean,
    private val trade: Trade
) {

    private val app: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun ready() = ScreenProofKit.apply(app, language, level, phoneScale, dark, trade)

    @After
    fun back() = ScreenProofKit.reset(app)

    @Test
    fun theLayoutHeavyScreens() {
        EveryScreenOpensTest.seed(app)
        val big = ScreenProofKit.bigAccount(app)
        fun open(cls: Class<out Activity>, extras: Intent.() -> Unit = {}) = Intent(app, cls).apply(extras)
        fun shoot(name: String, intent: Intent, settleMs: Long = 1500) =
            ScreenProofKit.shoot(look, app, "$look-$name", intent, settleMs)

        shoot("01-home", open(MainActivity::class.java) { putExtra(MainActivity.EXTRA_UNLOCKED, true) })
        shoot("02-khata", open(KhataActivity::class.java))
        shoot("03-customer", open(PartyDetailActivity::class.java) { putExtra(PartyDetailActivity.EXTRA_PARTY_ID, big) })
        // The add-entry form open with a seven-figure amount, as 04-add-entry of the four full looks.
        shoot("04-add-entry", open(PartyDetailActivity::class.java) {
            putExtra(PartyDetailActivity.EXTRA_PARTY_ID, big)
            putExtra(PartyDetailActivity.EXTRA_VOICE_AMOUNT, 1234567.0)
            putExtra(PartyDetailActivity.EXTRA_VOICE_IS_GIVEN, true)
        }, settleMs = 2500)
        shoot("11-cashbook", open(CashbookActivity::class.java))
        shoot("17-counter", open(CounterSaleActivity::class.java), settleMs = 2500)
    }

    companion object {
        /** The nine languages, in the covering array's order; "in" is Indonesian (the locale tag is "id"). */
        private val LANGUAGES = listOf(
            "en" to "en", "ur" to "ur", "ur-Latn" to "ur-Latn", "sd" to "sd", "fa" to "fa",
            "ar" to "ar", "hi" to "hi", "bn" to "bn", "in" to "id"
        )

        /** 85%, 100% and 150% of the app's text: the phone's own scale times the app's level (150% = 1.15 x LARGEST 1.30). */
        private val SIZES = listOf(Triple(85, TextSize.SMALL, 1.0f), Triple(100, TextSize.NORMAL, 1.0f), Triple(150, TextSize.LARGEST, 1.15f))

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun looks(): List<Array<Any>> {
            val out = ArrayList<Array<Any>>()
            var i = 0
            for ((code, tag) in LANGUAGES) for ((pct, level, scale) in SIZES) {
                // Alternating down the list: each language's three sizes are day-night-day or night-day-night.
                val dark = i % 2 == 1
                // The picker's order, one trade a look (the 25 come round after 25 looks).
                val trade = Trade.PICKER[i % Trade.PICKER.size]
                val look = "lang-$code-$pct-${if (dark) "night" else "day"}-${trade.name.lowercase()}"
                out.add(arrayOf(look, tag, level, scale, dark, trade))
                i++
            }
            return out
        }
    }
}
