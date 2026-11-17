package com.innovation313.roshankhata.ui

import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.innovation313.roshankhata.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog

/**
 * "Set the owner's PIN" is asked once however fast Save is tapped (17 Nov 2026, audit L025): a second call while the
 * first is looking or asking does nothing, and the screen may ask again once the question was closed.
 */
@RunWith(AndroidJUnit4::class)
class OwnerPinOnceTest {

    private fun settle(what: () -> Boolean) {
        repeat(500) {
            shadowOf(Looper.getMainLooper()).idle()
            if (what()) return
            Thread.sleep(10)
        }
    }

    @Test
    fun `a double tap asks for the owner's PIN once`() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(R.style.Theme_RoshanKhata)
        val activity = controller.setup().get()
        var thens = 0
        val before = ShadowDialog.getShownDialogs().size

        AccessUi.requireOwnerPin(activity) { thens++ }
        AccessUi.requireOwnerPin(activity) { thens++ }
        assertTrue(AccessUi.isAskingOwnerPin(activity))
        settle { ShadowDialog.getShownDialogs().size > before }
        settle { false }

        assertEquals("one question, not two", before + 1, ShadowDialog.getShownDialogs().size)
        assertEquals("nothing goes ahead without the PIN", 0, thens)

        ShadowDialog.getLatestDialog().dismiss()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse("closed: the screen may ask again", AccessUi.isAskingOwnerPin(activity))
        AccessUi.requireOwnerPin(activity) { thens++ }
        settle { ShadowDialog.getShownDialogs().size > before + 1 }
        assertEquals(before + 2, ShadowDialog.getShownDialogs().size)
    }
}
