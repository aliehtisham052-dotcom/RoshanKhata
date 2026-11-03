package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.ui.Format
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

/**
 * The 3 Nov 2026 audit's money findings, held on a real book file (made-up names and figures).
 */
class AuditMoneyTest {

    private fun <T> withBook(block: suspend (KhataDao) -> T): T {
        val dir = Files.createTempDirectory("rk-audit-money").toFile()
        val db = DesktopDatabase.open(dir.resolve("khata.db"))
        return try {
            runBlocking { block(db.khataDao()) }
        } finally {
            db.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun `cash bills and staff pay leave the drawer by the books`() = withBook { dao ->
        val supplier = dao.insertParty(Party(name = "Test Supplier", phone = null, isCustomer = false))
        // A bill paid in cash on the spot writes no khata entry; its cash still left the drawer.
        dao.insertSupplierBill(
            null,
            SupplierBill(partyId = supplier, billNumber = "9", totalAmount = 8_000.0, isPaidInFull = true, ledgerEntryId = null),
            emptyList()
        )
        val staff = dao.insertStaff(Staff(name = "Test Helper", monthlySalary = 20_000.0))
        dao.insertStaffPayment(StaffPayment(staffId = staff, amount = 2_000.0, kind = Payroll.ADVANCE))
        val figures = DayCloseMath.drawerByTheBooks(dao)
        assertEquals(10_000.0, figures.otherOut, 0.001)
        assertEquals("opening 25,000 less 10,000 paid out", 15_000.0, figures.expectedFrom(25_000.0), 0.001)
    }

    @Test
    fun `a settled residue prints as nothing owed, and a whole figure as written`() {
        assertEquals(Format.money(0.0), Format.money(1000.10 - 1000.00 - 0.10))
        assertEquals(Format.money(3751.0), Format.money(3750.9999999))
        assertEquals("a residue is no minus", Format.money(0.0), Format.signedIfNegative(1250.10 + 2500.20 - 3750.30))
    }

    @Test
    fun `a cash sale comes back from the bin whole, and an edit can take its other half along`() = withBook { dao ->
        val party = dao.insertParty(Party(name = "Test Customer", phone = null, isCustomer = true))
        val sale = LedgerEntry(partyId = party, amount = 600.0, isGiven = true, entryNumber = "", rateType = RateType.CASH)
        val (saleId, gotId) = dao.insertEntryWithItemsPaidNow(sale, emptyList())
        val got = gotId!!
        dao.deleteEntryAudited(saleId, 600.0, got)
        assertEquals("the binned half's number is said first", got, dao.binnedPairOf(saleId)?.id)
        assertEquals(got, dao.restoreEntryWithPair(saleId))
        assertEquals("both halves back: nothing owed", 0.0, dao.liveBalance(party), 0.001)

        // The sale corrected from 600 to 800: its "I got" still says 600, so it is asked about, and follows on "Change both".
        val before = dao.getEntry(saleId)!!
        dao.updateEntry(before.copy(amount = 800.0))
        val pair = dao.pairToFollow(before, before.amount)
        assertEquals(got, pair?.id)
        assertEquals(true, dao.setPairAmount(got, saleId, 800.0))
        assertEquals(0.0, dao.liveBalance(party), 0.001)
        assertNull("once they differ, nothing is asked", dao.pairToFollow(dao.getEntry(saleId)!!, 600.0))
    }

    @Test
    fun `a deleted plan no longer holds its instalments`() = withBook { dao ->
        val party = dao.insertParty(Party(name = "Test Customer", phone = null, isCustomer = true))
        val plan = dao.insertPlan(PaymentPlan(partyId = party, totalAmount = 5_000.0, installmentAmount = 1_000.0))
        dao.recordPlanPayment(plan, party, 1_000.0, paidBefore = 0.0, entryNote = "Instalment", note = null)
        val entry = dao.latestEntriesWithItems(party, 10).single().entry.id
        assertEquals("plan", dao.entryOwner(entry))
        dao.softDeletePlan(plan)
        assertNull("the instalment can be corrected once its plan is gone", dao.entryOwner(entry))
    }
}
