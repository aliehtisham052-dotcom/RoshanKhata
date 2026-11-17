package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * One held ticket is rung up once (17 Nov 2026, the audit's L002), between two real books ("phone" and "pc") that sync
 * the way a WiFi round does ([LiveSync.applyFrom], as LiveSyncReviewTest): a sale names the held ticket it came from
 * ([Sale.heldUid]) and a ticket parked again keeps the first name; a held ticket another device rang up is refused
 * here — rung up or parked again — even when that device's drop has not arrived; and one rung up on both devices before
 * either heard of the other is one conflict, the same row, on both, for the bell's money row. Made-up names and figures.
 */
class HeldTicketSyncTest {

    private lateinit var dir: File
    private lateinit var phone: KhataDatabase
    private lateinit var pc: KhataDatabase
    private val phoneSql get() = phone.syncSql()
    private val pcSql get() = pc.syncSql()
    private lateinit var phoneId: LiveSync.Identity
    private lateinit var pcId: LiveSync.Identity

    @Before
    fun open() {
        Access.resetForTest()
        dir = Files.createTempDirectory("rk-held-sync").toFile()
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
        Access.resetForTest()
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

    private val soap = listOf(SaleItem(saleId = 0, itemName = "Soap", quantity = 1.0, rate = 500.0))

    private suspend fun hold(db: KhataDatabase, dropId: Long? = null): Long =
        db.khataDao().holdSale(Sale(subtotal = 0.0, grandTotal = 0.0, paidTotal = 0.0), soap, dropId)

    private suspend fun post(db: KhataDatabase, heldId: Long?): Long = db.khataDao().postSale(
        Sale(subtotal = 0.0, grandTotal = 0.0, paidTotal = 0.0),
        soap, listOf(SalePayment(saleId = 0, method = PaymentMethod.CASH, amount = 500.0)), heldId = heldId
    )

    private suspend fun heldOn(db: KhataDatabase): Long = db.khataDao().heldSalesOnce().single().id

    private suspend fun refused(what: String, block: suspend () -> Unit) {
        try {
            block(); fail(what)
        } catch (e: CounterRefused) {
            assertEquals(CounterRefused.Why.HELD_GONE, e.why)
        }
    }

    @Test
    fun `a sale names the held ticket it came from, and a ticket parked again keeps the first name`() = runBlocking {
        val dao = phone.khataDao()
        val first = hold(phone)
        val firstUid = dao.getSale(first)!!.uid!!
        assertNull("a ticket parked fresh names nothing", dao.getSale(first)!!.heldUid)
        val again = hold(phone, dropId = first)
        assertEquals(firstUid, dao.getSale(again)!!.heldUid)
        val sale = post(phone, again)
        assertEquals(firstUid, dao.getSale(sale)!!.heldUid)
        // A ticket rung up fresh names no held ticket, whatever the caller's row said.
        val fresh = dao.postSale(
            Sale(subtotal = 0.0, grandTotal = 0.0, paidTotal = 0.0, heldUid = "made-up"), soap,
            listOf(SalePayment(saleId = 0, method = PaymentMethod.CASH, amount = 500.0))
        )
        assertNull(dao.getSale(fresh)!!.heldUid)
    }

    @Test
    fun `a held ticket rung up on the other device is refused here, before its drop arrives`() = runBlocking {
        hold(phone)
        round()
        val onPc = heldOn(pc)
        post(phone, heldOn(phone))
        // Only the phone's sale reaches the computer, not yet its drop of the held ticket (a parcel in two parts).
        val sale = phone.khataDao().allSalesForBackup().single { it.status == SaleStatus.DONE }
        pc.khataDao().insertSale(sale.copy(id = 0, ledgerEntryId = null))
        assertTrue("still on the computer's hook", pc.khataDao().heldSalesOnce().any { it.id == onPc })
        refused("rung up twice") { post(pc, onPc) }
        refused("parked again after it was rung up") { hold(pc, dropId = onPc) }
        assertEquals(1, pc.khataDao().allSalesForBackup().count { it.status == SaleStatus.DONE })
    }

    @Test
    fun `a held ticket rung up on both devices before they met is one conflict, the same row on both`() = runBlocking {
        hold(phone)
        round()
        // Apart for a while: each counter resumes the parked ticket and rings it up.
        post(phone, heldOn(phone))
        post(pc, heldOn(pc))
        assertEquals(0, LiveSync.moneyConflicts(pcSql))
        round()
        val done = phone.khataDao().allSalesForBackup().filter { it.status == SaleStatus.DONE && !it.isDeleted }
        assertEquals("both tickets stand: money was taken at both counters", 2, done.size)
        val second = done.mapNotNull { it.uid }.sorted()[1]
        for (sql in listOf(phoneSql, pcSql)) {
            val c = LiveSync.conflicts(sql).filter { it.kept == "both" }
            assertEquals(listOf("sales"), c.map { it.table })
            assertEquals(second, c.single().uid)
            assertTrue(LiveSync.moneyConflicts(sql) >= 1)
            assertTrue(LiveSync.conflictViews(sql).any { v -> v.fields.any { it.first == "problem" } })
        }
        // Noted once: a later round touching the tickets does not note it again.
        phone.khataDao().updateSale(phone.khataDao().allSalesForBackup().first { it.status == SaleStatus.DONE }.copy(note = "seen"))
        round()
        assertEquals(1, LiveSync.conflicts(phoneSql).count { it.kept == "both" })
        assertEquals(1, LiveSync.conflicts(pcSql).count { it.kept == "both" })
    }
}
