package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The automations read a real book (31 Oct 2026, Automations): the low shelf with its quantity and supplier,
 * nothing already ordered, the morning's list by Follow-up's rule, and today's closing figures. Made-up names.
 */
class AutomationsBookTest {

    private lateinit var dir: File
    private lateinit var db: KhataDatabase
    private val dao get() = db.khataDao()
    private val now = System.currentTimeMillis()
    private val day = 24L * 60 * 60 * 1000

    @Before
    fun open() {
        dir = Files.createTempDirectory("rk-automations").toFile()
        db = DesktopDatabase.open(dir.resolve("khata.db"))
    }

    @After
    fun close() {
        db.close()
        dir.deleteRecursively()
    }

    private fun product(name: String, low: Double?) =
        Product(name = name, nameKey = ProductName.key(name), normalisedName = ProductName.normalised(name), defaultUnit = "bottle",
            salePrice = 1000.0, lowStockLevel = low)

    /** 25 bottles from Test Supplier 40 days ago; 18 sold to a customer (promised yesterday, with a phone) today. */
    private suspend fun book(): Triple<Long, Long, Long> {
        val supplier = dao.insertParty(Party(name = "Test Supplier", isCustomer = false))
        val customer = dao.insertParty(Party(name = "Test Customer", phone = "03000000002", harvestPromise = now - day))
        val sprayId = dao.insertProduct(product("Test Spray", low = 10.0))
        dao.insertSupplierBill(
            null, SupplierBill(partyId = supplier, billDate = now - 40 * day, totalAmount = 0.0),
            listOf(BillItem(billId = 0, productName = "Test Spray", quantity = 25.0, unit = "bottle", rate = 700.0, productId = sprayId))
        )
        dao.insertEntryWithItems(
            LedgerEntry(partyId = customer, amount = 18_000.0, isGiven = true, entryNumber = ""),
            listOf(EntryItem(entryId = 0, itemName = "Test Spray", quantity = 18.0, unit = "bottle", rate = 1000.0, productId = sprayId))
        )
        return Triple(supplier, customer, sprayId)
    }

    @Test
    fun `the low shelf comes with a month's quantity and its last supplier, and only once ordered`() = runBlocking {
        val (supplier, _, spray) = book()
        val lines = Automations.reorder(dao, now)
        assertEquals(1, lines.size)
        val line = lines.single()
        assertEquals(spray, line.productId)
        assertEquals("the supplier of the newest bill", supplier, line.supplierId)
        // Twice the level (20) is more than the 18 sold in 30 days: 20 less the 7 on the shelf = 13.
        assertEquals(13.0, line.qty, 0.001)
        dao.saveOrder(PurchaseOrder(partyId = supplier), listOf(PurchaseOrderItem(orderId = 0, productId = spray, itemName = "Test Spray", quantity = 13.0, unit = "bottle")))
        assertTrue("an open order already carries it", Automations.reorder(dao, now).isEmpty())
    }

    @Test
    fun `the morning list is Follow-up's, less those reminded today`() = runBlocking {
        val (_, customer, _) = book()
        val c = Automations.collect(dao, emptySet(), now)
        assertEquals(1, c.count)
        assertEquals(18_000.0, c.total, 0.001)
        assertEquals(0, Automations.collect(dao, setOf(customer), now).count)
    }

    @Test
    fun `today's closing counts today's sales`() = runBlocking {
        book()
        val d = Automations.dayEnd(dao, now)
        assertEquals(18_000.0, d.sales, 0.001)
        assertTrue(d.moved)
        assertTrue("the drawer was never counted", !d.counted)
    }
}
