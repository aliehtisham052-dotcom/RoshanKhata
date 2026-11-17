package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

/**
 * The phone's Khata Report reads its window a page at a time (17 Nov 2026, audit L026): "All" read every entry of
 * the book at once. The page is the newest rows of the very window [KhataDao.entriesInRange] reads, and the figures
 * the screen shows are the whole window's, from the book — never summed from the page. Made-up names and figures.
 */
class ReportWindowPageTest {

    @Test
    fun `a page is the newest rows of the window and the figures are the whole window's`() = runBlocking {
        val dir = Files.createTempDirectory("rk-report-page").toFile()
        val db = DesktopDatabase.open(dir.resolve("khata.db"))
        try {
            val dao = db.khataDao()
            val t0 = 1_700_000_000_000L
            val a = dao.insertParty(Party(name = "Test Customer A"))
            val b = dao.insertParty(Party(name = "Test Customer B"))
            val gone = dao.insertParty(Party(name = "Test Customer Binned"))
            repeat(450) { i ->
                dao.insertEntry(LedgerEntry(partyId = if (i % 2 == 0) a else b, amount = 10.0 + i, isGiven = i % 3 != 0,
                    entryNumber = "RK-$i", timestamp = t0 + i * 60_000L))
            }
            // Out of every figure: a binned entry, and a binned customer's entry.
            dao.insertEntry(LedgerEntry(partyId = a, amount = 9_999.0, isGiven = true, entryNumber = "RK-X", timestamp = t0 + 5, isDeleted = true))
            dao.insertEntry(LedgerEntry(partyId = gone, amount = 7_777.0, isGiven = true, entryNumber = "RK-Y", timestamp = t0 + 6))
            dao.softDeleteParty(gone)

            val all = dao.entriesInRange(0L, Long.MAX_VALUE)
            val page = dao.entriesInRangeNewest(0L, Long.MAX_VALUE, ListWindow.PAGE)
            assertEquals(ListWindow.PAGE, page.size)
            assertEquals("the newest first, the same rows the whole read ends with",
                all.asReversed().take(ListWindow.PAGE).map { it.id }, page.map { it.id })

            val sums = dao.entriesInRangeTotals(0L, Long.MAX_VALUE)
            assertEquals(all.size, sums.entryCount)
            assertEquals(all.map { it.partyId }.distinct().size, sums.partyCount)
            assertEquals(all.filter { it.isGiven }.sumOf { it.amount }, sums.gaveTotal, 0.0005)
            assertEquals(all.filter { !it.isGiven }.sumOf { it.amount }, sums.gotTotal, 0.0005)

            // A bounded window, its ends counted (BETWEEN, as entriesInRange reads it).
            val from = t0 + 100 * 60_000L
            val to = t0 + 149 * 60_000L
            assertEquals(50, dao.entriesInRangeTotals(from, to).entryCount)
            assertEquals(dao.entriesInRange(from, to).asReversed().map { it.id }, dao.entriesInRangeNewest(from, to, 500).map { it.id })

            // An empty window: zeros, not nothing.
            val none = dao.entriesInRangeTotals(t0 - 10_000, t0 - 1)
            assertEquals(0, none.entryCount); assertEquals(0.0, none.gaveTotal, 0.0); assertEquals(0.0, none.gotTotal, 0.0)
        } finally {
            db.close()
            dir.deleteRecursively()
        }
    }
}
