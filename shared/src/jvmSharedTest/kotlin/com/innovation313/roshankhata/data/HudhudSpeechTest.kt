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
}
