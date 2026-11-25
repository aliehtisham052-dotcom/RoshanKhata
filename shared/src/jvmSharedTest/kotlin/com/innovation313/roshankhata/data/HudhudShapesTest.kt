package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.Hudhud.Ask
import com.innovation313.roshankhata.data.Hudhud.GoodsFacet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Four classes of 26 Nov 2026, each through [Hudhud.understand] with the shipped sense, as the app asks (lesson 145). Every name is
 * made up (lesson 80). The sentences are the test writer's, not a native reader's: the Sindhi, Bengali, Farsi and Arabic lines are
 * for a native read.
 *
 * - BC-354: "kal" in a sentence of the future is tomorrow, never yesterday's span ("Kal Ali ko 500 dunga" was told "as it stands now").
 * - BC-355: an Arabic word with the article joined meets the product saved in Latin letters ("كم سعر اليوريا" for "Urea").
 * - BC-356: a bare amount asked after ("500 ka kya bana?") asks which way first; never an entry, never the page its figure found.
 * - BC-357: "is there sugar" in Arabic or Farsi is the stock of a thing, never the privacy page the sound of "سكر" met.
 * - BC-358: "Ali ke 500 ka kya bana" is his account, never an entry opened in his name.
 */
class HudhudShapesTest {

    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val names = listOf(Hudhud.Name(1, "Ali Raza"), Hudhud.Name(2, "Bashir Ahmad"), Hudhud.Name(3, "Tariq Butt"))
    private val goods = listOf(Hudhud.Name(100, "Urea"), Hudhud.Name(101, "DAP"))

    private fun read(s: String, language: String?, g: List<Hudhud.Name> = goods) =
        Hudhud.understand(s, names, HudhudRealPages.forApp(language ?: "en"), language, goods = g)

    private val side = Ask.DidYouMean(
        listOf(Ask.Figure(VoiceQuestion.Kind.TO_GET), Ask.Figure(VoiceQuestion.Kind.TO_GIVE)), Hudhud.Unsure.SIDE
    )

    private fun opensEntry(a: Ask): Boolean = a is Ask.Entry || a is Ask.Entries ||
        (a is Ask.Guessed && opensEntry(a.ask)) || (a is Ask.DidYouMean && a.options.any(::opensEntry))

    // ------------------------------------------------------------------------------------------------ BC-354

    @Test fun `kal in a promise is tomorrow - no span and no 'as it stands now'`() {
        for ((lang, s) in listOf(
            "ur-Latn" to "Kal Ali ko 500 dunga", "ur-Latn" to "Ali ko kal 500 de dunga", "ur-Latn" to "kal Ali Raza ko 500 de dunga",
            "hi" to "कल अली को 500 दूँगा", "ur" to "کل کا حساب علی کو دوں گا"
        )) {
            val a = read(s, lang)
            assertFalse("$lang: $s read $a", a is Ask.Now)
            assertEquals("$lang: $s", Ask.Parties(listOf(1L)), a)
            assertEquals("$lang: $s", null, VoiceQuestion.windowOf(s))
        }
    }

    @Test fun `kal in the past is still yesterday, and a word that is only yesterday stays yesterday in any tense`() {
        assertEquals(VoiceQuestion.Window.YESTERDAY, VoiceQuestion.windowOf("Ali ne kal 500 diye"))
        assertEquals(VoiceQuestion.Window.YESTERDAY, VoiceQuestion.windowOf("kal ki sale"))
        assertEquals(VoiceQuestion.Window.YESTERDAY, VoiceQuestion.windowOf("कल की बिक्री"))
        assertEquals(VoiceQuestion.Window.YESTERDAY, VoiceQuestion.windowOf("I will pay Ali the 500 from yesterday"))
        assertEquals(VoiceQuestion.Window.YESTERDAY, VoiceQuestion.windowOf("Ali guzishta roz ka hisaab dega"))
        assertEquals(Ask.Period(Hudhud.Measure.SALES, VoiceQuestion.Window.YESTERDAY), read("kal ki sale", "ur-Latn"))
        // The day of an entry said in the past is still yesterday's.
        val e = read("Ali ne kal 500 diye", "ur-Latn")
        assertTrue("$e", e is Ask.Entry && e.day?.daysBack == 1)
    }

    // ------------------------------------------------------------------------------------------------ BC-355 (Arabic: native read)

    @Test fun `an Arabic product word with its article meets the product saved in Latin letters`() {
        assertEquals(Ask.Goods(listOf(100L), GoodsFacet.PRICE), read("كم سعر اليوريا", "ar"))
        assertEquals(Ask.Goods(listOf(100L), GoodsFacet.PRICE), read("كم ثمن اليوريا", "ar"))
        assertEquals(Ask.Goods(listOf(100L), GoodsFacet.BUYERS), read("من اشترى اليوريا", "ar"))
        // Without the article, as before.
        assertEquals(Ask.Goods(listOf(100L), GoodsFacet.PRICE), read("كم سعر يوريا", "ar"))
    }

    @Test fun `the article never makes a word of the language a product or a name`() {
        // "الربح" (the profit) is the bank's word: the profit, not a product.
        assertEquals(Ask.Figure(VoiceQuestion.Kind.PROFIT_MONTH), read("الربح", "ar"))
        // A word the bank taught keeps its article and its meaning, even beside a product spelled like it without one ("Rabih").
        val rabih = goods + Hudhud.Name(102, "Rabih Store")
        val profit = read("كم الربح", "ar", rabih)
        assertFalse("$profit", profit is Ask.Goods && 102L in profit.ids)
        // The article adds no meeting the bare word does not make: with it and without it read the same. (The bare "سماد", the
        // fertiliser, already meets a product saved as "Samad": an untaught word of the language meeting a name, lesson 139's
        // known limit, not this class.)
        val samad = goods + Hudhud.Name(103, "Samad Sons")
        for (w in listOf("سماد", "يوريا", "سكر", "ملح")) assertEquals(w, read("كم سعر $w", "ar", samad), read("كم سعر ال$w", "ar", samad))
        // An Urdu word that begins with "ال" is not cut ("الگ", apart).
        assertNotEquals(Ask.Goods(listOf(100L), GoodsFacet.OVERVIEW), read("الگ رکھو", "ur"))
    }

    // ------------------------------------------------------------------------------------------------ BC-356

    @Test fun `a bare amount asked after asks which way first, in the nine languages, and never opens an entry`() {
        for ((lang, s) in listOf(
            "ur-Latn" to "500 ka kya bana?", "ur-Latn" to "500 ka kya hua", "ur-Latn" to "500 rupay ka kya hua",
            "en" to "what about the 500?", "en" to "what happened to the 500", "en" to "what about 500 rupees",
            "ur" to "500 کا کیا بنا؟", "ur" to "۵۰۰ روپے کا کیا ہوا",
            "hi" to "500 का क्या हुआ?", "hi" to "500 रुपये का क्या बना",
            "bn" to "500 টাকার কী হল?", "bn" to "৫০০ টাকার কী হলো",
            "ar" to "ماذا عن الـ500؟", "ar" to "ما أخبار 500 ريال",
            "fa" to "500 چی شد؟", "fa" to "500 تومان چه شد",
            "sd" to "500 جو ڇا ٿيو؟",
            "in" to "bagaimana dengan 500?", "in" to "gimana 500?"
        )) {
            val a = read(s, lang)
            assertEquals("$lang: $s", side, a)
            assertFalse("$lang: $s", opensEntry(a))
        }
    }

    @Test fun `an amount said with a verb of giving is still an entry, and a phrase alone is no amount asked after`() {
        assertTrue(read("Ali ko 500 diye", "ur-Latn") is Ask.Entry)
        assertNotEquals(side, read("kya bana", "ur-Latn"))
        assertNotEquals(side, read("what about it", "en"))
        // More than the amount and the phrase: left to the readers that know the rest.
        assertNotEquals(side, read("500 ka kya bana kal", "ur-Latn"))
    }

    // ------------------------------------------------------------------------------------------------ BC-358

    @Test fun `a named man's amount asked after is his account, never an entry in his name`() {
        for ((lang, s) in listOf(
            "ur-Latn" to "Ali ke 500 ka kya bana", "ur-Latn" to "500 ka kya bana Ali", "ur-Latn" to "Ali Raza ke 500 ka kya hua",
            "en" to "what about Ali's 500"
        )) {
            val a = read(s, lang)
            assertFalse("$lang: $s read $a", opensEntry(a))
            assertEquals("$lang: $s", Ask.Parties(listOf(1L)), a)
        }
    }

    // ------------------------------------------------------------------------------------------------ BC-357 (Arabic, Farsi: native read)

    @Test fun `is there sugar in Arabic or Farsi is a thing's stock, never the privacy page`() {
        for ((lang, s) in listOf(
            "ar" to "هل يوجد سكر", "ar" to "هل عندنا سكر", "ar" to "هل عندي سكر", "ar" to "كم سكر عندنا", "ar" to "هل يوجد ملح",
            "fa" to "شکر داریم؟"
        )) {
            val a = read(s, lang)
            assertNotEquals("$lang: $s", Ask.About(Hudhud.AboutTopic.PRIVACY), a)
            assertEquals("$lang: $s", Ask.NeedGoods(GoodsFacet.STOCK), a)
        }
        // A product of the book: its stock, in every language that asks so.
        for ((lang, s) in listOf("ar" to "هل يوجد يوريا", "ar" to "هل يوجد اليوريا", "en" to "is there urea", "fa" to "آیا اوره داریم",
            "bn" to "ইউরিয়া আছে কি", "in" to "ada urea?")) {
            assertEquals("$lang: $s", Ask.Goods(listOf(100L), GoodsFacet.STOCK), read(s, lang))
        }
    }

    @Test fun `privacy said in its own words stays privacy, and a word that only sounds like safety does not make it`() {
        assertEquals(Ask.About(Hudhud.AboutTopic.PRIVACY), read("هل بياناتي آمنة", "ar"))
        assertEquals(Ask.About(Hudhud.AboutTopic.PRIVACY), read("هل البيانات محفوظة", "ar"))
        assertEquals(Ask.About(Hudhud.AboutTopic.PRIVACY), read("mera data mehfooz hai", "ur-Latn"))
        assertNotEquals(Ask.About(Hudhud.AboutTopic.PRIVACY), read("Sindhi mein dikhao", "ur-Latn"))
        // A Latin word that is no product is left to the readers that know it: never "which product?".
        assertNotEquals(Ask.NeedGoods(GoodsFacet.STOCK), read("is there any problem", "en"))
        assertNotEquals(Ask.NeedGoods(GoodsFacet.STOCK), read("do you have feelings", "en"))
    }
}
