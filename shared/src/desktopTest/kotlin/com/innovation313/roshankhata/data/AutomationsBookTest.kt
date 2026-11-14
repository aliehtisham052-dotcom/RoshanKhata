package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.desktopWords
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * The automations read a real book (31 Oct 2026, Automations): the low shelf with its quantity and supplier,
 * nothing already ordered, the morning's list by Follow-up's rule, today's closing figures, and (14 Nov 2026) the
 * cheques due by direction with their sums and who. Made-up names.
 */
class AutomationsBookTest {

    private lateinit var dir: File
    private lateinit var db: KhataDatabase
    private val dao get() = db.khataDao()
    private val now = System.currentTimeMillis()
    private val day = 24L * 60 * 60 * 1000

    @Before
    fun open() {
        dir = Files.createTempDirectory("rk-automations").toFile()
        db = DesktopDatabase.open(dir.resolve("khata.db"))
    }

    @After
    fun close() {
        db.close()
        dir.deleteRecursively()
    }

    private fun product(name: String, low: Double?) =
        Product(name = name, nameKey = ProductName.key(name), normalisedName = ProductName.normalised(name), defaultUnit = "bottle",
            salePrice = 1000.0, lowStockLevel = low)

    /** 25 bottles from Test Supplier 40 days ago; 18 sold to a customer (promised yesterday, with a phone) today. */
    private suspend fun book(): Triple<Long, Long, Long> {
        val supplier = dao.insertParty(Party(name = "Test Supplier", isCustomer = false))
        val customer = dao.insertParty(Party(name = "Test Customer", phone = "03000000002", harvestPromise = now - day))
        val sprayId = dao.insertProduct(product("Test Spray", low = 10.0))
        dao.insertSupplierBill(
            null, SupplierBill(partyId = supplier, billDate = now - 40 * day, totalAmount = 0.0),
            listOf(BillItem(billId = 0, productName = "Test Spray", quantity = 25.0, unit = "bottle", rate = 700.0, productId = sprayId))
        )
        dao.insertEntryWithItems(
            LedgerEntry(partyId = customer, amount = 18_000.0, isGiven = true, entryNumber = ""),
            listOf(EntryItem(entryId = 0, itemName = "Test Spray", quantity = 18.0, unit = "bottle", rate = 1000.0, productId = sprayId))
        )
        return Triple(supplier, customer, sprayId)
    }

    @Test
    fun `the low shelf comes with a month's quantity and its last supplier, and only once ordered`() = runBlocking {
        val (supplier, _, spray) = book()
        val lines = Automations.reorder(dao, now)
        assertEquals(1, lines.size)
        val line = lines.single()
        assertEquals(spray, line.productId)
        assertEquals("the supplier of the newest bill", supplier, line.supplierId)
        // Twice the level (20) is more than the 18 sold in 30 days: 20 less the 7 on the shelf = 13.
        assertEquals(13.0, line.qty, 0.001)
        dao.saveOrder(PurchaseOrder(partyId = supplier), listOf(PurchaseOrderItem(orderId = 0, productId = spray, itemName = "Test Spray", quantity = 13.0, unit = "bottle")))
        assertTrue("an open order already carries it", Automations.reorder(dao, now).isEmpty())
    }

    @Test
    fun `the morning list is Follow-up's, less those reminded today`() = runBlocking {
        val (_, customer, _) = book()
        val c = Automations.collect(dao, emptySet(), now)
        assertEquals(1, c.count)
        assertEquals(18_000.0, c.total, 0.001)
        assertEquals(0, Automations.collect(dao, setOf(customer), now).count)
    }

    @Test
    fun `today's closing counts today's sales`() = runBlocking {
        book()
        val d = Automations.dayEnd(dao, now)
        assertEquals(18_000.0, d.sales, 0.001)
        assertTrue(d.moved)
        assertTrue("the drawer was never counted", !d.counted)
    }

    // ---------------------------------------------------------------- the cheque reminders (14 Nov 2026)

    private val karachi = TimeZone.getTimeZone("Asia/Karachi")

    /** A fixed morning, so the quiet hours of the real clock can never make a run flaky. */
    private val morning = Calendar.getInstance(karachi).apply { clear(); set(2026, 10, 14, 10, 0) }.timeInMillis
    private val midnight = DayMath.startOfDay(morning, karachi)

    /** Two to bank (one today, one three days late), one to pay today; tomorrow's and a cleared one are not due. */
    private suspend fun chequeBook(): Pair<Long, Long> {
        val customer = dao.insertParty(Party(name = "Test Customer"))
        val supplier = dao.insertParty(Party(name = "Test Supplier", isCustomer = false))
        dao.insertCheque(Cheque(partyId = customer, amount = 5000.0, isReceived = true, dueDate = midnight))
        dao.insertCheque(Cheque(partyId = customer, amount = 2500.0, isReceived = true, dueDate = midnight - 3 * day))
        dao.insertCheque(Cheque(partyId = supplier, amount = 700.0, isReceived = false, dueDate = midnight))
        dao.insertCheque(Cheque(partyId = customer, amount = 9999.0, isReceived = true, dueDate = midnight + day))
        dao.insertCheque(Cheque(partyId = customer, amount = 123.0, isReceived = true, dueDate = midnight - 10 * day, status = ChequeStatus.CLEARED))
        return customer to supplier
    }

    private class OnlyCheques : Automations.Settings {
        val said = mutableMapOf<Automations.Kind, Long>()
        override fun isOn(kind: Automations.Kind) = kind == Automations.Kind.CHEQUES
        override fun hour(kind: Automations.Kind) = 0
        override fun lastSaid(kind: Automations.Kind) = said[kind]
        override fun setSaid(kind: Automations.Kind, stamp: Long) { said[kind] = stamp }
    }

    private suspend fun notices(settings: Automations.Settings) = Automations.noticesDue(
        dao, settings, desktopWords(Locale.ENGLISH), money = { Format.money(it) },
        date = { it.toString() }, monthName = { it.toString() }, remindedToday = emptySet(),
        now = morning, tz = karachi
    )

    @Test
    fun `cheques due are counted by direction, with their sums, how many are late and who`() = runBlocking {
        chequeBook()
        val c = Automations.cheques(dao, morning, karachi)
        assertEquals(2, c.received)
        assertEquals(7500.0, c.receivedTotal, 0.001)
        assertEquals(1, c.issued)
        assertEquals(700.0, c.issuedTotal, 0.001)
        assertEquals("only the one three days old is past its day", 1, c.late)
        assertEquals("the oldest cheque's party first, each once", listOf("Test Customer", "Test Supplier"), c.names)
    }

    @Test
    fun `the notice says it in the owner's words and once a day`() = runBlocking {
        chequeBook()
        val settings = OnlyCheques()
        val said = notices(settings).single()
        assertEquals(Automations.Kind.CHEQUES, said.kind)
        assertEquals("Cheques due", said.title)
        assertTrue(said.body, said.body.contains("2 cheques to bank · " + Format.money(7500.0)))
        assertTrue(said.body, said.body.contains("1 cheque to pay · " + Format.money(700.0)))
        assertTrue(said.body, said.body.contains("Of these, 1 is past its day"))
        assertTrue(said.body, said.body.contains("Test Customer") && said.body.contains("Test Supplier"))
        assertTrue("a second run the same morning says nothing", notices(settings).isEmpty())
    }

    @Test
    fun `no cheque due is no notice, and the morning is still marked looked at`() = runBlocking {
        val customer = dao.insertParty(Party(name = "Test Customer"))
        dao.insertCheque(Cheque(partyId = customer, amount = 9999.0, isReceived = true, dueDate = midnight + day))
        dao.insertCheque(Cheque(partyId = customer, amount = 123.0, isReceived = false, dueDate = midnight - 10 * day, status = ChequeStatus.BOUNCED))
        val settings = OnlyCheques()
        assertTrue(notices(settings).isEmpty())
        assertFalse("looked at, so not looked at again all day", settings.lastSaid(Automations.Kind.CHEQUES) == null)
    }

    @Test
    fun `the notice names the very cheques the bell and the daily count name`() = runBlocking {
        // One rule (ChequesDue): the daily sweep's count, the bell's rows and the automation can never disagree.
        // On the real clock here: the bell and the count read it themselves.
        val customer = dao.insertParty(Party(name = "Test Customer"))
        val today = DayMath.startOfDay(now)
        dao.insertCheque(Cheque(partyId = customer, amount = 100.0, isReceived = true, dueDate = today))
        dao.insertCheque(Cheque(partyId = customer, amount = 200.0, isReceived = false, dueDate = today - 2 * day))
        dao.insertCheque(Cheque(partyId = customer, amount = 300.0, isReceived = true, dueDate = today + day))
        dao.insertCheque(Cheque(partyId = customer, amount = 400.0, isReceived = true, dueDate = today - day, status = ChequeStatus.CLEARED))
        val counted = LedgerAlerts.dueCounts(dao).cheques
        val onTheBell = LedgerAlerts.compute(desktopWords(Locale.ENGLISH), dao, { Format.money(it) }).count { it.kind == LedgerAlerts.Kind.CHEQUE }
        val byAutomation = Automations.cheques(dao, now).count
        assertEquals(2, counted)
        assertEquals(counted, onTheBell)
        assertEquals(counted, byAutomation)
    }

    @Test
    fun `an uncounted drawer with nothing moved today is not a closing summary`() = runBlocking {
        dao.insertCashEntry(CashEntry(amount = 500.0, isIncome = true, category = "Old", timestamp = now - 3 * day))
        val d = Automations.dayEnd(dao, now)
        assertTrue("cash from three days ago is not today's", !d.moved)
        dao.insertCashEntry(CashEntry(amount = 200.0, isIncome = true, category = "Today", timestamp = now))
        assertTrue(Automations.dayEnd(dao, now).moved)
    }
}
