package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.CashEntry
import com.innovation313.roshankhata.data.DayClose
import com.innovation313.roshankhata.data.DayCloseMath
import com.innovation313.roshankhata.data.DesktopDatabase
import com.innovation313.roshankhata.data.LedgerEntry
import com.innovation313.roshankhata.data.Party
import com.innovation313.roshankhata.data.PaymentMethod
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

/**
 * Home's cash card (25 Oct 2026, the audit): it showed the cashbook's net in
 * every method, so a walk-in's bank transfer read as cash in hand and khata
 * cash was left out. It now follows Close the day's rule: the last count plus
 * cash in, less cash out, since — the rule shared with the phone since 29 Oct 2026
 * (DayCloseMath.drawerByTheBooks; DrawerByTheBooksTest holds it to the phone's arithmetic).
 * Made-up figures only.
 */
class DrawerFigureTest {

    @Test
    fun `the drawer counts cash only, khata cash included, from the last count`() = runBlocking {
        val dir = Files.createTempDirectory("rk-drawer").toFile()
        val db = DesktopDatabase.open(dir.resolve("khata.db"))
        try {
            val dao = db.khataDao()
            val t0 = System.currentTimeMillis() - 60 * 60 * 1000
            dao.insertCashEntry(CashEntry(amount = 1_000.0, isIncome = true, category = "Sale", timestamp = t0))
            dao.insertCashEntry(CashEntry(amount = 5_000.0, isIncome = true, category = "Sale", timestamp = t0, paymentMethod = PaymentMethod.BANK))
            dao.insertCashEntry(CashEntry(amount = 200.0, isIncome = false, category = "Tea", timestamp = t0))
            val aslam = dao.insertParty(Party(name = "Aslam"))
            dao.insertEntry(LedgerEntry(partyId = aslam, amount = 300.0, isGiven = false, entryNumber = "RK-1", timestamp = t0, paymentMethod = PaymentMethod.CASH))

            val neverCounted = DayCloseMath.drawerByTheBooks(dao)
            val (never, none) = neverCounted.expected to neverCounted.last
            assertNull("never counted", none)
            assertEquals("1,000 + 300 khata cash − 200; the bank transfer is not in the drawer", 1_100.0, never, 0.001)

            val countedAt = t0 + 10 * 60 * 1000
            dao.saveDayCloseRow(DayClose(day = DayCloseMath.dayOf(countedAt), opening = 0.0, cashIn = 1_300.0, cashOut = 200.0, counted = 900.0, closedAt = countedAt))
            dao.insertCashEntry(CashEntry(amount = 100.0, isIncome = true, category = "Sale", timestamp = countedAt + 60_000))
            val counted = DayCloseMath.drawerByTheBooks(dao)
            val (now, last) = counted.expected to counted.last
            assertEquals(900.0, last!!.counted, 0.0)
            assertEquals("the count, plus what came in after it", 1_000.0, now, 0.001)
        } finally {
            db.close()
            dir.deleteRecursively()
        }
    }
}
