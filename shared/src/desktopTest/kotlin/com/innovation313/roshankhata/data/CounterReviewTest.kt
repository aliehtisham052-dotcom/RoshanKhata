package com.innovation313.roshankhata.data

import kotlinx.coroutines.flow.first
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
import java.io.File
import java.nio.file.Files

/**
 * The counter's money and stock after the 22 Oct 2026 review, on a real book:
 * a merged customer takes his tickets along; a voided ticket's rows never come
 * back from the bin; two lines of one product share its batches; a ticket's
 * cash line is changed from the ticket; a customer with tickets is not binned
 * or purged; a customer the bin keeps keeps his entries; nothing is posted to
 * a customer in the bin or returned against a voided ticket; a closed day's
 * return stays; a held ticket comes off the hook with its sale.
 */
class CounterReviewTest {

    private lateinit var dir: File
    private lateinit var db: KhataDatabase
    private val dao get() = db.khataDao()
    private val day = 24L * 60 * 60 * 1000
    private val now = System.currentTimeMillis()

    @Before
    fun open() {
        dir = Files.createTempDirectory("rk-counter-review").toFile()
        db = DesktopDatabase.open(dir.resolve("khata.db"))
    }

    @After
    fun close() {
        db.close()
        dir.deleteRecursively()
    }

    private suspend fun product(name: String, price: Double = 1000.0) = dao.insertProduct(
        Product(name = name, nameKey = ProductName.key(name), normalisedName = ProductName.normalised(name), defaultUnit = "bottle", salePrice = price)
    )

    private suspend fun sellTo(partyId: Long?, productId: Long, qty: Double, cash: Double? = null): Long =
        dao.postSale(
            Sale(partyId = partyId, customerName = partyId?.let { dao.getParty(it)?.name }, timestamp = now, subtotal = 0.0, grandTotal = 0.0, paidTotal = 0.0),
            listOf(SaleItem(saleId = 0, productId = productId, itemName = dao.productById(productId)!!.name, quantity = qty, unit = "bottle", rate = 1000.0)),
            listOfNotNull((cash ?: if (partyId == null) qty * 1000.0 else null)?.let { SalePayment(saleId = 0, method = PaymentMethod.CASH, amount = it) })
        )

    @Test
    fun `a merged customer takes his tickets, returns, points, orders and schemes along`() = runBlocking {
        val kept = dao.insertParty(Party(name = "Aslam"))
        val twice = dao.insertParty(Party(name = "Aslam", phone = "0300"))
        val dap = product("DAP")
        val sale = sellTo(twice, dap, 2.0)
        dao.insertLoyalty(LoyaltyEntry(partyId = twice, saleId = sale, points = 50.0))
        dao.insertOrder(PurchaseOrder(partyId = twice))
        dao.saveScheme(Scheme(name = "Offer", partyId = twice, startDate = 0, endDate = 1, measure = SchemeMath.BY_VALUE, rewardKind = SchemeMath.PERCENT, target1 = 1.0, reward1 = 1.0))
        dao.mergeParty(twice, kept)
        assertEquals(kept, dao.getSale(sale)!!.partyId)
        assertEquals(50.0, dao.loyaltyBalance(kept), 0.001)
        assertTrue(dao.allOrdersForBackup().all { it.partyId == kept })
        assertTrue(dao.allSchemesForBackup().all { it.partyId == kept })
        // A return now goes to the customer kept, and counts there.
        val sold = dao.saleItems(sale).single()
        dao.postReturn(
            SaleReturn(saleId = sale, partyId = kept, total = 0.0, refundMethod = SaleReturn.ACCOUNT, reason = "damaged"),
            listOf(SaleReturnItem(returnId = 0, saleItemId = sold.id, productId = dap, itemName = "DAP", quantity = 2.0, unit = "bottle", rate = 1000.0))
        )
        assertEquals(0.0, dao.partiesWithBalanceOnce().single { it.id == kept }.balance, 0.001)
    }

    @Test
    fun `a voided ticket's rows are neither listed in the bin nor brought back from it`() = runBlocking {
        val aslam = dao.insertParty(Party(name = "Aslam"))
        val sale = sellTo(aslam, product("Karate"), 3.0, cash = 1000.0)
        val gave = dao.getSale(sale)!!.ledgerEntryId!!
        assertTrue(dao.voidSale(sale, "wrong customer"))
        assertTrue(dao.observeDeletedEntries().first().none { it.id == gave })
        dao.restoreEntry(gave)
        assertTrue(dao.getEntry(gave)!!.isDeleted)
        assertEquals(0.0, dao.partiesWithBalanceOnce().single().balance, 0.001)
        // An entry the owner deleted himself still comes back as before.
        val own = dao.insertEntryNumbered(LedgerEntry(partyId = aslam, amount = 200.0, isGiven = true, entryNumber = ""))
        dao.softDeleteEntry(own)
        assertTrue(dao.observeDeletedEntries().first().any { it.id == own })
        dao.restoreEntry(own)
        assertFalse(dao.getEntry(own)!!.isDeleted)
    }

    @Test
    fun `two lines of one product share its batches, and an expired batch behind them is refused`() = runBlocking {
        val engro = dao.insertParty(Party(name = "Engro", isCustomer = false))
        val karate = dao.insertProduct(
            Product(name = "Karate", nameKey = ProductName.key("Karate"), normalisedName = ProductName.normalised("Karate"), defaultUnit = "bottle",
                salePrice = 1000.0, packUnit = "carton", packSize = 6.0)
        )
        dao.insertSupplierBill(
            null, SupplierBill(partyId = engro, billDate = now - 30 * day, totalAmount = 0.0),
            listOf(
                BillItem(billId = 0, productName = "Karate", batchNumber = "FRESH", expiryDate = now + 100 * day, quantity = 12.0, unit = "bottle", rate = 500.0, productId = karate),
                BillItem(billId = 0, productName = "Karate", batchNumber = "OLD", expiryDate = now - 5 * day, quantity = 20.0, unit = "bottle", rate = 500.0, productId = karate)
            )
        )
        val p = dao.productById(karate)!!
        // A carton (6) and 7 loose bottles: the fresh batch holds 12, the 13th is only expired stock.
        val draft = CounterDraft()
        draft.add(p, qty = 1.0).switchUnit(draft.unitRate(p))
        draft.add(p, qty = 7.0)
        assertEquals(2, draft.lines.size)
        draft.payRestInCash()
        try {
            Counter.post(dao, draft)
            fail("13 bottles from a fresh batch of 12")
        } catch (e: Counter.ExpiredStock) {
            assertEquals(6.0, e.fresh, 0.001)
        }
        // A carton and 6 bottles fit: both lines from the fresh batch, which is then empty, never below it.
        val ok = CounterDraft()
        ok.add(p, qty = 1.0).switchUnit(ok.unitRate(p))
        ok.add(p, qty = 6.0)
        ok.payRestInCash()
        val sale = Counter.post(dao, ok)
        val fresh = dao.batchesOfProduct(karate).first { it.batchNumber == "FRESH" }.id
        assertTrue(dao.saleItems(sale).all { it.billItemId == fresh })
        assertEquals(0.0, Fefo.lots(dao, p).first { it.billItemId == fresh }.left, 0.001)
    }

    @Test
    fun `a ticket's cash line is changed from the ticket, the owner's own from the cashbook`() = runBlocking {
        val sale = sellTo(null, product("Urea"), 2.0)
        val line = dao.allCashForBackup().single { it.saleId == sale }
        assertEquals("sale", dao.cashOwner(line.id))
        assertFalse(dao.softDeleteOwnCashEntry(line.id))
        assertFalse(dao.allCashForBackup().single { it.id == line.id }.isDeleted)
        val tea = dao.insertCashEntry(CashEntry(amount = 50.0, isIncome = false, category = "Tea"))
        assertNull(dao.cashOwner(tea))
        assertTrue(dao.softDeleteOwnCashEntry(tea, "50.00"))
        assertTrue(dao.allCashForBackup().single { it.id == tea }.isDeleted)
        assertEquals(AuditAction.CASH_DELETED, dao.latestAudit(1).single().action)
    }

    @Test
    fun `a customer with posted tickets is not binned, nor purged once in the bin`() = runBlocking {
        val aslam = dao.insertParty(Party(name = "Aslam"))
        val bilal = dao.insertParty(Party(name = "Bilal"))
        sellTo(aslam, product("DAP"), 1.0)
        val kept = dao.softDeleteParties(listOf(aslam, bilal))
        assertEquals(listOf(aslam), kept)
        assertFalse(dao.getParty(aslam)!!.isDeleted)
        assertTrue(dao.getParty(bilal)!!.isDeleted)
        // A ticket voided, the customer binned: the purge still keeps him, so the ticket's record keeps its customer.
        val voided = dao.allSalesForBackup().single()
        assertTrue(dao.voidSale(voided.id, "test"))
        assertTrue(dao.softDeleteParties(listOf(aslam)).isEmpty())
        dao.purgeAllParties()
        assertNotNull(dao.getParty(aslam))
        assertNull(dao.getParty(bilal))
        // Entries picked by hand: a ticket's own are left, and counted.
        val carol = dao.insertParty(Party(name = "Carol"))
        val t = sellTo(carol, product("Urea"), 1.0)
        val own = dao.insertEntryNumbered(LedgerEntry(partyId = carol, amount = 10.0, isGiven = true, entryNumber = ""))
        assertEquals(1, dao.softDeleteOwnEntries(listOf(dao.getSale(t)!!.ledgerEntryId!!, own)))
        assertTrue(dao.getEntry(own)!!.isDeleted)
        assertFalse(dao.getEntry(dao.getSale(t)!!.ledgerEntryId!!)!!.isDeleted)
    }

    @Test
    fun `a customer the bin keeps keeps his entries, and comes back whole`() = runBlocking {
        val engro = dao.insertParty(Party(name = "Engro", isCustomer = false))
        dao.insertSupplierBill(
            LedgerEntry(partyId = engro, amount = 5000.0, isGiven = false, entryNumber = ""),
            SupplierBill(partyId = engro, totalAmount = 5000.0), emptyList()
        )
        dao.insertEntryNumbered(LedgerEntry(partyId = engro, amount = 2000.0, isGiven = true, entryNumber = ""))
        val aslam = dao.insertParty(Party(name = "Aslam"))
        val own = dao.insertEntryNumbered(LedgerEntry(partyId = aslam, amount = 300.0, isGiven = true, entryNumber = ""))
        dao.softDeleteEntry(own)
        val before = dao.partiesWithBalanceOnce().single { it.id == engro }.balance
        assertTrue(dao.softDeleteParties(listOf(engro)).isEmpty())
        // Empty the bin: the entry binned on its own goes; the supplier, kept by his bill, keeps every entry.
        dao.purgeAllEntries(); dao.purgeAllParties()
        assertNull(dao.getEntry(own))
        assertEquals(1, dao.binnedPartyCount())
        assertEquals(0, dao.purgeParty(engro))
        // The 30-day purge alike.
        dao.purgeOldEntries(Long.MAX_VALUE); dao.purgeOldParties(Long.MAX_VALUE)
        val binned = dao.getParty(engro)!!
        dao.restoreParty(engro)
        dao.restoreEntriesOfParty(engro, binned.deletedAt!!)
        assertEquals(before, dao.partiesWithBalanceOnce().single { it.id == engro }.balance, 0.001)
        // A customer with nothing else goes, his entries with him.
        val bilal = dao.insertParty(Party(name = "Bilal"))
        val b = dao.insertEntryNumbered(LedgerEntry(partyId = bilal, amount = 100.0, isGiven = true, entryNumber = ""))
        dao.softDeleteParties(listOf(bilal))
        assertEquals(1, dao.purgeParty(bilal))
        assertNull(dao.getEntry(b))
    }

    @Test
    fun `nothing is posted to a customer in the bin, nor returned against a voided ticket`() = runBlocking {
        val aslam = dao.insertParty(Party(name = "Aslam"))
        val dap = product("DAP")
        dao.softDeleteParty(aslam)
        try { sellTo(aslam, dap, 1.0); fail("a customer in the bin") } catch (e: IllegalArgumentException) {}
        dao.restoreParty(aslam)
        val sale = sellTo(aslam, dap, 1.0)
        val sold = dao.saleItems(sale).single()
        assertTrue(dao.voidSale(sale, "cancelled"))
        try {
            dao.postReturn(
                SaleReturn(saleId = sale, partyId = aslam, total = 0.0, refundMethod = SaleReturn.ACCOUNT, reason = "x"),
                listOf(SaleReturnItem(returnId = 0, saleItemId = sold.id, productId = dap, itemName = "DAP", quantity = 1.0, unit = "bottle", rate = 1000.0))
            )
            fail("returned against a voided ticket")
        } catch (e: IllegalArgumentException) {}
    }

    @Test
    fun `a closed day's return is not voided`() = runBlocking {
        val aslam = dao.insertParty(Party(name = "Aslam"))
        val dap = product("DAP")
        val sale = sellTo(aslam, dap, 2.0)
        val sold = dao.saleItems(sale).single()
        val r = dao.postReturn(
            SaleReturn(saleId = sale, partyId = aslam, timestamp = now, total = 0.0, refundMethod = SaleReturn.ACCOUNT, reason = "x"),
            listOf(SaleReturnItem(returnId = 0, saleItemId = sold.id, productId = dap, itemName = "DAP", quantity = 1.0, unit = "bottle", rate = 1000.0))
        )
        dao.saveDayClose(DayClose(day = DayCloseMath.dayOf(now), opening = 0.0, cashIn = 0.0, cashOut = 0.0, counted = 0.0, closedAt = now + 1000))
        assertEquals(SaleMath.VoidRefusal.DAY_CLOSED, dao.returnVoidRefusal(r))
        assertFalse(dao.voidReturn(r, "too late"))
        assertEquals(SaleStatus.DONE, dao.getReturn(r)!!.status)
    }

    @Test
    fun `a held ticket comes off the hook with its sale, in one step`() = runBlocking {
        val p = dao.productById(product("Urea"))!!
        val draft = CounterDraft()
        draft.add(p, qty = 1.0)
        val held = Counter.hold(dao, draft)
        assertEquals(1, dao.heldSalesOnce().size)
        val again = CounterDraft()
        again.add(p, qty = 1.0)
        again.heldId = held
        again.payRestInCash()
        Counter.post(dao, again)
        assertTrue(dao.heldSalesOnce().isEmpty())
    }
}
