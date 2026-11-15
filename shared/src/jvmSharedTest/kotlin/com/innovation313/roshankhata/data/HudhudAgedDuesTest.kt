package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.Hudhud.Ask
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 15 Nov 2026, the owner's 1,000 sentences, by class: old and doubtful dues ("kis ka udhaar sab se purana hai", "kis ka udhaar doob sakta
 * hai", "kaun payment dene mein sust hai"). They read as the biggest debt or the shop's own payables; the dues by age page ranks every
 * customer by how long his money has stood. Through the whole pipeline with the learnt sense (lesson 145); every sentence invented.
 */
class HudhudAgedDuesTest {
    private val topics = listOf("screen_aging", "intro_followup", "intro_cashbook", "screen_bills").map { Hudhud.Topic(it, it, it) }
    init { HudhudSense.use(HudhudModelTest.shippedModel()) }
    private fun read(s: String, lang: String = "ur-Latn") = Hudhud.understand(s, emptyList(), topics, lang)

    @Test fun `old or doubtful dues open the dues by age page`() {
        for (s in listOf("Kis ka udhaar sab se purana hai", "Kis ka udhaar bohat dair se baqi hai", "Kis ka udhaar doob sakta hai",
            "Kaun payment dene mein sust hai", "Kis ki wasooli mushkil lag rahi hai"))
            assertEquals(s + " -> " + read(s), Ask.Help("screen_aging"), read(s))
        assertEquals(Ask.Help("screen_aging"), read("whose dues are the oldest", "en"))
        assertEquals(Ask.Help("screen_aging"), read("who is slow to pay", "en"))
    }

    @Test fun `the biggest debt and the plain figures stay as they were`() {
        assertEquals(Ask.Figure(VoiceQuestion.Kind.WHO_OWES_MOST), read("Kis ka udhaar sab se zyada hai"))
        assertEquals(false, read("Aaj kitna udhaar hai") == Ask.Help("screen_aging"))
    }

    @Test fun `a bill, a supplier or goods are other readings`() {
        assertEquals(false, Hudhud.agedDues("sab se purana bill baqi hai"))
        assertEquals(false, Hudhud.agedDues("supplier ka udhaar sab se purana"))
        assertEquals(false, Hudhud.agedDues("sab se purana maal baqi hai"))
        assertEquals(false, Hudhud.agedDues("sab se purana"))
        assertEquals(false, Hudhud.agedDues("purchase ka udhaar sab se purana"))
        assertEquals(false, Hudhud.agedDues("udhaar kitna hai"))
    }
}
