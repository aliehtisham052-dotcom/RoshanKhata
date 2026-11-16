package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.Access
import com.innovation313.roshankhata.data.AnswerBook
import com.innovation313.roshankhata.data.DesktopDatabase
import com.innovation313.roshankhata.data.FileShopSettings
import com.innovation313.roshankhata.data.Hudhud
import com.innovation313.roshankhata.data.LedgerEntry
import com.innovation313.roshankhata.data.Party
import com.innovation313.roshankhata.data.Role
import com.innovation313.roshankhata.data.ScreenId
import com.innovation313.roshankhata.data.Staff
import com.innovation313.roshankhata.data.VoiceQuestion
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.desktopWords
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

    private fun env(): HudhudDeskEnv = HudhudDeskEnv(
        desktopWords(Locale.US), Locale.US, FileShopSettings(File(dir, "shop.properties")),
        AppPrefs(File(dir, "app.properties")), 1L, ReminderStore(File(dir, "reminders_b1.properties"))
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
}
