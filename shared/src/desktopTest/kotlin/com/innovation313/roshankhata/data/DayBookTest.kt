package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/**
 * The day book (3 Nov 2026): one day's money, row by row, from the rows the day report and Close the day read —
 * the khata, the cashbook, bills paid in cash on delivery and the staff paid; nothing from another day, nothing
 * binned, nothing twice. Made-up figures only.
 */
class DayBookTest {

    @Test
    fun `a day's rows come from every place money moves, in the order they happened`() = runBlocking {
        val dir = Files.createTempDirectory("rk-day-book").toFile()
        val db = DesktopDatabase.open(dir.resolve("khata.db"))
        try {
            val dao = db.khataDao()
            val day = DayCloseMath.dayOf(System.currentTimeMillis()) - 1
            val from = DayCloseMath.startOf(day)
            val to = DayCloseMath.startOf(day + 1)
            val at = from + 9 * 60 * 60 * 1000L
            val aslam = dao.insertParty(Party(name = "Aslam"))
            val seeds = dao.insertParty(Party(name = "Seeds Co.", isCustomer = false))
            // The khata: a sale on credit, then a cash payment; a binned entry and yesterday's are left out.
            dao.insertEntry(LedgerEntry(partyId = aslam, amount = 900.0, isGiven = true, entryNumber = "RK-1", timestamp = at + 3))
            dao.insertEntry(LedgerEntry(partyId = aslam, amount = 300.0, isGiven = false, entryNumber = "RK-2", timestamp = at + 1, paymentMethod = PaymentMethod.CASH, note = "Part payment"))
            dao.insertEntry(LedgerEntry(partyId = aslam, amount = 50.0, isGiven = true, entryNumber = "RK-3", timestamp = at + 2, isDeleted = true))
            dao.insertEntry(LedgerEntry(partyId = aslam, amount = 70.0, isGiven = true, entryNumber = "RK-4", timestamp = from - 1))
            // The cashbook: a walk-in's ticket (its payment is cashbook income) and an expense.
            dao.insertCashEntry(CashEntry(amount = 1_000.0, isIncome = true, category = CashCategory.SALE, timestamp = at + 4, paymentMethod = PaymentMethod.BANK, paymentRef = "TX-9"))
            dao.insertCashEntry(CashEntry(amount = 200.0, isIncome = false, category = "Tea", timestamp = at + 5))
            dao.insertCashEntry(CashEntry(amount = 999.0, isIncome = false, category = "Rent", timestamp = to))
            // A bill paid in cash on delivery (no khata entry), and one on credit (its khata entry is the money).
            dao.insertBill(SupplierBill(partyId = seeds, billNumber = "B-7", totalAmount = 400.0, isPaidInFull = true, createdAt = at + 6))
            dao.insertBill(SupplierBill(partyId = seeds, billNumber = "B-8", totalAmount = 800.0, isPaidInFull = false, createdAt = at + 7))
            // The staff: an advance.
            val ali = dao.insertStaff(Staff(name = "Ali", monthlySalary = 20_000.0))
            dao.insertStaffPayment(StaffPayment(staffId = ali, amount = 500.0, kind = Payroll.ADVANCE, timestamp = at + 8))

            val rows = DayBook.rows(dao, from, to)
            assertEquals(listOf(DayBook.Kind.GOT, DayBook.Kind.GAVE, DayBook.Kind.CASH_IN, DayBook.Kind.CASH_OUT, DayBook.Kind.BILL_PAID, DayBook.Kind.STAFF_PAID),
                rows.map { it.kind })
            assertEquals(listOf(300.0, 900.0, 1_000.0, 200.0, 400.0, 500.0), rows.map { it.amount })
            assertTrue(rows.zipWithNext().all { (a, b) -> a.at <= b.at })
            val paid = rows.first()
            assertEquals("Aslam", paid.who)
            assertEquals(PaymentMethod.CASH, paid.method)
            assertEquals("RK-2 · Part payment", paid.detail)
            assertTrue(paid.isIn)
            assertFalse(rows[1].isIn)
            assertEquals("TX-9", rows[2].detail)
            assertEquals("B-7", rows[4].detail)
            assertEquals(Payroll.ADVANCE, rows[5].staffKind)
            // Each row knows the row it came from, so a list keyed on it never repeats a key.
            assertEquals(rows.size, rows.map { it.kind to it.ref }.toSet().size)
        } finally {
            db.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun `the words name the counter's own cashbook rows and say the way once`() {
        val words = object : com.innovation313.roshankhata.words.Words {
            override fun text(name: String, vararg args: Any) = name
            override fun plural(name: String, count: Int, vararg args: Any) = name
            override fun list(name: String) = emptyList<String>()
        }
        val sale = DayBook.Row(0, DayBook.Kind.CASH_IN, CashCategory.SALE, PaymentMethod.CASH, 10.0, null)
        assertEquals("cash_category_sale", DayBook.whoText(words, sale))
        assertTrue(DayBook.kindText(words, sale).startsWith("cash_in · "))
        val bill = DayBook.Row(0, DayBook.Kind.BILL_PAID, "Seeds Co.", PaymentMethod.CASH, 10.0, null)
        assertEquals("bill_paid_cash", DayBook.kindText(words, bill))
        val loan = DayBook.Row(0, DayBook.Kind.GAVE, "Aslam", null, 10.0, null, loan = true)
        assertEquals("i_gave · pdf_stmt_qarze_hasna", DayBook.kindText(words, loan))
    }
}
