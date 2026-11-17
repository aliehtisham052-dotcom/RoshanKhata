package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.Hudhud.Ask
import com.innovation313.roshankhata.data.Hudhud.GoodsFacet
import com.innovation313.roshankhata.data.Hudhud.RecordFacet
import com.innovation313.roshankhata.data.Hudhud.StatusOf
import com.innovation313.roshankhata.data.Hudhud.Thing
import com.innovation313.roshankhata.data.VoiceQuestion.Kind
import com.innovation313.roshankhata.data.VoiceQuestion.Window
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The classes the audit's left list held (17 Nov 2026, L004, L019 and the lead's retrain check), each through the whole pipeline:
 * [Hudhud.understand] with the shipped sense and the app's own pages (lesson 145). Every name and product is invented.
 *
 * The sense is the shipped one; `-Dhudhud.sense=<file>` runs the same sentences with another (a retrain not yet merged): the
 * readings here are the rules', so they must stand with any sense.
 */
class HudhudLeftClassesTest {

    init {
        HudhudSense.use(System.getProperty("hudhud.sense")?.let { HudhudSense.decode(java.io.File(it).readBytes()) }
            ?: HudhudModelTest.shippedModel())
    }

    private val names = listOf("Ali Raza", "Ali Hassan", "Bashir Ahmad", "Tariq Butt", "Naeem Khan", "Kamran Traders", "Urea Traders")
        .mapIndexed { i, n -> Hudhud.Name(i + 1L, n) }
    private val goods = listOf("Urea", "DAP", "Sugar", "Paracetamol", "Atta", "Ghee").mapIndexed { i, n -> Hudhud.Name(100L + i, n) }

    private fun read(lang: String, said: String, book: List<Hudhud.Name> = names): Ask =
        Hudhud.understand(said, book, HudhudRealPages.forApp(lang), lang, goods, null)

    private fun check(expected: Ask, lang: String, vararg said: String) {
        for (s in said) assertEquals("$lang: $s", expected, read(lang, s))
    }

    // ----------------------------------------------------------------------------------- a customer and a product

    @Test fun `a customer and a product in one question is what he took of it`() {
        check(Ask.Took(listOf(1), listOf(100)), "ur-Latn", "Ali Raza ne Urea kab liya", "Ali Raza ne Urea kab kharidi",
            "Ali Raza ko Urea kab di", "Ali Raza ne kitni Urea li")
        check(Ask.Took(listOf(1), listOf(101)), "ur-Latn", "Ali Raza ne DAP kab kharida")
        check(Ask.Took(listOf(3), listOf(100)), "ur-Latn", "Bashir Ahmad ne Urea kitna liya")
        check(Ask.Took(listOf(1), listOf(100)), "en", "when did Ali Raza buy Urea")
    }

    @Test fun `money, an and, a product alone or a name holding the product's word are read as before`() {
        assertEquals(Ask.WhenPays(listOf(1)), read("ur-Latn", "Ali Raza ke Urea ke paise kab dega"))
        assertTrue(read("ur-Latn", "Ali Raza ka hisaab aur Urea ka rate") is Ask.Both)
        assertEquals(Ask.Goods(listOf(100), GoodsFacet.PRICE), read("ur-Latn", "Urea ka rate"))
        // "Urea Traders" is the man: the product is looked for only among the words that are not his name.
        assertFalse(read("ur-Latn", "Urea Traders ka hisaab") is Ask.Took)
    }

    // ----------------------------------------------------------------------------------- a festival as a span

    @Test fun `a festival framed as a span is a span he cannot give, never the whole book or this month`() {
        for (s in listOf("Eid se ab tak ki sale", "Eid par kitni sale hui", "Eid se aaj tak kitna munafa hua", "Diwali se ab tak ki sale",
            "Christmas ki sale")) {
            assertTrue("ur-Latn: $s -> " + read("ur-Latn", s), read("ur-Latn", s) is Ask.SpanLimit)
        }
        assertTrue(VoiceQuestion.unsupportedSpan("Eid se ab tak ki sale"))
        assertTrue(VoiceQuestion.unsupportedSpan("since Eid how much did I sell"))
    }

    @Test fun `a man called Eid is no festival`() {
        val book = names + Hudhud.Name(20, "Eid Muhammad")
        assertFalse(VoiceQuestion.unsupportedSpan("Eid Muhammad se ab tak kitna mila"))
        assertFalse(read("ur-Latn", "Eid Muhammad se ab tak kitna mila", book) is Ask.SpanLimit)
    }

    // ----------------------------------------------------------------------------------- bills, ways of paying, attendance, deliveries

    @Test fun `Indonesian's biggest tagihan is the supplier bills still owed`() {
        check(Ask.Status(StatusOf.BILLS), "in", "tagihan terbesar", "tagihan pemasok terbesar")
    }

    @Test fun `money in by its ways is money that came in, not the business card`() {
        check(Ask.Collected(null), "ur-Latn", "cash aur card se kitna aaya", "cash aur bank se kitna aaya", "card se kitni payment aayi")
        check(Ask.Collected(Window.TODAY), "ur-Latn", "aaj card se kitna aaya")
        check(Ask.Collected(null), "en", "how much came by cash and card")
        // The business card itself is still its page.
        assertEquals(Ask.Help("screen_card"), read("ur-Latn", "visiting card bana do"))
    }

    @Test fun `bills shown are the invoices first, with the supplier bills beside them`() {
        check(Ask.DidYouMean(listOf(Ask.Count(Thing.INVOICES, Window.TODAY), Ask.Count(Thing.BILLS, Window.TODAY))), "ur-Latn",
            "aaj ke sab bill dikhao", "aaj ke bill dikhao")
        // A supplier word says which bills.
        assertEquals(Ask.Count(Thing.BILLS, null), read("ur-Latn", "supplier ke bill dikhao"))
    }

    @Test fun `Hindi attendance is no date`() {
        check(Ask.Record(RecordFacet.ATTENDANCE, emptyList(), Window.TODAY), "hi", "आज की हाज़री", "आज की हाजरी")
        assertEquals(Ask.Clock, read("hi", "आज की तारीख क्या है"))
    }

    @Test fun `how long since a product came is its last delivery`() {
        check(Ask.Goods(listOf(100), GoodsFacet.LAST_IN), "ur-Latn", "Urea aaye kitne din ho gaye", "Urea ko aaye kitne din ho gaye")
        check(Ask.Goods(listOf(101), GoodsFacet.LAST_IN), "ur-Latn", "DAP aaye kitne din hue")
        check(Ask.Goods(listOf(100), GoodsFacet.LAST_IN), "en", "how many days since Urea came")
        // How long the shelf lasts is the cover, as before.
        assertEquals(Ask.Goods(listOf(100), GoodsFacet.COVER), read("ur-Latn", "Urea kitne din chalegi"))
    }

    // ----------------------------------------------------------------------------------- L019: what the app can do, past talks

    @Test fun `a question about what the app can do that names a feature is that feature's page`() {
        assertEquals(Ask.Help("screen_zakat"), read("ur-Latn", "kya app mein zakat ka hisaab ho sakta hai"))
        assertEquals(Ask.Help("screen_card"), read("ur-Latn", "kya aap visiting card bana sakte ho"))
        assertEquals(Ask.Help("screen_posters"), read("en", "can the app make posters for my shop"))
        // A feature the app does not have stays unknown.
        assertEquals(Ask.Unknown, read("ur-Latn", "kya delivery ka record Excel ya PDF mein export ho sakta hai"))
    }

    @Test fun `past talks asked for are told plainly, not the day's activity`() {
        check(Ask.NoHistory, "ur-Latn", "pichli chat dikhao", "chat history", "hamari purani baatein dikhao")
        check(Ask.NoHistory, "en", "show my chat history")
        // "Say it again" is no chat history, and a name is that person's.
        assertNotEquals(Ask.NoHistory, read("ur-Latn", "pichli baat phir se batao"))
        assertNotEquals(Ask.NoHistory, read("ur-Latn", "Ali Raza ki purani chat"))
    }

    // ----------------------------------------------------------------------------------- the side, stated by the sentence (lead)

    @Test fun `the shop's to give, stated, whatever the sense`() {
        for ((lang, s) in listOf(
            "ur-Latn" to "Kis ki raqam deni hai is hafte", "ur-Latn" to "is hafte kis ki raqam deni hai",
            "ur" to "اس ہفتے کس کی رقم دینی ہے", "hi" to "इस हफ्ते किसकी रकम देनी है",
            "sd" to "ڪل ادائيگي ڪيتري رهيل آهي", "sd" to "ڪيتري ادائيگي رهيل آهي",
            "ur-Latn" to "kul adaigi kitni baqi hai", "ur" to "کل ادائیگی کتنی باقی ہے", "hi" to "कुल अदायगी कितनी बाकी है"
        )) assertTrue("$lang: $s -> " + read(lang, s), read(lang, s).let { it is Ask.Figure && it.kind == Kind.TO_GIVE || it is Ask.Now })
    }

    @Test fun `the shop's to get, stated, whatever the sense`() {
        for ((lang, s) in listOf(
            "ur-Latn" to "log mujhe kitna denge sab mila ke", "ur-Latn" to "sab log mila ke mujhe kitna denge",
            "ur" to "لوگ مجھے کل کتنا دیں گے", "hi" to "लोग मुझे कुल कितना देंगे", "en" to "how much will people pay me in total",
            "in" to "berapa total yang akan dibayar orang ke saya", "fa" to "مردم در کل چقدر به من می‌دهند",
            "ar" to "كم سيدفع لي الناس إجمالا", "bn" to "আমাকে মোট কত দেবে"
        )) assertEquals("$lang: $s", Ask.Figure(Kind.TO_GET), read(lang, s))
        // "mujhe kitna dena hai" is what the shop gives, with no future.
        assertEquals(Ask.Figure(Kind.TO_GIVE), read("ur-Latn", "mujhe kitna dena hai sab mila ke"))
        // "kis ki adaigi baqi hai" asks who has not paid; it is not the shop's payment.
        assertFalse(VoiceQuestion.sideStated("kis ki adaigi baqi hai"))
    }

    @Test fun `a greeting whose first word is a customer's name is a greeting (salaamHeadRead)`() {
        // L018's breakage pass: nothing held salaamHeadRead (lesson 141). "اسلام" is also Aslam, said in Urdu letters.
        val book = names + Hudhud.Name(30, "Aslam Khan")
        assertEquals(Ask.Salaam, read("ur", "اسلام علیکم", book))
        assertEquals(Ask.Salaam, read("ur-Latn", "aslam alaikum", book))
    }

    @Test fun `a sentence that is only a name is his account by rule`() {
        check(Ask.Parties(listOf(1)), "ur-Latn", "Ali Raza", "Ali Raza?")
        check(Ask.Parties(listOf(6)), "en", "Kamran Traders")
        check(Ask.Parties(listOf(1)), "ur", "علی رضا")
        // Hindi: his account, or a choice among the Alis (the bridge does not yet meet "रज़ा" with "Raza"), never his details.
        assertTrue(read("hi", "अली रज़ा").let { it is Ask.Parties && 1L in it.ids })
    }
}
