package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.Hudhud.Ask
import com.innovation313.roshankhata.data.Hudhud.GoodsFacet
import com.innovation313.roshankhata.data.Hudhud.Thing
import com.innovation313.roshankhata.data.VoiceQuestion.Kind
import com.innovation313.roshankhata.data.VoiceQuestion.Window
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 15 Nov 2026, a read-only review of 1,115 everyday sentences in nine languages (who paid, where the money went, what sells,
 * what a product costs): the classes it found, each held through the whole pipeline ([Hudhud.understand] with the shipped sense
 * and a topic list), and — where another layer would refuse the same sentence anyway (lesson 129) — through the reader alone.
 * Names and products are invented (lesson 80); the sentences are the tests' maker's, not a native reader's (Sindhi, Bengali and
 * Farsi want one).
 */
class HudhudReviewAnswersTest {
    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val names = listOf(Hudhud.Name(1L, "Ali Raza"), Hudhud.Name(2L, "Bashir Ahmad"))
    private val goods = listOf(Hudhud.Name(100L, "Urea"), Hudhud.Name(101L, "DAP"))
    private val topics = listOf("intro_cashbook", "screen_rates", "screen_aging", "intro_documents").map { Hudhud.Topic(it, it, it) }

    private fun read(s: String, language: String? = null, before: Ask? = null) =
        Hudhud.understand(s, names, topics, language, goods = goods, before = before)

    private fun all(expected: Ask, vararg said: String) {
        for (s in said) assertEquals(s, expected, read(s))
    }

    private fun figure(kind: Kind) = Ask.Figure(kind)

    // ------------------------------------------------------------------------------------------------- A: who paid

    @Test fun `who paid is the money collected and never the unpaid list`() {
        all(Ask.Collected(Window.TODAY), "kis ne aaj paise diye", "aaj kis kis ne paise diye", "آج کس نے پیسے دیے", "من دفع اليوم", "امروز چه کسی پول داد",
            "اڄ ڪنهن پئسا ڏنا", "আজ কে টাকা দিয়েছে")
        all(Ask.Collected(Window.YESTERDAY), "kal kis ne paise diye", "من دفع أمس", "دیروز چه کسی پول داد", "ڪالهه ڪنهن پئسا ڏنا", "siapa yang bayar kemarin")
    }

    @Test fun `a past verb says which kal it is`() {
        assertEquals(Ask.Collected(Window.YESTERDAY), read("کل کس نے پیسے دیے"))
    }

    @Test fun `who does not pay, as a habit, is the unpaid list`() {
        all(figure(Kind.NOT_PAID), "kaun nahi deta", "کون نہیں دیتا", "कौन नहीं देता", "who never pays", "من لا يدفع أبدا", "کی هیچوقت نمی‌دهد", "ڪير نه ڏيندو آهي",
            "siapa yang tidak pernah bayar", "siapa yang tidak bayar")
    }

    @Test fun `a question of when is the radar's, not the unpaid list`() {
        assertTrue(read("kab dega") !is Ask.Figure)
    }

    // only the reader's own negation guard can refuse here: "who paid" with a "not" would be the opposite question
    @Test fun `the reader alone does not read a denied payment as money collected`() {
        assertTrue(HudhudEveryday.read("kis ne aaj paise nahi diye", Window.TODAY) !is Ask.Collected)
        assertEquals(figure(Kind.NOT_PAID), HudhudEveryday.read("kis ne paise nahi diye", null))
        assertEquals(Ask.Collected(Window.TODAY), HudhudEveryday.read("kis ne aaj paise diye", Window.TODAY))
    }

    @Test fun `the shop's own paying is not a payment that came in`() {
        assertTrue(HudhudEveryday.read("maine kis ko paise diye", null) !is Ask.Collected)
    }

    // ------------------------------------------------------------------------------------------------ H: least

    @Test fun `the least owed stays the least owed in nine languages`() {
        all(figure(Kind.WHO_OWES_LEAST), "sab se kam udhaar kis ka hai", "sab se chhota udhaar kis ka hai", "سب سے کم ادھار کس کا ہے", "सबसे कम उधार किसका है",
            "whose balance is the smallest", "من عليه أقل دين", "کمترین بدهکار کیست", "سڀ کان گهٽ اڌار ڪنهن جو آهي", "utang siapa yang paling sedikit",
            "siapa yang utangnya paling sedikit")
    }

    @Test fun `the most owed is still the most owed`() {
        assertEquals(figure(Kind.WHO_OWES_MOST), read("sab se zyada udhaar kis ka hai"))
    }

    // ----------------------------------------------------------------------------------------- B: where the money is

    @Test fun `where the money went is the cashbook's money out`() {
        all(Ask.Cashbook(null), "paise kahan gaye", "پیسے کہاں گئے", "पैसे कहाँ गए", "where did the money go", "টাকা কোথায় গেল", "أين ذهبت الأموال", "پول‌ها کجا رفت",
            "پئسا ڪيڏانهن ويا", "uang saya ke mana")
    }

    @Test fun `where my money is means what is owed to the shop`() {
        all(figure(Kind.TO_GET), "mere paise kahan hain", "میرے پیسے کہاں ہیں", "मेरे पैसे कहाँ हैं", "where is my money", "أين أموالي", "پول من کجاست", "منهنجا پئسا ڪٿي آهن",
            "uang saya di mana")
    }

    @Test fun `what is in hand is the drawer`() {
        all(Ask.Drawer, "kitna paisa hai mere paas", "كم معي من النقود")
    }

    // -------------------------------------------------------------------------------------------- E: money going out

    @Test fun `money that left is the cashbook over the day asked`() {
        all(Ask.Cashbook(Window.TODAY), "aaj kitne paise nikle", "آج کتنے پیسے نکلے", "आज कितने पैसे निकले", "how much did I spend today", "كم صرفت اليوم", "امروز چقدر پول خارج شد",
            "اڄ ڪيترا پئسا نڪتا", "hari ini habis berapa", "aaj paise kitne aaye kitne gaye", "আজ ক্যাশে কত এলো আর গেল")
    }

    @Test fun `goods that ran out stay the stock`() {
        assertTrue(read("Urea habis berapa") !is Ask.Cashbook)
    }

    // ---------------------------------------------------------------------------------------------- C: parts of a day

    @Test fun `up to now from morning is the day's figure, said plainly`() {
        all(figure(Kind.SALES_TODAY), "subah se ab tak kitna bika", "صبح سے اب تک کتنا بکا", "सुबह से अब तक कितना बिका", "how much did we sell since morning", "كم بعنا منذ الصباح",
            "dari pagi sampai sekarang laku berapa", "সকাল থেকে এখন পর্যন্ত কত বিক্রি")
    }

    @Test fun `since morning in Farsi, with the app's language`() {
        val said = "از صبح تا حالا چقدر فروختیم"
        assertEquals(figure(Kind.SALES_TODAY), Hudhud.understand(said, names, HudhudRealPages.forApp("fa"), "fa", goods = goods))
    }

    @Test fun `a part of the day is told as a guess because the book keeps no hour`() {
        for (s in listOf("how much did we sell this morning", "shaam tak kitna bika", "كم بعنا هذا الصباح", "dopahar tak ki sale")) {
            val ask = read(s)
            assertTrue("$s -> $ask", ask is Ask.Guessed && ask.ask == figure(Kind.SALES_TODAY))
        }
    }

    @Test fun `a greeting that names a part of the day asks nothing about it`() {
        assertEquals(figure(Kind.SALES_TODAY), read("good morning how much did we sell today", "en"))
        assertNull(VoiceQuestion.partOfDay("good evening today sales"))
        assertTrue(VoiceQuestion.partOfDay("evening ki sale") != null)
    }

    @Test fun `a span the book cannot place is a limit, not a month or the whole book`() {
        for (s in listOf("Ramzan mein kitna bika", "Eid ke baad ki bikri kitni hui", "Eid se pehle kitna bika", "sales since Eid")) {
            assertTrue("$s -> ${read(s)}", read(s) is Ask.SpanLimit)
        }
    }

    // ------------------------------------------------------------------------------------------- D: what sells best

    @Test fun `the goods that sell best, said as a shop says it`() {
        all(Ask.TopGoods(null), "kaun sa maal zyada chalta hai", "sab se zyada bikne wali cheez", "kya cheez chal rahi hai aajkal", "سب سے زیادہ کیا بک رہا ہے",
            "सबसे ज़्यादा क्या बिक रहा है", "এখন কোন জিনিস চলছে", "أي بضاعة تباع الآن", "الان چه چیزی خوب می‌فروشد", "barang apa yang lagi laris")
        all(Ask.TopGoods(Window.THIS_MONTH), "which item sells best this month", "این ماه کدام کالا بیشتر فروش رفت")
    }

    @Test fun `a sales figure is not the best-selling goods`() {
        assertEquals(figure(Kind.SALES_TODAY), read("aaj kitna bika"))
    }

    // --------------------------------------------------------------------------------- I: who will give me, G: debts

    @Test fun `who will give me is who owes, never who am I`() {
        all(figure(Kind.WHO_OWES_MOST), "kaun kaun mujhe dega", "کون کون مجھے دے گا", "कौन कौन मुझे देगा", "چه کسانی به من می‌دهند", "ڪير ڪير مون کي ڏيندو",
            "কে কে আমাকে দেবে", "من الذي سيدفع لي")
    }

    @Test fun `debt idioms are the unpaid list`() {
        all(figure(Kind.NOT_PAID), "kis ka udhaar nahi aaya", "کس کا ادھار نہیں آیا", "किसका उधार नहीं आया", "কার বাকি আসেনি", "پول چه کسی نیامده", "ڪنهن جو اڌار نه آيو",
            "uang siapa yang belum masuk", "kis ki raqam atki hui hai", "kisne abhi tak hisaab saaf nahi kiya", "who hasn't cleared their account",
            "किसका पैसा फँसा हुआ है", "من الذي أمواله عالقة", "پول چه کسی گیر کرده", "কে এখনো হিসাব মেটায়নি", "siapa yang belum melunasi", "kis ki wusooli baqi hai")
    }

    @Test fun `a debt word alone is what the shop is owed`() {
        all(figure(Kind.TO_GET), "baqaya", "baqi", "udhaar")
    }

    @Test fun `in debt myself is what the shop owes`() {
        assertEquals(figure(Kind.TO_GIVE), read("main kitna qarzdar hun"))
    }

    // only the reader's own guards refuse these: another layer would read "paid"/"not paid" words from the same sentence
    @Test fun `the reader alone keeps goods out of the unpaid idioms`() {
        assertNull(HudhudEveryday.read("kaun sa maal mein paisa phansa hua hai", null))
        assertEquals(figure(Kind.NOT_PAID), HudhudEveryday.read("kis ka paisa phansa hua hai", null))
    }

    @Test fun `the reader alone keeps a name and a denied debt out of the idioms`() {
        assertNull(HudhudEveryday.read("Ali Raza ka udhaar nahi aaya", null, knows = { it == "ali" || it == "raza" }))
        assertNull(HudhudEveryday.read("kis ka udhaar aaya", null))
        assertNull(HudhudEveryday.read("kis ka udhaar nahi aaya kab", null))
    }

    // ----------------------------------------------------------------------------------------------- F: a product's shape

    @Test fun `a product's price in the shapes shops use`() {
        for (s in listOf("Urea kitne ka hai", "bhai Urea kitne ki hai", "Urea kitne mein dete ho", "Urea ki bori kitne ki hai", "Urea کتنے کی ہے", "Urea कितने की है",
            "how much is Urea", "what do you charge for Urea", "بكم Urea", "Urea چند است", "Urea ڪيترين جي آهي", "Urea কত করে", "Urea berapaan")) {
            val ask = read(s)
            assertTrue("$s -> $ask", ask is Ask.Goods && ask.facet == GoodsFacet.PRICE && 100L in ask.ids)
        }
    }

    @Test fun `what a product cost is its cost, not its price`() {
        for (s in listOf("Urea kitne ki aati hai", "Urea mujhe kitne ki parti hai", "what did I buy Urea for")) {
            val ask = read(s)
            assertTrue("$s -> $ask", ask is Ask.Goods && ask.facet == GoodsFacet.COST)
        }
    }

    @Test fun `what is left of a product in the shapes shops use`() {
        for (s in listOf("Urea hai ya nahi", "Urea kam hai kya", "Urea کم ہے کیا", "Urea कम है क्या", "is Urea running low", "كم بقي من Urea", "Urea گهٽ آهي ڇا", "Urea কম আছে কি")) {
            val ask = read(s)
            assertTrue("$s -> $ask", ask is Ask.Goods && ask.facet == GoodsFacet.STOCK)
        }
    }

    @Test fun `the last delivery and who bought it`() {
        val last = read("when did Urea last come in")
        assertTrue("$last", last is Ask.Goods && last.facet == GoodsFacet.LAST_IN)
        val buyers = read("Urea ڪنهن ورتي")
        assertTrue("$buyers", buyers is Ask.Goods && buyers.facet == GoodsFacet.BUYERS)
    }

    @Test fun `a plain name still gives the overview and a sold question stays sold`() {
        val plain = read("Urea")
        assertTrue("$plain", plain is Ask.Goods && plain.facet == GoodsFacet.OVERVIEW)
        val sold = read("Urea kitni bik gayi")
        assertTrue("$sold", sold is Ask.Goods && sold.facet == GoodsFacet.SOLD)
    }

    // ------------------------------------------------------------------------------------------------------- L: counting

    @Test fun `how many people work here is the staff count`() {
        all(Ask.Count(Thing.STAFF), "kitne banday kaam karte hain", "کتنے بندے کام کرتے ہیں", "कितने लोग काम करते हैं", "ڪيترا ماڻهو ڪم ڪن ٿا")
    }

    @Test fun `how many came to buy is the day's tickets`() {
        assertEquals(Ask.Count(Thing.TICKETS, Window.TODAY), read("aaj kitne log kharidne aaye"))
        assertEquals(Ask.Count(Thing.TICKETS, Window.TODAY), read("how many sales did we make today"))
    }

    // ------------------------------------------------------------------------------------------------------------------
    // The guards below are each the only thing that refuses their sentence: the reader is called alone, with a sentence in no other
    // layer's words (lesson 129), and each was broken on a private copy and had to fail one of these.
    // ------------------------------------------------------------------------------------------------------------------

    private fun alone(s: String, window: Window? = null) = HudhudEveryday.read(s, window)

    @Test fun `the shop paying out is not a payment that came in, nor a payer who did not`() {
        assertTrue(alone("siapa yang saya bayar kemarin", Window.YESTERDAY) !is Ask.Collected)
        assertTrue(alone("kaun hai jisko maine paise nahi diye") != figure(Kind.NOT_PAID))
    }

    @Test fun `a question of when somebody paid is not the list of who paid`() {
        assertTrue(alone("kis ne kab paise diye") !is Ask.Collected)
    }

    @Test fun `a bare Indonesian bayar needs a day that is past, and today's is the radar's`() {
        assertTrue(alone("siapa yang bayar") !is Ask.Collected)
        assertTrue(alone("siapa yang bayar hari ini", Window.TODAY) !is Ask.Collected)
        assertEquals(Ask.Collected(Window.YESTERDAY), alone("siapa yang bayar kemarin", Window.YESTERDAY))
    }

    @Test fun `a denied where-it-went is no cashbook question`() {
        assertNull(alone("paise kahan nahi gaye"))
        assertEquals(Ask.Cashbook(null), alone("paise kahan gaye"))
    }

    @Test fun `what I have in hand is not the drawer when it is a debt`() {
        assertTrue(alone("mere paas kitna baqaya paisa hai") != Ask.Drawer)
        assertEquals(Ask.Drawer, alone("mere paas kitna paisa hai"))
    }

    @Test fun `money out needs money or a word of spending, and ran out needs money and a short sentence`() {
        assertNull(alone("aaj kitna maal nikla", Window.TODAY))
        assertNull(alone("berapa habis hari ini di toko saya", Window.TODAY))
        assertNull(alone("stok habis berapa"))
        assertEquals(Ask.Cashbook(Window.TODAY), alone("hari ini habis berapa", Window.TODAY))
    }

    @Test fun `the slowest, the figure and the money are not the best-selling goods`() {
        assertTrue(alone("kaun sa maal kam bikta hai") !is Ask.TopGoods)
        assertTrue(alone("sab se zyada paise ka maal bika") !is Ask.TopGoods)
        assertTrue(alone("kitna maal zyada bika") !is Ask.TopGoods)
        assertTrue(alone("kya kitna bika zyada") !is Ask.TopGoods)
        assertEquals(Ask.TopGoods(null), alone("kaun sa maal zyada bikta hai"))
    }

    @Test fun `who will not give me, and when, are not the list of who owes`() {
        assertTrue(alone("من لن يعطي لي") != figure(Kind.WHO_OWES_MOST))
        assertNull(alone("kaun kaun mujhe kab dega"))
        assertEquals(figure(Kind.WHO_OWES_MOST), alone("kaun kaun mujhe dega"))
    }

    @Test fun `stuck, not come and in debt need their own companions`() {
        assertNull(alone("kis ka paisa phansa nahi hai"))
        assertNull(alone("kis ka truck phansa hua hai"))
        assertNull(alone("kis ka parcel nahi aaya"))
        assertTrue(alone("kaun kitna qarzdar hai") != figure(Kind.TO_GIVE))
    }

    @Test fun `a price and a shelf asked together, or a facet already chosen, stay as they were`() {
        assertNull(HudhudEveryday.goodsFacet("Urea kitne ki hai aur kam hai kya", GoodsFacet.OVERVIEW))
        assertNull(HudhudEveryday.goodsFacet("Urea kitne ka hai", GoodsFacet.SOLD))
        assertNull(HudhudEveryday.goodsFacet("Urea kitne ka hai", GoodsFacet.SUPPLIERS))
        assertEquals(GoodsFacet.PRICE, HudhudEveryday.goodsFacet("Urea kitne ka hai", GoodsFacet.OVERVIEW))
        assertEquals(GoodsFacet.STOCK, HudhudEveryday.goodsFacet("Urea kam hai kya", GoodsFacet.OVERVIEW))
    }

    @Test fun `counting needs the people, the buying and a day`() {
        assertNull(alone("kitna kaam karte hain"))
        assertNull(alone("aaj kitne log nahi kharidne aaye", Window.TODAY))
        assertNull(alone("how many sales"))
        assertEquals(Ask.Count(Thing.TICKETS, Window.TODAY), alone("how many sales today", Window.TODAY))
    }

    @Test fun `a denied payment is not the radar's when-will-he-pay`() {
        assertTrue(read("Ali Raza nahi dega") !is Ask.WhenPays)
    }

    @Test fun `since morning and a morning greeting, in the span words`() {
        assertEquals(Kind.SALES_TODAY, VoiceQuestion.classify("sales since morning"))
        assertEquals(false, VoiceQuestion.partOfDay("subah se shaam tak kitna bika")!!.toNow)
        assertEquals(true, VoiceQuestion.partOfDay("subah se ab tak kitna bika")!!.toNow)
        assertNull(VoiceQuestion.windowOf("good morning how much did we sell"))
        assertEquals(Window.TODAY, VoiceQuestion.windowOf("how much did we sell since morning"))
    }

    @Test fun `a part of the day wraps a guess once`() {
        for (s in listOf("امروز صبح چقدر فروختیم", "this morning sales", "aaj subah kitna bika")) {
            val ask = Hudhud.understand(s, emptyList(), emptyList(), null)
            assertTrue("$s -> $ask", ask !is Ask.Guessed || ask.ask !is Ask.Guessed)
        }
    }
}
