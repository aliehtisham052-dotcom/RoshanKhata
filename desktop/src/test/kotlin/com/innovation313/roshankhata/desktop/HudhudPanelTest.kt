package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.Access
import com.innovation313.roshankhata.data.AnswerBook
import com.innovation313.roshankhata.data.DayCloseMath
import com.innovation313.roshankhata.data.DesktopDatabase
import com.innovation313.roshankhata.data.EntryItem
import com.innovation313.roshankhata.data.FileShopSettings
import com.innovation313.roshankhata.data.Hudhud
import com.innovation313.roshankhata.data.LedgerEntry
import com.innovation313.roshankhata.data.OpenWith
import com.innovation313.roshankhata.data.Party
import com.innovation313.roshankhata.data.Product
import com.innovation313.roshankhata.data.Role
import com.innovation313.roshankhata.data.ScreenId
import com.innovation313.roshankhata.data.Staff
import com.innovation313.roshankhata.data.VoiceQuestion
import com.innovation313.roshankhata.screens.ReportPeriod
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.desktopWords
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Locale

/**
 * Hudhud on the computer (16 Nov 2026, audit T23): typed questions over a small made-up book, answered by the shared answers
 * (AnswerBook) through the computer's own door (HudhudDeskEnv) — the figure the phone's rule gives, the customer's page as the
 * button, the computer's page for a help card, a cashier told only who may see a figure, and the phone-only cards (voice,
 * look, acts) told in writing. Names are invented.
 */
class HudhudPanelTest {

    private lateinit var dir: File
    private lateinit var keptLocale: Locale

    @Before
    fun open() {
        keptLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        Access.resetForTest()
        dir = Files.createTempDirectory("rk-hudhud-desk").toFile()
    }

    @After
    fun close() {
        Access.resetForTest()
        Locale.setDefault(keptLocale)
        dir.deleteRecursively()
    }

    private fun env(usage: UsageCounts? = null): HudhudDeskEnv = HudhudDeskEnv(
        desktopWords(Locale.US), Locale.US, FileShopSettings(File(dir, "shop.properties")),
        AppPrefs(File(dir, "app.properties")), 1L, ReminderStore(File(dir, "reminders_b1.properties")), usage
    )

    @Test
    fun `typed questions are answered from the book by the shared rule`() = runBlocking {
        val db = DesktopDatabase.open(dir.resolve("khata.db"))
        try {
            val dao = db.khataDao()
            val ali = dao.insertParty(Party(name = "Ali Raza"))
            val bashir = dao.insertParty(Party(name = "Bashir Ahmad"))
            dao.insertEntryNumbered(LedgerEntry(partyId = ali, amount = 500.0, isGiven = true, entryNumber = ""))
            dao.insertEntryNumbered(LedgerEntry(partyId = bashir, amount = 200.0, isGiven = true, entryNumber = ""))
            val env = env()
            val parties = dao.partiesWithBalanceOnce()
            val names = parties.map { Hudhud.Name(it.id, it.name) }
            // The help pages in every language they are matched in: built without a failure on the computer's words.
            assertTrue(AnswerBook.topics(env).isNotEmpty())
            fun read(s: String) = Hudhud.understand(s, names, emptyList(), "en")

            // What is to get, typed: the whole of both customers' balances, opened on Khata.
            val toGetAsk = read("kitna lena hai")
            assertEquals(Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GET), toGetAsk)
            val toGet = AnswerBook.answer(env, dao, toGetAsk, parties)
            assertEquals(AnswerBook.money(700.0), toGet.figure)
            assertEquals(Section.KHATA, toGet.open?.let { HudhudDeskEnv.sectionOf(it) })

            // A customer by name: his balance, and the button is his page.
            val his = AnswerBook.answer(env, dao, read("Ali Raza ka baqaya"), parties)
            assertEquals("Ali Raza", his.label)
            assertEquals(AnswerBook.money(500.0), his.figure)
            assertEquals(ali, his.openParty)

            // A help card opens the computer's own page for it (the phone's Backup screen is Daily copies here).
            val help = AnswerBook.answer(env, dao, Hudhud.Ask.Help("faq_backup"), parties)
            assertEquals(ScreenId.BACKUP, help.open)
            assertEquals(Section.DAILY, HudhudDeskEnv.sectionOf(ScreenId.BACKUP))
            assertTrue(help.lines.isNotEmpty())

            // A cashier at the counter: no figure, only who may see it — the phone's gate, the same words.
            Access.switchCounterMode(true)
            Access.signIn(Staff(id = 7, name = "Tariq Butt", monthlySalary = 1.0, role = Role.CASHIER))
            val hidden = AnswerBook.answer(env, dao, Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GET), parties)
            assertNull(hidden.figure)
            assertEquals(listOf(env.words.text(W.hudhud_owner_only)), hidden.lines)
        } finally {
            db.close()
        }
    }

    @Test
    fun `a card for the phone's voice, look or acts is told in writing`() = runBlocking {
        val db = DesktopDatabase.open(dir.resolve("khata.db"))
        try {
            val dao = db.khataDao()
            val env = env()
            for (ask in listOf(Hudhud.Ask.Voice(Hudhud.VoiceChange.SLOWER), Hudhud.Ask.Act(Hudhud.Move.FLY))) {
                val card = HudhudDeskEnv.forDesk(AnswerBook.answer(env, dao, ask, emptyList()), env.words)
                assertEquals(listOf(env.words.text(W.hudhud_intro_pc)), card.lines)
                assertNull(card.voice)
                assertNull(card.act)
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun `every page an answer names is a page of the computer's or none`() {
        // sectionOf is a when with no else; this holds what the rail gates for each (the screen's own gate, as on the phone).
        for (id in ScreenId.values()) HudhudDeskEnv.sectionOf(id)
        assertEquals(Access.Permission.STAFF, env().screenNeeds(ScreenId.STAFF))
        assertEquals(Access.Permission.REPORTS, env().screenNeeds(ScreenId.INSIGHTS))
        assertNull(env().screenNeeds(ScreenId.CASHBOOK))
        assertNull(env().screenNeeds(ScreenId.MERA_KHATA))
    }

    @Test
    fun `a card's button opens the computer's page on what the card said`() = runBlocking {
        // 17 Nov 2026: the panel opened every page plain — "aaj ki sale" the report on its default, "Urea kis ne li" the
        // whole product list, "aaj kitna aaya" the cashbook without the day's report. Each start is the pane's own.
        val db = DesktopDatabase.open(dir.resolve("khata.db"))
        try {
            val dao = db.khataDao()
            val env = env()
            val ali = dao.insertParty(Party(name = "Ali Raza"))
            val urea = dao.insertProduct(Product(name = "Urea", nameKey = "urea", normalisedName = "urea", defaultUnit = "bag"))
            dao.insertEntryWithItems(
                LedgerEntry(partyId = ali, amount = 4000.0, isGiven = true, entryNumber = ""),
                listOf(EntryItem(entryId = 0, itemName = "Urea", quantity = 1.0, unit = "bag", rate = 4000.0, productId = urea))
            )
            val parties = dao.partiesWithBalanceOnce()

            // Today's sales: the report on its own Today.
            val sales = AnswerBook.answer(env, dao, Hudhud.Ask.Period(Hudhud.Measure.SALES, VoiceQuestion.Window.TODAY), parties)
            val salesStart = HudhudDeskEnv.startOf(sales.openWith)
            assertEquals(DeskStart.SalesPeriod(ReportPeriod.TODAY), salesStart)
            assertEquals(sales.open?.let { HudhudDeskEnv.sectionOf(it) }, HudhudDeskEnv.sectionFor(salesStart!!))

            // Money that came in today: the cashbook's day report, on today.
            val inToday = AnswerBook.answer(env, dao, Hudhud.Ask.Collected(VoiceQuestion.Window.TODAY), parties)
            val dayStart = HudhudDeskEnv.startOf(inToday.openWith)
            assertEquals(DeskStart.DayReport(DayCloseMath.dayOf(Hudhud.span(VoiceQuestion.Window.TODAY).first)), dayStart)
            assertEquals(inToday.open?.let { HudhudDeskEnv.sectionOf(it) }, HudhudDeskEnv.sectionFor(dayStart!!))

            // Who bought Urea: Products' own Who bought window, on Urea.
            val buyers = AnswerBook.answer(env, dao, Hudhud.Ask.Goods(listOf(urea), Hudhud.GoodsFacet.BUYERS), parties)
            val buyersStart = HudhudDeskEnv.startOf(buyers.openWith)
            assertEquals(DeskStart.Buyers(urea), buyersStart)
            assertEquals(buyers.open?.let { HudhudDeskEnv.sectionOf(it) }, HudhudDeskEnv.sectionFor(buyersStart!!))
        } finally {
            db.close()
        }
    }

    @Test
    fun `every start the shared answers can name is mapped, and the entry form stays the customer's page`() {
        assertEquals(DeskStart.SalesPeriod(ReportPeriod.WEEK), HudhudDeskEnv.startOf(OpenWith.SalesPeriod(ReportPeriod.WEEK.name)))
        assertEquals(DeskStart.SalesPeriod(ReportPeriod.MONTH), HudhudDeskEnv.startOf(OpenWith.SalesPeriod(ReportPeriod.MONTH.name)))
        // A period this build does not know opens the report as usual, never a guess.
        assertNull(HudhudDeskEnv.startOf(OpenWith.SalesPeriod("FORTNIGHT")))
        assertEquals(DeskStart.SalesDays(1_000L, 2_000L), HudhudDeskEnv.startOf(OpenWith.SalesDays(1_000L, 2_000L)))
        val noon = Hudhud.span(VoiceQuestion.Window.TODAY).first + 12 * 60 * 60 * 1000L
        assertEquals(DeskStart.DayReport(DayCloseMath.dayOf(noon)), HudhudDeskEnv.startOf(OpenWith.Day(noon)))
        assertEquals(DeskStart.Buyers(42L), HudhudDeskEnv.startOf(OpenWith.ProductBuyers(42L, "Urea")))
        assertNull(HudhudDeskEnv.startOf(OpenWith.EntryForm(500.0, true)))
        assertNull(HudhudDeskEnv.startOf(null))
        // Each start belongs to the page its ScreenId is on the computer, so Main hands it to that page only.
        assertEquals(HudhudDeskEnv.sectionOf(ScreenId.SALES_REPORT), HudhudDeskEnv.sectionFor(DeskStart.SalesDays(0L, 0L)))
        assertEquals(HudhudDeskEnv.sectionOf(ScreenId.DAY_BOOK), HudhudDeskEnv.sectionFor(DeskStart.DayReport(0L)))
        assertEquals(HudhudDeskEnv.sectionOf(ScreenId.PRODUCT_CUSTOMERS), HudhudDeskEnv.sectionFor(DeskStart.Buyers(0L)))
    }

    @Test
    fun `the computer says once that the book stays on this computer`() {
        val env = env()
        val computer = desktopWords(Locale.US).text(W.hudhud_privacy_once_pc)
        val first = HudhudDeskEnv.opening(env)
        assertEquals(listOf(env.words.text(W.hudhud_intro_pc), computer), first.lines)
        assertFalse(first.lines.any { it.contains("phone", ignoreCase = true) })
        // Said once: the next opening (a new panel, the same computer) has only the introduction.
        assertEquals(listOf(env.words.text(W.hudhud_intro_pc)), HudhudDeskEnv.opening(env()).lines)
    }

    @Test
    fun `the shared answers speak of this computer, not this phone`() = runBlocking {
        val db = DesktopDatabase.open(dir.resolve("khata.db"))
        try {
            val dao = db.khataDao()
            val env = env()
            val words = desktopWords(Locale.US)
            val privacy = AnswerBook.answer(env, dao, Hudhud.Ask.About(Hudhud.AboutTopic.PRIVACY), emptyList())
            assertEquals(listOf(words.text(W.hudhud_about_privacy_pc)), privacy.lines)
            val hello = AnswerBook.answer(env, dao, Hudhud.Ask.Hello, emptyList())
            assertEquals(listOf(words.text(W.hudhud_hello_pc)), hello.lines)
            for (card in listOf(privacy, hello)) assertFalse(card.lines.joinToString(" ").contains("phone", ignoreCase = true))
        } finally {
            db.close()
        }
    }

    @Test
    fun `the computer's own counts of openings and sums are what Hudhud says`() {
        val noCounts = env()
        assertEquals(0, noCounts.usageCount(Hudhud.Thing.CALCULATIONS, 0L, Long.MAX_VALUE))
        assertNull(noCounts.usageSince())
        val counts = UsageCounts(File(dir, "usage.properties"))
        val now = System.currentTimeMillis()
        counts.add(UsageCounts.What.CALCULATIONS, now)
        counts.add(UsageCounts.What.CALCULATIONS, now)
        counts.add(UsageCounts.What.APP_OPENS, now)
        val env = env(counts)
        val (from, to) = Hudhud.span(VoiceQuestion.Window.TODAY)
        assertEquals(2, env.usageCount(Hudhud.Thing.CALCULATIONS, from, to))
        assertEquals(1, env.usageCount(Hudhud.Thing.APP_OPENS, from, to))
        assertEquals(2, env.usageCount(Hudhud.Thing.CALCULATIONS, 0L, Long.MAX_VALUE))
        assertEquals(0, env.usageCount(Hudhud.Thing.CUSTOMERS, from, to))
        assertEquals(counts.since(), env.usageSince())
    }

    /**
     * Audit L027 (17 Nov 2026): Hudhud reads the bell's list the window already worked out, sync rows included, rather than
     * reading the whole book again; until the window has one he works it out himself.
     */
    @Test
    fun `Hudhud reads the window's bell when it has one`() = runBlocking {
        val db = DesktopDatabase.open(dir.resolve("khata.db"))
        try {
            val dao = db.khataDao()
            val shown = listOf(com.innovation313.roshankhata.data.LedgerAlerts.Alert(
                com.innovation313.roshankhata.data.LedgerAlerts.Kind.SYNC, "Two changes disagree", ""))
            val withBell = HudhudDeskEnv(
                desktopWords(Locale.US), Locale.US, FileShopSettings(File(dir, "shop.properties")),
                AppPrefs(File(dir, "app.properties")), 1L, ReminderStore(File(dir, "reminders_b1.properties")), null,
                bell = { shown }
            )
            val rows = withBell.alerts(dao)
            assertEquals(listOf("Two changes disagree"), rows.map { it.title })
            assertEquals(com.innovation313.roshankhata.data.AnswerAlert.Kind.SYNC, rows.single().kind)
            // No list yet: worked out from the book itself, which has no sync row (only the window adds those).
            assertTrue(env().alerts(dao).none { it.kind == com.innovation313.roshankhata.data.AnswerAlert.Kind.SYNC })
        } finally {
            db.close()
        }
    }
}
