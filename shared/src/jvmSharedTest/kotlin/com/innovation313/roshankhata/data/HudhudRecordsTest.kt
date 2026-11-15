package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.Hudhud.Ask
import com.innovation313.roshankhata.data.Hudhud.GoodsFacet
import com.innovation313.roshankhata.data.Hudhud.PaperOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 15 Nov 2026, the catalog's GAP group "records" (importance 8: stock, cost and supplier money): what a product's label and pack
 * say (barcode, registration, category, tax, shelf place, batches, when it was added, its scheme), the papers by what they hold
 * (a supplier bill's number and lines, a purchase order's state, an invoice's lines) and how the company schemes stand. Each
 * sentence goes through the whole pipeline with the learnt sense (lesson 145). Every name, figure and code is invented (lesson 80).
 */
class HudhudRecordsTest {

    private val names = listOf(Hudhud.Name(1, "Ali Raza"), Hudhud.Name(2, "Bashir Ahmad"), Hudhud.Name(3, "Tariq Butt"))
    private val goods = listOf(
        Hudhud.Name(100, "Urea"), Hudhud.Name(101, "DAP"), Hudhud.Name(200, "یوریا"), Hudhud.Name(202, "यूरिया"),
        Hudhud.Name(204, "يوريا"), Hudhud.Name(206, "ইউরিয়া"),
    )
    private val topics = listOf("intro_documents", "intro_cashbook", "screen_bills", "screen_schemes", "intro_cheques")
        .map { Hudhud.Topic(it, it, it) }

    private fun heard(s: String, lang: String, before: Ask? = null) =
        Hudhud.understand(s, names, topics, lang, goods, before)

    private fun facet(s: String, lang: String, want: GoodsFacet, id: Long) {
        val a = heard(s, lang)
        assertEquals("$s [$lang]", want, (a as? Ask.Goods)?.facet)
        assertEquals("$s [$lang] id", listOf(id), a.let { (it as Ask.Goods).ids }.filter { it == id })
    }

    private fun paper(s: String, lang: String, want: PaperOf) {
        val a = heard(s, lang)
        assertEquals("$s [$lang]", want, (a as? Ask.Paper)?.of)
    }

    // ------------------------------------------------------------------------------------------------ a product's fields

    @Test fun `a product's barcode is asked in nine languages`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        facet("Urea ka barcode", "ur-Latn", GoodsFacet.BARCODE, 100)
        facet("What is the barcode of Urea", "en", GoodsFacet.BARCODE, 100)
        facet("یوریا کا بارکوڈ", "ur", GoodsFacet.BARCODE, 200)
        facet("यूरिया का बारकोड क्या है", "hi", GoodsFacet.BARCODE, 202)
        facet("ما هو باركود يوريا", "ar", GoodsFacet.BARCODE, 204)
        facet("ইউরিয়ার বারকোড কী", "bn", GoodsFacet.BARCODE, 206)
        facet("Urea جو بارڪوڊ ڇا آهي", "sd", GoodsFacet.BARCODE, 100)
        facet("Barcode Urea apa", "in", GoodsFacet.BARCODE, 100)
        facet("بارکد Urea چیست", "fa", GoodsFacet.BARCODE, 100)
    }

    @Test fun `a code read the other way round asks whose it is, and a phone number is not a code`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        assertEquals(Ask.NeedGoods(GoodsFacet.BARCODE, "8964000123456"), heard("barcode 8964000123456 kis ka hai", "ur-Latn"))
        assertEquals(Ask.NeedGoods(GoodsFacet.BARCODE, "8964000123456"), heard("Which product has the code 8964000123456", "en"))
        assertNotEquals(GoodsFacet.BARCODE, ((heard("Ali Raza ka number 03001234567", "ur-Latn")) as? Ask.NeedGoods)?.facet)
    }

    @Test fun `the label facts are the registration, the technical name, the formulation and the category`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        facet("Urea ka registration number kya hai", "ur-Latn", GoodsFacet.LABEL, 100)
        facet("Urea ki category kya hai", "ur-Latn", GoodsFacet.LABEL, 100)
        facet("What is the technical name of Urea", "en", GoodsFacet.LABEL, 100)
        facet("What is the formulation of DAP", "en", GoodsFacet.LABEL, 101)
        facet("यूरिया की श्रेणी क्या है", "hi", GoodsFacet.LABEL, 202)
        facet("ইউরিয়ার ক্যাটাগরি কী", "bn", GoodsFacet.LABEL, 206)
        facet("ما هي فئة يوريا", "ar", GoodsFacet.LABEL, 204)
        facet("یوریا کا رجسٹریشن نمبر", "ur", GoodsFacet.LABEL, 200)
    }

    @Test fun `pack, tax, shelf place and the day it was added`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        facet("Urea ki packing kitni hai", "ur-Latn", GoodsFacet.PACK, 100)
        facet("Urea par tax kitna hai", "ur-Latn", GoodsFacet.TAX, 100)
        facet("Where is Urea kept", "en", GoodsFacet.SHELF, 100)
        facet("Urea kahan rakhi hai", "ur-Latn", GoodsFacet.SHELF, 100)
        facet("When was Urea added", "en", GoodsFacet.ADDED, 100)
        facet("यूरिया पर टैक्स कितना है", "hi", GoodsFacet.TAX, 202)
        facet("यूरिया कब जोड़ा गया", "hi", GoodsFacet.ADDED, 202)
        facet("یوریا کب شامل کی گئی", "ur", GoodsFacet.ADDED, 200)
        facet("ইউরিয়ার প্যাকিং কত", "bn", GoodsFacet.PACK, 206)
        facet("یوریا کہاں رکھی ہے", "ur", GoodsFacet.SHELF, 200)
    }

    @Test fun `batches and their expiry are the shelf's lots, not the figure of what expires`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        facet("Urea ka batch kab expire hoga", "ur-Latn", GoodsFacet.BATCHES, 100)
        facet("Urea ke batches", "ur-Latn", GoodsFacet.BATCHES, 100)
        facet("When does Urea expire", "en", GoodsFacet.BATCHES, 100)
        facet("متى تنتهي صلاحية Urea", "ar", GoodsFacet.BATCHES, 100)
        facet("ইউরিয়ার মেয়াদ কবে শেষ", "bn", GoodsFacet.BATCHES, 206)
        facet("यूरिया की एक्सपायरी कब है", "hi", GoodsFacet.BATCHES, 202)
        // no product named: the figure of what is expiring, as before
        assertEquals(Ask.Figure(VoiceQuestion.Kind.EXPIRING), heard("Kaun sa maal expire ho raha hai", "ur-Latn"))
    }

    @Test fun `a scheme on a named product is told by its facet`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        facet("Urea par scheme", "ur-Latn", GoodsFacet.SCHEME, 100)
        facet("Which scheme is running on Urea", "en", GoodsFacet.SCHEME, 100)
        facet("यूरिया पर कोई स्कीम चल रही है", "hi", GoodsFacet.SCHEME, 202)
        facet("یوریا پر کوئی اسکیم چل رہی ہے", "ur", GoodsFacet.SCHEME, 200)
    }

    @Test fun `the older facets of a product are not moved`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        facet("Urea ka rate", "ur-Latn", GoodsFacet.PRICE, 100)
        facet("Urea kitni bachi hai", "ur-Latn", GoodsFacet.STOCK, 100)
        facet("Urea kis ne khareedi", "ur-Latn", GoodsFacet.BUYERS, 100)
    }

    @Test fun `a follow-up carries the product on to its next field`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        val urea = Ask.Goods(listOf(100L), GoodsFacet.PRICE, null)
        for ((s, f) in listOf(
            "aur uska barcode" to GoodsFacet.BARCODE, "uski registration number" to GoodsFacet.LABEL,
            "uski packing" to GoodsFacet.PACK, "aur uska tax" to GoodsFacet.TAX, "uska batch" to GoodsFacet.BATCHES,
        )) {
            val a = heard(s, "ur-Latn", urea)
            assertEquals(s, f, (a as? Ask.Goods)?.facet)
            assertEquals(s, listOf(100L), (a as Ask.Goods).ids)
        }
        assertEquals(GoodsFacet.BARCODE, (heard("and its barcode", "en", urea) as? Ask.Goods)?.facet)
    }

    // ------------------------------------------------------------------------------------------------------------ papers

    @Test fun `a supplier bill is asked by its number, note and lines`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        paper("supplier bill ka number kya hai", "ur-Latn", PaperOf.BILL)
        paper("supplier bill ka note kya hai", "ur-Latn", PaperOf.BILL)
        paper("is bill mein kitna maal aaya", "ur-Latn", PaperOf.BILL)
        paper("What is the supplier bill number", "en", PaperOf.BILL)
        paper("What is in the last supplier bill", "en", PaperOf.BILL)
        paper("سپلائر بل کا نمبر کیا ہے", "ur", PaperOf.BILL)
        paper("आखिरी सप्लायर बिल में क्या आया", "hi", PaperOf.BILL)
        paper("ما هو رقم فاتورة المورد", "ar", PaperOf.BILL)
        paper("شماره فاکتور تامین کننده چند است", "fa", PaperOf.BILL)
        paper("সাপ্লায়ার বিলের নম্বর কত", "bn", PaperOf.BILL)
        paper("nomor tagihan pemasok berapa", "in", PaperOf.BILL)
        paper("سپلائر بل جو نمبر ڇا آهي", "sd", PaperOf.BILL)
    }

    @Test fun `a named supplier's bill is his, and only a field word makes it a paper`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        val a = heard("Bashir Ahmad ke bill ka number", "ur-Latn")
        assertEquals(Ask.Paper(PaperOf.BILL, listOf(2L)), a)
        assertEquals(Ask.Parties(listOf(1L)), heard("Ali Raza ka bill batao", "ur-Latn"))
    }

    @Test fun `a purchase order is asked by whether it went, to whom and what it holds`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        paper("purchase order bheja ya nahi", "ur-Latn", PaperOf.ORDER)
        paper("purchase order kis supplier ko bheja", "ur-Latn", PaperOf.ORDER)
        paper("purchase order mein kya kya hai", "ur-Latn", PaperOf.ORDER)
        paper("Was the purchase order sent", "en", PaperOf.ORDER)
        paper("Which supplier was the purchase order sent to", "en", PaperOf.ORDER)
        paper("پرچیز آرڈر بھیجا یا نہیں", "ur", PaperOf.ORDER)
        paper("परचेज़ ऑर्डर भेजा या नहीं", "hi", PaperOf.ORDER)
        paper("هل تم إرسال أمر الشراء", "ar", PaperOf.ORDER)
        paper("آیا سفارش خرید فرستاده شد", "fa", PaperOf.ORDER)
        paper("ক্রয় অর্ডার পাঠানো হয়েছে কি", "bn", PaperOf.ORDER)
        paper("apakah pesanan pembelian sudah dikirim", "in", PaperOf.ORDER)
    }

    @Test fun `an invoice is asked by what is written on it`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        paper("invoice mein kya kya likha hai", "ur-Latn", PaperOf.INVOICE)
        paper("invoice ke items batao", "ur-Latn", PaperOf.INVOICE)
        paper("What is written on the invoice", "en", PaperOf.INVOICE)
        paper("انوائس میں کیا لکھا ہے", "ur", PaperOf.INVOICE)
        paper("इनवॉइस में क्या लिखा है", "hi", PaperOf.INVOICE)
        paper("ماذا كتب في الفاتورة", "ar", PaperOf.INVOICE)
        paper("در فاکتور چه نوشته شده", "fa", PaperOf.INVOICE)
        paper("ইনভয়েসে কী লেখা আছে", "bn", PaperOf.INVOICE)
        paper("faktur berisi apa saja", "in", PaperOf.INVOICE)
        paper("انوائس ۾ ڇا لکيل آهي", "sd", PaperOf.INVOICE)
        assertEquals(Ask.Paper(PaperOf.INVOICE, listOf(1L)), heard("Ali Raza ka invoice number kya hai", "ur-Latn"))
    }

    @Test fun `making, counting and the utility bill are not the papers' lines`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        for ((s, l) in listOf("kitne bills hain" to "ur-Latn", "How many invoices are there" to "en", "invoice banao" to "ur-Latn",
            "bill banao" to "ur-Latn", "bijli ka bill kitna hai" to "ur-Latn", "How many purchase orders are there" to "en")) {
            assertNotEquals("$s [$l]", Ask.Paper::class, heard(s, l)::class)
        }
    }

    @Test fun `a quotation is the documents page, not the invoice's lines`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        assertEquals(Ask.Help("intro_documents"), heard("Sales order kitne hain", "ur-Latn"))
    }

    // ----------------------------------------------------------------------------------------------------------- schemes

    @Test fun `how the schemes stand is told, how to make one is the help page`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        paper("kaun si schemes chal rahi hain", "ur-Latn", PaperOf.SCHEMES)
        paper("scheme kab khatam hogi", "ur-Latn", PaperOf.SCHEMES)
        paper("scheme ka target kitna hai", "ur-Latn", PaperOf.SCHEMES)
        paper("Which schemes are running", "en", PaperOf.SCHEMES)
        paper("When does the scheme end", "en", PaperOf.SCHEMES)
        paper("کون سی اسکیمیں چل رہی ہیں", "ur", PaperOf.SCHEMES)
        paper("कौन सी स्कीम चल रही हैं", "hi", PaperOf.SCHEMES)
        paper("ما هي العروض الجارية", "ar", PaperOf.SCHEMES)
        paper("কোন স্কিম চলছে", "bn", PaperOf.SCHEMES)
        paper("skema apa yang sedang berjalan", "in", PaperOf.SCHEMES)
    }

    // ------------------------------------------------------------------------------------ protected from the learnt sense

    @Test fun `the sense cannot turn a record ask`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        for (s in listOf("Urea ka barcode", "Urea ka batch kab expire hoga", "purchase order bheja ya nahi", "supplier bill ka number kya hai")) {
            val a = heard(s, "ur-Latn")
            assertEquals(s, true, a is Ask.Paper || (a is Ask.Goods && a.facet in Hudhud.RECORD_FACETS))
        }
    }

    // ------------------------------------------------------------------------------------ each guard has a sentence only it refuses

    private fun notPaper(s: String, lang: String) {
        val a = heard(s, lang)
        assertEquals("$s [$lang] read as $a", false, a is Ask.Paper)
    }

    @Test fun `the light company's bill is not a supplier's bill even when it came and has a number`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        notPaper("bijli ka bill aaya hai uska number kya hai", "ur-Latn")
        notPaper("The electricity bill came, what is its number", "en")
    }

    @Test fun `a bill still to pay is the bills' standing, unless its own number or note is asked`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        assertEquals(Ask.Status(Hudhud.StatusOf.BILLS), heard("supplier ke kaunse bill baqi hain", "ur-Latn"))
        paper("supplier bill ka number kya hai jo pending hai", "ur-Latn", PaperOf.BILL)
    }

    @Test fun `an order word beside an invoice is the invoice, a purchase order named is the order`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        paper("is invoice ka order number kya hai", "ur-Latn", PaperOf.INVOICE)
        paper("purchase order ka number kya hai", "ur-Latn", PaperOf.ORDER)
        paper("purchase order ka invoice number kya hai", "ur-Latn", PaperOf.ORDER)
    }

    @Test fun `a quotation or a sales order is the documents page whatever its lines say`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        assertEquals(Ask.Help("intro_documents"), heard("quotation mein kya kya likha hai", "ur-Latn"))
    }

    @Test fun `a bare order with no supplier and no state is not a purchase order`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        notPaper("order ka number kya hai", "ur-Latn")
    }

    @Test fun `a long number without the barcode's word is not a code`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        assertNotEquals(Ask.NeedGoods(GoodsFacet.BARCODE, "03001234567"), heard("03001234567 kiska hai", "ur-Latn"))
        assertEquals(false, heard("number 03001234567 kiska hai", "ur-Latn") is Ask.NeedGoods)
    }

    @Test fun `a product asked about with a figure's word keeps the figure's reading`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        for (s in listOf("Urea ki aaj ki sale mein tax kitna laga", "Urea ka aaj ka munafa aur tax", "Urea kal kitni bik gayi barcode"))
            assertEquals("$s read as ${heard(s, "ur-Latn")}", false, (heard(s, "ur-Latn") as? Ask.Goods)?.facet in Hudhud.RECORD_FACETS)
    }

    @Test fun `the day a product was added needs a when word`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        assertNotEquals(GoodsFacet.ADDED, (heard("Urea ka stock add hua kitna", "ur-Latn") as? Ask.Goods)?.facet)
    }

    @Test fun `a named person with a span or a scheme keeps his own account`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        notPaper("Bashir Ahmad ke bill ka number aaj", "ur-Latn")
    }
}
