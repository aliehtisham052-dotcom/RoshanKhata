package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.Hudhud.Ask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Business knowledge and the plan's limits (Marhala 9, lane B, 26 Nov 2026), read through the whole pipeline with the shipped sense
 * (lesson 145): the atoms' meanings and formulas in nine languages, the worked margin and markup from the sentence's own figures,
 * the refusals (a dose, a medicine, a tax rate, a ruling) 100% on their list, and never a refusal or a knowledge card for the book's
 * own questions ("Urea ka rate" is the product's price). Each guard alone is held by a test of [HudhudKnowledge] called directly
 * (lesson 129). Names and goods are invented.
 */
class HudhudKnowledgeTest {

    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val names = listOf("Ali Raza", "Bashir Ahmad", "Tariq Butt", "Naeem Khan").mapIndexed { i, n -> Hudhud.Name(i + 1L, n) }
    private val goods = listOf("Urea", "DAP", "Confidor", "Panadol").mapIndexed { i, n -> Hudhud.Name(100L + i, n) }

    private fun read(lang: String, said: String, before: Ask? = null) =
        Hudhud.understand(said, names, HudhudRealPages.forApp(lang), lang, goods, before)

    private fun atom(id: String) = Ask.Knowledge(KnowKind.GENERAL_GUIDANCE, atom = id)
    private fun refused(l: KnowLimit) = Ask.Knowledge(KnowKind.UNSUPPORTED, limit = l)
    private fun worked(cost: Double, sale: Double, m: KnowMeasure) =
        Ask.Knowledge(KnowKind.CALCULATED, atom = if (m == KnowMeasure.MARKUP) "K202" else "K201", cost = cost, sale = sale, asked = m)

    // ------------------------------------------------------------------------------------------------ the atoms

    @Test fun `the difference between margin and markup, in the nine languages`() {
        for ((lang, said) in listOf(
            "ur-Latn" to "margin aur markup mein farq", "en" to "what is the difference between margin and markup",
            "ur" to "مارجن اور مارک اپ میں فرق", "hi" to "मार्जिन और मार्कअप में फर्क", "bn" to "মার্জিন আর মার্কআপের পার্থক্য",
            "ar" to "الفرق بين هامش الربح والمارك اب", "fa" to "تفاوت حاشیه سود و مارک آپ", "sd" to "مارجن ۽ مارڪ اپ ۾ فرق",
            "in" to "perbedaan margin dan markup"
        )) assertEquals("[$lang] $said", atom("K203"), read(lang, said))
    }

    @Test fun `what margin is, in the nine languages`() {
        for ((lang, said) in listOf(
            "ur-Latn" to "margin kya hota hai", "en" to "what does margin mean", "ur" to "مارجن کیا ہوتا ہے",
            "hi" to "मार्जिन क्या होता है", "bn" to "মার্জিন কাকে বলে", "ar" to "ما هو هامش الربح", "fa" to "حاشیه سود چیست",
            "sd" to "مارجن ڇا ٿيندو آهي", "in" to "apa itu margin"
        )) assertEquals("[$lang] $said", atom("K201"), read(lang, said))
    }

    @Test fun `break-even and the rest of the atoms`() {
        assertEquals(atom("K205"), read("ur-Latn", "break-even kaise nikalein"))
        assertEquals(atom("K205"), read("en", "how to calculate break even point"))
        assertEquals(atom("K205"), read("ur", "بریک ایون کیسے نکالیں"))
        assertEquals(atom("K205"), read("in", "cara menghitung titik impas"))
        assertEquals(atom("K205"), read("ar", "كيف احسب نقطة التعادل"))
        assertEquals(atom("K202"), read("ur-Latn", "markup kya hai"))
        assertEquals(atom("K202"), read("en", "what is markup"))
        assertEquals(atom("K204"), read("en", "what is contribution margin"))
        assertEquals(atom("K204"), read("ur-Latn", "contribution margin kise kehte hain"))
        assertEquals(atom("K206"), read("ur-Latn", "discount aur markup mein farq"))
        assertEquals(atom("K206"), read("en", "difference between discount and markup"))
        assertEquals(atom("K101"), read("en", "what does accounts receivable mean"))
        assertEquals(atom("K102"), read("in", "apa itu utang usaha"))
        assertEquals(atom("K103"), read("ur-Latn", "net balance ka matlab"))
        assertEquals(atom("K103"), read("ur-Latn", "net balance kya hota hai"))
        assertEquals(atom("K103"), read("en", "what does net balance mean"))
        assertEquals(atom("K104"), read("en", "what does credit concentration mean"))
    }

    // ------------------------------------------------------------------------------------------------ worked figures

    @Test fun `800 cost 1000 sale, the owner's example, in the languages that say it`() {
        assertEquals(worked(800.0, 1000.0, KnowMeasure.MARGIN), read("ur-Latn", "800 ki cheez 1000 mein bechi margin kitna"))
        assertEquals(worked(800.0, 1000.0, KnowMeasure.MARKUP), read("ur-Latn", "800 ki cheez 1000 mein bechi markup kitna"))
        assertEquals(worked(800.0, 1000.0, KnowMeasure.PROFIT), read("ur-Latn", "800 ki cheez 1000 mein bechi munafa kitna"))
        assertEquals(worked(800.0, 1000.0, KnowMeasure.MARGIN), read("en", "bought for 800, sold for 1000, what is the margin?"))
        assertEquals(worked(800.0, 1000.0, KnowMeasure.MARKUP), read("en", "cost 800 price 1,000 markup?"))
        assertEquals(worked(800.0, 1000.0, KnowMeasure.MARGIN), read("ur", "800 کی چیز 1000 میں بیچی، مارجن کتنا؟"))
        assertEquals(worked(800.0, 1000.0, KnowMeasure.MARGIN), read("hi", "800 की चीज़ 1000 में बेची, मार्जिन कितना?"))
        assertEquals(worked(800.0, 1000.0, KnowMeasure.MARGIN), read("in", "harga beli 800, harga jual 1000, berapa margin?"))
        assertEquals(worked(800.0, 1000.0, KnowMeasure.MARGIN), read("ar", "التكلفة 800، سعر البيع 1000، كم هامش الربح؟"))
        assertEquals(worked(800.0, 1000.0, KnowMeasure.MARGIN), read("fa", "قیمت خرید 800، قیمت فروش 1000، حاشیه سود چقدر است؟"))
        assertEquals(worked(800.0, 1000.0, KnowMeasure.MARGIN), read("sd", "خريد 800، وڪرو 1000، مارجن ڪيترو؟"))
        assertEquals(worked(800.0, 1000.0, KnowMeasure.MARGIN), read("bn", "কেনা 800, বিক্রি 1000, মার্জিন কত?"))
        // A sale at a loss is a real sale: the roles are said, never guessed from which figure is larger.
        assertEquals(worked(1000.0, 800.0, KnowMeasure.MARGIN), read("ur-Latn", "1000 ki cheez 800 mein bechi margin kitna"))
        assertEquals(worked(800.0, 1250.5, KnowMeasure.MARGIN), read("en", "cost 800 sale price 1250.50 margin?"))
    }

    @Test fun `the example of each language's need-the-roles line is itself read`() {
        // hudhud_kn_need_roles tells the owner how to say it: each language's own example must work (checked against strings.xml).
        val folders = mapOf(
            "ur-Latn" to "values-b+ur+Latn", "en" to "values", "ur" to "values-ur", "hi" to "values-hi", "bn" to "values-bn",
            "ar" to "values-ar", "fa" to "values-fa", "sd" to "values-sd", "in" to "values-in"
        )
        val res = listOf(File("../app/src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        for ((lang, folder) in folders) {
            val xml = File(res, "$folder/strings.xml").readText()
            val line = Regex("<string name=\"hudhud_kn_need_roles\">(.*?)</string>").find(xml)!!.groupValues[1]
            val example = line.substringAfter(':').trim()
            assertEquals("[$lang] $example", worked(800.0, 1000.0, KnowMeasure.MARGIN), read(lang, example))
        }
    }

    @Test fun `two figures whose roles are not said are asked about, never guessed`() {
        val ask = Ask.Knowledge(KnowKind.NEEDS_CLARIFICATION, asked = KnowMeasure.MARGIN)
        assertEquals(ask, HudhudKnowledge.read("800 aur 1000 ka margin kitna"))
        assertEquals(ask, HudhudKnowledge.read("cost 800 cost 1000 margin"))
    }

    @Test fun `a figure that reads two ways is not worked`() {
        assertNull(HudhudKnowledge.read("cost 1.000 sale 1.200 margin"))
        assertNull(HudhudKnowledge.read("800 ki cheez ek hazar mein bechi margin kitna"))
    }

    // ------------------------------------------------------------------------------------------------ the limits: 100%

    private val PESTICIDE = listOf(
        "ur-Latn" to "confidor ka dose kitna hai per acre", "ur-Latn" to "spray mein kitni dawai dalni hai",
        "ur-Latn" to "gandum par spray ke kitne din baad katai karein waiting period", "ur-Latn" to "keetnashak kitna milana hai tanki mein",
        "en" to "what is the dose of this pesticide per acre", "en" to "how much insecticide should I mix in the tank",
        "en" to "what is the pre harvest interval for this spray", "ur" to "سپرے کی ڈوز فی ایکڑ کتنی ہے",
        "ur" to "کپاس پر کتنی دوائی ڈالنی ہے", "hi" to "कीटनाशक की डोज प्रति एकड कितनी है", "hi" to "फसल पर कितना स्प्रे डालना है",
        "bn" to "প্রতি একর কীটনাশক কত দেব", "ar" to "ما جرعة المبيد للفدان", "fa" to "دوز سم در هکتار چقدر است",
        "sd" to "اسپري جو ڊوز في ايڪڙ ڪيترو آهي", "in" to "berapa dosis pestisida per hektar"
    )
    private val MEDICINE = listOf(
        "ur-Latn" to "bachay ko bukhar hai kaunsi dawai dein", "ur-Latn" to "panadol ki kitni goli leni chahiye",
        "ur-Latn" to "sar dard ke liye kya ilaj hai", "en" to "how many tablets should a child take for fever",
        "en" to "what is the dose of this syrup for a baby", "ur" to "بخار کے لیے کون سی دوا لیں", "ur" to "کتنی گولیاں کھانی ہیں",
        "hi" to "बुखार में कितनी गोली लेनी है", "bn" to "জ্বরে কোন ওষুধ খাব", "ar" to "كم حبة يأخذ الطفل", "fa" to "برای تب چه دارویی بخورم",
        "sd" to "بخار لاءِ ڪهڙي دوا", "in" to "berapa tablet obat demam untuk anak"
    )
    private val DOSE = listOf("ur-Latn" to "urea kitna dalna hai", "en" to "what is the dosage", "ur-Latn" to "dawai kitni milani hai")
    private val TAX = listOf(
        "ur-Latn" to "gst ka rate kya hai", "ur-Latn" to "sales tax kitna lagta hai", "ur-Latn" to "income tax ki slab kya hai",
        "en" to "what is the sales tax rate in pakistan", "en" to "what percent is withholding tax", "ur" to "سیلز ٹیکس کا ریٹ کیا ہے",
        "hi" to "जीएसटी की दर क्या है", "bn" to "ভ্যাটের হার কত", "ar" to "ما نسبة ضريبة القيمة المضافة", "fa" to "نرخ مالیات چقدر است",
        "sd" to "ٽيڪس جو ريٽ ڇا آهي", "in" to "berapa persen tarif pajak ppn"
    )
    private val RULING = listOf(
        "ur-Latn" to "kya udhaar par munafa lena jaiz hai", "ur-Latn" to "is ka fatwa kya hai",
        "ur-Latn" to "kya ye sood halal hai", "en" to "is it halal to charge interest on credit", "en" to "is this permissible in islam",
        "ur" to "کیا ادھار پر منافع لینا جائز ہے", "hi" to "क्या यह जायज है", "bn" to "এটা কি জায়েজ", "ar" to "هل يجوز أخذ الربح على الدين",
        "fa" to "آیا این کار جایز است", "sd" to "ڇا اهو جائز آهي", "in" to "apakah riba itu haram"
    )

    @Test fun `every refusal on the list is refused, with its kind`() {
        for ((list, kind) in listOf(PESTICIDE to KnowLimit.PESTICIDE, MEDICINE to KnowLimit.MEDICINE, DOSE to KnowLimit.DOSE,
            TAX to KnowLimit.TAX_RATE, RULING to KnowLimit.RULING)) {
            for ((lang, said) in list) assertEquals("[$lang] $said", refused(kind), read(lang, said))
        }
    }

    @Test fun `a refusal starting with if is still a refusal`() {
        assertEquals(refused(KnowLimit.PESTICIDE), read("ur-Latn", "agar gandum par spray karun to per acre dose kitna hoga"))
        assertEquals(refused(KnowLimit.MEDICINE), read("en", "if my child has fever how many tablets should he take"))
    }

    // ------------------------------------------------------------------------------------------------ never on the book's own

    @Test fun `the book's own questions are not knowledge and not refused`() {
        for ((lang, said) in listOf(
            "ur-Latn" to "Urea ka rate", "ur-Latn" to "Urea ka rate kya hai", "ur-Latn" to "Urea kitna bacha hai",
            "ur-Latn" to "is mahine kitna tax laga", "ur-Latn" to "aaj ki sale", "ur-Latn" to "is mahine munafa kitna hua",
            "ur-Latn" to "Ali Raza ka balance", "ur-Latn" to "Ali Raza ke khate mein kitna dalna hai", "ur-Latn" to "Panadol kitni bachi hain",
            "ur-Latn" to "dawai ki sale kitni hui", "ur-Latn" to "zakat kitni banti hai", "ur-Latn" to "mera margin kitna hai",
            "en" to "what is my profit this month", "en" to "how much tax did I collect this month", "en" to "what is the rate of Urea",
            "ur-Latn" to "Urea ka tax rate", "ur-Latn" to "kitna lena hai", "ur-Latn" to "Ali Raza ke bachay ko kitna dena hai",
            "ur-Latn" to "800 ki cheez 1000 mein bechi", "ur-Latn" to "margin"
        )) {
            val got = read(lang, said)
            assertFalse("[$lang] $said read as $got", got is Ask.Knowledge)
        }
    }

    @Test fun `a sentence with a name, a span or another word is never knowledge`() {
        assertNull(HudhudKnowledge.read("Ali Raza ka margin kya hota hai", isPerson = { it == "ali" || it == "raza" }))
        assertNull(HudhudKnowledge.read("is mahine margin kya hota hai"))
        assertNull(HudhudKnowledge.read("margin kya hota hai cricket"))
        assertNull(HudhudKnowledge.read("margin aur markup"))
        assertNull(HudhudKnowledge.read("margin mein farq"))
    }

    // ------------------------------------------------------------------------------------------------ each guard alone (lesson 129)

    @Test fun `a topic with no cue says nothing`() {
        assertNull(HudhudKnowledge.read("markup"))
        assertNull(HudhudKnowledge.read("break even"))
        assertNull(HudhudKnowledge.read("discount aur markup"))
    }

    @Test fun `a topic word that is a product's name is the product's`() {
        assertNull(HudhudKnowledge.read("margin kya hota hai", isGoods = { it == "margin" }))
        assertNull(HudhudKnowledge.read("markup kise kehte hain", isPerson = { it == "markup" }))
    }

    @Test fun `a refusal said beside a word off the book stays a refusal`() {
        assertEquals(refused(KnowLimit.PESTICIDE), read("ur-Latn", "mausam kaisa hai aur spray ka dose per acre kitna hai"))
    }

    @Test fun `a weak what-is beside a figure the book has is not read, a strong one is`() {
        assertNull(HudhudKnowledge.read("margin kya hai"))
        assertNull(HudhudKnowledge.read("net balance kya hai"))
        assertEquals(atom("K201"), HudhudKnowledge.read("margin kya hota hai"))
        assertEquals(atom("K202"), HudhudKnowledge.read("markup kya hai"))
    }

    @Test fun `my beside a meaning is the book's own figure`() {
        assertNull(HudhudKnowledge.read("mera margin kya hota hai"))
        assertNull(HudhudKnowledge.read("what does my margin mean"))
    }

    @Test fun `a word of the book beside a dose cue keeps it the book's`() {
        assertNull(HudhudKnowledge.limitOf("is bill mein kitna dalna hai urea"))
        assertNull(HudhudKnowledge.limitOf("dose ka stock kitna hai"))
        assertEquals(KnowLimit.DOSE, HudhudKnowledge.limitOf("dose kitna hai"))
        assertNull(HudhudKnowledge.limitOf("is bill mein urea kitna dalna hai", isGoods = { it == "urea" }))
        assertEquals(KnowLimit.DOSE, HudhudKnowledge.limitOf("urea kitna dalna hai", isGoods = { it == "urea" }))
    }

    @Test fun `a product named with a tax rate is the book's own tax rate`() {
        assertNull(HudhudKnowledge.limitOf("urea ka gst rate", isGoods = { it == "urea" }))
        assertEquals(KnowLimit.TAX_RATE, HudhudKnowledge.limitOf("urea ka gst rate"))
        assertNull(HudhudKnowledge.limitOf("gst rate laga"))
    }

    @Test fun `halal beside a figure or a book word is not a ruling, a ruling word always is`() {
        assertNull(HudhudKnowledge.limitOf("halal gosht ki sale"))
        assertNull(HudhudKnowledge.limitOf("halal gosht ka bill"))
        assertNull(HudhudKnowledge.limitOf("halal maal ka hisaab"))
        assertNull(HudhudKnowledge.limitOf("halal 500"))
        assertEquals(KnowLimit.RULING, HudhudKnowledge.limitOf("kya ye halal hai"))
        assertEquals(KnowLimit.RULING, HudhudKnowledge.limitOf("khata rakhna jaiz hai"))
        assertNull(HudhudKnowledge.limitOf("Halal Traders ka hisaab", isPerson = { it == "halal" || it == "traders" }))
    }

    @Test fun `zakat is the app's own page, a word of what is allowed beside it is a ruling`() {
        assertNull(HudhudKnowledge.limitOf("zakat kaise nikalein"))
        // The owner's own sentence (owner_1000 K0805) reads the Zakat page: who owes Zakat is the mainstream rule, not a dispute.
        assertNull(HudhudKnowledge.limitOf("zakat kis par farz hai"))
        assertEquals(KnowLimit.RULING, HudhudKnowledge.limitOf("udhaar par zakat dena jaiz hai"))
    }

    @Test fun `taking and giving are a medicine's only with a medicine, never beside a named person`() {
        assertNull(HudhudKnowledge.limitOf("kitna dena hai"))
        assertNull(HudhudKnowledge.limitOf("ali ke bachay ko kitna dena hai", isPerson = { it == "ali" }))
        assertEquals(KnowLimit.MEDICINE, HudhudKnowledge.limitOf("bachay ko kitni goli deni hai"))
        assertEquals(KnowLimit.DOSE, HudhudKnowledge.limitOf("dawai kitni leni hai"))
    }

    @Test fun `putting in goes with a field, a product or dawai, never alone`() {
        assertNull(HudhudKnowledge.limitOf("kitna dalna hai"))
        assertEquals(KnowLimit.DOSE, HudhudKnowledge.limitOf("urea kitna dalna hai", isGoods = { it == "urea" }))
        assertEquals(KnowLimit.PESTICIDE, HudhudKnowledge.limitOf("khet mein kitna dalna hai"))
    }

    @Test fun `an illness with a cure is medicine, an illness alone is not`() {
        assertEquals(KnowLimit.MEDICINE, HudhudKnowledge.limitOf("bukhar ka ilaj"))
        assertNull(HudhudKnowledge.limitOf("bukhar"))
    }

    // ------------------------------------------------------------------------------------------------ the atoms' table

    @Test fun `the table the app ships is atoms tsv exactly`() {
        val tsv = listOf(File("hudhud/knowledge/atoms.tsv"), File("shared/hudhud/knowledge/atoms.tsv")).first { it.isFile }
        assertEquals(KnowledgeAtom.parse(tsv.readLines()), HudhudKnowledgeAtoms.ALL)
    }

    @Test fun `every atom the reader names is in the table, and unreviewed atoms say guidance`() {
        for (id in listOf("K101", "K102", "K103", "K104", "K201", "K202", "K203", "K204", "K205", "K206")) {
            val a = HudhudKnowledge.atom(id)!!
            assertEquals(if (a.reviewed) KnowKind.VERIFIED else KnowKind.GENERAL_GUIDANCE, a.kind)
            assertTrue(id, a.sources.isNotEmpty() && a.sources.size == a.tiers.size)
        }
        assertEquals(KnowKind.VERIFIED, HudhudKnowledge.atom("K201")!!.copy(reviewedBy = "owner:2026-12-01").kind)
        assertNotEquals(KnowKind.VERIFIED, HudhudKnowledge.atom("K201")!!.copy(reviewedBy = KnowledgeAtom.PENDING).kind)
    }
}
