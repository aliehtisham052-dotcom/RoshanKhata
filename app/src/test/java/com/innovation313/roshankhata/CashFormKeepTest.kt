package com.innovation313.roshankhata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The cashbook's money form kept for a rebuilt screen (23 Nov 2026, audit L160, BC-241): the four boxes, then the way it was
 * paid and the day picked, so a turned screen no longer puts Cash and now back. A form kept by an older build (four boxes)
 * still opens, with the defaults.
 */
class CashFormKeepTest {
    private val boxes = listOf("1500", "Rent", "for November", "TID-1")

    @Test fun `the way paid and the day come back with the boxes`() {
        val kept = CashbookActivity.keptForm(boxes, "BANK", 1_763_000_000_000L)
        assertEquals(boxes, kept.take(4))
        assertEquals("BANK", CashbookActivity.keptMethod(kept))
        assertEquals(1_763_000_000_000L, CashbookActivity.keptTime(kept))
    }

    @Test fun `no way chosen and an older form keep the defaults`() {
        assertNull(CashbookActivity.keptMethod(CashbookActivity.keptForm(boxes, null, 1L)))
        assertNull(CashbookActivity.keptMethod(boxes))
        assertNull(CashbookActivity.keptTime(boxes))
        assertNull(CashbookActivity.keptTime(null))
    }
}
