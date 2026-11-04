package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.ui.Format
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/**
 * How a supplier bill was settled, and what the cash drawer counts of it (4 Nov 2026, the owner's phone: bills
 * ticked "paid in cash" by mistake took Rs 20,000 off the drawer, and the drawer's total lost its minus).
 * Made-up names and figures.
 */
class BillPaidHowTest {

    private fun <T> withBook(block: suspend (KhataDao) -> T): T {
        val dir = Files.createTempDirectory("rk-bill-paid").toFile()
        val db = DesktopDatabase.open(dir.resolve("khata.db"))
        return try {
            runBlocking { block(db.khataDao()) }
        } finally {
            db.close()
            dir.deleteRecursively()
        }
    }

    private fun owed(supplier: Long, amount: Double) =
        LedgerEntry(partyId = supplier, amount = amount, isGiven = false, entryNumber = "")

    @Test
    fun `only a bill paid in cash leaves the drawer`() = withBook { dao ->
        val supplier = dao.insertParty(Party(name = "Test Supplier", phone = null, isCustomer = false))
        dao.insertSupplierBill(null, SupplierBill(partyId = supplier, totalAmount = 5_000.0, isPaidInFull = true), emptyList())
        dao.insertSupplierBill(owed(supplier, 7_000.0), SupplierBill(partyId = supplier, totalAmount = 7_000.0), emptyList())
        dao.insertSupplierBill(
            owed(supplier, 8_000.0), SupplierBill(partyId = supplier, totalAmount = 8_000.0, isPaidInFull = true),
            emptyList(), paidBy = PaymentMethod.BANK
        )
        assertEquals("cash only", 5_000.0, DayCloseMath.drawerByTheBooks(dao).otherOut, 0.001)
        assertEquals("the credit bill alone is owed", -7_000.0, dao.liveBalance(supplier), 0.001)
        val bills = dao.observeBills().first().associateBy { it.totalAmount }
        assertNull(bills[5_000.0]!!.ledgerEntryId)
        assertNull(bills[7_000.0]!!.paidBy)
        assertEquals(PaymentMethod.BANK, bills[8_000.0]!!.paidBy)
    }

    @Test
    fun `a bank-paid bill edited or deleted takes its payment along`() = withBook { dao ->
        val supplier = dao.insertParty(Party(name = "Test Supplier", phone = null, isCustomer = false))
        dao.insertSupplierBill(
            owed(supplier, 8_000.0), SupplierBill(partyId = supplier, totalAmount = 8_000.0, isPaidInFull = true),
            emptyList(), paidBy = PaymentMethod.WALLET
        )
        val bill = dao.getBill(dao.observeBills().first().single().id)!!
        dao.editSupplierBill(bill.copy(totalAmount = 9_000.0), null)
        assertEquals("owed and paid move together", 0.0, dao.liveBalance(supplier), 0.001)
        dao.deleteSupplierBill(bill.id)
        assertEquals(0.0, dao.liveBalance(supplier), 0.001)
        assertEquals(0.0, DayCloseMath.drawerByTheBooks(dao).otherOut, 0.001)
    }

    @Test
    fun `a cash bill put on credit leaves the drawer and goes on the account`() = withBook { dao ->
        val supplier = dao.insertParty(Party(name = "Test Supplier", phone = null, isCustomer = false))
        dao.insertSupplierBill(null, SupplierBill(partyId = supplier, totalAmount = 8_000.0, isPaidInFull = true), emptyList())
        val id = dao.observeBills().first().single().id
        assertTrue(dao.billCashToCredit(id, null))
        assertFalse("only once", dao.billCashToCredit(id, null))
        assertEquals(0.0, DayCloseMath.drawerByTheBooks(dao).otherOut, 0.001)
        assertEquals(-8_000.0, dao.liveBalance(supplier), 0.001)
        assertFalse(dao.getBill(id)!!.isPaidInFull)
    }

    @Test
    fun `a drawer the books put below zero keeps its minus`() {
        val expected = DayCloseMath.expected(0.0, 9_000.0, 25_000.0)
        assertEquals(-16_000.0, expected, 0.001)
        assertTrue(Format.signedIfNegative(expected).startsWith("−"))
    }
}
