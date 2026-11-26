package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The talk (Marhala 7, 26 Nov 2026): what it is about, what clears it, a correction ("nahi, doosra Ali"), the follow-ups and
 * the answers to a question he asked back — each through [HudhudDialogue.read], which reads a sentence on its own through
 * [Hudhud.understand] with the shipped sense attached and the app's own pages (lesson 145). The exit gate is stale-memory
 * mistakes 0: nothing of a talk is carried into another book, to another person, past ten quiet minutes or a greeting.
 *
 * Names and goods are invented. Sindhi, Bengali, Farsi and Arabic sentences are for a native read (D17).
 */
class HudhudDialogueTest {

    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val names = listOf("Ali Raza", "Ali Hassan", "Ali Ahmad", "Bashir Ahmad", "Tariq Butt", "Naeem Khan")
        .mapIndexed { i, n -> Hudhud.Name(i + 1L, n) }
    private val goods = listOf("Urea", "DAP", "Sugar").mapIndexed { i, n -> Hudhud.Name(100L + i, n) }

    private val aliRaza = 1L
    private val aliHassan = 2L
    private val aliAhmad = 3L
    private val bashir = 4L
    private val urea = 100L

    private val owner = HudhudDialogue.who("owner", reports = true, staff = true)
    private val cashier = HudhudDialogue.who("staff:c1", reports = false, staff = false)
    private val t0 = 1_000_000L

    private fun talk(about: Hudhud.Ask?, pending: Hudhud.Ask? = null, last: Hudhud.Ask? = about ?: pending) =
        HudhudDialogue.State(1L, owner, t0, about, pending, last)

    private fun read(lang: String, said: String, s: HudhudDialogue.State): Hudhud.Ask =
        HudhudDialogue.read(said, names, HudhudRealPages.forApp(lang), lang, goods, emptyList(), s)

    /** One turn as the sheet takes it: the talk as it stands now, the reading, the talk after it. */
    private fun turn(lang: String, said: String, s: HudhudDialogue.State, book: Long = 1L, who: String = owner, now: Long = t0 + 1):
        Pair<Hudhud.Ask, HudhudDialogue.State> {
        val here = HudhudDialogue.current(s, book, who, now)
        val ask = read(lang, said, here)
        return ask to HudhudDialogue.after(here, ask, reverent = false, now = now)
    }

    private fun peopleOf(a: Hudhud.Ask) = HudhudDialogue.peopleOf(a)?.toSet()

    // ------------------------------------------------------------------------------------------ stale memory (the exit gate)

    @Test fun `another book, another person, ten quiet minutes or a clock gone back start a new talk`() {
        val s = talk(Hudhud.Ask.Parties(listOf(aliRaza)))
        assertEquals(s, HudhudDialogue.current(s, 1L, owner, t0 + 60_000))
        for (fresh in listOf(
            HudhudDialogue.current(s, 2L, owner, t0 + 1),
            HudhudDialogue.current(s, 1L, cashier, t0 + 1),
            HudhudDialogue.current(s, 1L, owner, t0 + HudhudDialogue.TIME_OUT_MS + 1),
            HudhudDialogue.current(s, 1L, owner, t0 - 1)
        )) {
            assertNull(fresh.about); assertNull(fresh.pending); assertNull(fresh.last)
        }
    }

    @Test fun `after a book switch nothing of the last book's man carries`() {
        val (ask, _) = turn("ur-Latn", "aur number?", talk(Hudhud.Ask.Parties(listOf(aliRaza))), book = 2L)
        assertNotEquals(Hudhud.Ask.Person(listOf(aliRaza), Hudhud.Facet.PHONE), ask)
        assertTrue(ask.toString(), aliRaza !in (peopleOf(ask) ?: emptySet()))
    }

    @Test fun `a cashier signed in is never handed the owner's carried figure`() {
        val profit = Hudhud.Ask.Figure(VoiceQuestion.Kind.PROFIT_MONTH)
        // The owner's own talk carries: "and last month?" is last month's profit.
        val (mine, _) = turn("en", "and last month?", talk(profit))
        assertEquals(Hudhud.Measure.PROFIT, (mine as Hudhud.Ask.Period).measure)
        // The same words from the cashier signed in after him: no carried profit, only a guess of the sales (shown as a guess).
        val (his, _) = turn("en", "and last month?", talk(profit), who = cashier)
        val inner = (his as? Hudhud.Ask.Guessed)?.ask ?: his
        assertFalse(his.toString(), inner is Hudhud.Ask.Period && inner.measure == Hudhud.Measure.PROFIT)
        assertFalse(his.toString(), inner is Hudhud.Ask.Figure && inner.kind == VoiceQuestion.Kind.PROFIT_MONTH)
    }

    @Test fun `a talk left ten minutes does not carry its man`() {
        val (ask, _) = turn("ur-Latn", "uska number", talk(Hudhud.Ask.Parties(listOf(aliRaza))), now = t0 + HudhudDialogue.TIME_OUT_MS + 5)
        assertTrue(ask.toString(), aliRaza !in (peopleOf(ask) ?: emptySet()))
    }

    @Test fun `a greeting or a goodbye ends the talk`() {
        val s = talk(Hudhud.Ask.Parties(listOf(aliRaza)))
        for (end in listOf(Hudhud.Ask.Salaam, Hudhud.Ask.Hello, Hudhud.Ask.Chat(Hudhud.Talk.BYE), Hudhud.Ask.Chat(Hudhud.Talk.HOW_ARE_YOU, salaam = true))) {
            val after = HudhudDialogue.after(s, end, reverent = false, now = t0 + 1)
            assertNull(end.toString(), after.about); assertNull(after.pending); assertNull(after.last)
            val (ask, _) = turn("ur-Latn", "aur number?", after)
            assertTrue(ask.toString(), aliRaza !in (peopleOf(ask) ?: emptySet()))
        }
        // "Bas", "dobara bolo" and the like leave it as it was.
        val again = HudhudDialogue.after(s, Hudhud.Ask.Voice(Hudhud.VoiceChange.AGAIN), reverent = false, now = t0 + 1)
        assertEquals(s.about, again.about)
    }

    @Test fun `a new man named replaces the old and the next follow-up is his`() {
        var s = talk(Hudhud.Ask.Parties(listOf(aliRaza)))
        val (first, s1) = turn("ur-Latn", "Bashir Ahmad ka balance", s); s = s1
        assertEquals(Hudhud.Ask.Parties(listOf(bashir)), first)
        assertEquals(listOf(bashir), s.person)
        val (next, _) = turn("ur-Latn", "aur number?", s)
        assertEquals(Hudhud.Ask.Person(listOf(bashir), Hudhud.Facet.PHONE), next)
    }

    @Test fun `a question of the shop changes the topic and the old man no longer carries`() {
        val (sale, s) = turn("ur-Latn", "aaj ki sale kitni hai", talk(Hudhud.Ask.Parties(listOf(aliRaza))))
        assertTrue(sale.toString(), peopleOf(sale) == null)
        assertNull(s.person)
        val (next, _) = turn("ur-Latn", "uska number", s)
        assertTrue(next.toString(), aliRaza !in (peopleOf(next) ?: emptySet()))
    }

    @Test fun `a matter of faith is a detour and a question back is no topic`() {
        val s = talk(Hudhud.Ask.Parties(listOf(aliRaza)))
        val faith = HudhudDialogue.after(s, Hudhud.Ask.About(Hudhud.AboutTopic.TAWHEED), reverent = true, now = t0 + 1)
        assertEquals(s.about, faith.about)
        val whose = HudhudDialogue.after(s, Hudhud.Ask.NeedName(Hudhud.Facet.PHONE), reverent = false, now = t0 + 1)
        assertEquals(s.about, whose.about)
        assertEquals(Hudhud.Ask.NeedName(Hudhud.Facet.PHONE), whose.pending)
        // Any answer that asks nothing back drops the pending question.
        val answered = HudhudDialogue.after(whose, Hudhud.Ask.Figure(VoiceQuestion.Kind.SALES_TODAY), reverent = false, now = t0 + 2)
        assertNull(answered.pending)
    }

    // ------------------------------------------------------------------------------------------ "no, the other Ali"

    private val otherAlis = setOf(aliHassan, aliAhmad)

    @Test fun `the other Ali in nine languages keeps the question and changes only the man`() {
        for ((lang, said) in listOf(
            "ur-Latn" to "nahi, doosra Ali",
            "ur-Latn" to "doosra Ali",
            "ur-Latn" to "nahi doosra",
            "en" to "no, the other Ali",
            "ur" to "نہیں، دوسرا علی",
            "ur" to "غلط، دوسرا علی",
            "hi" to "नहीं, दूसरा अली",
            "bn" to "না, অন্য আলী",          // native read
            "ar" to "لا، علي الآخر",          // native read
            "fa" to "نه، علی دیگر",           // native read
            "sd" to "نه، ٻيو علي",            // native read
            "id" to "bukan, Ali yang lain"
        )) {
            val balance = read(lang, said, talk(Hudhud.Ask.Parties(listOf(aliRaza))))
            assertTrue("[$lang] $said -> $balance", balance is Hudhud.Ask.Parties && balance.ids.toSet() == otherAlis)
            val phone = read(lang, said, talk(Hudhud.Ask.Person(listOf(aliRaza), Hudhud.Facet.PHONE)))
            assertTrue("[$lang] $said -> $phone", phone is Hudhud.Ask.Person && phone.facet == Hudhud.Facet.PHONE && phone.ids.toSet() == otherAlis)
        }
    }

    @Test fun `the other Ali keeps the span and the side of what he paid`() {
        val paid = Hudhud.Ask.Moved(listOf(aliRaza), VoiceQuestion.Window.LAST_MONTH, got = true)
        val a = read("ur-Latn", "nahi, doosra Ali", talk(paid))
        assertTrue(a.toString(), a is Hudhud.Ask.Moved && a.window == VoiceQuestion.Window.LAST_MONTH && a.got && a.ids.toSet() == otherAlis)
    }

    @Test fun `a man named in the correction is asked the same thing`() {
        for ((lang, said) in listOf("ur-Latn" to "nahi, Ali Hassan", "ur-Latn" to "nahi Ali Hassan ka", "en" to "no, Ali Hassan",
            "en" to "not that one, Ali Hassan", "ur" to "نہیں، علی حسن")) {
            val a = read(lang, said, talk(Hudhud.Ask.Person(listOf(aliRaza), Hudhud.Facet.PHONE)))
            assertEquals("[$lang] $said", Hudhud.Ask.Person(listOf(aliHassan), Hudhud.Facet.PHONE), a)
        }
        assertEquals(Hudhud.Ask.Parties(listOf(aliHassan)), read("en", "not that one, Ali Hassan", talk(Hudhud.Ask.Parties(listOf(aliRaza)))))
    }

    @Test fun `the whole talk of a correction - balance, the other Ali, the choice, his balance`() {
        var s = HudhudDialogue.State(1L, owner, t0)
        val (first, s1) = turn("ur-Latn", "Ali Raza ka balance", s); s = s1
        assertEquals(Hudhud.Ask.Parties(listOf(aliRaza)), first)
        val (choice, s2) = turn("ur-Latn", "nahi, doosra Ali", s); s = s2
        assertEquals(otherAlis, peopleOf(choice))
        assertEquals(choice, s.pending)
        val (his, s3) = turn("ur-Latn", "Ali Hassan", s); s = s3
        assertEquals(Hudhud.Ask.Parties(listOf(aliHassan)), his)
        assertNull(s3.pending)
    }

    @Test fun `when nobody else has the name he asks whose and does not guess`() {
        val a = read("ur-Latn", "nahi, doosra Bashir", talk(Hudhud.Ask.Person(listOf(bashir), Hudhud.Facet.PHONE)))
        assertEquals(Hudhud.Ask.NeedName(Hudhud.Facet.PHONE), a)
    }

    @Test fun `the other product and a product named in a correction`() {
        val price = Hudhud.Ask.Goods(listOf(urea), Hudhud.GoodsFacet.PRICE)
        assertEquals(Hudhud.Ask.Goods(listOf(101L), Hudhud.GoodsFacet.PRICE), read("ur-Latn", "nahi DAP", talk(price)))
        assertEquals(Hudhud.Ask.Goods(listOf(101L), Hudhud.GoodsFacet.PRICE), read("ur-Latn", "doosra DAP", talk(price)))
        // A product that is the only one of its name: which product, never the same one again.
        assertEquals(Hudhud.Ask.NeedGoods(Hudhud.GoodsFacet.PRICE), read("ur-Latn", "nahi, doosri Urea", talk(price)))
        // A product named with a question of its own keeps that question.
        assertEquals(Hudhud.Ask.Goods(listOf(101L), Hudhud.GoodsFacet.STOCK), read("ur-Latn", "nahi, DAP ka stock", talk(price)))
    }

    @Test fun `a no that belongs to the sentence is not taken for a correction`() {
        // "Nahi bika maal" after a product's price is the dead stock, never the price with "nahi" dropped.
        val a = read("ur-Latn", "nahi bika maal", talk(Hudhud.Ask.Goods(listOf(urea), Hudhud.GoodsFacet.PRICE)))
        assertFalse(a.toString(), a is Hudhud.Ask.Goods)
        // A correction of the span: the same figure over the days said.
        assertEquals(Hudhud.Ask.Period(Hudhud.Measure.SALES, VoiceQuestion.Window.LAST_MONTH),
            read("ur-Latn", "nahi, pichle mahine", talk(Hudhud.Ask.Figure(VoiceQuestion.Kind.SALES_MONTH))))
        assertEquals(Hudhud.Ask.Period(Hudhud.Measure.SALES, VoiceQuestion.Window.LAST_MONTH),
            read("en", "no, last month", talk(Hudhud.Ask.Figure(VoiceQuestion.Kind.SALES_MONTH))))
    }

    // ------------------------------------------------------------------------------------------ follow-ups

    @Test fun `and his number in nine languages`() {
        for ((lang, said) in listOf("ur-Latn" to "aur number?", "en" to "and his number?", "ur" to "اور نمبر؟", "hi" to "और नंबर?",
            "bn" to "আর নম্বর?", "ar" to "ورقمه؟", "ar" to "ورقم هاتفه؟", "fa" to "و شماره‌اش؟", "sd" to "۽ نمبر؟", "id" to "nomornya?")) {
            assertEquals("[$lang] $said", Hudhud.Ask.Person(listOf(aliRaza), Hudhud.Facet.PHONE),
                read(lang, said, talk(Hudhud.Ask.Parties(listOf(aliRaza)))))
        }
    }

    @Test fun `and last month in nine languages is the same figure over the new span`() {
        for ((lang, said) in listOf("ur-Latn" to "aur pichle mahine?", "en" to "and last month?", "ur" to "اور پچھلے مہینے؟",
            "hi" to "और पिछले महीने?", "bn" to "আর গত মাসে?", "ar" to "والشهر الماضي؟", "fa" to "و ماه گذشته؟", "sd" to "۽ پوئين مهيني؟",
            "id" to "dan bulan lalu?")) {
            assertEquals("[$lang] $said", Hudhud.Ask.Period(Hudhud.Measure.SALES, VoiceQuestion.Window.LAST_MONTH),
                read(lang, said, talk(Hudhud.Ask.Figure(VoiceQuestion.Kind.SALES_MONTH))))
        }
    }

    @Test fun `its stock in nine languages is the product before`() {
        for ((lang, said) in listOf("ur-Latn" to "iska stock?", "en" to "its stock?", "ur" to "اس کا اسٹاک؟", "hi" to "इसका स्टॉक?",
            "bn" to "এর স্টক?", "ar" to "ومخزونه؟", "fa" to "موجودی‌اش؟", "sd" to "ان جو اسٽاڪ؟", "id" to "stoknya?")) {
            assertEquals("[$lang] $said", Hudhud.Ask.Goods(listOf(urea), Hudhud.GoodsFacet.STOCK),
                read(lang, said, talk(Hudhud.Ask.Goods(listOf(urea), Hudhud.GoodsFacet.PRICE))))
        }
    }

    @Test fun `and Bashir's in nine languages is the same question of another man`() {
        for ((lang, said) in listOf("ur-Latn" to "aur Bashir ka?", "en" to "and Bashir's?", "ur" to "اور بشیر کا؟", "hi" to "और बशीर का?",
            "bn" to "আর বশিরের?", "ar" to "وبشير؟", "fa" to "و بشیر؟", "sd" to "۽ بشير جو؟", "id" to "kalau Bashir?")) {
            assertEquals("[$lang] $said", Hudhud.Ask.Person(listOf(bashir), Hudhud.Facet.PHONE),
                read(lang, said, talk(Hudhud.Ask.Person(listOf(aliRaza), Hudhud.Facet.PHONE))))
        }
        // After a balance (no question of its own to carry) "and Bashir's?" is his balance, as before.
        assertEquals(Hudhud.Ask.Parties(listOf(bashir)), read("ur", "اور بشیر کا؟", talk(Hudhud.Ask.Parties(listOf(aliRaza)))))
    }

    // ------------------------------------------------------------------------------------------ yes, no, cancel, again

    private val guess = Hudhud.Ask.Guessed(Hudhud.Ask.Figure(VoiceQuestion.Kind.PROFIT_MONTH))

    @Test fun `yes to a guess is what he took it for, in nine languages`() {
        for ((lang, said) in listOf("ur-Latn" to "haan", "ur-Latn" to "ji haan", "en" to "yes", "ur" to "ہاں", "hi" to "हाँ", "bn" to "হ্যাঁ",
            "ar" to "نعم", "fa" to "بله", "sd" to "ها", "id" to "ya")) {
            assertEquals("[$lang] $said", guess.ask, read(lang, said, talk(guess.ask, guess, guess)))
        }
    }

    @Test fun `no and cancel drop the question back`() {
        for ((lang, said) in listOf("ur-Latn" to "nahi", "en" to "no", "ur" to "نہیں", "hi" to "नहीं", "bn" to "না", "ar" to "لا",
            "fa" to "نه", "id" to "tidak", "ur-Latn" to "rehne do", "ur-Latn" to "cancel", "en" to "never mind", "ur" to "رہنے دو",
            "hi" to "रहने दो", "id" to "batal")) {
            val (ask, after) = turn(lang, said, talk(guess.ask, guess, guess))
            assertEquals("[$lang] $said", Hudhud.Ask.Chat(Hudhud.Talk.ADDRESS), ask)
            assertNull("[$lang] $said", after.pending)
        }
    }

    @Test fun `yes never takes a write, a choice of people or which way the money runs`() {
        val entry = Hudhud.Ask.Guessed(Hudhud.Ask.Entry(500.0, true, listOf(aliRaza), listOf("ali")))
        assertEquals(entry, read("ur-Latn", "haan", talk(null, entry)))
        val callMe = Hudhud.Ask.DidYouMean(listOf(Hudhud.Ask.CallMe("Ali bhai"), Hudhud.Ask.Clock))
        assertEquals(callMe, read("en", "yes", talk(null, callMe)))
        val side = Hudhud.Ask.DidYouMean(listOf(Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GET), Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GIVE)), Hudhud.Unsure.SIDE)
        assertEquals(side, read("ur-Latn", "haan", talk(null, side)))
        val pick = Hudhud.Ask.Person(listOf(aliAhmad, aliHassan, aliRaza), Hudhud.Facet.PHONE)
        assertEquals(pick, read("ur-Latn", "haan", talk(pick, pick)))
        val whose = Hudhud.Ask.NeedName(Hudhud.Facet.PHONE)
        assertEquals(whose, read("ur-Latn", "haan", talk(null, whose)))
        // The side question is answered by its own words.
        assertEquals(Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GET), read("ur-Latn", "lene hain", talk(null, side)))
        assertEquals(Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GIVE), read("ur-Latn", "dene hain", talk(null, side)))
    }

    @Test fun `no to a choice of meanings is the next meaning, still offered as a choice`() {
        val dym = Hudhud.Ask.DidYouMean(listOf(Hudhud.Ask.Status(Hudhud.StatusOf.BOOK), Hudhud.Ask.NeedName(Hudhud.Facet.LAST_ENTRY)))
        assertEquals(Hudhud.Ask.Status(Hudhud.StatusOf.BOOK), read("ur-Latn", "haan", talk(null, dym)))
        assertEquals(Hudhud.Ask.DidYouMean(listOf(Hudhud.Ask.NeedName(Hudhud.Facet.LAST_ENTRY))), read("ur-Latn", "nahi", talk(null, dym)))
        assertEquals(Hudhud.Ask.NeedName(Hudhud.Facet.LAST_ENTRY), read("ur-Latn", "doosra wala", talk(null, dym)))
    }

    @Test fun `the first or the second of a choice of people`() {
        val pick = Hudhud.Ask.Person(listOf(aliAhmad, aliHassan, aliRaza), Hudhud.Facet.PHONE)
        assertEquals(Hudhud.Ask.Person(listOf(aliAhmad), Hudhud.Facet.PHONE), read("ur-Latn", "pehla wala", talk(pick, pick)))
        assertEquals(Hudhud.Ask.Person(listOf(aliHassan), Hudhud.Facet.PHONE), read("ur-Latn", "doosra wala", talk(pick, pick)))
        assertEquals(Hudhud.Ask.Person(listOf(aliHassan), Hudhud.Facet.PHONE), read("en", "the second one", talk(pick, pick)))
        assertEquals(Hudhud.Ask.Person(listOf(aliRaza), Hudhud.Facet.PHONE), read("ur", "تیسرا", talk(pick, pick)))
        assertEquals(Hudhud.Ask.Person(listOf(aliHassan), Hudhud.Facet.PHONE), read("ur-Latn", "Ali Hassan", talk(pick, pick)))
    }

    @Test fun `a name given to whose is that man's fact, not his balance`() {
        val whose = Hudhud.Ask.NeedName(Hudhud.Facet.PHONE)
        assertEquals(Hudhud.Ask.Person(listOf(aliRaza), Hudhud.Facet.PHONE), read("ur-Latn", "Ali Raza", talk(null, whose)))
        assertEquals(Hudhud.Ask.Person(listOf(aliRaza), Hudhud.Facet.PHONE), read("ur-Latn", "Ali Raza ka", talk(null, whose)))
        assertEquals(Hudhud.Ask.Person(listOf(aliRaza), Hudhud.Facet.PHONE), read("ar", "علي رضا", talk(null, whose)))
        val which = Hudhud.Ask.NeedGoods(Hudhud.GoodsFacet.PRICE)
        assertEquals(Hudhud.Ask.Goods(listOf(101L), Hudhud.GoodsFacet.PRICE), read("ur-Latn", "DAP", talk(null, which)))
    }

    @Test fun `say it again is his voice's own and keeps the question he asked`() {
        val pick = Hudhud.Ask.Person(listOf(aliAhmad, aliHassan, aliRaza), Hudhud.Facet.PHONE)
        for ((lang, said) in listOf("ur-Latn" to "dobara bolo", "ur-Latn" to "phir se batao", "en" to "say again", "ur" to "دوبارہ بتاؤ",
            "hi" to "दोबारा बोलो", "bn" to "আবার বলো", "ar" to "أعد", "fa" to "دوباره بگو", "sd" to "ٻيهر ٻڌايو", "id" to "ulangi")) {
            val (ask, after) = turn(lang, said, talk(pick, pick))
            assertEquals("[$lang] $said", Hudhud.Ask.Voice(Hudhud.VoiceChange.AGAIN), ask)
            assertEquals("[$lang] $said", pick, after.pending)
        }
    }

    @Test fun `with no talk yes and no are read on their own as before`() {
        val none = HudhudDialogue.State(1L, owner, t0)
        assertEquals(Hudhud.understand("haan", names, HudhudRealPages.forApp("ur-Latn"), "ur-Latn", goods, null),
            read("ur-Latn", "haan", none))
        assertEquals(Hudhud.understand("nahi doosra Ali", names, HudhudRealPages.forApp("ur-Latn"), "ur-Latn", goods, null),
            read("ur-Latn", "nahi doosra Ali", none))
    }

    @Test fun `the one rule of what carries is the answer layer's`() {
        assertTrue(HudhudDialogue.carries(Hudhud.Ask.Parties(listOf(aliRaza)), false))
        assertFalse(HudhudDialogue.carries(Hudhud.Ask.Parties(listOf(aliRaza)), true))
        assertFalse(HudhudDialogue.carries(Hudhud.Ask.NeedName(null), false))
        assertFalse(HudhudDialogue.carries(Hudhud.Ask.Unknown, false))
    }

    // ------------------------------------------------------------------------------------------ each guard alone (lesson 129)

    @Test fun `a matter of faith between keeps the man a correction mends`() {
        val s = talk(Hudhud.Ask.Parties(listOf(aliRaza)))
        val faith = HudhudDialogue.after(s, Hudhud.Ask.About(Hudhud.AboutTopic.TAWHEED), reverent = true, now = t0 + 1)
        assertEquals(otherAlis, peopleOf(read("ur-Latn", "nahi, doosra Ali", faith)))
    }

    @Test fun `no or cancel after an answer, with nothing asked back, is no list of who has not paid`() {
        for (said in listOf("nahi", "cancel", "rehne do")) {
            assertEquals(said, Hudhud.Ask.Chat(Hudhud.Talk.ADDRESS), read("ur-Latn", said, talk(Hudhud.Ask.Parties(listOf(aliRaza)))))
        }
    }

    @Test fun `the other one after a choice is the second of it`() {
        val pick = Hudhud.Ask.Person(listOf(aliAhmad, aliHassan, aliRaza), Hudhud.Facet.PHONE)
        assertEquals(Hudhud.Ask.Person(listOf(aliHassan), Hudhud.Facet.PHONE), read("en", "the other one", talk(pick, pick)))
    }

    @Test fun `a correction that asks something of its own is not put in the last question`() {
        val a = read("ur-Latn", "nahi, Bashir Ahmad ka balance batao", talk(Hudhud.Ask.Person(listOf(aliRaza), Hudhud.Facet.PHONE)))
        assertNotEquals(Hudhud.Ask.Person(listOf(bashir), Hudhud.Facet.PHONE), a)
        val b = read("ur-Latn", "Ali Raza ka balance", talk(null, Hudhud.Ask.NeedName(Hudhud.Facet.PHONE)))
        assertEquals(Hudhud.Ask.Parties(listOf(aliRaza)), b)
    }

    @Test fun `the other of a name nobody else has asks whose`() {
        assertEquals(Hudhud.Ask.NeedName(Hudhud.Facet.PHONE),
            read("ur-Latn", "nahi, doosra Kamran", talk(Hudhud.Ask.Person(listOf(aliRaza), Hudhud.Facet.PHONE))))
    }

    @Test fun `a name alone after one man's question is the same question, but never after an entry`() {
        assertEquals(Hudhud.Ask.Person(listOf(bashir), Hudhud.Facet.PHONE), read("ur", "بشیر کا؟", talk(Hudhud.Ask.Person(listOf(aliRaza), Hudhud.Facet.PHONE))))
        val entry = Hudhud.Ask.Entry(500.0, true, listOf(aliRaza), listOf("ali", "raza"))
        val a = read("ur", "اور بشیر کا؟", talk(entry))
        assertFalse(a.toString(), a is Hudhud.Ask.Entry)
    }
}
