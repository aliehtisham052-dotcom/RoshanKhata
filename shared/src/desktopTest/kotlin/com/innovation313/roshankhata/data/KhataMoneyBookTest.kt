package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.StringWriter
import java.nio.file.Files

/**
 * Money a supplier handed back is in the drawer (17 Nov 2026, the owner's decision T43, [KhataMoney]).
 *
 * A supplier's "I got" asks "What did you get?": Goods on credit (no way of paying, nothing joins the drawer — the
 * 8 Nov C2 fix stays) or Money (a way of paying, [EntryKind.MONEY]). The drawer counts a supplier's "I got" only when it
 * is MONEY and marked Cash, so the rows the old forms wrote with a pre-ticked Cash never start counting. This holds the
 * DAO's SQL to the Kotlin rule over every combination, the kind's edits, the kind chooser's refusals and a backup's
 * round trip. Made-up names and figures.
 */
class KhataMoneyBookTest {

    private lateinit var dir: File
    private lateinit var db: KhataDatabase
    private val dao get() = db.khataDao()
    private val now = System.currentTimeMillis()
    private val from = now - 60 * 60_000L

    @Before
    fun open() {
        dir = Files.createTempDirectory("rk-khata-money").toFile()
        db = DesktopDatabase.open(dir.resolve("khata.db"))
    }

    @After
    fun close() {
        db.close()
        dir.deleteRecursively()
    }

    @Test
    fun `the stored words are the ones the SQL spells`() {
        assertEquals(PaymentMethod.CASH, KhataMoney.CASH)
        assertEquals("cash", KhataMoney.CASH)
        assertEquals("MONEY", EntryKind.MONEY)
        assertEquals(EntryKind.MONEY, EntryKind.of(" money "))
        assertTrue(EntryKind.MONEY in EntryKind.ALL)
        assertTrue("money a supplier handed back is no sale", !EntryKind.countsAsSale(EntryKind.MONEY))
    }

    @Test
    fun `the drawer's SQL is the Kotlin rule, every combination`() = runBlocking {
        val customer = dao.insertParty(Party(name = "Ali Raza", isCustomer = true))
        val supplier = dao.insertParty(Party(name = "Tariq Butt", isCustomer = false))
        var amount = 1.0
        var expectIn = 0.0
        var expectOut = 0.0
        var n = 0
        for (given in listOf(true, false)) for (party in listOf(customer, supplier))
            for (method in listOf(null, PaymentMethod.CASH, PaymentMethod.BANK))
                for (kind in listOf(EntryKind.NORMAL, EntryKind.OPENING, EntryKind.MONEY)) {
                    // Amounts that are powers of two: every sum says exactly which rows were counted.
                    dao.insertEntry(LedgerEntry(partyId = party, amount = amount, isGiven = given, entryNumber = "RK-${++n}",
                        timestamp = now - 1000, paymentMethod = method, entryKind = kind))
                    if (KhataMoney.countsInDrawer(given, party == customer, method, kind)) {
                        if (given) expectOut += amount else expectIn += amount
                    }
                    amount *= 2
                }
        assertEquals(expectIn, dao.khataCashBetween(false, from, now), 0.0)
        assertEquals(expectOut, dao.khataCashBetween(true, from, now), 0.0)
        assertTrue("some money in is counted", expectIn > 0.0)
    }

    @Test
    fun `cash a supplier handed back is in the drawer, goods and the old default are not`() = runBlocking {
        val supplier = dao.insertParty(Party(name = "Tariq Butt", isCustomer = false))
        val customer = dao.insertParty(Party(name = "Ali Raza", isCustomer = true))
        // As the forms write them now: Goods (no way of paying), Money in cash, Money by bank.
        dao.insertEntry(LedgerEntry(partyId = supplier, amount = 40_000.0, isGiven = false, entryNumber = "RK-1", timestamp = now - 5000))
        dao.insertEntry(LedgerEntry(partyId = supplier, amount = 700.0, isGiven = false, entryNumber = "RK-2", timestamp = now - 4000,
            paymentMethod = PaymentMethod.CASH, entryKind = EntryKind.MONEY))
        dao.insertEntry(LedgerEntry(partyId = supplier, amount = 9_000.0, isGiven = false, entryNumber = "RK-3", timestamp = now - 3000,
            paymentMethod = PaymentMethod.BANK, entryKind = EntryKind.MONEY))
        // As the old forms wrote them: goods taken on credit with Cash pre-ticked.
        dao.insertEntry(LedgerEntry(partyId = supplier, amount = 25_000.0, isGiven = false, entryNumber = "RK-4", timestamp = now - 2000,
            paymentMethod = PaymentMethod.CASH))
        dao.insertEntry(LedgerEntry(partyId = customer, amount = 300.0, isGiven = false, entryNumber = "RK-5", timestamp = now - 1000,
            paymentMethod = PaymentMethod.CASH))
        val d = DayCloseMath.drawerByTheBooks(dao, now)
        assertEquals("the customer's 300 and the supplier's 700 cash", 1_000.0, d.kIn, 0.0)
        assertEquals(1_000.0, d.expectedFrom(0.0), 0.0)
        // The day report, the evening notice and the bell read the same sum.
        assertEquals(1_000.0, dao.khataCashOver(false, from, now + 1), 0.0)
    }

    @Test
    fun `the entry form's Goods or Money decides the kind on an edit, an opening balance stays`() = runBlocking {
        val supplier = dao.insertParty(Party(name = "Tariq Butt", isCustomer = false))
        val id = dao.insertEntryNumbered(LedgerEntry(partyId = supplier, amount = 500.0, isGiven = false, entryNumber = "",
            paymentMethod = PaymentMethod.CASH, entryKind = EntryKind.MONEY))
        val e = dao.getEntry(id)!!
        assertEquals(EntryKind.MONEY, e.entryKind)
        // An editor that copies the row (the phone's Edit entry) keeps it Money.
        dao.updateEntryWithItems(e.copy(amount = 600.0), emptyList())
        assertEquals(EntryKind.MONEY, dao.getEntry(id)!!.entryKind)
        // Goods chosen on the form: an ordinary "I got", no way of paying.
        dao.updateEntryWithItems(e.copy(amount = 600.0, paymentMethod = null, entryKind = EntryKind.NORMAL), emptyList())
        assertEquals(EntryKind.NORMAL, dao.getEntry(id)!!.entryKind)
        assertEquals(0.0, dao.khataCashBetween(false, 0L, Long.MAX_VALUE), 0.0)
        // Money again.
        dao.updateEntryWithItems(e.copy(amount = 600.0, entryKind = EntryKind.MONEY), emptyList())
        assertEquals(EntryKind.MONEY, dao.getEntry(id)!!.entryKind)
        assertEquals(600.0, dao.khataCashBetween(false, 0L, Long.MAX_VALUE), 0.0)
        // Goods lines are goods, whatever the form said.
        dao.updateEntryWithItems(dao.getEntry(id)!!, listOf(EntryItem(entryId = id, itemName = "Urea", quantity = 1.0, unit = "bag")))
        assertEquals(EntryKind.NORMAL, dao.getEntry(id)!!.entryKind)
        // An "I gave" is never Money.
        val gave = dao.insertEntryNumbered(LedgerEntry(partyId = supplier, amount = 100.0, isGiven = true, entryNumber = ""))
        dao.updateEntryWithItems(dao.getEntry(gave)!!.copy(entryKind = EntryKind.MONEY), emptyList())
        assertEquals(EntryKind.NORMAL, dao.getEntry(gave)!!.entryKind)
        // A stored opening balance stays one, whatever a stale form passes.
        val opening = dao.insertEntryNumbered(LedgerEntry(partyId = supplier, amount = 2_000.0, isGiven = false, entryNumber = "",
            entryKind = EntryKind.OPENING))
        dao.updateEntryWithItems(dao.getEntry(opening)!!.copy(entryKind = EntryKind.MONEY), emptyList())
        assertEquals(EntryKind.OPENING, dao.getEntry(opening)!!.entryKind)
    }

    @Test
    fun `the kind chooser neither makes nor unmakes money`() = runBlocking {
        val supplier = dao.insertParty(Party(name = "Tariq Butt", isCustomer = false))
        val money = dao.insertEntryNumbered(LedgerEntry(partyId = supplier, amount = 500.0, isGiven = false, entryNumber = "",
            paymentMethod = PaymentMethod.CASH, entryKind = EntryKind.MONEY))
        val plain = dao.insertEntryNumbered(LedgerEntry(partyId = supplier, amount = 800.0, isGiven = false, entryNumber = ""))
        assertEquals(EntryKind.Refusal.MONEY, EntryKind.refusal(dao.getEntry(money)!!, false, null))
        assertNull(EntryKind.refusal(dao.getEntry(plain)!!, false, null))
        assertEquals(EntryKind.Refusal.MONEY, dao.setEntryKind(money, EntryKind.OPENING))
        assertEquals(EntryKind.Refusal.MONEY, dao.setEntryKind(money, EntryKind.NORMAL))
        assertEquals(EntryKind.Refusal.MONEY, dao.setEntryKind(plain, EntryKind.MONEY))
        assertEquals(EntryKind.MONEY, dao.getEntry(money)!!.entryKind)
        assertEquals(EntryKind.NORMAL, dao.getEntry(plain)!!.entryKind)
        assertTrue("nothing refused is written down", dao.allAuditForBackup().none { it.action == AuditAction.ENTRY_KIND_CHANGED })
    }

    @Test
    fun `a backup carries the mark, the helper's copy too`() = runBlocking {
        val supplier = dao.insertParty(Party(name = "Tariq Butt", isCustomer = false))
        dao.insertEntryNumbered(LedgerEntry(partyId = supplier, amount = 700.0, isGiven = false, entryNumber = "", timestamp = now - 1000,
            paymentMethod = PaymentMethod.CASH, entryKind = EntryKind.MONEY))
        for (secrets in listOf(true, false)) {
            val text = StringWriter().also { BackupCodec.exportTo(dao, "Shop", JSONObject(), it, secrets = secrets) }.toString()
            val (result, data) = BackupCodec.parseText(text)
            assertTrue("$result", result is BackupCodec.ImportResult.Ok)
            val other = DesktopDatabase.open(dir.resolve("copy-$secrets.db"))
            try {
                BackupCodec.restoreLedger(other.khataDao(), data!!)
                val e = other.khataDao().allEntriesForBackup().single()
                assertEquals(EntryKind.MONEY, e.entryKind)
                assertEquals(700.0, other.khataDao().khataCashBetween(false, from, now), 0.0)
            } finally { other.close() }
        }
    }
}
