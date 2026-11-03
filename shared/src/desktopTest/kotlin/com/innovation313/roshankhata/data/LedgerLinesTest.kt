package com.innovation313.roshankhata.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import kotlin.random.Random

/**
 * The customers' lines read a slice at a time ([LedgerLines], 3 Nov 2026, the speed work) are exactly what the
 * single whole-book queries they replaced gave: the owing customers' lines (Home's late count, Follow-up, the
 * morning list), every customer's lines (the Payment Radar's pace) and the moves Dues by age lays a balance over.
 * The old queries are run here as they were written, on a book of more customers than three slices, with
 * suppliers, binned customers, binned lines, settled accounts and lines that share a moment. Made-up names.
 */
class LedgerLinesTest {

    private lateinit var dir: java.io.File
    private lateinit var db: KhataDatabase
    private val dao get() = db.khataDao()

    @Before
    fun open() {
        dir = Files.createTempDirectory("rk-lines").toFile()
        db = DesktopDatabase.open(dir.resolve("khata.db"))
    }

    @After
    fun close() {
        db.close()
        dir.deleteRecursively()
    }

    private val day = 24L * 60 * 60 * 1000

    /** 1,400 parties: one in ten a supplier, two binned, every tenth customer settled to zero; some lines binned. */
    private suspend fun book(): Int {
        val rnd = Random(42)
        val now = System.currentTimeMillis()
        val parties = (0 until 1_400).map { i ->
            Party(name = "Test Party $i", isCustomer = i % 10 != 3, isDeleted = i == 17 || i == 600)
        }
        val ids = dao.insertParties(parties)
        val lines = ArrayList<LedgerEntry>()
        var n = 0
        for ((k, id) in ids.withIndex()) {
            val at = now - rnd.nextLong(0, 400 * day)
            if (k % 10 == 5) {
                // Settled: gave and got the same.
                lines += LedgerEntry(partyId = id, amount = 500.0, isGiven = true, entryNumber = "T-${n++}", timestamp = at)
                lines += LedgerEntry(partyId = id, amount = 500.0, isGiven = false, entryNumber = "T-${n++}", timestamp = at + day)
                continue
            }
            repeat(3 + rnd.nextInt(4)) { j ->
                // Every third customer's first two lines share a moment.
                val t = if (k % 3 == 0 && j < 2) at else at + j * rnd.nextLong(1, 20 * day)
                lines += LedgerEntry(
                    // Suppliers mostly give (what the shop owes them), customers mostly take.
                    partyId = id, amount = (rnd.nextInt(1, 200) * 50).toDouble(),
                    isGiven = if (k % 10 == 3) rnd.nextInt(4) == 0 else rnd.nextInt(4) != 0,
                    entryNumber = "T-${n++}", timestamp = t, isDeleted = rnd.nextInt(15) == 0,
                    isQarzeHasna = rnd.nextInt(40) == 0
                )
            }
        }
        dao.restoreEntries(lines)
        return ids.size
    }

    private suspend fun oldRows(sql: String): List<LedgerPoint> = db.syncSql().read {
        rows(sql, types = "IIRIIII").map {
            LedgerPoint(it[0] as Long, it[1] as Long, it[2] as Double, (it[3] as Long) == 1L, it[4] as Long, it[5] as Long?, (it[6] as Long) == 1L)
        }
    }

    private val byPartyTimeId = compareBy<LedgerPoint>({ it.partyId }, { it.timestamp }, { it.id })

    @Test
    fun `the owing customers' lines a slice at a time are the single query's rows`() = runBlocking {
        book()
        val old = oldRows(
            """
            SELECT t.partyId, t.timestamp, t.amount, t.isGiven, t.id, t.pairedEntryId, t.isQarzeHasna FROM transactions t
            WHERE t.isDeleted = 0 AND t.partyId IN (
                SELECT p.id FROM parties p
                JOIN transactions x ON x.partyId = p.id AND x.isDeleted = 0
                WHERE p.isDeleted = 0 AND p.isCustomer = 1
                GROUP BY p.id
                HAVING SUM(CASE WHEN x.isGiven = 1 THEN x.amount ELSE -x.amount END) >= 0.005
            )
            ORDER BY t.partyId, t.timestamp
            """
        )
        val sliced = LedgerLines.owingOf(dao, dao.partiesWithBalanceOnce())
        assertTrue("more owing customers than three slices", old.map { it.partyId }.distinct().size > 2 * LedgerLines.SLICE)
        assertEquals(old.sortedWith(byPartyTimeId), sliced.sortedWith(byPartyTimeId))
        assertEquals("by customer and time, as before", sliced.sortedWith(compareBy({ it.partyId }, { it.timestamp })).map { it.partyId to it.timestamp },
            sliced.map { it.partyId to it.timestamp })
        // The flow the screens collect gives the same.
        assertEquals(sliced, LedgerLines.observeOwing(dao).first())
    }

    @Test
    fun `every customer's lines a slice at a time are the Radar's single query's rows`() = runBlocking {
        book()
        val old = oldRows(
            "SELECT t.partyId AS partyId, t.timestamp AS timestamp, t.amount AS amount, t.isGiven AS isGiven, " +
                "t.id AS id, t.pairedEntryId AS pairedEntryId, t.isQarzeHasna AS isQarzeHasna " +
                "FROM transactions t JOIN parties p ON p.id = t.partyId " +
                "WHERE t.isDeleted = 0 AND p.isDeleted = 0 AND p.isCustomer = 1"
        )
        val sliced = LedgerLines.customers(dao)
        assertEquals(old.sortedWith(byPartyTimeId), sliced.sortedWith(byPartyTimeId))
        assertEquals(PaymentRadar.shopTypicalDays(old), PaymentRadar.shopTypicalDays(sliced))
    }

    @Test
    fun `dues by age from slices is the report the whole book's moves made`() = runBlocking {
        book()
        val parties = dao.partiesWithBalanceOnce()
        val old = db.syncSql().read {
            rows("SELECT partyId, amount, isGiven, timestamp FROM transactions WHERE isDeleted = 0 ORDER BY timestamp DESC, id DESC", types = "IRII")
                .map { AgingMove(it[0] as Long, it[1] as Double, (it[2] as Long) == 1L, it[3] as Long) }
        }
        val now = System.currentTimeMillis()
        val before = Aging.compute(parties, old, now)
        val after = Aging.compute(dao, now)
        assertTrue("something to age", before.receivable.isNotEmpty() && before.payable.isNotEmpty())
        assertEquals(before, after)
    }

    @Test
    fun `a year of khata sales read a fortnight at a time are the single query's rows`() = runBlocking {
        book()
        val customer = dao.partiesWithBalanceOnce().first { it.isCustomer }.id
        val now = System.currentTimeMillis()
        // Sales with goods across the year, two lines each, one of them free: a sale's lines must never be split.
        repeat(30) { k ->
            dao.insertEntryWithItems(
                LedgerEntry(partyId = customer, amount = 900.0, isGiven = true, entryNumber = "", timestamp = now - k * 11 * day),
                listOf(
                    EntryItem(entryId = 0, itemName = "Test Item", quantity = 2.0, rate = 450.0),
                    EntryItem(entryId = 0, lineNo = 1, itemName = "Test Gift", quantity = 1.0, rate = 0.0, isBonus = true)
                )
            )
        }
        val from = now - 365 * day
        val to = now + 60_000
        val single = dao.khataSaleRowsBetween(from, to)
        assertTrue("a year of sales over many fortnights", single.size > 1_000)
        assertEquals(single, KhataSales.rowsBetween(dao, from, to))
        // A window left open is read in one go, as before.
        assertEquals(dao.khataSaleRowsBetween(0L, Long.MAX_VALUE), KhataSales.rowsBetween(dao, 0L, Long.MAX_VALUE))
    }
}
