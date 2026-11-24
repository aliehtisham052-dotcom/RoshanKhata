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
 * BC-252 (24 Nov 2026, money, importance 10): the sales report's revenue is the sales total, to the coin, even when a
 * ticket's discount does not divide evenly over its lines — a pair of figures on the existing [ReportsAgreeBook], and the
 * same pair on a book of discounted tickets in the three kinds of coin (Rs, KWD, Rp). Every ticket's own lines add up to
 * the ticket's total. CI is the first run of this class (Room); the arithmetic itself is held in TicketSpreadTest and
 * SoldLinesSpreadTest. Made-up names and figures.
 */
class ReportsAgreeSpreadTest {

    private lateinit var dir: File
    private lateinit var db: KhataDatabase
    private val dao get() = db.khataDao()
    private val at = System.currentTimeMillis()
    private val from get() = at - 10_000
    private val to get() = at + 10_000
    private val signBefore = Currency.symbol

    @Before
    fun open() {
        dir = Files.createTempDirectory("rk-reports-spread").toFile()
        db = DesktopDatabase.open(dir.resolve("khata.db"))
    }

    @After
    fun close() {
        Currency.symbol = signBefore
        db.close()
        dir.deleteRecursively()
    }

    private fun same(what: String, want: Double, got: Double) =
        assertTrue("$what: wanted $want, got $got", Money.same(want, got))

    /** Every ticket of the report (counter tickets by id, khata entries by minus their id) adds up to [expected]'s figure. */
    private fun ticketsAdd(report: SalesReport, expected: Map<Long, Double>) {
        val sums = report.lines.groupBy { it.saleId }.mapValues { (_, ls) -> ls.sumOf { Money.minor(it.revenue) } }
        expected.forEach { (id, total) -> assertEquals("ticket $id", Money.minor(total), sums[id]) }
    }

    @Test
    fun `on the shared book the sales report's revenue is the sales total, and each ticket adds up`() = runBlocking {
        ReportsAgreeBook.seed(dao, at)
        val report = SalesReport.compute(dao, from, to)
        same("revenue against the sales total", dao.salesTotalBetween(from, to), report.revenue)
        same("revenue against the hand-worked sales", ReportsAgreeBook.SALES, report.revenue)
        // each counter ticket's lines add up to the ticket's own total
        val tickets = dao.salesBetween(from, to).filter { it.status == SaleStatus.DONE }
        ticketsAdd(report, tickets.associate { it.id to it.grandTotal })
    }

    @Test
    fun `on the shared book a discounted ticket still agrees`() = runBlocking {
        ReportsAgreeBook.seed(dao, at)
        // 3 bags at 1,000 with 100 off, in cash: 2,900 — not 2,900.01
        val sale = dao.postSale(
            Sale(timestamp = at - 100, subtotal = 0.0, grandTotal = 0.0, paidTotal = 0.0, discountAmount = 100.0),
            List(3) { SaleItem(saleId = 0, itemName = "Bag $it", quantity = 1.0, unit = "bag", rate = 1_000.0) },
            listOf(SalePayment(saleId = 0, method = PaymentMethod.CASH, amount = 2_900.0))
        )
        val report = SalesReport.compute(dao, from, to)
        same("revenue against the sales total", dao.salesTotalBetween(from, to), report.revenue)
        same("revenue against the hand-worked sales", ReportsAgreeBook.SALES + 2_900.0, report.revenue)
        ticketsAdd(report, mapOf(sale to 2_900.0))
    }

    /** A walk-in ticket, a customer's ticket and a khata entry, each with an amount that does not divide over three lines. */
    private fun discountedBookAgrees(sign: String, price: Double, off: Double, khataAmount: Double) = runBlocking {
        Currency.symbol = sign
        val aslam = dao.insertParty(Party(name = "Aslam"))
        val bilal = dao.insertParty(Party(name = "Bilal"))
        fun lines() = List(3) { SaleItem(saleId = 0, itemName = "Item $it", quantity = 1.0, unit = "bag", rate = price) }
        val ticketTotal = 3 * price - off
        val walkIn = dao.postSale(Sale(timestamp = at - 300, subtotal = 0.0, grandTotal = 0.0, paidTotal = 0.0, discountAmount = off), lines(),
            listOf(SalePayment(saleId = 0, method = PaymentMethod.CASH, amount = ticketTotal)))
        val onAccount = dao.postSale(Sale(partyId = aslam, customerName = "Aslam", timestamp = at - 200, subtotal = 0.0, grandTotal = 0.0, paidTotal = 0.0,
            discountAmount = off), lines(), emptyList())
        // a khata entry written with goods worth 3 x price, for less
        val entry = dao.insertEntryWithItems(
            LedgerEntry(partyId = bilal, amount = khataAmount, isGiven = true, entryNumber = "", timestamp = at - 100),
            List(3) { EntryItem(entryId = 0, itemName = "Good $it", quantity = 1.0, unit = "bag", rate = price) }
        )
        val report = SalesReport.compute(dao, from, to)
        same("$sign: revenue against the sales total", dao.salesTotalBetween(from, to), report.revenue)
        same("$sign: the hand-worked sales", 2 * ticketTotal + khataAmount, report.revenue)
        ticketsAdd(report, mapOf(walkIn to ticketTotal, onAccount to ticketTotal, -entry to khataAmount))
        // and the by-item rows (the same lines, grouped) still add up to it
        same("$sign: by item", report.revenue, report.byItem.sumOf { it.revenue })
    }

    @Test fun `discounts that do not divide evenly agree in rupees`() = discountedBookAgrees("Rs", 1_000.0, 100.0, 2_000.0)
    @Test fun `discounts that do not divide evenly agree in dinars`() = discountedBookAgrees("KWD", 10.0, 1.0, 20.0)
    @Test fun `discounts that do not divide evenly agree in rupiah`() = discountedBookAgrees("Rp", 1_000.0, 100.0, 2_000.0)
}
