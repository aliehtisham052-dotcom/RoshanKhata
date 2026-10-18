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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Does the keyboard cover a text field in a pop-up? (18 Oct)
 *
 * Android's own pages do not say whether pop-up windows are drawn behind the
 * bars the way screens are from Android 15, so this asks the phone instead.
 * Each case builds a pop-up the way the app builds its forms — a Material
 * alert dialog, the same stretched to full height (the add-entry form), a
 * bottom sheet — with its text field low down, opens the keyboard on it,
 * and measures.
 *
 * Cases marked "as built" are the plain platform behaviour, recorded for the
 * record (Download/keyboard-proof/dialogs-apiNN.txt) and not asserted: they
 * are what decides where KeyboardFollow is needed. Cases marked "with
 * KeyboardFollow" are the app's fix and MUST keep the field visible.
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
            var dialog: Dialog? = null
            var field: EditText? = null
            instrumentation.runOnMainSync {
                val built = build(activity)
                dialog = built.first; field = built.second
            }
            idle(800)
            instrumentation.runOnMainSync {
                field!!.requestFocus()
                (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
            }
            val up = waitFor(8000) { imeVisible(dialog!!) }
            assertTrue("$case: the on-screen keyboard never appeared", up)
            idle(1200)

            var line = ""
            var ok = false
            instrumentation.runOnMainSync {
                val decor = dialog!!.window!!.decorView
                val insets = ViewCompat.getRootWindowInsets(decor)!!
                val covered = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                val at = IntArray(2)
                field!!.getLocationInWindow(at)
                val fieldBottom = at[1] + field!!.height
                val keyboardTop = decor.height - covered
                ok = fieldBottom <= keyboardTop
                line = "api${Build.VERSION.SDK_INT} | $case | floating=${dialog!!.window!!.isFloating} " +
                    "| keyboard covers ${covered}px of the window | field bottom $fieldBottom, " +
                    "keyboard top $keyboardTop | ${if (ok) "VISIBLE" else "HIDDEN"}"
            }
            Log.i(TAG, line)
            record(line)
            screenshot("dialog-${case.lowercase().replace(Regex("[^a-z0-9]+"), "-")}.png")
            instrumentation.runOnMainSync { dialog!!.dismiss() }
            if (asserted) assertTrue("$case: field hidden by the keyboard — $line", ok)
        }
    }

    /** The pop-up for this case, and the low text field in it. */
    private fun build(activity: Activity): Pair<Dialog, EditText> {
        val d = activity.resources.displayMetrics
        val tall = (d.heightPixels * 0.55f).toInt()
        val field = EditText(activity).apply { hint = "Last line"; isSingleLine = true }
        fun spacer() = View(activity).apply { minimumHeight = tall }
        fun column(vararg v: View) = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            v.forEach { addView(it) }
        }
        val withFix = case.endsWith("with KeyboardFollow")
        val dialog: Dialog = when {
            case.startsWith("Alert dialog") -> {
                val body = ScrollView(activity).apply { addView(column(spacer(), field)) }
                MaterialAlertDialogBuilder(activity).setTitle("Test").setView(body)
                    .setPositiveButton("OK", null).show()
            }
            case.startsWith("Full-height dialog") -> {
                // The add-entry form: an alert dialog stretched to the whole
                // screen, adjustResize, a scrolling body and a pinned button.
                val scroll = ScrollView(activity).apply { addView(column(spacer(), spacer(), field)) }
                val root = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
                    addView(Button(activity).apply { text = "SAVE" })
                }
                val dlg = MaterialAlertDialogBuilder(activity).setView(root).create()
                dlg.show()
                dlg.window?.apply {
                    setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                }
                dlg
            }
            else -> {
                val sheet = BottomSheetDialog(activity)
                sheet.setContentView(ScrollView(activity).apply { addView(column(spacer(), field)) })
                sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
                sheet.behavior.skipCollapsed = true
                sheet.show()
                sheet
            }
        }
        if (withFix) KeyboardFollow.dialog(dialog)
        return dialog to field
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
            arrayOf("Full-height dialog, as built", false),
            arrayOf("Full-height dialog, with KeyboardFollow", true),
            arrayOf("Bottom sheet, as built", false),
            arrayOf("Bottom sheet, with KeyboardFollow", true),
        )
    }
}
