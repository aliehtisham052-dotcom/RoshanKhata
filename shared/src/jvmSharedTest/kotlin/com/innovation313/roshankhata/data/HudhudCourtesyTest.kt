package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.Hudhud.Ask
import com.innovation313.roshankhata.data.Hudhud.Facet
import com.innovation313.roshankhata.data.Hudhud.Talk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A thank-you, a goodbye or a salaam in front of a real question (15 Nov 2026, the owner: "he must keep the sense of what is asked").
 * "thanks what is my balance" was answered "you're welcome", "good morning how much does Ali owe" with a greeting, "salam Ali Raza ka
 * number" with a salaam: the rules took any sentence of little words as courtesy (their list of little words holds question and
 * book words), the sense had learnt the four courtesy labels from whole sentences, and the rules' fall-through answered a greeting
 * for any sentence with a greeting word. A sentence is courtesy only when nothing in it asks the book (a customer or a product of
 * this book, a word of the book, a question word, a figure word, a closed few — "how", "total"); then a courtesy label stands,
 * else the question is answered exactly as it would be without the courtesy words.
 *
 * The test sentences are written by the maker of the tests, not by a native reader of each language; every name is made up.
 */
class HudhudCourtesyTest {

    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val goods = listOf(Hudhud.Name(100, "Urea"))
    private val shop = listOf(Hudhud.Name(1, "Ali Raza"), Hudhud.Name(2, "Noman Aslam"), Hudhud.Name(3, "Saleem Bhai"))

    private fun read(s: String, language: String?, names: List<Hudhud.Name> = shop) =
        Hudhud.understand(s, names, emptyList(), language, goods = goods)

    private fun isCourtesy(a: Ask) =
        a is Ask.Salaam || a is Ask.Hello || (a is Ask.Chat && (a.talk == Talk.THANKS || a.talk == Talk.BYE))

    /** Language, the plain question, the thank-you word, the goodbye, the salaam. */
    private data class Row(val lang: String, val question: String, val thanks: String, val bye: String, val salaam: String)

    private val rows = listOf(
        Row("en", "what is my balance", "thanks", "bye bye", "salam"),
        Row("en", "how much do I owe", "thank you", "bye", "hello"),
        Row("ur-Latn", "mera hisaab kitna hai", "shukriya", "allah hafiz", "assalam o alaikum"),
        Row("ur-Latn", "total kitna hai", "shukriya", "bye bye", "salam"),
        Row("ur", "میرا حساب کتنا ہے", "شکریہ", "خدا حافظ", "سلام"),
        Row("ar", "كم الرصيد", "شكرا", "مع السلامة", "السلام عليكم"),
        Row("fa", "حساب من چقدر است", "ممنون", "خداحافظ", "سلام"),
        Row("sd", "منهنجو حساب ڪيترو آهي", "شڪريو", "الله حافظ", "سلام"),
        Row("hi", "मेरा हिसाब कितना है", "धन्यवाद", "अलविदा", "सलाम"),
        Row("bn", "আমার হিসাব কত", "ধন্যবাদ", "বিদায়", "সালাম"),
        Row("in", "berapa total utang saya", "terima kasih", "sampai jumpa", "halo")
    )

    @Test
    fun aCourtesyWordInFrontOfAQuestionChangesNothing() {
        for (r in rows) {
            val plain = read(r.question, r.lang)
            assertFalse("plain ${r.question} is courtesy", isCourtesy(plain))
            for (c in listOf(r.thanks, r.bye, r.salaam)) {
                assertEquals("${r.lang}: $c ${r.question}", plain, read("$c ${r.question}", r.lang))
                assertEquals("${r.lang}: ${r.question} $c", plain, read("${r.question} $c", r.lang))
            }
        }
    }

    @Test
    fun aGreetingBeforeANamedCustomerIsHisQuestion() {
        assertEquals(Ask.Person(listOf(1), Facet.PHONE), read("salam Ali Raza ka number", "ur-Latn"))
        assertEquals(Ask.Person(listOf(1), Facet.PHONE), read("shukriya Ali Raza ka number", "ur-Latn"))
        assertEquals(Ask.Person(listOf(1), Facet.PHONE), read("bye bye Ali Raza ka number", "ur-Latn"))
        assertEquals(Ask.Person(listOf(1), Facet.PHONE), read("سلام علی رضا کا نمبر", "ur"))
        assertEquals(Ask.Person(listOf(1), Facet.PHONE), read("السلام عليكم رقم علي رضا", "ar"))
        assertEquals(Ask.Person(listOf(1), Facet.PHONE), read("سلام شماره علی رضا", "fa"))
        assertEquals(Ask.Person(listOf(1), Facet.PHONE), read("सलाम अली रज़ा का नंबर", "hi"))
        assertEquals(Ask.Parties(listOf(1)), read("good morning how much does Ali Raza owe", "en"))
        assertEquals(Ask.Parties(listOf(1)), read("thanks, how much does Ali Raza owe", "en"))
        assertEquals(Ask.Parties(listOf(1)), read("salam Ali", "en"))
    }

    @Test
    fun aGreetingBeforeAFigureIsTheFigure() {
        assertEquals(Ask.Figure(VoiceQuestion.Kind.SALES_TODAY), read("good morning today sales", "en"))
        assertEquals(Ask.Figure(VoiceQuestion.Kind.SALES_TODAY), read("assalam o alaikum hudhud aaj ki sale", "ur-Latn"))
        assertEquals(Ask.Figure(VoiceQuestion.Kind.SALES_TODAY), read("bye what are the sales today", "en"))
        assertEquals(Ask.Figure(VoiceQuestion.Kind.TO_GET), read("hey what is the total I will get", "en"))
    }

    @Test
    fun courtesyAloneStaysCourtesy() {
        for (s in listOf("thanks a lot", "thanks so much", "thank you very much indeed my friend", "bye for now", "thanks for today")) {
            assertTrue(s, isCourtesy(read(s, "en")))
        }
        assertTrue(isCourtesy(read("shukriya bohat bohat", "ur-Latn")))
        assertEquals(Ask.Salaam, read("salam bhai", "ur-Latn"))
        assertEquals(Ask.Hello, read("hello hudhud", "en"))
        assertEquals(Ask.Chat(Talk.HOW_ARE_YOU, true), read("salam kaise ho", "ur-Latn"))
        assertEquals(Ask.Chat(Talk.HOW_ARE_YOU, false), read("thanks how are you", "en"))
    }

    /** The guards one by one, where only the guard can refuse (lesson 129): the thank-you words never reach the sense. */
    @Test
    fun theSenseNeverSeesTheThankYouBeforeAQuestion() {
        val said = Hudhud.senseHeardForTest("شڪريو منهنجو حساب ڪيترو آهي", shop, goods)
        assertEquals("the thank-you word is gone, the four question words stay", 4, said.split(" ").size)
        assertEquals("thanks so much", Hudhud.senseHeardForTest("thanks so much", shop, goods))
    }

    /** Lesson 129: each guard where only it can refuse. A courtesy, calling or filler word asks nothing; a name or product of the book does. */
    @Test
    fun theLittleWordsAskNothingAndTheBooksNamesAsk() {
        for (r in rows) for (w in listOf(r.thanks, r.bye, r.salaam).filter { " " !in it })
            assertFalse("${r.lang}: $w", Hudhud.asksBookForTest(w, shop, goods))
        assertFalse(Hudhud.asksBookForTest("yaar", shop, goods))
        assertFalse(Hudhud.asksBookForTest("bhai", emptyList(), emptyList()))
        assertTrue(Hudhud.asksBookForTest("noman", shop, goods))
        assertTrue(Hudhud.asksBookForTest("urea", shop, goods))
        assertFalse(Hudhud.asksBookForTest("banana", shop, goods))
    }

    /** The rules alone (no sense behind them): a greeting in front of a book word is never answered as a greeting. */
    @Test
    fun withoutTheSenseAGreetingBeforeABookWordIsNotAGreeting() {
        HudhudSense.use(null)
        try {
            for (q in listOf("hello customers", "hello total", "salam total kitna hai", "hello Noman Aslam"))
                assertFalse(q, read(q, "en").let { it is Ask.Hello || it is Ask.Salaam })
            assertTrue(read("hello", "en") is Ask.Hello)
            assertTrue(read("salam", "ur-Latn") is Ask.Salaam)
        } finally { HudhudSense.use(HudhudModelTest.shippedModel()) }
    }
}
