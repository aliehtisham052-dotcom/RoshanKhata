package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.nio.file.Files

/**
 * The DAO refuses an amount the book may not hold (16 Nov 2026, audit T42 / D-06): a minus, NaN, infinity, a figure past
 * the calculator's range — on the khata's numbered insert, its edit and the cashbook's audited insert. Nothing is written.
 * Zero stays allowed (goods at no price). Made-up names.
 */
class BookAmountGuardTest {

    private fun refused(what: String, block: suspend () -> Unit) = runBlocking {
        try {
            block()
            fail("$what was written")
        } catch (e: IllegalArgumentException) {
            // refused, as it should be
        }
    }

    @Test
    fun `a minus, NaN, infinity or a figure past range is never written`() = runBlocking {
        val dir = Files.createTempDirectory("rk-amount-guard").toFile()
        val db = DesktopDatabase.open(dir.resolve("khata.db"))
        try {
            val dao = db.khataDao()
            val party = dao.insertParty(Party(name = "Guard Test Customer"))
            fun entry(a: Double) = LedgerEntry(partyId = party, amount = a, isGiven = true, entryNumber = "")
            refused("a minus entry") { dao.insertEntryNumbered(entry(-500.0)) }
            refused("a NaN entry") { dao.insertEntryNumbered(entry(Double.NaN)) }
            refused("an infinite entry") { dao.insertEntryNumbered(entry(Double.POSITIVE_INFINITY)) }
            refused("an entry past range") { dao.insertEntryNumbered(entry(1e16)) }
            refused("a minus entry with goods") { dao.insertEntryWithItems(entry(-1.0), emptyList()) }
            refused("a minus cashbook row") { dao.insertCashEntryAudited(CashEntry(amount = -100.0, isIncome = true, category = "Other")) }
            assertEquals("nothing was written", 0, dao.entryCountBetween(0, Long.MAX_VALUE))

            // Allowed: an ordinary amount and zero (goods given at no price).
            val ok = dao.insertEntryNumbered(entry(500.0))
            dao.insertEntryNumbered(entry(0.0))
            assertEquals(2, dao.entryCountBetween(0, Long.MAX_VALUE))
            // An edit to a minus is refused and leaves the row as it was.
            val stored = dao.getEntry(ok)!!
            refused("an edit to a minus") { dao.updateEntryWithItems(stored.copy(amount = -500.0), emptyList()) }
            assertEquals(500.0, dao.getEntry(ok)!!.amount, 0.0)
        } finally {
            db.close()
            dir.deleteRecursively()
        }
    }
}
