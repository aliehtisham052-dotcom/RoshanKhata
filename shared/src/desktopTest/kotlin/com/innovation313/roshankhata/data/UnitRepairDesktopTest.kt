package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * A computer's book whose units were saved as labels ("بوری", "bori") is
 * repaired to the keys when it opens, as the phone's is (23 Oct review, R02);
 * the counter's tables too, and in a live book without a word to the sync log
 * (23 Oct, phase 2).
 */
class UnitRepairDesktopTest {
    @Test
    fun `labels saved on the computer become keys on the next open`() = runBlocking {
        val dir = Files.createTempDirectory("rk-unit-repair").toFile()
        try {
            val file = dir.resolve("khata.db")
            var db = DesktopDatabase.open(file)
            val urea = db.khataDao().insertProduct(
                Product(name = "Urea", nameKey = ProductName.key("Urea"), normalisedName = ProductName.normalised("Urea"), defaultUnit = "بوری")
            )
            val tin = db.khataDao().insertProduct(
                Product(name = "Ghee", nameKey = ProductName.key("Ghee"), normalisedName = ProductName.normalised("Ghee"), defaultUnit = "tin")
            )
            db.close()
            db = DesktopDatabase.open(file)
            assertEquals("bag", db.khataDao().productById(urea)?.defaultUnit)
            // The owner's own word is left as typed.
            assertEquals("tin", db.khataDao().productById(tin)?.defaultUnit)
            db.close()
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun withBook(block: suspend (File) -> Unit) {
        val dir = Files.createTempDirectory("rk-unit-repair").toFile()
        try {
            runBlocking { block(dir.resolve("khata.db")) }
        } finally {
            dir.deleteRecursively()
        }
    }

    private class Made(val urea: Long, val sale: Long, val ret: Long, val order: Long)

    /** One labelled row in every counter table, and a product whose pack is a label. */
    private suspend fun makeCounterRows(db: KhataDatabase): Made {
        val d = db.khataDao()
        val urea = d.insertProduct(
            Product(
                name = "Urea", nameKey = ProductName.key("Urea"), normalisedName = ProductName.normalised("Urea"),
                defaultUnit = "kg", packUnit = "بوری", packSize = 50.0
            )
        )
        val supplier = d.insertParty(Party(name = "Supplier"))
        val sale = d.insertSale(Sale(subtotal = 10.0, grandTotal = 10.0, paidTotal = 10.0))
        d.insertSaleItems(listOf(SaleItem(saleId = sale, productId = urea, itemName = "Urea", quantity = 1.0, unit = "Bori", rate = 10.0)))
        val ret = d.insertReturn(SaleReturn(saleId = sale, total = 10.0, reason = "test"))
        d.insertReturnItems(listOf(SaleReturnItem(returnId = ret, productId = urea, itemName = "Urea", quantity = 1.0, unit = "بوری", rate = 10.0)))
        val order = d.insertOrder(PurchaseOrder(partyId = supplier))
        d.insertOrderItems(listOf(PurchaseOrderItem(orderId = order, productId = urea, itemName = "Urea", quantity = 2.0, unit = "बोरी")))
        d.insertAdjustment(StockAdjustment(productId = urea, quantity = 3.0, unit = "کلو", reason = "count"))
        return Made(urea, sale, ret, order)
    }

    private suspend fun assertRepaired(db: KhataDatabase, m: Made) {
        val d = db.khataDao()
        assertEquals("bag", d.productById(m.urea)?.packUnit)
        assertEquals("kg", d.productById(m.urea)?.defaultUnit)
        assertEquals(listOf<String?>("bag"), d.saleItems(m.sale).map { it.unit })
        assertEquals(listOf<String?>("bag"), d.returnItems(m.ret).map { it.unit })
        assertEquals(listOf<String?>("bag"), d.orderItems(m.order).map { it.unit })
        assertEquals(listOf<String?>("kg"), d.allAdjustmentsForBackup().map { it.unit })
    }

    private suspend fun count(db: KhataDatabase, table: String): Long =
        db.syncSql().read { rows("SELECT COUNT(*) FROM $table", types = "I").first()[0] as Long }

    @Test
    fun `every place a unit is saved exists in the computer's book`() = withBook { file ->
        val db = DesktopDatabase.open(file)
        try {
            db.syncSql().read {
                for ((table, column) in UnitWords.STORED_IN) rows("SELECT $column FROM $table LIMIT 1", types = "T")
                // The one read the repair starts with is valid SQL against the real schema.
                rows(UnitWords.repairNeededQuery()!!, types = "I")
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun `the counter's lines and a product's pack are repaired too`() = withBook { file ->
        var db = DesktopDatabase.open(file)
        val made = makeCounterRows(db)
        db.close()
        db = DesktopDatabase.open(file)
        try {
            assertRepaired(db, made)
        } finally {
            db.close()
        }
    }

    @Test
    fun `a live book is repaired without a line in the sync log, and the mute is lifted`() = withBook { file ->
        var db = DesktopDatabase.open(file)
        LiveSync.enable(db.syncSql(), install = "install-pc", name = "PC")
        val made = makeCounterRows(db)
        val logged = count(db, "sync_log")
        assertTrue("the labelled rows themselves were noted", logged > 0)
        db.close()

        db = DesktopDatabase.open(file)
        try {
            assertRepaired(db, made)
            // Each device repairs its own copy: nothing of it goes to the other devices.
            assertEquals(logged, count(db, "sync_log"))
            assertEquals(0L, count(db, "sync_mute"))
            // The owner's next real change is noted as ever.
            db.khataDao().insertParty(Party(name = "Next customer"))
            assertTrue(count(db, "sync_log") > logged)
            val afterChange = count(db, "sync_log")
            db.close()

            // A book already in order is not written to as it opens.
            db = DesktopDatabase.open(file)
            assertRepaired(db, made)
            assertEquals(afterChange, count(db, "sync_log"))
        } finally {
            db.close()
        }
    }

    @Test
    fun `the read says yes only while a label is left`() = withBook { file ->
        val db = DesktopDatabase.open(file)
        try {
            val ask = UnitWords.repairNeededQuery()!!
            suspend fun needed() = db.syncSql().read { rows(ask, types = "I").first()[0] as Long } != 0L
            assertFalse(needed())
            db.khataDao().insertAdjustment(
                StockAdjustment(
                    productId = db.khataDao().insertProduct(
                        Product(name = "Ghee", nameKey = ProductName.key("Ghee"), normalisedName = ProductName.normalised("Ghee"), defaultUnit = "tin")
                    ),
                    quantity = 1.0, unit = "tin", reason = "count"
                )
            )
            // The owner's own word is not a label.
            assertFalse(needed())
            db.khataDao().insertAdjustment(
                StockAdjustment(productId = db.khataDao().allAdjustmentsForBackup().first().productId, quantity = 1.0, unit = "Bori", reason = "count")
            )
            assertTrue(needed())
        } finally {
            db.close()
        }
    }
}
