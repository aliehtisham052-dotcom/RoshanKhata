package com.innovation313.roshankhata.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The counter against a real database (POS Phase 1). The checks and
 * balances the plan promised, one test each: a ticket posts everything or
 * nothing; a customer's sale is in the khata once and in stock once; a
 * walk-in's money is in the cashbook with its method; a void reverses
 * exactly what was posted; a held ticket moves nothing; numbers never repeat.
 */
@RunWith(AndroidJUnit4::class)
class SalesDaoTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: KhataDatabase
    private lateinit var dao: KhataDao
    private var bilal = 0L
    private var dap = 0L
    private var confidor = 0L

    @Before
    fun open() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java).build()
        dao = db.khataDao()
        bilal = dao.insertParty(Party(name = "Bilal"))
        dap = dao.insertProduct(Product(name = "DAP", nameKey = "dap", normalisedName = "dap", defaultUnit = "bag", barcode = "890123"))
        confidor = dao.insertProduct(Product(name = "Confidor", nameKey = "confidor", normalisedName = "confidor", defaultUnit = "bottle"))
    }

    @After
    fun close() = db.close()

    private fun lines() = listOf(
        SaleItem(saleId = 0, productId = dap, itemName = "DAP", quantity = 3.0, unit = "bag", rate = 3500.0),
        SaleItem(saleId = 0, productId = confidor, itemName = "Confidor", quantity = 2.0, unit = "bottle", rate = 875.0)
    )
    // 10,500 + 1,750 = 12,250
    private fun ticket(partyId: Long? = null, note: String? = null) =
        Sale(partyId = partyId, customerName = if (partyId != null) "Bilal" else null, timestamp = 5_000L,
            subtotal = 0.0, grandTotal = 0.0, paidTotal = 0.0, note = note)

    @Test
    fun aCustomerSaleSplitBetweenCashAndTheAccount() = runBlocking {
        val id = dao.postSale(
            ticket(bilal), lines(),
            listOf(SalePayment(saleId = 0, method = PaymentMethod.CASH, amount = 10_000.0))
        )
        val sale = dao.getSale(id)!!
        assertEquals("S-000001", sale.saleNumber)
        assertEquals(12_250.0, sale.grandTotal, 0.001)
        assertEquals(10_000.0, sale.paidTotal, 0.001)
        assertEquals(2_250.0, sale.creditAmount, 0.001)
        assertNotNull(sale.ledgerEntryId)

        // The khata: one "I gave" of the whole ticket, one "I got" of the cash → owes 2,250.
        assertEquals(2_250.0, dao.partiesWithBalanceOnce().single().balance, 0.001)
        val gave = dao.getEntry(sale.ledgerEntryId!!)!!
        assertTrue(gave.isGiven); assertEquals(id, gave.saleId)
        assertEquals(2, dao.itemsOfEntry(gave.id).size)
        val got = dao.salePayments(id).single()
        assertEquals(PaymentMethod.CASH, dao.getEntry(got.ledgerEntryId!!)!!.paymentMethod)
        assertEquals(gave.id, dao.getEntry(got.ledgerEntryId!!)!!.pairedEntryId)
        assertNull("a customer's cash is in the khata, not the cashbook", got.cashEntryId)
        assertTrue(dao.allCashForBackup().isEmpty())

        // Stock: counted once, through the khata lines; the sale lines add nothing on top.
        val stock = Stock.of(dao)
        assertEquals(3.0, stock.single { it.productId == dap }.soldQty, 0.0)
        assertTrue(dao.walkInSoldPerProduct().isEmpty())
        assertEquals(AuditAction.SALE_POSTED, dao.latestAudit(1).single().action)
    }

    @Test
    fun aWalkInSaleIsCashbookAndStockOnly() = runBlocking {
        val id = dao.postSale(
            ticket(), lines(),
            listOf(
                SalePayment(saleId = 0, method = PaymentMethod.CASH, amount = 10_000.0),
                SalePayment(saleId = 0, method = PaymentMethod.ONLINE, amount = 2_250.0, reference = "TXN-77")
            )
        )
        assertTrue("no party, no khata", dao.partiesWithBalanceOnce().single().balance == 0.0)
        val cash = dao.allCashForBackup().sortedBy { it.id }
        assertEquals(2, cash.size)
        assertEquals(CashCategory.SALE, cash[0].category)
        assertEquals(PaymentMethod.CASH, cash[0].paymentMethod)
        assertEquals("TXN-77", cash[1].note)
        assertEquals(id, cash[1].saleId)
        // The drawer: only the cash half; the transfer is not in it.
        assertEquals(10_000.0, dao.cashbookCashBetween(true, 0, Long.MAX_VALUE), 0.001)
        assertEquals(12_250.0, dao.cashbookTotalBetween(true, 0, Long.MAX_VALUE), 0.001)
        // Stock: the sale lines are the movement.
        assertEquals(2.0, Stock.of(dao).single { it.productId == confidor }.soldQty, 0.0)
        assertEquals(2, dao.walkInSoldPerProduct().size)
    }

    @Test
    fun aWalkInMustPayInFullAndNothingIsWrittenOtherwise() = runBlocking {
        try {
            dao.postSale(ticket(), lines(), listOf(SalePayment(saleId = 0, method = PaymentMethod.CASH, amount = 10_000.0)))
            fail("a walk-in owing 2,250 must be refused")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("WALK_IN_UNPAID"))
        }
        assertTrue(dao.allSalesForBackup().isEmpty())
        assertTrue(dao.allCashForBackup().isEmpty())
        assertTrue(dao.allAuditForBackup().isEmpty())
    }

    @Test
    fun overpayingAndEmptyTicketsAreRefused() = runBlocking {
        try {
            dao.postSale(ticket(bilal), lines(), listOf(SalePayment(saleId = 0, method = PaymentMethod.CASH, amount = 20_000.0)))
            fail("overpaid")
        } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("OVERPAID")) }
        try {
            dao.postSale(ticket(bilal), emptyList(), emptyList())
            fail("no lines")
        } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("NO_LINES")) }
        assertEquals(0, dao.highestSaleNumber())
    }

    @Test
    fun theScreensTotalsAreNeverTrusted() = runBlocking {
        // A screen claiming a 1 rupee total for a 12,250 ticket: the book keeps 12,250.
        val id = dao.postSale(
            ticket(bilal).copy(subtotal = 1.0, grandTotal = 1.0, paidTotal = 1.0, saleNumber = "S-999999", status = SaleStatus.VOID),
            lines(), emptyList()
        )
        val sale = dao.getSale(id)!!
        assertEquals(12_250.0, sale.grandTotal, 0.001)
        assertEquals(0.0, sale.paidTotal, 0.001)
        assertEquals("S-000001", sale.saleNumber)
        assertEquals(SaleStatus.DONE, sale.status)
        assertEquals(12_250.0, dao.partiesWithBalanceOnce().single().balance, 0.001)
    }

    @Test
    fun discountTaxAndRounding() = runBlocking {
        val id = dao.postSale(
            ticket(bilal).copy(discountAmount = 250.0, taxPercent = 5.0), lines(), emptyList(), roundToWhole = true
        )
        val s = dao.getSale(id)!!
        // 12,250 − 250 = 12,000; +5% = 12,600.00; whole already.
        assertEquals(250.0, s.discountAmount, 0.001)
        assertEquals(600.0, s.taxAmount, 0.001)
        assertEquals(12_600.0, s.grandTotal, 0.001)
        val t = SaleMath.totals(
            listOf(SaleItem(saleId = 0, itemName = "x", quantity = 3.0, rate = 33.33)), 0.0, null, roundToWhole = true
        )
        assertEquals(99.99, t.subtotal, 0.001)
        assertEquals(100.0, t.grandTotal, 0.001)
        assertEquals(0.01, t.roundOff, 0.001)
    }

    @Test
    fun aVoidReversesExactlyWhatWasPostedAndKeepsTheNumber() = runBlocking {
        val id = dao.postSale(
            ticket(bilal), lines(), listOf(SalePayment(saleId = 0, method = PaymentMethod.BANK, amount = 2_250.0))
        )
        assertEquals(10_000.0, dao.partiesWithBalanceOnce().single().balance, 0.001)
        assertTrue(dao.voidSale(id, "wrong customer"))
        assertEquals(0.0, dao.partiesWithBalanceOnce().single().balance, 0.001)
        assertTrue("stock comes back", Stock.of(dao).all { it.soldQty == 0.0 })
        val sale = dao.getSale(id)!!
        assertEquals(SaleStatus.VOID, sale.status)
        assertEquals("wrong customer", sale.voidReason)
        // Binned, not erased: the entries are still there for the bin to show.
        assertTrue(dao.getEntry(sale.ledgerEntryId!!)!!.isDeleted)
        assertEquals(2, dao.allEntriesForBackup().size)
        assertFalse("a void cannot be voided twice", dao.voidSale(id, "again"))
        // The next ticket takes the next number, never the voided one.
        val next = dao.postSale(ticket(), lines(), listOf(SalePayment(saleId = 0, method = PaymentMethod.CASH, amount = 12_250.0)))
        assertEquals("S-000002", dao.getSale(next)!!.saleNumber)
        assertEquals(AuditAction.SALE_VOIDED, dao.allAuditForBackup()[1].action)
    }

    @Test
    fun aHeldTicketMovesNothingUntilItIsRungUp() = runBlocking {
        val held = dao.holdSale(ticket(bilal), lines())
        assertEquals("", dao.getSale(held)!!.saleNumber)
        assertEquals(0.0, dao.partiesWithBalanceOnce().single().balance, 0.001)
        assertTrue(Stock.of(dao).all { it.soldQty == 0.0 })
        assertEquals(1, dao.observeHeldCount().firstValue())
        dao.dropHeldSale(held)
        assertEquals(0, dao.observeHeldCount().firstValue())
    }

    @Test
    fun barcodeAndCostFallBack() = runBlocking {
        assertEquals(dap, dao.productByBarcode("890123")!!.id)
        assertNull(dao.productByBarcode("000"))
        assertNull(dao.lastPurchaseRate(dap))
        val supplier = dao.insertParty(Party(name = "Engro", isCustomer = false))
        val tied = dao.insertSupplierBill(null, SupplierBill(partyId = supplier, billNumber = "B1", billDate = 100L, totalAmount = 30_000.0),
            listOf(BillItem(billId = 0, productName = "DAP", quantity = 10.0, unit = "bag", rate = 3000.0, productId = dap)))
        assertEquals(dap, tied.single().id)
        assertEquals(3000.0, dao.lastPurchaseRate(dap)!!, 0.001)
    }

    @Test
    fun aBackupCarriesTheCounterAndRestoresIt() = runBlocking {
        val id = dao.postSale(ticket(bilal, note = "seed"), lines(), listOf(SalePayment(saleId = 0, method = PaymentMethod.CASH, amount = 250.0)))
        dao.holdSale(ticket(), lines())
        val out = java.io.StringWriter()
        BackupCodec.exportTo(dao, "Test", org.json.JSONObject(), out)
        val (result, parsed) = BackupCodec.parseText(out.toString())
        assertTrue(result is BackupCodec.ImportResult.Ok)
        assertEquals(2, parsed!!.sales.size)
        assertEquals(4, parsed.saleItems.size)
        assertEquals(1, parsed.salePayments.size)
        assertEquals(2, parsed.audit.size)
        assertEquals(id, parsed.entries.first { it.isGiven }.saleId)

        val other = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java).build()
        try {
            BackupCodec.restoreLedger(other.khataDao(), parsed)
            val back = other.khataDao().getSale(id)!!
            assertEquals("S-000001", back.saleNumber)
            assertEquals(12_000.0, other.khataDao().partiesWithBalanceOnce().single().balance, 0.001)
            assertEquals(1, other.khataDao().observeHeldCount().firstValue())
        } finally {
            other.close()
        }
    }

    private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.firstValue(): T = kotlinx.coroutines.flow.first()
}
