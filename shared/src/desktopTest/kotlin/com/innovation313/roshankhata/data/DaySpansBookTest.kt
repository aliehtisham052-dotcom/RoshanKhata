package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

/**
 * The six (from, to] cash sums asked through [DaySpans]' [from, to) wrappers on a real book (16 Nov 2026, audit D-04): a cash
 * row written at exactly 00:00:00.000 is counted in the day that starts at it, never in the day before; the DAO's own call with
 * a [from, to) window shows the trap the wrappers close. Made-up names.
 */
class DaySpansBookTest {

    @Test
    fun `cash written at exactly midnight is the new day's`() = runBlocking {
        val dir = Files.createTempDirectory("rk-day-spans").toFile()
        val db = DesktopDatabase.open(dir.resolve("khata.db"))
        try {
            val dao = db.khataDao()
            val day = DayMath.startOfDay(System.currentTimeMillis()) // today's first moment
            val before = DaySpans.previousDayStart(day)
            val after = DaySpans.nextDayStart(day)
            dao.insertCashEntry(CashEntry(amount = 100.0, isIncome = true, category = "Other", timestamp = day))
            val party = dao.insertParty(Party(name = "Window Test Customer"))
            dao.insertEntryNumbered(LedgerEntry(partyId = party, amount = 40.0, isGiven = false, entryNumber = "", timestamp = day, paymentMethod = "cash"))

            assertEquals(100.0, dao.cashbookCashOver(true, day, after), 0.0)
            assertEquals(0.0, dao.cashbookCashOver(true, before, day), 0.0)
            assertEquals(100.0, dao.cashbookTotalOver(true, day, after), 0.0)
            assertEquals(0.0, dao.cashbookTotalOver(true, before, day), 0.0)
            assertEquals(40.0, dao.khataCashOver(false, day, after), 0.0)
            assertEquals(0.0, dao.khataCashOver(false, before, day), 0.0)
            // The trap: the DAO's own sum handed a [from, to) window puts the midnight row in the day before.
            assertEquals(100.0, dao.cashbookCashBetween(true, before, day), 0.0)
            assertEquals(0.0, dao.cashbookCashBetween(true, day, after), 0.0)
        } finally {
            db.close()
            dir.deleteRecursively()
        }
    }
}
