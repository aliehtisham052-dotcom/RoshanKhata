package com.innovation313.roshankhata.ui

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.innovation313.roshankhata.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/**
 * The add-entry form's signature across a rebuilt screen (17 Nov 2026, audit L024): the row tells the form when the
 * owner draws or removes it (so a copy waits in a file), and a rebuilt form puts it back as a change — Save files it,
 * and leaving asks first.
 */
@RunWith(AndroidJUnit4::class)
class EntryFormSignatureTest {

    private fun row(): Pair<AppCompatActivity, View> {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(R.style.Theme_RoshanKhata)
        val activity = controller.setup().get()
        return activity to LayoutInflater.from(activity).inflate(R.layout.item_signature_row, null)
    }

    private fun about() = SignatureSheet.About(party = "Test Customer", isGiven = true, amount = 500.0, time = 0L)

    @Test
    fun `a signature put back after a rebuild is a change the form files and guards`() {
        val (activity, view) = row()
        val sig = SignatureRow(activity, view, { about() })
        val bmp = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888)
        sig.restoreChanged(bmp)
        assertSame(bmp, sig.current)
        assertTrue(sig.changed)
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.ivSigRowThumb).visibility)
    }

    @Test
    fun `a removed signature put back stays removed and changed`() {
        val (activity, view) = row()
        val sig = SignatureRow(activity, view, { about() }, existing = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))
        sig.restoreChanged(null)
        assertNull(sig.current)
        assertTrue(sig.changed)
    }

    @Test
    fun `removing or resetting tells the form so its waiting copy goes`() {
        val (activity, view) = row()
        val sig = SignatureRow(activity, view, { about() })
        val told = mutableListOf<Bitmap?>()
        sig.onChanged = { told.add(it) }
        sig.restoreChanged(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))
        view.findViewById<View>(R.id.btnSigRowRemove).performClick()
        assertEquals(listOf<Bitmap?>(null), told)
        sig.reset()
        assertEquals(2, told.size)
        assertNull(told.last())
        assertFalse(sig.changed)
    }
}
