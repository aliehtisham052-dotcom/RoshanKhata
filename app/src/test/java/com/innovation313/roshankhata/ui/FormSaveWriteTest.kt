package com.innovation313.roshankhata.ui

import android.content.DialogInterface
import android.os.Bundle
import android.os.Looper
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.innovation313.roshankhata.R
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog

/**
 * One pattern for every money form's Save (17 Nov 2026, audit L003): the form closes only once its write is in the
 * book; a write that fails keeps the form open with what was typed; while it is written Save takes no second tap,
 * Back waits, and a turned screen does not keep the form's text (its row is about to land).
 */
@RunWith(AndroidJUnit4::class)
class FormSaveWriteTest {

    private class Form(val activity: AppCompatActivity, val dialog: AlertDialog, val box: EditText)

    private fun form(): Form {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(R.style.Theme_RoshanKhata)
        val activity = controller.setup().get()
        val box = EditText(activity)
        val dialog = AlertDialog.Builder(activity)
            .setView(box)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .show()
        return Form(activity, dialog, box)
    }

    private fun tapSave(dialog: AlertDialog) = dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()

    /** The write runs on AppScope's threads; its answer comes back to the main thread. */
    private fun waitFor(what: () -> Boolean) {
        repeat(500) {
            shadowOf(Looper.getMainLooper()).idle()
            if (what()) return
            Thread.sleep(10)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `a refused save keeps the form and writes nothing`() {
        val f = form()
        var checks = 0
        FormGuard.onSaveWrite(f.dialog, onFailed = {}) { _ -> checks++ }
        tapSave(f.dialog)
        assertEquals(1, checks)
        assertTrue(f.dialog.isShowing)
        assertFalse(FormGuard.isWriting(f.dialog))
    }

    @Test
    fun `the form closes only once the write is in, and takes no second tap meanwhile`() {
        val f = form()
        f.box.setText("500")
        val gate = CompletableDeferred<Unit>()
        var checks = 0
        var written = 0
        var told = 0
        FormGuard.onSaveWrite(f.dialog, onFailed = {}) { commit ->
            checks++
            commit(FormGuard.Pending({ gate.await(); written++ }) { told++ })
        }
        tapSave(f.dialog)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("the form waits for its write", f.dialog.isShowing)
        assertTrue(FormGuard.isWriting(f.dialog))
        tapSave(f.dialog)
        assertEquals("a second tap while writing does nothing", 1, checks)

        gate.complete(Unit)
        waitFor { !f.dialog.isShowing }
        assertFalse("written: the form closes", f.dialog.isShowing)
        assertEquals(1, written)
        assertEquals("onWritten runs once, before the form closes", 1, told)
        assertFalse(FormGuard.isWriting(f.dialog))
    }

    @Test
    fun `a write that fails says so and keeps the form open with what was typed`() {
        val f = form()
        f.box.setText("1,250 for Test Supplier")
        var failure: Throwable? = null
        var told = 0
        var checks = 0
        FormGuard.onSaveWrite(f.dialog, onFailed = { failure = it }) { commit ->
            checks++
            commit(FormGuard.Pending({ throw IllegalStateException("disk full") }) { told++ })
        }
        tapSave(f.dialog)
        waitFor { failure != null }
        assertNotNull("the failure is said", failure)
        assertEquals("disk full", failure!!.message)
        assertTrue("the form stays", f.dialog.isShowing)
        assertEquals("what was typed stays", "1,250 for Test Supplier", f.box.text.toString())
        assertEquals("nothing was written: no 'saved'", 0, told)
        assertFalse(FormGuard.isWriting(f.dialog))
        tapSave(f.dialog)
        assertEquals("Save can be tapped again", 2, checks)
    }

    @Test
    fun `a commit after the form was closed does nothing`() {
        val f = form()
        var written = 0
        var later: ((FormGuard.Pending) -> Unit)? = null
        FormGuard.onSaveWrite(f.dialog, onFailed = {}) { commit -> later = commit }
        tapSave(f.dialog)
        f.dialog.dismiss()
        later!!(FormGuard.Pending({ written++ }))
        waitFor { false }
        assertEquals(0, written)
    }

    @Test
    fun `Back waits while the form is written`() {
        val f = form()
        f.box.setText("75")
        val gate = CompletableDeferred<Unit>()
        FormGuard.guard(f.activity, f.dialog, typed = { true })
        FormGuard.onSaveWrite(f.dialog, onFailed = {}) { commit -> commit(FormGuard.Pending({ gate.await() })) }
        tapSave(f.dialog)
        shadowOf(Looper.getMainLooper()).idle()
        f.dialog.onBackPressedDispatcher.onBackPressed()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(f.dialog.isShowing)
        assertSame("no 'discard?' question over a form being written", f.dialog, ShadowDialog.getLatestDialog())
        gate.complete(Unit)
        waitFor { !f.dialog.isShowing }
        assertFalse(f.dialog.isShowing)
    }

    @Test
    fun `a screen turned while the form is written does not keep its text`() {
        val f = form()
        f.box.setText("300")
        val draft = FormDraft("t")
        draft.open(0) { listOf(f.box) }
        val gate = CompletableDeferred<Unit>()
        FormGuard.onSaveWrite(f.dialog, draft, onFailed = {}) { commit -> commit(FormGuard.Pending({ gate.await() })) }
        tapSave(f.dialog)
        shadowOf(Looper.getMainLooper()).idle()
        val during = Bundle().also { draft.save(it) }
        assertFalse("not kept while its row is landing", during.containsKey("t_which"))
        gate.complete(Unit)
        waitFor { !f.dialog.isShowing }
        assertFalse(draft.writing)
    }

    @Test
    fun `a form that failed is kept again for a turned screen`() {
        val f = form()
        f.box.setText("300")
        val draft = FormDraft("t")
        draft.open(0) { listOf(f.box) }
        var failed = false
        FormGuard.onSaveWrite(f.dialog, draft, onFailed = { failed = true }) { commit ->
            commit(FormGuard.Pending({ error("no") }))
        }
        tapSave(f.dialog)
        waitFor { failed }
        val after = Bundle().also { draft.save(it) }
        assertTrue(after.containsKey("t_which"))
        assertEquals(listOf("300"), after.getStringArrayList("t_texts"))
    }
}
