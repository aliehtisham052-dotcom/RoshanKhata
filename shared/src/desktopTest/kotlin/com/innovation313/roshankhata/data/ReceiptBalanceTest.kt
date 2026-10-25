package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * A receipt's "Balance after" (25 Oct 2026, the audit): the phone printed the
 * entry's own amount there and the computer today's balance. Aslam owed
 * 50,000 and paid 5,000: his receipt must say 45,000. Made-up names.
 */
class ReceiptBalanceTest {

    private lateinit var dir: File
    private lateinit var db: KhataDatabase
    private val dao get() = db.khataDao()

    @Before
    fun open() {
        dir = Files.createTempDirectory("rk-receipt-balance").toFile()
        db = DesktopDatabase.open(dir.resolve("khata.db"))
    }

    @After
    fun close() {
        db.close()
        dir.deleteRecursively()
    }

    private suspend fun entry(party: Long, amount: Double, gave: Boolean, at: Long): LedgerEntry {
        val id = dao.insertEntryNumbered(LedgerEntry(partyId = party, amount = amount, isGiven = gave, entryNumber = "", timestamp = at))
        return dao.getEntry(id)!!
    }

    @Test
    fun `each receipt says the balance that entry left, not today's`() = runBlocking {
        val aslam = dao.insertParty(Party(name = "Aslam"))
        val sale = entry(aslam, 50_000.0, gave = true, at = 1_000)
        val paid = entry(aslam, 5_000.0, gave = false, at = 2_000)
        val later = entry(aslam, 1_000.0, gave = true, at = 3_000)
        assertEquals(50_000.0, dao.balanceAfterEntry(sale), 0.001)
        assertEquals("not the payment's own 5,000", 45_000.0, dao.balanceAfterEntry(paid), 0.001)
        assertEquals(46_000.0, dao.balanceAfterEntry(later), 0.001)
        assertEquals(dao.liveBalance(aslam), dao.balanceAfterEntry(later), 0.001)
    }

    @Test
    fun `entries at the same moment count in the order they were written, and the bin is left out`() = runBlocking {
        val bilal = dao.insertParty(Party(name = "Bilal"))
        val first = entry(bilal, 700.0, gave = true, at = 5_000)
        val second = entry(bilal, 200.0, gave = false, at = 5_000)
        val binned = entry(bilal, 9_999.0, gave = true, at = 4_000)
        dao.softDeleteEntry(binned.id)
        assertEquals(700.0, dao.balanceAfterEntry(first), 0.001)
        assertEquals(500.0, dao.balanceAfterEntry(second), 0.001)
    }

    @Test
    fun `a sale paid there and then says what is left after the payment`() = runBlocking {
        val kashif = dao.insertParty(Party(name = "Kashif"))
        entry(kashif, 3_000.0, gave = true, at = 1_000)
        val sale = entry(kashif, 2_000.0, gave = true, at = 2_000)
        val gotId = dao.recordCashForSale(sale.id)!!
        val saleNow = dao.getEntry(sale.id)!!
        assertEquals("the cash sale does not add to what he owes", 3_000.0, dao.balanceAfterEntry(saleNow), 0.001)
        assertEquals(3_000.0, dao.balanceAfterEntry(dao.getEntry(gotId)!!), 0.001)
    }

    @Test
    fun `the customer's words: he pays, he receives, or nothing is left`() {
        val names = object : Words {
            override fun text(name: String, vararg args: Any) = name
            override fun plural(name: String, count: Int, vararg args: Any) = name
            override fun list(name: String) = emptyList<String>()
        }
        assertEquals(W.pdf_stmt_you_will_pay, ReceiptBalance.sideWord(45_000.0))
        assertEquals(W.pdf_stmt_you_will_receive, ReceiptBalance.sideWord(-300.0))
        assertEquals(W.pdf_stmt_settled, ReceiptBalance.text(names, 0.0000001))
        val owed = ReceiptBalance.text(names, 45_000.0, "Rs")
        assertTrue(owed, owed.contains("45,000") && owed.endsWith(W.pdf_stmt_you_will_pay))
    }
}
