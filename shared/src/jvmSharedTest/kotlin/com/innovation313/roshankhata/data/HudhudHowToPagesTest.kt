package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The how-to pages of the six screens that had none — Duplicate customers, Help & Support, Mera Khata, Payment voice,
 * the Helper's phone and Heir Access — and of the shop's side of the khata link (14 Nov 2026, #32), asked the way an
 * owner asks in each of the nine languages, against the app's own pages as they ship ([HudhudRealPages]: the pages of
 * `pages.tsv`, held to `HudhudBook.PAGES` by the catalog check, with their words read from each language's strings.xml).
 *
 * What is held here, and what is not:
 *  - Every sentence below was run against the build and reads as the page named; each is a "how do I…" or "where is…"
 *    that names the screen. They were written by Claude, not by a native reader of each language: the Sindhi, Bengali
 *    and Farsi ones in particular want a native read.
 *  - A question that does NOT reach its page yet is not asserted (see the report of #32): "what is <the screen>" and
 *    "<screen> kya hai" are taken by other readers first (About, Status, Abilities), as they already are for the older
 *    pages ("payment radar kya hai"), and "mera khata" still means the shop's own book (Status). Asserting them would
 *    freeze a wrong answer.
 *  - A page's title is also a name or a shop word (Waris, Madad, Phone, Duplicate): the six are no feature words
 *    ([Hudhud.isFeaturePage]), so a customer called Waris is still found.
 *  - The pages tell their screen's own labels, so each page must contain them, word for word, in every language: a
 *    screen whose button is renamed makes its page wrong, and this fails instead.
 * Names and goods are made up (lesson 80).
 */
class HudhudHowToPagesTest {

    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val people = listOf("Ali Raza", "Bashir Ahmad", "Imran Khan").mapIndexed { i, n -> Hudhud.Name(i + 1L, n) }
    private val goods = listOf("Urea", "DAP", "Sugar").mapIndexed { i, n -> Hudhud.Name(100L + i, n) }

    /** The pages as the phone hands them to Hudhud, for each app language, read once. */
    private val topicsOf: Map<String, List<Hudhud.Topic>> by lazy {
        HudhudRealPages.LANGUAGES.associateWith { HudhudRealPages.forApp(it) }
    }

    /** The tag the app gives Hudhud (`Locale.toLanguageTag`): Indonesian is "id", though its resource folder is "in". */
    private fun tagOf(language: String) = if (language == "in") "id" else language

    private fun ask(said: String, language: String, book: List<Hudhud.Name> = people): Hudhud.Ask =
        Hudhud.understand(said, book, topicsOf.getValue(language), tagOf(language), goods)

    /** What an owner says, by app language, and the page it must open. */
    private val cases: List<Triple<String, String, String>> = buildList {
        fun of(language: String, key: String, vararg said: String) = said.forEach { add(Triple(language, it, key)) }

        // English
        of("en", "screen_waris", "how do I set up heir access")
        of("en", "screen_viewer", "how do I make a helper phone")
        of("en", "screen_duplicates", "how do I merge two customers")
        of("en", "screen_payment_voice", "how do I turn on payment voice")
        of("en", "screen_help", "how do I contact support")

        // Roman Urdu: the owner's own tongue, so the most sentences
        of("ur-Latn", "screen_waris", "waris ki chaabi kaise banaun", "waris ki chabi kahan hai", "heir access kaise on karun",
            "how do I set up heir access")
        of("ur-Latn", "screen_viewer", "madadgar ka phone kaise banaun", "helper phone kaise banaun", "view only phone kaise banaun",
            "how do I make a helper phone")
        of("ur-Latn", "screen_duplicates", "duplicate customers kahan hain", "duplicate customers kaise milaun",
            "how do I merge two customers")
        of("ur-Latn", "screen_payment_voice", "payment voice kaise on karun", "paisa aaya ki awaaz kaise lagaun", "payment voice kahan hai",
            "how do I turn on payment voice")
        of("ur-Latn", "screen_help", "help kahan hai", "madad aur support kahan hai", "support se kaise baat karun",
            "how do I contact support")
        of("ur-Latn", "faq_khata_link", "customer ko khata link kaise bhejun", "customer ko balance link kaise bheju",
            "khata link kaise bhejte hain", "how do I send a khata link to a customer")
        // the customer's side: opening a link a shop sent
        of("ur-Latn", "screen_mera_khata", "dukaan ka bheja link kaise kholun", "dukandar ne jo link bheja usay kaise kholun",
            "how do I open the khata link a shop sent me")
        // the older pages keep their own questions
        of("ur-Latn", "screen_zakat", "zakat kahan hai")
        of("ur-Latn", "intro_staff", "staff kaise add karun")
        of("ur-Latn", "faq_lock", "app lock kaise lagaun")
        of("ur-Latn", "screen_posters", "poster kaise banaun")

        // Urdu
        of("ur", "screen_waris", "وارث کی چابی کیسے بناؤں")
        of("ur", "screen_duplicates", "دوہرے گاہک کیسے ملاؤں")
        of("ur", "screen_payment_voice", "پیسہ آیا کی آواز کیسے چلاؤں")
        of("ur", "screen_help", "مدد اور سپورٹ کہاں ہے")

        // Sindhi
        of("sd", "screen_waris", "وارث جي چاٻي ڪيئن ٺاهيان")
        of("sd", "screen_viewer", "مددگار جو فون ڪيئن ٺاهيان")
        of("sd", "screen_duplicates", "ٻٽا گراهڪ ڪيئن ملايان")
        of("sd", "screen_payment_voice", "پئسا آيا جو آواز ڪيئن هلايان")

        // Arabic
        of("ar", "screen_waris", "كيف أفعّل مفتاح الوارث")
        of("ar", "screen_viewer", "كيف أجعل هاتف المساعد")
        of("ar", "screen_payment_voice", "كيف أشغل صوت وصول المال")
        of("ar", "screen_help", "أين المساعدة والدعم")

        // Farsi
        of("fa", "screen_waris", "کلید وارث چیست", "چطور کلید وارث را بسازم")
        of("fa", "screen_viewer", "چطور گوشی دستیار بسازم")
        of("fa", "screen_duplicates", "چطور مشتریان تکراری را ادغام کنم")
        of("fa", "screen_payment_voice", "چطور صدای رسیدن پول را روشن کنم")
        of("fa", "screen_help", "راهنما و پشتیبانی کجاست")

        // Hindi
        of("hi", "screen_waris", "वारिस की चाबी क्या है", "वारिस की चाबी कैसे बनाऊं")
        of("hi", "screen_viewer", "सहायक का फ़ोन कैसे बनाऊं")
        of("hi", "screen_duplicates", "दोहरे ग्राहक कैसे मिलाऊं")
        of("hi", "screen_payment_voice", "पैसा आया की आवाज़ कैसे चालू करूं")
        of("hi", "screen_help", "मदद और सहायता कहाँ है")

        // Bengali
        of("bn", "screen_waris", "ওয়ারিশের চাবি কীভাবে বানাব")
        of("bn", "screen_viewer", "সহকারীর ফোন কীভাবে বানাব")
        of("bn", "screen_payment_voice", "টাকা আসার আওয়াজ কীভাবে চালু করব")
        of("bn", "screen_help", "সাহায্য ও সহায়তা কোথায়")

        // Indonesian
        of("in", "screen_waris", "bagaimana cara membuat kunci ahli waris")
        of("in", "screen_viewer", "bagaimana cara membuat ponsel pembantu")
        of("in", "screen_duplicates", "bagaimana cara menggabungkan pelanggan ganda")
        of("in", "screen_payment_voice", "bagaimana cara menyalakan suara uang masuk")
        of("in", "screen_help", "di mana bantuan dan dukungan")
    }

    @Test fun `the how-to pages are found by the words of a question in nine languages`() {
        val wrong = cases.filter { (language, said, key) -> ask(said, language) != Hudhud.Ask.Help(key) }
            .map { (language, said, key) -> "  [$language] $said  wanted $key, got ${ask(said, language)}" }
        assertTrue("${wrong.size} of ${cases.size} sentences do not open their page:\n" + wrong.joinToString("\n"), wrong.isEmpty())
    }

    @Test fun `every language is tried and the six pages and the link's page are each asked in more than one`() {
        val perLanguage = cases.groupBy { it.first }.mapValues { it.value.size }
        assertEquals(HudhudRealPages.LANGUAGES.toSet(), perLanguage.keys)
        for (key in listOf("screen_waris", "screen_viewer", "screen_duplicates", "screen_payment_voice", "screen_help")) {
            val languages = cases.filter { it.third == key }.map { it.first }.toSet()
            assertTrue("$key is asked in only $languages", languages.size >= 5)
        }
        assertTrue(cases.any { it.third == "screen_mera_khata" } && cases.any { it.third == "faq_khata_link" })
    }

    /**
     * Questions that reach their page by the words of its body, not of its title. The English page of Heir Access,
     * emptied on purpose, still passed every title-worded case above (14 Nov 2026, breaking the test to see it fail): the
     * title carried them. These do not say "heir access" or "helper phone"; if a page is reworded and loses the words an
     * owner would use, the question stops finding it and this fails. Only questions that read as their page today are
     * here — the ones other readers take first ("can the helper change anything", "does it keep a copy of the balance")
     * are the known gap of #32 and are not frozen.
     */
    private val byBody: List<Triple<String, String, String>> = buildList {
        fun of(language: String, key: String, vararg said: String) = said.forEach { add(Triple(language, it, key)) }

        of("en", "screen_waris", "who can open the locked copy of my book on the relays")
        of("en", "screen_viewer", "how do I send a copy of my book to a helper on whatsapp")
        of("en", "screen_duplicates", "what happens to the ledger and bills when I merge")
        of("en", "screen_payment_voice", "does it read my sms")
        of("en", "screen_help", "how do I show the home tour again")
        of("en", "faq_khata_link", "what does the customer see on the khata link")
        of("en", "screen_mera_khata", "where is the balance of shops that sent me a link")

        of("ur-Latn", "screen_waris", "agar main intezar ke waqt mein mana na karun to kya hota hai")
        of("ur-Latn", "screen_viewer", "madadgar ko copy kaise bhejun whatsapp par", "malik ki file kaise kholte hain")
        of("ur-Latn", "screen_duplicates", "merge karne par khata aur bills ka kya hota hai")
        of("ur-Latn", "screen_payment_voice", "notification tak rasai kaise dein", "wallet app tick kaise karun")
        of("ur-Latn", "screen_help", "home ka taaruf dobara kaise dekhun")
        of("ur-Latn", "faq_khata_link", "customer ko apna balance kaise dikhaun")
    }

    @Test fun `a question made of the words of a page's body, not its title, still finds the page`() {
        val wrong = byBody.filter { (language, said, key) -> ask(said, language) != Hudhud.Ask.Help(key) }
            .map { (language, said, key) -> "  [$language] $said  wanted $key, got ${ask(said, language)}" }
        assertTrue("${wrong.size} of ${byBody.size} body-worded questions do not open their page:\n" + wrong.joinToString("\n"),
            wrong.isEmpty())
    }

    // A customer may be called what a page is called.

    private val hazard = listOf("Ali Raza", "Waris", "Madad Ali", "Phone House", "Bashir Ahmad")
        .mapIndexed { i, n -> Hudhud.Name(i + 1L, n) }

    @Test fun `a customer called Waris or Madad is still found, and the page of the same word is still reached`() {
        // Waris is the Heir Access page and a very common name; Madad is Help & Support and a name part.
        val waris = 2L
        val madad = 3L
        assertEquals(Hudhud.Ask.Parties(listOf(waris)), ask("Waris ka balance", "ur-Latn", hazard))
        assertEquals(Hudhud.Ask.Person(listOf(waris), Hudhud.Facet.PHONE), ask("Waris ka number", "ur-Latn", hazard))
        assertEquals(Hudhud.Ask.Person(listOf(waris), Hudhud.Facet.LIMIT), ask("Waris ki limit", "ur-Latn", hazard))
        assertEquals(Hudhud.Ask.Parties(listOf(madad)), ask("Madad Ali ka balance", "ur-Latn", hazard))
        assertEquals(Hudhud.Ask.Person(listOf(madad), Hudhud.Facet.PHONE), ask("Madad Ali ka number", "ur-Latn", hazard))
        assertEquals(Hudhud.Ask.Parties(listOf(waris)), ask("what is Waris's balance", "en", hazard))
        // ... and the page is reached by its own question, with Waris in the book
        assertEquals(Hudhud.Ask.Help("screen_waris"), ask("Waris ki chaabi kaise banaun", "ur-Latn", hazard))
        assertEquals(Hudhud.Ask.Help("screen_waris"), ask("how do I set up heir access", "en", hazard))
    }

    @Test fun `a question is never a feature word, and neither are the six pages whose titles are names`() {
        for (key in listOf("faq_backup", "faq_data", "faq_khata_link")) assertFalse(key, Hudhud.isFeaturePage(key))
        for (key in listOf("screen_duplicates", "screen_help", "screen_mera_khata", "screen_payment_voice", "screen_viewer", "screen_waris")) {
            assertFalse(key, Hudhud.isFeaturePage(key))
        }
        for (key in listOf("screen_zakat", "screen_khata", "screen_bin", "intro_staff", "intro_counter")) {
            assertTrue(key, Hudhud.isFeaturePage(key))
        }
    }

    // The pages tell what the screens say.

    @Test fun `the seven pages are among the pages the tests read`() {
        val keys = HudhudRealPages.rows().map { it.first }
        for (key in listOf("screen_duplicates", "screen_help", "screen_mera_khata", "screen_payment_voice", "screen_viewer",
            "screen_waris", "faq_khata_link")) {
            assertTrue("$key is not in pages.tsv", key in keys)
        }
        assertEquals("a page is listed twice", keys.size, keys.toSet().size)
    }

    /** A page and the labels of its screen's own buttons and menu entries that it tells the owner to tap. */
    private val labelsOf = mapOf(
        "hudhud_page_duplicates" to listOf("duplicate_customers", "duplicate_review_merge", "duplicate_not_duplicate"),
        "hudhud_page_help" to listOf("help_support"),
        "hudhud_page_paisa" to listOf("paisa_title"),
        "hudhud_page_khata_link" to listOf("clink_menu"),
        "hudhud_page_viewer" to listOf("viewer_menu", "viewer_send_file", "viewer_from_file"),
        "hudhud_page_waris" to listOf("waris_title", "waris_turn_on", "waris_add_heir")
    )

    @Test fun `each page names its screen's own buttons word for word in every language`() {
        val wrong = mutableListOf<String>()
        for (language in HudhudRealPages.LANGUAGES) {
            val strings = HudhudRealPages.strings(HudhudRealPages.folderOf(language))
            for ((page, labels) in labelsOf) {
                val body = strings[page]
                if (body == null) { wrong += "[$language] $page is missing"; continue }
                for (label in labels) {
                    val text = strings[label]
                    if (text == null) wrong += "[$language] $label is missing"
                    else if (text !in body) wrong += "[$language] $page does not say \"$text\" ($label)"
                }
            }
        }
        assertTrue("a page tells an owner to tap a label the screen does not have:\n" + wrong.joinToString("\n"), wrong.isEmpty())
    }
}
