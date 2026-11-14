package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hudhud's words made ready for the ear (13 Nov 2026): sentences, marks out, a help page cut at a sentence. */
class HudhudSpeechTest {

    @Test fun `an answer is said a sentence at a time, figures kept whole`() {
        assertEquals(
            listOf("Today's sale is Rs 1.5 thousand.", "Well done!", "Anything else?"),
            HudhudSpeech.parts("Today's sale is Rs 1.5 thousand. Well done! Anything else?")
        )
        assertEquals(listOf("آج کی بکری۔", "اور کچھ؟"), HudhudSpeech.parts("آج کی بکری۔ اور کچھ؟"))
        assertEquals(listOf("no end at all"), HudhudSpeech.parts("no end at all"))
    }

    @Test fun `marks for the eye are taken out, lines are sentences`() {
        assertEquals(
            listOf("Ali Raza: 500", "Bashir: 300"),
            HudhudSpeech.parts("\u2068Ali Raza\u2069: 500\n\u2022 Bashir: 300")
        )
    }

    @Test fun `a long page is said up to whole sentences, then where the rest is`() {
        val page = "One two three. Four five six. Seven eight nine."
        assertEquals("One two three. Four five six. More on screen.", HudhudSpeech.upTo(page, 30, "More on screen."))
        assertEquals(page, HudhudSpeech.upTo(page, 200, "More on screen."))
        // The first sentence is said even when it alone is longer than the limit.
        assertEquals("One two three. More on screen.", HudhudSpeech.upTo(page, 5, "More on screen."))
    }

    @Test fun `a pause after a joining word is not the end of the question`() {
        assertTrue(HudhudSpeech.endsOpen("Ali ka"))
        assertTrue(HudhudSpeech.endsOpen("aaj Ali Raza ki"))
        assertTrue(HudhudSpeech.endsOpen("آج علی کی"))
        assertTrue(HudhudSpeech.endsOpen("अली का"))
        assertTrue(HudhudSpeech.endsOpen("what is the price of"))
        assertTrue(HudhudSpeech.endsOpen("قیمت از"))
        assertTrue(HudhudSpeech.endsOpen("harga untuk"))
        assertFalse(HudhudSpeech.endsOpen("Ali ka hisaab"))
        assertFalse(HudhudSpeech.endsOpen("aaj ki sale kitni hai"))
        assertFalse(HudhudSpeech.endsOpen("آج کی بکری بتاؤ"))
        assertFalse(HudhudSpeech.endsOpen(""))
    }

    @Test fun `a verse's address is left out of the voice, nothing else in brackets is`() {
        assertEquals(
            "I found a woman ruling over them.",
            HudhudSpeech.withoutVerseRefs("I found a woman ruling over them (27:23).")
        )
        assertEquals("“Sure news”.", HudhudSpeech.withoutVerseRefs("“Sure news” (27:22)."))
        assertEquals("He said so.", HudhudSpeech.withoutVerseRefs("He said so (27:16\u201317)."))
        assertEquals("He said so.", HudhudSpeech.withoutVerseRefs("He said so (27:24-26)."))
        // A place in brackets, a time of day and a plain figure are not an address.
        assertEquals("from Saba (Sheba) with news", HudhudSpeech.withoutVerseRefs("from Saba (Sheba) with news"))
        assertEquals("closes at (9:30 pm)", HudhudSpeech.withoutVerseRefs("closes at (9:30 pm)"))
        assertEquals("Rs (500)", HudhudSpeech.withoutVerseRefs("Rs (500)"))
        // Said sentence by sentence, nothing is left over after it.
        val said = HudhudSpeech.parts(HudhudSpeech.withoutVerseRefs("First telling (27:20\u201321). Second telling (27:22)."))
        assertEquals(listOf("First telling.", "Second telling."), said)
    }
}
