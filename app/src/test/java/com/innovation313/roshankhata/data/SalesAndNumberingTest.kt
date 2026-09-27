package com.innovation313.roshankhata.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Two figures the owner reads as fact: what he sold, and the number printed
 * on a receipt. Both were wrong in a way that looked right.
 */
@RunWith(AndroidJUnit4::class)
class SalesAndNumberingTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: KhataDatabase
    private lateinit var dao: KhataDao

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java).build()
        dao = db.khataDao()
    }

    @After
    fun close() = db.close()

    @Test
    fun payingASupplierIsNotASale() = runBlocking {
        val ahmad = dao.insertParty(Party(name = "Ahmad"))
        val supplier = dao.insertParty(Party(name = "Bhatti Traders", isCustomer = false))

        dao.insertEntryWithItems(
            LedgerEntry(partyId = ahmad, amount = 19200.0, isGiven = true, entryNumber = "", timestamp = 1_000L),
            listOf(EntryItem(entryId = 0, itemName = "Urea", quantity = 2.0, unit = "bori", rate = 4800.0))
        )
        // The shop pays its supplier 50,000 — "I gave" on the supplier's account.
        dao.insertEntryWithItems(
            LedgerEntry(partyId = supplier, amount = 50000.0, isGiven = true, entryNumber = "", timestamp = 1_000L),
            emptyList()
        )
        // And sends two bags back to him — goods out, still not a sale.
        dao.insertEntryWithItems(
            LedgerEntry(partyId = supplier, amount = 1500.0, isGiven = true, entryNumber = "", timestamp = 1_000L),
            listOf(EntryItem(entryId = 0, itemName = "Sulphur", quantity = 2.0, unit = "bag", rate = 750.0))
        )

        assertEquals(19200.0, dao.salesTotalBetween(0, Long.MAX_VALUE), 0.001)
        assertEquals(1, dao.salesCountBetween(0, Long.MAX_VALUE))
        assertEquals(listOf("Ahmad"), dao.topCustomersBetween(0, Long.MAX_VALUE, 5).map { it.name })
        assertEquals(listOf("Urea"), dao.topProductsBetween(0, Long.MAX_VALUE, 5).map { it.name })

        // "Given today" is every party's outgoing, by design — not a sale figure.
        assertEquals(19200.0 + 50000.0 + 1500.0, dao.givenBetween(0, Long.MAX_VALUE), 0.001)
    }

    @Test
    fun aNumberStillOnTheBookIsNeverHandedOutAgain() = runBlocking {
        val ahmad = dao.insertParty(Party(name = "Ahmad"))
        fun e() = LedgerEntry(partyId = ahmad, amount = 100.0, isGiven = true, entryNumber = "")

        val ids = (1..3).map { dao.insertEntryNumbered(e()) }
        assertEquals(
            listOf("RK-000001", "RK-000002", "RK-000003"),
            ids.map { dao.getEntry(it)!!.entryNumber }
        )

        // Delete #1 for good. The old rule (count + 1) would now give
        // RK-000003 again — the number already printed on the third entry.
        dao.softDeleteEntry(ids[0])
        dao.purgeEntry(ids[0])
        val next = dao.insertEntryNumbered(e())
        assertEquals("RK-000004", dao.getEntry(next)!!.entryNumber)

        val numbers = dao.allEntriesForBackup().map { it.entryNumber }
        assertTrue("duplicate receipt numbers: $numbers", numbers.size == numbers.toSet().size)
    }

    @Test
    fun aBookWithNoNumbersYetStartsAtOne() = runBlocking {
        val ahmad = dao.insertParty(Party(name = "Ahmad"))
        val id = dao.insertEntryNumbered(
            LedgerEntry(partyId = ahmad, amount = 100.0, isGiven = true, entryNumber = "")
        )
        assertEquals("RK-000001", dao.getEntry(id)!!.entryNumber)
    }
}
