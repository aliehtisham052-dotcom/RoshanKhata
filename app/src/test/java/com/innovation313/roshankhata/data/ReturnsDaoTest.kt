package com.innovation313.roshankhata.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Returns against a real database (POS Phase 3): the checks, the postings, the void, the file. */
@RunWith(AndroidJUnit4::class)
class ReturnsDaoTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: KhataDatabase
    private lateinit var dao: KhataDao
    private var bilal = 0L
    private var dap = 0L
    private var saleId = 0L

    @Before
    fun open(): Unit = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java).build()
        dao = db.khataDao()
        bilal = dao.insertParty(Party(name = "Bilal"))
        dap = dao.insertProduct(Product(name = "DAP", nameKey = "dap", normalisedName = "dap", defaultUnit = "bag"))
        saleId = dao.postSale(
            Sale(partyId = bilal, customerName = "Bilal", timestamp = 5_000L, subtotal = 0.0, grandTotal = 0.0, paidTotal = 0.0),
            listOf(SaleItem(saleId = 0, productId = dap, itemName = "DAP", quantity = 3.0, unit = "bag", rate = 3500.0)),
            listOf(SalePayment(saleId = 0, method = PaymentMethod.CASH, amount = 10_500.0))
        )
    }

    @After
    fun close() = db.close()

    private fun line(qty: Double) = runBlocking {
        val sold = dao.saleItems(saleId).single()
        SaleReturnItem(returnId = 0, saleItemId = sold.id, productId = dap, itemName = "DAP", quantity = qty, unit = "bag", rate = 3500.0)
    }

    @Test
    fun aCustomerReturnWithCashBackReversesStockAndMoney() = runBlocking {
        assertEquals(3.0, Stock.of(dao).single().soldQty, 0.0)
        val id = dao.postReturn(
            SaleReturn(saleId = saleId, partyId = bilal, customerName = "Bilal", timestamp = 6_000L, total = 0.0,
                refundMethod = PaymentMethod.CASH, refundAmount = 3500.0, reason = "damaged bag"),
            listOf(line(1.0))
        )
        val r = dao.getReturn(id)!!
        assertEquals("R-000001", r.returnNumber)
        assertEquals(3500.0, r.total, 0.001)
        // Stock: 3 sold, 1 back → 2 on hand's worth of selling.
        val st = Stock.of(dao).single()
        assertEquals(1.0, st.returnedQty, 0.0)
        assertEquals(-2.0, st.onHand!!, 0.0)
        // Money: the sale was fully paid; goods back + cash out leaves the balance at 0.
        assertEquals(0.0, dao.partiesWithBalanceOnce().single().balance, 0.001)
        assertEquals(PaymentMethod.CASH, dao.getEntry(r.refundEntryId!!)!!.paymentMethod)
        assertEquals(1.0, dao.returnedOfSale(saleId).single().qty, 0.0)
        assertEquals(AuditAction.RETURN_POSTED, dao.latestAudit(1).single().action)
    }

    @Test
    fun creditStaysOnTheAccountWhenNothingIsHandedBack() = runBlocking {
        dao.postReturn(
            SaleReturn(saleId = saleId, partyId = bilal, timestamp = 6_000L, total = 0.0, refundMethod = SaleReturn.ACCOUNT, reason = "exchange"),
            listOf(line(2.0))
        )
        // Paid 10,500 for 3; 2 back → the shop owes him 7,000.
        assertEquals(-7000.0, dao.partiesWithBalanceOnce().single().balance, 0.001)
    }

    @Test
    fun moreThanWasSoldIsRefusedAndAWalkInIsPaidInCash() = runBlocking {
        try {
            dao.postReturn(SaleReturn(saleId = saleId, partyId = bilal, total = 0.0, refundMethod = SaleReturn.ACCOUNT, reason = "x"), listOf(line(4.0)))
            fail("4 of 3")
        } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("OVER_SOLD")) }
        dao.postReturn(SaleReturn(saleId = saleId, partyId = bilal, total = 0.0, refundMethod = SaleReturn.ACCOUNT, reason = "x"), listOf(line(2.0)))
        try {
            dao.postReturn(SaleReturn(saleId = saleId, partyId = bilal, total = 0.0, refundMethod = SaleReturn.ACCOUNT, reason = "x"), listOf(line(2.0)))
            fail("2 more of the 1 left")
        } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("OVER_SOLD")) }
        try {
            dao.postReturn(SaleReturn(saleId = saleId, partyId = bilal, total = 0.0, refundMethod = SaleReturn.ACCOUNT, reason = " "), listOf(line(1.0)))
            fail("no reason")
        } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("NO_REASON")) }
        // A walk-in: no account to hold credit, so the money goes out of the cashbook.
        try {
            dao.postReturn(SaleReturn(total = 0.0, refundMethod = SaleReturn.ACCOUNT, reason = "x"), listOf(SaleReturnItem(returnId = 0, productId = dap, itemName = "DAP", quantity = 1.0, rate = 3500.0)))
            fail("walk-in on account")
        } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("WALK_IN_NEEDS_CASH")) }
        val wid = dao.postReturn(
            SaleReturn(total = 0.0, refundMethod = PaymentMethod.CASH, refundAmount = 3500.0, reason = "x"),
            listOf(SaleReturnItem(returnId = 0, productId = dap, itemName = "DAP", quantity = 1.0, unit = "bag", rate = 3500.0))
        )
        val cash = dao.allCashForBackup().single()
        assertEquals(CashCategory.RETURN, cash.category); assertEquals(3500.0, cash.amount, 0.0); assertTrue(!cash.isIncome)
        assertEquals(3500.0, dao.cashbookCashBetween(false, 0, Long.MAX_VALUE), 0.001)
        assertEquals(1.0, dao.walkInReturnedPerProduct().single().qty, 0.0)
        assertEquals(3.0, Stock.of(dao).single().returnedQty, 0.0)
        assertEquals("R-000002", dao.getReturn(wid)!!.returnNumber)
    }

    @Test
    fun aVoidPutsEverythingBackAndTheFileCarriesReturns() = runBlocking {
        val id = dao.postReturn(
            SaleReturn(saleId = saleId, partyId = bilal, total = 0.0, refundMethod = PaymentMethod.BANK, refundAmount = 3500.0, reason = "wrong"),
            listOf(line(1.0))
        )
        assertTrue(dao.voidReturn(id, "mistake"))
        assertEquals(0.0, dao.partiesWithBalanceOnce().single().balance, 0.001)
        assertEquals(0.0, Stock.of(dao).single().returnedQty, 0.0)
        assertTrue(dao.returnedOfSale(saleId).isEmpty())
        assertTrue(!dao.voidReturn(id, "again"))
        val out = java.io.StringWriter()
        BackupCodec.exportTo(dao, "T", org.json.JSONObject(), out)
        val (res, parsed) = BackupCodec.parseText(out.toString())
        assertTrue(res is BackupCodec.ImportResult.Ok)
        assertEquals(1, parsed!!.returns.size); assertEquals(1, parsed.returnItems.size)
        assertEquals(SaleStatus.VOID, parsed.returns.single().status)
    }

    /** A version-32 file opens at 33: the two tables arrive, the old rows survive. */
    @Test
    fun aVersion32BookOpensAt33() = runBlocking {
        val name = "migration_32_33_test.db"
        context.deleteDatabase(name)
        val file = Room.databaseBuilder(context, KhataDatabase::class.java, name).addMigrations(*ALL_MIGRATIONS).build()
        val a = file.khataDao().insertParty(Party(name = "Ahmad"))
        file.khataDao().postSale(Sale(partyId = a, subtotal = 0.0, grandTotal = 0.0, paidTotal = 0.0),
            listOf(SaleItem(saleId = 0, itemName = "Urea", quantity = 1.0, rate = 100.0)), emptyList())
        file.close()
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
            SchemaRewind.undo33(raw)
            raw.version = 32
        }
        val again = Room.databaseBuilder(context, KhataDatabase::class.java, name).addMigrations(*ALL_MIGRATIONS).build()
        try {
            assertEquals(100.0, again.khataDao().partiesWithBalanceOnce().single().balance, 0.001)
            assertTrue(again.khataDao().allReturnsForBackup().isEmpty())
            val rid = again.khataDao().postReturn(
                SaleReturn(partyId = a, total = 0.0, refundMethod = SaleReturn.ACCOUNT, reason = "r"),
                listOf(SaleReturnItem(returnId = 0, itemName = "Urea", quantity = 1.0, rate = 100.0))
            )
            assertEquals("R-000001", again.khataDao().getReturn(rid)!!.returnNumber)
        } finally {
            again.close(); context.deleteDatabase(name)
        }
    }
}
