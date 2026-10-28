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

/**
 * Migration 31→32 (the counter) on a real database file.
 *
 * Room compares the migrated schema with the entities the moment it opens
 * and refuses to start on any difference — on a phone, a crash on launch; on
 * the owner's laptop, a book that will not open. This builds a genuine
 * version-31 file (today's schema with exactly what 32 adds taken back out),
 * fills it the way the 1.10.0 app did, and opens it through the real chain.
 * If one hand-written statement in [Schema32] differs from Room's own idea of
 * a table by one NOT NULL, this fails instead of a device.
 */
@RunWith(AndroidJUnit4::class)
class SalesMigrationTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val name = "migration_31_32_test.db"

    @Before
    fun clean() = context.deleteDatabase(name).let { }

    @After
    fun tidy() = context.deleteDatabase(name).let { }

    private fun open() = Room.databaseBuilder(context, KhataDatabase::class.java, name)
        .addMigrations(*ALL_MIGRATIONS)
        .build()

    @Test
    fun aVersion31BookOpensAt32WithEverythingInPlace() = runBlocking {
        var ahmad = 0L
        var urea = 0L
        open().also { db ->
            val dao = db.khataDao()
            ahmad = dao.insertParty(Party(name = "Ahmad"))
            dao.insertEntryWithItems(
                LedgerEntry(partyId = ahmad, amount = 9600.0, isGiven = true, entryNumber = ""),
                listOf(EntryItem(entryId = 0, itemName = "Urea", quantity = 2.0, unit = "bori", rate = 4800.0))
            )
            dao.insertCashEntry(CashEntry(amount = 500.0, isIncome = false, category = "Fuel"))
            urea = dao.insertProduct(Product(name = "Urea", nameKey = "urea", normalisedName = "urea", defaultUnit = "bori"))
            db.close()
        }

        SQLiteDatabase.openDatabase(
            context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE
        ).use { raw ->
            SchemaRewind.undo32(raw)
            val tables = raw.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { c ->
                buildList { while (c.moveToNext()) add(c.getString(0)) }
            }
            assertTrue("rewind left the counter's tables", Schema32.newTables.none { it in tables })
            raw.version = 31
        }

        val db = open()
        try {
            val dao = db.khataDao()
            // The old rows survived, and the new columns start empty.
            val entry = dao.partiesWithBalanceOnce().single { it.id == ahmad }
            assertEquals(9600.0, entry.balance, 0.001)
            assertNull(dao.productById(urea)!!.barcode)
            assertNull(dao.allCashForBackup().single().paymentMethod)
            assertEquals(500.0, dao.cashbookCashBetween(false, 0, Long.MAX_VALUE), 0.001)

            // The counter works on the migrated file: a walk-in sale posts and counts.
            val saleId = dao.postSale(
                Sale(subtotal = 0.0, grandTotal = 0.0, paidTotal = 0.0),
                listOf(SaleItem(saleId = 0, productId = urea, itemName = "Urea", quantity = 1.0, unit = "bori", rate = 5000.0)),
                listOf(SalePayment(saleId = 0, method = PaymentMethod.CASH, amount = 5000.0))
            )
            assertEquals("S-000001", dao.getSale(saleId)!!.saleNumber)
            assertEquals(1.0, dao.walkInSoldPerProduct().single().qty, 0.0)
        } finally {
            db.close()
        }
    }
}
