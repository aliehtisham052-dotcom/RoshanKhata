package com.innovation313.roshankhata.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** A quantity is grouped as the reader groups money (23 Oct review, R16). */
class FormatQtyGroupingTest {
    @Test
    fun `Indonesian quantities group with dots like Rp`() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale("id", "ID"))
            assertEquals("1.500", Format.qty(1500.0, null))
            assertEquals("1.500,50", Format.qty(1500.5, null))
            assertEquals("1.500 — Beras", Format.goods("Beras", 1500.0, null))
            Locale.setDefault(Locale.ENGLISH)
            assertEquals("1,500", Format.qty(1500.0, null))
            assertEquals("1,500 — Rice", Format.goods("Rice", 1500.0, null))
        } finally {
            Locale.setDefault(before)
        }
    }
}
