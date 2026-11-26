package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.Hudhud.Ask
import com.innovation313.roshankhata.words.W
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Business knowledge, modules 3 to 8 (Marhala 9, lane B; K2, 26 Nov 2026): cash flow, stock, credit and collection, tax words
 * (never a rate), the Zakat page's own figures (a ruling goes to a scholar) and trade basics. Read through the whole pipeline with
 * the shipped sense (lesson 145): one atom of each module in all nine languages, the others in the languages the owner speaks,
 * and the book's own questions never taken. Each guard the modules added is held alone by a test of [HudhudKnowledge] called
 * directly (lesson 129). Names and goods are invented.
 */
class HudhudKnowledgeModulesTest {

    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val names = listOf("Ali Raza", "Bashir Ahmad", "Tariq Butt", "Naeem Khan").mapIndexed { i, n -> Hudhud.Name(i + 1L, n) }
    private val goods = listOf("Urea", "DAP", "Confidor", "Panadol").mapIndexed { i, n -> Hudhud.Name(100L + i, n) }

    private fun read(lang: String, said: String) = Hudhud.understand(said, names, HudhudRealPages.forApp(lang), lang, goods, null)
    private fun atom(id: String) = Ask.Knowledge(KnowKind.GENERAL_GUIDANCE, atom = id)
    private fun nine(id: String, list: List<Pair<String, String>>) {
        assertEquals("nine languages", 9, list.map { it.first }.toSet().size)
        for ((lang, said) in list) assertEquals("[$lang] $said", atom(id), read(lang, said))
    }

    // ------------------------------------------------------------------------------------------------ module 3: cash flow

    @Test fun `module 3, the cash cycle in the nine languages`() = nine("K302", listOf(
        "ur-Latn" to "cash cycle kya hota hai", "en" to "what is the cash conversion cycle", "ur" to "کیش سائیکل کیا ہوتا ہے",
        "hi" to "नकदी चक्र क्या होता है", "bn" to "নগদ চক্র কাকে বলে", "ar" to "ما هي الدورة النقدية", "fa" to "چرخه نقدی چیست",
        "sd" to "ڪيش سائيڪل ڇا ٿيندو آهي", "in" to "apa itu siklus kas"
    ))

    @Test fun `module 3, cash against profit and credit sales against cash`() {
        assertEquals(atom("K301"), read("ur-Latn", "cash aur munafa mein farq"))
        assertEquals(atom("K301"), read("en", "what is the difference between cash and profit"))
        assertEquals(atom("K301"), read("ur", "کیش اور منافع میں فرق"))
        assertEquals(atom("K303"), read("ur-Latn", "udhaar sale ka cash par asar"))
        assertEquals(atom("K303"), read("en", "why do credit sales hurt cash"))
        assertEquals(atom("K303"), read("ur-Latn", "udhaar sale kya hoti hai"))
    }

    // ------------------------------------------------------------------------------------------------ module 4: stock

    @Test fun `module 4, FIFO and FEFO in the nine languages`() = nine("K404", listOf(
        "ur-Latn" to "fefo kya hota hai", "en" to "what does fifo mean", "ur" to "فیفو کیا ہوتا ہے", "hi" to "फीफो क्या होता है",
        "bn" to "ফিফো কাকে বলে", "ar" to "ما معنى الوارد أولا صادر أولا", "fa" to "فیفو یعنی چه", "sd" to "فيفو ڇا ٿيندو آهي",
        "in" to "apa itu fifo"
    ))

    @Test fun `module 4, stock cover, reorder point and dead stock`() {
        assertEquals(atom("K401"), read("ur-Latn", "stock cover kya hota hai"))
        assertEquals(atom("K401"), read("ur-Latn", "stock cover kaise nikalein"))
        assertEquals(atom("K402"), read("en", "what is a reorder point"))
        assertEquals(atom("K402"), read("ur-Latn", "reorder point kaise nikalein"))
        assertEquals(atom("K403"), read("ur-Latn", "dead stock kya hota hai"))
        assertEquals(atom("K404"), read("ur-Latn", "fifo aur fefo mein farq"))
    }

    // ------------------------------------------------------------------------------------------------ module 5: credit and collection

    @Test fun `module 5, the credit limit in the nine languages`() = nine("K501", listOf(
        "ur-Latn" to "credit limit kya hoti hai", "en" to "what does credit limit mean", "ur" to "کریڈٹ لمٹ کیا ہوتی ہے",
        "hi" to "क्रेडिट लिमिट का मतलब", "bn" to "ক্রেডিট লিমিট মানে কী", "ar" to "ما معنى حد الائتمان", "fa" to "سقف اعتبار یعنی چه",
        "sd" to "ڪريڊٽ لمٽ جو مطلب", "in" to "apa yang dimaksud dengan batas kredit"
    ))

    @Test fun `module 5, ageing, a reminder's tone and Qarz-e-Hasna`() {
        assertEquals(atom("K502"), read("en", "what does receivables aging mean"))
        assertEquals(atom("K502"), read("ur-Latn", "ageing kya hoti hai"))
        assertEquals(atom("K503"), read("ur-Latn", "reminder ka lehja kaisa hona chahiye"))
        assertEquals(atom("K504"), read("ur-Latn", "qarz e hasna kya hota hai"))
        assertEquals(atom("K504"), read("ur-Latn", "qarz e hasna aur udhaar sale mein farq"))
    }

    // ------------------------------------------------------------------------------------------------ module 6: tax words, no rate

    @Test fun `module 6, sales tax in the nine languages`() = nine("K601", listOf(
        "ur-Latn" to "sales tax kya hota hai", "en" to "what is sales tax", "ur" to "سیلز ٹیکس کیا ہوتا ہے", "hi" to "जीएसटी क्या होता है",
        "bn" to "ভ্যাট কাকে বলে", "ar" to "ما هي ضريبة القيمة المضافة", "fa" to "مالیات بر ارزش افزوده چیست",
        "sd" to "سيلز ٽيڪس ڇا ٿيندو آهي", "in" to "apa itu pajak penjualan"
    ))

    @Test fun `module 6, withholding and tax inside or on top, and a rate is still refused`() {
        assertEquals(atom("K602"), read("en", "what is withholding tax"))
        assertEquals(atom("K603"), read("ur-Latn", "tax inclusive ka matlab"))
        assertEquals(atom("K603"), read("ur-Latn", "tax inclusive aur tax exclusive mein farq"))
        val refused = Ask.Knowledge(KnowKind.UNSUPPORTED, limit = KnowLimit.TAX_RATE)
        assertEquals(refused, read("ur-Latn", "sales tax ka rate kya hai"))
        assertEquals(refused, read("en", "what percent is withholding tax"))
        assertEquals(refused, read("ur-Latn", "gst kitna lagta hai"))
    }

    @Test fun `module 6, every tax atom names the FBR and no rate`() {
        for (id in listOf("K601", "K602", "K603")) {
            val a = HudhudKnowledge.atom(id)!!
            assertEquals(id, W.hudhud_kn_tax_verify, a.exception)
        }
        assertTrue(HudhudKnowledge.atom("K601")!!.sources.single().startsWith("Federal Board of Revenue"))
        assertTrue(HudhudKnowledge.atom("K603")!!.sources.any { it.startsWith("Federal Board of Revenue") })
    }

    // ------------------------------------------------------------------------------------------------ module 7: the Zakat page

    @Test fun `module 7, Qarz-e-Hasna and Zakat in the nine languages`() = nine("K702", listOf(
        "ur-Latn" to "qarz e hasna par zakat", "en" to "zakat on qard hasan", "ur" to "قرض حسنہ پر زکوٰۃ", "hi" to "कर्ज़-ए-हसना पर ज़कात",
        "bn" to "কর্জে হাসানা আর যাকাত", "ar" to "زكاة القرض الحسن", "fa" to "زکات قرض الحسنه", "sd" to "قرض حسنو ۽ زڪوات",
        "in" to "zakat atas qardhul hasan"
    ))

    @Test fun `module 7, nisab and doubtful debts, and the Zakat page keeps its own questions`() {
        assertEquals(atom("K701"), read("ur-Latn", "nisab kya hota hai"))
        assertEquals(atom("K703"), read("ur-Latn", "doubtful udhaar par zakat"))
        assertEquals(atom("K703"), read("en", "zakat on doubtful debts"))
        for (said in listOf("zakat kya hai", "zakat kaise nikalein", "nisab kya hai", "zakat kis par farz hai")) {
            assertEquals(said, Ask.Help("screen_zakat"), read("ur-Latn", said))
        }
        // A word that asks what is allowed beside them is still a ruling, never an atom.
        assertEquals(Ask.Knowledge(KnowKind.UNSUPPORTED, limit = KnowLimit.RULING), read("ur-Latn", "qarz e hasna par zakat dena jaiz hai"))
    }

    @Test fun `module 7, every atom sends a ruling to a scholar`() {
        for (id in listOf("K504", "K701", "K702", "K703")) assertTrue(id, HudhudKnowledge.atom(id)!!.exception != null)
    }

    // ------------------------------------------------------------------------------------------------ module 8: trade basics

    @Test fun `module 8, batch and expiry in the nine languages`() = nine("K802", listOf(
        "ur-Latn" to "batch number kya hota hai", "en" to "what does batch number mean", "ur" to "بیچ نمبر کیا ہوتا ہے",
        "hi" to "बैच नंबर का मतलब", "bn" to "ব্যাচ নম্বর মানে কী", "ar" to "ما معنى رقم التشغيلة", "fa" to "شماره سری ساخت یعنی چه",
        "sd" to "بيچ نمبر جو مطلب", "in" to "apa yang dimaksud dengan nomor batch"
    ))

    @Test fun `module 8, packs, the price list and its two prices`() {
        assertEquals(atom("K801"), read("ur-Latn", "pack aur unit price mein farq"))
        assertEquals(atom("K801"), read("ur-Latn", "unit price kya hota hai"))
        assertEquals(atom("K802"), read("ur-Latn", "expiry date ka matlab"))
        assertEquals(atom("K803"), read("ur-Latn", "price list kya hoti hai"))
        assertEquals(atom("K803"), read("ur-Latn", "cash price aur credit price mein farq"))
        // How to make one is the app's own page.
        assertEquals(Ask.Help("screen_rates"), read("ur-Latn", "rate list kaise banaun"))
    }

    // ------------------------------------------------------------------------------------------------ never the book's own

    @Test fun `the book's own questions are never taken by the new atoms`() {
        for ((lang, said) in listOf(
            "ur-Latn" to "Urea ka rate", "ur-Latn" to "Ali Raza ka baqaya", "ur-Latn" to "is mahine tax kitna laga",
            "ur-Latn" to "cash kitna hai", "ur-Latn" to "Urea ka batch", "ur-Latn" to "Urea ki expiry kab hai",
            "ur-Latn" to "Ali Raza ki credit limit", "ur-Latn" to "qarz e hasna kitna diya", "ur-Latn" to "qarz e hasna kya hai",
            "ur-Latn" to "credit limit kya hai", "ur-Latn" to "dead stock kya hai", "ur-Latn" to "reminder kaise bhejein",
            "ur-Latn" to "aaj cash kitna aaya", "ur-Latn" to "Urea kitne din chalegi", "en" to "what is my credit limit",
            "ur-Latn" to "kis ka udhaar sab se purana hai", "ur-Latn" to "Maine kis ko qarz-e-hasna diya hai",
            "ur-Latn" to "Qarz-e-hasna mein koi sood to nahi", "ur-Latn" to "Rate list dikhao"
        )) {
            val got = read(lang, said)
            assertFalse("[$lang] $said read as $got", got is Ask.Knowledge)
        }
    }

    // ------------------------------------------------------------------------------------------------ each guard alone (lesson 129)

    @Test fun `cash with profit needs a difference or an effect`() {
        assertNull(HudhudKnowledge.read("cash aur munafa"))
        assertEquals(atom("K301"), HudhudKnowledge.read("cash aur munafa mein farq"))
        assertEquals(atom("K301"), HudhudKnowledge.read("munafa ka cash par asar"))
    }

    @Test fun `a word that only names a pair is not read alone`() {
        assertNull(HudhudKnowledge.read("cash kya hota hai"))
        assertNull(HudhudKnowledge.read("zakat kya hota hai"))
        assertNull(HudhudKnowledge.read("doubtful debt kya hota hai"))
        assertNull(HudhudKnowledge.read("cash cycle aur zakat"))
        // Only the left-over guard refuses these: a topic with its cue, and a pair word beside it that names no pair.
        assertNull(HudhudKnowledge.read("cash cycle aur zakat ka matlab"))
        assertNull(HudhudKnowledge.read("stock cover aur cash ka matlab"))
    }

    @Test fun `Urdu's bare word for batch is also sell, so only the number is the batch`() {
        assertNull(HudhudKnowledge.read("یہ بیچ کیا ہوتا ہے"))
        assertEquals(atom("K802"), HudhudKnowledge.read("بیچ نمبر کیا ہوتا ہے"))
    }

    @Test fun `how beside a topic the app has a page for is the page's`() {
        assertNull(HudhudKnowledge.read("price list kaise nikalein"))
        assertNull(HudhudKnowledge.read("nisab kaise nikalein"))
        assertNull(HudhudKnowledge.read("credit limit kaise nikalein"))
        assertEquals(atom("K401"), HudhudKnowledge.read("stock cover kaise nikalein"))
        assertEquals(atom("K803"), HudhudKnowledge.read("price list ka matlab"))
    }

    @Test fun `a weak what-is beside a topic the book has a figure of is not read`() {
        for (said in listOf("credit limit kya hai", "dead stock kya hai", "qarz e hasna kya hai", "expiry kya hai", "stock cover kya hai")) {
            assertNull(said, HudhudKnowledge.read(said))
        }
        assertEquals(atom("K402"), HudhudKnowledge.read("reorder point kya hai"))
        assertEquals(atom("K601"), HudhudKnowledge.read("sales tax kya hai"))
    }

    @Test fun `a pair that is no question of its own needs its cue`() {
        assertNull(HudhudKnowledge.read("qarz e hasna aur udhaar sale"))
        assertNull(HudhudKnowledge.read("pack aur unit price"))
        assertNull(HudhudKnowledge.read("cash price aur credit price"))
        assertEquals(atom("K702"), HudhudKnowledge.read("qarz e hasna zakat"))
    }

    @Test fun `two topics of two atoms, a name or a figure, and nothing is read`() {
        assertNull(HudhudKnowledge.read("stock cover aur credit limit ka matlab"))
        assertNull(HudhudKnowledge.read("Ali Raza ki credit limit kya hoti hai", isPerson = { it == "ali" || it == "raza" }))
        assertNull(HudhudKnowledge.read("cash 800 sale 1000 margin kitna"))
        assertNull(HudhudKnowledge.read("is mahine sales tax kya hota hai"))
    }

    @Test fun `every new atom is in the table and reached by some sentence`() {
        val reach = mapOf(
            "K301" to "cash aur munafa mein farq", "K302" to "cash cycle kya hota hai", "K303" to "udhaar sale kya hoti hai",
            "K401" to "stock cover kya hota hai", "K402" to "reorder point kya hai", "K403" to "dead stock kya hota hai",
            "K404" to "fefo kya hai", "K501" to "credit limit kya hoti hai", "K502" to "ageing kya hoti hai",
            "K503" to "reminder ka lehja kaisa hona chahiye", "K504" to "qarz e hasna kya hota hai", "K601" to "sales tax kya hai",
            "K602" to "withholding tax kya hota hai", "K603" to "tax inclusive ka matlab", "K701" to "nisab kya hota hai",
            "K702" to "qarz e hasna par zakat", "K703" to "doubtful udhaar par zakat", "K801" to "unit price kya hota hai",
            "K802" to "batch number kya hota hai", "K803" to "price list kya hoti hai"
        )
        val shipped = HudhudKnowledgeAtoms.ALL.map { it.id }.filter { it[1] in '3'..'8' }
        assertEquals(reach.keys.sorted(), shipped.sorted())
        for ((id, said) in reach) {
            val a = HudhudKnowledge.atom(id)!!
            assertEquals(id, KnowledgeAtom.PENDING, a.reviewedBy)
            assertEquals(said, atom(id), HudhudKnowledge.read(said))
            assertTrue(id, a.sources.size == a.tiers.size && a.tiers.all { it in 1..4 })
        }
        // The owner, 26 Nov 2026: OpenStax's pages forbid ingestion into AI offerings, so no atom of any module cites it.
        for (a in HudhudKnowledgeAtoms.ALL) assertFalse(a.id, a.sources.any { it.contains("OpenStax", ignoreCase = true) })
    }
}
