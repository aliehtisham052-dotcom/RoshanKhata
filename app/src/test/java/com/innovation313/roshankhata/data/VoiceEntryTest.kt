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
}
