package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The paged reader under a page-at-a-time restore (17 Nov 2026, the audit's L001, first step), on a real book's backup:
 * [BackupCodec.readPages] hands out exactly the rows [BackupCodec.parseFile] reads — every table that grows with the
 * shop, in the same order, page after page of at most the asked size — and the same counts; through a backup's lock too;
 * a locked file without its password hands out nothing; a file that is not a backup is refused. Made-up names and figures.
 */
class BackupPagesTest {

    private lateinit var dir: File
    private lateinit var db: KhataDatabase
    private val dao get() = db.khataDao()
    private val pageRows = 3

    @Before
    fun open() {
        dir = Files.createTempDirectory("rk-pages").toFile()
        db = DesktopDatabase.open(dir.resolve("khata.db"))
        runBlocking {
            for (p in 0 until 5) {
                val id = dao.insertParty(Party(name = "Ali Raza $p", isCustomer = true))
                for (e in 0 until 4) {
                    dao.insertEntryWithItems(
                        LedgerEntry(partyId = id, amount = 100.0 + e, isGiven = e % 2 == 0, entryNumber = "", note = "Urea $e"),
                        if (e % 2 == 0) listOf(EntryItem(entryId = 0, itemName = "Urea", quantity = 1.0 + e, unit = "bag", rate = 50.0)) else emptyList()
                    )
                }
            }
            for (c in 0 until 7) dao.insertCashEntryAudited(CashEntry(amount = 10.0 + c, isIncome = c % 2 == 0, category = "Tea"))
            for (s in 0 until 4) dao.postSale(
                Sale(subtotal = 0.0, grandTotal = 0.0, paidTotal = 0.0),
                listOf(SaleItem(saleId = 0, itemName = "Soap", quantity = 1.0, rate = 90.0), SaleItem(saleId = 0, itemName = "Tea", quantity = 2.0, rate = 40.0)),
                listOf(SalePayment(saleId = 0, method = PaymentMethod.CASH, amount = 170.0))
            )
        }
    }

    @After
    fun close() {
        db.close()
        dir.deleteRecursively()
    }

    private fun export(name: String, password: String? = null): File = runBlocking {
        val file = dir.resolve(name)
        BackupSeal.writer(file.outputStream(), password, BackupSeal.MIN_ITERATIONS).use { BackupCodec.exportTo(dao, "Shop", JSONObject(), it) }
        file
    }

    private class Gathered {
        val pages = ArrayList<BackupCodec.Page>()
        inline fun <reified P : BackupCodec.Page, T> rows(of: (P) -> List<T>): List<T> = pages.filterIsInstance<P>().flatMap(of)
    }

    private fun pagesOf(file: File, passwords: List<String> = emptyList()): Pair<BackupCodec.ImportResult, Gathered> {
        val g = Gathered()
        val (result, _) = file.inputStream().buffered().use { BackupCodec.readPages(it, passwords, pageRows) { p -> g.pages += p } }
        return result to g
    }

    private fun assertSameAsParsed(file: File, passwords: List<String> = emptyList()) {
        val (parsedResult, data) = BackupCodec.parseFile(file, passwords)
        assertTrue("$parsedResult", parsedResult is BackupCodec.ImportResult.Ok)
        val (result, g) = pagesOf(file, passwords)
        assertTrue("$result", result is BackupCodec.ImportResult.Ok)
        data!!
        assertEquals(data.parties, g.rows<BackupCodec.Page.Parties, Party> { it.rows })
        assertEquals(data.entries, g.rows<BackupCodec.Page.Entries, LedgerEntry> { it.rows })
        assertEquals(data.entryItems, g.rows<BackupCodec.Page.EntryItems, EntryItem> { it.rows })
        assertEquals(data.cash, g.rows<BackupCodec.Page.Cash, CashEntry> { it.rows })
        assertEquals(data.sales, g.rows<BackupCodec.Page.Sales, Sale> { it.rows })
        assertEquals(data.saleItems, g.rows<BackupCodec.Page.SaleItems, SaleItem> { it.rows })
        assertEquals(data.salePayments, g.rows<BackupCodec.Page.SalePayments, SalePayment> { it.rows })
        assertEquals(data.audit, g.rows<BackupCodec.Page.Audit, AuditLog> { it.rows })
        assertEquals(data.bookSeals, g.rows<BackupCodec.Page.Seals, BookSeal> { it.rows })
        // The book is big enough to need several pages of the big tables, and no page is over the size asked.
        assertTrue(g.pages.count { it is BackupCodec.Page.Entries } >= 2)
        assertTrue(g.pages.count { it is BackupCodec.Page.Cash } >= 2)
        assertTrue(g.pages.all { it.size in 1..pageRows })
        // Every page of a table but its last is full: rows are let go a whole page at a time.
        for (kind in g.pages.map { it::class }.distinct()) {
            val sizes = g.pages.filter { it::class == kind }.map { it.size }
            assertTrue("$kind $sizes", sizes.dropLast(1).all { it == pageRows })
        }
        val ok = result as BackupCodec.ImportResult.Ok
        assertEquals(data.parties.size, ok.parties)
        assertEquals(data.entries.size, ok.entries)
        assertEquals(data.cash.size, ok.cash)
    }

    @Test
    fun `the pages are the parser's rows, in its order`() = assertSameAsParsed(export("plain.txt"))

    @Test
    fun `a locked backup pages through its lock, and hands out nothing without the password`() {
        val locked = export("locked.rkb", password = "made-up words 123")
        assertSameAsParsed(locked, listOf("made-up words 123"))
        val (result, g) = pagesOf(locked)
        assertEquals(BackupCodec.ImportResult.Why.LOCKED, (result as BackupCodec.ImportResult.Failed).why)
        assertTrue(g.pages.isEmpty())
    }

    @Test
    fun `a file that is not a backup is refused`() {
        val other = dir.resolve("other.json").also { it.writeText("{\"format\":\"SomethingElse\",\"version\":1,\"entries\":[]}") }
        val (result, _) = pagesOf(other)
        assertEquals(BackupCodec.ImportResult.Why.NOT_OURS, (result as BackupCodec.ImportResult.Failed).why)
        val newer = dir.resolve("newer.json").also { it.writeText("{\"format\":\"RoshanKhata\",\"version\":${BackupCodec.FORMAT_VERSION + 1}}") }
        assertEquals(BackupCodec.ImportResult.Why.TOO_NEW, ((pagesOf(newer).first) as BackupCodec.ImportResult.Failed).why)
    }
}
