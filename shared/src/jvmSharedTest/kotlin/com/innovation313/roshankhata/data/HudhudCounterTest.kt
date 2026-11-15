package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.Hudhud.Ask
import com.innovation313.roshankhata.data.Hudhud.CounterFacet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 15 Nov 2026, the catalog's group "the counter's own books" (importance 8: counter money and stock): sales, their lines
 * and payments, returns, shifts, loyalty, the day's close, stock adjustments. The reader's guards are held two ways (lesson
 * 129): through [Hudhud.understand] with the shipped sense and a page list (lesson 145), and by calling [HudhudCounter.read]
 * directly where only that guard can refuse. Every name, figure and sentence is invented (lesson 80).
 */
class HudhudCounterTest {

    private val topics = listOf("intro_documents", "intro_cashbook", "screen_bills", "intro_cheques", "intro_counter")
        .map { Hudhud.Topic(it, it, it) }

    private fun heard(s: String, lang: String) = Hudhud.understand(s, emptyList(), topics, lang)

    private fun facet(s: String, lang: String): CounterFacet? = (heard(s, lang) as? Ask.Counter)?.facet

    private fun direct(s: String, how: Boolean = false, supplier: Boolean = false, spanOwnsLast: Boolean = false): CounterFacet? {
        val said = s.lowercase().split(" ").filter { it.isNotEmpty() }
        return HudhudCounter.read(said.joinToString(" "), said, how, supplier, spanOwnsLast)
    }

    private fun assertFacet(want: CounterFacet, s: String, lang: String) {
        HudhudSense.use(HudhudModelTest.shippedModel())
        assertEquals("$s [$lang]", want, facet(s, lang))
    }

    // ------------------------------------------------------------------------------------------- through the whole pipeline

    @Test fun `the biggest bill is read in nine languages`() {
        assertFacet(CounterFacet.BIGGEST, "Aaj ka sab se bada bill", "ur-Latn")
        assertFacet(CounterFacet.BIGGEST, "biggest bill this month", "en")
        assertFacet(CounterFacet.BIGGEST, "آج کا سب سے بڑا بل", "ur")
        assertFacet(CounterFacet.BIGGEST, "أكبر فاتورة اليوم", "ar")
        assertFacet(CounterFacet.BIGGEST, "بزرگ‌ترین فاکتور امروز", "fa")
        assertFacet(CounterFacet.BIGGEST, "आज का सबसे बड़ा बिल", "hi")
        assertFacet(CounterFacet.BIGGEST, "আজকের সবচেয়ে বড় বিল", "bn")
        assertFacet(CounterFacet.BIGGEST, "struk terbesar hari ini", "in")
        assertFacet(CounterFacet.BIGGEST, "اڄ جو سڀ کان وڏو بل", "sd")
    }

    @Test fun `the latest bill and the cashier's sales`() {
        assertFacet(CounterFacet.LAST_TICKET, "aakhri bill dikhao", "ur-Latn")
        assertFacet(CounterFacet.LAST_TICKET, "latest sale ticket", "en")
        assertFacet(CounterFacet.BY_CASHIER, "kis cashier ne sab se zyada becha", "ur-Latn")
        assertFacet(CounterFacet.BY_CASHIER, "sales by cashier today", "en")
        assertFacet(CounterFacet.BY_CASHIER, "कैशियर के हिसाब से बिक्री", "hi")
    }

    @Test fun `discount, tax and round off are three questions`() {
        assertFacet(CounterFacet.DISCOUNTS, "kitna discount diya aaj", "ur-Latn")
        assertFacet(CounterFacet.DISCOUNTS, "इस महीने कितनी छूट दी", "hi")
        assertFacet(CounterFacet.DISCOUNTS, "berapa diskon yang diberikan hari ini", "in")
        assertFacet(CounterFacet.TAX, "how much tax collected today", "en")
        assertFacet(CounterFacet.TAX, "aaj kitna tax laga", "ur-Latn")
        // "round off" holds the word "off": it is the round-off, not a discount
        assertFacet(CounterFacet.TAX, "round off kitna hua aaj", "ur-Latn")
        assertFacet(CounterFacet.DISCOUNTS, "10 percent off kitna diya", "ur-Latn")
    }

    @Test fun `cancelled bills are read, and a cancelled cheque or order is not the till's`() {
        assertFacet(CounterFacet.VOIDED, "kis ne void kiya", "ur-Latn")
        assertFacet(CounterFacet.VOIDED, "which bills were cancelled today", "en")
        assertFacet(CounterFacet.VOIDED, "voided sales this week", "en")
        assertNotEquals(Ask.Counter::class, heard("cheque cancel kis ne kiya", "ur-Latn")::class)
        assertNotEquals(Ask.Counter::class, heard("order cancel hua kab", "ur-Latn")::class)
        assertNotEquals(Ask.Counter::class, heard("quotation cancel kis ne ki", "ur-Latn")::class)
    }

    @Test fun `returns are asked by why, by way and by goods`() {
        assertFacet(CounterFacet.RETURN_REASONS, "kis wajah se return hua", "ur-Latn")
        assertFacet(CounterFacet.RETURN_REASONS, "why were goods returned this week", "en")
        assertFacet(CounterFacet.RETURN_GOODS, "kaun sa maal wapas aaya", "ur-Latn")
        assertFacet(CounterFacet.RETURN_GOODS, "which goods came back today", "en")
        // the how in the past tense is the way it WAS done; the how-to is the help page's
        assertFacet(CounterFacet.RETURN_WAYS, "return ka paisa kaise diya", "ur-Latn")
        assertNotEquals(Ask.Counter::class, heard("return ka paisa kaise dein", "ur-Latn")::class)
    }

    @Test fun `a supplier's goods are not the counter's returns`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        assertNotEquals(Ask.Counter::class, heard("supplier ko maal wapas kiya", "ur-Latn")::class)
        assertNotEquals(Ask.Counter::class, heard("maal wapas aaya supplier se", "ur-Latn")::class)
    }

    @Test fun `shifts, the drawer and the stock's own books`() {
        assertFacet(CounterFacet.DAY_COUNT, "kal drawer ki ginti kitni thi", "ur-Latn")
        assertFacet(CounterFacet.DAY_COUNT, "yesterday's drawer count and difference", "en")
        assertFacet(CounterFacet.SHIFT_SHORT, "which shift was short", "en")
        assertFacet(CounterFacet.SHIFTS, "shift kab band hui", "ur-Latn")
        assertFacet(CounterFacet.LOYALTY, "who got loyalty points this month", "en")
        assertFacet(CounterFacet.WRITTEN_OFF, "how much stock was written off damaged", "en")
        assertFacet(CounterFacet.WRITTEN_OFF, "hari ini berapa barang rusak dihapuskan", "in")
        assertFacet(CounterFacet.LAST_COUNT, "last stock count", "en")
        assertFacet(CounterFacet.LAST_COUNT, "آخری مال کی گنتی", "ur")
        assertFacet(CounterFacet.LAST_COUNT, "آخر جرد للمخزون", "ar")
        assertFacet(CounterFacet.STOCK_CHANGES, "stock changes today", "en")
        assertFacet(CounterFacet.STOCK_CHANGES, "stock mein kya kya tabdeeli hui", "ur-Latn")
    }

    @Test fun `what is not the till's stays where it was`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        // the shop's own tax number is the profile's; "bill banao" is the documents page; the plural "sales" is the figure
        assertNotEquals(Ask.Counter::class, heard("tax number kya hai", "ur-Latn")::class)
        assertNotEquals(Ask.Counter::class, heard("bill banao", "ur-Latn")::class)
        assertNotEquals(Ask.Counter::class, heard("supplier ka bill kitna baqi hai", "ur-Latn")::class)
        assertEquals(Ask.Figure(VoiceQuestion.Kind.SALES_TODAY), heard("aaj ki sales", "ur-Latn"))
    }

    // ------------------------------------------------------------------------------------------- the reader's own guards

    @Test fun `a how-to is never read as a question of the till`() {
        assertEquals(CounterFacet.BIGGEST, direct("aaj ka sab se bada bill"))
        assertNull(direct("aaj ka sab se bada bill", how = true))
    }

    @Test fun `a sentence with a word of making is no question`() {
        assertEquals(CounterFacet.VOIDED, direct("kis ne bill void kiya"))
        assertNull(direct("naya bill void kaise"))
        assertNull(direct("bill banao discount ke saath"))
    }

    @Test fun `a supplier side sentence is the supplier's papers`() {
        assertEquals(CounterFacet.DISCOUNTS, direct("aaj kitna discount diya bill par"))
        assertNull(direct("aaj kitna discount diya bill par", supplier = true))
        assertNull(direct("supplier bill cancel kis ne kiya", supplier = true))
    }

    @Test fun `another paper that is cancelled is not a void of the till`() {
        assertNull(direct("cheque cancel kis ne kiya"))
        assertNull(direct("quotation cancel kis ne kiya"))
    }

    @Test fun `a limit or a number is the settings, not the day's figure`() {
        assertNull(direct("discount limit kitni hai"))
        assertNull(direct("tax number kya hai"))
        assertNull(direct("invoice mein tax kitna lagta hai"))
    }

    @Test fun `the singular sale is a bill, the plural is the figure`() {
        assertEquals(CounterFacet.BIGGEST, direct("aaj ki sab se badi sale"))
        assertNull(direct("aaj ki sab se badi sales"))
    }

    @Test fun `loyalty, a stock count and an adjustment are not read from a sentence of making or of bills`() {
        assertEquals(CounterFacet.LOYALTY, direct("loyalty points kis ko mile"))
        assertNull(direct("loyalty points naya banao"))
        // a drawer's count is the day's, whatever goods the sentence also names
        assertEquals(CounterFacet.DAY_COUNT, direct("drawer ki ginti kab hui maal bechne ke baad"))
        // "closing stock" is a figure of the shelf, not the count that was taken
        assertNotEquals(CounterFacet.LAST_COUNT, direct("closing stock kab hua"))
        assertEquals(CounterFacet.LAST_COUNT, direct("stock ki ginti kab hui"))
        // a bill that mentions stock changing is the bill's
        assertNotEquals(CounterFacet.STOCK_CHANGES, direct("bill mein stock tabdeeli"))
        assertNotEquals(CounterFacet.STOCK_CHANGES, direct("bill adjustment kitna hua"))
        assertEquals(CounterFacet.STOCK_CHANGES, direct("stock adjustment kab hui"))
    }

    @Test fun `a bill of goods or one that came in is not the biggest or the last of the till`() {
        assertEquals(CounterFacet.BIGGEST, direct("aaj ka sab se bada bill"))
        assertNotEquals(CounterFacet.BIGGEST, direct("aaj ka sab se bada maal bill"))
        assertNotEquals(CounterFacet.BIGGEST, direct("sab se bada bill aaya"))
        assertEquals(CounterFacet.LAST_TICKET, direct("aakhri bill dikhao"))
        assertNotEquals(CounterFacet.LAST_TICKET, direct("aakhri bill aaya kab"))
    }

    @Test fun `a drawer count with a bill word, a shift with a word of making, a void with a return`() {
        assertEquals(CounterFacet.DAY_COUNT, direct("drawer ki ginti kal"))
        assertNotEquals(CounterFacet.DAY_COUNT, direct("drawer mein kitne bill ki ginti"))
        assertEquals(CounterFacet.SHIFTS, direct("shift kab band hui"))
        assertNull(direct("naya shift kaise kholte hain"))
        assertEquals(CounterFacet.VOIDED, direct("void hue return kyun hue"))
        assertEquals(CounterFacet.RETURN_REASONS, direct("return kyun hue"))
    }

    @Test fun `a word of tax alone, the tax number, and goods lost on a bill`() {
        assertEquals(CounterFacet.TAX, direct("aaj kitna tax laga"))
        assertNull(direct("tax rules"))
        assertNull(direct("ntn number kitna hai tax"))
        assertEquals(CounterFacet.WRITTEN_OFF, direct("kitna maal kharab likha gaya"))
        assertNotEquals(CounterFacet.WRITTEN_OFF, direct("bill mein kharab maal kitna likha gaya"))
    }

    @Test fun `goods that came back are the till's only when nothing else claims the sentence`() {
        assertEquals(CounterFacet.RETURN_GOODS, direct("which goods came back today"))
        assertNull(direct("create new goods came back today"))
        assertNull(direct("which goods came back from supplier", supplier = true))
        assertNull(direct("goods on bill came back today"))
    }

    @Test fun `a return or a supplier is not a stock correction or a write-off`() {
        assertEquals(CounterFacet.STOCK_CHANGES, direct("stock mein kya kya tabdeeli hui"))
        assertNotEquals(CounterFacet.STOCK_CHANGES, direct("return ke baad stock mein kya tabdeeli hui"))
        assertEquals(CounterFacet.WRITTEN_OFF, direct("kitna maal kharab likha gaya"))
        assertNotEquals(CounterFacet.WRITTEN_OFF, direct("kharab maal ka return kitna hua"))
        assertNull(direct("supplier ka kharab maal kitna likha gaya", supplier = true))
    }

    @Test fun `the owner's own everyday sentences are not taken by the till`() {
        // money coming back, a cheque coming back, the drawer just counted, a scheme's discount, a bill's last date,
        // cash and credit sales: other readers own these (the owner's lists, 15 Nov 2026)
        for (s in listOf(
            "aaj kitne paise wapas aaye", "kaun sa cheque wapas aaya", "kitna paisa wapas aana hai", "mulazim se paise wapas lene hain",
            "galla gin liya farq kitna hai", "galla kam kyun nikla", "gallay mein kitna farq hai", "scheme par kitna discount hai",
            "bill ki aakhri tareekh kab hai", "naqad sale kitni hui", "udhaar sale kitni hui", "galat bill cancel karo",
            "bill mein discount dena hai", "kaun sa bill baqi hai", "which invoices are unpaid"
        )) assertNull(s, direct(s))
    }

    @Test fun `a credit question is the till's only with the counter named`() {
        assertEquals(CounterFacet.ON_ACCOUNT, direct("counter se aaj kitna udhaar gaya"))
        assertNull(direct("aaj kitna udhaar bika"))
        assertEquals(CounterFacet.PAY_WAYS, direct("counter par cash aur card kitna aaya"))
        assertNull(direct("naqad sale kitni hui"))
    }

    @Test fun `a drawer count is the recorded one only when a day or a time is asked`() {
        assertEquals(CounterFacet.DAY_COUNT, direct("kal drawer ki ginti kitni thi"))
        assertEquals(CounterFacet.DAY_COUNT, direct("last drawer count difference"))
        assertNull(direct("drawer count difference"))
    }

    @Test fun `a span's own last is not the last bill`() {
        assertEquals(CounterFacet.LAST_TICKET, direct("aakhri bill dikhao"))
        assertNull(direct("aakhri bill dikhao", spanOwnsLast = true))
    }

    @Test fun `only a return's own how is read in the past, and a cheque's return is not the till's`() {
        assertNull(direct("sab se bada bill kaise hua", how = true))
        assertEquals(CounterFacet.RETURN_REASONS, direct("return kyun hue"))
        assertNull(direct("cheque return kyun hua"))
    }

    @Test fun `held-out classes of closing the day, a word of writing, a bare cashier, goods that came back in Arabic`() {
        assertNull(direct("i want to close the day"))
        assertNull(direct("today was an entry written in the khata"))
        assertNull(direct("aaj khate mein entry likha gaya"))
        assertNull(direct("حذف هذا الاسبوع"))
        assertNull(direct("قديش وصل طلع من الكاشير اليوم"))
        assertNull(direct("hari ini berapa struk yang dicetak di kasir"))
        assertEquals(CounterFacet.BY_CASHIER, direct("कैशियर के हिसाब से बिक्री"))
        assertNull(direct("شو رجع بضاعة اليوم"))
    }

    @Test fun `windows follow the facet`() {
        assertNull(HudhudCounter.defaultWindow(CounterFacet.LAST_COUNT))
        assertNull(HudhudCounter.defaultWindow(CounterFacet.LAST_TICKET))
        assertNull(HudhudCounter.defaultWindow(CounterFacet.DAY_COUNT))
        assertEquals(VoiceQuestion.Window.TODAY, HudhudCounter.defaultWindow(CounterFacet.BIGGEST))
        assertEquals(VoiceQuestion.Window.THIS_WEEK, HudhudCounter.defaultWindow(CounterFacet.SHIFTS))
        assertEquals(VoiceQuestion.Window.THIS_WEEK, HudhudCounter.defaultWindow(CounterFacet.SHIFT_SHORT))
        assertEquals(VoiceQuestion.Window.THIS_MONTH, HudhudCounter.defaultWindow(CounterFacet.LOYALTY))
    }

    // ------------------------------------------------------------------------------------------- the tallies

    @Test fun `reasons typed in different cases are one reason`() {
        val t = HudhudCounter.tallyReasons(listOf("Damaged " to 100.0, "damaged" to 50.0, "DAMAGED" to 10.0, "Wrong size" to 500.0, "" to 5.0))
        assertEquals(3, t.size)
        assertEquals("Damaged", t[0].reason)
        assertEquals(3, t[0].count)
        assertEquals(160.0, t[0].worth, 0.0001)
        assertEquals("Wrong size", t[1].reason)
        assertEquals("", t[2].reason)
    }

    @Test fun `goods of one name in two units are two kinds`() {
        val t = HudhudCounter.tallyGoods(listOf(
            HudhudCounter.GoodsLine("Test Rice", "bag", 5.0, 5000.0),
            HudhudCounter.GoodsLine("test  rice", "Bag", 2.0, 2000.0),
            HudhudCounter.GoodsLine("Test Rice", "kg", 5.0, 900.0)
        ))
        assertEquals(2, t.size)
        assertEquals(7.0, t[0].qty, 0.0001)
        assertEquals(7000.0, t[0].worth, 0.0001)
        assertEquals("kg", t[1].unit)
        val by = HudhudCounter.tallyGoods(listOf(
            HudhudCounter.GoodsLine("Test Soap", "pc", 100.0, 500.0),
            HudhudCounter.GoodsLine("Test Oil", "l", 2.0, 900.0)
        ))
        assertEquals(listOf("Test Oil", "Test Soap"), by.map { it.name })
        assertEquals(1, HudhudCounter.tallyGoods(t.map { HudhudCounter.GoodsLine(it.name, it.unit, it.qty, it.worth) }, 1).size)
    }

    @Test fun `loyalty points are earned and spent apart, a zero is left out`() {
        val t = HudhudCounter.tallyPoints(listOf(1L to 10.0, 1L to -4.0, 2L to 5.0, 2L to -5.0, 3L to 0.0))
        val one = t.first { it.partyId == 1L }
        assertEquals(10.0, one.earned, 0.0001)
        assertEquals(4.0, one.spent, 0.0001)
        assertEquals(6.0, one.net, 0.0001)
        assertTrue(t.any { it.partyId == 2L })
        assertTrue(t.none { it.partyId == 3L })
    }

    @Test fun `only damage, expiry and theft are written off, and a count is not`() {
        val lines = listOf(
            HudhudCounter.AdjustLine(1, -3.0, "bag", "damage"),
            HudhudCounter.AdjustLine(1, -2.0, "bag", "damage"),
            HudhudCounter.AdjustLine(1, -9.0, "bag", "count"),
            HudhudCounter.AdjustLine(2, -1.0, "kg", "other"),
            HudhudCounter.AdjustLine(3, 4.0, "kg", "theft"),
            HudhudCounter.AdjustLine(4, -1.0, "kg", "expired"),
            HudhudCounter.AdjustLine(4, 1.0, "kg", "expired")
        )
        val t = HudhudCounter.tallyLost(lines)
        assertEquals(1, t.size)
        assertEquals(1L, t[0].productId)
        assertEquals(5.0, t[0].lost, 0.0001)
        assertEquals(2, t[0].rows)
    }
}
