package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.Access
import com.innovation313.roshankhata.data.AnswerBook
import com.innovation313.roshankhata.data.AnswerCard
import com.innovation313.roshankhata.data.DesktopDatabase
import com.innovation313.roshankhata.data.FileShopSettings
import com.innovation313.roshankhata.data.Hudhud
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.LedgerEntry
import com.innovation313.roshankhata.data.LiveSync
import com.innovation313.roshankhata.data.Party
import com.innovation313.roshankhata.data.Product
import com.innovation313.roshankhata.data.ProductName
import com.innovation313.roshankhata.data.Role
import com.innovation313.roshankhata.data.ShopCountry
import com.innovation313.roshankhata.data.Staff
import com.innovation313.roshankhata.data.SyncSchema
import com.innovation313.roshankhata.data.VoiceQuestion
import com.innovation313.roshankhata.data.syncSql
import com.innovation313.roshankhata.words.desktopWords
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Locale

/**
 * The computer's Hudhud panel as each role (plan B7, 24 Nov 2026, private data): a cashier signed in under counter mode,
 * a locked counter, a manager and the owner ask every kind of question through [HudhudDeskEnv] and the shared answers.
 * The marker of each class of private data must reach only the roles whose gate holds it: a cashier and a locked counter
 * find none (not the cost price, a salary, the tax number, the bank account, the email or the country), a manager finds the
 * cost price but none of the owner's three (staff, settings), the owner finds every one (the control: a leak test that
 * cannot see the data proves nothing). Asking changes nothing in the book, and a private number is on its card and not in
 * what would be said aloud. The shared matrix over the phone's and the helper's copy is HudhudRolesMatrixTest (:shared).
 *
 * Invented names and numbers only (lesson 80). NOT COMPILED OR RUN where it was written (no Gradle here): CI's
 * `:desktop:test` is its first run. The questions are listed here again, not read from :shared's table, because :desktop's
 * tests do not see :shared's test folder.
 */
class HudhudRolesDeskTest {

    private lateinit var dir: File
    private lateinit var keptLocale: Locale

    private companion object {
        const val COST = "7731"
        const val SALARY = "48617"
        const val TAX = "RLTAX-5520"
        const val IBAN = "RLBANK-0419"
        const val EMAIL = "roles.shop.5520@example.invalid"
        const val COUNTRY = "Fiji"
        const val DAY = 24 * 60 * 60_000L
    }

    @Before
    fun open() {
        keptLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        Access.resetForTest()
        dir = Files.createTempDirectory("rk-hudhud-roles").toFile()
    }

    @After
    fun close() {
        Access.resetForTest()
        Locale.setDefault(keptLocale)
        dir.deleteRecursively()
    }

    private fun env(): HudhudDeskEnv {
        val settings = FileShopSettings(File(dir, "shop.properties"))
        settings.setBusinessName("Roles Test Traders")
        settings.setBusinessEmail(EMAIL)
        settings.setStrn(TAX)
        settings.setBankIban(IBAN)
        settings.setCountry(ShopCountry.FIJI)
        return HudhudDeskEnv(
            desktopWords(Locale.US), Locale.US, settings, AppPrefs(File(dir, "app.properties")), 1L,
            ReminderStore(File(dir, "reminders_b1.properties"))
        )
    }

    private class Ids(val ali: Long, val bashir: Long, val urea: Long, val cashier: Long, val manager: Long)

    private suspend fun seed(dao: KhataDao): Ids {
        val now = System.currentTimeMillis()
        val ali = dao.insertParty(Party(name = "Ali Raza", phone = "0300 1234567", createdAt = now - 90 * DAY))
        val bashir = dao.insertParty(Party(name = "Bashir Ahmad", phone = "0300 7654321", createdAt = now - 90 * DAY, note = "RLNOTE-3306 pays late"))
        val urea = dao.insertProduct(
            Product(name = "Urea", nameKey = ProductName.key("Urea"), normalisedName = ProductName.normalised("Urea"),
                defaultUnit = "bag", salePrice = 8_000.0, costPrice = COST.toDouble(), createdAt = now - 90 * DAY)
        )
        dao.insertEntryWithItems(
            LedgerEntry(partyId = ali, amount = 8_000.0, isGiven = true, entryNumber = "", timestamp = now - 30 * 60_000L), emptyList()
        )
        val cashier = dao.insertStaff(Staff(name = "Hamid Khan", monthlySalary = SALARY.toDouble(), role = Role.CASHIER, createdAt = now - 90 * DAY))
        val manager = dao.insertStaff(Staff(name = "Imran Shah", monthlySalary = 20_000.0, role = Role.MANAGER, createdAt = now - 90 * DAY))
        return Ids(ali, bashir, urea, cashier, manager)
    }

    private enum class Who { OWNER, MANAGER, CASHIER, LOCKED }

    private fun become(who: Who, ids: Ids) {
        Access.resetForTest()
        when (who) {
            Who.OWNER -> Unit
            Who.LOCKED -> Access.switchCounterMode(true)
            Who.MANAGER -> { Access.switchCounterMode(true); Access.signIn(Staff(id = ids.manager, name = "Imran Shah", monthlySalary = 1.0, role = Role.MANAGER)) }
            Who.CASHIER -> { Access.switchCounterMode(true); Access.signIn(Staff(id = ids.cashier, name = "Hamid Khan", monthlySalary = 1.0, role = Role.CASHIER)) }
        }
    }

    private fun asks(ids: Ids): List<Hudhud.Ask> {
        val a = ArrayList<Hudhud.Ask>()
        val ali = listOf(ids.ali)
        val today = VoiceQuestion.Window.TODAY
        val month = VoiceQuestion.Window.THIS_MONTH
        VoiceQuestion.Kind.values().forEach { a += Hudhud.Ask.Figure(it) }
        Hudhud.Measure.values().forEach { m -> listOf(today, month).forEach { w -> a += Hudhud.Ask.Period(m, w) } }
        Hudhud.Facet.values().forEach { a += Hudhud.Ask.Person(ali, it); a += Hudhud.Ask.NeedName(it) }
        a += Hudhud.Ask.Person(listOf(ids.bashir), Hudhud.Facet.NOTE)
        a += Hudhud.Ask.NeedName(null)
        Hudhud.GoodsFacet.values().forEach { a += Hudhud.Ask.Goods(listOf(ids.urea), it); a += Hudhud.Ask.NeedGoods(it) }
        Hudhud.ShopFacet.values().forEach { a += Hudhud.Ask.Shop(it) }
        Hudhud.Thing.values().forEach { a += Hudhud.Ask.Count(it) }
        Hudhud.StatusOf.values().forEach { a += Hudhud.Ask.Status(it) }
        Hudhud.RecordFacet.values().forEach {
            a += Hudhud.Ask.Record(it)
            a += Hudhud.Ask.Record(it, listOf(ids.cashier), VoiceQuestion.Window.THIS_MONTH)
        }
        Hudhud.PaperOf.values().forEach { a += Hudhud.Ask.Paper(it) }
        Hudhud.CounterFacet.values().forEach { a += Hudhud.Ask.Counter(it, today) }
        listOf(null, today, month).forEach {
            a += Hudhud.Ask.Deliveries(it); a += Hudhud.Ask.Activity(it); a += Hudhud.Ask.Collected(it)
            a += Hudhud.Ask.Cashbook(it); a += Hudhud.Ask.Returns(it); a += Hudhud.Ask.TopGoods(it)
        }
        Hudhud.WhyOf.values().forEach { a += Hudhud.Ask.Why(it, today, true) }
        a += Hudhud.Ask.Trend(VoiceQuestion.Window.THIS_MONTH)
        Hudhud.AdviseOf.values().forEach { a += Hudhud.Ask.Advise(it) }
        a += Hudhud.Ask.Today; a += Hudhud.Ask.Drawer; a += Hudhud.Ask.Qarz; a += Hudhud.Ask.Automations
        a += Hudhud.Ask.BackupStatus; a += Hudhud.Ask.Abilities
        return a
    }

    private fun textOf(card: AnswerCard): String = buildList {
        card.label?.let { add(it) }
        card.figure?.let { add(it) }
        addAll(card.lines)
        card.rows.forEach { r -> add(r.name); add(r.figure); r.sub?.let { add(it) } }
    }.joinToString("\n")

    private fun flat(s: String) = s.replace(",", "").replace(" ", "").replace(" ", "")

    private suspend fun askAll(dao: KhataDao, env: HudhudDeskEnv, list: List<Hudhud.Ask>): List<Pair<Hudhud.Ask, AnswerCard>> =
        list.map { it to AnswerBook.answer(env, dao, it, dao.partiesWithBalanceOnce()) }

    @Test
    fun `each role finds only the private data its gate holds and the owner finds all of it`() = runBlocking {
        val db = DesktopDatabase.open(dir.resolve("khata.db"))
        try {
            val dao = db.khataDao()
            val ids = seed(dao)
            val list = asks(ids)
            val env = env()
            // marker -> the roles that may find it
            val held = mapOf(
                COST to setOf(Who.OWNER, Who.MANAGER),
                SALARY to setOf(Who.OWNER),
                TAX to setOf(Who.OWNER), IBAN to setOf(Who.OWNER), EMAIL to setOf(Who.OWNER), COUNTRY to setOf(Who.OWNER)
            )
            val leaks = ArrayList<String>()
            for (who in Who.values()) {
                become(who, ids)
                val answered = askAll(dao, env, list)
                for ((marker, roles) in held) {
                    val found = answered.filter { flat(textOf(it.second)).contains(flat(marker)) }
                    if (who == Who.OWNER) assertTrue("control: the owner never found $marker; the seed or the card changed", found.isNotEmpty())
                    else if (who !in roles) found.forEach { leaks += "$who is told $marker by ${it.first}" }
                }
            }
            assertEquals("the computer's panel shows more than the role's gate allows", emptyList<String>(), leaks)
        } finally {
            db.close()
        }
    }

    @Test
    fun `asking every kind of question writes nothing and answers the same twice`() = runBlocking {
        val db = DesktopDatabase.open(dir.resolve("khata.db"))
        try {
            val dao = db.khataDao()
            val ids = seed(dao)
            val list = asks(ids)
            val env = env()
            fun counts() = runBlocking {
                (SyncSchema.tables.map { it.name } + listOf("audit_log", "book_seals")).associateWith { t ->
                    db.syncSql().read { rows("SELECT COUNT(*) FROM `$t`", types = "I").first()[0] as Long }
                }
            }
            for (who in Who.values()) {
                become(who, ids)
                val fingerprint = LiveSync.fingerprint(db.syncSql())
                val before = counts()
                val first = askAll(dao, env, list).map { textOf(it.second) }
                val second = askAll(dao, env, list).map { textOf(it.second) }
                assertEquals("$who: asking changed the book's fingerprint", fingerprint, LiveSync.fingerprint(db.syncSql()))
                assertEquals("$who: asking changed a table's row count", before, counts())
                assertEquals("$who: the same questions answered differently the second time", first, second)
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun `a private number is on the card and not in what is said aloud`() = runBlocking {
        val db = DesktopDatabase.open(dir.resolve("khata.db"))
        try {
            val dao = db.khataDao()
            val ids = seed(dao)
            become(Who.OWNER, ids)
            val env = env()
            val phone = AnswerBook.answer(env, dao, Hudhud.Ask.Person(listOf(ids.ali), Hudhud.Facet.PHONE), dao.partiesWithBalanceOnce())
            assertTrue("the number is on the card", flat(textOf(phone)).contains("03001234567"))
            assertTrue("the card lists the number as a secret", phone.secrets.any { flat(it).contains("03001234567") })
            val said = AnswerBook.speech(env, phone, hideMoney = false)?.text.orEmpty()
            assertTrue("the number is said aloud: $said", !flat(said).contains("03001234567"))
        } finally {
            db.close()
        }
    }
}
