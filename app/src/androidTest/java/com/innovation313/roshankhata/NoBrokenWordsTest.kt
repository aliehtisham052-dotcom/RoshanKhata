package com.innovation313.roshankhata

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.innovation313.roshankhata.data.TextSize
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * No button label is broken in the middle of a word (13 Oct).
 *
 * The owner, at a large phone font, found "Change" drawn as "Ch / ang / e" on
 * the Profile and "Invoice" as "INVOIC / E" on a receipt. A label may wrap
 * between words or shorten with an ellipsis; it may never split a word. This
 * opens the screens that had it, at the largest text size, in English and in
 * Urdu, and checks every shown button's line breaks.
 */
@RunWith(AndroidJUnit4::class)
class NoBrokenWordsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun ready() {
        context.getSharedPreferences("language", Context.MODE_PRIVATE)
            .edit().putBoolean("chosen", true).commit()
        TextSize.setLevel(context, TextSize.LARGEST)
    }

    @After
    fun reset() {
        TextSize.setLevel(context, TextSize.NORMAL)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        }
    }

    @Test
    fun englishLargest() = checkAll("en")

    @Test
    fun urduLargest() = checkAll("ur")

    private fun checkAll(language: String) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
        }
        val broken = mutableListOf<String>()
        broken += check(BusinessSettingsActivity::class.java) { activity ->
            // Open every folded section so its buttons are laid out too.
            listOf(R.id.bodyIdentity, R.id.bodyPaid, R.id.bodySeals, R.id.bodyDocs).forEach {
                activity.findViewById<View>(it)?.visibility = View.VISIBLE
            }
        }
        broken += check(CalculatorActivity::class.java)
        broken += check(BillsActivity::class.java)
        broken += check(ProductsActivity::class.java)
        assertTrue("[$language] words broken mid-word: $broken", broken.isEmpty())
    }

    private fun <A : Activity> check(type: Class<A>, prepare: (A) -> Unit = {}): List<String> {
        val found = mutableListOf<String>()
        ActivityScenario.launch(type).use { scenario ->
            scenario.onActivity { prepare(it) }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                walk(activity.findViewById(android.R.id.content)) { b ->
                    val layout = b.layout ?: return@walk
                    val text = layout.text
                    for (line in 0 until layout.lineCount - 1) {
                        val end = layout.getLineEnd(line)
                        if (end <= 0 || end >= text.length) continue
                        val before = text[end - 1]
                        val after = text[end]
                        if (!before.isWhitespace() && !after.isWhitespace() &&
                            before.isLetterOrDigit() && after.isLetterOrDigit()
                        ) {
                            val name = runCatching { b.resources.getResourceEntryName(b.id) }.getOrDefault("?")
                            found += "${type.simpleName}/$name \"$text\""
                            break
                        }
                    }
                }
            }
        }
        return found
    }

    private fun walk(v: View?, each: (Button) -> Unit) {
        if (v == null || v.visibility != View.VISIBLE) return
        if (v is Button && v.text.isNotEmpty()) each(v)
        if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), each)
    }
}
