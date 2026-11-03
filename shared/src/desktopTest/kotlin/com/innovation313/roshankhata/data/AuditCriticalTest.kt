package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter
import java.nio.file.Files

/**
 * The 3 Nov 2026 audit's critical findings, held on a real book file (made-up names and figures).
 *
 * C1: one half of a cash sale deleted for good left the other pointing at nothing, and every backup of the book
 * refused to restore. C2/M10: a sale of unpriced goods with its money taken on the spot wrote no payment, and the
 * sale and its payment were two transactions.
 */
class AuditCriticalTest {

    private fun <T> withBook(block: suspend (KhataDao) -> T): T {
        val dir = Files.createTempDirectory("rk-audit").toFile()
        val db = DesktopDatabase.open(dir.resolve("khata.db"))
        return try {
            runBlocking { block(db.khataDao()) }
        } finally {
            db.close()
            dir.deleteRecursively()
        }
    }

    private suspend fun copyOf(dao: KhataDao): String =
        StringWriter().also { BackupCodec.exportTo(dao, "Test Shop", JSONObject(), it) }.toString()

    private suspend fun cashSale(dao: KhataDao): Pair<Long, Long> {
        val party = dao.insertParty(Party(name = "Test Customer", phone = null, isCustomer = true))
        val (sale, got) = dao.insertEntryWithItemsPaidNow(
            LedgerEntry(partyId = party, amount = 600.0, isGiven = true, entryNumber = "", rateType = RateType.CASH),
            emptyList()
        )
        return sale to got!!
    }

    @Test
    fun `a half of a cash sale purged from the bin leaves a book whose copy still restores`() = withBook { dao ->
        val (sale, got) = cashSale(dao)
        // "Only this one": the I got is binned, then deleted for good.
        dao.softDeleteEntry(got)
        dao.purgeEntry(got)
        assertNull("the survivor no longer points at the purged row", dao.getEntry(sale)!!.pairedEntryId)

        val (result, data) = BackupCodec.parseText(copyOf(dao))
        assertTrue("$result", result is BackupCodec.ImportResult.Ok)
        assertEquals(600.0, data!!.entries.single().amount, 0.0)
    }

    @Test
    fun `a copy made while a pairing pointed at nothing restores, the pairing cleared`() = withBook { dao ->
        val (sale, got) = cashSale(dao)
        // The old purge: the row went and nothing cleared the survivor's pointer.
        dao.softDeleteEntry(got)
        dao.purgeEntryRow(got)
        assertEquals(got, dao.getEntry(sale)!!.pairedEntryId)

        val (result, data) = BackupCodec.parseText(copyOf(dao))
        assertTrue("refused before the fix: $result", result is BackupCodec.ImportResult.Ok)
        assertNull(data!!.entries.single { it.id == sale }.pairedEntryId)

        // The next purge mends the book itself.
        dao.purgeOldEntries(cutoff = 0L)
        assertNull(dao.getEntry(sale)!!.pairedEntryId)
    }

    @Test
    fun `money taken now on unpriced goods makes a cash sale, so its payment is written`() {
        assertEquals(RateType.CASH, RateType.forEntry(isSale = true, goodsHaveRate = false, cashRate = true, paidNow = true, kept = null))
        assertNull("nothing ticked, nothing priced: no rate type, as before",
            RateType.forEntry(isSale = true, goodsHaveRate = false, cashRate = true, paidNow = false, kept = null))
        assertEquals(RateType.CREDIT, RateType.forEntry(isSale = true, goodsHaveRate = true, cashRate = false, paidNow = false, kept = null))
        assertNull("a supplier's goods are not a sale", RateType.forEntry(isSale = false, goodsHaveRate = true, cashRate = true, paidNow = true, kept = null))
    }

    @Test
    fun `a sale with its money taken now is written with its payment in one step`() = withBook { dao ->
        val (sale, got) = cashSale(dao)
        assertEquals(sale, dao.getEntry(got)!!.pairedEntryId)
        assertEquals(got, dao.getEntry(sale)!!.pairedEntryId)
        assertNotNull(dao.getEntry(got)!!.entryNumber)
        assertEquals("nothing owed for goods paid on the spot", 0.0, dao.partiesWithBalanceOnce().single().balance, 0.001)
    }
}
