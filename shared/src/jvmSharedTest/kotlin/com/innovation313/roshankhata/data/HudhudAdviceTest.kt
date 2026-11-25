package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.Hudhud.Ask
import com.innovation313.roshankhata.data.Hudhud.AdviseOf
import com.innovation313.roshankhata.data.HudhudAdviceRules.Way
import com.innovation313.roshankhata.data.VoiceQuestion.Window
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hudhud the business advisor (25 Nov 2026, plan H5, first part): "is my sale going up or down", "whom should I chase first", "what
 * will come in the next 30 days", read by rules ([HudhudAdviceAsk]) and never guessed, through the whole pipeline with the shipped
 * sense (lesson 145); the pure rules ([HudhudAdviceRules]) held here too; each guard alone (lesson 129). Names and goods are invented.
 */
class HudhudAdviceTest {

    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val names = listOf("Ali Raza", "Bashir Ahmad", "Tariq Butt", "Naeem Khan").mapIndexed { i, n -> Hudhud.Name(i + 1L, n) }
    private val goods = listOf("Urea", "DAP", "Sugar").mapIndexed { i, n -> Hudhud.Name(100L + i, n) }

    private fun read(lang: String, said: String, before: Ask? = null) =
        Hudhud.understand(said, names, HudhudRealPages.forApp(lang), lang, goods, before)

    private val chase = Ask.Advise(AdviseOf.CHASE)
    private val next30 = Ask.Advise(AdviseOf.NEXT_30)
    private fun trend(w: Window = Window.THIS_MONTH) = Ask.Trend(w)

    // ------------------------------------------------------------------------------------------------ the three readings

    @Test fun `is my sale going up or down, in the nine languages`() {
        for ((lang, said) in listOf(
            "ur-Latn" to "kya meri sale barh rahi hai ya kam ho rahi hai", "en" to "is my sale going up or down",
            "ur" to "کیا میری سیل بڑھ رہی ہے یا کم ہو رہی ہے", "hi" to "क्या मेरी बिक्री बढ़ रही है या घट रही है",
            "bn" to "আমার বিক্রি কি বাড়ছে নাকি কমছে", "ar" to "هل مبيعاتي ترتفع أم تنخفض",
            "fa" to "آیا فروش من در حال افزایش است یا کاهش", "sd" to "ڇا منهنجو وڪرو وڌي رهيو آهي يا گهٽجي رهيو آهي",
            "in" to "apakah penjualan saya naik atau turun"
        )) assertEquals("[$lang] $said", trend(), read(lang, said))
    }

    @Test fun `a way alone is a trend, and the span said is the span compared`() {
        assertEquals(trend(), read("ur-Latn", "meri sale barh rahi hai"))
        assertEquals(trend(), read("en", "how are my sales trending"))
        assertEquals(trend(Window.THIS_WEEK), read("ur-Latn", "is hafte sale ka trend"))
        assertEquals(trend(Window.THIS_WEEK), read("en", "are sales going down this week"))
        assertEquals(trend(Window.LAST_MONTH), read("ur-Latn", "pichle mahine sale barh rahi thi ya kam"))
        assertEquals(trend(Window.LAST_WEEK), read("en", "sales trend last week"))
        assertEquals(trend(Window.TODAY), read("ur-Latn", "aaj sale barh rahi hai"))
    }

    @Test fun `whom should I chase first, in the nine languages`() {
        for ((lang, said) in listOf(
            "ur-Latn" to "pehle kis se wusooli karun", "en" to "whom should I chase first",
            "ur" to "پہلے کس سے وصولی کروں", "hi" to "पहले किससे वसूली करूं", "bn" to "আগে কার কাছ থেকে আদায় করব",
            "ar" to "من أطالب أولًا", "fa" to "اول از چه کسی وصول کنم", "sd" to "پهرين ڪنهن کان وصولي ڪريان",
            "in" to "tagih siapa dulu"
        )) assertEquals("[$lang] $said", chase, read(lang, said))
    }

    @Test fun `what will come in the next 30 days, in the nine languages`() {
        for ((lang, said) in listOf(
            "ur-Latn" to "agle 30 din mein kitna paisa aayega", "en" to "how much money will come in over the next 30 days",
            "ur" to "اگلے 30 دن میں کتنے پیسے آئیں گے", "hi" to "अगले 30 दिनों में कितने पैसे आएंगे",
            "bn" to "আগামী 30 দিনে কত টাকা আসবে", "ar" to "كم المبلغ المتوقع خلال 30 يومًا القادمة",
            "fa" to "در 30 روز آینده چقدر پول می‌آید", "sd" to "ايندڙ 30 ڏينهن ۾ ڪيترا پيسا ايندا",
            "in" to "berapa uang masuk dalam 30 hari ke depan"
        )) assertEquals("[$lang] $said", next30, read(lang, said))
    }

    // ------------------------------------------------------------------------------------------------ what each guard alone refuses

    @Test fun `the trend needs the sales and a way, and nothing else in the sentence`() {
        val none: (String) -> Boolean = { false }
        // No word of the sales: a way alone is anybody's.
        assertNull(HudhudAdviceAsk.trend("kya barh rahi hai", none, none))
        // The sales and no way: a figure, not a trend.
        assertNull(HudhudAdviceAsk.trend("is mahine ki sale", none, none))
        // A word of another kind: not this reader's.
        assertNull(HudhudAdviceAsk.trend("sale barh rahi hai lekin cricket", none, none))
        // A name of the book: it is his sale, not the shop's.
        assertNull(HudhudAdviceAsk.trend("Ali ki meri sale barh rahi hai", { it == "ali" }, none))
        // A word that only calls him is skipped.
        assertEquals(trend(), HudhudAdviceAsk.trend("hudhud meri sale barh rahi hai", none, { it == "hudhud" }))
        // A span he has no window for, and the whole book (nothing before it).
        assertNull(HudhudAdviceAsk.trend("pichle 3 mahine ki sale barh rahi hai", none, none))
        assertNull(HudhudAdviceAsk.trend("shuru se ab tak sale barh rahi hai", none, none))
        // More words than a sentence of these has.
        assertNull(HudhudAdviceAsk.trend("sale barh rahi hai ya kam ho rahi hai kya meri is hafte ki aap bata sakte hain mujhe kya", none, none))
    }

    @Test fun `the trend needs my, a span or the word trend, and a bare up or down is the sales page's question`() {
        val none: (String) -> Boolean = { false }
        // The owner's own list reads these as the sales page's help: no "my", no span, no "trend".
        assertNull(HudhudAdviceAsk.trend("sale gir rahi hai ya barh rahi hai", none, none))
        assertNull(HudhudAdviceAsk.trend("sales going up", none, none))
        assertEquals(trend(), HudhudAdviceAsk.trend("meri sale gir rahi hai ya barh rahi hai", none, none))
        assertEquals(trend(Window.THIS_WEEK), HudhudAdviceAsk.trend("is hafte sale barh rahi hai", none, none))
        assertEquals(trend(), HudhudAdviceAsk.trend("sale ka trend", none, none))
    }

    @Test fun `a word that is also little or how much says no way by itself`() {
        val none: (String) -> Boolean = { false }
        // "kam" is little; in Arabic letters "كم" is how much; a comparative is not a movement.
        assertNull(HudhudAdviceAsk.trend("aaj bohat kam bikri hui kya", none, none))
        assertNull(HudhudAdviceAsk.trend("كم المبيعات اليوم", none, none))
        assertNull(HudhudAdviceAsk.trend("sab se zyada sale kis item ki hai", none, none))
        assertNull(HudhudAdviceAsk.trend("سڀ کان وڌيڪ ڇا وڪرو ٿي رهيو آهي", none, none))
        // Beside a word of motion or "trend" it is a way.
        assertEquals(trend(), HudhudAdviceAsk.trend("meri sale kam ho rahi hai", none, none))
        assertEquals(trend(), HudhudAdviceAsk.trend("sale kam trend", none, none))
    }

    @Test fun `a span with no window of its own is never read as another one`() {
        val none: (String) -> Boolean = { false }
        // Each of these says only words the reader knows, so the span's own guard is the only thing that can refuse it.
        for (s in listOf("sales going up the day before yesterday", "sales going up last quarter", "sales going up this fiscal year",
            "sales going up a year ago", "sales going up next week", "sales trend next month", "sale barh rahi hai parson",
            "sale barh rahi hai agle hafte", "sale barh rahi hai pichle saal"))
            if (VoiceQuestion.unsupportedSpan(s)) assertNull(s, HudhudAdviceAsk.trend(s, none, none))
        assertTrue(VoiceQuestion.unsupportedSpan("sales going up next month"))
        assertNull(HudhudAdviceAsk.trend("sales going up next month", none, none))
    }

    @Test fun `a name of the book is never part of the question, even when it is also a little word`() {
        // "Kafi" is a little word of the language and a shop's name: the book's own name wins.
        assertEquals(trend(), HudhudAdviceAsk.trend("kafi meri sale barh rahi hai", { false }, { false }))
        assertNull(HudhudAdviceAsk.trend("kafi meri sale barh rahi hai", { it == "kafi" }, { false }))
        assertEquals(chase, HudhudAdviceAsk.chase("kafi pehle kis se wusooli karun", { false }, { false }))
        assertNull(HudhudAdviceAsk.chase("kafi pehle kis se wusooli karun", { it == "kafi" }, { false }))
        assertNull(HudhudAdviceAsk.nextDays("kafi agle 30 din kitna aayega", { it == "kafi" }, { false }))
    }

    @Test fun `a sentence is a question of a few words, not a paragraph`() {
        val none: (String) -> Boolean = { false }
        val fourteen = "meri sale barh rahi hai ya sale barh rahi hai ya sale barh rahi"
        assertEquals(14, fourteen.split(' ').size)
        assertEquals(trend(), HudhudAdviceAsk.trend(fourteen, none, none))
        assertNull(HudhudAdviceAsk.trend("$fourteen hai", none, none))
    }

    @Test fun `the chase needs a word of collecting and first`() {
        val none: (String) -> Boolean = { false }
        assertNull(HudhudAdviceAsk.chase("kis se wusooli karun", none, none))
        assertNull(HudhudAdviceAsk.chase("pehle kis se baat karun", none, none))
        assertNull(HudhudAdviceAsk.chase("pehle Ali se wusooli karun", { it == "ali" }, none))
        assertNull(HudhudAdviceAsk.chase("pehle kis se wusooli karun cricket", none, none))
        assertEquals(chase, HudhudAdviceAsk.chase("sab se pehle kis se wusooli karun", none, none))
        // First and whom, but nothing about collecting: not this reader's.
        assertNull(HudhudAdviceAsk.chase("pehle kis se paise", none, none))
        assertNull(HudhudAdviceAsk.chase("who first", none, none))
        // A reminder is Follow-up's "whom to remind", the not-paid list: not this reader's.
        assertNull(HudhudAdviceAsk.chase("kis ko pehle yaad dilana chahiye", none, none))
        assertNull(HudhudAdviceAsk.chase("whom should I remind first", none, none))
    }

    @Test fun `the next 30 days needs something coming, next and the thirty`() {
        val none: (String) -> Boolean = { false }
        assertNull(HudhudAdviceAsk.nextDays("agle 30 din", none, none))
        assertNull(HudhudAdviceAsk.nextDays("30 din mein kitna aayega", none, none))
        assertNull(HudhudAdviceAsk.nextDays("agle din kitna aayega", none, none))
        // "How much will come" is anything that comes: the thirty days need a word of money.
        assertNull(HudhudAdviceAsk.nextDays("agle 30 din mein kitna aayega", none, none))
        assertEquals(next30, HudhudAdviceAsk.nextDays("agle 30 din mein kitna paisa aayega", none, none))
        // Another number of days is another span: not answered as the thirty.
        assertNull(HudhudAdviceAsk.nextDays("agle 60 din mein kitna paisa aayega", none, none))
        assertNull(HudhudAdviceAsk.nextDays("agle 7 din mein kitna paisa aayega", none, none))
        assertNull(HudhudAdviceAsk.nextDays("agle 30 din mein Ali se kitna aayega", { it == "ali" }, none))
        assertNull(HudhudAdviceAsk.nextDays("agle 30 din mein kitna paisa aayega cricket", none, none))
        // Money needed is money going out.
        assertNull(HudhudAdviceAsk.nextDays("agle mahine kitna cash chahiye", none, none))
        assertNull(HudhudAdviceAsk.nextDays("how much cash do I need over the next 30 days", none, none))
        assertNull(HudhudAdviceAsk.nextDays("كم مبلغ احتاج خلال 30 يومًا القادمة", none, none))
        // "Next month" says the month, and the thirty days are the month's own words.
        assertEquals(next30, HudhudAdviceAsk.nextDays("agle mahine kitna paisa aayega 30 din", none, none))
    }

    @Test fun `a why stays the why, and the questions beside them stay as they were`() {
        assertEquals(Ask.Why(Hudhud.WhyOf.SALES, Window.THIS_MONTH, true), read("ur-Latn", "is mahine sale kam kyun hui"))
        assertEquals(Ask.Figure(VoiceQuestion.Kind.SALES_MONTH), read("ur-Latn", "is mahine ki sale"))
        assertEquals(Ask.Figure(VoiceQuestion.Kind.SALES_TODAY), read("ur-Latn", "aaj ki sale"))
        assertEquals(Ask.Figure(VoiceQuestion.Kind.NOT_PAID), read("en", "list of people I need to chase for money"))
        assertFalse(read("ur-Latn", "Ali Raza ki sale barh rahi hai") is Ask.Trend)
        assertFalse(read("en", "how much does Ali Raza owe me") is Ask.Advise)
        // A customer's own question about when he pays is the Radar's.
        assertTrue(read("ur-Latn", "Ali Raza kab dega") is Ask.WhenPays)
    }

    // ------------------------------------------------------------------------------------------------ carried on

    @Test fun `and Ali, and the other span, after the sales or their trend`() {
        val ali = listOf(1L)
        assertEquals(Ask.Moved(ali, Window.THIS_MONTH, false), read("ur-Latn", "aur Ali Raza", trend()))
        assertEquals(Ask.Moved(ali, Window.THIS_WEEK, false), read("en", "and Ali Raza", trend(Window.THIS_WEEK)))
        assertEquals(Ask.Moved(ali, Window.THIS_MONTH, false), read("ur-Latn", "aur Ali Raza", Ask.Figure(VoiceQuestion.Kind.SALES_MONTH)))
        assertEquals(Ask.Moved(ali, Window.TODAY, false), read("en", "and Ali Raza", Ask.Figure(VoiceQuestion.Kind.SALES_TODAY)))
        assertEquals(Ask.Moved(ali, Window.LAST_MONTH, false), read("ur-Latn", "aur Ali Raza pichle mahine", trend()))
        assertEquals(Ask.Moved(ali, Window.TODAY, true), read("en", "and Ali Raza", Ask.Collected(null)))
        // The span of the last answer carries to a bare span: the trend of last week.
        assertEquals(trend(Window.LAST_WEEK), read("en", "and last week", trend()))
        assertEquals(trend(Window.LAST_MONTH), read("ur-Latn", "aur pichle mahine", trend()))
    }

    // ------------------------------------------------------------------------------------------------ the pure rules

    @Test fun `the verdict by the shop's own coin`() {
        assertEquals(Way.UP, HudhudAdviceRules.verdict(1200.0, 1000.0).way)
        assertEquals(20.0, HudhudAdviceRules.verdict(1200.0, 1000.0).percent!!, 1e-9)
        assertEquals(Way.DOWN, HudhudAdviceRules.verdict(900.0, 1000.0).way)
        assertEquals(-10.0, HudhudAdviceRules.verdict(900.0, 1000.0).percent!!, 1e-9)
        assertEquals(Way.FLAT, HudhudAdviceRules.verdict(1000.0, 1000.0).way)
        // Under one percent is about the same, with the change still said.
        val near = HudhudAdviceRules.verdict(1005.0, 1000.0)
        assertEquals(Way.FLAT, near.way)
        assertEquals(5.0, near.change, 1e-9)
        // Exactly one percent is a move.
        assertEquals(Way.UP, HudhudAdviceRules.verdict(1010.0, 1000.0).way)
        assertEquals(Way.DOWN, HudhudAdviceRules.verdict(990.0, 1000.0).way)
        // Nothing before: up with no percentage, never a division by nothing.
        val fresh = HudhudAdviceRules.verdict(500.0, 0.0)
        assertEquals(Way.UP, fresh.way)
        assertNull(fresh.percent)
        // Nothing now and something before: down, all of it.
        assertEquals(-100.0, HudhudAdviceRules.verdict(0.0, 400.0).percent!!, 1e-9)
        assertEquals(Way.DOWN, HudhudAdviceRules.verdict(0.0, 400.0).way)
        // Nothing in either.
        assertEquals(Way.NONE, HudhudAdviceRules.verdict(0.0, 0.0).way)
    }

    private fun who(id: Long, name: String, balance: Double, bucket: Int, light: Int) = HudhudAdviceRules.Candidate(id, name, balance, bucket, light)

    @Test fun `the chase asks the oldest first, then the lateness, then the amount, then the name`() {
        val rows = listOf(
            who(1, "Young but big", 90000.0, 0, 3),
            who(2, "Old red", 4000.0, 3, 3),
            who(3, "Old green", 9000.0, 3, 1),
            who(4, "Mid", 7000.0, 2, 2),
            who(5, "Old green big", 12000.0, 3, 1),
            who(6, "Zed", 4000.0, 3, 3),
            who(7, "Abe", 4000.0, 3, 3)
        )
        // Bucket 3 first (red before green, a bigger amount first, a tie by name), then the middle, then the young.
        assertEquals(listOf(7L, 2L, 6L, 5L, 3L, 4L, 1L), rows.sortedWith(HudhudAdviceRules.CHASE_ORDER).map { it.id })
        assertEquals(listOf(7L, 2L, 6L, 5L, 3L, 4L, 1L), rows.reversed().sortedWith(HudhudAdviceRules.CHASE_ORDER).map { it.id })
    }

    @Test fun `the Today card tells two facts at most, a promise first`() {
        assertEquals(emptyList<String>(), HudhudAdviceRules.interleave(emptyList(), emptyList()))
        assertEquals(listOf("p1"), HudhudAdviceRules.interleave(listOf("p1"), emptyList()))
        assertEquals(listOf("p1", "s1"), HudhudAdviceRules.interleave(listOf("p1", "p2"), listOf("s1", "s2")))
        assertEquals(listOf("p1", "p2"), HudhudAdviceRules.interleave(listOf("p1", "p2", "p3"), emptyList()))
        assertEquals(listOf("s1", "s2"), HudhudAdviceRules.interleave(emptyList(), listOf("s1", "s2", "s3")))
        assertEquals(listOf("p1", "s1"), HudhudAdviceRules.interleave(listOf("p1"), listOf("s1", "s2")))
        assertEquals(2, HudhudAdviceRules.interleave(listOf("a", "b", "c"), listOf("d", "e", "f"), HudhudAdviceRules.TODAY_MAX).size)
    }
}
