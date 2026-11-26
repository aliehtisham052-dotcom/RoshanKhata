package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.HudhudCoordinator.Outcome
import com.innovation313.roshankhata.words.W
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Locale
import java.util.TimeZone

/**
 * "No record" is not "Rs 0" (Marhala 6, 26 Nov 2026; BC-374..BC-377): where the book holds no rows for what was asked — a new
 * shop's sale this month, money in today, what a customer paid in days he paid nothing, what is owed in a book with nobody in
 * it — the card says so in words, in each of the nine languages, with no figure and nothing said as money; where rows exist
 * and come to nought (a sale written at no price, every account settled), "Rs 0" is the true figure and stays. The
 * Coordinator says NO_DATA for the first and ACCEPT for the second.
 *
 * Invented names only (lesson 80). NOT RUN where it was written (no Gradle, no Room here): CI's `:shared:desktopTest` is its
 * first run.
 */
class HudhudNoDataTest {

    private lateinit var dir: File
    private lateinit var keptZone: TimeZone
    private lateinit var keptLocale: Locale
    private lateinit var keptSign: String
    private val books = ArrayList<KhataDatabase>()
    private val languages = listOf("en", "ur", "ur-Latn", "sd", "ar", "fa", "hi", "bn", "id")

    @Before
    fun open() {
        keptZone = TimeZone.getDefault()
        keptLocale = Locale.getDefault()
        keptSign = Currency.symbol
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.US)
        Currency.symbol = Currency.DEFAULT
        Access.resetForTest()
        dir = Files.createTempDirectory("rk-nodata").toFile()
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

    private fun book(name: String): KhataDatabase = DesktopDatabase.open(dir.resolve("$name.db")).also { books += it }

    /** Midday, so "today" and "this month" are never a minute from their edge. */
    private val now: Long = DayMath.startOfDay(System.currentTimeMillis()) + 12 * 60 * 60_000L

    private fun noRecord(card: AnswerCard, env: HudhudRolesEnv, key: String, ask: Hudhud.Ask) {
        val where = env.locale.toLanguageTag() + " " + ask
        assertNull("$where: a figure for no record", card.figure)
        assertEquals("$where: money", false, card.money)
        assertTrue("$where: the words '" + env.getString(key) + "' in " + card.lines, card.lines.contains(env.getString(key)))
        assertEquals(where, Outcome.NO_DATA, HudhudCoordinator.outcomeOf(ask, card, { env.getString(it) }).outcome)
    }

    @Test
    fun aNewShopIsToldThereIsNoRecordInEveryLanguage() = runBlocking {
        val db = book("new")
        val dao = db.ledgerDao()
        for (tag in languages) {
            val env = HudhudRolesEnv(now, viewer = false, locale = Locale.forLanguageTag(tag))
            val parties = dao.partiesWithBalanceOnce()
            val asks = listOf(
                Hudhud.Ask.Figure(VoiceQuestion.Kind.SALES_TODAY) to W.hudhud_top_goods_none,
                Hudhud.Ask.Figure(VoiceQuestion.Kind.SALES_MONTH) to W.hudhud_top_goods_none,
                Hudhud.Ask.Period(Hudhud.Measure.SALES, VoiceQuestion.Window.THIS_MONTH) to W.hudhud_top_goods_none,
                Hudhud.Ask.Period(Hudhud.Measure.SALES, VoiceQuestion.Window.LAST_MONTH) to W.hudhud_top_goods_none,
                Hudhud.Ask.OnDays(Hudhud.DatedOf.SALES, VoiceQuestion.Dated(daysBack = 2)) to W.hudhud_top_goods_none,
                Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GET) to W.voice_no_party,
                Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GIVE) to W.voice_no_party,
                Hudhud.Ask.Collected(VoiceQuestion.Window.TODAY) to W.hudhud_collected_none,
                Hudhud.Ask.Collected(VoiceQuestion.Window.THIS_MONTH) to W.hudhud_collected_none
            )
            for ((ask, key) in asks) noRecord(AnswerBook.answer(env, dao, ask, parties), env, key, ask)
        }
    }

    @Test
    fun aCustomerWhoPaidNothingInTheDaysAskedHasNoRecordThere() = runBlocking {
        val db = book("quiet")
        val dao = db.ledgerDao()
        val ali = dao.insertParty(Party(name = "Ali Raza", phone = "0300 1234567", createdAt = now - 90 * DAY))
        // He took goods sixty days ago and paid nothing since: today's and this month's "paid" have no record.
        dao.insertEntryWithItems(LedgerEntry(partyId = ali, amount = 900.0, isGiven = true, entryNumber = "", timestamp = now - 60 * DAY), emptyList())
        for (tag in languages) {
            val env = HudhudRolesEnv(now, viewer = false, locale = Locale.forLanguageTag(tag))
            val parties = dao.partiesWithBalanceOnce()
            for (w in listOf(VoiceQuestion.Window.TODAY, VoiceQuestion.Window.THIS_MONTH)) {
                val ask = Hudhud.Ask.Moved(listOf(ali), w, got = true)
                noRecord(AnswerBook.answer(env, dao, ask, parties), env, W.hudhud_activity_none, ask)
            }
            val paidDay = Hudhud.Ask.OnDays(Hudhud.DatedOf.PAID, VoiceQuestion.Dated(daysBack = 2), listOf(ali))
            noRecord(AnswerBook.answer(env, dao, paidDay, parties), env, W.hudhud_activity_none, paidDay)
        }
    }

    /** Rows that come to nought are a record: "Rs 0" is the true figure, and the Coordinator calls it an answer. */
    @Test
    fun rowsThatComeToNoughtStillSayRsZero() = runBlocking {
        val db = book("zero")
        val dao = db.ledgerDao()
        val ali = dao.insertParty(Party(name = "Ali Raza", phone = "0300 1234567", createdAt = now - 90 * DAY))
        // A sale written at no price today (goods given free), and an account settled to nought.
        dao.insertEntryWithItems(LedgerEntry(partyId = ali, amount = 0.0, isGiven = true, entryNumber = "", timestamp = now - 60_000L), emptyList())
        dao.insertEntryWithItems(LedgerEntry(partyId = ali, amount = 500.0, isGiven = true, entryNumber = "", timestamp = now - 120_000L), emptyList())
        dao.insertEntryWithItems(
            LedgerEntry(partyId = ali, amount = 500.0, isGiven = false, entryNumber = "", timestamp = now - 90_000L, paymentMethod = PaymentMethod.CASH),
            emptyList()
        )
        val env = HudhudRolesEnv(now, viewer = false)
        val parties = dao.partiesWithBalanceOnce()
        for (ask in listOf(
            Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GET),
            Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GIVE)
        )) {
            val card = AnswerBook.answer(env, dao, ask, parties)
            assertEquals(ask.toString(), AnswerBook.money(0.0), card.figure)
            assertEquals(ask.toString(), Outcome.ACCEPT, HudhudCoordinator.outcomeOf(ask, card, { env.getString(it) }).outcome)
        }
        // Today's sale: 500 given today with goods at no price beside it; the figure is the sum of the day's rows.
        val sale = AnswerBook.answer(env, dao, Hudhud.Ask.Figure(VoiceQuestion.Kind.SALES_TODAY), parties)
        assertEquals(AnswerBook.money(dao.salesTotalBetween(DayMath.startOfDay(now), DayMath.startOfDay(now) + DAY)), sale.figure)
    }

    /** A book whose only sale today was written at nought: one record, "Rs 0", never "nothing was sold". */
    @Test
    fun aSaleWrittenAtNoughtIsARecord() = runBlocking {
        val db = book("free")
        val dao = db.ledgerDao()
        val ali = dao.insertParty(Party(name = "Ali Raza", phone = "0300 1234567", createdAt = now - 90 * DAY))
        dao.insertEntryWithItems(LedgerEntry(partyId = ali, amount = 0.0, isGiven = true, entryNumber = "", timestamp = now - 60_000L), emptyList())
        val env = HudhudRolesEnv(now, viewer = false)
        val ask = Hudhud.Ask.Figure(VoiceQuestion.Kind.SALES_TODAY)
        val card = AnswerBook.answer(env, dao, ask, dao.partiesWithBalanceOnce())
        assertEquals(AnswerBook.money(0.0), card.figure)
        assertEquals(Outcome.ACCEPT, HudhudCoordinator.outcomeOf(ask, card, { env.getString(it) }).outcome)
    }

    private companion object {
        const val DAY = 24 * 60 * 60_000L
    }
}
