package com.innovation313.roshankhata

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.innovation313.roshankhata.data.EntrySignature
import com.innovation313.roshankhata.data.ThemeMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.sin

/**
 * The redesigned signature, walked through as the owner would (18 Oct):
 * the receipt's signature screen (empty, then signed), the receipt with the
 * signature and its time, the new Edit popup with its signature row, and the
 * new-entry form's signature row and screen. Light, then dark.
 *
 * It checks that a saved signature really is filed under the entry, and
 * puts a picture of every step in Download/signature-proof/ for a person to
 * look at (CLAUDE.md lesson 16).
 */
class SignatureProofTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Before
    fun readyToUse() {
        context.getSharedPreferences("language", Context.MODE_PRIVATE)
            .edit().putBoolean("chosen", true).commit()
    }

    @After
    fun light() {
        instrumentation.runOnMainSync { ThemeMode.set(context, ThemeMode.LIGHT) }
    }

    @Test
    fun light_walkthrough() = walkthrough("light")

    @Test
    fun dark_walkthrough() {
        instrumentation.runOnMainSync {
            ThemeMode.set(context, ThemeMode.DARK)
            AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.getEmptyLocaleList())
        }
        walkthrough("dark")
    }

    private fun walkthrough(theme: String) {
        val seed = EveryScreenOpensTest.seed(context)
        // Start each run unsigned, so the empty screen is what is pictured.
        EntrySignature.delete(context, seed.entryId)

        // ---- The receipt: sign, save, see it there ----
        val receipt = Intent(context, EntryDetailActivity::class.java)
            .putExtra(EntryDetailActivity.EXTRA_ENTRY_ID, seed.entryId)
            .putExtra(EntryDetailActivity.EXTRA_PARTY_NAME, "Test Kisan")
        ActivityScenario.launch<Activity>(receipt).use { scenario ->
            idle(800)
            scenario.onActivity { it.findViewById<View>(R.id.btnSignature).performClick() }
            idle(900)
            shot("$theme-1-signature-empty")

            val sheet = topRoot()
            val save = onMain { sheet.findViewById<View>(R.id.btnSigSave) }
            assertTrue("Save must look off before signing", onMain { save.alpha } < 0.6f)
            sign(onMain { sheet.findViewById<View>(R.id.sigPad) })
            idle(500)
            assertEquals("Save must look on once signed", 1f, onMain { save.alpha }, 0.01f)
            shot("$theme-2-signature-signed")

            onMain { save.performClick() }
            idle(1500)
            assertNotNull("the signature was not filed under the entry", EntrySignature.load(context, seed.entryId))
            scenario.onActivity { act ->
                val block = act.findViewById<View>(R.id.blockSignature)
                (block.parent as? View)?.let { p -> scrollTo(p, block) }
                val at = act.findViewById<TextView>(R.id.tvSignedAt).text.toString()
                assertTrue("no signed time on the receipt", at.isNotBlank())
            }
            idle(500)
            shot("$theme-3-receipt-signed")

            // ---- Edit: the redesigned popup ----
            scenario.onActivity { it.findViewById<View>(R.id.btnEdit).performClick() }
            idle(1200)
            shot("$theme-4-edit-top")
            val edit = topRoot()
            val status = onMain { edit.findViewById<TextView>(R.id.tvSigRowStatus).text.toString() }
            assertEquals("Edit should show the entry as signed", context.getString(R.string.sig_done), status)
            // The form's own scroll: the one around the signature row (an
            // alert dialog has a hidden message scroll of its own, found first).
            onMain { (scrollAround(edit.findViewById(R.id.sigRow)) as? ScrollView)?.fullScroll(View.FOCUS_DOWN) }
            idle(600)
            shot("$theme-5-edit-bottom")
        }

        // ---- A new entry: the signature row on the details step ----
        val party = Intent(context, PartyDetailActivity::class.java)
            .putExtra(PartyDetailActivity.EXTRA_PARTY_ID, seed.customerId)
        ActivityScenario.launch<Activity>(party).use { scenario ->
            idle(900)
            scenario.onActivity { it.findViewById<View>(R.id.btnGave).performClick() }
            idle(1200)
            val form = topRoot()
            onMain {
                form.findViewById<EditText>(R.id.etAmount).setText("1500")
                form.findViewById<View>(R.id.btnMoreDetails).performClick()
            }
            idle(900)
            onMain {
                val row = form.findViewById<View>(R.id.sigRow)
                scrollAround(row)?.let { scrollTo(it, row) }
            }
            idle(600)
            shot("$theme-6-new-entry-row")
            onMain { form.findViewById<View>(R.id.sigRow).performClick() }
            idle(1000)
            shot("$theme-7-new-entry-sheet")
            val sheet2 = topRoot()
            sign(onMain { sheet2.findViewById<View>(R.id.sigPad) })
            onMain { sheet2.findViewById<View>(R.id.btnSigSave).performClick() }
            idle(800)
            onMain {
                val row = form.findViewById<View>(R.id.sigRow)
                scrollAround(row)?.let { scrollTo(it, row) }
            }
            idle(500)
            shot("$theme-8-new-entry-row-signed")
            val signed = onMain { form.findViewById<TextView>(R.id.tvSigRowStatus).text.toString() }
            assertEquals(context.getString(R.string.sig_done), signed)
        }
    }

    // ------------------------------------------------------------------ helpers

    /** A short wavy signature, drawn with real touch events. */
    private fun sign(pad: View) {
        onMain {
            val w = pad.width.toFloat(); val h = pad.height.toFloat()
            val t0 = SystemClock.uptimeMillis()
            fun ev(action: Int, x: Float, y: Float, dt: Long) =
                MotionEvent.obtain(t0, t0 + dt, action, x, y, 0).also { pad.dispatchTouchEvent(it); it.recycle() }
            val y0 = h * 0.6f
            ev(MotionEvent.ACTION_DOWN, w * 0.15f, y0, 0)
            for (i in 1..40) {
                val x = w * (0.15f + 0.65f * i / 40f)
                val y = y0 - (h * 0.18f) * sin(i / 3.0).toFloat()
                ev(MotionEvent.ACTION_MOVE, x, y, i * 10L)
            }
            ev(MotionEvent.ACTION_UP, w * 0.8f, y0, 420)
        }
    }

    /** The newest window's root view (a dialog over the activity), as Espresso finds it. */
    @Suppress("UNCHECKED_CAST")
    private fun topRoot(): View = onMain {
        val cls = Class.forName("android.view.WindowManagerGlobal")
        val global = cls.getMethod("getInstance").invoke(null)
        val field = cls.getDeclaredField("mViews").apply { isAccessible = true }
        (field.get(global) as List<View>).last { it.isShown }
    }

    private fun scrollAround(v: View): View? {
        var p = v.parent
        while (p is View) {
            if (p is ScrollView || p is androidx.core.widget.NestedScrollView) return p
            p = p.parent
        }
        return null
    }

    private fun findScroll(v: View): ViewGroup? {
        if (v is ScrollView || v is androidx.core.widget.NestedScrollView) return v as ViewGroup
        if (v is ViewGroup) for (i in 0 until v.childCount) findScroll(v.getChildAt(i))?.let { return it }
        return null
    }

    private fun scrollTo(scroller: View, target: View) {
        val r = android.graphics.Rect(0, 0, target.width, target.height)
        target.requestRectangleOnScreen(r, true)
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

    private fun shot(name: String) {
        val bmp: Bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "api${Build.VERSION.SDK_INT}-$name.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/signature-proof")
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        context.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
