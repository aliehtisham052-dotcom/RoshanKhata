package com.innovation313.roshankhata.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Schema 40 (29 Oct 2026, the documents): a version-39 book opens with every
 * invoice reading as an open invoice made from nothing (kind INVOICE, status
 * OPEN, no source), a quotation written afterwards keeps its kind and its own
 * Q- series, a conversion leaves the pair linked, and the backup (format 17)
 * carries kind, status and the link so a restored book still knows which
 * sales order its invoice came from.
 */
@RunWith(AndroidJUnit4::class)
class Schema40Test {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val lines = listOf(InvoiceItem(invoiceId = 0, itemName = "Urea", quantity = 10.0, unit = "bag", rate = 4650.0))

    @Test
    fun aVersion39BookOpensAt40WithEveryOldRowAnOpenInvoice() = runBlocking {
        val name = "migration_39_40_test.db"
        context.deleteDatabase(name)
        val file = Room.databaseBuilder(context, KhataDatabase::class.java, name).addMigrations(*ALL_MIGRATIONS).build()
        val d = file.khataDao()
        d.saveInvoiceWithItems(Invoice(customerName = "Ali"), lines)
        d.saveInvoiceWithItems(Invoice(customerName = "Bashir"), lines)
        file.close()
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
            SchemaRewind.undo40(raw); raw.version = 39
        }
        val again = Room.databaseBuilder(context, KhataDatabase::class.java, name).addMigrations(*ALL_MIGRATIONS).build()
        try {
            val a = again.khataDao()
            val old = a.allInvoicesForBackup().sortedBy { it.id }
            assertEquals(listOf("INV-000001", "INV-000002"), old.map { it.invoiceNumber })
            assertTrue("every row written before 40 is an invoice", old.all { it.kind == DocKind.INVOICE })
            assertTrue("every row written before 40 is open", old.all { it.status == DocStatus.OPEN })
            assertTrue("nothing written before 40 was converted from anything", old.all { it.sourceDocId == null })

            // The series run on: the invoices from their third, a quotation from its first.
            val q = a.saveInvoiceWithItems(Invoice(customerName = "Ali", kind = DocKind.QUOTATION), lines)
            assertEquals("Q-000001", a.getInvoice(q)!!.invoiceNumber)
            val inv = a.saveInvoiceWithItems(Invoice(customerName = "Ali"), lines)
            assertEquals("INV-000003", a.getInvoice(inv)!!.invoiceNumber)

            val columns = again.openHelper.readableDatabase.query("PRAGMA table_info(invoices)").use { c ->
                buildSet { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
            }
            assertTrue(columns.containsAll(listOf("kind", "status", "sourceDocId")))
            val indices = again.openHelper.readableDatabase.query("PRAGMA index_list(invoices)").use { c ->
                buildSet { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
            }
            assertTrue("$indices", "index_invoices_sourceDocId" in indices)
        } finally { again.close(); context.deleteDatabase(name) }
    }

    @Test
    fun theBackupCarriesAConvertedPairAndARestoreKeepsTheLink() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java).build()
        val other = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java).build()
        try {
            val dao = db.khataDao()
            val q = dao.saveInvoiceWithItems(Invoice(customerName = "Ali", kind = DocKind.QUOTATION), lines)
            dao.acceptDocument(q, by = "Owner")
            val so = dao.convertDocument(q, DocKind.SALES_ORDER, by = "Owner")
            dao.saveInvoiceWithItems(Invoice(customerName = "Bashir"), lines)

            val out = java.io.StringWriter()
            BackupCodec.exportTo(dao, "T", org.json.JSONObject(), out)
            val (res, parsed) = BackupCodec.parseText(out.toString())
            assertTrue(res is BackupCodec.ImportResult.Ok)
            parsed!!
            // Written at the format of the day (17, 29 Oct 2026, brought the documents).
            assertEquals(BackupCodec.FORMAT_VERSION, parsed.version)
            assertTrue(parsed.version >= 17)
            val o = other.khataDao()
            BackupCodec.restoreLedger(o, parsed)
            val back = o.allInvoicesForBackup().associateBy { it.id }
            val quotation = back.getValue(q)
            assertEquals(DocKind.QUOTATION, quotation.kind)
            assertEquals(DocStatus.CONVERTED, quotation.status)
            assertNull(quotation.sourceDocId)
            val order = back.getValue(so)
            assertEquals(DocKind.SALES_ORDER, order.kind)
            assertEquals(DocStatus.OPEN, order.status)
            assertEquals("the link points where it did: a restore keeps every id", q, order.sourceDocId)
            assertEquals("SO-000001", order.invoiceNumber)
            val plain = back.values.single { it.customerName == "Bashir" }
            assertEquals(DocKind.INVOICE, plain.kind)
            assertEquals(DocStatus.OPEN, plain.status)
            assertNull(plain.sourceDocId)
            assertEquals(listOf("Urea"), o.invoiceItems(so).map { it.itemName })
        } finally { db.close(); other.close() }
    }
}
