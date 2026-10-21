package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Live sync's engine between two real books (live sync Marhala 1): "phone"
 * and "pc", each its own SQLite file, as on two devices. A change written in
 * one arrives in the other with the same uid, its pointers turned into the
 * other's own ids; nothing arrives twice or comes back; a change made on both
 * at once ends the same on both; the fingerprints then agree.
 */
class LiveSyncTest {

    private lateinit var dir: File
    private lateinit var phone: KhataDatabase
    private lateinit var pc: KhataDatabase
    private val phoneSql get() = phone.syncSql()
    private val pcSql get() = pc.syncSql()

    /** How far each side has read the other's log. */
    private var pcReadPhone = 0L
    private var phoneReadPc = 0L

    @Before
    fun open() {
        dir = Files.createTempDirectory("rk-live").toFile()
        phone = DesktopDatabase.open(dir.resolve("phone.db"))
        pc = DesktopDatabase.open(dir.resolve("pc.db"))
        runBlocking {
            val id = LiveSync.enable(phoneSql, install = "install-phone", name = "Phone")
            LiveSync.enable(pcSql, install = "install-pc", name = "PC", adopt = id.book to id.key)
        }
    }

    @After
    fun close() {
        phone.close(); pc.close()
        dir.deleteRecursively()
    }

    /** The phone's changes to the computer, then the computer's to the phone, as one live round does. */
    private suspend fun round() {
        val toPc = LiveSync.export(phoneSql, pcReadPhone)
        val a = LiveSync.apply(pcSql, toPc, recv = pcReadPhone, sentToThem = phoneReadPc)
        assertTrue("$a", a is LiveSync.Applied.Done)
        pcReadPhone = (a as LiveSync.Applied.Done).to
        val toPhone = LiveSync.export(pcSql, phoneReadPc)
        // The phone has read what the computer just sent? Not yet: the computer's changes are unseen by it until here.
        val b = LiveSync.apply(phoneSql, toPhone, recv = phoneReadPc, sentToThem = pcReadPhone)
        assertTrue("$b", b is LiveSync.Applied.Done)
        phoneReadPc = (b as LiveSync.Applied.Done).to
    }

    @Test
    fun `a customer, an entry and its goods arrive whole, pointing at the computer's own rows`() = runBlocking {
        val d = phone.khataDao()
        val ali = d.insertParty(Party(name = "Ali", phone = "0300 1234567"))
        // A row on the computer first, so its ids and the phone's differ.
        pc.khataDao().insertParty(Party(name = "Someone else on the PC only"))
        d.insertEntryWithItems(
            LedgerEntry(partyId = ali, amount = 900.0, isGiven = true, entryNumber = ""),
            listOf(EntryItem(entryId = 0, itemName = "Urea", quantity = 2.0, rate = 450.0))
        )
        round()
        val p = pc.khataDao()
        val aliHere = p.allPartiesForBackup().single { it.name == "Ali" }
        assertEquals(phone.khataDao().getParty(ali)!!.uid, aliHere.uid)
        val entry = p.entriesPageForBackup(0, 10).single()
        assertEquals(aliHere.id, entry.partyId)
        assertEquals(900.0, entry.amount, 0.0)
        val line = p.allEntryItemsForBackup().single()
        assertEquals(entry.id, line.entryId)
        assertEquals("Urea", line.itemName)
        assertEquals(900.0, p.partiesWithBalanceOnce().single { it.name == "Ali" }.balance, 0.001)
    }

    @Test
    fun `what a parcel wrote is not sent back, and a second round sends nothing`() = runBlocking {
        phone.khataDao().insertParty(Party(name = "Ali"))
        round()
        assertEquals(0, LiveSync.export(pcSql, 0).changes.size)
        val again = LiveSync.export(phoneSql, pcReadPhone)
        assertEquals(0, again.changes.size)
    }

    @Test
    fun `a change and a removal on the computer reach the phone`() = runBlocking {
        val id = phone.khataDao().insertParty(Party(name = "Ali"))
        round()
        val p = pc.khataDao()
        val here = p.allPartiesForBackup().single()
        p.updateParty(here.copy(name = "Ali Raza"))
        round()
        assertEquals("Ali Raza", phone.khataDao().getParty(id)!!.name)
        pcSql.write { exec("DELETE FROM parties WHERE id = ?", listOf(here.id)) }
        round()
        assertNull(phone.khataDao().getParty(id))
    }

    @Test
    fun `the same row changed on both ends the same on both, and the loss is kept`() = runBlocking {
        val id = phone.khataDao().insertParty(Party(name = "Ali"))
        round()
        val here = pc.khataDao().allPartiesForBackup().single()
        phone.khataDao().updateParty(phone.khataDao().getParty(id)!!.copy(name = "Ali (phone)"))
        pc.khataDao().updateParty(here.copy(name = "Ali (pc)"))
        round()
        round()
        val onPhone = phone.khataDao().getParty(id)!!.name
        val onPc = pc.khataDao().getParty(here.id)!!.name
        assertEquals(onPhone, onPc)
        assertTrue(LiveSync.conflicts(phoneSql).isNotEmpty() || LiveSync.conflicts(pcSql).isNotEmpty())
        assertEquals(LiveSync.fingerprint(phoneSql), LiveSync.fingerprint(pcSql))
    }

    @Test
    fun `a product made on both by the same name becomes one product with one uid`() = runBlocking {
        phone.khataDao().insertProduct(Product(name = "Urea", nameKey = "urea", normalisedName = "urea", salePrice = 4500.0))
        pc.khataDao().insertProduct(Product(name = "Urea", nameKey = "urea", normalisedName = "urea", salePrice = 4600.0))
        round()
        round()
        val a = phone.khataDao().allProductsForBackup().single()
        val b = pc.khataDao().allProductsForBackup().single()
        assertEquals(a.uid, b.uid)
        assertEquals(a.salePrice, b.salePrice)
        assertEquals(LiveSync.fingerprint(phoneSql), LiveSync.fingerprint(pcSql))
    }

    @Test
    fun `a parcel that starts after a line never read asks for it again`() = runBlocking {
        phone.khataDao().insertParty(Party(name = "Ali"))
        phone.khataDao().insertParty(Party(name = "Bilal"))
        val all = LiveSync.export(phoneSql, 0)
        val later = all.copy(from = 1)
        val r = LiveSync.apply(pcSql, later, recv = 0, sentToThem = 0)
        assertEquals(LiveSync.Applied.Gap(0), r)
        assertTrue(pc.khataDao().allPartiesForBackup().isEmpty())
    }

    @Test
    fun `another book's parcel is refused and writes nothing`() = runBlocking {
        phone.khataDao().insertParty(Party(name = "Ali"))
        val p = LiveSync.export(phoneSql, 0).copy(book = "f".repeat(32))
        val r = LiveSync.apply(pcSql, p, recv = 0, sentToThem = 0)
        assertTrue(r is LiveSync.Applied.Refused)
        assertTrue(pc.khataDao().allPartiesForBackup().isEmpty())
    }

    @Test
    fun `a first copy brings every row, and the two books then match`() = runBlocking {
        val d = phone.khataDao()
        val ali = d.insertParty(Party(name = "Ali"))
        d.insertEntryWithItems(LedgerEntry(partyId = ali, amount = 500.0, isGiven = true, entryNumber = ""), emptyList())
        d.insertCashEntry(CashEntry(amount = 50.0, isIncome = false, category = "Tea"))
        d.setSetting(BookSettings.PRICES_INCLUDE_TAX, "1")
        val copy = LiveSync.snapshot(phoneSql)
        val r = LiveSync.apply(pcSql, copy.copy(from = 0), recv = 0, sentToThem = 0)
        assertTrue("$r", r is LiveSync.Applied.Done)
        assertEquals("1", pc.khataDao().settingValue(BookSettings.PRICES_INCLUDE_TAX))
        assertEquals(LiveSync.fingerprint(phoneSql), LiveSync.fingerprint(pcSql))
    }

    @Test
    fun `turned off, a write is no longer noted`() = runBlocking {
        LiveSync.disable(phoneSql)
        phone.khataDao().insertParty(Party(name = "Ali"))
        assertEquals(0L, LiveSync.lastSeq(phoneSql))
        assertNull(LiveSync.identity(phoneSql))
        assertFalse(LiveSync.check(phoneSql, "install-phone"))
    }

    @Test
    fun `a book file opened by another install stops noting writes`() = runBlocking {
        assertTrue(LiveSync.check(phoneSql, "install-phone"))
        assertFalse(LiveSync.check(phoneSql, "someone-else"))
        val before = LiveSync.lastSeq(phoneSql)
        phone.khataDao().insertParty(Party(name = "Ali"))
        assertEquals(before, LiveSync.lastSeq(phoneSql))
    }

    @Test
    fun `the tables a sync carries are exactly the tables with a uid, and the settings`() {
        assertEquals(Schema38.uidTables.toSet(), SyncSchema.uidTables.map { it.name }.toSet())
        assertNotNull(SyncSchema.table(SyncSchema.SETTINGS))
    }
}
