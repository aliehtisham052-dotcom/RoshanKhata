package com.innovation313.roshankhata

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.innovation313.roshankhata.data.TextSize
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Every Home tile shows its whole name — measured, not eyeballed.
 *
 * The owner found this by sight (25-26 Sep 2026): in Arabic, Farsi, Sindhi
 * and Urdu the second line of names like "Business Settings" was sliced off
 * at the bottom of the tile. Those scripts are drawn in a fallback font whose
 * lines are much taller than Latin ones, and the tile was a fixed 72dp sized
 * for Latin. A crash test cannot see that; this one measures it.
 *
 * For each language x text size it checks, on a real Android:
 *  - the label's last visible line ends inside the label (no self-clipping);
 *  - the label ends inside its tile (the card does not cut it);
 *  - every tile in both grids has the same height (the grid stays even);
 *  - every tile has the same width, and a short last row lines up with the
 *    columns above it (it used to come out wider and shifted);
 *  - at normal size, no name is cut short with "…" in any language.
 */
@RunWith(Parameterized::class)
class HomeTilesFitTest(
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
    fun everyTileShowsItsWholeName() {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_UNLOCKED, true)
        ActivityScenario.launch<Activity>(intent).use { scenario ->
            // Let the pre-draw pass that evens out the tiles run first.
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val labels = mutableListOf<TextView>()
                collectLabels(activity.findViewById(android.R.id.content), labels)
                assertTrue("no Home tiles found", labels.size >= 10)

                val problems = mutableListOf<String>()
                val heights = mutableSetOf<Int>()
                val widths = mutableSetOf<Int>()
                val columnEdges = mutableSetOf<Int>()
                for (label in labels) {
                    val text = label.text.toString()
                    val layout = label.layout
                    if (layout == null) { problems += "'$text': not laid out"; continue }
                    val tile = label.parent.parent as View
                    heights += tile.height
                    widths += tile.width
                    columnEdges += IntArray(2).also { tile.getLocationOnScreen(it) }[0]

                    // 1. The last visible line ends inside the label itself.
                    val shown = minOf(layout.lineCount, 2)
                    val textBottom = label.totalPaddingTop + layout.getLineBottom(shown - 1)
                    if (textBottom > label.height) {
                        problems += "'$text': text ${textBottom}px > label ${label.height}px"
                    }

                    // 2. The label ends inside its tile.
                    val l = IntArray(2).also { label.getLocationOnScreen(it) }
                    val t = IntArray(2).also { tile.getLocationOnScreen(it) }
                    val labelBottom = l[1] + label.height
                    val tileBottom = t[1] + tile.height
                    if (labelBottom > tileBottom) {
                        problems += "'$text': ends ${labelBottom - tileBottom}px below its tile"
                    }

                    // 3. At normal size, every name fits in full.
                    if (level == TextSize.NORMAL) {
                        val cut = (0 until layout.lineCount).any { layout.getEllipsisCount(it) > 0 }
                        if (cut) problems += "'$text': shortened with … at normal size"
                    }
                }

                assertTrue("[$language, level $level] ${problems.joinToString("; ")}", problems.isEmpty())
                assertEquals("[$language, level $level] tiles of different heights: $heights", 1, heights.size)
                // One width, to the pixel. LinearLayout shares out the pixels left
                // over when the row width does not divide by three, so on a real
                // screen one tile can be a single pixel wider (316 vs 317px on the
                // CI emulator). Invisible; the slot was wrong by 2 x TILE_GAP_DP.
                assertTrue(
                    "[$language, level $level] tiles of different widths: $widths",
                    widths.max() - widths.min() <= 1
                )
                // Three columns means exactly three left edges across every row.
                assertEquals("[$language, level $level] tiles off the column lines: $columnEdges", 3, columnEdges.size)
            }
        }
    }

    private fun collectLabels(v: View?, out: MutableList<TextView>) {
        if (v == null) return
        if (v is TextView && v.id == R.id.tvFeatureLabel && v.isShown) out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) collectLabels(v.getChildAt(i), out)
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
