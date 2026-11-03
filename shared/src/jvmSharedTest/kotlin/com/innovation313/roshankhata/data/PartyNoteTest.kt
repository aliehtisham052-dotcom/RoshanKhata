package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The owner's private note on a party (schema 43, 4 Nov 2026): what is kept of what was typed, and two joined. */
class PartyNoteTest {

    @Test
    fun `a note is trimmed, kept short, and nothing typed is no note`() {
        assertEquals("Pays on the 10th", PartyNote.clean("  Pays on the 10th \n"))
        assertNull(PartyNote.clean("   \n  "))
        assertNull(PartyNote.clean(null))
        assertEquals(PartyNote.MAX, PartyNote.clean("x".repeat(PartyNote.MAX + 40))!!.length)
    }

    @Test
    fun `two notes joined keep both, on their own lines, and never one twice`() {
        assertEquals("Only cash\nAsk for his son", PartyNote.join("Only cash", "Ask for his son"))
        assertEquals("Only cash", PartyNote.join("Only cash", "only cash"))
        assertEquals("Ask for his son", PartyNote.join(null, " Ask for his son "))
        assertEquals("Only cash", PartyNote.join("Only cash", "  "))
        assertNull(PartyNote.join(null, null))
        // Joined, still no longer than a note may be.
        assertEquals(PartyNote.MAX, PartyNote.join("a".repeat(400), "b".repeat(400))!!.length)
    }
}
