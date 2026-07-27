package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the microphone is allowed to conclude.
 *
 * VoiceEntry decides whose page an amount lands on. It was written to be
 * checkable — the customer list is passed in rather than looked up — and until
 * now nothing checked it. The bug these tests exist for shipped and was found
 * by the owner on his own ledger: every sentence opening "میں نے" was matched
 * to a customer named Moon, because both reduce to the consonants "mn".
 *
 * The first two tests are that bug. The rest are the rules that replaced it.
 */
class VoiceEntryTest {

    // ---------------------------------------------------------------- reported

    /**
     * The bug, exactly as it was reported. "میں نے" is grammar and must never
     * stand in for a name; the sentence is about Ahmed.
     */
    @Test
    fun `grammar words do not stand in for a customer`() {
        val p = VoiceEntry.parse(
            "میں نے احمد کو پانچ ہزار دی ہے",
            listOf("Moon", "Ahmed")
        )
        assertEquals("Ahmed", p.partyName)
        assertEquals(5000.0, p.amount!!, 0.0)
        assertEquals(true, p.isGiven)
    }

    /**
     * The same sentence with Ahmed absent. Nothing may be matched at all —
     * this is the case that used to hand the owner a stranger's page.
     */
    @Test
    fun `a sentence with no known name matches nobody`() {
        val p = VoiceEntry.parse(
            "میں نے احمد کو پانچ ہزار دی ہے",
            listOf("Moon")
        )
        assertNull(p.partyName)
        assertEquals(5000.0, p.amount!!, 0.0)
    }

    /**
     * The second reported sentence. "دی" was missing from the verbs and the
     * old test could not see past a trailing "ہے", so the direction came back
     * blank and the owner was shown a dash where "I Gave" belonged.
     */
    @Test
    fun `di is heard as gave even before a trailing hai`() {
        val p = VoiceEntry.parse(
            "میں نے احتشام کو 10 ہزار دیے",
            listOf("Moon", "Ehtisham")
        )
        assertEquals("Ehtisham", p.partyName)
        assertEquals(10000.0, p.amount!!, 0.0)
        assertEquals(true, p.isGiven)
    }

    // ------------------------------------------------------------------- rules

    /**
     * Moon and Amin are the same consonants. Two customers fitting equally
     * well is not a match — the caller opens the list, which costs a tap and
     * cannot put money on the wrong person.
     */
    @Test
    fun `two equally good matches are refused rather than guessed`() {
        val p = VoiceEntry.parse("امین کو ہزار دیا", listOf("Moon", "Amin"))
        assertNull(p.partyName)
        assertEquals(1000.0, p.amount!!, 0.0)
    }

    /** The same sentence with only one of them on the books resolves. */
    @Test
    fun `a single candidate resolves where two would not`() {
        val p = VoiceEntry.parse("امین کو ہزار دیا", listOf("Amin", "Bilal"))
        assertEquals("Amin", p.partyName)
    }

    /**
     * "Ali" leaves the single consonant "l" — far too little to match on, so
     * it was skipped entirely and one of the commonest names in Pakistan could
     * not be spoken. It is compared with its vowels kept instead.
     */
    @Test
    fun `very short names match on their vowels`() {
        val p = VoiceEntry.parse("علی سے دس ہزار لیے", listOf("Ali", "Bilal"))
        assertEquals("Ali", p.partyName)
        assertEquals(10000.0, p.amount!!, 0.0)
        assertEquals(false, p.isGiven)
    }

    /**
     * The reason the consonant skeleton exists: a shop saves a name in Latin
     * and speaks it in Urdu. "جی" is part of this customer's name, not
     * grammar, and must survive the stopword filter.
     */
    @Test
    fun `a name saved in Latin is found when spoken in Urdu`() {
        val p = VoiceEntry.parse("ابو جی کو دو ہزار دیے", listOf("Abu G"))
        assertEquals("Abu G", p.partyName)
        assertEquals(2000.0, p.amount!!, 0.0)
    }

    /** Nobody says a customer's whole entry. The first part must be enough. */
    @Test
    fun `part of a long stored name is enough`() {
        val p = VoiceEntry.parse(
            "عبدالرحمان کو 5 ہزار دیے",
            listOf("Abdul Rahman Chacha Lamber Lappay Wali")
        )
        assertEquals("Abdul Rahman Chacha Lamber Lappay Wali", p.partyName)
        assertEquals(5000.0, p.amount!!, 0.0)
    }

    /** The recogniser returns whichever script was spoken. Both must work. */
    @Test
    fun `roman urdu is read the same as urdu`() {
        val p = VoiceEntry.parse("Bilal ko paanch hazaar diye", listOf("Bilal"))
        assertEquals("Bilal", p.partyName)
        assertEquals(5000.0, p.amount!!, 0.0)
        assertEquals(true, p.isGiven)
    }

    /**
     * A sentence that never says which way the money went. Null, so the owner
     * is asked — a guess here lands the amount on the wrong side of the book.
     */
    @Test
    fun `an unsaid direction stays unsaid`() {
        val p = VoiceEntry.parse("احمد کو پانچ ہزار", listOf("Ahmed"))
        assertEquals("Ahmed", p.partyName)
        assertNull(p.isGiven)
    }

    /** Figures spoken as words, in the forms a shopkeeper actually uses. */
    @Test
    fun `spoken figures are read`() {
        assertEquals(
            200000.0,
            VoiceEntry.parse("دو لاکھ", emptyList()).amount!!,
            0.0
        )
        assertEquals(
            700.0,
            VoiceEntry.parse("saat sau", emptyList()).amount!!,
            0.0
        )
    }

    /** Eastern digits are the same figures. ۵۰۰۰ is five thousand. */
    @Test
    fun `eastern digits are read as figures`() {
        assertEquals(
            5000.0,
            VoiceEntry.parse("۵۰۰۰", emptyList()).amount!!,
            0.0
        )
    }

    /**
     * Nothing spoken, nothing concluded. An empty customer list must not
     * throw — a shop's first day has no customers in it.
     */
    @Test
    fun `an empty list is safe`() {
        val p = VoiceEntry.parse("پانچ ہزار دیے", emptyList())
        assertNull(p.partyName)
        assertTrue(p.amount!! > 0)
    }

    /**
     * English did not work at all. The number words and the verbs were Urdu
     * and Roman Urdu only, so an owner running the app in English — the
     * default — had the customer's name found and nothing else. The entry
     * opened blank and they typed it in by hand, which is what the microphone
     * was meant to save them.
     */
    @Test
    fun `english sentences are read`() {
        val p = VoiceEntry.parse("I gave Ahmed five thousand", listOf("Ahmed"))
        assertEquals("Ahmed", p.partyName)
        assertEquals(5000.0, p.amount!!, 0.0)
        assertEquals(true, p.isGiven)
    }

    @Test
    fun `english money coming in`() {
        val p = VoiceEntry.parse("Received twenty five thousand from Bilal", listOf("Bilal"))
        assertEquals("Bilal", p.partyName)
        assertEquals(25000.0, p.amount!!, 0.0)
        assertEquals(false, p.isGiven)
    }

    /**
     * English builds a hundred thousand out of two words. Read the old way —
     * a hundred, then a thousand, added — this came out as 1,100 for a figure
     * the owner meant as 100,000.
     */
    @Test
    fun `english hundreds multiply rather than add`() {
        val p = VoiceEntry.parse("Gave Ali one hundred thousand", listOf("Ali"))
        assertEquals(100000.0, p.amount!!, 0.0)
        assertEquals(750.0, VoiceEntry.parse("seven hundred fifty", listOf("Ali")).amount!!, 0.0)
    }

    /**
     * "Gave back" says money moved but not who moved it. Both directions
     * match, so the sentence is reported unclear and the owner is asked —
     * better than putting the amount on the wrong side of the ledger.
     */
    @Test
    fun `gave back is left for the owner to settle`() {
        val p = VoiceEntry.parse("Ahmed gave back five thousand", listOf("Ahmed"))
        assertEquals(5000.0, p.amount!!, 0.0)
        assertNull(p.isGiven)
    }

    /** Crore was missing from both languages. */
    @Test
    fun `crore is understood`() {
        assertEquals(10000000.0, VoiceEntry.parse("ek crore", listOf("Ali")).amount!!, 0.0)
        assertEquals(10000000.0, VoiceEntry.parse("ایک کروڑ", listOf("Ali")).amount!!, 0.0)
    }

    /** English grammar must not be mistaken for a customer. */
    @Test
    fun `english grammar words are not names`() {
        val p = VoiceEntry.parse("I have the total amount", listOf("Ihsan", "Amina"))
        assertNull(p.partyName)
    }

    // ---- Reported from a real shop, 1162 customers on the books ----
    //
    // "Maine Ahmad ko 50000 ki urea di" put a customer called Moon above the
    // owner's money. "Main ne" was already treated as grammar, but the phone
    // writes it as one word, and "Maine" reduces to the same two consonants
    // as Moon. Worse than a stray match: a full match on two letters outscored
    // a partial match on the right name, so Ahmad Ali lost to Moon in a
    // sentence that said Ahmad.

    /** The name from a shop's own book, emoji and all. */
    private val moon = "Moon\uD83D\uDDA4\u2640\u2640\u2642\u2642"

    private fun heard(names: List<String>) =
        VoiceEntry.parse("Maine Ahmad ko 50000 ki urea di", names)

    @Test
    fun `the customer who was spoken about wins over a short name`() {
        assertEquals("Ahmad Ali", heard(listOf(moon, "Ahmad Ali")).partyName)
        assertEquals("Ahmad Bhai", heard(listOf(moon, "Ahmad Bhai")).partyName)
        assertEquals("Ahmad", heard(listOf(moon, "Ahmad")).partyName)
    }

    @Test
    fun `with no such customer it names no one rather than a stranger`() {
        assertNull(heard(listOf(moon, "A Bilal", "Touseef")).partyName)
    }

    @Test
    fun `the joined pronouns are grammar, not customers`() {
        for (p in listOf("Maine", "Mene", "Tumne", "Usne", "Humne", "Unhone")) {
            assertEquals(
                "$p should not have matched a customer",
                "Bilal",
                VoiceEntry.parse("$p Bilal ko 500 diye", listOf(moon, "Bilal")).partyName
            )
        }
    }

    /** The amount and the direction were never wrong here. Keep them right. */
    @Test
    fun `the figure and the direction survive the fix`() {
        val p = heard(listOf(moon, "Ahmad Ali"))
        assertEquals(50000.0, p.amount!!, 0.0)
        assertEquals(true, p.isGiven)
    }

    /**
     * Short names stay reachable, but two of them sharing consonants is a tie,
     * and a tie is handed back rather than guessed. Moon and Mun are both
     * m-n; with both on the books the owner is asked, exactly as with Moon and
     * Amin further up.
     *
     * This was briefly asserted the other way, when two-consonant names were
     * made to agree on their vowels as well. That looked like caution and was
     * not: it broke a name saved in Latin and spoken in Urdu, where the short
     * vowels are never written. The tie-break was already the right answer.
     */
    @Test
    fun `short names tie rather than guess`() {
        assertNull(VoiceEntry.parse("Moon ko 500 diye", listOf(moon, "Mun")).partyName)
        assertEquals(moon, VoiceEntry.parse("Moon ko 500 diye", listOf(moon, "Bilal")).partyName)
        assertEquals("Mun", VoiceEntry.parse("Mun ko 500 diye", listOf("Mun", "Bilal")).partyName)
    }

    // ------------------------------------------------ choosing between answers

    /** Stands in for the book: a word it knows is worth something. */
    private fun bookOf(vararg known: String): (List<String>) -> Double =
        { words -> words.count { it in known }.toDouble() * 3.0 }

    /**
     * The recogniser lost the end of the sentence and this app acted on it.
     *
     * Every other candidate carried the figure; the first did not, so the
     * entry was refused for having no amount. Worse, the recogniser had scored
     * the truncated one HIGHEST — 0.89 against 0.83 — which is why its
     * confidence is not what decides this.
     */
    @Test
    fun `a candidate with the figure beats one that lost it`() {
        val heard = listOf(
            "Maine Memorial School",
            "Maine Memorial School 5000 Di Hai",
            "Maine Memorial School 5000 Diye",
            "Maine Memorial School 5000"
        )
        val at = VoiceEntry.bestCandidate(heard, emptyList(), bookOf("memorial", "school"))
        assertTrue("chose ${heard[at]}", at != 0)
        assertEquals(5000.0, VoiceEntry.parse(heard[at], emptyList()).amount!!, 0.0)
    }

    /**
     * "Kripa" answers nobody. Three of the five candidates said "khurpa",
     * which fifty customers carry, and the owner had to say it all again.
     */
    @Test
    fun `a candidate the book recognises beats one it does not`() {
        val heard = listOf(
            "Maine Kripa ko 10000 Diya",
            "Maine khurpa udaasar Diya",
            "Maine khurpa 10000 Diya"
        )
        val at = VoiceEntry.bestCandidate(heard, emptyList(), bookOf("khurpa"))
        assertEquals("Maine khurpa 10000 Diya", heard[at])
    }

    /**
     * Most candidates differ only in how the verb was spelled. Nothing should
     * move for those — the recogniser's own order stands.
     */
    @Test
    fun `equally good candidates keep the order they came in`() {
        val heard = listOf(
            "Maine Ahmad ko 5000 Diye",
            "Maine Ahmed ko 5000 Diye",
            "Maine Ahmad ko 5000 Di Hai"
        )
        assertEquals(0, VoiceEntry.bestCandidate(heard, emptyList(), bookOf("ahmad", "ahmed")))
    }

    /** One answer, or none, is not a choice. */
    @Test
    fun `a single candidate is taken as it is`() {
        assertEquals(0, VoiceEntry.bestCandidate(listOf("Maine Bilal"), emptyList()) { 0.0 })
    }
}
