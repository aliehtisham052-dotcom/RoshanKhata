package com.innovation313.roshankhata.data

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
 * A customer's balance, computed by SQLite itself.
 *
 * The balance is the one number in this app that must never be wrong, and it
 * is not computed in Kotlin — it is a SUM inside a Room @Query. The unit
 * tests cannot reach it: Room generates that code at build time and it needs
 * a real SQLite engine to run, which only a device has. So the arithmetic
 * the owner trusts has been checked by hand, on a phone, and never by the
 * build.
 *
 * The database here is IN-MEMORY: it exists only for the length of this test
 * and is never written to disk, so nothing this file does can touch the real
 * ledger on the device it runs on.
 */
@RunWith(AndroidJUnit4::class)
class LedgerBalanceOnDeviceTest {

    private lateinit var db: KhataDatabase
    private lateinit var dao: KhataDao

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java
        ).build()
        dao = db.khataDao()
    }

    @After
    fun close() = db.close()

    private fun entry(partyId: Long, amount: Double, given: Boolean, number: String) =
        LedgerEntry(
            partyId = partyId,
            amount = amount,
            isGiven = given,
            entryNumber = number
        )

    @Test
    fun givenMinusGotIsWhatTheCustomerOwes() = runBlocking {
        val id = dao.insertParty(Party(name = "Test Customer"))

        // Goods worth 5,000 went out; 2,000 came back. 3,000 is owed.
        dao.insertEntry(entry(id, 5000.0, given = true, number = "RK-000001"))
        dao.insertEntry(entry(id, 2000.0, given = false, number = "RK-000002"))

        val row = dao.partiesWithBalanceOnce().first { it.id == id }
        assertEquals(3000.0, row.balance, 0.001)
    }

    @Test
    fun aDeletedEntryIsNotCountedInTheBalance() = runBlocking {
        val id = dao.insertParty(Party(name = "Test Customer"))
        dao.insertEntry(entry(id, 5000.0, given = true, number = "RK-000001"))
        dao.insertEntry(
            entry(id, 9999.0, given = true, number = "RK-000002")
                .copy(isDeleted = true, deletedAt = System.currentTimeMillis())
        )

        // An entry in the Recycle Bin is still in the table. If the SUM ever
        // stopped excluding it, every balance in the app would silently
        // inflate — and the owner would chase money nobody owes.
        val row = dao.partiesWithBalanceOnce().first { it.id == id }
        assertEquals(5000.0, row.balance, 0.001)
    }

    @Test
    fun aDeletedCustomerIsNotInTheList() = runBlocking {
        val kept = dao.insertParty(Party(name = "Kept Customer"))
        val binned = dao.insertParty(
            Party(name = "Binned Customer", isDeleted = true, deletedAt = 1L)
        )

        val ids = dao.partiesWithBalanceOnce().map { it.id }
        assertEquals(true, kept in ids)
        assertEquals(false, binned in ids)
    }
}
