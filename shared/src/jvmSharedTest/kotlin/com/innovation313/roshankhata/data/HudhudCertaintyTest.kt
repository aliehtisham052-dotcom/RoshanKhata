package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.HudhudCertainty.Level
import com.innovation313.roshankhata.data.HudhudCertainty.Part
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The certainty vector (Marhala 6, 26 Nov 2026; BC-379): each part of a reading said as one level, from what the reading
 * carries and the sentence's own words. Invented names only (lesson 80). It reads; it never changes a reading — the last test
 * holds [Hudhud.understand]'s answer the same before and after a vector is made of it.
 */
class HudhudCertaintyTest {

    private val ali = Hudhud.Name(1L, "Ali Raza")
    private val bashir = Hudhud.Name(2L, "Bashir Ahmad")
    private val names = listOf(ali, bashir)

    @Test
    fun theMeaningsLevelFollowsTheReadingsKind() {
        val fig = Hudhud.Ask.Figure(VoiceQuestion.Kind.SALES_TODAY)
        assertEquals(Level.SURE, HudhudCertainty.of("aaj ki sale", fig)[Part.MEANING])
        assertEquals(Level.GUESSED, HudhudCertainty.of("aaj ki sale", Hudhud.Ask.Guessed(fig))[Part.MEANING])
        assertEquals(Level.ASKED, HudhudCertainty.of("x", Hudhud.Ask.DidYouMean(listOf(fig), Hudhud.Unsure.SIDE))[Part.MEANING])
        assertEquals(Level.CHOICE, HudhudCertainty.of("x", Hudhud.Ask.DidYouMean(listOf(fig, Hudhud.Ask.Drawer)))[Part.MEANING])
        assertEquals(Level.MISSING, HudhudCertainty.of("x", Hudhud.Ask.Unknown)[Part.MEANING])
        assertEquals(Level.MISSING, HudhudCertainty.of("cricket score", Hudhud.Ask.OffTopic(Hudhud.OffTopicWhy.GENERAL))[Part.MEANING])
        // Wrapped in "as it stands now", the inner reading's meaning.
        assertEquals(Level.GUESSED, HudhudCertainty.of("x", Hudhud.Ask.Now(Hudhud.Ask.Guessed(fig)))[Part.MEANING])
    }

    @Test
    fun aPersonIsSureOneAChoiceOfSeveralOrMissing() {
        assertEquals(Level.SURE, HudhudCertainty.of("Ali ka number", Hudhud.Ask.Person(listOf(1L), Hudhud.Facet.PHONE))[Part.PERSON])
        assertEquals(Level.CHOICE, HudhudCertainty.of("Ali", Hudhud.Ask.Parties(listOf(1L, 2L)))[Part.PERSON])
        assertEquals(Level.MISSING, HudhudCertainty.of("number", Hudhud.Ask.NeedName(Hudhud.Facet.PHONE))[Part.PERSON])
        // Who is likely to pay, with nobody named, asks no person at all.
        assertEquals(Level.NOT_ASKED, HudhudCertainty.of("aaj kaun dega", Hudhud.Ask.WhenPays(emptyList()))[Part.PERSON])
        assertEquals(Level.NOT_ASKED, HudhudCertainty.of("aaj ki sale", Hudhud.Ask.Figure(VoiceQuestion.Kind.SALES_TODAY))[Part.PERSON])
    }

    @Test
    fun aProductIsSureAChoiceOrMissing() {
        assertEquals(Level.SURE, HudhudCertainty.of("Urea ka rate", Hudhud.Ask.Goods(listOf(7L), Hudhud.GoodsFacet.PRICE))[Part.PRODUCT])
        assertEquals(Level.CHOICE, HudhudCertainty.of("rate", Hudhud.Ask.Goods(listOf(7L, 8L), Hudhud.GoodsFacet.PRICE))[Part.PRODUCT])
        assertEquals(Level.MISSING, HudhudCertainty.of("rate", Hudhud.Ask.NeedGoods(Hudhud.GoodsFacet.PRICE))[Part.PRODUCT])
        assertEquals(Level.CHOICE, HudhudCertainty.of("x", Hudhud.Ask.Took(listOf(1L), listOf(7L, 8L)))[Part.PRODUCT])
    }

    @Test
    fun anEntrysAmountAndSideAreSaidOrDefaulted() {
        val gave = Hudhud.Ask.Entry(500.0, true, listOf(1L), listOf("ali"))
        val v = HudhudCertainty.of("Ali ko 500 diye", gave, names)
        assertEquals(Level.SURE, v[Part.AMOUNT])
        assertEquals(Level.SURE, v[Part.SIDE])
        assertEquals(Level.SURE, v[Part.PERSON])
        // An amount with no verb of giving or taking: the side is the reading's default.
        assertEquals(Level.DEFAULT, HudhudCertainty.of("Ali 500", gave, names)[Part.SIDE])
        assertEquals(Level.NOT_ASKED, HudhudCertainty.of("aaj ki sale", Hudhud.Ask.Figure(VoiceQuestion.Kind.SALES_TODAY))[Part.AMOUNT])
    }

    @Test
    fun aSpanIsSaidDefaultedOrNotAsked() {
        assertEquals(Level.SURE, HudhudCertainty.of("is mahine ki sale", Hudhud.Ask.Period(Hudhud.Measure.SALES, VoiceQuestion.Window.THIS_MONTH))[Part.SPAN])
        // "Cashbook dikhao": the cashbook's own default day, nobody said it.
        assertEquals(Level.DEFAULT, HudhudCertainty.of("cashbook dikhao", Hudhud.Ask.Cashbook(null))[Part.SPAN])
        assertEquals(Level.SURE, HudhudCertainty.of("x", Hudhud.Ask.OnDays(Hudhud.DatedOf.SALES, VoiceQuestion.Dated(daysBack = 2)))[Part.SPAN])
        assertEquals(Level.CHOICE, HudhudCertainty.of("x", Hudhud.Ask.SpanLimit(listOf(Hudhud.Ask.Activity(null)), long = true))[Part.SPAN])
        assertEquals(Level.NOT_ASKED, HudhudCertainty.of("Ali ka number", Hudhud.Ask.Person(listOf(1L), Hudhud.Facet.PHONE))[Part.SPAN])
    }

    @Test
    fun aMoneySideIsStatedDefaultedOrAsked() {
        val toGet = Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GET)
        // "Mujhe kitne paise lene hain": the side is in the words.
        assertEquals(Level.SURE, HudhudCertainty.of("mujhe kitne paise lene hain", toGet)[Part.SIDE])
        // D15's own sentence: nobody said which way.
        assertEquals(Level.DEFAULT, HudhudCertainty.of("kitna qarz baqi hai", toGet)[Part.SIDE])
        val asked = Hudhud.Ask.DidYouMean(listOf(toGet, Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GIVE)), Hudhud.Unsure.SIDE)
        assertEquals(Level.ASKED, HudhudCertainty.of("kitna qarz baqi hai", asked)[Part.SIDE])
        assertEquals(Level.NOT_ASKED, HudhudCertainty.of("aaj ki sale", Hudhud.Ask.Figure(VoiceQuestion.Kind.SALES_TODAY))[Part.SIDE])
    }

    @Test
    fun theLanguageAndTheSensesScore() {
        val fig = Hudhud.Ask.Figure(VoiceQuestion.Kind.SALES_TODAY)
        assertEquals(Level.SURE, HudhudCertainty.of("aaj ki sale", fig, language = "ur-Latn")[Part.LANGUAGE])
        assertEquals(Level.DEFAULT, HudhudCertainty.of("aaj ki sale", fig)[Part.LANGUAGE])
        assertNull("no sense asked, no score", HudhudCertainty.of("aaj ki sale", fig).meaningScore)
        val weak = HudhudCertainty.of("kitna qarz baqi hai", Hudhud.Ask.Guessed(Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GET))).weak
        assertTrue(weak.toString(), Part.MEANING in weak && Part.SIDE in weak && Part.LANGUAGE in weak)
    }

    /** A vector reads the reading; the reading is the same after it (an adapter, never a second reader). */
    @Test
    fun makingAVectorChangesNoReading() {
        for (s in listOf("Ali Raza ka number", "aaj ki sale", "kitna qarz baqi hai", "Ali ko 500 diye")) {
            val before = Hudhud.understand(s, names, emptyList(), "ur-Latn")
            HudhudCertainty.of(s, before, names, language = "ur-Latn", withSense = true)
            assertEquals(s, before, Hudhud.understand(s, names, emptyList(), "ur-Latn"))
        }
    }
}
