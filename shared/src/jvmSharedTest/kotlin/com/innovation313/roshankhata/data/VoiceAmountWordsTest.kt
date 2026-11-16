package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Amounts said in words (16 Nov 2026, audit T35): Hindi in Devanagari and Indonesian, each read by its own grammar, and a
 * scale after a number word the lists do not have is never a bare thousand. A wrong figure is worse than none: when the
 * reader is unsure, no amount (the owner types it). Bengali, Arabic, Farsi and Sindhi number words wait for a native check.
 */
class VoiceAmountWordsTest {

    private fun amount(said: String, lang: String? = null) = VoiceEntry.parse(said, emptyList(), lang).amount

    @Test fun `Hindi number words with a scale are the amount`() {
        assertEquals(500.0, amount("अली को पांच सौ दिए", "hi")!!, 0.0)
        assertEquals(500.0, amount("अली को पाँच सौ दिए", "hi")!!, 0.0)
        assertEquals(1500.0, amount("अली को डेढ़ हज़ार दिए", "hi")!!, 0.0)
        assertEquals(2500.0, amount("ढाई हजार मिले", "hi")!!, 0.0)
        assertEquals(125.0, amount("सवा सौ दिए", "hi")!!, 0.0)
        assertEquals(3500.0, amount("साढ़े तीन हज़ार दिए", "hi")!!, 0.0)
        assertEquals(175_000.0, amount("पौने दो लाख दिए", "hi")!!, 0.0)
        assertEquals(250.0, amount("दो सौ पचास दिए", "hi")!!, 0.0)
        assertEquals(5200.0, amount("पांच हज़ार दो सौ दिए", "hi")!!, 0.0)
        assertEquals(10_000_000.0, amount("एक करोड़ दिए", "hi")!!, 0.0)
        assertEquals(20.0, amount("बीस रुपये दिए", "hi")!!, 0.0)
        // A Hindi phrase is read whatever the recogniser's language was.
        assertEquals(1000.0, amount("अली रज़ा से एक हज़ार मिले")!!, 0.0)
    }

    @Test fun `a Hindi number word that may be another word, or a number the list lacks, gives no amount`() {
        assertNull(amount("अली को पैसे दो", "hi"))             // दो: "give"
        assertNull(amount("एक ग्राहक ने पैसे दिए", "hi"))       // एक: "a"
        assertNull(amount("पच्चीस हज़ार दिए", "hi"))            // twenty-five is not in the list: never a bare thousand
        assertNull(amount("दस बीस दिए", "hi"))                   // two numbers side by side
        assertNull(amount("दस बीस हज़ार दिए", "hi"))              // "ten or twenty thousand": no one figure
    }

    @Test fun `Indonesian number words by their own grammar`() {
        assertEquals(5000.0, amount("Ali bayar lima ribu", "in")!!, 0.0)
        assertEquals(25_000.0, amount("dua puluh lima ribu", "in")!!, 0.0)
        assertEquals(150_000.0, amount("seratus lima puluh ribu", "in")!!, 0.0)
        assertEquals(12_000.0, amount("dua belas ribu", "in")!!, 0.0)
        assertEquals(11_000.0, amount("sebelas ribu", "in")!!, 0.0)
        assertEquals(1000.0, amount("seribu", "in")!!, 0.0)
        assertEquals(1_000_000.0, amount("satu juta", "in")!!, 0.0)
        assertEquals(1_000_000.0, amount("sejuta", "in")!!, 0.0)
        assertEquals(2_500_000.0, amount("dua juta lima ratus ribu", "in")!!, 0.0)
        assertEquals(500.0, amount("lima ratus", "in")!!, 0.0)
        assertEquals(5.0, amount("lima rupiah", "in")!!, 0.0)
    }

    @Test fun `Indonesian digits grouped with dots stay as written`() {
        assertEquals(5000.0, amount("Ali bayar 5.000", "in")!!, 0.0)
        assertEquals(5000.0, amount("Rp 5.000 lima ribu", "in")!!, 0.0)
    }

    @Test fun `an Indonesian unit with no scale, two units side by side, or another language's word give no amount`() {
        assertNull(amount("Ali bayar lima", "in"))
        assertNull(amount("lima enam ribu", "in"))
        assertNull(amount("ribu", "in"))
        // "dua" is Urdu's prayer: Indonesian words are read only for an Indonesian sentence.
        assertNull(amount("Ali ke liye dua karo", "ur"))
        assertEquals(2000.0, amount("Bashir ko do hazar diye dua karo", "ur")!!, 0.0)
    }

    @Test fun `a scale after a number word the list lacks is never a bare thousand`() {
        assertNull(amount("pachees hazar diye"))
        assertNull(amount("پچیس ہزار دیے"))
        // After grammar, a verb or nothing it is one thousand, as before.
        assertEquals(1000.0, amount("Ali Raza ko hazar diye")!!, 0.0)
        assertEquals(1000.0, amount("hazar diye")!!, 0.0)
        assertEquals(1500.0, amount("dedh hazar diye")!!, 0.0)
    }
}
