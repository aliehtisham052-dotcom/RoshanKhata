package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.Hudhud.Ask
import com.innovation313.roshankhata.data.Hudhud.WhyOf
import com.innovation313.roshankhata.data.VoiceQuestion.Window
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * "Why" about a figure of the book (16 Nov 2026, audit T8, the owner's H-10): read by rules ([HudhudWhyAsk]), never guessed, through
 * the whole pipeline with the shipped sense (lesson 145). The answer's two spans and its movers are pure and held here; the card
 * itself is the app's (HudhudWhy). Names and goods are invented.
 */
class HudhudWhyTest {

    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val names = listOf("Ali Raza", "Bashir Ahmad", "Tariq Butt", "Naeem Khan", "Saleem Bhai", "Kamran Traders")
        .mapIndexed { i, n -> Hudhud.Name(i + 1L, n) }
    private val goods = listOf("Urea", "DAP", "Sugar", "Paracetamol", "Atta", "Ghee").mapIndexed { i, n -> Hudhud.Name(100L + i, n) }

    private fun read(lang: String, said: String) = Hudhud.understand(said, names, HudhudRealPages.forApp(lang), lang, goods, null)

    private fun why(of: WhyOf, w: Window = Window.THIS_MONTH, down: Boolean? = true) = Ask.Why(of, w, down)

    @Test fun `why the sales fell, in the nine languages`() {
        for ((lang, said) in listOf(
            "ur-Latn" to "Is mahine sale kam kyun hui", "en" to "why did sales drop this month", "ur" to "اس مہینے سیل کم کیوں ہوئی",
            "hi" to "इस महीने बिक्री कम क्यों हुई", "bn" to "এই মাসে বিক্রি কম কেন", "ar" to "لماذا انخفضت المبيعات هذا الشهر",
            "fa" to "چرا فروش این ماه کم شد", "sd" to "هن مهيني وڪرو گهٽ ڇو ٿيو", "in" to "kenapa penjualan bulan ini turun"
        )) assertEquals("[$lang] $said", why(WhyOf.SALES), read(lang, said))
    }

    @Test fun `each figure the book has, and the way asked`() {
        assertEquals(why(WhyOf.PROFIT), read("ur-Latn", "profit kyun gira"))
        assertEquals(why(WhyOf.PROFIT), read("ur", "منافع کیوں کم ہوا"))
        assertEquals(why(WhyOf.EXPENSES, down = false), read("en", "why are expenses up"))
        assertEquals(why(WhyOf.EXPENSES, down = false), read("ur-Latn", "kharcha kyun barh gaya"))
        assertEquals(why(WhyOf.COLLECTED), read("ur-Latn", "wasooli kyun kam hui"))
        assertEquals(why(WhyOf.DUES, down = false), read("ur-Latn", "udhaar kyun barh gaya"))
        // "Not" turns the way asked: the sales that did not go up are asked as down.
        assertEquals(why(WhyOf.SALES), read("ur-Latn", "sale kyun nahi barhi"))
        // The reason asked as a noun.
        assertEquals(why(WhyOf.SALES), read("ur-Latn", "sale kam hone ki wajah kya hai"))
    }

    @Test fun `the span said is the span compared`() {
        assertEquals(why(WhyOf.SALES, Window.YESTERDAY), read("ur-Latn", "kal sale kyun kam thi"))
        assertEquals(why(WhyOf.PROFIT, Window.LAST_MONTH), read("en", "why did profit fall last month"))
    }

    @Test fun `his name's story and somebody's own question stay as they were`() {
        assertEquals(Ask.About(Hudhud.AboutTopic.NAME_STORY), read("ur-Latn", "Hudhud tumhara naam kyun hai"))
        assertEquals(Ask.About(Hudhud.AboutTopic.NAME_STORY), read("en", "why is your name Hudhud"))
        assertEquals(Ask.Figure(VoiceQuestion.Kind.NOT_PAID), read("ur-Latn", "Ali ne paise kyun nahi diye"))
        // A customer or a product named: not the shop's "why", read as before.
        assertFalse(read("ur-Latn", "Urea ki sale kyun kam hui") is Ask.Why)
        assertFalse(read("ur-Latn", "Ali ki sale kyun kam hui") is Ask.Why)
        // The counter's own "kis wajah se return hua" is its returns' reasons.
        assertEquals(Hudhud.CounterFacet.RETURN_REASONS, (read("ur-Latn", "kis wajah se return hua") as Ask.Counter).facet)
    }

    @Test fun `what is not one figure over a span he can compare is not read`() {
        // Two figures in one sentence, a span he has no window for, the whole book (nothing before it), no "why".
        assertFalse(read("ur-Latn", "sale aur munafa kyun kam hua") is Ask.Why)
        assertFalse(read("ur-Latn", "pichle 3 mahine sale kyun kam hui") is Ask.Why)
        assertFalse(read("ur-Latn", "aaj tak sale kam kyun hai") is Ask.Why)
        assertEquals(Ask.Figure(VoiceQuestion.Kind.SALES_MONTH), read("ur-Latn", "is mahine sale kitni hui"))
        // The reader itself: a figure, an unknown word, a name.
        assertNull(HudhudWhyAsk.read("sale 500 kyun kam hui"))
        assertNull(HudhudWhyAsk.read("sale weather ki wajah se kyun kam hui"))
        assertNull(HudhudWhyAsk.read("Bashir ki sale kyun kam hui", isName = { it == "bashir" }))
        assertEquals(why(WhyOf.SALES), HudhudWhyAsk.read("Hudhud sale kyun kam hui", calling = { it == "hudhud" }))
        // Two figures with no other word: not one figure, not read (only this guard refuses it, lesson 129).
        assertNull(HudhudWhyAsk.read("sale munafa kyun kam hua"))
        // Arabic "لم" is "did not" here, no "why": a statement that the sales did not fall is not asked why.
        assertNull(HudhudWhyAsk.read("لم تنخفض المبيعات"))
        // A word that is the reader's own and a name the book holds ("Sabab", a customer): his, not the shop's "why".
        assertNull(HudhudWhyAsk.read("sabab ki sale kam hui", isName = { it == "sabab" }))
        assertEquals(why(WhyOf.SALES), HudhudWhyAsk.read("sabab ki sale kam hui"))
    }

    @Test fun `why in Urdu script meets no Latin name (BC-110)`() {
        // "کیوں" meets the letters of "Kevin", "وجہ" those of "Wajih": a book with them still reads the shop's "why".
        val book = names + listOf("Kevin Traders", "Wajih Ahmad").mapIndexed { i, n -> Hudhud.Name(50L + i, n) }
        fun readIn(said: String) = Hudhud.understand(said, book, HudhudRealPages.forApp("ur"), "ur", goods, null)
        assertEquals(why(WhyOf.SALES), readIn("اس مہینے سیل کم کیوں ہوئی"))
        assertEquals(why(WhyOf.SALES), readIn("سیل کم ہونے کی وجہ کیا ہے"))
        assertFalse(readIn("کیوں") == Ask.Parties(listOf(50L)))
    }

    @Test fun `why in Bengali is no customer's name (BC-110)`() {
        // "কেন" bridged to the Latin "Khan" and answered with Naeem Khan's balance.
        assertNotEquals(Ask.Parties(listOf(4L)), read("bn", "কেন"))
        assertTrue(HudhudWhyAsk.isWhyWord(VoiceQuestion.normalise("কেন")))
        // Names said across scripts are still met (HudhudNameBridgeTest holds those); only the words for "why" are set aside.
    }

    // ------------------------------------------------------------------------------------------------ the two spans

    private val tz = TimeZone.getTimeZone("Asia/Karachi")
    private fun at(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0): Long =
        Calendar.getInstance(tz).apply { clear(); set(y, m - 1, d, h, min) }.timeInMillis

    @Test fun `a running span is set beside the same length of the span before`() {
        val now = at(2026, 11, 16, 15, 30)
        val s = HudhudWhyAsk.spans(Window.THIS_MONTH, now, tz)!!
        assertTrue(s.soFar)
        assertEquals(at(2026, 11, 1), s.now.first)
        assertEquals(now + 1, s.now.second)
        assertEquals(at(2026, 10, 1), s.before.first)
        assertEquals(at(2026, 10, 16, 15, 30) + 1, s.before.second)
        val t = HudhudWhyAsk.spans(Window.TODAY, now, tz)!!
        assertEquals(at(2026, 11, 15) to (at(2026, 11, 15, 15, 30) + 1), t.before)
    }

    @Test fun `a span already over is set beside the whole span before it`() {
        val now = at(2026, 11, 16, 15, 30)
        val s = HudhudWhyAsk.spans(Window.LAST_MONTH, now, tz)!!
        assertFalse(s.soFar)
        assertEquals(at(2026, 10, 1) to at(2026, 11, 1), s.now)
        assertEquals(at(2026, 9, 1) to at(2026, 10, 1), s.before)
        assertEquals(at(2026, 11, 14) to at(2026, 11, 15), HudhudWhyAsk.spans(Window.YESTERDAY, now, tz)!!.before)
        // March against February: the cut never runs into the month asked.
        val march = HudhudWhyAsk.spans(Window.THIS_MONTH, at(2026, 3, 31, 12), tz)!!
        assertEquals(at(2026, 3, 1), march.before.second)
        assertNull(HudhudWhyAsk.spans(Window.ALL_TIME, now, tz))
    }

    // ------------------------------------------------------------------------------------------------ what moved

    @Test fun `the parts that moved the way the whole moved, biggest first`() {
        val now = mapOf("Ali" to 1000.0, "Bashir" to 5000.0, "Tariq" to 300.0)
        val before = mapOf("Ali" to 4000.0, "Bashir" to 4000.0, "Naeem" to 2000.0)
        val parts = HudhudWhyAsk.movers(now, before, whole = -5700.0)
        assertEquals(listOf("Ali", "Naeem"), parts.map { it.name })
        assertEquals(-3000.0, parts[0].change, 1e-9)
        // It rose: only the rises, and nothing when nothing moved.
        assertEquals(listOf("Bashir", "Tariq"), HudhudWhyAsk.movers(now, before, whole = 100.0).map { it.name })
        assertTrue(HudhudWhyAsk.movers(now, before, whole = 0.0).isEmpty())
        assertEquals(1, HudhudWhyAsk.movers(now, before, whole = -1.0, limit = 1).size)
        assertEquals(mapOf("Urea" to 30.0, "DAP" to 5.0), HudhudWhyAsk.summed(listOf("Urea" to 10.0, "DAP" to 5.0, "Urea" to 20.0)))
    }
}
