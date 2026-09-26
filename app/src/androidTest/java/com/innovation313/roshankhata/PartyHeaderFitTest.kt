package com.innovation313.roshankhata

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.innovation313.roshankhata.data.TextSize
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.math.abs

/**
 * The customer ledger's header, measured on a real Android in every language.
 *
 * The owner found both of these by sight (26 Sep 2026, Sindhi):
 *  - Call, WhatsApp and Statement were cut off at the bottom while SMS sat
 *    higher than the rest. The row lined its buttons up by their text
 *    baselines, and a Latin label sits on a different baseline from an
 *    Arabic-script one.
 *  - The Call button and the avatar sat hard against the right-hand edge with
 *    an empty band on the left. The header's start/end padding was being
 *    re-applied as left/right, so it never mirrored.
 *
 * For each language x text size this checks:
 *  - the four action buttons share one top and one height;
 *  - each one ends inside its row, and the row inside the header;
 *  - the action row is inset the same distance from both screen edges.
 */
@RunWith(Parameterized::class)
class PartyHeaderFitTest(
    private val language: String,
    private val level: Int,
    @Suppress("unused") private val name: String
) {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun ready() {
        context.getSharedPreferences("language", Context.MODE_PRIVATE)
            .edit().putBoolean("chosen", true).commit()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
        }
        TextSize.setLevel(context, level)
    }

    @After
    fun reset() {
        TextSize.setLevel(context, TextSize.NORMAL)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        }
    }

    @Test
    fun actionButtonsAreWholeEvenAndCentred() {
        val seed = EveryScreenOpensTest.seed(context)
        val intent = Intent(context, PartyDetailActivity::class.java)
            .putExtra(PartyDetailActivity.EXTRA_PARTY_ID, seed.customerId)

        ActivityScenario.launch<Activity>(intent).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val tag = "[$language, level $level]"
                val header = activity.findViewById<View>(R.id.header)
                val bar = activity.findViewById<ViewGroup>(R.id.reminderBar)
                val buttons = listOf(R.id.btnCall, R.id.btnWhatsApp, R.id.btnSms, R.id.btnPdf)
                    .map { activity.findViewById<View>(it) }
                    .filter { it.visibility == View.VISIBLE }
                assertTrue("$tag no action buttons visible", buttons.isNotEmpty())

                val problems = mutableListOf<String>()
                fun name(v: View) = v.resources.getResourceEntryName(v.id)
                fun screen(v: View) = IntArray(2).also { v.getLocationOnScreen(it) }

                // One top, one height.
                val tops = buttons.map { screen(it)[1] }.toSet()
                val heights = buttons.map { it.height }.toSet()
                if (tops.size != 1) problems += "tops differ: " + buttons.joinToString { "${name(it)}=${screen(it)[1]}" }
                if (heights.size != 1) problems += "heights differ: " + buttons.joinToString { "${name(it)}=${it.height}" }

                // Each button inside its row; the row inside the header.
                val barBottom = screen(bar)[1] + bar.height
                buttons.forEach { b ->
                    val bottom = screen(b)[1] + b.height
                    if (bottom > barBottom) problems += "${name(b)} ends ${bottom - barBottom}px below its row"
                }
                val headerBottom = screen(header)[1] + header.height
                if (barBottom > headerBottom) problems += "row ends ${barBottom - headerBottom}px below the header"

                // Same inset from both screen edges (1dp tolerance for rounding).
                val width = activity.window.decorView.width
                val left = screen(bar)[0]
                val right = width - (left + bar.width)
                val tolerance = activity.resources.displayMetrics.density.toInt() + 1
                if (abs(left - right) > tolerance) problems += "row inset left=${left}px right=${right}px"

                assertTrue("$tag ${problems.joinToString("; ")}", problems.isEmpty())
            }
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{2}")
        fun cases(): List<Array<Any>> =
            listOf("en", "ur", "ar", "fa", "sd", "ur-Latn").flatMap { lang ->
                listOf(
                    arrayOf<Any>(lang, TextSize.NORMAL, "$lang @ normal"),
                    arrayOf<Any>(lang, TextSize.LARGEST, "$lang @ largest")
                )
            }
    }
}
