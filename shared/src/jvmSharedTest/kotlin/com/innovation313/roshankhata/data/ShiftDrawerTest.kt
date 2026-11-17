package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The two plain rules under a shift's drawer (17 Nov 2026, the audit's L108): who a new entry is written by
 * ([EntryWriter]) and what the drawer's parts add up to ([ShiftDrawer]). The book's side is ShiftByHandTest (desktop).
 */
class ShiftDrawerTest {

    @Test
    fun `an entry the caller names keeps its writer`() {
        assertEquals("Imran", EntryWriter.of("Imran", null, "Bilal"))
        assertEquals("Imran", EntryWriter.of("Imran", 12L, "Bilal"))
    }

    @Test
    fun `an entry written by hand takes the name signed in, null for the owner`() {
        assertEquals("Bilal", EntryWriter.of(null, null, "Bilal"))
        assertEquals("Bilal", EntryWriter.of("  ", null, "Bilal"))
        assertNull(EntryWriter.of(null, null, null))
    }

    @Test
    fun `a ticket's own row never takes the name signed in`() {
        // The owner's ticket (no name) rung up while a cashier is signed in stays the owner's.
        assertNull(EntryWriter.of(null, 12L, "Bilal"))
    }

    @Test
    fun `the drawer is the float, plus what came in, less what went out`() {
        val d = ShiftDrawer(float = 1_000.0, taken = 400.0, back = 120.0, handIn = 560.0, handOut = 150.0)
        assertEquals(1_690.0, d.sum, 0.0)
        // With nothing by hand it is the drawer as it was before the by-hand parts.
        assertEquals(1_280.0, ShiftDrawer(1_000.0, 400.0, 120.0).sum, 0.0)
        // A drawer can be short of its float: no part is clamped.
        assertEquals(-50.0, ShiftDrawer(0.0, 0.0, 0.0, 0.0, 50.0).sum, 0.0)
    }
}
