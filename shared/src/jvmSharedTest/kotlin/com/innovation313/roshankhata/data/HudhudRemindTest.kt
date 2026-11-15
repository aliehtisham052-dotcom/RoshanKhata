package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.Hudhud.Ask
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 15 Nov 2026, the owner's 1,000 sentences, by class: whom to remind, what to chase, a message for the money. They read as who made the
 * app ("Kis ko yaad dilana hai"), somebody's phone number ("Kis ko phone karna hai") or a product ("WhatsApp par yaad dahani bhejo").
 * The Follow-up page lists them and sends the reminder one by one. A bill's reminder is the bill rule's, a supplier's is not a customer's.
 * Through the whole pipeline with the learnt sense (lesson 145); every sentence invented.
 */
class HudhudRemindTest {
    private val topics = listOf("intro_followup", "intro_documents", "intro_cashbook", "screen_bills").map { Hudhud.Topic(it, it, it) }
    init { HudhudSense.use(HudhudModelTest.shippedModel()) }
    private fun read(s: String, lang: String = "ur-Latn") = Hudhud.understand(s, emptyList(), topics, lang)

    @Test fun `reminding customers for money opens Follow-up`() {
        for (s in listOf("WhatsApp par yaad dahani bhejo", "Reminder lagao", "Yaad dahani ka message likh do", "Sab ko ek saath message bhejo"))
            assertEquals(s, Ask.Help("intro_followup"), read(s))
    }

    @Test fun `whom to remind is the ones who have not paid`() {
        for (s in listOf("Kis ko yaad dilana zaroori hai", "Kis ko phone karna hai", "Kis ko ab tak yaad nahi dilaya", "Kis ko payment ki request bhejun",
            "Kis ko pehle yaad dilana chahiye", "Aaj kin se paise mangne hain", "Aaj kis ko yaad dilana hai"))
            assertEquals(s, Ask.Figure(com.innovation313.roshankhata.data.VoiceQuestion.Kind.NOT_PAID), read(s))
        assertEquals(Ask.Figure(com.innovation313.roshankhata.data.VoiceQuestion.Kind.NOT_PAID), read("whom should I remind today", "en"))
    }

    @Test fun `a stock reminder is not a customer's`() {
        assertEquals(false, Hudhud.remindPage("stock kam ho to yaad dilao"))
    }

    @Test fun `a bill's reminder, a supplier's and a setting are other readings`() {
        assertEquals(false, read("Bill ki yaad dahani lagao") == Ask.Help("intro_followup"))
        assertEquals(false, Hudhud.remindPage("supplier ko yaad dahani bhejo"))
        assertEquals(false, Hudhud.remindPage("reminder automation band karo"))
        assertEquals(false, Hudhud.remindPage("aaj ka khulasa"))
    }

    // A short word of the language is no product by being the start of its name ("par" and Paracetamol): lesson 139's class, in Latin letters.
    private val goods = listOf("Urea", "Paracetamol", "Parle").mapIndexed { i, n -> Hudhud.Name(100L + i, n) }
    private val withParWord = goods + Hudhud.Name(103L, "Par Traders")
    private fun readWith(s: String, g: List<Hudhud.Name> = goods) = Hudhud.understand(s, listOf(Hudhud.Name(1, "Ali Raza")), topics, "ur-Latn", g)

    @Test fun `par is not Paracetamol but a name of its own is still read`() {
        assertEquals(Ask.Help("intro_followup"), readWith("WhatsApp par yaad dahani bhejo"))
        assertEquals(false, readWith("kis par") is Ask.Goods)
        // The whole word "Par" of a product's own name still names it; so does the full name.
        assertEquals(true, (readWith("Par Traders ka rate", withParWord) as? Ask.Goods)?.ids?.contains(103L) == true)
        assertEquals(true, (readWith("Paracetamol ka rate") as? Ask.Goods)?.ids?.contains(101L) == true)
    }

    @Test fun `a customer whose name starts with par is not every sentence with par`() {
        val book = listOf(Hudhud.Name(1, "Parvez Ali"), Hudhud.Name(2, "Pari Gul"))
        for (q in listOf("kis par", "par hai", "par kya hai")) {
            val got = Hudhud.understand(q, book, topics, "ur-Latn", goods)
            assertEquals(q, false, got is Ask.NeedName || got is Ask.Person || got is Ask.Parties)
        }
    }

    @Test fun `a product whose whole name is par is still read`() {
        val lone = listOf(Hudhud.Name(100L, "Par"), Hudhud.Name(101L, "Urea"))
        val got = Hudhud.understand("Par ka rate", emptyList(), topics, "ur-Latn", lone)
        assertEquals(true, (got as? Ask.Goods)?.ids?.contains(100L) == true)
    }
}
