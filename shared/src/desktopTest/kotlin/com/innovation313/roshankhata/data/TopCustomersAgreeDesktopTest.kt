package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

/**
 * BC-265 (24 Nov 2026), on a real book (CI only): a customer flagged deleted whose entries are still live is in the sales
 * total AND in the top customers; binning him with his entries takes him out of both. Invented names and figures.
 */
class TopCustomersAgreeDesktopTest {

    @Test
    fun `top customers always add up to the sales total`() = runBlocking {
        val dir = Files.createTempDirectory("rk-top-customers").toFile()
        val db = DesktopDatabase.open(dir.resolve("khata.db"))
        try {
            val dao = db.khataDao()
            val a = dao.insertParty(Party(name = "Top Test Alpha"))
            val b = dao.insertParty(Party(name = "Top Test Beta"))
            val now = System.currentTimeMillis()
            dao.insertEntryNumbered(LedgerEntry(partyId = a, amount = 700.0, isGiven = true, entryNumber = "", timestamp = now - 1000))
            dao.insertEntryNumbered(LedgerEntry(partyId = b, amount = 300.0, isGiven = true, entryNumber = "", timestamp = now - 1000))
            val from = now - 86_400_000L
            val to = now + 1000
            fun top() = runBlocking { dao.topCustomersBetween(from, to, 10) }
            fun total() = runBlocking { dao.salesTotalBetween(from, to) }

            assertEquals(1_000.0, total(), 0.0)
            assertEquals(total(), top().sumOf { it.total }, 0.0)

            // the party's flag alone (the entries stay live): both figures keep him
            dao.softDeleteParty(b)
            assertEquals(1_000.0, total(), 0.0)
            assertEquals(listOf("Top Test Alpha", "Top Test Beta"), top().map { it.name })
            assertEquals(total(), top().sumOf { it.total }, 0.0)

            // binned with his entries: out of both
            dao.softDeleteEntriesOfParty(b)
            assertEquals(700.0, total(), 0.0)
            assertEquals(listOf("Top Test Alpha"), top().map { it.name })
        } finally {
            db.close()
            dir.deleteRecursively()
        }
    }
}
