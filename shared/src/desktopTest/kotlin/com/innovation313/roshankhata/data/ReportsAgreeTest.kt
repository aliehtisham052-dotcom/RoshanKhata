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
 * Plan item A2 (24 Nov 2026, importance 10): one made-up book ([ReportsAgreeBook]) on which every report must give the
 * same figure for the same thing. Each test is one PAIR of screens, named by what they show; the hand-worked figures in
 * [ReportsAgreeBook] are the oracle where one exists, and money is compared to the smallest coin ([Money.same]), the
 * rule a screen rounds by. The sum-of-Doubles half is ReportsAgreeMoneyTest (plain Kotlin, run in the shared harness).
 */
class ReportsAgreeTest {

    private lateinit var dir: File
    private lateinit var db: KhataDatabase
    private val dao get() = db.khataDao()
    private val at = System.currentTimeMillis()
    private val from get() = at - 10_000
    private val to get() = at + 10_000
    private lateinit var ids: ReportsAgreeBook.Ids

    @Before
    fun open() {
        dir = Files.createTempDirectory("rk-reports-agree").toFile()
        db = DesktopDatabase.open(dir.resolve("khata.db"))
        runBlocking { ids = ReportsAgreeBook.seed(dao, at) }
    }

    @After
    fun close() {
        db.close()
        dir.deleteRecursively()
    }

    private fun same(what: String, want: Double, got: Double) =
        assertTrue("$what: wanted $want, got $got", Money.same(want, got))

    @Test
    fun `Home's net balance is the sum of the parties' balances, by PartyTotals`() = runBlocking {
        val balances = dao.partiesWithBalanceOnce().map { it.balance }
        val sides = PartyTotals.of(balances)
        same("to get", ReportsAgreeBook.TO_GET, sides.get)
        same("to give", ReportsAgreeBook.TO_GIVE, sides.give)
        same("net", ReportsAgreeBook.TO_GET - ReportsAgreeBook.TO_GIVE, sides.net)
        // The loan and the opening balance are owed money like any other.
        same("Kashif", 8_500.0, dao.partiesWithBalanceOnce().first { it.id == ids.kashif }.balance)
    }

    @Test
    fun `dues by age add up to the same two totals as Home`() = runBlocking {
        val sides = PartyTotals.of(dao.partiesWithBalanceOnce().map { it.balance })
        val aging = Aging.compute(dao, at + 1_000)
        same("receivable", sides.get, aging.receivable.sumOf { it.balance })
        same("payable", sides.give, aging.payable.sumOf { it.balance })
        same("receivable by age", sides.get, aging.receivableTotals.sum())
        same("payable by age", sides.give, aging.payableTotals.sum())
        (aging.receivable + aging.payable).forEach { same("${it.name}: its ages are its balance", it.balance, it.buckets.sum()) }
    }

    @Test
    fun `sales are one figure on Insights, the sales report, the day report and the day book`() = runBlocking {
        val total = dao.salesTotalBetween(from, to)
        same("the hand-worked sales", ReportsAgreeBook.SALES, total)   // the opening, the loan and the supplier are no sale
        same("Insights' month", total, Insights.thisMonth(dao).thisMonthTotal)
        same("Insights' today", total, Insights.thisMonth(dao).todayGiven)
        same("the sales report", total, SalesReport.compute(dao, from, to).revenue)
        same("the day report", total, ZReport.compute(dao, from, to).salesTotal)
        // The day book: every customer "I gave" that is not a loan nor an opening balance, and the walk-in tickets.
        val rows = DayBook.rows(dao, from, to)
        val khata = rows.filter { it.kind == DayBook.Kind.GAVE && it.customer && !it.loan && !it.opening }.sumOf { it.amount }
        val walkIns = dao.salesBetween(from, to).filter { it.status == SaleStatus.DONE && it.customerName == null }.sumOf { it.grandTotal }
        same("the day book", total, khata + walkIns)
    }

    @Test
    fun `money received is one figure on Insights and the day book`() = runBlocking {
        val received = dao.customerReceivedBetween(from, to)
        same("the hand-worked money in", ReportsAgreeBook.RECEIVED, received)
        same("Insights' today", received, Insights.thisMonth(dao).todayReceived)
        val rows = DayBook.rows(dao, from, to)
        val khata = rows.filter { it.kind == DayBook.Kind.GOT && it.customer && !it.opening }.sumOf { it.amount }
        val counter = rows.filter { it.kind == DayBook.Kind.CASH_IN && it.who == CashCategory.SALE }.sumOf { it.amount }
        same("the day book", received, khata + counter)
    }

    @Test
    fun `the drawer by the books is the day book's cash, and Home, Close the day and the evening notice read one call`() = runBlocking {
        val now = at + 1_000
        val drawer = DayCloseMath.drawerByTheBooks(dao, now)
        same("cash in by hand", ReportsAgreeBook.DRAWER_IN, drawer.cashIn)
        same("cash out by hand", ReportsAgreeBook.DRAWER_OUT, drawer.cashOut)
        // From the day book alone: what moved in cash, by the drawer's own rule (no way written counts as cash on the cashbook only).
        val rows = DayBook.rows(dao, DayCloseMath.firstCountFrom(now) + 1, now + 1)
        fun cashbook(r: DayBook.Row) = r.method == null || r.method == PaymentMethod.CASH
        val booksIn = rows.filter { it.kind == DayBook.Kind.CASH_IN && cashbook(it) }.sumOf { it.amount } +
            rows.filter { it.kind == DayBook.Kind.GOT && it.method == PaymentMethod.CASH && it.customer }.sumOf { it.amount }
        val booksOut = rows.filter { it.kind == DayBook.Kind.CASH_OUT && cashbook(it) }.sumOf { it.amount } +
            rows.filter { it.kind == DayBook.Kind.GAVE && it.method == PaymentMethod.CASH }.sumOf { it.amount } +
            rows.filter { it.kind == DayBook.Kind.BILL_PAID || it.kind == DayBook.Kind.STAFF_PAID }.sumOf { it.amount }
        same("cash in, from the day book", drawer.cashIn, booksIn)
        same("cash out, from the day book", drawer.cashOut, booksOut)
        same("expected", ReportsAgreeBook.DRAWER_IN - ReportsAgreeBook.DRAWER_OUT, drawer.expected)
        // Home and the 21:00 notice take the same call a second time and must read the same: nothing moved between.
        same("a second reading", drawer.expected, DayCloseMath.drawerByTheBooks(dao, now).expected)
    }

    @Test
    fun `profit is one figure on Insights and the sales report, staff pay included`() = runBlocking {
        val profit = Profit.between(dao, from, to)
        val report = SalesReport.compute(dao, from, to)
        same("gross, hand-worked", ReportsAgreeBook.GROSS_PROFIT, profit.grossProfit)
        same("gross, sales report", profit.grossProfit, report.grossProfit)
        same("expenses, Insights", ReportsAgreeBook.CASHBOOK_EXPENSES + ReportsAgreeBook.STAFF_PAID, profit.expenses)
        same("expenses, sales report", profit.expenses, report.expenses)
        same("net", profit.netAfterExpenses, report.netProfit)
    }

    @Test
    fun `stock value is each product's shelf times its cost, rounded once per product`() = runBlocking {
        val valuation = Valuation.compute(dao)
        val urea = valuation.rows.first { it.productId == ids.urea }
        assertEquals(ReportsAgreeBook.UREA_ON_HAND, urea.onHand!!, 0.0001)
        same("Urea's value", ReportsAgreeBook.UREA_ON_HAND * ReportsAgreeBook.UREA_COST, urea.value!!)
        same("the total", valuation.rows.sumOf { Money.round(maxOf(it.onHand ?: 0.0, 0.0) * (it.cost ?: 0.0)) }, valuation.total)
    }
}
