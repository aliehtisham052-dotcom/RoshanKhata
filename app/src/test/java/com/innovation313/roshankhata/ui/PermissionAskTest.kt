package com.innovation313.roshankhata.ui

import android.Manifest
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The one permission asker (16 Nov 2026): which permissions an Android asks about at all, and that a permission
 * already given is answered without a question. The question itself is Android's and is not drawn here.
 */
@RunWith(AndroidJUnit4::class)
class PermissionAskTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    @Config(sdk = [30])
    fun `before Android 12 and 13 the notification and Bluetooth permissions are not asked and count as given`() {
        assertFalse(PermissionAsk.asked(Manifest.permission.POST_NOTIFICATIONS))
        assertFalse(PermissionAsk.asked(Manifest.permission.BLUETOOTH_CONNECT))
        assertTrue(PermissionAsk.granted(context, Manifest.permission.POST_NOTIFICATIONS))
        assertTrue(PermissionAsk.granted(context, Manifest.permission.BLUETOOTH_CONNECT))
    }

    @Test
    @Config(sdk = [32])
    fun `Android 12 asks for Bluetooth but not yet for notifications`() {
        assertTrue(PermissionAsk.asked(Manifest.permission.BLUETOOTH_CONNECT))
        assertFalse(PermissionAsk.asked(Manifest.permission.POST_NOTIFICATIONS))
    }

    @Test
    @Config(sdk = [34])
    fun `Android 13 and later ask for all three`() {
        assertTrue(PermissionAsk.asked(Manifest.permission.POST_NOTIFICATIONS))
        assertTrue(PermissionAsk.asked(Manifest.permission.BLUETOOTH_CONNECT))
        assertTrue(PermissionAsk.asked(Manifest.permission.RECORD_AUDIO))
    }

    @Test
    @Config(sdk = [34])
    fun `granted follows what the phone says`() {
        shadowOf(context as android.app.Application).denyPermissions(Manifest.permission.RECORD_AUDIO)
        assertFalse(PermissionAsk.granted(context, Manifest.permission.RECORD_AUDIO))
        shadowOf(context as android.app.Application).grantPermissions(Manifest.permission.RECORD_AUDIO)
        assertTrue(PermissionAsk.granted(context, Manifest.permission.RECORD_AUDIO))
    }

    @Test
    @Config(sdk = [34])
    fun `a permission already given is answered at once with no question`() {
        shadowOf(context as android.app.Application).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val host = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val answers = mutableListOf<Boolean>()
        PermissionAsk.ask(host, Manifest.permission.RECORD_AUDIO) { answers += it }
        assertEquals(listOf(true), answers)
    }

    @Test
    @Config(sdk = [34])
    fun `a permission not given waits for Android's answer and is not answered before it`() {
        shadowOf(context as android.app.Application).denyPermissions(Manifest.permission.RECORD_AUDIO)
        val host = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val answers = mutableListOf<Boolean>()
        PermissionAsk.ask(host, Manifest.permission.RECORD_AUDIO) { answers += it }
        assertTrue("nothing is answered before Android answers", answers.isEmpty())
    }
}
