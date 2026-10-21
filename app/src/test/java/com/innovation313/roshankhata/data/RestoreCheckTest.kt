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
 * Restore safety (21 Oct 2026): before a copy replaces the book, the work
 * done here since the copy is counted — by uid for a format-12 copy, by what
 * the rows say for an older one.
 */
@RunWith(AndroidJUnit4::class)
class RestoreCheckTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: KhataDatabase
    private lateinit var dao: KhataDao
    private var ali = 0L

    @Before
    fun open(): Unit = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java).build()
        dao = db.khataDao()
        ali = dao.insertParty(Party(name = "Ali"))
        dao.insertEntryNumbered(LedgerEntry(partyId = ali, amount = 500.0, isGiven = true, entryNumber = "", timestamp = 1_000L))
        dao.insertEntryNumbered(LedgerEntry(partyId = ali, amount = 200.0, isGiven = false, entryNumber = "", timestamp = 2_000L))
        dao.insertCashEntry(CashEntry(amount = 50.0, isIncome = false, category = "Tea", timestamp = 3_000L))
    }

    @After
    fun close() = db.close()

    private suspend fun copy(): String {
        val out = java.io.StringWriter()
        BackupCodec.exportTo(dao, "T", org.json.JSONObject(), out)
        return out.toString()
    }

    @Test
    fun aCopyOfThisVeryBookLosesNothing() = runBlocking {
        val (_, parsed) = BackupCodec.parseText(copy())
        val loss = RestoreCheck.of(dao, parsed!!)
        assertTrue(loss.toString(), loss.none)
        assertEquals(3_000L, loss.newestHere)
        assertEquals(3_000L, loss.newestThere)
    }

    @Test
    fun workDoneAfterTheCopyIsCountedAndAnEditIsSeen() = runBlocking {
        val (_, parsed) = BackupCodec.parseText(copy())
        // After the copy: a new entry, a cashbook row, a new customer, and an edit of an old entry.
        val bilal = dao.insertParty(Party(name = "Bilal"))
        dao.insertEntryNumbered(LedgerEntry(partyId = bilal, amount = 900.0, isGiven = true, entryNumber = "", timestamp = 4_000L))
        dao.insertCashEntry(CashEntry(amount = 75.0, isIncome = true, category = "Sale", timestamp = 5_000L))
        val first = dao.entriesPageForBackup(0, 10).first { it.amount == 500.0 }
        dao.updateEntry(first.copy(amount = 550.0))
        val loss = RestoreCheck.of(dao, parsed!!)
        assertEquals(1, loss.entries)
        assertEquals(1, loss.changed)
        assertEquals(1, loss.cash)
        assertEquals(1, loss.parties)
        assertEquals(5_000L, loss.newestHere)
        assertEquals(3_000L, loss.newestThere)
        assertTrue(RestoreCheck.lines(loss, object : com.innovation313.roshankhata.words.Words {
            override fun text(name: String, vararg args: Any) = name + args.joinToString(",", "(", ")")
            override fun plural(name: String, count: Int, vararg args: Any) = name
            override fun list(name: String) = emptyList<String>()
        }) { it.toString() }.any { it.startsWith("restore_loss_entries(1") })
    }

    @Test
    fun anOlderCopyWithoutUidsIsMatchedByWhatItsRowsSay() = runBlocking {
        val old = copy().replace(Regex("\"uid\": ?\"[0-9a-f]{32}\""), "\"uid\": null").replace("\"version\": ${BackupCodec.FORMAT_VERSION}", "\"version\": 11")
        val (_, parsed) = BackupCodec.parseText(old)
        assertEquals(11, parsed!!.version)
        assertTrue("the same rows, matched by content", RestoreCheck.of(dao, parsed).none)
        dao.insertEntryNumbered(LedgerEntry(partyId = ali, amount = 10.0, isGiven = true, entryNumber = "", timestamp = 9_000L))
        assertEquals(1, RestoreCheck.of(dao, parsed).entries)
    }
}
