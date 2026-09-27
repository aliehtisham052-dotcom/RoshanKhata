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
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE
        ).use { raw ->
            raw.execSQL("DROP TABLE entry_items")
            raw.execSQL("ALTER TABLE transactions DROP COLUMN rateType")
            raw.execSQL("ALTER TABLE transactions DROP COLUMN pairedEntryId")
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
