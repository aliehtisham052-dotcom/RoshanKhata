package com.innovation313.roshankhata

import android.app.Activity
import android.app.Dialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.ui.KeyboardFollow
import com.innovation313.roshankhata.ui.TextFit
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.math.abs

/**
 * Does the keyboard cover a text field in a pop-up? (18 Oct; 6 Nov 2026)
 *
 * Android's own pages do not say whether pop-up windows are drawn behind the
 * bars the way screens are from Android 15, so this asks the phone instead.
 * Each case builds a pop-up the way the app builds its forms — a Material
 * alert dialog, the same stretched to full height (the add-entry form), a
 * bottom sheet — with its text field low down, opens the keyboard on it,
 * and measures.
 *
 * Cases marked "as built" are the plain platform behaviour (with the app's
 * theme), recorded (Download/keyboard-proof/dialogs-apiNN.txt) and not
 * asserted. Cases marked "with KeyboardFollow" call it outright; "by itself"
 * cases only pass their form through TextFit.relax, as every form in the app
 * does, and must get the same. Asserted cases MUST:
 *  - keep the field in sight, above the keyboard and below the window's top;
 *  - for a sheet, sit on the keyboard: no band a keyboard's height under it.
 *    Until 6 Nov a sheet was lifted twice on Android 15 (KeyboardFollow and
 *    Material's own container), and the API 35 run measured the field 867 px
 *    above the keyboard — "visible", and the old test passed it;
 *  - for a field in the middle of a long form, bring it to the middle of what
 *    can be seen (the owner, 6 Nov: "bilkul mid mein").
 */
@RunWith(Parameterized::class)
class KeyboardDialogsOnDeviceTest(
    private val case: String,
    private val asserted: Boolean
) {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Before
    fun readyToUse() {
        context.getSharedPreferences("language", Context.MODE_PRIVATE)
            .edit().putBoolean("chosen", true).commit()
    }

    @Test
    fun fieldStaysAboveTheKeyboard() {
        val intent = Intent(context, ReportProblemActivity::class.java)
        ActivityScenario.launch<Activity>(intent).use { scenario ->
            var act: Activity? = null
            scenario.onActivity { act = it }
            val activity = act!!
            var built: Built? = null
            instrumentation.runOnMainSync { built = build(activity) }
            val (dialog, field, scroller) = built!!
            idle(800)
            instrumentation.runOnMainSync {
                field.requestFocus()
                (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
            }
            val up = waitFor(8000) { imeVisible(dialog) }
            assertTrue("$case: the on-screen keyboard never appeared", up)
            // The keyboard's slide-in, the sheet riding with it, and the scroll to the middle.
            idle(1500)

            var line = ""
            val problems = mutableListOf<String>()
            instrumentation.runOnMainSync {
                val decor = dialog.window!!.decorView
                val density = decor.resources.displayMetrics.density
                val insets = ViewCompat.getRootWindowInsets(decor)!!
                val covered = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                val keyboardTop = decor.height - covered
                val f = boxOf(field)
                if (f.bottom > keyboardTop) problems += "field under the keyboard"
                if (f.top < 0) problems += "field above the window's top"

                // A sheet sits on the keyboard (or on the screen's foot when the phone moved the window).
                val sheet = decor.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                var band = 0
                if (sheet != null) {
                    band = keyboardTop - boxOf(sheet).bottom
                    if (band > (24 * density).toInt()) problems += "a ${band}px band under the sheet (lifted twice)"
                }

                // The field in the middle of what its scroll shows, when the form goes on below it.
                var offMiddle = 0
                if (case.contains("long form")) {
                    val s = boxOf(scroller)
                    val seenTop = s.top + scroller.paddingTop
                    val seenBottom = minOf(s.bottom - scroller.paddingBottom, keyboardTop)
                    offMiddle = (f.top + f.bottom) / 2 - (seenTop + seenBottom) / 2
                    if (abs(offMiddle) > (48 * density).toInt()) problems += "field ${offMiddle}px off the middle"
                }

                line = "api${Build.VERSION.SDK_INT} | $case | floating=${dialog.window!!.isFloating} " +
                    "| keyboard covers ${covered}px of the window | field ${f.top}..${f.bottom}, " +
                    "keyboard top $keyboardTop | band under sheet $band | off middle $offMiddle | " +
                    if (problems.isEmpty()) "OK" else problems.joinToString("; ")
            }
            Log.i(TAG, line)
            record(line)
            screenshot("dialog-${case.lowercase().replace(Regex("[^a-z0-9]+"), "-")}.png")
            instrumentation.runOnMainSync { dialog.dismiss() }
            if (asserted) assertTrue("$case: $line", problems.isEmpty())
        }
    }

    private data class Built(val dialog: Dialog, val field: EditText, val scroller: View)

    /** The pop-up for this case, the text field in it and the scroll it sits in. */
    private fun build(activity: Activity): Built {
        val d = activity.resources.displayMetrics
        val tall = (d.heightPixels * 0.55f).toInt()
        val field = EditText(activity).apply { hint = "Last line"; isSingleLine = true }
        fun spacer() = View(activity).apply { minimumHeight = tall }
        fun column(vararg v: View) = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            v.forEach { addView(it) }
        }
        val withFix = case.endsWith("with KeyboardFollow")
        val byItself = case.contains("by itself")
        val scroll: ScrollView
        val dialog: Dialog = when {
            case.startsWith("Alert dialog") -> {
                scroll = ScrollView(activity).apply { addView(column(spacer(), field)) }
                if (byItself) TextFit.relax(scroll)
                MaterialAlertDialogBuilder(activity).setTitle("Test").setView(scroll)
                    .setPositiveButton("OK", null).show()
            }
            case.startsWith("Full-height dialog") -> {
                // The add-entry form: an alert dialog stretched to the whole
                // screen, adjustResize, a scrolling body and a pinned button.
                scroll = ScrollView(activity).apply { addView(column(spacer(), spacer(), field)) }
                val root = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
                    addView(Button(activity).apply { text = "SAVE" })
                }
                if (byItself) TextFit.relax(root)
                val dlg = MaterialAlertDialogBuilder(activity).setView(root).create()
                dlg.show()
                dlg.window?.apply {
                    setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                }
                dlg
            }
            else -> {
                // A sheet: a scrolling body over a pinned button, the invoice options' shape. The long
                // form goes on below the field, so the field can be brought to the middle.
                val body = if (case.contains("long form")) column(spacer(), field, spacer()) else column(spacer(), field)
                scroll = ScrollView(activity).apply { addView(body) }
                val root = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(Button(activity).apply { text = "APPLY" })
                }
                if (byItself || case.contains("long form")) TextFit.relax(root)
                val sheet = BottomSheetDialog(activity)
                sheet.setContentView(root)
                sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
                sheet.behavior.skipCollapsed = true
                sheet.show()
                sheet
            }
        }
        if (withFix) KeyboardFollow.dialog(dialog)
        return Built(dialog, field, scroll)
    }

    /** [v]'s box in its window, where the layout puts it (a sheet's slide is over by now). */
    private fun boxOf(v: View): android.graphics.Rect {
        val at = IntArray(2)
        v.getLocationInWindow(at)
        return android.graphics.Rect(at[0], at[1], at[0] + v.width, at[1] + v.height)
    }

    private fun imeVisible(dialog: Dialog): Boolean {
        var v = false
        instrumentation.runOnMainSync {
            val insets = dialog.window?.decorView?.let { ViewCompat.getRootWindowInsets(it) }
            v = insets?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        return v
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

    private fun record(line: String) {
        RESULTS += line
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "dialogs-api${Build.VERSION.SDK_INT}-${RESULTS.size}.txt")
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/keyboard-proof")
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        context.contentResolver.openOutputStream(uri)?.use { it.write((line + "\n").toByteArray()) }
    }

    private fun screenshot(name: String) {
        val shot: Bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "api${Build.VERSION.SDK_INT}-$name")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/keyboard-proof")
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        context.contentResolver.openOutputStream(uri)?.use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    companion object {
        private const val TAG = "KeyboardTest"
        private val RESULTS = mutableListOf<String>()

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = listOf(
            arrayOf("Alert dialog, as built", false),
            arrayOf("Alert dialog, with KeyboardFollow", true),
            arrayOf("Alert dialog, by itself", true),
            arrayOf("Full-height dialog, as built", false),
            arrayOf("Full-height dialog, with KeyboardFollow", true),
            arrayOf("Full-height dialog, by itself", true),
            arrayOf("Bottom sheet, as built", false),
            arrayOf("Bottom sheet, with KeyboardFollow", true),
            arrayOf("Bottom sheet, by itself", true),
            arrayOf("Bottom sheet long form, by itself", true),
        )
    }
}
