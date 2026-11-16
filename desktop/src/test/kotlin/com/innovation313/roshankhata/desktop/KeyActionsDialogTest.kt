package com.innovation313.roshankhata.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test

/**
 * Register BC-075 (lesson 54): the app's dialogs are drawn inside the window (Marhala 2), so a key typed in one passes the
 * window's own shortcuts first — F2 typed in an entry form opened a second form. [KeyActions] does nothing while
 * [OpenDialogs] counts one open (or while one of Book's questions is: [KeyActions.busy]), and does its work when none is.
 *
 * The decision is driven with Compose's own [Key] and [KeyEventType] values; the window's real path (a Compose [KeyEvent]
 * read for its type and key) is driven too where this Compose lets a test make one from an AWT key event — else that one
 * test is skipped and says so, the decision's tests still run.
 */
class KeyActionsDialogTest {

    private var gaveRan = 0
    private var gotRan = 0
    private var newPartyRan = 0
    private var khataRan = 0
    private var pageAsked = 0

    private fun keys(): KeyActions {
        val k = KeyActions()
        k.gave = { gaveRan++ }
        k.got = { gotRan++ }
        k.newParty = { newPartyRan++ }
        k.khata = { khataRan++ }
        return k
    }

    @Before
    fun noDialog() {
        while (OpenDialogs.count > 0) OpenDialogs.closed()
    }

    @After
    fun closeAll() {
        while (OpenDialogs.count > 0) OpenDialogs.closed()
    }

    @Test
    fun `no window shortcut fires while one of the app's dialogs is open`() {
        val keys = keys()
        OpenDialogs.opened()
        assertFalse(keys.handle(KeyEventType.KeyDown, Key.F2, false) { pageAsked++; false })
        assertFalse(keys.handle(KeyEventType.KeyDown, Key.F3, false))
        assertFalse(keys.handle(KeyEventType.KeyDown, Key.F1, false))
        assertFalse(keys.handle(KeyEventType.KeyDown, Key.N, true))
        assertEquals(0, gaveRan + gotRan + khataRan + newPartyRan)
        // Not the counter's own keys either: the page is not even asked.
        assertEquals(0, pageAsked)
        // Two dialogs, one closed: one is still open.
        OpenDialogs.opened()
        OpenDialogs.closed()
        assertFalse(keys.handle(KeyEventType.KeyDown, Key.F2, false))
        assertEquals(0, gaveRan)
    }

    @Test
    fun `the shortcuts fire again once every dialog is closed`() {
        val keys = keys()
        OpenDialogs.opened()
        assertFalse(keys.handle(KeyEventType.KeyDown, Key.F2, false))
        OpenDialogs.closed()
        assertEquals(0, OpenDialogs.count)
        assertTrue(keys.handle(KeyEventType.KeyDown, Key.F2, false))
        assertTrue(keys.handle(KeyEventType.KeyDown, Key.F3, false))
        assertTrue(keys.handle(KeyEventType.KeyDown, Key.F1, false))
        assertTrue(keys.handle(KeyEventType.KeyDown, Key.N, true))
        assertEquals(listOf(1, 1, 1, 1), listOf(gaveRan, gotRan, khataRan, newPartyRan))
        // A key let go is no shortcut; N without Ctrl is typing.
        assertFalse(keys.handle(KeyEventType.KeyUp, Key.F2, false))
        assertFalse(keys.handle(KeyEventType.KeyDown, Key.N, false))
        assertEquals(1, gaveRan)
        assertEquals(1, newPartyRan)
    }

    @Test
    fun `one of the window's own questions holds the shortcuts as a dialog does`() {
        val keys = keys()
        var asking = true
        keys.busy = { asking }
        assertFalse(keys.handle(KeyEventType.KeyDown, Key.F2, false))
        asking = false
        assertTrue(keys.handle(KeyEventType.KeyDown, Key.F2, false))
        assertEquals(1, gaveRan)
    }

    @Test
    fun `a real key event through the window's own path does nothing while a dialog is open`() {
        val source = object : java.awt.Component() {}
        val f2 = runCatching {
            KeyEvent(
                java.awt.event.KeyEvent(
                    source, java.awt.event.KeyEvent.KEY_PRESSED, 0L, 0,
                    java.awt.event.KeyEvent.VK_F2, java.awt.event.KeyEvent.CHAR_UNDEFINED
                )
            ).takeIf { it.type == KeyEventType.KeyDown && it.key == Key.F2 }
        }.getOrNull()
        Assume.assumeTrue("this Compose does not read a key event made from an AWT one in a test", f2 != null)
        val event = f2!!
        val keys = keys()
        OpenDialogs.opened()
        assertFalse(keys.handle(event))
        assertEquals(0, gaveRan)
        OpenDialogs.closed()
        assertTrue(keys.handle(event))
        assertEquals(1, gaveRan)
    }
}
