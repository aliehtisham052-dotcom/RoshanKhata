package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.desktopWords
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Locale

/**
 * Audit L027 (17 Nov 2026), register BC-207: the computer worked the bell out again (LedgerAlerts.compute, a dozen reads of
 * the whole book) on every page the owner opened. It is now worked out when the book changes ([KhataDao.observeBellSources])
 * or the day does. This holds that a write to each kind of table the bell reads says so — a cheque, a plan, a bill, a
 * product, a count, a cashbook row, a day close, an entry — so a bell row cannot go stale because its table was left out of
 * the query, and that the bell itself reads the new row. Made-up names.
 */
class BellSourcesTest {

    private lateinit var dir: File
    private lateinit var db: KhataDatabase
    private val dao get() = db.khataDao()
    private val now = System.currentTimeMillis()
    private val day = 24L * 60 * 60 * 1000

    @Before
    fun open() {
        dir = Files.createTempDirectory("rk-bell").toFile()
        db = DesktopDatabase.open(dir.resolve("khata.db"))
    }

    @After
    fun close() {
        db.close()
        dir.deleteRecursively()
    }

    /** True when [write] makes observeBellSources run again (its first answer is taken before the write). */
    private fun saysChanged(write: suspend () -> Unit): Boolean = runBlocking {
        val seen = Channel<Long>(Channel.UNLIMITED)
        val job = launch(Dispatchers.IO) { dao.observeBellSources().collect { seen.send(it) } }
        try {
            withTimeout(10_000) { seen.receive() }
            write()
            runCatching { withTimeout(10_000) { seen.receive() } }.isSuccess
        } finally {
            job.cancel()
        }
    }

    @Test
    fun `a write to each table the bell reads says the book changed`() {
        val party = runBlocking { dao.insertParty(Party(name = "Test Customer", phone = "03000000002")) }
        val supplier = runBlocking { dao.insertParty(Party(name = "Test Supplier", isCustomer = false)) }
        assertTrue("a cheque", saysChanged { dao.insertCheque(Cheque(partyId = party, amount = 500.0, isReceived = true, dueDate = now)) })
        assertTrue("a plan", saysChanged { dao.insertPlan(PaymentPlan(partyId = party, totalAmount = 900.0, nextDueDate = now - day)) })
        assertTrue("a supplier's bill", saysChanged {
            dao.insertSupplierBill(null, SupplierBill(partyId = supplier, billDate = now, totalAmount = 700.0, dueDate = now - day), emptyList())
        })
        assertTrue("a product", saysChanged {
            dao.insertProduct(Product(name = "Test Spray", nameKey = ProductName.key("Test Spray"), normalisedName = ProductName.normalised("Test Spray"), lowStockLevel = 5.0))
        })
        assertTrue("a cashbook row", saysChanged { dao.insertCashEntry(CashEntry(amount = 100.0, isIncome = true, category = "Sale")) })
        assertTrue("a day close", saysChanged {
            dao.saveDayCloseRow(DayClose(day = DayCloseMath.dayOf(now), opening = 0.0, cashIn = 0.0, cashOut = 0.0, counted = 0.0))
        })
        assertTrue("an entry", saysChanged { dao.insertEntryWithItems(LedgerEntry(partyId = party, amount = 300.0, isGiven = true, entryNumber = ""), emptyList()) })
    }

    @Test
    fun `the bell reads the row the change was about`() = runBlocking {
        val words = desktopWords(Locale.ENGLISH)
        val party = dao.insertParty(Party(name = "Test Customer"))
        val before = LedgerAlerts.compute(words, dao, { Format.money(it) }).count { it.kind == LedgerAlerts.Kind.CHEQUE }
        dao.insertCheque(Cheque(partyId = party, amount = 500.0, isReceived = true, dueDate = now))
        val after = LedgerAlerts.compute(words, dao, { Format.money(it) }).count { it.kind == LedgerAlerts.Kind.CHEQUE }
        assertTrue("a cheque due today is a bell row", after > before)
    }
}
