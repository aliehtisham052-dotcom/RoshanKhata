package com.innovation313.roshankhata.data

import android.content.Context
import android.os.Bundle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import java.util.TimeZone

/**
 * Golden answers (16 Nov 2026, audit T21): what every card Hudhud's answer layer makes ([HudhudBook.answer], with
 * HudhudFacts, HudhudCounterFacts and HudhudRecordFacts behind it) says today, for one made-up book
 * ([HudhudGoldenBook]), frozen in plain text. The layer is to move to :shared so the computer can have the assistant
 * too (T22); this test is what proves the move changed nothing. A wording or figure change that is meant is a golden
 * file changed in the same commit, where the diff shows it.
 *
 * The asks are built as [Hudhud.Ask] objects, never from sentences: the sentence readers change often and are held by
 * their own tests; this test holds the answers. Every subtype of [Hudhud.Ask] is asked (the test fails for a new one
 * nobody added here), every value of each enum a subtype carries, and the spans of days whose answer does not move with
 * the calendar (this year and last year are left out: whether 75 days ago is in this year depends on the date). The
 * whole list is asked twice — as the owner and as a signed-in cashier — and once more after a name is saved for the
 * owner (the greeting and the salaam change with it).
 *
 * What moves with the clock is masked, not frozen: dates, times and weekdays become <date>, <time>, <weekday>, <month>;
 * the clock's own answer keeps only its shape, and the app's history and version keep their words with every figure as
 * "#". Bidi marks are written as <U+2068> and the like. Everything else — every figure, line, row, button and flag — is
 * compared exactly.
 *
 * Files: app/src/test/resources/hudhud-golden/<owner|cashier|named>/<Ask subtype>.txt, one block per ask. Every run
 * also writes what it got to app/build/hudhud-golden/ (CI uploads that folder as the "hudhud-golden" artifact).
 *
 * TO UPDATE THE GOLDENS (after a change that is meant to change an answer):
 *   HUDHUD_GOLDEN_UPDATE=1 gradle :app:testDebugUnitTest --tests '*HudhudGoldenAnswersTest*'
 * (or -Dgolden.update=true where the test JVM receives system properties). That writes the files straight into
 * app/src/test/resources/hudhud-golden/ and passes; read the diff in git before committing it. Without Gradle: take the
 * "hudhud-golden" artifact of the failing CI run, read it, and copy its folders over app/src/test/resources/hudhud-golden/.
 *
 * Runs under Robolectric (the real SQLite, as LedgerBalanceTest and HudhudCashierGateTest), in UTC, en-US, with the
 * book's currency sign. It skips itself (Assume) within 30 minutes of midnight, where "a few minutes ago" is yesterday.
 *
 * The answers' own clock (17 Nov 2026, audit L035): every card is asked through an env whose [AnswerEnv.now] is held at the
 * moment the book was seeded, so the answer layer cannot cross midnight in the middle of a run. The skip stays, because not
 * every reading behind a card takes that clock yet: the bell's rows (HomeAlerts / LedgerAlerts.compute), who was reminded
 * today (ReminderLog), the follow-up habits inside Automations.collect (PaymentHabit.forAll's default) and the spoken lambdas
 * (HudhudBook.card builds them over a fresh env) still read the system clock. When they all take a `now`, the skip can go.
 */
@RunWith(AndroidJUnit4::class)
class HudhudGoldenAnswersTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: KhataDatabase
    private lateinit var dao: KhataDao
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
    }

    @After
    fun close() {
        Access.resetForTest()
        db.close()
        TimeZone.setDefault(keptZone)
        Locale.setDefault(keptLocale)
        Currency.symbol = keptSign
    }

    @Test
    fun everyAnswerIsAsFrozen() {
        runBlocking {
            val now = System.currentTimeMillis()
            assumeTrue("too close to midnight for 'a few minutes ago' to be today", HudhudGoldenBook.clockIsSafe(now))
            val ids = HudhudGoldenBook.seed(dao, now)
            val asks = HudhudGoldenAsks.all(ids)
            checkEverySubtypeIsAsked(asks)

            val files = LinkedHashMap<String, StringBuilder>()
            suspend fun pass(role: String, list: List<Hudhud.Ask>) {
                for (ask in list) {
                    val parties = dao.partiesWithBalanceOnce()
                    val text = runCatching { HudhudBook.card(AnswerBook.answer(HudhudPhoneEnv(context) { now }, dao, ask, parties)) }
                        .fold({ HudhudGoldenText.block(context, ask, it) }, { HudhudGoldenText.threw(ask, it) })
                    files.getOrPut(role + "/" + ask.javaClass.simpleName + ".txt") { StringBuilder() }.append(text).append('\n')
                }
            }

            // The owner's own phone (counter mode off).
            pass("owner", asks)
            // A counter with the cashier signed in: every money figure is told only to the owner or a manager.
            Access.switchCounterMode(true)
            Access.signIn(Staff(id = ids.staff, name = "Hamid Khan", monthlySalary = 30_000.0, role = Role.CASHIER))
            pass("cashier", asks)
            Access.resetForTest()
            // A name kept for the owner (the card's Save): the greeting, the salaam and the calling say it.
            pass("named", HudhudGoldenAsks.afterNaming())

            HudhudGolden.compare(files.mapValues { it.value.toString() })
        }
    }

    /** Every subtype of [Hudhud.Ask] is asked at least once: a new subtype fails here until it is added to the list. */
    private fun checkEverySubtypeIsAsked(asks: List<Hudhud.Ask>) {
        val base = Hudhud.Ask::class.java
        val subtypes = base.declaredClasses.filter { base.isAssignableFrom(it) && it != base }.map { it.simpleName }.toSortedSet()
        val asked = asks.map { it.javaClass.simpleName }.toSortedSet()
        assertEquals("Ask subtypes the golden list does not ask", emptySet<String>(), subtypes - asked)
    }
}

/** The asks, built straight from [Hudhud.Ask] (never from sentences), every enum value each one carries. */
internal object HudhudGoldenAsks {

    /** The spans whose answers do not move with the calendar for [HudhudGoldenBook] (not this year, not last year). */
    val WINDOWS = listOf(
        VoiceQuestion.Window.TODAY, VoiceQuestion.Window.YESTERDAY, VoiceQuestion.Window.THIS_WEEK, VoiceQuestion.Window.LAST_WEEK,
        VoiceQuestion.Window.THIS_MONTH, VoiceQuestion.Window.LAST_MONTH, VoiceQuestion.Window.LAST_6_MONTHS, VoiceQuestion.Window.ALL_TIME
    )

    private val SOME = listOf(VoiceQuestion.Window.TODAY, VoiceQuestion.Window.THIS_MONTH, VoiceQuestion.Window.LAST_6_MONTHS)

    fun all(ids: HudhudGoldenBook.Ids): List<Hudhud.Ask> {
        val a = ArrayList<Hudhud.Ask>()
        val ali = listOf(ids.ali)
        val customers = listOf(ids.ali, ids.bashir)
        val nobodyWindows: List<VoiceQuestion.Window?> = listOf<VoiceQuestion.Window?>(null) + WINDOWS

        // The figures, a span's figure, one customer's money over a span.
        VoiceQuestion.Kind.values().forEach { a += Hudhud.Ask.Figure(it) }
        Hudhud.Measure.values().forEach { m -> WINDOWS.forEach { w -> a += Hudhud.Ask.Period(m, w) } }
        for (got in listOf(true, false)) for (w in listOf(VoiceQuestion.Window.TODAY, VoiceQuestion.Window.THIS_MONTH, VoiceQuestion.Window.ALL_TIME)) {
            a += Hudhud.Ask.Moved(ali, w, got)
        }
        // Bashir's opening balance is owed from before the book: what he took over the whole book leaves it out (17 Nov 2026).
        a += Hudhud.Ask.Moved(listOf(ids.bashir), VoiceQuestion.Window.ALL_TIME, false)
        a += Hudhud.Ask.Moved(customers, VoiceQuestion.Window.TODAY, true)
        a += Hudhud.Ask.Moved(emptyList(), VoiceQuestion.Window.TODAY, true)

        // People.
        a += Hudhud.Ask.Parties(ali)
        a += Hudhud.Ask.Parties(customers)
        a += Hudhud.Ask.Parties(listOf(ids.tariq))
        a += Hudhud.Ask.Parties(listOf(9_999L))
        a += Hudhud.Ask.Entry(500.0, true, ali, listOf("ali"))
        a += Hudhud.Ask.Entry(500.0, false, emptyList(), listOf("raza"))
        a += Hudhud.Ask.Entry(500.0, true, customers, listOf("a"))
        a += Hudhud.Ask.WhenPays(ali)
        a += Hudhud.Ask.WhenPays(emptyList())
        a += Hudhud.Ask.WhenPays(listOf(ids.tariq))
        a += Hudhud.Ask.WhenPays(customers)
        Hudhud.Facet.values().forEach { a += Hudhud.Ask.Person(ali, it) }
        a += Hudhud.Ask.Person(listOf(ids.bashir), Hudhud.Facet.NOTE)
        a += Hudhud.Ask.Person(listOf(ids.bashir), Hudhud.Facet.DETAILS)
        a += Hudhud.Ask.Person(listOf(ids.tariq), Hudhud.Facet.LAST_PAYMENT)
        a += Hudhud.Ask.Person(customers, Hudhud.Facet.DETAILS)
        a += Hudhud.Ask.Person(customers, Hudhud.Facet.NOTE)
        a += Hudhud.Ask.Person(emptyList(), Hudhud.Facet.PHONE)
        a += Hudhud.Ask.NeedName(null)
        Hudhud.Facet.values().forEach { a += Hudhud.Ask.NeedName(it) }

        // Goods.
        Hudhud.GoodsFacet.values().forEach { a += Hudhud.Ask.Goods(listOf(ids.urea), it) }
        for (f in listOf(Hudhud.GoodsFacet.SOLD, Hudhud.GoodsFacet.BUYERS, Hudhud.GoodsFacet.COVER)) for (w in SOME) {
            a += Hudhud.Ask.Goods(listOf(ids.urea), f, w)
        }
        Hudhud.GoodsFacet.values().forEach { a += Hudhud.Ask.Goods(listOf(ids.paracetamol), it) }
        for (f in listOf(Hudhud.GoodsFacet.OVERVIEW, Hudhud.GoodsFacet.PRICE, Hudhud.GoodsFacet.COST, Hudhud.GoodsFacet.SUPPLIERS)) {
            a += Hudhud.Ask.Goods(listOf(ids.dap), f)
        }
        a += Hudhud.Ask.Goods(listOf(ids.urea, ids.dap), Hudhud.GoodsFacet.PRICE)
        a += Hudhud.Ask.Goods(emptyList(), Hudhud.GoodsFacet.PRICE)
        Hudhud.GoodsFacet.values().forEach { a += Hudhud.Ask.NeedGoods(it) }
        a += Hudhud.Ask.NeedGoods(Hudhud.GoodsFacet.PRICE, "2100000000017")
        a += Hudhud.Ask.NeedGoods(Hudhud.GoodsFacet.PRICE, "0000000000000")
        nobodyWindows.forEach { a += Hudhud.Ask.TopGoods(it) }

        // The shop, the app, the clock, counts, where things stand, records.
        Hudhud.ShopFacet.values().forEach { a += Hudhud.Ask.Shop(it) }
        Hudhud.AboutTopic.values().forEach { a += Hudhud.Ask.About(it) }
        a += Hudhud.Ask.Clock
        for (t in Hudhud.Thing.values()) for (w in listOf(null, VoiceQuestion.Window.TODAY, VoiceQuestion.Window.THIS_MONTH, VoiceQuestion.Window.LAST_6_MONTHS)) {
            a += Hudhud.Ask.Count(t, w)
        }
        Hudhud.StatusOf.values().forEach { a += Hudhud.Ask.Status(it) }
        Hudhud.ChequeState.values().forEach { a += Hudhud.Ask.Status(Hudhud.StatusOf.CHEQUES, it) }
        for (f in Hudhud.RecordFacet.values()) {
            val who = when (f) {
                Hudhud.RecordFacet.STAFF_PAY, Hudhud.RecordFacet.STAFF_PAID, Hudhud.RecordFacet.ATTENDANCE,
                Hudhud.RecordFacet.STAFF_PERSON -> listOf(ids.staff)
                Hudhud.RecordFacet.PLAN, Hudhud.RecordFacet.PLAN_CLOSED, Hudhud.RecordFacet.CHEQUE -> ali
            }
            a += Hudhud.Ask.Record(f)
            a += Hudhud.Ask.Record(f, who)
            a += Hudhud.Ask.Record(f, who, VoiceQuestion.Window.THIS_MONTH)
        }
        Hudhud.PaperOf.values().forEach { a += Hudhud.Ask.Paper(it) }
        a += Hudhud.Ask.Paper(Hudhud.PaperOf.BILL, listOf(ids.tariq))
        a += Hudhud.Ask.Paper(Hudhud.PaperOf.ORDER, listOf(ids.tariq))
        a += Hudhud.Ask.Paper(Hudhud.PaperOf.INVOICE, ali)
        a += Hudhud.Ask.Paper(Hudhud.PaperOf.SCHEMES, listOf(ids.tariq))

        // The day's books.
        nobodyWindows.forEach { w ->
            a += Hudhud.Ask.Deliveries(w)
            a += Hudhud.Ask.Activity(w)
            a += Hudhud.Ask.Collected(w)
            a += Hudhud.Ask.Cashbook(w)
            a += Hudhud.Ask.Returns(w)
        }
        for (f in Hudhud.CounterFacet.values()) for (w in listOf(null, VoiceQuestion.Window.TODAY, VoiceQuestion.Window.THIS_MONTH)) {
            a += Hudhud.Ask.Counter(f, w)
        }
        a += Hudhud.Ask.Counter(Hudhud.CounterFacet.LOYALTY, null, ali)
        a += Hudhud.Ask.Today
        a += Hudhud.Ask.Drawer
        a += Hudhud.Ask.BackupStatus
        a += Hudhud.Ask.Qarz
        a += Hudhud.Ask.Automations
        Hudhud.DatedOf.values().forEach { of ->
            val who = if (of == Hudhud.DatedOf.PAID || of == Hudhud.DatedOf.TOOK) ali else emptyList()
            a += Hudhud.Ask.OnDays(of, VoiceQuestion.Dated(daysBack = 0), who)
            a += Hudhud.Ask.OnDays(of, VoiceQuestion.Dated(daysBack = 2), who)
        }

        // Help pages: every one, and a key no page has.
        HudhudBook.PAGES.forEach { a += Hudhud.Ask.Help(it.key) }
        a += Hudhud.Ask.Help("no_such_page")

        // Talk, acts, the voice and the look.
        a += Hudhud.Ask.Salaam
        a += Hudhud.Ask.Hello
        a += Hudhud.Ask.Abilities
        a += Hudhud.Ask.Unknown
        Hudhud.Talk.values().forEach { a += Hudhud.Ask.Chat(it) }
        a += Hudhud.Ask.Chat(Hudhud.Talk.HOW_ARE_YOU, salaam = true)
        a += Hudhud.Ask.OffTopic(Hudhud.OffTopicWhy.GENERAL)
        a += Hudhud.Ask.OffTopic(Hudhud.OffTopicWhy.SUM, "2+2", 4.0)
        a += Hudhud.Ask.OffTopic(Hudhud.OffTopicWhy.SUM, "500 aur 300 ka total", null)
        Hudhud.Move.values().forEach { a += Hudhud.Ask.Act(it) }
        Hudhud.VoiceChange.values().forEach { a += Hudhud.Ask.Voice(it) }
        a += Hudhud.Ask.CallMe("Ali bhai")
        a += Hudhud.Ask.Look(HudhudLook.Request.Show(HudhudLook.Setting.SIZE))
        a += Hudhud.Ask.Look(HudhudLook.Request.Show(HudhudLook.Setting.THEME))
        a += Hudhud.Ask.Look(HudhudLook.Request.SizeTo(HudhudLook.SIZE_LARGE))
        a += Hudhud.Ask.Look(HudhudLook.Request.SizeTo(HudhudLook.SIZE_NORMAL))
        a += Hudhud.Ask.Look(HudhudLook.Request.SizeStep(1))
        a += Hudhud.Ask.Look(HudhudLook.Request.SizeStep(-1))
        a += Hudhud.Ask.Look(HudhudLook.Request.ThemeTo(HudhudLook.THEME_DARK))
        a += Hudhud.Ask.LookDone(HudhudLook.Made(HudhudLook.Setting.SIZE, HudhudLook.SIZE_NORMAL, HudhudLook.SIZE_LARGE))
        a += Hudhud.Ask.LookDone(HudhudLook.Made(HudhudLook.Setting.SIZE, HudhudLook.SIZE_LARGE, HudhudLook.SIZE_NORMAL, undone = true))
        a += Hudhud.Ask.LookDone(HudhudLook.Made(HudhudLook.Setting.THEME, HudhudLook.THEME_SYSTEM, HudhudLook.THEME_DARK))

        // The asks that wrap others.
        a += Hudhud.Ask.Guessed(Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GET))
        a += Hudhud.Ask.Guessed(Hudhud.Ask.Count(Hudhud.Thing.CUSTOMERS))
        a += Hudhud.Ask.DidYouMean(listOf(Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GET), Hudhud.Ask.Status(Hudhud.StatusOf.BILLS)))
        a += Hudhud.Ask.DidYouMean(emptyList())
        // D15 (Marhala 1, 25 Nov 2026): the side nobody said is asked first, both sides as chips; a cashier is told it is the owner's.
        a += Hudhud.Ask.DidYouMean(
            listOf(Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GET), Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GIVE)), unsure = Hudhud.Unsure.SIDE
        )
        a += Hudhud.Ask.SpanLimit(listOf(Hudhud.Ask.Activity(VoiceQuestion.Window.TODAY), Hudhud.Ask.Activity(VoiceQuestion.Window.THIS_WEEK)), long = false)
        a += Hudhud.Ask.SpanLimit(
            listOf(Hudhud.Ask.Period(Hudhud.Measure.SALES, VoiceQuestion.Window.THIS_MONTH), Hudhud.Ask.Period(Hudhud.Measure.SALES, VoiceQuestion.Window.ALL_TIME)),
            long = true
        )
        a += Hudhud.Ask.Now(Hudhud.Ask.Count(Hudhud.Thing.CUSTOMERS))
        a += Hudhud.Ask.Now(Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GET))
        a += Hudhud.Ask.Both(Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GET), Hudhud.Ask.Count(Hudhud.Thing.CUSTOMERS))
        a += Hudhud.Ask.Both(Hudhud.Ask.Figure(VoiceQuestion.Kind.SALES_TODAY), Hudhud.Ask.Drawer)
        a += Hudhud.Ask.Compare(Hudhud.Ask.Period(Hudhud.Measure.SALES, VoiceQuestion.Window.TODAY), Hudhud.Ask.Period(Hudhud.Measure.SALES, VoiceQuestion.Window.YESTERDAY))
        a += Hudhud.Ask.Compare(Hudhud.Ask.Period(Hudhud.Measure.PROFIT, VoiceQuestion.Window.THIS_MONTH), Hudhud.Ask.Period(Hudhud.Measure.PROFIT, VoiceQuestion.Window.LAST_MONTH))
        a += Hudhud.Ask.Compare(
            Hudhud.Ask.OnDays(Hudhud.DatedOf.SALES, VoiceQuestion.Dated(daysBack = 0)),
            Hudhud.Ask.OnDays(Hudhud.DatedOf.SALES, VoiceQuestion.Dated(daysBack = 2))
        )
        a += Hudhud.Ask.Compare(Hudhud.Ask.Drawer, Hudhud.Ask.Drawer)
        // Why (16 Nov 2026, audit T8): every figure a why may ask, today and this month, said as a fall and as a plain why.
        for (of in Hudhud.WhyOf.values()) {
            a += Hudhud.Ask.Why(of, VoiceQuestion.Window.TODAY, down = true)
            a += Hudhud.Ask.Why(of, VoiceQuestion.Window.THIS_MONTH)
        }
        for (w in WINDOWS) a += Hudhud.Ask.Trend(w)
        Hudhud.AdviseOf.values().forEach { a += Hudhud.Ask.Advise(it) }
        // Two entries in one sentence: offered one at a time, nothing written.
        a += Hudhud.Ask.Entries(
            listOf(
                Hudhud.Ask.Entry(500.0, true, ali, listOf("ali")),
                Hudhud.Ask.Entry(300.0, false, listOf(ids.bashir), listOf("bashir"))
            )
        )
        // A customer with a product (17 Nov 2026, audit L004): the last time he took it, over all time and over a span;
        // two products named offer a choice. Talk about past chats says he keeps none (L019).
        a += Hudhud.Ask.Took(listOf(ids.bashir), listOf(ids.urea))
        a += Hudhud.Ask.Took(listOf(ids.bashir), listOf(ids.urea), VoiceQuestion.Window.THIS_MONTH)
        a += Hudhud.Ask.Took(listOf(ids.bashir), listOf(ids.urea, ids.dap))
        a += Hudhud.Ask.NoHistory
        // Business knowledge (Marhala 9, 26 Nov 2026): every atom, the card of an atom asked as checked (it still says what the
        // atom's own review says), each measure worked from 800 and 1,000, the question back, and every limit refused.
        for (id in listOf("K101", "K102", "K103", "K104", "K201", "K202", "K203", "K204", "K205", "K206")) {
            a += Hudhud.Ask.Knowledge(KnowKind.GENERAL_GUIDANCE, atom = id)
        }
        a += Hudhud.Ask.Knowledge(KnowKind.VERIFIED, atom = "K201")
        for (m in KnowMeasure.values()) {
            a += Hudhud.Ask.Knowledge(KnowKind.CALCULATED, atom = if (m == KnowMeasure.MARKUP) "K202" else "K201", cost = 800.0, sale = 1000.0, asked = m)
        }
        a += Hudhud.Ask.Knowledge(KnowKind.CALCULATED, atom = "K201", cost = 800.0, sale = 0.0, asked = KnowMeasure.MARGIN)
        a += Hudhud.Ask.Knowledge(KnowKind.NEEDS_CLARIFICATION, asked = KnowMeasure.MARGIN)
        for (l in KnowLimit.values()) a += Hudhud.Ask.Knowledge(KnowKind.UNSUPPORTED, limit = l)
        return a
    }

    /** Asked last, as the owner: the name kept by the card's Save, then what says it. */
    fun afterNaming(): List<Hudhud.Ask> = listOf(
        Hudhud.Ask.CallMe("Ali bhai", save = true),
        Hudhud.Ask.Salaam,
        Hudhud.Ask.Chat(Hudhud.Talk.ADDRESS),
        Hudhud.Ask.Chat(Hudhud.Talk.HOW_ARE_YOU, salaam = true),
        Hudhud.Ask.About(Hudhud.AboutTopic.OWNER_NAME),
        Hudhud.Ask.Today
    )
}

/** One answer as plain text: one field a line, only what is set, what moves with the clock masked. */
internal object HudhudGoldenText {

    /** Asks whose words carry today's date or a count of days since a fixed day: their figures become "#". */
    private fun countsDays(ask: Hudhud.Ask): Boolean = ask is Hudhud.Ask.About && ask.topic in setOf(
        Hudhud.AboutTopic.APP_STARTED, Hudhud.AboutTopic.APP_PUBLISHED, Hudhud.AboutTopic.HUDHUD_ADDED,
        Hudhud.AboutTopic.APP_JOURNEY, Hudhud.AboutTopic.VERSION
    )

    fun block(context: Context, ask: Hudhud.Ask, a: HudhudBook.Answer): String {
        val clock = ask == Hudhud.Ask.Clock
        val digits = countsDays(ask)
        fun clean(v: Any?): String {
            var t = mask(v.toString())
            if (digits) t = t.replace(Regex("\\d+"), "#")
            return t
        }
        val out = StringBuilder()
        out.append("=== ").append(describe(ask)).append('\n')
        out.append("mood: ").append(a.mood).append('\n')
        fun put(key: String, value: Any?) {
            if (value == null || value == false) return
            if (value is String && value.isEmpty()) return
            out.append(key).append(": ").append(clean(value)).append('\n')
        }
        if (clock) {
            // The clock's answer is today's date and time in two calendars: only its shape is frozen.
            put("label", a.label?.let { "<now>" })
            put("figure", a.figure?.let { "<now>" })
            put("lines", a.lines.size.takeIf { it > 0 }?.toString())
        } else {
            put("label", a.label)
            put("figure", a.figure)
            a.lines.forEach { put("line", it) }
        }
        for (r in a.rows) {
            val bits = listOfNotNull(
                "party=" + r.partyId, r.name, r.figure.ifEmpty { "-" }, r.sub ?: "-",
                r.amount?.let { "amount=$it" }, if (r.amount != null) (if (r.isGiven) "gave" else "got") else null,
                r.open?.let { "open=" + it.simpleName }, r.ask?.let { "ask=" + describe(it) }
            )
            put("row", bits.joinToString(" | "))
        }
        put("open", a.open?.simpleName)
        put("openParty", a.openParty)
        put("openExtras", a.openExtras?.let { extras(it) })
        put("openLabel", a.openLabel?.let { context.resources.getResourceEntryName(it) })
        put("money", a.money)
        if (!clock) {
            put("spoken", a.spoken?.let { s -> runCatching { s(context) }.getOrElse { e -> "<threw " + e.javaClass.simpleName + ">" } })
            put("speech", HudhudBook.speech(context, a, hideMoney = false)?.text)
            put("speechHidden", HudhudBook.speech(context, a, hideMoney = true)?.let { it.text + (if (it.leftOutMoney) " [an amount left out]" else "") })
        }
        put("doIt", if (a.doIt != null) "set" else null)
        a.options.forEach { o -> put("option", describe(o) + (a.optionLabels[o]?.let { " = $it" } ?: "")) }
        put("voiceSettings", a.voiceSettings)
        put("automations", a.automations)
        put("ask", a.ask?.let { describe(it) })
        put("tookAs", a.tookAs)
        put("quiet", a.quiet)
        put("act", a.act)
        a.secrets.forEach { put("secret", it) }
        put("voice", a.voice)
        a.verses.forEach { put("verse", it.surah + " " + it.ref + " " + it.arabic) }
        put("verseNote", a.verseNote)
        put("reverent", a.reverent)
        put("look", a.look)
        put("lookUndo", a.lookUndo)
        return out.toString()
    }

    /** An ask whose answer threw: the failure is frozen too (it should not happen, and a move must not change it). */
    fun threw(ask: Hudhud.Ask, e: Throwable): String =
        "=== " + describe(ask) + "\nTHREW: " + e.javaClass.simpleName + ": " + mask(e.message ?: "") + "\n"

    /** An ask as its data class says it; an `object` ask (Drawer, Today…) by its name, never by its hash. */
    fun describe(ask: Hudhud.Ask): String = mask(ask.toString())

    @Suppress("DEPRECATION")
    private fun extras(b: Bundle): String = b.keySet().sorted().joinToString(", ") { k ->
        val v = b.get(k)
        // A moment (the sales report's first and last day) is the clock's: masked.
        k + "=" + (if (v is Long && v > 100_000_000_000L) "<millis>" else v.toString())
    }

    private val OBJECT_HASH = Regex("""[A-Za-z0-9_.$]+\$([A-Za-z0-9_]+)@[0-9a-f]+""")
    private val TIME = Regex("""\b\d{1,2}:\d{2}[\s\u00A0\u202F]?[AaPp]\.?[Mm]\b\.?""")
    private val WEEKDAY = Regex("""\b(?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday|Mon|Tue|Wed|Thu|Fri|Sat|Sun)\b""")
    private val DAY_MONTH = Regex("""\b\d{1,2} (?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\.?(?:,? \d{4})?""")
    private val MONTH_YEAR = Regex("""\b(?:January|February|March|April|May|June|July|August|September|October|November|December) \d{4}""")

    /** Dates, times and weekdays masked; an object's hash dropped; invisible format marks written out. */
    fun mask(text: String): String {
        var t = OBJECT_HASH.replace(text) { it.groupValues[1] }
        t = TIME.replace(t, "<time>")
        t = WEEKDAY.replace(t, "<weekday>")
        t = DAY_MONTH.replace(t, "<date>")
        t = MONTH_YEAR.replace(t, "<month>")
        val out = StringBuilder()
        for (ch in t) {
            when {
                ch == '\n' -> out.append("\\n")
                Character.getType(ch) == Character.FORMAT.toInt() -> out.append(String.format(Locale.ROOT, "<U+%04X>", ch.code))
                else -> out.append(ch)
            }
        }
        return out.toString()
    }
}

/** The golden files: read from the source folder, written to build/ every run, written to the source folder on request. */
internal object HudhudGolden {

    const val DIR = "hudhud-golden"

    private fun updating(): Boolean =
        System.getProperty("golden.update") == "true" || System.getenv("HUDHUD_GOLDEN_UPDATE") == "1"

    /** The app module's folder: Gradle runs the tests in it; a run from the repository's root finds it under app/. */
    private fun module(): File = if (File("src/test").isDirectory) File(".") else File("app")

    private fun source(): File = File(module(), "src/test/resources/$DIR")

    private fun built(): File = File(module(), "build/$DIR")

    private fun write(root: File, files: Map<String, String>) {
        for ((path, text) in files) {
            val f = File(root, path)
            f.parentFile?.mkdirs()
            f.writeText(text)
        }
    }

    private fun expected(path: String): String? {
        val f = File(source(), path)
        if (f.isFile) return f.readText()
        return HudhudGolden::class.java.classLoader?.getResource("$DIR/$path")?.readText()
    }

    fun compare(actual: Map<String, String>) {
        built().deleteRecursively()
        write(built(), actual)
        if (updating()) {
            source().deleteRecursively()
            write(source(), actual)
            return
        }
        val missing = actual.keys.filter { expected(it) == null }
        // No goldens checked in yet (16 Nov 2026): the run writes them and is skipped, not failed, so the build still makes
        // its APK while the first goldens wait to be read and checked in (the artifact host is not reachable from every
        // session). Once any golden is in, every file is held.
        org.junit.Assume.assumeTrue(
            "No golden answers are checked in yet: ${actual.size} files were written to app/build/$DIR " +
                "(on CI, the \"hudhud-golden\" artifact of this run). Read them, then copy them to " +
                "app/src/test/resources/$DIR/ and commit; or run with HUDHUD_GOLDEN_UPDATE=1.",
            missing.size < actual.size
        )
        val problems = ArrayList<String>()
        missing.forEach { problems += "$it: new file, no golden yet" }
        for ((path, text) in actual) {
            val want = expected(path)?.replace("\r\n", "\n") ?: continue
            if (want != text) problems += path + ": " + firstDifference(want, text)
        }
        // A golden file nothing produced any more: an ask removed, or a subtype renamed.
        val here = source()
        if (here.isDirectory) {
            here.walkTopDown().filter { it.isFile }.map { it.relativeTo(here).invariantSeparatorsPath }
                .filter { it !in actual.keys }.forEach { problems += "$it: golden file no answer produced" }
        }
        if (problems.isNotEmpty()) {
            fail(
                "Hudhud's answers differ from the golden files (what this run got is in app/build/$DIR, on CI the " +
                    "\"hudhud-golden\" artifact; if the change is meant, update the goldens — see the test's comment):\n" +
                    problems.take(25).joinToString("\n") + (if (problems.size > 25) "\n… and ${problems.size - 25} more" else "")
            )
        }
    }

    /** The first line where the two differ, with the ask whose block it is in. */
    private fun firstDifference(want: String, got: String): String {
        val w = want.lines()
        val g = got.lines()
        for (i in 0 until maxOf(w.size, g.size)) {
            val a = w.getOrNull(i)
            val b = g.getOrNull(i)
            if (a != b) {
                val head = (i downTo 0).firstOrNull { (g.getOrNull(it) ?: w.getOrNull(it) ?: "").startsWith("=== ") }
                    ?.let { g.getOrNull(it) ?: w.getOrNull(it) } ?: "(top)"
                return "line ${i + 1} in [$head]\n  golden: ${a ?: "<none>"}\n  got:    ${b ?: "<none>"}"
            }
        }
        return "differs (line endings?)"
    }
}
