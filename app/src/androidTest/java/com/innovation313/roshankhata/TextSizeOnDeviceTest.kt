package com.innovation313.roshankhata

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.innovation313.roshankhata.data.TextSize
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The text-size setting on a real Android: that the size actually reaches a
 * screen, that the chosen LANGUAGE survives alongside it (both travel through
 * the same attachBaseContext, and that pairing is what a future AppCompat
 * would break first), and that fixed-height buttons are relaxed only when
 * the text is larger — never at normal size.
 */
@RunWith(AndroidJUnit4::class)
class TextSizeOnDeviceTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun ready() {
        context.getSharedPreferences("language", Context.MODE_PRIVATE)
            .edit().putBoolean("chosen", true).commit()
    }

    @After
    fun reset() {
        TextSize.setLevel(context, TextSize.NORMAL)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        }
    }

    @Test
    fun largestReachesTheScreenAndTheLanguageSurvivesIt() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ur"))
        }
        TextSize.setLevel(context, TextSize.LARGEST)
        val system = context.resources.configuration.fontScale

        ActivityScenario.launch(KhataActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val config = activity.resources.configuration
                assertEquals(
                    "font scale on screen",
                    TextSize.effective(system, TextSize.LARGEST), config.fontScale, 0.001f
                )
                assertEquals("language on screen", "ur", config.locales[0].language)

                val stillFixed = fixedHeightText(activity.findViewById(android.R.id.content))
                assertTrue("text views still at a fixed height: $stillFixed", stillFixed.isEmpty())
            }
        }
    }

    @Test
    fun normalLeavesTheScreenExactlyAsDesigned() {
        TextSize.setLevel(context, TextSize.NORMAL)
        val system = context.resources.configuration.fontScale

        ActivityScenario.launch(KhataActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(system, activity.resources.configuration.fontScale, 0f)
                if (system <= 1.0f) {
                    // The ledger screen's filter and action buttons are fixed
                    // at 36-44dp by design. At normal size they must stay so.
                    val fixed = fixedHeightText(activity.findViewById(android.R.id.content))
                    assertTrue("normal size relaxed the designed heights", fixed.isNotEmpty())
                }
            }
        }
    }

    private fun fixedHeightText(root: View?): List<String> {
        val found = mutableListOf<String>()
        fun walk(v: View) {
            if (v is TextView && (v.layoutParams?.height ?: 0) > 0) {
                found += runCatching { v.resources.getResourceEntryName(v.id) }
                    .getOrDefault(v.javaClass.simpleName)
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        root?.let { walk(it) }
        return found
    }
}
