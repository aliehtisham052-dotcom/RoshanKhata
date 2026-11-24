package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.HudhudRolesTable.Gate
import com.innovation313.roshankhata.data.HudhudRolesTable.Held
import com.innovation313.roshankhata.words.W
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.StringWriter
import java.nio.file.Files
import java.util.Locale
import java.util.TimeZone

/**
 * The roles matrix over a real book (plan B7, 24 Nov 2026, private data, importance 8): every kind of question
 * ([Hudhud.Ask], every facet) asked as every role, and what each answer may tell held to [HudhudRolesTable].
 *
 * Roles: the owner (counter mode off), a manager, a cashier (counter mode on, signed in), a locked counter (counter mode on,
 * nobody in) and a helper's read-only copy (a `secrets = false` export restored into a second book, an env whose `viewer()`
 * is true). The computer's own panel is HudhudRolesDeskTest (in :desktop).
 *
 * What it holds:
 *  - the markers: eight invented values, each a class of private data (a product's cost price, a salary, the tax number, the
 *    bank account, the email, the country, a customer's private note, a day's sales total). A role that may not hold the
 *    marker's gate must not find it in any card's text, in any ask; the owner must find every one somewhere (the control: a
 *    leak test that cannot see the data proves nothing); the helper's copy must not find the cost price or the note;
 *  - every gated kind refuses (no figure, no rows) to a role without its gate;
 *  - a read-only copy refuses the forms;
 *  - asking everything writes nothing (the book's fingerprint and every table's row count), and the same question asked
 *    twice answers the same;
 *  - a private number is on the card and never in what is said aloud.
 *
 * Invented names and numbers only (lesson 80). NOT TESTED where this was written: no Gradle, no Room here; CI's
 * `:shared:desktopTest` is its first run. A control or a leak that fails there names the role, the ask and the marker:
 * read the answer code before blaming the test.
 */
class HudhudRolesMatrixTest {

    private lateinit var dir: File
    private lateinit var keptZone: TimeZone
    private lateinit var keptLocale: Locale
    private lateinit var keptSign: String
    private val books = ArrayList<KhataDatabase>()

    private companion object {
        const val MINUTE = 60_000L
        const val DAY = 24 * 60 * MINUTE
    }

    @Before
    fun open() {
        keptZone = TimeZone.getDefault()
        keptLocale = Locale.getDefault()
        keptSign = Currency.symbol
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.US)
        Currency.symbol = Currency.DEFAULT
        Access.resetForTest()
        dir = Files.createTempDirectory("rk-roles").toFile()
    }

    @After
    fun close() {
        Access.resetForTest()
        books.forEach { it.close() }
        dir.deleteRecursively()
        TimeZone.setDefault(keptZone)
        Locale.setDefault(keptLocale)
        Currency.symbol = keptSign
    }

    // ------------------------------------------------------------------------------------------------ the books

    private class Ids(val ali: Long, val bashir: Long, val tariq: Long, val urea: Long, val cashier: Long, val manager: Long)

    private fun book(name: String): KhataDatabase = DesktopDatabase.open(dir.resolve("$name.db")).also { books += it }

    private fun marker(name: String) = HudhudRolesTable.MARKERS.first { it.name == name }.text

    /** The owner's book: invented people, goods, a staff pay, a walk-in ticket, every marker placed where its card reads it. */
    private suspend fun seed(dao: KhataDao, now: Long): Ids {
        fun ago(days: Int) = now - days * DAY
        fun today(minutes: Int) = now - minutes * MINUTE
        val ali = dao.insertParty(Party(name = "Ali Raza", phone = "0300 1234567", creditLimit = 50_000.0, createdAt = ago(90)))
        val bashir = dao.insertParty(
            Party(name = "Bashir Ahmad", phone = "0300 7654321", createdAt = ago(90), note = marker("private note") + " pays after the harvest")
        )
        val tariq = dao.insertParty(Party(name = "Tariq Butt", phone = "0300 0000001", isCustomer = false, createdAt = ago(90)))
        val urea = dao.insertProduct(
            Product(
                name = "Urea", nameKey = ProductName.key("Urea"), normalisedName = ProductName.normalised("Urea"),
                defaultUnit = "bag", salePrice = 8_000.0, costPrice = marker("cost price").toDouble(), lowStockLevel = 5.0, createdAt = ago(90)
            )
        )
        dao.insertSupplierBill(
            LedgerEntry(partyId = tariq, amount = 70_000.0, isGiven = false, note = "B-101", entryNumber = "", timestamp = ago(30)),
            SupplierBill(partyId = tariq, billNumber = "B-101", totalAmount = 70_000.0, billDate = ago(30), dueDate = ago(5), createdAt = ago(30)),
            listOf(BillItem(billId = 0, productName = "Urea", batchNumber = "U-01", quantity = 10.0, unit = "bag", rate = 7_000.0, productId = urea))
        )
        dao.insertEntryWithItems(
            LedgerEntry(partyId = ali, amount = 8_000.0, isGiven = true, entryNumber = "", timestamp = today(30), rateType = RateType.CREDIT),
            listOf(EntryItem(entryId = 0, itemName = "Urea", quantity = 1.0, unit = "bag", rate = 8_000.0, productId = urea))
        )
        dao.insertEntryWithItems(
            LedgerEntry(partyId = ali, amount = 1_000.0, isGiven = false, entryNumber = "", timestamp = today(20), paymentMethod = PaymentMethod.CASH),
            emptyList()
        )
        // The walk-in ticket nobody signed in rang up: the day's sales total, the shop's and no cashier's.
        val total = marker("sales total").toDouble()
        dao.postSale(
            Sale(customerName = null, timestamp = today(10), subtotal = 0.0, grandTotal = 0.0, paidTotal = 0.0, createdBy = null),
            listOf(SaleItem(saleId = 0, itemName = "Item", quantity = 1.0, unit = "piece", rate = total)),
            listOf(SalePayment(saleId = 0, method = PaymentMethod.CASH, amount = total))
        )
        val cashier = dao.insertStaff(Staff(name = "Hamid Khan", monthlySalary = marker("salary").toDouble(), role = Role.CASHIER, createdAt = ago(90)))
        val manager = dao.insertStaff(Staff(name = "Imran Shah", monthlySalary = 20_000.0, role = Role.MANAGER, createdAt = ago(90)))
        return Ids(ali, bashir, tariq, urea, cashier, manager)
    }

    /** The helper's copy: the owner's book exported with `secrets = false`, restored into a book of its own. */
    private suspend fun helperCopyOf(dao: KhataDao): KhataDatabase {
        val text = StringWriter().also { BackupCodec.exportTo(dao, "Roles Test Traders", JSONObject(), it, secrets = false) }.toString()
        val copy = BackupCodec.parseText(text).second!!
        val db = book("helper")
        BackupCodec.restoreLedger(db.khataDao(), copy)
        return db
    }

    private fun become(role: HudhudRolesTable.Role, ids: Ids) {
        Access.resetForTest()
        when (role) {
            HudhudRolesTable.Role.OWNER, HudhudRolesTable.Role.HELPER -> Unit
            HudhudRolesTable.Role.LOCKED -> Access.switchCounterMode(true)
            HudhudRolesTable.Role.MANAGER -> {
                Access.switchCounterMode(true)
                Access.signIn(Staff(id = ids.manager, name = "Imran Shah", monthlySalary = 1.0, role = Role.MANAGER))
            }
            HudhudRolesTable.Role.CASHIER, HudhudRolesTable.Role.DESK_CASHIER -> {
                Access.switchCounterMode(true)
                Access.signIn(Staff(id = ids.cashier, name = "Hamid Khan", monthlySalary = 1.0, role = Role.CASHIER))
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ the questions

    private fun asks(ids: Ids): List<Hudhud.Ask> {
        val a = ArrayList<Hudhud.Ask>()
        val ali = listOf(ids.ali)
        val today = VoiceQuestion.Window.TODAY
        val month = VoiceQuestion.Window.THIS_MONTH
        VoiceQuestion.Kind.values().forEach { a += Hudhud.Ask.Figure(it) }
        Hudhud.Measure.values().forEach { m -> listOf(today, month).forEach { w -> a += Hudhud.Ask.Period(m, w) } }
        a += Hudhud.Ask.Moved(ali, today, true)
        a += Hudhud.Ask.Moved(ali, VoiceQuestion.Window.ALL_TIME, false)
        a += Hudhud.Ask.Parties(ali)
        a += Hudhud.Ask.Parties(listOf(ids.ali, ids.bashir))
        a += Hudhud.Ask.Entry(500.0, true, ali, listOf("ali"))
        a += Hudhud.Ask.Entries(listOf(Hudhud.Ask.Entry(500.0, true, ali, listOf("ali")), Hudhud.Ask.Entry(200.0, false, ali, listOf("ali"))))
        a += Hudhud.Ask.WhenPays(ali)
        a += Hudhud.Ask.WhenPays(emptyList())
        Hudhud.Facet.values().forEach { a += Hudhud.Ask.Person(ali, it) }
        a += Hudhud.Ask.Person(listOf(ids.bashir), Hudhud.Facet.NOTE)
        a += Hudhud.Ask.Person(listOf(ids.bashir), Hudhud.Facet.DETAILS)
        a += Hudhud.Ask.NeedName(null)
        Hudhud.Facet.values().forEach { a += Hudhud.Ask.NeedName(it) }
        Hudhud.GoodsFacet.values().forEach { a += Hudhud.Ask.Goods(listOf(ids.urea), it) }
        Hudhud.GoodsFacet.values().forEach { a += Hudhud.Ask.NeedGoods(it) }
        listOf(null, today, month).forEach { a += Hudhud.Ask.TopGoods(it) }
        Hudhud.ShopFacet.values().forEach { a += Hudhud.Ask.Shop(it) }
        Hudhud.AboutTopic.values().forEach { a += Hudhud.Ask.About(it) }
        a += Hudhud.Ask.Clock
        Hudhud.Thing.values().forEach { t -> listOf(null, today).forEach { w -> a += Hudhud.Ask.Count(t, w) } }
        Hudhud.StatusOf.values().forEach { a += Hudhud.Ask.Status(it) }
        Hudhud.ChequeState.values().forEach { a += Hudhud.Ask.Status(Hudhud.StatusOf.CHEQUES, it) }
        for (f in Hudhud.RecordFacet.values()) {
            val who = when (f) {
                Hudhud.RecordFacet.STAFF_PAY, Hudhud.RecordFacet.STAFF_PAID, Hudhud.RecordFacet.ATTENDANCE,
                Hudhud.RecordFacet.STAFF_PERSON -> listOf(ids.cashier)
                Hudhud.RecordFacet.PLAN, Hudhud.RecordFacet.PLAN_CLOSED, Hudhud.RecordFacet.CHEQUE -> ali
            }
            a += Hudhud.Ask.Record(f)
            a += Hudhud.Ask.Record(f, who, month)
        }
        Hudhud.PaperOf.values().forEach { a += Hudhud.Ask.Paper(it) }
        a += Hudhud.Ask.Paper(Hudhud.PaperOf.BILL, listOf(ids.tariq))
        listOf(null, today, month).forEach { w ->
            a += Hudhud.Ask.Deliveries(w); a += Hudhud.Ask.Activity(w); a += Hudhud.Ask.Collected(w)
            a += Hudhud.Ask.Cashbook(w); a += Hudhud.Ask.Returns(w)
        }
        Hudhud.CounterFacet.values().forEach { f -> listOf(null, today).forEach { w -> a += Hudhud.Ask.Counter(f, w) } }
        a += Hudhud.Ask.Today
        a += Hudhud.Ask.Drawer
        a += Hudhud.Ask.BackupStatus
        a += Hudhud.Ask.Qarz
        a += Hudhud.Ask.Automations
        Hudhud.DatedOf.values().forEach { of ->
            val who = if (of == Hudhud.DatedOf.PAID || of == Hudhud.DatedOf.TOOK) ali else emptyList()
            a += Hudhud.Ask.OnDays(of, VoiceQuestion.Dated(daysBack = 0), who)
        }
        a += Hudhud.Ask.Help("intro_cashbook")
        a += Hudhud.Ask.Help("no_such_page")
        a += Hudhud.Ask.Salaam
        a += Hudhud.Ask.Hello
        a += Hudhud.Ask.Abilities
        a += Hudhud.Ask.Unknown
        Hudhud.Talk.values().forEach { a += Hudhud.Ask.Chat(it) }
        a += Hudhud.Ask.OffTopic(Hudhud.OffTopicWhy.GENERAL)
        a += Hudhud.Ask.OffTopic(Hudhud.OffTopicWhy.SUM, "2+2", 4.0)
        Hudhud.Move.values().forEach { a += Hudhud.Ask.Act(it) }
        Hudhud.VoiceChange.values().forEach { a += Hudhud.Ask.Voice(it) }
        a += Hudhud.Ask.CallMe("Ali bhai")
        a += Hudhud.Ask.Look(HudhudLook.Request.Show(HudhudLook.Setting.SIZE))
        a += Hudhud.Ask.LookDone(HudhudLook.Made(HudhudLook.Setting.SIZE, HudhudLook.SIZE_NORMAL, HudhudLook.SIZE_LARGE))
        a += Hudhud.Ask.Guessed(Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GET))
        a += Hudhud.Ask.DidYouMean(listOf(Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GET), Hudhud.Ask.Status(Hudhud.StatusOf.BILLS)))
        a += Hudhud.Ask.SpanLimit(listOf(Hudhud.Ask.Activity(today), Hudhud.Ask.Activity(VoiceQuestion.Window.THIS_WEEK)), long = false)
        a += Hudhud.Ask.Now(Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GET))
        a += Hudhud.Ask.Both(Hudhud.Ask.Figure(VoiceQuestion.Kind.SALES_TODAY), Hudhud.Ask.Drawer)
        a += Hudhud.Ask.Compare(Hudhud.Ask.Period(Hudhud.Measure.SALES, today), Hudhud.Ask.Period(Hudhud.Measure.SALES, VoiceQuestion.Window.YESTERDAY))
        Hudhud.WhyOf.values().forEach { a += Hudhud.Ask.Why(it, today, down = true) }
        a += Hudhud.Ask.Took(ali, listOf(ids.urea))
        a += Hudhud.Ask.NoHistory
        return a
    }

    /** Every subtype of [Hudhud.Ask] is asked at least once: a new subtype fails here until it is added to the list. */
    private fun everySubtypeIsAsked(list: List<Hudhud.Ask>) {
        val base = Hudhud.Ask::class.java
        val subtypes = base.declaredClasses.filter { base.isAssignableFrom(it) && it != base }.map { it.simpleName }.toSortedSet()
        val asked = list.map { it.javaClass.simpleName }.toSortedSet()
        assertEquals("Ask subtypes the roles matrix does not ask", emptySet<String>(), subtypes - asked)
    }

    // ------------------------------------------------------------------------------------------------ what a card says

    private fun textOf(card: AnswerCard): String = buildList {
        card.label?.let { add(it) }
        card.figure?.let { add(it) }
        addAll(card.lines)
        card.rows.forEach { r -> add(r.name); add(r.figure); r.sub?.let { add(it) } }
        addAll(card.optionLabels.values)
        card.tookAs?.let { add(it) }
        card.verseNote?.let { add(it) }
    }.joinToString("\n")

    /** The card's words with the punctuation a figure carries taken out, so "Rs 7,731" is found by its digits "7731". */
    private fun flat(text: String): String = text.replace(",", "").replace(" ", "").replace(" ", "")

    private class Answered(val ask: Hudhud.Ask, val card: AnswerCard, val text: String, val said: String?)

    private suspend fun askAll(db: KhataDatabase, env: AnswerEnv, list: List<Hudhud.Ask>): List<Answered> {
        val dao = db.khataDao()
        return list.map { ask ->
            val parties = dao.partiesWithBalanceOnce()
            val card = AnswerBook.answer(env, dao, ask, parties)
            val said = AnswerBook.speech(env, card, hideMoney = false)?.text
            Answered(ask, card, textOf(card), said)
        }
    }

    private fun describe(a: Hudhud.Ask) = a.toString().take(90)

    /** The expected visibility of a marker for [role] over a book (the helper's copy has no cost price and no note). */
    private fun mayFind(role: HudhudRolesTable.Role, m: HudhudRolesTable.Marker): Boolean {
        if (role == HudhudRolesTable.Role.HELPER && (m.name == "cost price" || m.name == "private note")) return false
        return HudhudRolesTable.may(role, m.gate)
    }

    private fun clockIsSafe(now: Long): Boolean {
        val intoDay = now - DayMath.startOfDay(now)
        return intoDay >= 30 * MINUTE && intoDay < DAY - 30 * MINUTE
    }

    private val ROLES = listOf(
        HudhudRolesTable.Role.OWNER, HudhudRolesTable.Role.MANAGER, HudhudRolesTable.Role.CASHIER,
        HudhudRolesTable.Role.LOCKED, HudhudRolesTable.Role.HELPER
    )

    // ------------------------------------------------------------------------------------------------ the tests

    @Test
    fun noRoleIsToldAMarkerItsGateWithholdsAndTheOwnerFindsEveryOne() = runBlocking {
        val now = System.currentTimeMillis()
        assumeTrue("too close to midnight for 'a few minutes ago' to be today", clockIsSafe(now))
        val owner = book("owner")
        val ids = seed(owner.khataDao(), now)
        val helper = helperCopyOf(owner.khataDao())
        val list = asks(ids)
        everySubtypeIsAsked(list)

        val leaks = ArrayList<String>()
        for (role in ROLES) {
            become(role, ids)
            val db = if (role == HudhudRolesTable.Role.HELPER) helper else owner
            val env = HudhudRolesEnv(now, viewer = role == HudhudRolesTable.Role.HELPER)
            val answered = askAll(db, env, list)
            for (m in HudhudRolesTable.MARKERS) {
                val needle = flat(m.text)
                val found = answered.filter { flat(it.text).contains(needle) || it.said?.let { s -> flat(s).contains(needle) } == true }
                if (mayFind(role, m)) {
                    // The control: a role that may see the data must see it somewhere, or the detector sees nothing.
                    // The helper's copy is controlled only for what the env's profile holds: a book row (a pay, a ticket) it
                    // may or may not keep is the copy's own business and has its own tests (SecurityReviewTest).
                    val profileBased = m.name in setOf("tax number", "bank account", "email", "country")
                    if (role == HudhudRolesTable.Role.OWNER || (role == HudhudRolesTable.Role.HELPER && profileBased)) {
                        assertTrue("control: $role never found '${m.name}' (${m.note}); the seed or the card changed, this is not a leak", found.isNotEmpty())
                    }
                } else {
                    found.forEach { leaks += "$role is told '${m.name}' by ${describe(it.ask)}" }
                }
            }
        }
        assertEquals("answers that show more than the role's gate allows", emptyList<String>(), leaks)
    }

    @Test
    fun everyGatedKindRefusesARoleWithoutItsGate() = runBlocking {
        val now = System.currentTimeMillis()
        assumeTrue("too close to midnight", clockIsSafe(now))
        val owner = book("owner")
        val ids = seed(owner.khataDao(), now)
        val gated = ArrayList<Pair<Hudhud.Ask, Gate>>()
        for ((kind, row) in HudhudRolesTable.ASK) if (row.held == Held.GATED) {
            asks(ids).filter { it.javaClass.simpleName == kind }.forEach { gated += it to row.gate }
        }
        // The facet tables: a facet whose gate is not OPEN, asked once with a name where one is needed.
        fun add(ask: Hudhud.Ask, gate: Gate) { if (gate != Gate.OPEN) gated += ask to gate }
        for (f in Hudhud.GoodsFacet.values()) add(Hudhud.Ask.Goods(listOf(ids.urea), f), HudhudRolesTable.GOODS.getValue("GoodsFacet.${f.name}"))
        for (f in Hudhud.ShopFacet.values()) add(Hudhud.Ask.Shop(f), HudhudRolesTable.SHOP.getValue("ShopFacet.${f.name}"))
        for (s in Hudhud.StatusOf.values()) add(Hudhud.Ask.Status(s), HudhudRolesTable.STATUS.getValue("StatusOf.${s.name}"))
        add(Hudhud.Ask.Count(Hudhud.Thing.STAFF), HudhudRolesTable.COUNT.getValue("Thing.STAFF"))
        for (f in Hudhud.RecordFacet.values()) add(Hudhud.Ask.Record(f), HudhudRolesTable.RECORD.getValue("RecordFacet.${f.name}"))
        for (f in Hudhud.CounterFacet.values()) add(Hudhud.Ask.Counter(f), HudhudRolesTable.COUNTER.getValue("CounterFacet.${f.name}"))
        for (p in Hudhud.PaperOf.values()) add(Hudhud.Ask.Paper(p), HudhudRolesTable.PAPER.getValue("PaperOf.${p.name}"))
        assertTrue("too few gated asks were built: ${gated.size}", gated.size > 40)

        val told = ArrayList<String>()
        for (role in listOf(HudhudRolesTable.Role.MANAGER, HudhudRolesTable.Role.CASHIER, HudhudRolesTable.Role.LOCKED)) {
            become(role, ids)
            val env = HudhudRolesEnv(now, viewer = false)
            val dao = owner.khataDao()
            for ((ask, gate) in gated) {
                if (HudhudRolesTable.may(role, gate)) continue
                // A cashier asking today's sale hears his own tickets (the owner's T5): a figure, but his, and 0 here.
                if (role == HudhudRolesTable.Role.CASHIER && ask == Hudhud.Ask.Figure(VoiceQuestion.Kind.SALES_TODAY)) continue
                val card = AnswerBook.answer(env, dao, ask, dao.partiesWithBalanceOnce())
                if (card.figure != null || card.rows.isNotEmpty() || card.money) told += "$role / $gate / ${describe(ask)}"
            }
        }
        assertEquals("a gated kind told a figure or a list to a role without its gate", emptyList<String>(), told)
    }

    @Test
    fun aReadOnlyCopyRefusesTheForms() = runBlocking {
        val now = System.currentTimeMillis()
        assumeTrue("too close to midnight", clockIsSafe(now))
        val owner = book("owner")
        val ids = seed(owner.khataDao(), now)
        val helper = helperCopyOf(owner.khataDao())
        become(HudhudRolesTable.Role.HELPER, ids)
        val env = HudhudRolesEnv(now, viewer = true)
        val refused = env.words.text(W.viewer_refused)
        val dao = helper.khataDao()
        val parties = dao.partiesWithBalanceOnce()
        val ali = parties.first { it.name == "Ali Raza" }.id
        val one = Hudhud.Ask.Entry(500.0, true, listOf(ali), listOf("ali"))
        for (ask in listOf(one, Hudhud.Ask.Entries(listOf(one, one)))) {
            val card = AnswerBook.answer(env, dao, ask, parties)
            assertTrue("${describe(ask)} must say the copy cannot write", refused in textOf(card))
            assertTrue("${describe(ask)} must not open the form", card.openWith == null)
        }
        // The private note: a read-only copy holds none, and says so instead of "none saved".
        val bashir = parties.first { it.name == "Bashir Ahmad" }.id
        val note = AnswerBook.answer(env, dao, Hudhud.Ask.Person(listOf(bashir), Hudhud.Facet.NOTE), parties)
        assertFalse("the helper's copy holds no private note", flat(textOf(note)).contains(flat(marker("private note"))))
        val list = AnswerBook.answer(env, dao, Hudhud.Ask.NeedName(Hudhud.Facet.NOTE), parties)
        assertTrue("the helper is told it holds no notes", env.words.text(W.hudhud_note_helper) in textOf(list))
    }

    @Test
    fun askingEverythingWritesNothingAndTheSameQuestionAnswersTheSame() = runBlocking {
        val now = System.currentTimeMillis()
        assumeTrue("too close to midnight", clockIsSafe(now))
        val owner = book("owner")
        val ids = seed(owner.khataDao(), now)
        val helper = helperCopyOf(owner.khataDao())
        val list = asks(ids)
        for (role in ROLES) {
            become(role, ids)
            val db = if (role == HudhudRolesTable.Role.HELPER) helper else owner
            val env = HudhudRolesEnv(now, viewer = role == HudhudRolesTable.Role.HELPER)
            val before = snapshot(db)
            val first = askAll(db, env, list)
            val second = askAll(db, env, list)
            assertEquals("$role: asking changed the book's fingerprint", before.first, snapshot(db).first)
            assertEquals("$role: asking changed a table's row count", before.second, snapshot(db).second)
            assertEquals("$role: the same questions answered differently the second time",
                first.map { it.text }, second.map { it.text })
        }
    }

    private suspend fun snapshot(db: KhataDatabase): Pair<String, Map<String, Long>> {
        val tables = (SyncSchema.tables.map { it.name } + listOf("audit_log", "book_seals")).associateWith { t ->
            db.syncSql().read { rows("SELECT COUNT(*) FROM `$t`", types = "I").first()[0] as Long }
        }
        return LiveSync.fingerprint(db.syncSql()) to tables
    }

    @Test
    fun aPrivateNumberIsOnTheCardAndNeverInWhatIsSaidAloud() = runBlocking {
        val now = System.currentTimeMillis()
        assumeTrue("too close to midnight", clockIsSafe(now))
        val owner = book("owner")
        val ids = seed(owner.khataDao(), now)
        become(HudhudRolesTable.Role.OWNER, ids)
        val env = HudhudRolesEnv(now, viewer = false)
        val answered = askAll(owner, env, asks(ids))
        val withSecrets = answered.filter { it.card.secrets.isNotEmpty() }
        assertTrue("no card carried a secret: the seed or the cards changed", withSecrets.isNotEmpty())
        val spoken = ArrayList<String>()
        for (a in answered) {
            val said = a.said?.let { flat(it) } ?: continue
            for (s in a.card.secrets) if (s.isNotBlank() && said.contains(flat(s))) spoken += "${describe(a.ask)} says '$s' aloud"
            // The card's own words, said as they are, may hold no marker either.
            a.card.spoken?.let { own ->
                val text = flat(own(env))
                for (m in HudhudRolesTable.MARKERS) if (text.contains(flat(m.text))) spoken += "${describe(a.ask)}: its own spoken words hold '${m.name}'"
            }
        }
        // The phone number of the customer is the card's secret and is on the card.
        val phone = answered.first { it.ask == Hudhud.Ask.Person(listOf(ids.ali), Hudhud.Facet.PHONE) }
        assertTrue("the phone card lists the number as a secret", phone.card.secrets.any { flat(it).contains("03001234567") })
        assertTrue("the phone is on the card", flat(phone.text).contains("03001234567"))
        assertEquals("a private number was said aloud", emptyList<String>(), spoken)
    }

    @Test
    fun theTableAndTheCodeAgreeOnWhoHoldsWhat() {
        // The roles the matrix uses are the gates Access gives: the owner everything, a manager everything but the owner's three.
        Access.resetForTest()
        assertTrue(Access.can(Access.Permission.SETTINGS))
        Access.switchCounterMode(true)
        assertFalse("a locked counter holds nothing", Access.can(Access.Permission.REPORTS))
        Access.signIn(Staff(id = 1, name = "Imran Shah", monthlySalary = 1.0, role = Role.MANAGER))
        assertTrue(Access.can(Access.Permission.REPORTS))
        assertFalse(Access.can(Access.Permission.STAFF))
        assertFalse(Access.can(Access.Permission.SETTINGS))
        Access.signIn(Staff(id = 2, name = "Hamid Khan", monthlySalary = 1.0, role = Role.CASHIER))
        assertFalse(Access.can(Access.Permission.REPORTS))
        for (role in ROLES) for (g in Gate.values()) {
            val held = when (role) {
                HudhudRolesTable.Role.OWNER, HudhudRolesTable.Role.HELPER -> true
                HudhudRolesTable.Role.MANAGER -> g == Gate.OPEN || g == Gate.REPORTS
                else -> g == Gate.OPEN
            }
            assertEquals("$role / $g", held, HudhudRolesTable.may(role, g))
        }
    }
}
