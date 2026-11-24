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
 * Old books open right (24 Nov 2026, plan item B2): a real book file at EACH version from 31 to 45, made by taking
 * today's book back one step with [SchemaRewind] and stamping the older number, opens through [ALL_MIGRATIONS] with its
 * rows as they were and at the current schema (Room refuses to open a book whose tables differ from its own, so a
 * migration that forgot a column or an index fails here). Before this, versions 33, 36, 44 and 45 had no test of their own
 * (the others: Schema36/38-44Test, InventoryDaoTest, ReturnsDaoTest, SalesMigrationTest). The whole chain from the real
 * Play schema 31 is PlayBookMigrationTest's. Made-up names and figures only.
 */
@RunWith(AndroidJUnit4::class)
class OldBooksMigrationEveryVersionTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** Takes [raw] (today's book) back to the shape of version [v]. */
    private fun rewindTo(raw: SQLiteDatabase, v: Int) {
        when (v) {
            45 -> SchemaRewind.undo46(raw)
            44 -> SchemaRewind.undo45(raw)
            43 -> SchemaRewind.undo44(raw)
            42 -> SchemaRewind.undo43(raw)
            41 -> SchemaRewind.undo42(raw)
            40 -> SchemaRewind.undo41(raw)
            39 -> SchemaRewind.undo40(raw)
            38 -> SchemaRewind.undo39(raw)
            37 -> SchemaRewind.undo38(raw)
            36 -> SchemaRewind.undo37(raw)
            35 -> SchemaRewind.undo36(raw)
            34 -> SchemaRewind.undo35(raw)
            33 -> SchemaRewind.undo34(raw)
            32 -> SchemaRewind.undo33(raw)
            31 -> SchemaRewind.undo32(raw)
            else -> error("no rewind to $v")
        }
        raw.version = v
    }

    @Test
    fun aBookOfEachVersionFrom31To45OpensAtTheCurrentSchemaWithItsRows() = runBlocking {
        for (v in 31..45) {
            val name = "old_book_$v.db"
            context.deleteDatabase(name)
            val fresh = Room.databaseBuilder(context, KhataDatabase::class.java, name).addMigrations(*ALL_MIGRATIONS).build()
            val d = fresh.khataDao()
            val ali = d.insertParty(Party(name = "Ali Raza $v"))
            d.insertEntryNumbered(LedgerEntry(partyId = ali, amount = 900.0 + v, isGiven = true, entryNumber = "", note = "Urea"))
            d.insertEntryNumbered(LedgerEntry(partyId = ali, amount = 400.0, isGiven = false, entryNumber = ""))
            d.insertCashEntryAudited(CashEntry(amount = 55.0, isIncome = true, category = "Tea"))
            d.insertProduct(Product(name = "Urea", nameKey = ProductName.key("Urea"), normalisedName = ProductName.normalised("Urea"), salePrice = 4_500.0))
            fresh.close()

            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
                rewindTo(raw, v)
                assertEquals("version $v stamped", v, raw.version)
            }

            val again = Room.databaseBuilder(context, KhataDatabase::class.java, name).addMigrations(*ALL_MIGRATIONS).build()
            try {
                val a = again.khataDao()
                assertEquals("version $v: the party", "Ali Raza $v", a.allPartiesForBackup().single().name)
                assertEquals("version $v: the entries", listOf(400.0, 900.0 + v), a.allEntriesForBackup().map { it.amount }.sorted())
                assertEquals("version $v: the cash row", 55.0, a.allCashForBackup().single().amount, 0.0)
                assertEquals("version $v: the product", 4_500.0, a.allProductsForBackup().single().salePrice!!, 0.0)
                // At the current number, and writable: the migrated book is a working one.
                assertEquals("version $v migrated to now", KHATA_DB_VERSION, again.openHelper.readableDatabase.version)
                a.insertEntryNumbered(LedgerEntry(partyId = a.allPartiesForBackup().single().id, amount = 1.0, isGiven = true, entryNumber = ""))
                assertTrue(a.allEntriesForBackup().size == 3)
            } finally { again.close(); context.deleteDatabase(name) }
        }
    }
}
