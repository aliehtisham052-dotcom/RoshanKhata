package com.innovation313.roshankhata.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Schema 41 (30 Oct 2026, the speed review): indexes only. A version-40 book
 * opens at 41 with every row as it was and the four new indexes in place, so the
 * whole-book date windows, entry numbers, the cashbook and the documents page
 * read through an index instead of the whole table.
 */
@RunWith(AndroidJUnit4::class)
class Schema41Test {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun aVersion40BookOpensAt41WithItsRowsAndTheNewIndexes() = runBlocking {
        val name = "migration_40_41_test.db"
        context.deleteDatabase(name)
        val file = Room.databaseBuilder(context, KhataDatabase::class.java, name).addMigrations(*ALL_MIGRATIONS).build()
        val d = file.khataDao()
        d.saveInvoiceWithItems(Invoice(customerName = "Ali", kind = DocKind.QUOTATION), listOf(InvoiceItem(invoiceId = 0, itemName = "Urea", quantity = 10.0, unit = "bag", rate = 4650.0)))
        d.insertCashEntry(CashEntry(amount = 500.0, isIncome = true, category = "Other", note = "rent"))
        file.close()
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
            SchemaRewind.undo41(raw); raw.version = 40
        }
        val again = Room.databaseBuilder(context, KhataDatabase::class.java, name).addMigrations(*ALL_MIGRATIONS).build()
        try {
            val a = again.khataDao()
            assertEquals(listOf("Q-000001"), a.allInvoicesForBackup().map { it.invoiceNumber })
            val indexes = again.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type = 'index'").use { c ->
                buildSet { while (c.moveToNext()) add(c.getString(0)) }
            }
            for (idx in Schema41.newIndices) assertTrue("$idx missing from $indexes", idx in indexes)
        } finally { again.close(); context.deleteDatabase(name) }
    }
}
