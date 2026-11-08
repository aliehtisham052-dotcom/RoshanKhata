package com.innovation313.roshankhata.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/**
 * The Recycle Bin's five newer kinds (8 Nov 2026, the owner's phone test): cashbook lines, cheques, supplier bills,
 * documents and schemes are listed, restored by undoing exactly what their delete did, and swept. Made-up names and figures.
 */
class BinKindsTest {

    private fun <T> withBook(block: suspend (KhataDao) -> T): T {
        val dir = Files.createTempDirectory("rk-bin-kinds").toFile()
        val db = DesktopDatabase.open(dir.resolve("khata.db"))
        return try {
            runBlocking { block(db.khataDao()) }
        } finally {
            db.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a deleted supplier bill comes back with its debt, and goes for good with it`() = withBook { dao ->
        val supplier = dao.insertParty(Party(name = "Test Supplier", phone = null, isCustomer = false))
        dao.insertSupplierBill(
            LedgerEntry(partyId = supplier, amount = 8_000.0, isGiven = false, entryNumber = ""),
            SupplierBill(partyId = supplier, billNumber = "7", totalAmount = 8_000.0, isPaidInFull = false),
            emptyList()
        )
        val bill = dao.observeBills().first().single()
        val entry = dao.latestEntriesWithItems(supplier, 10).single().entry.id
        dao.deleteSupplierBill(bill.id)
        assertEquals(listOf(bill.id), dao.observeDeletedBills().first().map { it.id })
        assertEquals("the debt is out with the bill", 0.0, dao.liveBalance(supplier), 0.001)

        assertTrue(dao.restoreSupplierBill(bill.id))
        assertEquals(1, dao.observeBills().first().size)
        assertEquals("the debt is back", -8_000.0, dao.liveBalance(supplier), 0.001)
        assertTrue(dao.observeDeletedBills().first().isEmpty())

        dao.deleteSupplierBill(bill.id)
        dao.purgeSupplierBill(bill.id)
        assertNull(dao.getBill(bill.id))
        assertNull("its binned debt entry goes with it", dao.getEntry(entry))
    }

    @Test
    fun `a bill whose debt entry is gone for good is not restored`() = withBook { dao ->
        val supplier = dao.insertParty(Party(name = "Test Supplier", phone = null, isCustomer = false))
        dao.insertSupplierBill(
            LedgerEntry(partyId = supplier, amount = 500.0, isGiven = false, entryNumber = ""),
            SupplierBill(partyId = supplier, billNumber = "8", totalAmount = 500.0, isPaidInFull = false),
            emptyList()
        )
        val bill = dao.observeBills().first().single()
        val entry = dao.latestEntriesWithItems(supplier, 10).single().entry.id
        dao.deleteSupplierBill(bill.id)
        dao.purgeEntryRow(entry)
        assertFalse(dao.restoreSupplierBill(bill.id))
        assertTrue("still in the bin", dao.getBill(bill.id)!!.isDeleted)
    }

    @Test
    fun `an old binned entry of a bill is not swept while its bill is in the bin`() = withBook { dao ->
        val supplier = dao.insertParty(Party(name = "Test Supplier", phone = null, isCustomer = false))
        dao.insertSupplierBill(
            LedgerEntry(partyId = supplier, amount = 900.0, isGiven = false, entryNumber = ""),
            SupplierBill(partyId = supplier, billNumber = "9", totalAmount = 900.0, isPaidInFull = false),
            emptyList()
        )
        val bill = dao.observeBills().first().single()
        val entry = dao.latestEntriesWithItems(supplier, 10).single().entry.id
        dao.deleteSupplierBill(bill.id, now = 1_000L)
        dao.purgeOldEntries(cutoff = 5_000L)
        assertNotNull("the bill's debt waits with its bill", dao.getEntry(entry))
        assertTrue(dao.restoreSupplierBill(bill.id))
    }

    @Test
    fun `a cheque returns with its status, a cleared one only while its payment stands`() = withBook { dao ->
        val party = dao.insertParty(Party(name = "Test Customer", phone = null, isCustomer = true))
        val pending = dao.insertCheque(Cheque(partyId = party, amount = 1_000.0, isReceived = true, dueDate = 1L))
        dao.softDeleteCheque(pending)
        assertEquals(listOf(pending), dao.observeDeletedCheques().first().map { it.id })
        assertTrue(dao.restoreCheque(pending))
        assertEquals(ChequeStatus.PENDING, dao.getCheque(pending)!!.status)
        assertFalse(dao.getCheque(pending)!!.isDeleted)

        val cleared = dao.insertCheque(Cheque(partyId = party, amount = 2_000.0, isReceived = true, dueDate = 1L))
        val payment = dao.clearCheque(cleared, "Cheque")!!
        dao.softDeleteCheque(cleared)
        dao.softDeleteEntry(payment)
        assertFalse("cleared, but its payment is in the bin", dao.restoreCheque(cleared))
        dao.restoreEntry(payment)
        assertTrue(dao.restoreCheque(cleared))
        assertEquals(ChequeStatus.CLEARED, dao.getCheque(cleared)!!.status)
    }

    @Test
    fun `a document returns with its status and its source goes back to converted`() = withBook { dao ->
        val quote = dao.saveInvoiceWithItems(
            Invoice(customerName = "Test Customer", kind = DocKind.QUOTATION),
            listOf(InvoiceItem(invoiceId = 0, itemName = "Test item", quantity = 2.0, rate = 50.0))
        )
        val order = dao.convertDocument(quote, DocKind.SALES_ORDER)
        assertEquals(DocStatus.CONVERTED, dao.getInvoice(quote)!!.status)
        assertTrue(dao.deleteDocument(order))
        assertEquals("the quotation is open again", DocStatus.OPEN, dao.getInvoice(quote)!!.status)
        assertEquals(listOf(order), dao.observeDeletedInvoices().first().map { it.id })

        assertTrue(dao.restoreDocument(order))
        assertEquals(DocStatus.OPEN, dao.getInvoice(order)!!.status)
        assertFalse(dao.getInvoice(order)!!.isDeleted)
        assertEquals("the quotation stands converted again", DocStatus.CONVERTED, dao.getInvoice(quote)!!.status)

        assertTrue(dao.deleteDocument(order))
        assertTrue(dao.purgeDocument(order))
        assertNull(dao.getInvoice(order))
        assertNull("nothing points at a document that is gone", dao.getInvoice(quote)!!.sourceDocId)
    }

    @Test
    fun `a cashbook line and a scheme come back, and Empty bin takes the five kinds`() = withBook { dao ->
        val cash = dao.insertCashEntry(CashEntry(amount = 50.0, isIncome = true, category = "Misc"))
        assertTrue(dao.softDeleteOwnCashEntry(cash))
        assertEquals(listOf(cash), dao.observeDeletedCash().first().map { it.id })
        assertEquals(1, dao.restoreCashRow(cash))
        assertEquals(1, dao.observeCashEntries().first().size)

        val scheme = dao.saveScheme(
            Scheme(
                name = "Test scheme", startDate = 0L, endDate = 1L, measure = SchemeMath.BY_VALUE,
                rewardKind = SchemeMath.PERCENT, target1 = 1.0, reward1 = 1.0
            )
        )
        dao.deleteScheme(scheme)
        assertEquals(listOf(scheme), dao.observeDeletedSchemes().first().map { it.id })
        assertEquals(1, dao.restoreScheme(scheme))
        assertTrue(dao.observeDeletedSchemes().first().isEmpty())

        // Empty bin: every kind, schemes too.
        dao.softDeleteOwnCashEntry(cash)
        dao.deleteScheme(scheme)
        dao.purgeBinKinds(0L, true)
        assertTrue(dao.observeDeletedCash().first().isEmpty())
        assertTrue(dao.observeDeletedSchemes().first().isEmpty())
    }

    @Test
    fun `the 30-day sweep takes only what was binned before the cutoff`() = withBook { dao ->
        val party = dao.insertParty(Party(name = "Test Customer", phone = null, isCustomer = true))
        val old = dao.insertCheque(Cheque(partyId = party, amount = 10.0, isReceived = true, dueDate = 1L))
        val fresh = dao.insertCheque(Cheque(partyId = party, amount = 20.0, isReceived = true, dueDate = 1L))
        dao.softDeleteCheque(old, now = 1_000L)
        dao.softDeleteCheque(fresh, now = 9_000L)
        dao.purgeBinKinds(cutoff = 5_000L, everything = false)
        assertNull(dao.getCheque(old))
        assertNotNull(dao.getCheque(fresh))
    }
}
