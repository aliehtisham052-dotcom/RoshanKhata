package com.innovation313.roshankhata.ui

import org.junit.Assert.assertEquals
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
}
