package com.innovation313.roshankhata.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ordering a shop's book against a spoken name.
 *
 * These are not invented cases. Each one is a way the matcher failed on the
 * owner's own phone, or the next way it was about to: a recogniser's spelling,
 * a doubled letter, a word in the middle of a name rather than at its start.
 * They are here together because they were being fixed one at a time, each
 * with its own explanation, and one measure that covers all of them is worth
 * more than a rule per name.
 */
class NameSearchTest {

    /** Shaped like the real book: messy, multi-word, and long. */
    private val book = listOf(
        "A Bilal",
        "(Shari)Touseef Adnan Lappay Wali",
        "Aamar Baiee",
        "Abbas Ghala Mandi Pasrur Dokandar",
        "Abbas Loader",
        "Asghar",
        "Asghar Ali",
        "Ali Asghar Dokandar",
        "Yaseen Bhai",
        "Tehseen Sahib",
        "Masood Ahmad",
        "Abdul Rahman Chacha Lamber Lappay Wali",
        "Bilal Khan"
    ) + (1..200).map { "Filler Customer $it" }

    private fun rank(vararg spoken: String) =
        NameSearch.rankSpoken(book, spoken.toList()) { it }

    private fun assertNear(spoken: String, want: String, within: Int = 3) {
        val at = rank(spoken).indexOf(want)
        assertTrue(
            "\"$spoken\" put \"$want\" at ${at + 1}, wanted within $within",
            at in 0 until within
        )
    }

    /** The sentence that sent the owner a list of 1162 strangers. */
    @Test
    fun `a recogniser's spelling still finds the customer`() {
        assertNear("asgar", "Asghar")
        assertNear("asgar", "Asghar Ali")
    }

    /** The same one letter of difference, in the other names it would hit. */
    @Test
    fun `one letter of difference is not a different person`() {
        assertNear("yasin", "Yaseen Bhai")
        assertNear("tahseen", "Tehseen Sahib")
        assertNear("masud", "Masood Ahmad")
        assertNear("abas", "Abbas Loader", within = 4)
    }

    /**
     * Half a shop's names are phrases. The word the owner says is as likely to
     * be in the middle of one as at its start.
     */
    @Test
    fun `a word anywhere in the name counts`() {
        assertNear("touseef", "(Shari)Touseef Adnan Lappay Wali")
        assertNear("rahman", "Abdul Rahman Chacha Lamber Lappay Wali")
        assertNear("dokandar", "Abbas Ghala Mandi Pasrur Dokandar", within = 4)
        assertNear("khan", "Bilal Khan")
    }

    /** Saying more of the name should settle which one of several is meant. */
    @Test
    fun `a second word narrows it`() {
        assertEquals("Abbas Loader", rank("abbas", "loader").first())
    }

    /**
     * The point of ordering rather than filtering. Whatever was said, and
     * however it was spelled, every customer is still reachable — the owner is
     * never told their own book is empty.
     */
    @Test
    fun `nothing is ever removed`() {
        assertEquals(book.size, rank("zzzznotaname").size)
        assertEquals(book.size, rank("asgar").size)
    }

    /**
     * A word that half the book shares cannot outweigh the one word that
     * identifies anybody.
     *
     * This is the shop's real shape: dozens of customers are recorded as some
     * variety of spray wala, and "Asghar Spray Wala" spoken against them put
     * every spray wala level with both Asghars — one Asghar finished below a
     * man named Abdul Latif. Counting a word for what it narrows fixes that
     * without a list of words to ignore, which would need adding to for every
     * shop and every trade.
     */
    @Test
    fun `a word the whole book shares counts for little`() {
        val shop = listOf(
            "Asghar Matykay (Spray Wala)",
            "Asghar Manager Lappay Wali",
            "Abdul Latif Spray Wala",
            "Ismail Spray Wala",
            "Anwar Spray Wala Mian Wali Bangla"
        ) + (1..80).map { "Customer $it Spray Wala" }

        val ranked = NameSearch.rankSpoken(shop, listOf("asghar", "spray", "wala")) { it }

        assertEquals("Asghar Matykay (Spray Wala)", ranked[0])
        assertEquals("Asghar Manager Lappay Wali", ranked[1])
        assertEquals("every customer is still listed", shop.size, ranked.size)
    }

    /** The same, with the spelling the recogniser actually produced. */
    @Test
    fun `rarity survives a misspelling of the rare word`() {
        val shop = listOf("Asghar Manager Lappay Wali", "Abdul Latif Spray Wala") +
            (1..40).map { "Customer $it Spray Wala" }

        val ranked = NameSearch.rankSpoken(shop, listOf("asgar", "spray", "wala")) { it }
        assertEquals("Asghar Manager Lappay Wali", ranked.first())
    }

    /**
     * The one that wrote five thousand rupees against the wrong man.
     *
     * "Maine Ahsaan Munshi ko 5000 diya" against a book with a Hassan in it
     * and no Ahsaan. The old matcher kept a name's consonants and nothing
     * else, so Ahsaan and Hassan were one name to it, and it answered without
     * asking. Coming nearest in a field of one is not recognition.
     */
    @Test
    fun `a near miss on the only candidate is not an answer`() {
        val said = listOf("ahsaan", "munshi")
        val shop = listOf("Hassan", "Bilal", "Aaa")
        assertNull(NameSearch.confidentMatch(shop, said) { it })
    }

    /** With the customer actually present, it should answer — and correctly. */
    @Test
    fun `the right customer is answered however it is spelled`() {
        val said = listOf("ahsaan", "munshi")
        assertEquals(
            "Ahsan Munshi Pasrur",
            NameSearch.confidentMatch(listOf("Hassan", "Ahsan Munshi Pasrur"), said) { it }
        )
        assertEquals(
            "Ehsaan Munshi",
            NameSearch.confidentMatch(listOf("Hassan", "Ehsaan Munshi"), said) { it }
        )
    }

    /**
     * Two customers who both answer to what was said are a question for the
     * owner, not a coin to toss. The picker is one tap and already in order.
     */
    @Test
    fun `a close second means asking`() {
        assertNull(
            NameSearch.confidentMatch(
                listOf("Asghar Ali", "Asghar Manager", "Bilal"), listOf("asghar")
            ) { it }
        )
        assertEquals(
            "Asghar Manager",
            NameSearch.confidentMatch(listOf("Asghar Manager", "Bilal"), listOf("asgar")) { it }
        )
    }

    /**
     * A name kept in Latin and spoken in Urdu is the same customer. This is
     * why a separate matcher existed; the measure carries it now.
     */
    @Test
    fun `a name saved in Latin is answered when spoken in Urdu`() {
        assertEquals(
            "Abu G",
            NameSearch.confidentMatch(
                listOf("Abu G", "Bilal", "Aaa"), listOf("ابو", "جی")
            ) { it }
        )
    }

    /** Nothing to go on leaves the screen's own order alone. */
    @Test
    fun `no spoken words changes nothing`() {
        assertEquals(book, NameSearch.rankSpoken(book, emptyList()) { it })
    }

    /**
     * Folding is for looking up and nothing else — it must never be what the
     * owner sees. These are equal once folded and plainly different on screen.
     */
    @Test
    fun `folding evens out spelling without changing names`() {
        assertEquals(NameSearch.fold("Asghar"), NameSearch.fold("Asgar"))
        assertEquals(NameSearch.fold("Abbas"), NameSearch.fold("Abas"))
        assertEquals(NameSearch.fold("Yaseen"), NameSearch.fold("Yasin"))
        assertEquals(NameSearch.fold("Masood"), NameSearch.fold("Masud"))
    }

    /** Typing is unchanged. The search box rule is not what was altered here. */
    @Test
    fun `typing still ranks as it did`() {
        assertEquals(0, NameSearch.rank("Ali Raza", "ali"))
        assertEquals(1, NameSearch.rank("Muhammad Ali", "ali"))
        assertEquals(2, NameSearch.rank("Wali Khan", "ali"))
        assertEquals(3, NameSearch.rank("Bilal", "ali"))
    }

    // ------------------------------------------------- one letter is not a name

    /**
     * Found by counting three logs of real entries. Every result was topped by
     * the same handful of names — "B", "Bh", "Aaa", "A Bilal", "Chacho Zahid
     * U.S" — whatever had been spoken, because a one-letter part answered any
     * word beginning with that letter and outscored the customer who was
     * actually meant.
     */
    @Test
    fun `a one letter name does not outrank the customer meant`() {
        val messy = listOf("A", "Aaa", "A Bilal", "Afzal Dhodi Bhattia Wala")
        val order = NameSearch.rankSpoken(messy, listOf("afzal")) { it }
        assertEquals("Afzal Dhodi Bhattia Wala", order.first())
    }

    /** The same letter hiding inside a longer name: "Chacho Zahid U.S". */
    @Test
    fun `a stray initial inside a name answers nothing`() {
        val messy = listOf("Chacho Zahid U.S", "Shahzad Steno")
        val order = NameSearch.rankSpoken(messy, listOf("shahzad", "stanon")) { it }
        assertEquals("Shahzad Steno", order.first())
    }

    /** A short name is still findable — it just has to be said, not guessed. */
    @Test
    fun `a genuinely short name still matches itself`() {
        val messy = listOf("Bh", "Bilal Khan")
        val order = NameSearch.rankSpoken(messy, listOf("bh")) { it }
        assertEquals("Bh", order.first())
    }

    // ------------------------------------------ the spaces are not where we put them

    /**
     * Both from the logs. A recogniser writes a name the way it hears it, not
     * the way the book spells it, and it puts the gaps wherever it likes.
     */
    @Test
    fun `a stored name said as one word is still found`() {
        assertNear("lapewali", "Abdul Rahman Chacha Lamber Lappay Wali")
        assertNear("lapewali", "(Shari)Touseef Adnan Lappay Wali")
    }

    /** And the other way: one stored word arriving as two. */
    @Test
    fun `a stored name said as two words is still found`() {
        val shop = listOf("Rana Matyky", "Rana Akhtar Darkali", "Rana Sajjad Boota") +
            (1..200).map { "Filler Customer $it" }
        val order = NameSearch.rankSpoken(shop, listOf("rana", "mate", "ki")) { it }
        assertEquals("Rana Matyky", order.first())
    }

    /** A join that answers nothing must not disturb what already worked. */
    @Test
    fun `joining words does not unsettle an ordinary match`() {
        assertEquals("Abbas Loader", rank("abbas", "loader").first())
        assertNear("asgar", "Asghar")
    }

    // ------------------------------------------------------------ the search box

    /**
     * Reported by the owner: the village names half his book is known by would
     * not come up when typed. The search box only ever tested an exact
     * fragment of the stored spelling, so a name typed the way it is said —
     * and that is how these names are said — found nothing at all.
     */
    @Test
    fun `typing a name the way it is said still finds it`() {
        assertTrue(NameSearch.matches("Abdul Aziz Khurpa", null, "kurpa"))
        assertTrue(NameSearch.matches("Abdullah Bhatti Lappay Wali", null, "lapewali"))
        assertTrue(NameSearch.matches("Asghar Ali", null, "asgar"))
        assertTrue(NameSearch.matches("Yaseen Bhai", null, "yasin"))
    }

    /** Exact typing is untouched — it is still tried first and still wins. */
    @Test
    fun `typing an exact fragment behaves as it always did`() {
        assertTrue(NameSearch.matches("Bilal Khan", null, "bilal"))
        assertTrue(NameSearch.matches("Bilal Khan", null, "khan"))
        assertTrue(NameSearch.matches("Bilal Khan", null, ""))
    }

    /** Generous is not the same as useless. A wrong name is still no match. */
    @Test
    fun `typing does not hand back the whole book`() {
        assertFalse(NameSearch.matches("Bilal Khan", null, "asgar"))
        assertFalse(NameSearch.matches("Bilal Khan", null, "khurpa"))
        // Below the floor a fold is a coincidence, not a name.
        assertFalse(NameSearch.matches("Bilal Khan", null, "ka"))
    }

    /** The phone number still answers when digits are what was typed. */
    @Test
    fun `a number still finds its customer`() {
        assertTrue(NameSearch.matches("Bilal Khan", "0300-1234567", "1234"))
        assertFalse(NameSearch.matches("Bilal Khan", "0300-1234567", "9999"))
    }

    /**
     * One slip of the finger. Folding forgives how a name is SAID; this
     * forgives a letter that is simply wrong, which folding cannot, because a
     * wrong letter is a different sound rather than the same one misspelled.
     */
    @Test
    fun `one wrong letter still finds the customer`() {
        assertTrue(NameSearch.matches("Nazeer Ahmad", null, "nazer"))
        assertTrue(NameSearch.matches("Aslam Traders", null, "aslm"))
        assertTrue(NameSearch.matches("Matyky Wala", null, "matyki"))
    }

    /** Two slips is not a typo any more, it is a different name. */
    @Test
    fun `a second wrong letter is not forgiven`() {
        assertFalse(NameSearch.matches("Aslam Traders", null, "akram"))
        assertFalse(NameSearch.matches("Nazeer Ahmad", null, "bashir"))
        assertFalse(NameSearch.matches("Abbas Kichia", null, "abdul"))
    }

    /**
     * Short queries stay strict. "khax" folds to three letters — the silent h
     * goes — and below four a fold is already a coincidence rather than a
     * name, so it never reaches the typo rule at all.
     */
    @Test
    fun `a typo is only forgiven once the query is long enough`() {
        assertFalse(NameSearch.matches("Bilal Khan", null, "khax"))
    }

    /**
     * Four is the floor, and it earns its place: a dropped vowel is the most
     * common typo there is, and it lands exactly here.
     */
    @Test
    fun `a dropped vowel is forgiven at four letters`() {
        assertTrue(NameSearch.matches("Aslam Traders", null, "aslm"))
        assertTrue(NameSearch.matches("Tariq Seeds", null, "tarq"))
    }

    @Test
    fun `withinOneEdit counts a change, a gap and an extra letter as one`() {
        assertTrue(NameSearch.withinOneEdit("nazer", "nazir"))   // changed
        assertTrue(NameSearch.withinOneEdit("aslm", "aslam"))    // missing
        assertTrue(NameSearch.withinOneEdit("kichia", "kichi"))  // extra
        assertTrue(NameSearch.withinOneEdit("same", "same"))
        assertFalse(NameSearch.withinOneEdit("aslam", "aslamxy"))
        assertFalse(NameSearch.withinOneEdit("hello", "world"))
    }
}
