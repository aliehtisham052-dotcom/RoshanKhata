package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The live sync engine's answers to the 22 Oct 2026 review, between two real
 * books ("phone" and "pc"), each knowing the other as a peer the way a round
 * moves the pointers ([LiveSync.applyFrom]):
 *
 *  - a parcel sent twice cannot put an older row over a newer edit;
 *  - one change the book cannot take is kept aside, and the conversation goes on;
 *  - a removal (for good, or to the bin) wins over a change made meanwhile;
 *  - a photo's path alone is not a change;
 *  - a book file put back from an older copy stops syncing instead of losing changes;
 *  - lines already let go are never answered as if nothing were missing;
 *  - two devices on one letter move one of them; a failed restore changes nothing;
 *  - a scheme, a day's attendance and a day's close keep their identity when saved again.
 */
class LiveSyncReviewTest {

    private lateinit var dir: File
    private lateinit var phone: KhataDatabase
    private lateinit var pc: KhataDatabase
    private val phoneSql get() = phone.syncSql()
    private val pcSql get() = pc.syncSql()
    private lateinit var phoneId: LiveSync.Identity
    private lateinit var pcId: LiveSync.Identity

    @Before
    fun open() {
        dir = Files.createTempDirectory("rk-live-review").toFile()
        phone = DesktopDatabase.open(dir.resolve("phone.db"))
        pc = DesktopDatabase.open(dir.resolve("pc.db"))
        runBlocking {
            phoneId = LiveSync.enable(phoneSql, install = "install-phone", name = "Phone")
            pcId = LiveSync.enable(pcSql, install = "install-pc", name = "PC", adopt = phoneId.book to phoneId.key)
            LiveSync.putPeer(pcSql, phoneId.device, name = "Phone", sent = 0, recv = 0)
            LiveSync.putPeer(phoneSql, pcId.device, name = "PC", sent = 0, recv = 0)
        }
    }

    @After
    fun close() {
        runCatching { phone.close() }
        runCatching { pc.close() }
        dir.deleteRecursively()
    }

    private suspend fun peerOf(sql: SyncSql, device: String) = LiveSync.state(sql).peers.first { it.device == device }

    /** One round as the WiFi road runs it: the phone's changes in on the computer, the computer's back on the phone. */
    private suspend fun round() {
        val toPc = LiveSync.export(phoneSql, peerOf(phoneSql, pcId.device).sent)
        val a = LiveSync.applyFrom(pcSql, toPc, theirAck = peerOf(phoneSql, pcId.device).recv)
        assertTrue("$a", a is LiveSync.Applied.Done)
        val toPhone = LiveSync.export(pcSql, peerOf(phoneSql, pcId.device).recv)
        val b = LiveSync.applyFrom(phoneSql, toPhone, theirAck = peerOf(pcSql, phoneId.device).recv)
        assertTrue("$b", b is LiveSync.Applied.Done)
    }

    @Test
    fun `a parcel sent again does not put an older row over a newer edit`() = runBlocking {
        val ali = phone.khataDao().insertParty(Party(name = "Ali"))
        val first = LiveSync.export(phoneSql, 0)
        assertTrue(LiveSync.applyFrom(pcSql, first, theirAck = 0) is LiveSync.Applied.Done)
        // The computer corrects Ali after it took him; the phone never heard its ack (left the screen mid-round).
        val here = pc.khataDao().allPartiesForBackup().single()
        pc.khataDao().updateParty(here.copy(name = "Ali (corrected on the PC)"))
        phone.khataDao().insertParty(Party(name = "Bilal"))
        // The phone sends from 0 again: Ali's old line comes too, with Bilal's new one.
        val again = LiveSync.export(phoneSql, 0)
        assertTrue(again.changes.any { it.table == "parties" && it.uid == phone.khataDao().getParty(ali)!!.uid })
        val r = LiveSync.applyFrom(pcSql, again, theirAck = 0)
        assertTrue("$r", r is LiveSync.Applied.Done)
        val names = pc.khataDao().allPartiesForBackup().map { it.name }.toSet()
        assertEquals(setOf("Ali (corrected on the PC)", "Bilal"), names)
        // Nothing was a conflict: the old line was simply already written.
        assertTrue(LiveSync.conflicts(pcSql).isEmpty())
    }

    @Test
    fun `the pointers move in the same transaction as the parcel, and never back`() = runBlocking {
        phone.khataDao().insertParty(Party(name = "Ali"))
        val p = LiveSync.export(phoneSql, 0)
        LiveSync.applyFrom(pcSql, p, theirAck = 0)
        val after = peerOf(pcSql, phoneId.device).recv
        assertEquals(p.to, after)
        // The same parcel again (the relay and the WiFi both carried it): nothing moves backwards.
        LiveSync.applyFrom(pcSql, p.copy(), theirAck = 0)
        assertEquals(after, peerOf(pcSql, phoneId.device).recv)
        // A stranger's parcel is refused, and writes nothing.
        val stranger = p.copy(device = "0123456789abcdef")
        assertTrue(LiveSync.applyFrom(pcSql, stranger, theirAck = 0) is LiveSync.Applied.Refused)
    }

    @Test
    fun `one change the book cannot take is kept aside and the rest goes on`() = runBlocking {
        val dap = phone.khataDao().insertProduct(Product(name = "DAP", nameKey = ProductName.key("DAP"), normalisedName = ProductName.normalised("DAP")))
        round()
        // The computer makes "DAP 50kg" while the phone renames its DAP to the same name.
        pc.khataDao().insertProduct(Product(name = "DAP 50kg", nameKey = ProductName.key("DAP 50kg"), normalisedName = ProductName.normalised("DAP 50kg")))
        val mine = phone.khataDao().allProductsForBackup().single { it.id == dap }
        phone.khataDao().updateProduct(mine.copy(name = "DAP 50kg", nameKey = ProductName.key("DAP 50kg"), normalisedName = ProductName.normalised("DAP 50kg")))
        phone.khataDao().insertParty(Party(name = "Ali"))
        val p = LiveSync.export(phoneSql, peerOf(phoneSql, pcId.device).sent)
        val r = LiveSync.applyFrom(pcSql, p, theirAck = peerOf(phoneSql, pcId.device).recv)
        // Not the end of the conversation: Ali came, the clash waits aside, and the pointer moved on.
        assertTrue("$r", r is LiveSync.Applied.Done)
        assertTrue(pc.khataDao().allPartiesForBackup().any { it.name == "Ali" })
        assertEquals(1, LiveSync.state(pcSql).held)
        assertEquals(p.to, peerOf(pcSql, phoneId.device).recv)
        // And the next parcel still goes in.
        phone.khataDao().insertParty(Party(name = "Bilal"))
        val next = LiveSync.export(phoneSql, p.to)
        assertTrue(LiveSync.applyFrom(pcSql, next, theirAck = 0) is LiveSync.Applied.Done)
        assertTrue(pc.khataDao().allPartiesForBackup().any { it.name == "Bilal" })
    }

    @Test
    fun `what waits for its customer lands once the customer arrives`() = runBlocking {
        val d = phone.khataDao()
        val ali = d.insertParty(Party(name = "Ali"))
        d.insertEntryWithItems(LedgerEntry(partyId = ali, amount = 700.0, isGiven = true, entryNumber = ""), emptyList())
        // The entry alone first (as a page that ended before its customer would): it waits aside.
        val entryOnly = LiveSync.export(phoneSql, 1)
        assertTrue(entryOnly.changes.all { it.table != "parties" })
        val a = LiveSync.apply(pcSql, entryOnly, recv = 1, sentToThem = 0)
        assertTrue("$a", a is LiveSync.Applied.Done)
        assertEquals(1, LiveSync.state(pcSql).held)
        assertTrue(pc.khataDao().entriesPageForBackup(0, 10).isEmpty())
        // Then the customer: the entry kept aside is written with him, pointing at the computer's own id.
        val customer = LiveSync.export(phoneSql, 0, page = 1)
        val b = LiveSync.apply(pcSql, customer, recv = 0, sentToThem = 0)
        assertTrue("$b", b is LiveSync.Applied.Done)
        assertEquals(0, LiveSync.state(pcSql).held)
        val aliHere = pc.khataDao().allPartiesForBackup().single()
        assertEquals(aliHere.id, pc.khataDao().entriesPageForBackup(0, 10).single().partyId)
    }

    @Test
    fun `a customer put in the bin on one side wins over a change on the other`() = runBlocking {
        val id = phone.khataDao().insertParty(Party(name = "Ali"))
        round()
        val here = pc.khataDao().allPartiesForBackup().single()
        phone.khataDao().softDeleteParty(id)
        pc.khataDao().updateParty(here.copy(name = "Ali (pc)"))
        round()
        round()
        assertTrue(phone.khataDao().getParty(id)!!.isDeleted)
        assertTrue(pc.khataDao().getParty(here.id)!!.isDeleted)
        assertEquals(LiveSync.fingerprint(phoneSql), LiveSync.fingerprint(pcSql))
    }

    @Test
    fun `a row removed for good wins over a change made meanwhile`() = runBlocking {
        val id = phone.khataDao().insertParty(Party(name = "Ali"))
        round()
        val here = pc.khataDao().allPartiesForBackup().single()
        pcSql.write { exec("DELETE FROM parties WHERE id = ?", listOf(here.id)) }
        phone.khataDao().updateParty(phone.khataDao().getParty(id)!!.copy(name = "Ali Raza"))
        round()
        round()
        assertNull(phone.khataDao().getParty(id))
        assertTrue(pc.khataDao().allPartiesForBackup().isEmpty())
        // The owner can see the change that lost.
        assertTrue(LiveSync.conflicts(pcSql).isNotEmpty())
    }

    @Test
    fun `a photo's path alone is not a change`() = runBlocking {
        val id = phone.khataDao().insertParty(Party(name = "Ali"))
        val before = LiveSync.lastSeq(phoneSql)
        phone.khataDao().updateParty(phone.khataDao().getParty(id)!!.copy(photoPath = "/photos/ali.jpg"))
        assertEquals(before, LiveSync.lastSeq(phoneSql))
        phone.khataDao().updateParty(phone.khataDao().getParty(id)!!.copy(phone = "0300 1234567"))
        assertEquals(before + 1, LiveSync.lastSeq(phoneSql))
    }

    private class Marks : LiveSync.LogMarks {
        val m = HashMap<String, Long>()
        override fun get(book: String): Long? = m[book]
        override fun put(book: String, seq: Long) { m[book] = seq }
    }

    @Test
    fun `a book file put back from an older copy stops syncing and says so`() = runBlocking {
        val file = dir.resolve("own.db")
        val marks = Marks()
        var db = DesktopDatabase.open(file)
        LiveSync.enable(db.syncSql(), "install-own", "Phone", marks = marks)
        db.khataDao().insertParty(Party(name = "Ali"))
        LiveSync.noteMark(db.syncSql(), marks)
        db.close()
        val older = dir.resolve("older.db")
        file.copyTo(older)
        // More work, told to the other device (the mark moves past the copy).
        db = DesktopDatabase.open(file)
        assertTrue(LiveSync.check(db.syncSql(), "install-own", marks))
        db.khataDao().insertParty(Party(name = "Bilal"))
        db.khataDao().insertParty(Party(name = "Chaudhry"))
        LiveSync.noteMark(db.syncSql(), marks)
        db.close()
        // The older file put back (a daily copy restored): its log is behind what was told.
        older.copyTo(file, overwrite = true)
        File(file.path + "-wal").delete(); File(file.path + "-shm").delete()
        db = DesktopDatabase.open(file)
        try {
            assertFalse(LiveSync.check(db.syncSql(), "install-own", marks))
            assertNull(LiveSync.identity(db.syncSql()))
            assertEquals(LiveSync.OFF_RESTORED, LiveSync.offReason(db.syncSql()))
            // Joined again, it starts over and is not caught a second time.
            LiveSync.enable(db.syncSql(), "install-own", "Phone", marks = marks)
            assertTrue(LiveSync.check(db.syncSql(), "install-own", marks))
        } finally {
            db.close()
        }
    }

    @Test
    fun `lines already let go are never answered as if nothing were missing`() = runBlocking {
        val d = phone.khataDao()
        repeat(3) { d.insertParty(Party(name = "P$it")) }
        val top = LiveSync.lastSeq(phoneSql)
        LiveSync.putPeer(phoneSql, pcId.device, sent = top)
        LiveSync.prune(phoneSql)
        try {
            LiveSync.export(phoneSql, 1)
            fail("lines 2 and 3 were let go")
        } catch (e: LiveSync.Lost) {
            assertEquals(1L, e.after)
        }
        assertEquals(0, LiveSync.export(phoneSql, top).changes.size)
    }

    @Test
    fun `two devices on one letter move one of them, never to A`() = runBlocking {
        phoneSql.write { exec("UPDATE sync_meta SET v = 'K' WHERE k = 'mark'") }
        pcSql.write { exec("UPDATE sync_meta SET v = 'K' WHERE k = 'mark'") }
        val smaller = if (phoneId.device < pcId.device) phoneSql else pcSql
        val larger = if (smaller === phoneSql) pcSql else phoneSql
        val smallerOther = if (smaller === phoneSql) pcId.device else phoneId.device
        val largerOther = if (smaller === phoneSql) phoneId.device else pcId.device
        assertNull(LiveSync.settleMark(larger, largerOther, "K"))
        val moved = LiveSync.settleMark(smaller, smallerOther, "K")
        assertNotNull(moved)
        assertNotEquals("K", moved)
        assertNotEquals("A", moved)
        assertEquals(moved, LiveSync.mark(smaller))
        assertEquals("K", LiveSync.mark(larger))
    }

    @Test
    fun `a restore that fails leaves live sync exactly as it was`() = runBlocking {
        assertTrue(LiveSync.pause(phoneSql))
        assertNull(LiveSync.identity(phoneSql))
        val before = LiveSync.lastSeq(phoneSql)
        phone.khataDao().insertParty(Party(name = "Written while paused"))
        assertEquals(before, LiveSync.lastSeq(phoneSql))
        LiveSync.resume(phoneSql)
        assertNotNull(LiveSync.identity(phoneSql))
        assertEquals(1, LiveSync.state(phoneSql).peers.size)
        phone.khataDao().insertParty(Party(name = "Written after"))
        assertEquals(before + 1, LiveSync.lastSeq(phoneSql))
        // Left paused by a restore that never finished (the app closed): the book cannot be trusted to match.
        assertTrue(LiveSync.pause(phoneSql))
        assertFalse(LiveSync.check(phoneSql, "install-phone"))
        assertEquals(LiveSync.OFF_RESTORED, LiveSync.offReason(phoneSql))
    }

    @Test
    fun `a scheme, a day's attendance and a day's close keep their identity when saved again`() = runBlocking {
        val d = phone.khataDao()
        val id = d.saveScheme(
            Scheme(name = "Rabi offer", startDate = 0, endDate = 1, measure = SchemeMath.BY_VALUE, rewardKind = SchemeMath.PERCENT, target1 = 100.0, reward1 = 2.0)
        )
        val uid = d.allSchemesForBackup().single().uid
        // As the forms do: the scheme built afresh with the same id (and so a fresh uid of its own).
        d.saveScheme(Scheme(id = id, name = "Rabi offer (2%)", startDate = 0, endDate = 1, measure = SchemeMath.BY_VALUE, rewardKind = SchemeMath.PERCENT, target1 = 100.0, reward1 = 2.0))
        assertEquals(uid, d.allSchemesForBackup().single().uid)

        val munshi = d.insertStaff(Staff(name = "Munshi", monthlySalary = 25000.0))
        d.markAttendance(StaffAttendance(staffId = munshi, day = 20_000L, status = 1))
        val att = d.allAttendanceForBackup().single()
        d.markAttendance(StaffAttendance(staffId = munshi, day = 20_000L, status = 2))
        val att2 = d.allAttendanceForBackup().single()
        assertEquals(att.uid, att2.uid)
        assertEquals(2, att2.status)

        d.saveDayClose(DayClose(day = 20_000L, opening = 0.0, cashIn = 100.0, cashOut = 0.0, counted = 100.0))
        val close = d.allDayClosesForBackup().single()
        d.saveDayClose(DayClose(day = 20_000L, opening = 0.0, cashIn = 150.0, cashOut = 0.0, counted = 150.0))
        assertEquals(close.uid, d.allDayClosesForBackup().single().uid)

        // On the other device: one of each, as here.
        round()
        assertEquals(1, pc.khataDao().allSchemesForBackup().size)
        assertEquals("Rabi offer (2%)", pc.khataDao().allSchemesForBackup().single().name)
        assertEquals(1, pc.khataDao().allAttendanceForBackup().size)
        assertEquals(1, pc.khataDao().allDayClosesForBackup().size)
        assertEquals(LiveSync.fingerprint(phoneSql), LiveSync.fingerprint(pcSql))
    }
}
