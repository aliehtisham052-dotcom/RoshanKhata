package com.innovation313.roshankhata.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.screens.ReportPeriod
import com.innovation313.roshankhata.screens.periodWindow
import com.innovation313.roshankhata.ui.Format
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.util.TimeZone

/**
 * One question, one function (16 Nov 2026, audit T21): every money figure Hudhud tells is the figure the app's own screen
 * shows for the same book, read by the very call that screen makes — never a sum of Hudhud's own (the audit found him
 * once adding up the day book himself and saying Rs 3,800 where Insights said Rs 1,300). Each test names its screen and
 * the call it makes; the figure is compared as both write it ([HudhudBook.money] over the screen's Double).
 *
 * The book is [HudhudGoldenBook] (made-up names), in UTC, en-US, the book's own currency sign; the tests skip
 * themselves within 30 minutes of midnight. Robolectric, real SQLite.
 *
 * Figures with no screen of their own are said so beside where they would be (cover days, a span's money in, a span's
 * cashbook, dues by age, stock value).
 */
@RunWith(AndroidJUnit4::class)
class HudhudParityTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: KhataDatabase
    private lateinit var dao: KhataDao
    private lateinit var ids: HudhudGoldenBook.Ids
    private lateinit var keptZone: TimeZone
    private lateinit var keptLocale: Locale
    private lateinit var keptSign: String

    @Before
    fun open() {
        keptZone = TimeZone.getDefault()
        keptLocale = Locale.getDefault()
        keptSign = Currency.symbol
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.US)
        Currency.symbol = Currency.DEFAULT
        Access.resetForTest()
        db = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java).allowMainThreadQueries().build()
        dao = db.ledgerDao()
        val now = System.currentTimeMillis()
        assumeTrue("too close to midnight for 'a few minutes ago' to be today", HudhudGoldenBook.clockIsSafe(now))
        runBlocking { ids = HudhudGoldenBook.seed(dao, now) }
    }

    @After
    fun close() {
        Access.resetForTest()
        db.close()
        TimeZone.setDefault(keptZone)
        Locale.setDefault(keptLocale)
        Currency.symbol = keptSign
    }

    private suspend fun ask(a: Hudhud.Ask): HudhudBook.Answer = HudhudBook.answer(context, dao, a, dao.partiesWithBalanceOnce())

    private fun money(v: Double) = HudhudBook.money(v)

    // ------------------------------------------------------------------------------------------------ Sale Insights

    /** Insights' today card: `Insights.thisMonth(dao).todayGiven` (salesTotalBetween over today). */
    @Test
    fun salesTodayIsInsightsTodayFigure() {
        runBlocking {
            val screen = Insights.thisMonth(dao).todayGiven
            assertTrue("the book has a sale today", screen > 0.0)
            assertEquals(money(screen), ask(Hudhud.Ask.Figure(VoiceQuestion.Kind.SALES_TODAY)).figure)
        }
    }

    /** Insights' month figure: `Insights.thisMonth(dao).thisMonthTotal`. */
    @Test
    fun salesThisMonthIsInsightsMonthFigure() {
        runBlocking {
            val screen = Insights.thisMonth(dao).thisMonthTotal
            assertEquals(money(screen), ask(Hudhud.Ask.Figure(VoiceQuestion.Kind.SALES_MONTH)).figure)
        }
    }

    /**
     * Money received today: Insights' `todayReceived` (customerReceivedBetween over today). The goods return and the owner's
     * own money in the cashbook are in the book and in neither figure.
     */
    @Test
    fun moneyInTodayIsInsightsReceived() {
        runBlocking {
            val screen = Insights.thisMonth(dao).todayReceived
            assertTrue("the book has money in today", screen > 0.0)
            assertEquals(money(screen), ask(Hudhud.Ask.Collected(VoiceQuestion.Window.TODAY)).figure)
            // A span's money in (this month, this week…) is on no screen: Insights says today's only.
        }
    }

    /** Insights' profit card: `Profit.thisMonth(dao)` and `Profit.lastMonth(dao)`, as InsightsActivity.load reads them. */
    @Test
    fun profitIsInsightsProfit() {
        runBlocking {
            val month = Profit.thisMonth(dao)
            val card = ask(Hudhud.Ask.Figure(VoiceQuestion.Kind.PROFIT_MONTH))
            if (month.isEmpty) assertNull(card.figure) else assertEquals(money(month.grossProfit), card.figure)
            assertFalse("the book's sales this month have a cost, so a profit is known", month.isEmpty)
            val last = Profit.lastMonth(dao)
            val lastCard = ask(Hudhud.Ask.Period(Hudhud.Measure.PROFIT, VoiceQuestion.Window.LAST_MONTH))
            if (last.isEmpty) assertNull(lastCard.figure) else assertEquals(money(last.grossProfit), lastCard.figure)
        }
    }

    /** Insights' top products: `Insights.thisMonth(dao).topProducts` (topProductsBetween, five), name and quantity. */
    @Test
    fun topGoodsAreInsightsTopProducts() {
        runBlocking {
            val screen = Insights.thisMonth(dao).topProducts
            assertTrue("the book sold goods this month", screen.isNotEmpty())
            val card = ask(Hudhud.Ask.TopGoods(VoiceQuestion.Window.THIS_MONTH))
            assertEquals(screen.map { it.name to Format.qty(it.qty, it.unit) }, card.rows.map { it.name to it.figure })
        }
    }

    // --------------------------------------------------------------------------------------------- the sales report

    /**
     * The sales report's revenue for its own periods: `SalesReport.compute(dao, periodWindow(period))`, as
     * SalesReportScreen reads it, against the sale Hudhud tells for the span (which opens that report).
     */
    @Test
    fun aSpansSaleIsTheSalesReportRevenue() {
        runBlocking {
            val pairs = listOf(
                ReportPeriod.TODAY to VoiceQuestion.Window.TODAY,
                ReportPeriod.WEEK to VoiceQuestion.Window.THIS_WEEK,
                ReportPeriod.MONTH to VoiceQuestion.Window.THIS_MONTH
            )
            for ((period, window) in pairs) {
                val (from, to) = periodWindow(period)
                val report = SalesReport.compute(dao, from, to)
                assertEquals("$period", money(report.revenue), ask(Hudhud.Ask.Period(Hudhud.Measure.SALES, window)).figure)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------- Home and Khata

    /** Home's two totals: `PartyTotals.of(observePartiesWithBalance().first().map { balance })` (MainActivity). */
    @Test
    fun toGetAndToGiveAreHomeTotals() {
        runBlocking {
            val sides = PartyTotals.of(dao.observePartiesWithBalance().first().map { it.balance })
            assertTrue(sides.get > 0.0 && sides.give > 0.0)
            assertEquals(money(sides.get), ask(Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GET)).figure)
            assertEquals(money(sides.give), ask(Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GIVE)).figure)
        }
    }

    /** A customer's balance: the Khata list's own row (`partiesWithBalanceOnce`), as his page shows it. */
    @Test
    fun aCustomersBalanceIsHisKhataRow() {
        runBlocking {
            val row = dao.observePartiesWithBalance().first().first { it.id == ids.ali }
            assertEquals(money(kotlin.math.abs(row.balance)), ask(Hudhud.Ask.Parties(listOf(ids.ali))).figure)
        }
    }

    // ------------------------------------------------------------------------------------- Cashbook, Close the day

    /**
     * What should be in the drawer: `DayCloseMath.drawerByTheBooks(dao).expected`, the call CashbookActivity's figures and
     * Close the day make. (The Cashbook screen's Money in / Money out are the whole book's — observeCashIncome/Expense —
     * and Hudhud gives the cashbook only a day, a week or a month at a time: no whole-book figure to compare.)
     */
    @Test
    fun theDrawerIsCloseTheDaysFigure() {
        runBlocking {
            val screen = DayCloseMath.drawerByTheBooks(dao).expected
            assertEquals(money(screen), ask(Hudhud.Ask.Drawer).figure)
        }
    }

    // ------------------------------------------------------------------------------------------ Products and stock

    /** Low stock: `Valuation.compute(dao).low`, the call ProductsActivity (and the bell) make. */
    @Test
    fun lowStockIsProductsLowList() {
        runBlocking {
            val low = Valuation.compute(dao).low
            assertTrue("the book has a product under its level", low.isNotEmpty())
            val card = ask(Hudhud.Ask.Status(Hudhud.StatusOf.LOW_STOCK))
            assertEquals(low.size.toString(), card.figure)
            assertEquals(low.take(6).map { it.name }, card.rows.map { it.name })
        }
    }

    /** A product's stock: its row of `Valuation.compute(dao)`, as Products shows it. */
    @Test
    fun aProductsStockIsProductsFigure() {
        runBlocking {
            for (id in listOf(ids.urea, ids.dap, ids.paracetamol)) {
                val row = Valuation.compute(dao).rows.first { it.productId == id }
                val onHand = row.onHand
                assertNotNull("${row.name} has stock", onHand)
                assertEquals(row.name, Format.qty(onHand!!, row.unit), ask(Hudhud.Ask.Goods(listOf(id), Hudhud.GoodsFacet.STOCK)).figure)
            }
        }
    }

    /**
     * How long the shelf lasts has no screen of its own (the low-stock order, Automations.reorder, reads the same
     * StockCover.soldBase). What the card says it stands on — the stock — is held to Products' figure.
     */
    @Test
    fun theCoversStockIsProductsFigure() {
        runBlocking {
            val row = Valuation.compute(dao).rows.first { it.productId == ids.urea }
            val card = ask(Hudhud.Ask.Goods(listOf(ids.urea), Hudhud.GoodsFacet.COVER))
            assertNotNull("Urea sold in the last 30 days and has stock: a number of days", card.figure)
            val stockLine = HudhudFacts.line(context, R.string.hudhud_l_stock, Format.qty(row.onHand ?: 0.0, row.unit))
            assertTrue("${card.lines} has $stockLine", stockLine in card.lines)
        }
    }

    // --------------------------------------------------------------------------------------------------- Zakat

    /**
     * Qarz-e-Hasna: the Zakat screen's figure, `Zakat.fromParties(dao.observeZakatBalancesByParty().first())
     * .qarzeHasnaGiven`. In this book every borrower owes more than his loan, and the two agree.
     */
    @Test
    fun qarzIsZakatsFigure() {
        runBlocking {
            val screen = Zakat.fromParties(dao.observeZakatBalancesByParty().first()).qarzeHasnaGiven
            assertTrue(screen > 0.0)
            assertEquals(money(screen), ask(Hudhud.Ask.Qarz).figure)
        }
    }

    /**
     * Fault, found by reading (16 Nov 2026, not run): Hudhud adds every customer's raw Qarz-e-Hasna column
     * (`HudhudFacts.qarz`: `observeZakatBalancesByParty().filter { qarz > 0.005 }.sumOf { qarz }`), while the Zakat
     * screen caps each customer's loan by what he owes in all (`Zakat.fromParties`: a customer whose ordinary payments
     * paid part of the loan back owes less than the loan; one who has paid back more owes nothing). Here: Ali lent 5,000,
     * paid back 2,000 as an ordinary "I got" (he owes 3,000); Bashir lent 1,000, paid back 1,500 (the shop owes him 500).
     * Zakat says 3,000 of Qarz-e-Hasna, Hudhud would say 6,000. Left to the lead: the production code is not changed here.
     */
    @Ignore("fault: Hudhud's Qarz sums the raw qarz column; Zakat.fromParties caps it per customer by what he owes (6,000 against 3,000)")
    @Test
    fun qarzPaidBackAsOrdinaryPaymentsIsZakatsFigure() {
        runBlocking {
            val book = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java).allowMainThreadQueries().build()
            try {
                val d = book.ledgerDao()
                val now = System.currentTimeMillis()
                val ali = d.insertParty(Party(name = "Ali Raza"))
                val bashir = d.insertParty(Party(name = "Bashir Ahmad"))
                d.insertEntryWithItems(LedgerEntry(partyId = ali, amount = 5_000.0, isGiven = true, entryNumber = "", timestamp = now - 3_600_000, isQarzeHasna = true), emptyList())
                d.insertEntryWithItems(LedgerEntry(partyId = ali, amount = 2_000.0, isGiven = false, entryNumber = "", timestamp = now - 1_800_000), emptyList())
                d.insertEntryWithItems(LedgerEntry(partyId = bashir, amount = 1_000.0, isGiven = true, entryNumber = "", timestamp = now - 3_600_000, isQarzeHasna = true), emptyList())
                d.insertEntryWithItems(LedgerEntry(partyId = bashir, amount = 1_500.0, isGiven = false, entryNumber = "", timestamp = now - 1_800_000), emptyList())
                val screen = Zakat.fromParties(d.observeZakatBalancesByParty().first()).qarzeHasnaGiven
                assertEquals(3_000.0, screen, 0.001)
                val card = HudhudBook.answer(context, d, Hudhud.Ask.Qarz, d.partiesWithBalanceOnce())
                assertEquals(money(screen), card.figure)
            } finally {
                book.close()
            }
        }
    }

    // Dues by age (AgingActivity, `Aging`) and the stock's value (Products → Stock value, `Valuation`'s cost) are not told by
    // Hudhud as figures — he points to the dues page, and gives a product's cost, not the shelf's worth — so they have no
    // pair here.
}
