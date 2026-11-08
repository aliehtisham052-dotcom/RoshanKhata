package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** A new order typed, kept as flat text and put back (M88, 8 Nov 2026): lines, quantities, rates and the note as typed. */
class OrderDraftKeepTest {
    @Test
    fun aDraftComesBackAsTyped() {
        val a = OrderDraft(null, emptyList())
        a.partyId = 7L
        a.note = "Friday, please"
        a.lines += OrderDraft.Line(3L, "Item A", "1+2", "bag", "450")
        a.lines += OrderDraft.Line(null, "Free text item", "5", null, "")
        val b = OrderDraft(null, emptyList())
        assertEquals(true, b.restore(a.snapshot()))
        assertEquals(7L, b.partyId)
        assertEquals("Friday, please", b.note)
        assertEquals(listOf("Item A", "Free text item"), b.lines.map { it.name })
        assertEquals(listOf("1+2", "5"), b.lines.map { it.qty })
        assertEquals(listOf<String?>("bag", null), b.lines.map { it.unit })
        assertEquals(a.snapshot(), b.snapshot())
    }

    @Test
    fun anEmptyKeepIsRefused() {
        assertEquals(false, OrderDraft(null, emptyList()).restore(emptyList()))
    }
}
