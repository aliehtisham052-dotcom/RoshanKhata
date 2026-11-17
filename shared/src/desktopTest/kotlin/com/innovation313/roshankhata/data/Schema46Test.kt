package com.innovation313.roshankhata.data

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.StringWriter
import java.nio.file.Files

/**
 * Schema 46 and backup format 21 (17 Nov 2026, the audit's L108 and L002), on the computer's own driver: a version-45
 * book opens at 46 with every row it had, no cashbook row with a writer and no sale with a held ticket, and the new index
 * in place; a backup carries both (a helper's copy too: neither is a secret); a format-20 file — written before either
 * existed — restores both as null. Made-up names and figures.
 */
class Schema46Test {

    private fun SQLiteConnection.names(sql: String): Set<String> {
        val st = prepare(sql)
        try { return buildSet { while (st.step()) add(st.getText(0)) } } finally { st.close() }
    }

    private fun SQLiteConnection.columns(table: String): Set<String> {
        val st = prepare("PRAGMA table_info($table)")
        try { return buildSet { while (st.step()) add(st.getText(1)) } } finally { st.close() }
    }

    @Test
    fun `a version 45 book opens at 46 with every row and nothing it cannot know`() = runBlocking {
        val dir = Files.createTempDirectory("rk-schema46").toFile()
        val file = File(dir, "khata.db")
        try {
            val first = DesktopDatabase.open(file)
            try {
                val dao = first.khataDao()
                dao.insertCashEntryAudited(CashEntry(amount = 700.0, isIncome = true, category = "Rent"))
                dao.postSale(
                    Sale(subtotal = 0.0, grandTotal = 0.0, paidTotal = 0.0),
                    listOf(SaleItem(saleId = 0, itemName = "Soap", quantity = 2.0, rate = 150.0)),
                    listOf(SalePayment(saleId = 0, method = PaymentMethod.CASH, amount = 300.0))
                )
            } finally { first.close() }

            // Back to 45: the index and the two columns 46 added taken out, the version written back.
            BundledSQLiteDriver().open(file.absolutePath).let { c ->
                try {
                    for (idx in Schema46.newIndices) c.execSQL("DROP INDEX IF EXISTS `$idx`")
                    for ((table, cols) in Schema46.newColumns) for (col in cols) c.execSQL("ALTER TABLE $table DROP COLUMN $col")
                    c.execSQL("PRAGMA user_version = 45")
                    for ((table, cols) in Schema46.newColumns) assertTrue(table, cols.none { it in c.columns(table) })
                } finally { c.close() }
            }

            // Opened as the app opens it: Room runs 45 -> 46 and refuses a result that differs from the entities.
            val again = DesktopDatabase.open(file)
            try {
                val dao = again.khataDao()
                val cash = dao.allCashForBackup()
                assertEquals(2, cash.size)
                assertTrue("no row written before says who wrote it", cash.all { it.createdBy == null })
                assertEquals(1_000.0, cash.sumOf { it.amount }, 0.001)
                assertTrue(dao.allSalesForBackup().all { it.heldUid == null })
            } finally { again.close() }

            BundledSQLiteDriver().open(file.absolutePath).let { c ->
                try {
                    val st = c.prepare("PRAGMA user_version")
                    try { st.step(); assertEquals(KHATA_DB_VERSION.toLong(), st.getLong(0)) } finally { st.close() }
                    assertTrue(KHATA_DB_VERSION >= 46)
                    for ((table, cols) in Schema46.newColumns) assertTrue(table, c.columns(table).containsAll(cols))
                    assertTrue(c.names("SELECT name FROM sqlite_master WHERE type = 'index'").containsAll(Schema46.newIndices))
                } finally { c.close() }
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    private suspend fun fill(dao: KhataDao) {
        Access.signIn(Staff(id = 7, name = "Imran", monthlySalary = 0.0, role = Role.CASHIER))
        try {
            dao.insertCashEntryAudited(CashEntry(amount = 250.0, isIncome = false, category = "Tea"))
        } finally { Access.resetForTest() }
        val held = dao.holdSale(Sale(subtotal = 0.0, grandTotal = 0.0, paidTotal = 0.0), listOf(SaleItem(saleId = 0, itemName = "Soap", quantity = 1.0, rate = 90.0)))
        dao.postSale(
            Sale(subtotal = 0.0, grandTotal = 0.0, paidTotal = 0.0),
            listOf(SaleItem(saleId = 0, itemName = "Soap", quantity = 1.0, rate = 90.0)),
            listOf(SalePayment(saleId = 0, method = PaymentMethod.CASH, amount = 90.0)), heldId = held
        )
    }

    @Test
    fun `a format 21 backup carries the cashbook's writer and the sale's held ticket, the helper's copy too`() = runBlocking {
        val dir = Files.createTempDirectory("rk-format21").toFile()
        try {
            val db = DesktopDatabase.open(File(dir, "a.db"))
            try {
                fill(db.khataDao())
                val heldUid = db.khataDao().allSalesForBackup().single { it.status == SaleStatus.DONE }.heldUid
                assertTrue(heldUid != null)
                for (secrets in listOf(true, false)) {
                    val text = StringWriter().also { BackupCodec.exportTo(db.khataDao(), "Shop", JSONObject(), it, secrets = secrets) }.toString()
                    val (result, data) = BackupCodec.parseText(text)
                    assertTrue("$result", result is BackupCodec.ImportResult.Ok)
                    assertEquals(BackupCodec.FORMAT_VERSION, data!!.version)
                    assertTrue(data.version >= 21)
                    val other = DesktopDatabase.open(File(dir, "b-$secrets.db"))
                    try {
                        BackupCodec.restoreLedger(other.khataDao(), data)
                        val o = other.khataDao()
                        assertEquals("Imran", o.allCashForBackup().single { it.category == "Tea" }.createdBy)
                        assertEquals(heldUid, o.allSalesForBackup().single { it.status == SaleStatus.DONE }.heldUid)
                    } finally { other.close() }
                }
            } finally { db.close() }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a format 20 file restores no writer and no held ticket`() = runBlocking {
        val dir = Files.createTempDirectory("rk-format20b").toFile()
        try {
            val db = DesktopDatabase.open(File(dir, "a.db"))
            try {
                fill(db.khataDao())
                val now = StringWriter().also { BackupCodec.exportTo(db.khataDao(), "Shop", JSONObject(), it) }.toString()
                // The file as a release before format 21 wrote it: no cashbook createdBy, no heldUid, version 20. An
                // entry's createdBy (older than 21) stays: only the cashbook's key goes, read from its own array.
                val json = JSONObject(now)
                fun strip(key: String, field: String) {
                    val arr = json.optJSONArray(key) ?: return
                    for (i in 0 until arr.length()) arr.getJSONObject(i).remove(field)
                }
                strip("cashbook", "createdBy")
                strip("sales", "heldUid")
                json.put("version", 20)
                val old = json.toString()
                assertTrue("heldUid" !in old)
                val (result, data) = BackupCodec.parseText(old)
                assertTrue("$result", result is BackupCodec.ImportResult.Ok)
                assertEquals(20, data!!.version)
                val other = DesktopDatabase.open(File(dir, "b.db"))
                try {
                    BackupCodec.restoreLedger(other.khataDao(), data)
                    val o = other.khataDao()
                    assertEquals(250.0, o.allCashForBackup().single { it.category == "Tea" }.amount, 0.001)
                    assertTrue(o.allCashForBackup().all { it.createdBy == null })
                    assertTrue(o.allSalesForBackup().all { it.heldUid == null })
                } finally { other.close() }
            } finally { db.close() }
        } finally {
            dir.deleteRecursively()
        }
    }
}
