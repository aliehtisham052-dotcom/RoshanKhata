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
 * A backup, restored, gives back the same ledger.
 *
 * This is the promise the whole app rests on. Everything else can be redone
 * by hand; a backup that parses but comes back short cannot, and the owner
 * finds out at the worst moment — after a lost phone, with the old one gone.
 *
 * Both halves are Android code: the SQL that reads every table, and org.json,
 * which on a plain JVM is only a stub that throws. Under Robolectric both are
 * real — native SQLite, and the Android framework's own org.json — so this
 * path, which could not be tested at all before, now runs on every push.
 *
 * Two in-memory databases stand in for two phones — the old one and the new
 * one. Neither is ever written anywhere.
 */
@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private lateinit var oldPhone: KhataDatabase
    private lateinit var newPhone: KhataDatabase

    @Before
    fun open() {
        oldPhone = inMemory()
        newPhone = inMemory()
    }

    @After
    fun close() {
        oldPhone.close()
        newPhone.close()
    }

    private fun inMemory() = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        KhataDatabase::class.java
    ).build()

    @Test
    fun everyCustomerAndEntrySurvivesTheRoundTrip() = runBlocking {
        val from = oldPhone.khataDao()

        val ali = from.insertParty(Party(name = "Ali", phone = "03001234567"))
        val sara = from.insertParty(Party(name = "Sara", isCustomer = false))
        from.insertEntry(
            LedgerEntry(partyId = ali, amount = 5000.0, isGiven = true, entryNumber = "RK-000001")
        )
        from.insertEntry(
            LedgerEntry(partyId = ali, amount = 2000.0, isGiven = false, entryNumber = "RK-000002")
        )
        from.insertEntry(
            LedgerEntry(partyId = sara, amount = 750.5, isGiven = true, entryNumber = "RK-000003")
        )

        val json = Backup.export(context, from)
        val (result, parsed) = Backup.parseText(json)

        assertTrue("the backup did not parse: $result", result is Backup.ImportResult.Ok)
        val data = requireNotNull(parsed) { "parse said Ok but returned nothing" }
        assertEquals(2, data.parties.size)
        assertEquals(3, data.entries.size)

        val to = newPhone.khataDao()
        Backup.restore(context, to, data)

        // Balances, not just row counts: a restore that loses which way an
        // entry pointed would keep every row and still hand back the wrong
        // money.
        val restored = to.partiesWithBalanceOnce().associateBy { it.name }
        assertEquals(3000.0, restored.getValue("Ali").balance, 0.001)
        assertEquals(750.5, restored.getValue("Sara").balance, 0.001)

        // And the details beside the name, which are what make the entry
        // usable at all.
        assertEquals("03001234567", restored.getValue("Ali").phone)
    }

    @Test
    fun aBinnedCustomerComesBackStillBinned() = runBlocking {
        val from = oldPhone.khataDao()
        from.insertParty(Party(name = "Still Here"))
        from.insertParty(Party(name = "Was Binned", isDeleted = true, deletedAt = 1L))

        val (_, parsed) = Backup.parseText(Backup.export(context, from))
        val data = requireNotNull(parsed)
        Backup.restore(context, newPhone.khataDao(), data)

        // Restoring must not quietly resurrect someone the owner deleted: the
        // Recycle Bin is a decision, and a backup carries decisions too.
        val names = newPhone.khataDao().partiesWithBalanceOnce().map { it.name }
        assertTrue("the live customer was lost", "Still Here" in names)
        assertTrue("a binned customer came back to the main list", "Was Binned" !in names)
    }

    @Test
    fun anEmptyLedgerBacksUpAndRestoresWithoutFailing() = runBlocking {
        // The very first backup a new owner makes. It has to be a valid file,
        // not a crash and not a Failed parse.
        val json = Backup.export(context, oldPhone.khataDao())
        val (result, parsed) = Backup.parseText(json)

        assertTrue("an empty backup did not parse: $result", result is Backup.ImportResult.Ok)
        Backup.restore(context, newPhone.khataDao(), requireNotNull(parsed))
        assertEquals(0, newPhone.khataDao().partiesWithBalanceOnce().size)
    }
}
