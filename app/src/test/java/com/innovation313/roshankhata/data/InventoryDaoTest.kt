package com.innovation313.roshankhata.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Stock counts, adjustments, valuation and purchase orders against a real database (POS Phase 6). */
@RunWith(AndroidJUnit4::class)
class InventoryDaoTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: KhataDatabase
    private lateinit var dao: KhataDao
    private var engro = 0L
    private var dap = 0L
    private var urea = 0L

    @Before
    fun open() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java).build()
        dao = db.khataDao()
        engro = dao.insertParty(Party(name = "Engro", isCustomer = false))
        dap = dao.insertProduct(Product(name = "DAP", nameKey = "dap", normalisedName = "dap", defaultUnit = "bag", costPrice = 3000.0, lowStockLevel = 5.0))
        urea = dao.insertProduct(Product(name = "Urea", nameKey = "urea", normalisedName = "urea", defaultUnit = "bag"))
        dao.insertSupplierBill(null, SupplierBill(partyId = engro, billNumber = "B1", billDate = 100L, totalAmount = 0.0),
            listOf(
                BillItem(billId = 0, productName = "DAP", quantity = 10.0, unit = "bag", rate = 3100.0, productId = dap),
                BillItem(billId = 0, productName = "Urea", quantity = 20.0, unit = "bag", rate = 4800.0, productId = urea)
            ))
    }

    @After
    fun close() = db.close()

    @Test
    fun aCountWritesOnlyTheDifferencesAndTheShelfFollows() = runBlocking {
        assertEquals(10.0, Stock.of(dao).first { it.productId == dap }.onHand!!, 0.0)
        val n = dao.recordCount(mapOf(dap to 8.0, urea to 20.0), "October count")
        assertEquals(1, n)
        assertEquals(8.0, Stock.of(dao).first { it.productId == dap }.onHand!!, 0.0)
        assertEquals(20.0, Stock.of(dao).first { it.productId == urea }.onHand!!, 0.0)
        val row = dao.allAdjustmentsForBackup().single()
        assertEquals(-2.0, row.quantity, 0.0); assertEquals(AdjustReason.COUNT, row.reason)
        assertEquals(10.0, row.bookQty!!, 0.0); assertEquals(8.0, row.countedQty!!, 0.0)
        assertEquals(AuditAction.STOCK_COUNT, dao.latestAudit(1).single().action)
        // A second count from the corrected figure.
        dao.recordCount(mapOf(dap to 9.0), null)
        assertEquals(9.0, Stock.of(dao).first { it.productId == dap }.onHand!!, 0.0)
    }

    @Test
    fun anAdjustmentNeedsAReasonAndValuationUsesCostThenLastRate() = runBlocking {
        dao.adjustStock(urea, -3.0, AdjustReason.DAMAGE, "torn")
        try { dao.adjustStock(urea, -1.0, "whim", null); throw AssertionError("reason") } catch (e: IllegalArgumentException) {}
        val v = Valuation.compute(dao)
        val d = v.rows.first { it.productId == dap }; val u = v.rows.first { it.productId == urea }
        assertEquals(3000.0, d.cost!!, 0.0)              // its own cost price
        assertEquals(4800.0, u.cost!!, 0.0)              // the last purchase rate
        assertEquals(17.0, u.onHand!!, 0.0)
        assertEquals(10 * 3000.0 + 17 * 4800.0, v.total, 0.001)
        assertEquals(2, v.known)
        assertTrue(v.low.isEmpty())
        dao.recordCount(mapOf(dap to 4.0), null)
        assertEquals(listOf("DAP"), Valuation.compute(dao).low.map { it.name })
    }

    @Test
    fun anOrderIsNumberedSentAndReceivedAsABill() = runBlocking {
        val id = dao.saveOrder(PurchaseOrder(partyId = engro), listOf(
            PurchaseOrderItem(orderId = 0, productId = dap, itemName = "DAP", quantity = 50.0, unit = "bag", rate = 3000.0)
        ))
        assertEquals("PO-000001", dao.getOrder(id)!!.orderNumber)
        // A draft's lines can be replaced.
        dao.saveOrder(dao.getOrder(id)!!, listOf(
            PurchaseOrderItem(orderId = 0, productId = dap, itemName = "DAP", quantity = 40.0, unit = "bag", rate = 3000.0),
            PurchaseOrderItem(orderId = 0, productId = urea, itemName = "Urea", quantity = 10.0, unit = "bag")
        ))
        assertEquals(2, dao.orderItems(id).size)
        dao.setOrderStatus(id, OrderStatus.SENT)
        try { dao.saveOrder(dao.getOrder(id)!!, dao.orderItems(id)); throw AssertionError("sent") } catch (e: IllegalArgumentException) {}
        val billId = dao.receiveOrder(id,
            SupplierBill(partyId = engro, billNumber = "B2", billDate = 200L, totalAmount = 120_000.0),
            listOf(BillItem(billId = 0, productName = "DAP", quantity = 40.0, unit = "bag", rate = 3000.0, productId = dap, batchNumber = "X1")),
            entry = null)
        val o = dao.getOrder(id)!!
        assertEquals(OrderStatus.RECEIVED, o.status); assertEquals(billId, o.billId)
        assertEquals(50.0, Stock.of(dao).first { it.productId == dap }.onHand!!, 0.0)
        try { dao.receiveOrder(id, SupplierBill(partyId = engro, totalAmount = 0.0), emptyList(), null); throw AssertionError("twice") } catch (e: IllegalArgumentException) {}
        assertEquals(AuditAction.ORDER_RECEIVED, dao.latestAudit(1).single().action)
    }

    @Test
    fun theFileCarriesAdjustmentsAndOrdersAndAVersion34BookOpensAt35() = runBlocking {
        dao.adjustStock(dap, -1.0, AdjustReason.THEFT, null)
        dao.saveOrder(PurchaseOrder(partyId = engro), listOf(PurchaseOrderItem(orderId = 0, itemName = "Zinc", quantity = 5.0)))
        val out = java.io.StringWriter()
        BackupCodec.exportTo(dao, "T", org.json.JSONObject(), out)
        val (res, parsed) = BackupCodec.parseText(out.toString())
        assertTrue(res is BackupCodec.ImportResult.Ok)
        assertEquals(1, parsed!!.adjustments.size); assertEquals(1, parsed.orders.size); assertEquals(1, parsed.orderItems.size)

        val name = "migration_34_35_test.db"
        context.deleteDatabase(name)
        val file = Room.databaseBuilder(context, KhataDatabase::class.java, name).addMigrations(*ALL_MIGRATIONS).build()
        val a = file.khataDao().insertParty(Party(name = "Ahmad"))
        file.close()
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
            SchemaRewind.undo35(raw); raw.version = 34
        }
        val again = Room.databaseBuilder(context, KhataDatabase::class.java, name).addMigrations(*ALL_MIGRATIONS).build()
        try {
            assertEquals("Ahmad", again.khataDao().partiesWithBalanceOnce().single { it.id == a }.name)
            assertTrue(again.khataDao().allOrdersForBackup().isEmpty())
            assertNull(again.khataDao().adjustedPerProduct().firstOrNull())
        } finally { again.close(); context.deleteDatabase(name) }
    }
}
