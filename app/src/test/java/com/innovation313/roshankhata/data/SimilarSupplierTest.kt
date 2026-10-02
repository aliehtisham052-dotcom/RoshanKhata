package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A scanned heading one letter off an existing supplier is offered, never applied. */
class SimilarSupplierTest {
    @Test
    fun `one letter off in the first word is offered`() {
        assertEquals("Sincrop", BillScan.similarKnown("Suncrop Pesticides", listOf("Jajja Medical Complex", "Sincrop")))
        assertEquals("Sayban International", BillScan.similarKnown("Saybaan International", listOf("Sayban International")))
    }

    @Test
    fun `different names and exact matches are not offered`() {
        assertNull(BillScan.similarKnown("Khan Traders", listOf("Shah Traders")))
        assertNull(BillScan.similarKnown("Sincrop", listOf("Sincrop")))
        assertNull(BillScan.similarKnown("Abc", listOf("Abd")))
    }
}
