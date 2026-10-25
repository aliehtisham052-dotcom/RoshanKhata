package com.innovation313.roshankhata.desktop

import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The counter on a laptop (25 Oct 2026, the audit): a six-digit total lost
 * its last digits at 26 sp in the 300 dp column. The figure shrinks instead.
 */
class CounterFitTest {

    @Test
    fun `a long total gets a smaller size instead of being cut`() {
        assertEquals(26.sp, totalSize("Rs 12,850"))
        assertEquals(22.sp, totalSize("Rs 1,234,567"))
        assertEquals(18.sp, totalSize("Rs 12,345,678.50"))
    }
}
