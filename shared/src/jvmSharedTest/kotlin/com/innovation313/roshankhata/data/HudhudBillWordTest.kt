package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.Hudhud.Ask
import com.innovation313.roshankhata.data.Hudhud.StatusOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 15 Nov 2026, the owner's 1,000 sentences, by class: a shop says "bill" for the paper it hands a customer, the one a
 * supplier sends it and the one the light company sends it, and the word alone does not say which. What is DONE to it
 * does: making, sending, changing it, or one customer's, is the customer's bill (the documents page); owing, late, a day,
 * a reminder is a bill to pay, supplier's or utility's, and he is asked which; a supplier or purchase word, or goods that
 * came, keeps it the supplier's bills as before. Names and sentences are invented (lesson 80).
 */
class HudhudBillWordTest {

    private val topics = listOf("intro_documents", "intro_cashbook", "screen_bills").map { Hudhud.Topic(it, it, it) }

    private fun read(s: String) = Hudhud.byRules(s, emptyList(), topics)

    private val ask = Ask.DidYouMean(listOf(Ask.Help("intro_cashbook"), Ask.Status(StatusOf.BILLS)))

    @Test fun `making, sending or changing a bill is the documents page`() {
        for (s in listOf(
            "Bill WhatsApp par bhejo", "Bill mein discount dena hai", "Bill par dukan ki muhar lagao", "Bill mein qeemat badlo",
            "Bill ka PDF banao", "Bill par naam aur number likhna hai", "send the bill on WhatsApp", "is customer ka bill dikhao"
        )) assertEquals(s, Ask.Help("intro_documents"), read(s))
    }

    @Test fun `a bill that is owed or late is asked which`() {
        for (s in listOf(
            "Kaun sa bill baqi hai", "Kaun sa bill late ho gaya", "Bill ki aakhri tareekh kab hai", "Bill ki yaad dahani lagao",
            "Is hafte kaun se bill dene hain", "which bill is still due", "کون سا بل باقی ہے", "कौन सा बिल बाकी है"
        )) assertEquals(s, ask, read(s))
    }

    @Test fun `a supplier or purchase word keeps it the supplier's bills`() {
        assertEquals(Ask.Status(StatusOf.BILLS), read("supplier ke kitne bill pending hain"))
        assertEquals(Ask.Status(StatusOf.BILLS), read("maal ka kaun sa bill baqi hai"))
        assertEquals(Ask.Status(StatusOf.BILLS), read("company ka kaun sa bill baqi hai"))
    }

    @Test fun `bills that came are still the deliveries`() {
        assertNotEquals(ask, read("is mahine kitne bill aaye"))
        assertNotEquals(Ask.Help("intro_documents"), read("is mahine kitne bill aaye"))
    }

    @Test fun `the light bill is the cashbook and a plain invoice is the documents page, as before`() {
        assertEquals(Ask.Help("intro_cashbook"), read("Bijli ka bill kab dena hai"))
        assertEquals(Ask.Help("intro_documents"), read("naya invoice banana hai"))
    }

    @Test fun `without the cashbook page in the book the question is not asked`() {
        val only = listOf(Hudhud.Topic("screen_bills", "x", "x"))
        assertTrue(Hudhud.byRules("Kaun sa bill baqi hai", emptyList(), only) !is Ask.DidYouMean)
    }

    // The whole pipeline, with the learnt sense: it learnt every "bill" as a count of supplier bills, and must not turn
    // the rules' own "which bill?" (or the documents page) into that.
    @Test fun `the sense does not turn the rules' reading of a bare bill`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        for (s in listOf("Kaun sa bill baqi hai", "Kaun sa bill late ho gaya", "Bill ki aakhri tareekh kab hai")) {
            assertEquals(s, ask, Hudhud.understand(s, emptyList(), topics, "ur-Latn"))
        }
        for (s in listOf("Bill WhatsApp par bhejo", "Bill mein discount dena hai", "Bill ka PDF banao")) {
            assertEquals(s, Ask.Help("intro_documents"), Hudhud.understand(s, emptyList(), topics, "ur-Latn"))
        }
    }
}
