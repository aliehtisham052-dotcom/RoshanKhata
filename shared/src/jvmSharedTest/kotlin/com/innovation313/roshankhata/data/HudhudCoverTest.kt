package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.Hudhud.Ask
import com.innovation313.roshankhata.data.Hudhud.GoodsFacet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Urea kitne din chalegi?" (15 Nov 2026, #30): a question about a named product that asks how long it lasts is the shelf's
 * cover, in nine languages, and a question about its price, stock or last delivery is not turned into it. Every sentence goes
 * through [Hudhud.understand] with the shipped sense. The product is saved in the script each language writes it in (a product's
 * name across scripts is not read: lesson 139). The sentences are written by the maker of the tests, not by a native reader of
 * each language (said plainly in the notes: Sindhi, Bengali and Farsi want one).
 */
class HudhudCoverTest {
    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val goods = listOf(
        "Urea", "یوریا", "يوريا", "यूरिया", "ইউরিয়া", "اوره"
    ).mapIndexed { i, n -> Hudhud.Name(100L + i, n) }
    private val one = Ask.Goods(listOf(100L), GoodsFacet.COVER)

    private fun read(s: String, language: String? = null, before: Ask? = null) =
        Hudhud.understand(s, emptyList(), emptyList(), language, goods = goods, before = before)

    private fun cover(said: List<Triple<String?, String, Long>>) {
        for ((language, s, id) in said) {
            val ask = read(s, language)
            assertTrue("$language: $s -> $ask", ask is Ask.Goods && ask.facet == GoodsFacet.COVER && id in ask.ids && ask.window == null)
        }
    }

    @Test fun `how long it lasts, in nine languages`() = cover(listOf(
        Triple("en", "how long will Urea last", 100L),
        Triple("en", "when will Urea run out", 100L),
        Triple("en", "is the Urea enough for a week", 100L),
        Triple("en", "how many days of Urea stock do I have", 100L),
        Triple(null, "Urea kitne din chalegi", 100L),
        Triple(null, "Urea kab tak chalegi", 100L),
        Triple(null, "Urea kab khatam hogi", 100L),
        Triple(null, "Urea kitne din ka stock hai", 100L),
        Triple(null, "Urea kitne din ke liye kafi hai", 100L),
        Triple("ur", "یوریا کتنے دن چلے گی", 101L),
        Triple("ur", "یوریا کب ختم ہوگی", 101L),
        Triple("sd", "يوريا ڪيترا ڏينهن هلندو", 102L),
        Triple("sd", "يوريا ڪڏهن ختم ٿيندو", 102L),
        Triple("ar", "كم يوم يكفي يوريا", 102L),
        Triple("ar", "متى ينفد يوريا", 102L),
        Triple("fa", "اوره برای چند روز کافی است", 105L),
        Triple("fa", "اوره چند روز دوام می‌آورد", 105L),
        Triple("hi", "यूरिया कितने दिन चलेगा", 103L),
        Triple("hi", "यूरिया कब खत्म होगा", 103L),
        Triple("bn", "ইউরিয়া কতদিন চলবে", 104L),
        Triple("bn", "ইউরিয়া কবে শেষ হবে", 104L),
        Triple("in", "Urea cukup untuk berapa hari", 100L),
        Triple("in", "kapan Urea habis", 100L)
    ))

    @Test fun `price, stock, cost and the last delivery are not turned into the cover`() {
        for ((s, facet) in listOf(
            "Urea price" to GoodsFacet.PRICE, "Urea stock" to GoodsFacet.STOCK, "Urea kitna stock hai" to GoodsFacet.STOCK,
            "Urea kab aayi thi" to GoodsFacet.LAST_IN, "Urea last rate" to GoodsFacet.PRICE
        )) assertEquals(s, Ask.Goods(listOf(100L), facet), read(s))
        // "how many days since it came" is not how long it lasts.
        assertNotEquals(one, read("how many days since Urea came"))
        // "khatam ho gayi" alone is the stock, with no "when".
        assertNotEquals(one, read("Urea khatam ho gayi"))
    }

    @Test fun `the guard words alone ask nothing about the cover`() {
        // A lasting word with no time word, a time word with no lasting word, a run-out word with no when.
        assertTrue(!Hudhud.asksCoverForTest("urea kafi mehngi hai"))
        assertTrue(!Hudhud.asksCoverForTest("urea kitne din purani hai"))
        assertTrue(!Hudhud.asksCoverForTest("urea khatam ho gayi"))
        assertTrue(!Hudhud.asksCoverForTest("how many days since urea came"))
        assertTrue(Hudhud.asksCoverForTest("urea kitne din chalegi"))
        assertTrue(Hudhud.asksCoverForTest("when will urea run out"))
        assertTrue(Hudhud.asksCoverForTest("urea kab khatam hogi"))
    }

    @Test fun `a bare question carries on from the product before it`() {
        val before = Ask.Goods(listOf(100L), GoodsFacet.PRICE)
        assertEquals(one, read("kitne din chalega", before = before))
        assertEquals(one, read("kab tak chalegi", before = before))
        // With no product before it, it is not guessed.
        assertNotEquals(one, read("kitne din chalega"))
    }

    @Test fun `the cover is no span of the book`() {
        // "for a week" belongs to the question; it is not "this is how it stands now" nor a span the book cannot give.
        assertEquals(one, read("is the Urea enough for a week", "en"))
        assertEquals(one, read("Urea next month tak chalegi", null))
    }

    /** The chip's own words (app strings, 15 Nov 2026), said under a product's answer: the readings that name nothing give way. */
    @Test fun `the chip's words carry on from the product before it, in nine languages`() {
        val before = Ask.Goods(listOf(100L), GoodsFacet.PRICE)
        for ((language, chip) in listOf(
            "en" to "How long will it last?", "ur-Latn" to "Kitne din chalega?", "ur" to "کتنے دن چلے گا؟",
            "sd" to "ڪيترا ڏينهن هلندو؟", "ar" to "كم يومًا سيكفي؟", "fa" to "برای چند روز کافی است؟",
            "hi" to "कितने दिन चलेगा?", "bn" to "কতদিন চলবে?", "in" to "Cukup untuk berapa hari?"
        )) assertEquals("$language: $chip", one, read(chip, language, before))
    }

    @Test fun `two products before it are no product, nothing is carried`() {
        val two = Ask.Goods(listOf(100L, 101L), GoodsFacet.PRICE)
        for (said in listOf("kitne din chalega", "How long will it last?", "kab tak chalegi")) {
            val got = read(said, before = two)
            assertTrue("$said -> $got", !(got is Ask.Goods && got.facet == GoodsFacet.COVER))
        }
    }

    @Test fun `no product named and none before is asked for, not guessed`() {
        assertEquals(Ask.NeedGoods(GoodsFacet.COVER), read("is cheez ka stock kitne din chalega"))
        assertEquals(Ask.NeedGoods(GoodsFacet.COVER), read("how long will the stock last", "en"))
    }

    @Test fun `a sentence that asks about another thing of the product keeps its own facet`() {
        for ((said, facet) in listOf("Urea ka rate" to GoodsFacet.PRICE, "Urea kab aayi thi" to GoodsFacet.LAST_IN,
            "Urea kis kis ne khareedi" to GoodsFacet.BUYERS,
            // A named thing of the product wins over a lasting word beside it: the answer is what was named first-hand.
            "Urea ka rate kitne din chalega" to GoodsFacet.PRICE, "Urea kis supplier se aati hai kitne din chalegi" to GoodsFacet.SUPPLIERS,
            "who bought Urea, how long will it last" to GoodsFacet.BUYERS, "Urea kab aayi thi kitne din chalegi" to GoodsFacet.LAST_IN)) {
            val got = read(said)
            assertTrue("$said -> $got", got is Ask.Goods && got.facet == facet)
        }
    }

    @Test fun `what runs out in date is the expiring batches, not a shelf`() {
        assertEquals(Ask.Figure(VoiceQuestion.Kind.EXPIRING), read("Expiry wala maal kab khatam hoga"))
    }
}
