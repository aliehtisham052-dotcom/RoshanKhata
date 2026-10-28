package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.LedgerEntry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A customer's entry search on Windows said "Notes or goods" and searched the
 * notes only (25 Oct 2026, the audit). Made-up entries.
 */
class EntryMatchTest {

    private val sale = LedgerEntry(id = 7, partyId = 1, amount = 5_000.0, isGiven = true, note = "Wheat season", entryNumber = "RK-000041")

    @Test
    fun `goods are found as well as notes`() {
        assertTrue(entryMatches(sale, "Urea DAP", "urea"))
        assertTrue(entryMatches(sale, null, "wheat"))
        assertFalse(entryMatches(sale, "Urea DAP", "zinc"))
    }

    @Test
    fun `the number and the amount are found, as typed or in Urdu digits`() {
        assertTrue(entryMatches(sale, null, "000041"))
        assertTrue(entryMatches(sale, null, "5000"))
        assertTrue(entryMatches(sale, null, "۵۰۰۰"))
        assertTrue("a blank box shows every entry", entryMatches(sale, null, "  "))
    }
}
