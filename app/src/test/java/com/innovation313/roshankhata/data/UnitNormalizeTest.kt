package com.innovation313.roshankhata.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [UnitWords.normalizeStored] against a real database (Robolectric runs
 * native SQLite). It swallows its own errors so that a book always opens,
 * which means a wrong table or column name would fail in silence; the first
 * test is there so that it cannot.
 */
@RunWith(AndroidJUnit4::class)
class UnitNormalizeTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: KhataDatabase

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java).build()
    }

    @After
    fun close() = db.close()

    private fun savedUnits(): List<String?> {
        val out = mutableListOf<String?>()
        db.openHelper.readableDatabase.query("SELECT unit FROM entry_items ORDER BY id").use { c ->
            while (c.moveToNext()) out.add(if (c.isNull(0)) null else c.getString(0))
        }
        return out
    }

    @Test
    fun everyPlaceAUnitIsSavedExistsInTheSchema() {
        for ((table, column) in UnitWords.STORED_IN) {
            db.openHelper.readableDatabase.query("SELECT $column FROM $table LIMIT 1").close()
        }
    }

    @Test
    fun handTypedLabelsBecomeTheKeyAndNothingElseIsTouched() = runBlocking {
        val dao = db.khataDao()
        val ahmad = dao.insertParty(Party(name = "Ahmad"))
        dao.insertEntryWithItems(
            LedgerEntry(partyId = ahmad, amount = 100.0, isGiven = true, entryNumber = "", timestamp = 1_000L),
            listOf(
                EntryItem(entryId = 0, itemName = "Urea", quantity = 2.0, unit = "بوری", rate = 10.0),
                EntryItem(entryId = 0, itemName = "DAP", quantity = 1.0, unit = "Bori", rate = 10.0),
                EntryItem(entryId = 0, itemName = "Zinc", quantity = 1.0, unit = "बोरी", rate = 10.0),
                EntryItem(entryId = 0, itemName = "Potash", quantity = 1.0, unit = "bag", rate = 10.0),
                EntryItem(entryId = 0, itemName = "Ghee", quantity = 1.0, unit = "tin", rate = 10.0),
                EntryItem(entryId = 0, itemName = "Rope", quantity = 1.0, unit = "Bag", rate = 10.0),
                EntryItem(entryId = 0, itemName = "Oil", quantity = 1.0, unit = "کلو", rate = 10.0)
            )
        )

        UnitWords.normalizeStored(db.openHelper.writableDatabase)

        // Three spellings of one unit are one unit; a key, the owner's own
        // word and his own capital letter are exactly as they were.
        assertEquals(listOf<String?>("bag", "bag", "bag", "bag", "tin", "Bag", "kg"), savedUnits())

        // Running it again changes nothing.
        UnitWords.normalizeStored(db.openHelper.writableDatabase)
        assertEquals(listOf<String?>("bag", "bag", "bag", "bag", "tin", "Bag", "kg"), savedUnits())
    }
}
