package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.Hudhud.AboutTopic
import com.innovation313.roshankhata.data.Hudhud.Ask
import com.innovation313.roshankhata.data.Hudhud.Facet
import com.innovation313.roshankhata.data.Hudhud.StatusOf
import com.innovation313.roshankhata.data.Hudhud.Talk
import com.innovation313.roshankhata.data.VoiceQuestion.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A word of the app that is also somebody's name (15 Nov 2026). In Urdu "aman" is peace and "mehfooz" is safe, and the concept words
 * the app knows (SAFE, OWNER, AGAIN, VICTORY…) are the same letters as a customer called Aman Khan, Mehfooz Ahmad, Aamna Bibi, Fateh
 * Muhammad, Baz Muhammad or Malik Riaz — in every script, because the name search and the concept words meet through the same
 * [VoiceQuestion.normalise]. A hard word is never a name; so "Aman ka number" asked a question about nobody ("whose?") and "who is
 * Aman" told the app's privacy. [HudhudMind]'s soft words ([HudhudMind.isSoftWord]) are looked at only when the plain words name
 * nobody, and give way to a shop question the word itself makes; the same is true of the words that call or befriend (dost, yaar,
 * jaan, sahib…) which are also real name parts (Dost Muhammad, Yar Muhammad, Rafiq).
 *
 * Every sentence goes through [Hudhud.understand] with the shipped sense, as the app does. The sentences are written by the maker
 * of the tests, not by a native reader of each language (said plainly in the notes), and every name is made up (lesson 80). What must
 * stay the app's matters as much as what is found: "is my data safe" is a question about the app even when a customer is called
 * Mehfooz, "who is the owner of the shop" is the shop's owner even when a customer is called Malik, and "yaar aaj ki sale batao" is
 * the day's sales, never a search for a man called Yar.
 */
class HudhudNameWordsTest {

    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val goods = listOf(Hudhud.Name(100, "Urea"))

    /** The words are all names here: aman, mehfooz, aamna, fateh, baz and malik are each also a word the app knows. */
    private val shop = listOf(
        Hudhud.Name(1, "Aman Khan"), Hudhud.Name(2, "Mehfooz Ahmad"), Hudhud.Name(3, "Aamna Bibi"),
        Hudhud.Name(4, "Fateh Muhammad"), Hudhud.Name(5, "Baz Muhammad"), Hudhud.Name(6, "Muhammad Waqas"),
        Hudhud.Name(7, "Malik Riaz")
    )

    /** Names that begin with a word people call each other by: Rafiq (a friend), Dost, Yar. */
    private val callers = listOf(
        Hudhud.Name(1, "Rafiq Ahmad"), Hudhud.Name(2, "Dost Muhammad"), Hudhud.Name(3, "Yar Muhammad"),
        Hudhud.Name(4, "Ali Raza"), Hudhud.Name(5, "Ahmad Khan")
    )

    /** The same shop with nobody called by such a word. */
    private val nobodyCalled = listOf(Hudhud.Name(4, "Ali Raza"), Hudhud.Name(5, "Ahmad Khan"))

    /** Names saved in Roman letters, said in the letters of other languages: the spellings that carry w, y, h, kh, gh and long vowels. */
    private val roman = listOf(
        Hudhud.Name(11, "Waseem Ahmad"), Hudhud.Name(12, "Anwar Ali"), Hudhud.Name(13, "Yusuf Khan"), Hudhud.Name(14, "Rizwan Shah"),
        Hudhud.Name(15, "Saad Hussain"), Hudhud.Name(16, "Imran Butt"), Hudhud.Name(17, "Haroon Rashid"), Hudhud.Name(18, "Noman Aslam"),
        Hudhud.Name(19, "Ghulam Hussain"), Hudhud.Name(20, "Khalid Mehmood")
    )

    private fun read(s: String, language: String?, names: List<Hudhud.Name>) =
        Hudhud.understand(s, names, emptyList(), language, goods = goods)

    private fun phone(vararg ids: Long) = Ask.Person(ids.toList(), Facet.PHONE)
    private fun person(facet: Facet, vararg ids: Long) = Ask.Person(ids.toList(), facet)
    private fun account(vararg ids: Long) = Ask.Parties(ids.toList())

    private fun check(expected: Ask, names: List<Hudhud.Name>, language: String?, vararg said: String) {
        for (s in said) assertEquals("$language: $s", expected, read(s, language, names))
    }

    /** Who a reading names, whatever the span or the way of asking around him. */
    private fun idsOf(ask: Ask): List<Long> = when (ask) {
        is Ask.Person -> ask.ids
        is Ask.Parties -> ask.ids
        is Ask.Entry -> ask.ids
        is Ask.WhenPays -> ask.ids
        is Ask.Now -> idsOf(ask.ask)
        else -> emptyList()
    }

    // ------------------------------------------------------------------------------------ the man, not the word

    @Test fun `a name that is also a word the app knows is the man the book has - Roman letters`() {
        check(phone(1), shop, "ur", "Aman ka number", "Aman Khan ka number")
        check(person(Facet.LIMIT, 1), shop, "ur", "Aman ki limit")
        check(person(Facet.DETAILS, 1), shop, "ur", "Aman kaun hai")
        check(person(Facet.DETAILS, 1), shop, "en", "who is Aman")
        check(account(1), shop, "ur", "Aman ka hisaab", "Aman ke paise")
        check(person(Facet.NOTE, 1), shop, "ur", "Aman ka note", "Aman Khan ka note")
        check(person(Facet.LAST_PAYMENT, 1), shop, "ur", "Aman ki last payment kab aayi")
        check(Ask.WhenPays(listOf(1L)), shop, "ur", "Aman kab dega")

        check(phone(2), shop, "ur", "Mehfooz ka number", "Mehfooz Ahmad ka number")
        check(account(2), shop, "ur", "Mehfooz ka hisaab")
        check(person(Facet.LAST_PAYMENT, 2), shop, "ur", "Mehfooz ki last payment")

        check(person(Facet.NOTE, 3), shop, "ur", "Aamna ka note")
        check(person(Facet.LIMIT, 3), shop, "ur", "Aamna ki limit")
        check(Ask.WhenPays(listOf(3L)), shop, "ur", "Aamna kab dega")

        check(account(4), shop, "ur", "Fateh Muhammad ka hisaab")
        check(person(Facet.LIMIT, 4), shop, "ur", "Fateh Muhammad ki limit")
        check(person(Facet.NOTE, 4), shop, "ur", "Fateh ka note")
        check(person(Facet.NOTE, 5), shop, "ur", "Baz Muhammad ka note")

        check(phone(7), shop, "ur", "Malik Riaz ka number", "Malik ka number")
        check(account(7), shop, "ur", "Malik Riaz ka hisaab", "Malik ka hisaab")
    }

    @Test fun `a name that is also a word the app knows is the man the book has - Urdu letters`() {
        check(phone(3), shop, "ur", "آمنہ کا نمبر", "آمنہ بی بی کا نمبر", "آمنة رقم")
        check(account(3), shop, "ur", "آمنة")
        check(phone(2), shop, "ur", "محفوظ کا نمبر", "محفوظ احمد کا نمبر")
        check(phone(4), shop, "ur", "فتح کا نمبر", "فتح محمد کا نمبر")
        check(account(4), shop, "ur", "فتح محمد کا حساب")
        check(person(Facet.LIMIT, 4), shop, "ur", "فتح محمد کی حد")
        check(phone(5), shop, "ur", "باز محمد کا نمبر", "باز کا نمبر")
        check(phone(7), shop, "ur", "ملک ریاض کا نمبر")
    }

    @Test fun `a name that is also a word the app knows is the man the book has - Hindi and Bengali letters`() {
        check(phone(1), shop, "hi", "अमन खान का नंबर")
        check(phone(1), shop, "bn", "আমন খানের নম্বর")
    }

    @Test fun `an entry said by voice names the man whose name is also a word, and keeps the way the money went`() {
        fun entry(s: String, language: String): Ask.Entry = read(s, language, shop) as Ask.Entry
        val got = entry("Aman ne 500 diye", "ur")
        assertEquals(listOf(1L), got.ids)
        assertEquals(false, got.isGiven)
        assertEquals(500.0, got.amount, 0.0)
        val gave = entry("Aman ko 500 diye", "ur")
        assertEquals(listOf(1L), gave.ids)
        assertEquals(true, gave.isGiven)
        assertEquals(listOf(2L), entry("Mehfooz ko 700 diye", "ur").ids)
        assertEquals(listOf(3L), entry("آمنہ کو 300 دیے", "ur").ids)
    }

    // ------------------------------------------------------------------------------------ the word, not a man

    @Test fun `a question about the app stays the apps even when a customer is called by its word`() {
        // Mehfooz and Aman are customers here; "mehfooz" and "aman" in these are safe and in safety.
        for (s in listOf("kya mera data safe hai", "میرا ڈیٹا محفوظ ہے", "aap ka data mehfooz hai", "mera data aman se hai?",
            "mera data aman mein hai kya", "mera data kitna mehfooz hai", "اپ کا ڈیٹا محفوظ ہے"))
            assertEquals(s, Ask.About(AboutTopic.PRIVACY), read(s, "ur", shop))
        for (s in listOf("is my data safe", "is my data secure"))
            assertEquals(s, Ask.About(AboutTopic.PRIVACY), read(s, "en", shop))
        assertEquals(Ask.About(AboutTopic.PRIVACY), read("कितनी देर मेरा डेटा सुरक्षित है", "hi", shop))
    }

    @Test fun `the shops owner is asked for as the owner even when a customer is called Malik`() {
        assertEquals(Ask.About(AboutTopic.OWNER_NAME), read("dukaan ka malik kaun hai", "ur", shop))
        assertEquals(Ask.About(AboutTopic.OWNER_NAME), read("who is the owner of the shop", "en", shop))
    }

    @Test fun `whose is still asked when nobody is named, and naming him beside it names him`() {
        check(Ask.NeedName(Facet.PHONE), shop, "ur", "number kis ka hai", "kis ka number hai")
        check(phone(1), shop, "ur", "Aman ka number kis ka hai")
    }

    // ------------------------------------------------------------------------------------ letters that two names share

    @Test fun `letters that two names share are a choice, and a surname settles it`() {
        // "امن" and "अमन" are Aman and also Aamna, as far as the letters go: the man is not guessed.
        for ((said, language) in listOf("امن کا نمبر" to "ur", "अमन का नंबर" to "hi")) {
            val ask = read(said, language, shop)
            assertEquals(said, Facet.PHONE, (ask as Ask.Person).facet)
            assertEquals(said, setOf(1L, 3L), ask.ids.toSet())
        }
        for ((said, language) in listOf("امن کو 300 دیے" to "ur", "अमन को 500 दिए" to "hi"))
            assertEquals(said, setOf(1L, 3L), (read(said, language, shop) as Ask.Entry).ids.toSet())
        check(phone(1), shop, "ur", "امن خان کا نمبر")
        check(phone(1), shop, "hi", "अमन खान का नंबर")
    }

    @Test fun `a first name that three men share is a choice of the three, and the full name is one man`() {
        val ask = read("Muhammad ka number", "ur", shop)
        assertEquals(Facet.PHONE, (ask as Ask.Person).facet)
        assertEquals(setOf(4L, 5L, 6L), ask.ids.toSet())
        check(phone(6), shop, "ur", "Muhammad Waqas ka number", "Waqas ka number")
    }

    // ------------------------------------------------------------------------------------ words that call or befriend

    @Test fun `a man named by a word people call each other by is that man when the book has him`() {
        check(phone(1), callers, "ur", "رفیق کا نمبر", "رفیق احمد کا نمبر", "Rafiq ka number", "Rafiq Ahmad ka number")
        check(phone(1), callers, "hi", "रफ़ीक़ का नंबर")
        check(phone(2), callers, "ur", "dost ka number", "Dost Muhammad ka number", "دوست محمد کا نمبر", "دوست کا نمبر")
        check(phone(2), callers, "hi", "दोस्त मोहम्मद का नंबर")
        check(phone(3), callers, "ur", "yaar number batao", "یار محمد کا نمبر", "Yar Muhammad ka number")
    }

    @Test fun `a word that calls somebody in front of a name is a call, and the name is the man`() {
        check(phone(4), callers, "ur", "bhai Ali Raza ka number", "okay Ali Raza ka number", "dear Ali Raza ka number")
        check(person(Facet.LIMIT, 4), callers, "ur", "Ali Raza bhai ki limit")
        check(account(4), callers, "ur", "Hudhud bhai Ali Raza ka hisaab")
    }

    @Test fun `a word that calls somebody in front of a shop question is a call, and the question is the shops`() {
        check(Ask.Figure(Kind.SALES_TODAY), callers, "ur", "yaar aaj ki sale batao", "jaan aaj ki sale kitni hai", "bhai aaj ki sale")
        // The day's own summary (the rules' reading, as without the calling word); the retrained sense no longer reads it as the whole book (H4, 25 Nov 2026).
        check(Ask.Today, callers, "ur", "dost aaj ka khata kaisa raha")
        assertEquals(Ask.About(AboutTopic.PRIVACY), read("رفیق بھائی میرا ڈیٹا محفوظ ہے", "ur", callers))
    }

    @Test fun `a call alone is an address, and a fact asked of a call nobody carries is whose`() {
        check(Ask.Chat(Talk.ADDRESS, false), callers, "ur", "bhai jaan", "رفیق")
        // Nobody in this book is called jaan, sahib or ustad: the word calls, the number is asked of nobody.
        check(Ask.NeedName(Facet.PHONE), callers, "ur", "jaan ka number", "sahib ka number", "sahab ka number", "ustad ka number",
            "dost mera number batao")
        // And in a book with nobody called dost, "dost ka number" is no man either.
        check(Ask.NeedName(Facet.PHONE), nobodyCalled, "ur", "dost ka number")
    }

    // ------------------------------------------------------------------------------------ across scripts, end to end

    @Test fun `names saved in Roman letters are met in Urdu letters`() {
        check(phone(11), roman, "ur", "وسیم احمد کا نمبر", "وسیم کا نمبر")
        check(person(Facet.LIMIT, 12), roman, "ur", "انور علی کی حد")
        check(account(12), roman, "ur", "انور کا حساب")
        check(person(Facet.NOTE, 13), roman, "ur", "یوسف خان کا نوٹ")
        check(phone(13), roman, "ur", "یوسف کا نمبر")
        check(phone(14), roman, "ur", "رضوان شاہ کا نمبر")
        check(account(14), roman, "ur", "رضوان کا حساب")
        check(phone(15), roman, "ur", "سعد حسین کا نمبر")
        check(phone(16), roman, "ur", "عمران کا نمبر")
        check(phone(17), roman, "ur", "ہارون کا نمبر")
        check(phone(18), roman, "ur", "نعمان کا نمبر")
        check(phone(19), roman, "ur", "غلام حسین کا نمبر")
        check(account(20), roman, "ur", "خالد محمود کا حساب")
        check(phone(20), roman, "ur", "خالد کا نمبر")
    }

    @Test fun `names saved in Roman letters are met in Hindi letters`() {
        check(phone(11), roman, "hi", "वसीम अहमद का नंबर", "वसीम का नंबर")
        check(phone(12), roman, "hi", "अनवर का नंबर")
        check(phone(13), roman, "hi", "यूसुफ़ का नंबर")
        check(phone(14), roman, "hi", "रिज़वान का नंबर")
        check(phone(16), roman, "hi", "इमरान का नंबर")
        check(phone(17), roman, "hi", "हारून का नंबर")
        check(phone(18), roman, "hi", "नोमान का नंबर")
        check(phone(19), roman, "hi", "ग़ुलाम हुसैन का नंबर")
        check(account(20), roman, "hi", "खालिद महमूद का हिसाब")
    }

    @Test fun `names saved in Roman letters are met in Bengali letters`() {
        check(phone(11), roman, "bn", "ওয়াসিম আহমদের নম্বর")
        check(phone(12), roman, "bn", "আনোয়ারের নম্বর")
        check(phone(14), roman, "bn", "রিজওয়ানের নম্বর")
        check(phone(16), roman, "bn", "ইমরানের নম্বর")
        check(phone(17), roman, "bn", "হারুনের নম্বর")
        check(phone(18), roman, "bn", "নোমানের নম্বর")
        check(phone(20), roman, "bn", "খালিদের নম্বর")
    }

    @Test fun `names saved in Roman letters are met in Arabic, Persian and Sindhi letters`() {
        check(phone(11), roman, "ar", "رقم وسيم أحمد")
        check(phone(12), roman, "ar", "رقم أنور")
        check(phone(13), roman, "ar", "رقم يوسف")
        check(phone(11), roman, "fa", "شماره وسیم احمد")
        check(phone(13), roman, "fa", "شماره یوسف")
        check(phone(11), roman, "sd", "وسيم جو نمبر")
    }

    // ------------------------------------------------------------------------------------ the man, with a span around him

    @Test fun `a man named with a span stays the man in every script, never a question about the period`() {
        for ((said, language) in listOf(
            "وسیم احمد پچھلے مہینے" to "ur", "وسیم پچھلے مہینے" to "ur", "وسیم احمد کا حساب پچھلے مہینے" to "ur",
            "वसीम अहमद पिछले महीने" to "hi", "वसीम पिछले महीने" to "hi", "वसीम अहमद का हिसाब पिछले महीने" to "hi",
            "ওয়াসিম আহমদ গত মাসে" to "bn", "ওয়াসিম আহমদের হিসাব গত মাসে" to "bn",
            "وسيم أحمد الشهر الماضي" to "ar", "حساب وسيم أحمد الشهر الماضي" to "ar",
            "Waseem Ahmad last month" to "en", "Waseem Ahmad ka hisaab pichle mahine" to "ur"
        )) {
            val ask = read(said, language, roman)
            assertTrue("$language: $said is $ask", ask is Ask.Now)
            assertEquals("$language: $said", listOf(11L), idsOf(ask))
        }
        // A name that is also a word of the app keeps the span too.
        for (said in listOf("Aman last month", "Baz Muhammad last month")) {
            val ask = read(said, "ur", shop)
            assertTrue("$said is $ask", ask is Ask.Now)
            assertEquals(said, if (said.startsWith("Aman")) listOf(1L) else listOf(5L), idsOf(ask))
        }
    }
}
