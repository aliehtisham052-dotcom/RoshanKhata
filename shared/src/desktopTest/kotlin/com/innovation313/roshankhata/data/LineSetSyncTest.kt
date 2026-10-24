package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * E8 (23 Oct 2026): the goods lines of one entry, invoice or purchase order
 * edited on two devices before a round. Every editor saves a whole new set of
 * lines (the old ones removed or put in the bin, the new ones with new uids),
 * so the two sets used to meet as rows nobody had seen — and both stood on
 * both books: the lines doubled while the fingerprints agreed.
 *
 * The rule now ([LiveSync], "line sets"): a parent's lines are one unit. When
 * both devices changed the lines of the same parent, the device the parent's
 * own rule picks (the bin, else the higher device id) keeps its whole set on
 * both books, and every line of the other set is kept in sync_conflict. Lines
 * changed on one device only still travel as before.
 *
 * Between two real books ("phone" and "pc"), in each order a round can run in
 * (the phone's parcel first, the computer's first, both at once).
 */
class LineSetSyncTest {

    private lateinit var dir: File
    private lateinit var phone: KhataDatabase
    private lateinit var pc: KhataDatabase
    private val phoneSql get() = phone.syncSql()
    private val pcSql get() = pc.syncSql()
    private lateinit var phoneId: LiveSync.Identity
    private lateinit var pcId: LiveSync.Identity

    @Before
    fun open() {
        dir = Files.createTempDirectory("rk-line-sets").toFile()
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

    /** The device the conflict rule keeps: the higher id. */
    private val phoneWins get() = phoneId.device > pcId.device

    private suspend fun peerOf(sql: SyncSql, device: String) = LiveSync.state(sql).peers.first { it.device == device }

    /** One device's changes in on the other, the pointers moved as a round moves them. */
    private suspend fun pass(from: SyncSql, to: SyncSql, toDevice: String) {
        val p = LiveSync.export(from, peerOf(from, toDevice).sent)
        val r = LiveSync.applyFrom(to, p, theirAck = peerOf(from, toDevice).recv)
        assertTrue("$r", r is LiveSync.Applied.Done)
    }

    private suspend fun phoneFirst() { pass(phoneSql, pcSql, pcId.device); pass(pcSql, phoneSql, phoneId.device) }
    private suspend fun pcFirst() { pass(pcSql, phoneSql, phoneId.device); pass(phoneSql, pcSql, pcId.device) }

    /** Both parcels made before either lands (the relay carried both at once), then written in. */
    private suspend fun atOnce() {
        val toPc = LiveSync.export(phoneSql, peerOf(phoneSql, pcId.device).sent)
        val ackPc = peerOf(phoneSql, pcId.device).recv
        val toPhone = LiveSync.export(pcSql, peerOf(pcSql, phoneId.device).sent)
        val ackPhone = peerOf(pcSql, phoneId.device).recv
        assertTrue(LiveSync.applyFrom(pcSql, toPc, theirAck = ackPc) is LiveSync.Applied.Done)
        assertTrue(LiveSync.applyFrom(phoneSql, toPhone, theirAck = ackPhone) is LiveSync.Applied.Done)
    }

    private suspend fun settle(first: suspend () -> Unit) {
        first()
        repeat(3) { phoneFirst() }
        assertEquals(LiveSync.fingerprint(phoneSql), LiveSync.fingerprint(pcSql))
    }

    // ------------------------------------------------------------- entries

    private suspend fun entryEditedOnBoth(first: suspend () -> Unit) {
        val d = phone.khataDao()
        val ali = d.insertParty(Party(name = "Ali"))
        val e = d.insertEntryWithItems(
            LedgerEntry(partyId = ali, amount = 9600.0, isGiven = true, entryNumber = ""),
            listOf(EntryItem(entryId = 0, itemName = "Urea", quantity = 2.0, unit = "bag", rate = 4800.0))
        )
        phoneFirst()
        val pcEntry = pc.khataDao().allEntriesForBackup().single()
        assertEquals(1, pc.khataDao().itemsOfEntry(pcEntry.id).size)

        // The same sale corrected on both before they meet, each with a whole new set of lines.
        d.updateEntryWithItems(
            d.getEntry(e)!!.copy(amount = 10000.0),
            listOf(EntryItem(entryId = e, itemName = "DAP", quantity = 2.0, unit = "bag", rate = 5000.0))
        )
        pc.khataDao().updateEntryWithItems(
            pcEntry.copy(amount = 4800.0),
            listOf(EntryItem(entryId = pcEntry.id, itemName = "Urea (pc)", quantity = 1.0, unit = "bag", rate = 4800.0))
        )
        settle(first)

        val onPhone = d.itemsOfEntry(e)
        val onPc = pc.khataDao().itemsOfEntry(pcEntry.id)
        assertEquals("phone's lines: $onPhone", 1, onPhone.size)
        assertEquals("pc's lines: $onPc", 1, onPc.size)
        val winner = if (phoneWins) "DAP" else "Urea (pc)"
        val loser = if (phoneWins) "Urea (pc)" else "DAP"
        assertEquals(winner, onPhone.single().itemName)
        assertEquals(winner, onPc.single().itemName)
        assertEquals(if (phoneWins) 10000.0 else 4800.0, d.getEntry(e)!!.amount, 0.0)
        assertEquals(d.getEntry(e)!!.amount, pc.khataDao().getEntry(pcEntry.id)!!.amount, 0.0)
        // The other set is not lost from sight: the device that decided keeps it for the owner.
        val kept = LiveSync.conflicts(phoneSql) + LiveSync.conflicts(pcSql)
        assertTrue("$kept", kept.any { it.table == "entry_items" && it.lost.orEmpty().contains(loser) })
    }

    @Test
    fun `an entry's lines edited on both do not double when the phone's parcel lands first`() = runBlocking {
        entryEditedOnBoth { phoneFirst() }
    }

    @Test
    fun `an entry's lines edited on both do not double when the computer's parcel lands first`() = runBlocking {
        entryEditedOnBoth { pcFirst() }
    }

    @Test
    fun `an entry's lines edited on both do not double when both parcels cross`() = runBlocking {
        entryEditedOnBoth { atOnce() }
    }

    /** An editor that keeps a line's uid (saved again in place) on both, one of them adding a line. */
    private suspend fun sameLineEditedOnBoth(first: suspend () -> Unit) {
        val d = phone.khataDao()
        val ali = d.insertParty(Party(name = "Ali"))
        val e = d.insertEntryWithItems(
            LedgerEntry(partyId = ali, amount = 9600.0, isGiven = true, entryNumber = ""),
            listOf(EntryItem(entryId = 0, itemName = "Urea", quantity = 2.0, unit = "bag", rate = 4800.0))
        )
        phoneFirst()
        val pcEntry = pc.khataDao().allEntriesForBackup().single()
        val line = d.itemsOfEntry(e).single()
        val pcLine = pc.khataDao().itemsOfEntry(pcEntry.id).single()
        assertEquals(line.uid, pcLine.uid)
        d.updateEntryWithItems(d.getEntry(e)!!.copy(amount = 14400.0), listOf(line.copy(quantity = 3.0)))
        pc.khataDao().updateEntryWithItems(
            pcEntry.copy(amount = 24900.0),
            listOf(pcLine.copy(quantity = 5.0), EntryItem(entryId = pcEntry.id, itemName = "Zinc", quantity = 1.0, unit = "kg", rate = 900.0))
        )
        settle(first)
        val want = if (phoneWins) listOf("Urea" to 3.0) else listOf("Urea" to 5.0, "Zinc" to 1.0)
        assertEquals(want, d.itemsOfEntry(e).map { it.itemName to it.quantity })
        assertEquals(want, pc.khataDao().itemsOfEntry(pcEntry.id).map { it.itemName to it.quantity })
        assertEquals(line.uid, d.itemsOfEntry(e).first().uid)
    }

    @Test
    fun `a line saved again in place on both keeps one version when the phone's parcel lands first`() = runBlocking {
        sameLineEditedOnBoth { phoneFirst() }
    }

    @Test
    fun `a line saved again in place on both keeps one version when the computer's parcel lands first`() = runBlocking {
        sameLineEditedOnBoth { pcFirst() }
    }

    @Test
    fun `lines changed on one device only still travel whoever wins the entry`() = runBlocking {
        val d = phone.khataDao()
        val ali = d.insertParty(Party(name = "Ali"))
        val e = d.insertEntryWithItems(
            LedgerEntry(partyId = ali, amount = 9600.0, isGiven = true, entryNumber = ""),
            listOf(EntryItem(entryId = 0, itemName = "Urea", quantity = 2.0, unit = "bag", rate = 4800.0))
        )
        phoneFirst()
        val pcEntry = pc.khataDao().allEntriesForBackup().single()
        // The phone changes the goods; the computer only the note.
        d.updateEntryWithItems(
            d.getEntry(e)!!.copy(amount = 10000.0),
            listOf(EntryItem(entryId = e, itemName = "DAP", quantity = 2.0, unit = "bag", rate = 5000.0))
        )
        pc.khataDao().updateEntry(pcEntry.copy(note = "paid half"))
        settle { pcFirst() }
        assertEquals(listOf("DAP"), d.itemsOfEntry(e).map { it.itemName })
        assertEquals(listOf("DAP"), pc.khataDao().itemsOfEntry(pcEntry.id).map { it.itemName })
        val kept = LiveSync.conflicts(phoneSql) + LiveSync.conflicts(pcSql)
        assertTrue("$kept", kept.none { it.table == "entry_items" })
    }

    // ------------------------------------------------------------ invoices

    private suspend fun invoiceEditedOnBoth(first: suspend () -> Unit) {
        val d = phone.khataDao()
        val inv = d.saveInvoiceWithItems(
            Invoice(customerName = "Ali"),
            listOf(InvoiceItem(invoiceId = 0, itemName = "Urea", quantity = 2.0, unit = "bag", rate = 4800.0))
        )
        phoneFirst()
        val pcInv = pc.khataDao().allInvoicesForBackup().single()
        d.updateInvoiceWithItems(
            d.getInvoice(inv)!!.copy(note = "phone"),
            listOf(InvoiceItem(invoiceId = inv, itemName = "DAP", quantity = 2.0, unit = "bag", rate = 5000.0))
        )
        pc.khataDao().updateInvoiceWithItems(
            pcInv.copy(note = "pc"),
            listOf(
                InvoiceItem(invoiceId = pcInv.id, itemName = "Urea (pc)", quantity = 1.0, unit = "bag", rate = 4800.0),
                InvoiceItem(invoiceId = pcInv.id, itemName = "Zinc (pc)", quantity = 1.0, unit = "kg", rate = 900.0)
            )
        )
        settle(first)
        val winner = if (phoneWins) listOf("DAP") else listOf("Urea (pc)", "Zinc (pc)")
        assertEquals(winner, d.invoiceItems(inv).map { it.itemName })
        assertEquals(winner, pc.khataDao().invoiceItems(pcInv.id).map { it.itemName })
        assertEquals(if (phoneWins) "phone" else "pc", pc.khataDao().getInvoice(pcInv.id)!!.note)
    }

    @Test
    fun `an invoice's lines edited on both do not double when the phone's parcel lands first`() = runBlocking {
        invoiceEditedOnBoth { phoneFirst() }
    }

    @Test
    fun `an invoice's lines edited on both do not double when the computer's parcel lands first`() = runBlocking {
        invoiceEditedOnBoth { pcFirst() }
    }

    @Test
    fun `an invoice's lines edited on both do not double when both parcels cross`() = runBlocking {
        invoiceEditedOnBoth { atOnce() }
    }

    // ------------------------------------------------------ purchase orders

    @Test
    fun `a draft order's lines edited on both do not double`() = runBlocking {
        val d = phone.khataDao()
        val engro = d.insertParty(Party(name = "Engro"))
        val order = d.saveOrder(PurchaseOrder(partyId = engro), listOf(PurchaseOrderItem(orderId = 0, itemName = "Urea", quantity = 10.0, unit = "bag")))
        phoneFirst()
        val pcOrder = pc.khataDao().allOrdersForBackup().single()
        d.saveOrder(d.getOrder(order)!!.copy(note = "phone"), listOf(PurchaseOrderItem(orderId = order, itemName = "DAP", quantity = 5.0, unit = "bag")))
        pc.khataDao().saveOrder(pcOrder.copy(note = "pc"), listOf(PurchaseOrderItem(orderId = pcOrder.id, itemName = "Urea (pc)", quantity = 20.0, unit = "bag")))
        settle { pcFirst() }
        val winner = if (phoneWins) "DAP" else "Urea (pc)"
        assertEquals(listOf(winner), d.orderItems(order).map { it.itemName })
        assertEquals(listOf(winner), pc.khataDao().orderItems(pcOrder.id).map { it.itemName })
    }
}
