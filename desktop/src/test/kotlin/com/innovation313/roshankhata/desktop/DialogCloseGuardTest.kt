package com.innovation313.roshankhata.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit L005 (17 Nov 2026), register BC-206: a Windows form with typing in it asks before Esc, the dialog's ✕ or its own
 * head's ✕ throws it away — the entry, invoice, cheque, plan, scheme, purchase-order and party forms, as the bill, product,
 * staff and cashbook forms did (8 Nov, L83). Before, only those four asked, and only on Esc: a DialogFrame's ✕ closed at
 * once, and most dialogs passed `escCloses(close)`, which named the close and not the question.
 *
 * [CloseGuard] is the decision every [AppDialog] makes; it is held here without a window. Each form says how it opened
 * ([rememberOpened]) and is judged against it ([typedSince]), so a form that opens filled (an edit, a low-stock order, a
 * plan's instalment) is not "typed" until the owner changes it.
 */
class DialogCloseGuardTest {

    private var closed = 0
    private val close: () -> Unit = { closed++ }

    @Test
    fun `an untouched form closes at once`() {
        val g = CloseGuard()
        g.request(close)
        assertEquals(1, closed)
        assertNull(g.pending)
        assertFalse(g.escTakesFirst())
    }

    @Test
    fun `a typed form asks first and closes only on Discard`() {
        var typed = false
        val g = CloseGuard()
        g.dirty = { typed }
        typed = true
        g.request(close)
        assertEquals("asked, not closed", 0, closed)
        assertTrue(g.pending != null)
        assertTrue("Esc goes through the question, before a caller's own key handler", g.escTakesFirst())
        g.dismiss()
        assertEquals("Keep editing closes nothing", 0, closed)
        assertNull(g.pending)
        g.request(close)
        g.confirm()
        assertEquals("Discard closes once", 1, closed)
        g.confirm()
        assertEquals("a second Discard closes nothing more", 1, closed)
    }

    @Test
    fun `what the body says it holds counts with the dialog's own dirty`() {
        // The entry and invoice forms report through FormTyped: their dialog cannot see their boxes.
        var bodyTyped = false
        val g = CloseGuard()
        g.typed = { bodyTyped }
        g.request(close)
        assertEquals(1, closed)
        bodyTyped = true
        g.request(close)
        assertEquals("the body's typing holds the close", 1, closed)
        g.confirm()
        assertEquals(2, closed)
    }

    @Test
    fun `Discard runs the close that was asked about`() {
        // A DialogFrame's ✕ names its own close (the form's onCancel), which may differ from the dialog's.
        var frameClosed = 0
        val g = CloseGuard()
        g.dirty = { true }
        g.request { frameClosed++ }
        g.confirm()
        assertEquals(1, frameClosed)
        assertEquals(0, closed)
    }

    @Test
    fun `a form is judged against how it opened`() {
        // An edit opens filled: nothing typed until a value changes.
        val opened = listOf<Any?>("Al-Noor Traders", "0300 1234567", 500.0, null, listOf("Urea", "DAP"))
        assertFalse(typedSince(opened, listOf("Al-Noor Traders", "0300 1234567", 500.0, null, listOf("Urea", "DAP"))))
        assertTrue(typedSince(opened, listOf("Al-Noor Traders", "0300 1234568", 500.0, null, listOf("Urea", "DAP"))))
        assertTrue("a line added", typedSince(opened, listOf("Al-Noor Traders", "0300 1234567", 500.0, null, listOf("Urea", "DAP", "Zinc"))))
        assertTrue("a day chosen", typedSince(opened, listOf("Al-Noor Traders", "0300 1234567", 500.0, 1L, listOf("Urea", "DAP"))))
        // A value typed and put back as it was is no typing.
        assertFalse(typedSince(opened, opened.toList()))
    }
}
