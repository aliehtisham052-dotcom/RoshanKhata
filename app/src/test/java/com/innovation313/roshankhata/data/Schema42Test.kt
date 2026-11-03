package com.innovation313.roshankhata.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Schema 42 (3 Nov 2026, the speed work): one covering index in place of (partyId, isDeleted, timestamp).
 * A version-41 book opens at 42 with every row as it was — the balances, who owes and his lines read the
 * same — the covering index in place and the old one gone; and the balances are read off the index alone.
 */
@RunWith(AndroidJUnit4::class)
class Schema42Test {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun aVersion41BookOpensAt42WithItsRowsAndTheCoveringIndex() = runBlocking {
        val name = "migration_41_42_test.db"
        context.deleteDatabase(name)
        val file = Room.databaseBuilder(context, KhataDatabase::class.java, name).addMigrations(*ALL_MIGRATIONS).build()
        val d = file.khataDao()
        val ali = d.insertParty(Party(name = "Ali"))
        val bilal = d.insertParty(Party(name = "Bilal"))
        d.insertEntryWithItems(LedgerEntry(partyId = ali, amount = 900.0, isGiven = true, entryNumber = ""), emptyList())
        d.insertEntryWithItems(LedgerEntry(partyId = ali, amount = 250.5, isGiven = false, entryNumber = ""), emptyList())
        d.insertEntryWithItems(LedgerEntry(partyId = bilal, amount = 300.0, isGiven = false, entryNumber = ""), emptyList())
        val balancesBefore = d.partiesWithBalanceOnce().associate { it.name to it.balance }
        val owingBefore = d.observeOwingLedgerPoints().first()
        file.close()
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
            SchemaRewind.undo42(raw); raw.version = 41
        }
        val again = Room.databaseBuilder(context, KhataDatabase::class.java, name).addMigrations(*ALL_MIGRATIONS).build()
        try {
            val a = again.khataDao()
            assertEquals(mapOf("Ali" to 649.5, "Bilal" to -300.0), balancesBefore)
            assertEquals(balancesBefore, a.partiesWithBalanceOnce().associate { it.name to it.balance })
            assertEquals("who owes and his lines, as before", owingBefore, a.observeOwingLedgerPoints().first())
            assertEquals(listOf(ali, ali), owingBefore.map { it.partyId })
            val db = again.openHelper.readableDatabase
            val indexes = db.query("SELECT name FROM sqlite_master WHERE type = 'index'").use { c ->
                buildSet { while (c.moveToNext()) add(c.getString(0)) }
            }
            for (idx in Schema42.newIndices) assertTrue("$idx missing from $indexes", idx in indexes)
            assertFalse("the index 42 replaces is still there", Schema42.REPLACED in indexes)
            // Every party's balance is read off the covering index, never the table.
            val plan = db.query(
                "EXPLAIN QUERY PLAN SELECT p.id, COALESCE(SUM(CASE WHEN t.isGiven = 1 THEN t.amount ELSE -t.amount END), 0) " +
                    "FROM parties p LEFT JOIN transactions t ON t.partyId = p.id AND t.isDeleted = 0 WHERE p.isDeleted = 0 GROUP BY p.id"
            ).use { c -> buildList { while (c.moveToNext()) add(c.getString(3)) } }
            assertTrue("the balances read the table: $plan", plan.any { it.contains("COVERING INDEX ${Schema42.COVERING}") })
        } finally { again.close(); context.deleteDatabase(name) }
    }
}
