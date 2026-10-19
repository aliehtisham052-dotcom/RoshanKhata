package com.innovation313.roshankhata.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

/**
 * Cheques and the cashbook on the computer (KMP Phase 7): a cheque is not
 * money until it clears, clearing posts exactly one entry, and the cashbook
 * never touches anyone's balance.
 */
class DesktopChequeCashTest {

    private fun <T> withBook(block: suspend (KhataDao) -> T): T {
        val dir = Files.createTempDirectory("rk-cheque").toFile()
        val db = DesktopDatabase.open(dir.resolve("khata.db"))
        return try {
            runBlocking { block(db.khataDao()) }
        } finally {
            db.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a received cheque lowers the balance only when it clears, and only once`() = withBook { dao ->
        val id = dao.insertParty(Party(name = "Bilal", phone = null, isCustomer = true))
        dao.insertEntryNumbered(LedgerEntry(partyId = id, amount = 5000.0, isGiven = true, entryNumber = ""))
        val cheque = dao.insertCheque(
            Cheque(partyId = id, amount = 3000.0, isReceived = true, chequeNumber = "123", dueDate = 0L)
        )
        assertEquals("pending: not money yet", 5000.0, dao.partiesWithBalanceOnce().single().balance, 0.001)

        val entryId = dao.clearCheque(cheque, "Cheque #123")!!
        assertEquals(2000.0, dao.partiesWithBalanceOnce().single().balance, 0.001)
        val stored = dao.getCheque(cheque)!!
        assertEquals(ChequeStatus.CLEARED, stored.status)
        assertEquals(entryId, stored.ledgerEntryId)
        assertEquals("RK-000002", dao.getEntry(entryId)!!.entryNumber)

        assertNull("a second click posts nothing", dao.clearCheque(cheque, "Cheque #123"))
        assertEquals(2000.0, dao.partiesWithBalanceOnce().single().balance, 0.001)
    }

    @Test
    fun `an issued cheque that clears is money the shop gave`() = withBook { dao ->
        val id = dao.insertParty(Party(name = "Supplier", phone = null, isCustomer = false))
        val cheque = dao.insertCheque(Cheque(partyId = id, amount = 1200.0, isReceived = false, dueDate = 0L))
        val entry = dao.getEntry(dao.clearCheque(cheque, "Cheque")!!)!!
        assertEquals(true, entry.isGiven)
        assertEquals(1200.0, entry.amount, 0.0)
    }

    @Test
    fun `a bounced cheque cannot be cleared into the ledger`() = withBook { dao ->
        val id = dao.insertParty(Party(name = "Bilal", phone = null, isCustomer = true))
        val cheque = dao.insertCheque(Cheque(partyId = id, amount = 900.0, isReceived = true, dueDate = 0L))
        dao.updateCheque(dao.getCheque(cheque)!!.copy(status = ChequeStatus.BOUNCED, settledAt = 1L))
        assertNull(dao.clearCheque(cheque, "Cheque"))
        assertEquals(0.0, dao.partiesWithBalanceOnce().single().balance, 0.001)
    }

    @Test
    fun `the cashbook sums in and out and leaves balances alone`() = withBook { dao ->
        val id = dao.insertParty(Party(name = "Bilal", phone = null, isCustomer = true))
        dao.insertCashEntry(CashEntry(amount = 10000.0, isIncome = true, category = "Cash sale"))
        val rent = dao.insertCashEntry(CashEntry(amount = 2500.0, isIncome = false, category = "Rent"))
        assertEquals(10000.0, dao.observeCashIncome().first(), 0.001)
        assertEquals(2500.0, dao.observeCashExpense().first(), 0.001)
        assertEquals(listOf("Cash sale", "Rent"), dao.cashCategories())
        dao.softDeleteCashEntry(rent)
        assertEquals(0.0, dao.observeCashExpense().first(), 0.001)
        assertEquals(1, dao.observeCashEntries().first().size)
        assertEquals(0.0, dao.partiesWithBalanceOnce().single { it.id == id }.balance, 0.001)
    }

    @Test
    fun `a plan payment is posted once and closes the plan when it is paid off`() = withBook { dao ->
        val id = dao.insertParty(Party(name = "Bilal", phone = null, isCustomer = true))
        dao.insertEntryNumbered(LedgerEntry(partyId = id, amount = 3000.0, isGiven = true, entryNumber = ""))
        val plan = dao.insertPlan(PaymentPlan(partyId = id, totalAmount = 3000.0, installmentAmount = 1000.0))
        dao.recordPlanPayment(plan, id, 1000.0, paidBefore = 0.0, entryNote = "Instalment payment", note = null)
        assertEquals(2000.0, dao.partiesWithBalanceOnce().single().balance, 0.001)
        assertEquals(false, dao.getPlan(plan)!!.isClosed)
        dao.recordPlanPayment(plan, id, 2000.0, paidBefore = 1000.0, entryNote = "Instalment payment", note = null)
        assertEquals(0.0, dao.partiesWithBalanceOnce().single().balance, 0.001)
        assertEquals(true, dao.getPlan(plan)!!.isClosed)
    }

    @Test
    fun `customers from another app come in with one opening entry each`() = withBook { dao ->
        val added = dao.importOpeningBalances(
            listOf(CsvImport.Row("Bilal", "0300", 1200.0), CsvImport.Row("Akram", null, -300.0), CsvImport.Row("Nawaz", null, 0.0)),
            note = "Opening balance"
        )
        assertEquals(3, added)
        val book = dao.partiesWithBalanceOnce().associateBy { it.name }
        assertEquals(1200.0, book.getValue("Bilal").balance, 0.001)
        assertEquals(-300.0, book.getValue("Akram").balance, 0.001)
        assertEquals(0.0, book.getValue("Nawaz").balance, 0.001)
        assertEquals("a zero balance writes no entry", 0, dao.latestEntriesWithItems(book.getValue("Nawaz").id, 10).size)
        assertEquals("Opening balance", dao.latestEntriesWithItems(book.getValue("Bilal").id, 10).single().entry.note)
    }
}
