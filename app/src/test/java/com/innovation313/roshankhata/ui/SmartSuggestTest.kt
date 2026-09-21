package com.innovation313.roshankhata.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the product boxes are allowed to suggest.
 *
 * Half of this file is the ordinary case — a name typed the way the shop says
 * it should reach the product on the shelf. The other half is the case that
 * actually matters: what must NOT come back.
 *
 * A suggestion list is read at a counter, in a hurry, with a customer
 * waiting. If it pads itself out with near misses the owner stops reading it
 * and taps by position, and the day it offers the wrong Confidor the wrong
 * product goes on someone's account. So every loosening below is pinned to a
 * floor, and the floors are tested harder than the matches are.
 *
 * The zzz case is not hypothetical — it is a bug this file caught before the
 * feature shipped. Folding collapses repeated letters, so "zzz" folds to a
 * bare "z" and matched every product carrying a z anywhere.
 */
class SmartSuggestTest {

    /** A dealer's shelf, close enough to a real one to be worth testing against. */
    private val shop = listOf(
        "Confidor 200SL", "Confidor Super", "Sencor 70WP", "Karate 2.5EC",
        "Lambda 2.5EC", "Urea (Engro)", "DAP Fauji", "Round Up", "Topstar 80WP",
        "Bayer Nativo", "Sulphur 80WP", "Wheat Seed Galaxy", "Cotton Seed IUB-13",
        "Nitrophos", "Potash SOP", "Zinc Sulphate 33%"
    ).map { SmartSuggest.Item(it) }

    private fun offer(typed: String): List<String> =
        SmartSuggest.rank(shop, typed).map { it.value }

    // ---------- The name reaches the product ----------

    @Test
    fun `a name typed exactly comes first`() {
        assertEquals("Sencor 70WP", offer("sencor").first())
        assertEquals("Topstar 80WP", offer("topstar").first())
        assertEquals("DAP Fauji", offer("dap").first())
    }

    @Test
    fun `both products sharing a name are offered, shorter first`() {
        assertEquals(listOf("Confidor 200SL", "Confidor Super"), offer("conf"))
    }

    @Test
    fun `a word inside the name counts, not only the first`() {
        assertEquals("Bayer Nativo", offer("bayer").first())
        assertTrue(offer("seed").containsAll(listOf("Wheat Seed Galaxy", "Cotton Seed IUB-13")))
    }

    @Test
    fun `spellings that sound the same reach the same product`() {
        // The fold earns its keep: c/k/q, ph/f, the silent h, doubled letters.
        assertEquals("Confidor 200SL", offer("kunfidor").first())
        assertEquals("Nitrophos", offer("nitrofos").first())
        assertEquals("Sulphur 80WP", offer("sulfur").first())
        assertEquals("Potash SOP", offer("potas").first())
        assertEquals("Wheat Seed Galaxy", offer("wheet").first())
        assertEquals("Karate 2.5EC", offer("karatay").first())
    }

    @Test
    fun `one wrong or missing letter is forgiven`() {
        assertEquals("Confidor 200SL", offer("confidr").first())
        assertEquals("Lambda 2.5EC", offer("lamda").first())
        assertEquals("Round Up", offer("rond").first())
    }

    // ---------- And nothing else does ----------

    @Test
    fun `a query that means nothing offers nothing`() {
        assertEquals(emptyList<String>(), offer("xylophone"))
        assertEquals(emptyList<String>(), offer("qwerty"))
    }

    @Test
    fun `a repeated letter is not a name`() {
        // Each of these folds down to one or two letters. Without the floor
        // they matched a large part of the shelf and looked like understanding.
        assertEquals(emptyList<String>(), offer("zzz"))
        assertEquals(emptyList<String>(), offer("aaa"))
        assertEquals(emptyList<String>(), offer("qqq"))
        assertEquals(emptyList<String>(), offer("hh"))
    }

    @Test
    fun `a typo is only forgiven once the query is long enough to have one`() {
        // "urea" is four letters and one edit from nothing else on this shelf;
        // a three-letter query must not reach it by an edit, or half the shelf
        // is one edit from half the alphabet.
        assertEquals("Urea (Engro)", offer("urea").first())
        assertTrue("Round Up" !in offer("rnd"))
    }

    @Test
    fun `the list never grows past what fits on a phone`() {
        assertTrue(offer("").size <= 8)
        assertTrue(offer("a").size <= 8)
    }

    @Test
    fun `an empty query offers the shelf rather than nothing`() {
        assertTrue(offer("").isNotEmpty())
    }

    // ---------- Urdu script ----------
    //
    // Urdu does not write short vowels, so a name typed in it can never be
    // one edit from the Latin spelling — it is two or three vowels short. The
    // consonants are compared instead, and ONLY for a query in Urdu script,
    // so nothing a Roman-typing dealer does can reach this tier.

    @Test
    fun `a product name typed in Urdu reaches the product`() {
        assertEquals("Confidor 200SL", offer("کنفیڈور").first())
        assertEquals("Karate 2.5EC", offer("کریٹ").first())
        assertEquals("Sencor 70WP", offer("سینکور").first())
        assertEquals("Potash SOP", offer("پوٹاش").first())
    }

    @Test
    fun `ph and f are the same sound when the query is in Urdu`() {
        // Urdu writes the f sound as ف. The app's fold reads "ph" as a p with
        // a silent h, so without a rule of its own سلفر and Sulphur differ by
        // a letter and never meet.
        assertEquals("Sulphur 80WP", offer("سلفر").first())
        assertEquals("Nitrophos", offer("نائٹروفاس").first())
    }

    @Test
    fun `a short Urdu query offers nothing rather than guessing`() {
        assertEquals(emptyList<String>(), offer("بی"))
        assertEquals(emptyList<String>(), offer("ایکس"))
        // Urea is one consonant and three vowels; its skeleton is a bare "r",
        // below the floor. It declines rather than offering the shelf, which
        // is the right failure.
        assertEquals(emptyList<String>(), offer("یوریا"))
    }

    @Test
    fun `Roman typing can never reach the consonants-only tier`() {
        // The tier that ignores vowels is the loosest thing here, and the one
        // that historically went wrong elsewhere in this app. "rnd" shares
        // its consonants with Round Up and must still find nothing, because
        // it was typed in Latin.
        assertTrue("Round Up" !in offer("rnd"))
        assertTrue("DAP Fauji" !in offer("dp"))
        assertEquals(emptyList<String>(), offer("cnf"))
    }

    // ---------- The subtitle ----------

    @Test
    fun `the subtitle carries only what the product actually has`() {
        val withBoth = listOf(
            com.innovation313.roshankhata.data.Product(
                name = "Confidor", nameKey = "confidor", normalisedName = "konfidor",
                company = "Bayer", defaultUnit = "bottle"
            )
        ).asSuggestions()
        assertEquals("Bayer · bottle", withBoth.single().subtitle)

        val withNeither = listOf(
            com.innovation313.roshankhata.data.Product(
                name = "Urea", nameKey = "urea", normalisedName = "ura"
            )
        ).asSuggestions()
        assertEquals(null, withNeither.single().subtitle)
    }

    // ---------- How many rows a form asks for ----------

    @Test
    fun `a form can ask for fewer rows and gets the best ones`() {
        val all = SmartSuggest.rank(shop, "s")
        assertTrue("the shelf must have more than three s-names for this to mean anything", all.size > 3)
        val three = SmartSuggest.rank(shop, "s", maxRows = 3)
        assertEquals(3, three.size)
        // The best three, in the same order: asking for fewer never reshuffles.
        assertEquals(all.take(3), three)
    }

    @Test
    fun `an empty box also respects the limit`() {
        assertEquals(3, SmartSuggest.rank(shop, "", maxRows = 3).size)
    }

    @Test
    fun `the default is unchanged`() {
        assertEquals(8, SmartSuggest.rank(shop, "").size)
    }
}
