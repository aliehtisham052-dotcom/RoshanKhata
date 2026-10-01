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
 * Migration 19→20 on a real database file — the check CI otherwise never makes.
 *
 * Room compares the migrated schema with the entities the moment it opens,
 * and refuses to start on any difference; on a phone that is a crash on
 * launch. This builds a genuine version-19 file — today's schema with exactly
 * what 19→20 adds taken back out — fills it the way an old app would have,
 * and opens it through the real migration chain. If the hand-written
 * CREATE TABLE in MIGRATION_19_20 differs from Room's own idea of
 * entry_items by one NOT NULL, this test fails instead of a phone.
 */
@RunWith(AndroidJUnit4::class)
class EntryItemsMigrationTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val name = "migration_19_20_test.db"

    @Before
    fun clean() {
        context.deleteDatabase(name)
    }

    @After
    fun tidy() {
        context.deleteDatabase(name)
    }

    private fun open() = Room.databaseBuilder(context, KhataDatabase::class.java, name)
        .addMigrations(*ALL_MIGRATIONS)
        .build()

    @Test
    fun oldGoodsBecomeLinesAndNoMoneyMoves() = runBlocking {
        // 1. Write the book as the old app did: goods on the entry itself.
        //    (Test code may build legacy entries; LegacyGoodsWritesTest only
        //    polices the app's own source.)
        var goodsId = 0L
        var qtyOnlyId = 0L
        var moneyId = 0L
        var binnedId = 0L
        var ahmad = 0L
        open().also { db ->
            val dao = db.khataDao()
            ahmad = dao.insertParty(Party(name = "Ahmad"))
            goodsId = dao.insertEntry(
                LedgerEntry(
                    partyId = ahmad, amount = 9600.0, isGiven = true, entryNumber = "RK-000001",
                    itemName = "Urea", quantity = 2.0, unit = "bori"
                )
            )
            qtyOnlyId = dao.insertEntry(
                LedgerEntry(partyId = ahmad, amount = 300.0, isGiven = true, entryNumber = "RK-000002", quantity = 3.0)
            )
            moneyId = dao.insertEntry(
                LedgerEntry(partyId = ahmad, amount = 5000.0, isGiven = false, entryNumber = "RK-000003")
            )
            binnedId = dao.insertEntry(
                LedgerEntry(
                    partyId = ahmad, amount = 750.0, isGiven = true, entryNumber = "RK-000004",
                    itemName = "Sulphur", quantity = 1.0, unit = "bag", isDeleted = true, deletedAt = 5L
                )
            )
            db.close()
        }

        // 2. Take the file back to version 19: remove exactly what 19→20 adds.
        //    Robolectric's SQLite predates ALTER TABLE … DROP COLUMN (3.35),
        //    so the two columns are removed the way every SQLite supports:
        //    rebuild `transactions` from its own CREATE statement minus those
        //    two columns, copy the rows across, and put its indices back.
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE
        ).use { raw ->
            raw.execSQL("PRAGMA foreign_keys = OFF")
            // Rename must not rewrite references elsewhere (newer SQLite would).
            raw.execSQL("PRAGMA legacy_alter_table = ON")
            raw.execSQL("DROP TABLE entry_items")

            fun sqlOf(type: String, table: String): List<String> =
                raw.rawQuery(
                    "SELECT sql FROM sqlite_master WHERE type = ? AND tbl_name = ? AND sql IS NOT NULL",
                    arrayOf(type, table)
                ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

            val createV20 = sqlOf("table", "transactions").single()
            val indices = sqlOf("index", "transactions")
            val createV19 = createV20
                .replace(", `rateType` TEXT", "")
                .replace(", `pairedEntryId` INTEGER", "")
            assertTrue("could not strip the v20 columns from: $createV20",
                !createV19.contains("rateType") && !createV19.contains("pairedEntryId"))

            val v19Columns = raw.rawQuery("PRAGMA table_info(transactions)", null).use { c ->
                buildList {
                    while (c.moveToNext()) {
                        val col = c.getString(c.getColumnIndexOrThrow("name"))
                        if (col != "rateType" && col != "pairedEntryId") add("`$col`")
                    }
                }
            }.joinToString(", ")

            raw.execSQL("ALTER TABLE transactions RENAME TO transactions_v20")
            raw.execSQL(createV19)
            raw.execSQL("INSERT INTO transactions ($v19Columns) SELECT $v19Columns FROM transactions_v20")
            raw.execSQL("DROP TABLE transactions_v20")
            indices.forEach { raw.execSQL(it) }

            // v21 added supplier_bills.photoPath; a v19 file never had it, so
            // the chain's 20→21 must find it absent. The table is empty here,
            // so it is simply rebuilt from its own CREATE minus that column.
            val billsV21 = sqlOf("table", "supplier_bills").single()
            val billIndices = sqlOf("index", "supplier_bills")
            val billsV20 = billsV21.replace(", `photoPath` TEXT", "")
            assertTrue("could not strip photoPath from: $billsV21", !billsV20.contains("photoPath"))
            raw.execSQL("DROP TABLE supplier_bills")
            raw.execSQL(billsV20)
            billIndices.forEach { raw.execSQL(it) }

            raw.version = 19
        }

        // 3. Open through the real chain. Room runs 19→20, then validates.
        val db = open()
        try {
            val dao = db.khataDao()

            val line = dao.itemsOfEntry(goodsId).single()
            assertEquals("Urea", line.itemName)
            assertEquals(2.0, line.quantity!!, 0.0)
            assertEquals("bori", line.unit)
            assertEquals(0, line.lineNo)
            assertNull("no rate is invented for an old entry", line.rate)

            assertEquals(3.0, dao.itemsOfEntry(qtyOnlyId).single().quantity!!, 0.0)
            assertTrue("a money entry gets no line", dao.itemsOfEntry(moneyId).isEmpty())
            assertEquals(
                "a binned entry keeps its goods for a restore",
                "Sulphur", dao.itemsOfEntry(binnedId).single().itemName
            )
            assertEquals(3, dao.allEntryItemsForBackup().size)

            // New columns exist and start empty.
            assertNull(dao.getEntry(goodsId)!!.rateType)
            assertNull(dao.getEntry(goodsId)!!.pairedEntryId)

            // Money is untouched: 9,600 + 300 − 5,000 (the binned 750 excluded).
            val balance = dao.partiesWithBalanceOnce().single { it.id == ahmad }.balance
            assertEquals(4900.0, balance, 0.001)
        } finally {
            db.close()
        }
    }
}
