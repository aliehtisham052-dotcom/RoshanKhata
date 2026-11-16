package com.innovation313.roshankhata.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.innovation313.roshankhata.R
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Who may hear a money figure from Hudhud (16 Nov 2026, audit T13b): on a counter with a cashier signed in, every ask behind
 * REPORTS — every figure, a span's sales or profit, money in, the day's activity, the drawer, when a customer pays, the
 * cashbook, the best sellers, the Qarz-e-Hasna, every counter card and a product's cost, suppliers, sales and cover — says
 * "only the owner or a manager" and carries no figure and no amount; the owner (and a manager) get the figure. The gate is
 * Access's signed-in role (HudhudGate.reports), never anything said. A made-up book in memory (lesson 80).
 */
@RunWith(AndroidJUnit4::class)
class HudhudCashierGateTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: KhataDatabase
    private lateinit var dao: KhataDao
    private var customer = 0L
    private var product = 0L

    @Before
    fun open() = runBlocking {
        Access.resetForTest()
        db = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java).allowMainThreadQueries().build()
        dao = db.ledgerDao()
        customer = dao.insertParty(Party(name = "Ali Raza"))
        dao.insertEntryNumbered(LedgerEntry(partyId = customer, amount = 1000.0, isGiven = true, entryNumber = ""))
        dao.insertEntryNumbered(LedgerEntry(partyId = customer, amount = 300.0, isGiven = false, entryNumber = ""))
        product = dao.insertProduct(Product(name = "Urea", nameKey = "urea", normalisedName = "urea", defaultUnit = "bag", costPrice = 3000.0))
    }

    @After
    fun close() {
        Access.resetForTest()
        db.close()
    }

    private fun cashier() = Staff(id = 7, name = "Tariq Butt", monthlySalary = 1.0, role = Role.CASHIER)

    /** Every ask HudhudBook keeps behind REPORTS, built for this book. */
    private fun moneyAsks(): List<Hudhud.Ask> {
        val today = VoiceQuestion.Window.TODAY
        val month = VoiceQuestion.Window.THIS_MONTH
        return VoiceQuestion.Kind.values().map { Hudhud.Ask.Figure(it) } +
            listOf(
                Hudhud.Ask.Period(Hudhud.Measure.SALES, month), Hudhud.Ask.Period(Hudhud.Measure.PROFIT, month),
                Hudhud.Ask.Collected(today), Hudhud.Ask.Activity(today), Hudhud.Ask.Drawer, Hudhud.Ask.WhenPays(listOf(customer)),
                Hudhud.Ask.WhenPays(emptyList()), Hudhud.Ask.Cashbook(today), Hudhud.Ask.TopGoods(month), Hudhud.Ask.Qarz,
                Hudhud.Ask.Now(Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GET))
            ) +
            Hudhud.CounterFacet.values().map { Hudhud.Ask.Counter(it, today) } +
            listOf(Hudhud.GoodsFacet.COST, Hudhud.GoodsFacet.SUPPLIERS, Hudhud.GoodsFacet.SOLD, Hudhud.GoodsFacet.COVER)
                .map { Hudhud.Ask.Goods(listOf(product), it, month) }
    }

    @Test
    fun aCashierHearsNoMoneyFigure() = runBlocking {
        Access.switchCounterMode(true)
        Access.signIn(cashier())
        assertTrue(!HudhudGate.reports())
        val ownerOnly = context.getString(R.string.hudhud_owner_only)
        val parties = dao.partiesWithBalanceOnce()
        for (ask in moneyAsks()) {
            val a = HudhudBook.answer(context, dao, ask, parties)
            assertNull("$ask showed the figure ${a.figure}", a.figure)
            assertTrue("$ask showed an amount in a row", a.rows.all { it.amount == null })
            assertTrue("$ask did not say who may see it: ${a.lines}", a.lines.any { ownerOnly in it })
        }
    }

    @Test
    fun theOwnerAndAManagerHearTheFigure() = runBlocking {
        val parties = dao.partiesWithBalanceOnce()
        val toGet = HudhudBook.money(700.0)
        // The owner's own phone (counter mode off): the owner.
        assertEquals(toGet, HudhudBook.answer(context, dao, Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GET), parties).figure)
        val most = HudhudBook.answer(context, dao, Hudhud.Ask.Figure(VoiceQuestion.Kind.WHO_OWES_MOST), parties)
        assertEquals(listOf("Ali Raza"), most.rows.map { it.name })
        // A counter with a manager signed in: REPORTS is a manager's too.
        Access.switchCounterMode(true)
        Access.signIn(Staff(id = 8, name = "Bashir Ahmad", monthlySalary = 1.0, role = Role.MANAGER))
        assertEquals(toGet, HudhudBook.answer(context, dao, Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GET), parties).figure)
        assertNotNull(HudhudBook.answer(context, dao, Hudhud.Ask.Goods(listOf(product), Hudhud.GoodsFacet.COST), parties).figure)
    }

    @Test
    fun aLockedCounterHearsNoFigureEither() = runBlocking {
        // Counter mode on, nobody signed in yet: not the owner.
        Access.switchCounterMode(true)
        val a = HudhudBook.answer(context, dao, Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GET), dao.partiesWithBalanceOnce())
        assertNull(a.figure)
    }
}
