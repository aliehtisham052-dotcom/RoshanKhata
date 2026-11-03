package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Schema 43 (4 Nov 2026) between two real books ("phone" and "pc"), the way a live sync's round moves them: the
 * owner's private note on a party is part of the book and travels; a cashbook row's and a cheque's picture are files
 * on one device and do not — setting one writes nothing to the sync's log, the other device's row keeps its own, and
 * the two books still read as the same book. Two customers merged keep both notes. Made-up names and figures only.
 */
class NoteAndPhotosTest {

    private lateinit var dir: File
    private lateinit var phone: KhataDatabase
    private lateinit var pc: KhataDatabase
    private val phoneSql get() = phone.syncSql()
    private val pcSql get() = pc.syncSql()
    private lateinit var phoneId: LiveSync.Identity
    private lateinit var pcId: LiveSync.Identity

    @Before
    fun open() {
        dir = Files.createTempDirectory("rk-note-photos").toFile()
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

    private suspend fun round() {
        val toPc = LiveSync.export(phoneSql, peerOf(phoneSql, pcId.device).sent)
        val a = LiveSync.applyFrom(pcSql, toPc, theirAck = peerOf(phoneSql, pcId.device).recv)
        assertTrue("$a", a is LiveSync.Applied.Done)
        val toPhone = LiveSync.export(pcSql, peerOf(phoneSql, pcId.device).recv)
        val b = LiveSync.applyFrom(phoneSql, toPhone, theirAck = peerOf(pcSql, phoneId.device).recv)
        assertTrue("$b", b is LiveSync.Applied.Done)
    }

    @Test
    fun `a party's note travels, a cashbook row's and a cheque's picture stay on their device`() = runBlocking {
        val dao = phone.khataDao()
        val ali = dao.insertParty(Party(name = "Ali", note = "Pays on the 10th"))
        val cash = dao.insertCashEntry(CashEntry(amount = 300.0, isIncome = false, category = "Rent", photoPath = "/phone/bills/bill_1.jpg"))
        val cheque = dao.insertCheque(Cheque(partyId = ali, amount = 2_000.0, isReceived = true, dueDate = 1_000L, photoPath = "/phone/bills/bill_2.jpg"))
        round()
        val there = pc.khataDao()
        assertEquals("Pays on the 10th", there.allPartiesForBackup().single().note)
        assertEquals(300.0, there.allCashForBackup().single().amount, 0.0)
        assertNull("a path to the phone's file means nothing on the computer", there.allCashForBackup().single().photoPath)
        assertNull(there.allChequesForBackup().single().photoPath)

        // A picture set or changed afterwards is no change the other device hears of.
        val before = LiveSync.lastSeq(phoneSql)
        dao.setCashPhotoPath(cash, "/phone/bills/bill_3.jpg")
        dao.setChequePhotoPath(cheque, null)
        assertEquals(before, LiveSync.lastSeq(phoneSql))
        // The note is: edited on the computer, it reaches the phone.
        val pcAli = there.allPartiesForBackup().single()
        there.updateParty(pcAli.copy(note = "Pays on the 10th\nAsk for his son"))
        round()
        assertEquals("Pays on the 10th\nAsk for his son", dao.getParty(ali)?.note)
        // Each keeps its own pictures, and the two are still the same book.
        assertEquals("/phone/bills/bill_3.jpg", dao.allCashForBackup().single().photoPath)
        there.setCashPhotoPath(there.allCashForBackup().single().id, "C:/RoshanKhata/bills/bill_9.jpg")
        assertEquals(LiveSync.fingerprint(phoneSql), LiveSync.fingerprint(pcSql))
    }

    @Test
    fun `two customers merged keep both notes, said once`() = runBlocking {
        val dao = phone.khataDao()
        val kept = dao.insertParty(Party(name = "Ali Raza", note = "Only cash"))
        val twice = dao.insertParty(Party(name = "Ali Raza 2", note = "Brother runs the shop on Mill Road"))
        val third = dao.insertParty(Party(name = "Ali R.", note = "only cash"))
        dao.mergeParties(listOf(twice, third), kept)
        assertEquals("Only cash\nBrother runs the shop on Mill Road", dao.getParty(kept)?.note)
        // One with no note of its own takes the merged one's.
        val bare = dao.insertParty(Party(name = "Bilal"))
        val noted = dao.insertParty(Party(name = "Bilal 2", note = "Pays after the 1st"))
        dao.mergeParty(noted, bare)
        assertEquals("Pays after the 1st", dao.getParty(bare)?.note)
    }
}
