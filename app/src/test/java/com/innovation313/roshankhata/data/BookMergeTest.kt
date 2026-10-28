package com.innovation313.roshankhata.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Two copies of one book brought together (21 Oct 2026): the computer and
 * the phone each worked after a whole copy; the merge adds only what the
 * computer lacks — the phone's customer, entries and ticket, renumbered
 * where a number was taken, its lines and payments with it — and changes
 * nothing the computer already has. A second merge adds nothing; another
 * book's copy is refused.
 */
@RunWith(AndroidJUnit4::class)
class BookMergeTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var pcDb: KhataDatabase
    private lateinit var phoneDb: KhataDatabase
    private lateinit var pc: KhataDao
    private lateinit var phone: KhataDao

    @Before
    fun open(): Unit = runBlocking {
        pcDb = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java).build()
        phoneDb = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java).build()
        pc = pcDb.khataDao()
        phone = phoneDb.khataDao()
    }

    @After
    fun close() {
        pcDb.close(); phoneDb.close()
        Access.resetForTest()
    }

    private suspend fun copyOf(dao: KhataDao, shop: String = "Al-Noor"): BackupCodec.ParsedBackup {
        val out = java.io.StringWriter()
        BackupCodec.exportTo(dao, shop, org.json.JSONObject(), out)
        return BackupCodec.parseText(out.toString()).second!!
    }

    private suspend fun balanceOf(dao: KhataDao, name: String) = dao.partiesWithBalanceOnce().single { it.name == name }.balance

    @Test
    fun whatTheOtherCopyAddedComesInAndNothingHereChanges() = runBlocking {
        // The morning: one book, copied whole to the phone.
        val ali = pc.insertParty(Party(name = "Ali", phone = "03000000001"))
        pc.insertEntryNumbered(LedgerEntry(partyId = ali, amount = 500.0, isGiven = true, entryNumber = "", timestamp = 1_000L))
        pc.insertCashEntry(CashEntry(amount = 50.0, isIncome = false, category = "Tea", timestamp = 2_000L))
        BackupCodec.restoreLedger(phone, copyOf(pc))

        // The day on the computer: an entry for Ali and a walk-in ticket.
        pc.insertEntryNumbered(LedgerEntry(partyId = ali, amount = 300.0, isGiven = true, entryNumber = "", timestamp = 3_000L))
        pc.postSale(Sale(subtotal = 0.0, grandTotal = 0.0, paidTotal = 0.0, timestamp = 3_500L),
            listOf(SaleItem(saleId = 0, itemName = "Bread", quantity = 2.0, rate = 60.0)),
            listOf(SalePayment(saleId = 0, method = PaymentMethod.CASH, amount = 120.0)))

        // The day on the phone: Ali paid 200, a new customer, and Ali's ticket on account with goods.
        val aliThere = phone.partiesWithBalanceOnce().single { it.name == "Ali" }.id
        phone.insertEntryNumbered(LedgerEntry(partyId = aliThere, amount = 200.0, isGiven = false, entryNumber = "", timestamp = 4_000L))
        val bilal = phone.insertParty(Party(name = "Bilal", phone = "03000000002"))
        phone.insertEntryNumbered(LedgerEntry(partyId = bilal, amount = 900.0, isGiven = true, entryNumber = "", timestamp = 5_000L))
        val dap = phone.insertProduct(Product(name = "DAP", nameKey = "dap", normalisedName = "dap", defaultUnit = "bag", salePrice = 1_000.0))
        phone.postSale(Sale(partyId = aliThere, customerName = "Ali", subtotal = 0.0, grandTotal = 0.0, paidTotal = 0.0, timestamp = 6_000L),
            listOf(SaleItem(saleId = 0, productId = dap, itemName = "DAP", quantity = 1.0, unit = "bag", rate = 1_000.0)),
            emptyList())

        val there = copyOf(phone)
        val plan = BookMerge.plan(pc, there, "Al-Noor")
        assertEquals(1, plan.sales)
        assertEquals(1, plan.parties)
        assertEquals(1, plan.products)
        assertEquals(0, plan.changed)

        val done = pc.mergeBook(there, "Al-Noor")
        assertTrue(done.renumbered >= 2)  // RK-000003.. and S-000001 were the computer's already

        // Ali: 500 + 300 (computer) − 200 (phone) + 1,000 (phone's ticket on account).
        assertEquals(1_600.0, balanceOf(pc, "Ali"), 0.0)
        assertEquals(900.0, balanceOf(pc, "Bilal"), 0.0)
        // Both tickets, the phone's with the next number and its own in the note; its goods line and its khata row with it.
        val sales = pc.allSalesForBackup().sortedBy { it.id }
        assertEquals(listOf("S-000001", "S-000002"), sales.map { it.saleNumber })
        assertTrue(sales[1].note.orEmpty().contains("S-000001"))
        val line = pc.saleItems(sales[1].id).single()
        assertEquals(pc.productByKey("dap")!!.id, line.productId)
        assertEquals(sales[1].id, pc.getEntry(sales[1].ledgerEntryId!!)!!.saleId)
        // Every entry number is used once.
        val numbers = pc.entriesPageForBackup(0, 100).map { it.entryNumber }
        assertEquals(numbers.size, numbers.toSet().size)
        // The cashbook row both had is there once.
        assertEquals(1, pc.allCashForBackup().count { it.category == "Tea" })
        assertTrue(pc.auditInOrder().any { it.action == AuditAction.BOOK_MERGED })
        assertTrue("the log still holds", AuditChain.verify(pc.auditInOrder()).intact)

        // A second merge of the same copy adds nothing.
        assertTrue(BookMerge.plan(pc, copyOf(phone), "Al-Noor").none)
    }

    @Test
    fun anEditMadeOnlyThereIsCountedNotApplied() = runBlocking {
        val ali = pc.insertParty(Party(name = "Ali"))
        pc.insertEntryNumbered(LedgerEntry(partyId = ali, amount = 500.0, isGiven = true, entryNumber = "", timestamp = 1_000L))
        BackupCodec.restoreLedger(phone, copyOf(pc))
        val e = phone.entriesPageForBackup(0, 10).single()
        phone.updateEntry(e.copy(amount = 550.0))
        val plan = BookMerge.plan(pc, copyOf(phone), "Al-Noor")
        assertEquals(1, plan.changed)
        pc.mergeBook(copyOf(phone), "Al-Noor")
        assertEquals(500.0, pc.entriesPageForBackup(0, 10).single().amount, 0.0)
    }

    @Test
    fun anotherBooksCopyAndAnOldCopyAreRefused() = runBlocking {
        val ali = pc.insertParty(Party(name = "Ali"))
        pc.insertEntryNumbered(LedgerEntry(partyId = ali, amount = 500.0, isGiven = true, entryNumber = "", timestamp = 1_000L))
        val other = phone.insertParty(Party(name = "Zahid"))
        phone.insertEntryNumbered(LedgerEntry(partyId = other, amount = 70.0, isGiven = true, entryNumber = "", timestamp = 1_000L))
        try { BookMerge.plan(pc, copyOf(phone), "Al-Noor"); fail("merged another book") } catch (e: BookMerge.Refused) { assertEquals(BookMerge.Why.UNRELATED, e.why) }
        val out = java.io.StringWriter()
        BackupCodec.exportTo(pc, "Al-Noor", org.json.JSONObject(), out)
        val old = BackupCodec.parseText(out.toString().replace("\"version\": ${BackupCodec.FORMAT_VERSION}", "\"version\": 11")).second!!
        try { BookMerge.plan(pc, old, "Al-Noor"); fail("merged a copy without uids") } catch (e: BookMerge.Refused) { assertEquals(BookMerge.Why.TOO_OLD, e.why) }
        // A renamed shop is said, not refused.
        BackupCodec.restoreLedger(phone, copyOf(pc))
        assertEquals("Al-Noor Agri", BookMerge.plan(pc, copyOf(phone, "Al-Noor Agri"), "Al-Noor").otherShop)
    }
}
