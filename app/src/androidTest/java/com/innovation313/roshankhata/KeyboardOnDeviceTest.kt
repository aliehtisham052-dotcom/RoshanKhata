package com.innovation313.roshankhata

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Every text field on every form stays in sight while it is typed in (18 Oct).
 *
 * The owner filmed the Poster's Words step: tapping "Last line" brought the
 * keyboard up OVER the line. Drawn behind the system bars (ScreenInsets),
 * Android no longer shrinks a screen for the keyboard, so every form was
 * affected, not only that one.
 *
 * For each screen this taps every visible text field in turn, the way the
 * owner would (scrolled to where it can be seen, then tapped), waits for the
 * real on-screen keyboard, and measures: the field's bottom edge must be
 * above the keyboard's top edge. Every failing field is listed together, so
 * one run names all of them. A picture of each screen with the keyboard up
 * on its lowest field goes to Download/keyboard-proof/ for a person to look
 * at (CLAUDE.md lesson 16: look at the pictures, not only the numbers).
 *
 * Needs the emulator to show its soft keyboard although a hardware keyboard
 * is attached: scripts/keyboard-check.sh sets show_ime_with_hard_keyboard.
 * A keyboard that never appears fails the test with that reason, never
 * passes it silently.
 */
@RunWith(Parameterized::class)
class KeyboardOnDeviceTest(
    private val screen: String,
    private val intentFor: (Context, EveryScreenOpensTest.Seed) -> Intent,
    /** Puts the screen in the state that shows its fields (e.g. a step). */
    private val prepare: (Activity) -> Unit
) {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Before
    fun readyToUse() {
        context.getSharedPreferences("language", Context.MODE_PRIVATE)
            .edit().putBoolean("chosen", true).commit()
        val ui = instrumentation.uiAutomation
        ui.grantRuntimePermission(context.packageName, Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= 33) {
            ui.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @Test
    fun everyFieldStaysAboveTheKeyboard() {
        val seed = EveryScreenOpensTest.seed(context)
        ActivityScenario.launch<Activity>(intentFor(context, seed)).use { scenario ->
            assumeTrue("$screen closed itself", scenario.state != Lifecycle.State.DESTROYED)
            scenario.onActivity { prepare(it) }
            idle(600)

            var activity: Activity? = null
            scenario.onActivity { activity = it }
            val act = activity!!

            val count = onMain { fields(act).size }
            assertTrue("$screen: no text field found on screen — is this still a form?", count > 0)

            val problems = mutableListOf<String>()
            for (i in 0 until count) {
                hideKeyboard(act)
                // The list again each time: a field may appear or move once
                // the one before it was typed in.
                val field = onMain { fields(act).getOrNull(i) } ?: continue
                val label = onMain { describe(field) }

                onMain {
                    // As the owner does: scroll it into sight, then tap it.
                    field.requestRectangleOnScreen(Rect(0, 0, field.width, field.height), true)
                    field.requestFocus()
                    (act.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                        .showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
                }
                val up = waitFor(8000) { keyboardHeight(act) > 0 }
                assertTrue(
                    "$screen: the on-screen keyboard never appeared for $label " +
                        "(emulator setting show_ime_with_hard_keyboard?)", up
                )
                // The keyboard's slide-in, then the layout pass and the scroll
                // KeyboardFollow posts after it.
                idle(1200)

                val result = onMain { measure(act, field) }
                Log.i(TAG, "$screen | $label | $result")
                if (!result.ok) problems += "$label ${result}"
                if (i == count - 1) screenshot("${slug(screen)}.png")
            }
            hideKeyboard(act)
            assertTrue(
                "$screen: ${problems.size} field(s) hidden by the keyboard: " +
                    problems.joinToString("; "),
                problems.isEmpty()
            )
        }
    }

    // ------------------------------------------------------------------ helpers

    data class Measure(val fieldBottom: Int, val keyboardTop: Int, val focused: Boolean) {
        val ok get() = focused && fieldBottom <= keyboardTop
        override fun toString() =
            "field bottom $fieldBottom, keyboard top $keyboardTop" + if (focused) "" else " (lost focus)"
    }

    /** Field bottom and keyboard top, both in the activity window's pixels. */
    private fun measure(act: Activity, field: View): Measure {
        val decor = act.window.decorView
        val at = IntArray(2)
        field.getLocationInWindow(at)
        return Measure(
            fieldBottom = at[1] + field.height,
            keyboardTop = decor.height - imeHeightHere(act),
            focused = field.hasFocus()
        )
    }

    /** From the test thread. */
    private fun keyboardHeight(act: Activity): Int = onMain { imeHeightHere(act) }

    /** Already on the main thread. */
    private fun imeHeightHere(act: Activity): Int {
        val insets = ViewCompat.getRootWindowInsets(act.window.decorView)
        return if (insets != null && insets.isVisible(WindowInsetsCompat.Type.ime()))
            insets.getInsets(WindowInsetsCompat.Type.ime()).bottom else 0
    }

    private fun hideKeyboard(act: Activity) {
        onMain {
            val focus = act.currentFocus
            (act.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(act.window.decorView.windowToken, 0)
            focus?.clearFocus()
        }
        waitFor(4000) { keyboardHeight(act) == 0 }
        idle(400)
    }

    /** Every enabled, shown text field, top of the screen first. */
    private fun fields(act: Activity): List<EditText> {
        val out = mutableListOf<EditText>()
        collect(act.findViewById(android.R.id.content), out)
        val at = IntArray(2)
        return out.sortedBy { it.getLocationInWindow(at); at[1] }
    }

    private fun collect(v: View, out: MutableList<EditText>) {
        if (!v.isShown) return
        if (v is EditText && v.isEnabled && v.isFocusable && v.width > 0 && v.height > 0) out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) collect(v.getChildAt(i), out)
    }

    private fun describe(field: View): String {
        val id = if (field.id != View.NO_ID)
            runCatching { field.resources.getResourceEntryName(field.id) }.getOrNull() else null
        return id ?: (field as? EditText)?.hint?.toString()?.take(24) ?: "field"
    }

    private fun <T> onMain(block: () -> T): T {
        var out: Result<T>? = null
        instrumentation.runOnMainSync { out = runCatching(block) }
        return out!!.getOrThrow()
    }

    private fun idle(ms: Long) {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(ms)
        instrumentation.waitForIdleSync()
    }

    private fun waitFor(ms: Long, cond: () -> Boolean): Boolean {
        val end = SystemClock.uptimeMillis() + ms
        while (SystemClock.uptimeMillis() < end) {
            if (cond()) return true
            SystemClock.sleep(150)
        }
        return cond()
    }

    private fun screenshot(name: String) {
        val shot: Bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "api${Build.VERSION.SDK_INT}-$name")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/keyboard-proof")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        resolver.openOutputStream(uri)?.use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun slug(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    companion object {
        private const val TAG = "KeyboardTest"

        private inline fun <reified T : Activity> open(
            name: String,
            noinline prepare: (Activity) -> Unit = {},
            noinline extras: Intent.(EveryScreenOpensTest.Seed) -> Unit = {}
        ): Array<Any> = arrayOf(
            name,
            { c: Context, s: EveryScreenOpensTest.Seed -> Intent(c, T::class.java).apply { extras(s) } },
            prepare
        )

        /**
         * Every screen that has text fields of its own (forms in pop-up
         * dialogs are KeyboardDialogsOnDeviceTest's).
         */
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun screens(): List<Array<Any>> = listOf(
            open<PosterActivity>("Poster words", prepare = { it.findViewById<View>(R.id.step2).performClick() }),
            open<KhataActivity>("Khata list"),
            open<PartyDetailActivity>("Customer khata") {
                putExtra(PartyDetailActivity.EXTRA_PARTY_ID, it.customerId)
            },
            open<ProductsActivity>("Products"),
            open<RateListActivity>("Rate list"),
            open<InvoiceEditorActivity>("New invoice"),
            open<ZakatActivity>("Zakat"),
            open<ImportContactsActivity>("Import contacts"),
            open<BusinessSettingsActivity>("Business settings"),
            open<BusinessCardActivity>("Business card"),
            open<ReportProblemActivity>("Report a problem"),
            open<ShopTypeActivity>("Shop type"),
            open<ShopNameActivity>("Shop name"),
        )
    }
}
