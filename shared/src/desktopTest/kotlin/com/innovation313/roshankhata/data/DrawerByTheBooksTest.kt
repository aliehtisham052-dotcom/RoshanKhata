package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

/**
 * The drawer by the books, one rule on both platforms (29 Oct 2026, the cleanup).
 *
 * Before, the computer had `drawerByTheBooks` (HomePane) and the phone's Close the day
 * (CashbookActivity.showDayClose) wrote the same four queries and the same arithmetic
 * inline. This test computes the phone's formula from the same rows, exactly as the
 * dialog wrote it, and holds [DayCloseMath.drawerByTheBooks] to it — and to what the
 * computer's pair (expected, last count) said. Made-up figures only.
 */
class DrawerByTheBooksTest {

    /** The phone's inline arithmetic, line for line as CashbookActivity.showDayClose had it. */
    private suspend fun phoneFigures(dao: KhataDao, now: Long): PhoneFigures {
        val last = dao.lastDayClose()
        val from = last?.closedAt ?: 0L
        val cbIn = dao.cashbookCashBetween(true, from, now)
        val cbOut = dao.cashbookCashBetween(false, from, now)
        val kIn = dao.khataCashBetween(false, from, now)
        val kOut = dao.khataCashBetween(true, from, now)
        val cashIn = cbIn + kIn
        val cashOut = cbOut + kOut
        // The dialog's opening box starts at the last count (nothing when never counted).
        val opening = last?.counted ?: 0.0
        return PhoneFigures(last, cbIn, cbOut, kIn, kOut, cashIn, cashOut, DayCloseMath.expected(opening, cashIn, cashOut))
    }

    private class PhoneFigures(
        val last: DayClose?, val cbIn: Double, val cbOut: Double, val kIn: Double, val kOut: Double,
        val cashIn: Double, val cashOut: Double, val expected: Double
    )

    private fun assertAgree(phone: PhoneFigures, shared: DrawerFigures) {
        assertEquals(phone.last?.id, shared.last?.id)
        assertEquals(phone.cbIn, shared.cbIn, 0.0)
        assertEquals(phone.cbOut, shared.cbOut, 0.0)
        assertEquals(phone.kIn, shared.kIn, 0.0)
        assertEquals(phone.kOut, shared.kOut, 0.0)
        assertEquals(phone.cashIn, shared.cashIn, 0.0)
        assertEquals(phone.cashOut, shared.cashOut, 0.0)
        assertEquals(phone.expected, shared.expected, 0.0)
        // The dialog recomputes from a typed opening: the same arithmetic, the same answer.
        assertEquals(DayCloseMath.expected(2_500.0, phone.cashIn, phone.cashOut), shared.expectedFrom(2_500.0), 0.0)
    }

    @Test
    fun `the shared drawer equals the phone's inline figures, before and after a count`() = runBlocking {
        val dir = Files.createTempDirectory("rk-drawer-shared").toFile()
        val db = DesktopDatabase.open(dir.resolve("khata.db"))
        try {
            val dao = db.khataDao()
            val t0 = System.currentTimeMillis() - 3 * 60 * 60 * 1000
            // Cashbook: cash in and out, a bank receipt that is not in the drawer, a wallet expense likewise.
            dao.insertCashEntry(CashEntry(amount = 1_000.0, isIncome = true, category = "Sale", timestamp = t0))
            dao.insertCashEntry(CashEntry(amount = 5_000.0, isIncome = true, category = "Sale", timestamp = t0 + 1, paymentMethod = PaymentMethod.BANK))
            dao.insertCashEntry(CashEntry(amount = 200.0, isIncome = false, category = "Tea", timestamp = t0 + 2))
            dao.insertCashEntry(CashEntry(amount = 350.5, isIncome = false, category = "Rent", timestamp = t0 + 3, paymentMethod = PaymentMethod.ONLINE))
            // Khata: a cash receipt, a cash payment out, a sale on credit (no cash moved).
            val aslam = dao.insertParty(Party(name = "Aslam"))
            dao.insertEntry(LedgerEntry(partyId = aslam, amount = 300.0, isGiven = false, entryNumber = "RK-1", timestamp = t0 + 4, paymentMethod = PaymentMethod.CASH))
            dao.insertEntry(LedgerEntry(partyId = aslam, amount = 120.25, isGiven = true, entryNumber = "RK-2", timestamp = t0 + 5, paymentMethod = PaymentMethod.CASH))
            dao.insertEntry(LedgerEntry(partyId = aslam, amount = 900.0, isGiven = true, entryNumber = "RK-3", timestamp = t0 + 6))

            val now1 = t0 + 10 * 60_000L
            val never = DayCloseMath.drawerByTheBooks(dao, now1)
            assertNull("never counted", never.last)
            assertAgree(phoneFigures(dao, now1), never)
            assertEquals("1,000 + 300 − 200 − 120.25", 979.75, never.expected, 0.0005)

            // A count, then more movement: the figures run from the count's moment.
            val countedAt = t0 + 20 * 60_000L
            dao.saveDayCloseRow(DayClose(day = DayCloseMath.businessDayOf(countedAt), opening = 0.0, cashIn = 1_300.0, cashOut = 320.25, counted = 900.0, closedAt = countedAt))
            dao.insertCashEntry(CashEntry(amount = 100.0, isIncome = true, category = "Sale", timestamp = countedAt + 60_000))
            dao.insertEntry(LedgerEntry(partyId = aslam, amount = 40.0, isGiven = true, entryNumber = "RK-4", timestamp = countedAt + 120_000, paymentMethod = PaymentMethod.CASH))
            // Before the count: must not be counted again.
            val now2 = countedAt + 10 * 60_000L
            val after = DayCloseMath.drawerByTheBooks(dao, now2)
            assertAgree(phoneFigures(dao, now2), after)
            assertEquals(900.0, after.last!!.counted, 0.0)
            assertEquals("the count, plus 100 in, less 40 out", 960.0, after.expected, 0.0005)
            assertEquals(100.0, after.cbIn, 0.0); assertEquals(0.0, after.cbOut, 0.0)
            assertEquals(0.0, after.kIn, 0.0); assertEquals(40.0, after.kOut, 0.0)

            // The computer's old pair (expected, last) is the same two values.
            assertEquals(after.expected, DayCloseMath.expected(after.last?.counted ?: 0.0, after.cbIn + after.kIn, after.cbOut + after.kOut), 0.0)
        } finally {
            db.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a movement after now is not in the figure`() = runBlocking {
        val dir = Files.createTempDirectory("rk-drawer-now").toFile()
        val db = DesktopDatabase.open(dir.resolve("khata.db"))
        try {
            val dao = db.khataDao()
            val now = System.currentTimeMillis()
            dao.insertCashEntry(CashEntry(amount = 500.0, isIncome = true, category = "Sale", timestamp = now - 1))
            dao.insertCashEntry(CashEntry(amount = 700.0, isIncome = true, category = "Sale", timestamp = now + 60_000))
            val d = DayCloseMath.drawerByTheBooks(dao, now)
            assertAgree(phoneFigures(dao, now), d)
            assertEquals(now, d.now)
            assertEquals(500.0, d.expected, 0.0)
        } finally {
            db.close()
            dir.deleteRecursively()
        }
    }
}
