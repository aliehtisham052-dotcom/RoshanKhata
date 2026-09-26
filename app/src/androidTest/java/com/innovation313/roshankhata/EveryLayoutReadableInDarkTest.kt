package com.innovation313.roshankhata

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.innovation313.roshankhata.data.ThemeMode
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every layout file in the app, drawn in dark mode and measured.
 *
 * EveryScreenOpensTest only sees what a screen shows on its own. Dialogs
 * (Add entry, photo options) are separate windows and list rows only appear
 * with data, so the owner found the Add-entry chips' text and borders
 * vanishing in dark mode (26 Sep 2026) after every screen had passed.
 * This inflates each layout — dialogs, rows, sheets, screens — onto the page
 * colour it will sit on, fills every empty text with a sample so it has
 * something to measure, and checks text (3:1), icons (3:1) and outlines
 * (2:1). Colours set from code at run time are covered by the screen test.
 */
@RunWith(AndroidJUnit4::class)
class EveryLayoutReadableInDarkTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun dark() {
        context.getSharedPreferences("language", Context.MODE_PRIVATE)
            .edit().putBoolean("chosen", true).commit()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeMode.set(context, ThemeMode.DARK)
        }
    }

    @After
    fun light() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeMode.set(context, ThemeMode.LIGHT)
        }
    }

    @Test
    fun everyLayoutIsReadableInDarkMode() {
        val host = Intent(context, AboutActivity::class.java)
        ActivityScenario.launch<Activity>(host).use { scenario ->
            scenario.onActivity { activity ->
                val content = activity.findViewById<ViewGroup>(android.R.id.content)
                val width = content.width.takeIf { it > 0 } ?: activity.resources.displayMetrics.widthPixels
                val problems = mutableListOf<String>()
                val skipped = mutableListOf<String>()
                var checked = 0

                for (field in R.layout::class.java.fields) {
                    val name = field.name
                    val id = field.getInt(null)
                    // Where the layout will sit: a dialog or sheet on the
                    // surface colour, everything else on the page.
                    val onSurface = name.startsWith("dialog") || name.startsWith("sheet") ||
                        name.startsWith("view_") || name.startsWith("bottom")
                    val page = ContextCompat.getColor(activity, if (onSurface) R.color.surface else R.color.page_bg)
                    val holder = FrameLayout(activity).apply { setBackgroundColor(page) }
                    try {
                        LayoutInflater.from(activity).inflate(id, holder, true)
                    } catch (t: Throwable) {
                        skipped += name
                        continue
                    }
                    fillBlanks(holder)
                    content.addView(holder, ViewGroup.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT))
                    holder.measure(
                        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                    )
                    holder.layout(0, 0, holder.measuredWidth, holder.measuredHeight)

                    val found = mutableListOf<String>()
                    ContrastCheck.text(holder, page, found)
                    ContrastCheck.iconsAndBorders(holder, page, found)
                    found.forEach { problems += "$name: $it" }
                    content.removeView(holder)
                    checked++
                }

                println("EveryLayoutReadableInDark: checked=$checked skipped=${skipped.joinToString()}")
                assertTrue("only $checked layouts could be drawn", checked >= 60)
                assertTrue(
                    "${problems.size} faint in dark mode across $checked layouts: " +
                        problems.take(20).joinToString("; "),
                    problems.isEmpty()
                )
            }
        }
    }

    /** Empty labels get a sample, so a row drawn with no data can still be measured. */
    private fun fillBlanks(v: View) {
        if (v is TextView && v !is EditText && v.text.isNullOrBlank()) v.text = "Aa 123"
        if (v is ViewGroup) for (i in 0 until v.childCount) fillBlanks(v.getChildAt(i))
    }
}
