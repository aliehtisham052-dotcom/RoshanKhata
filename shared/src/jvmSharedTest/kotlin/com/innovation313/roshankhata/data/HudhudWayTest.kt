package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 14 Nov 2026, the owner's 12,082 questions run through Hudhud: a question that asks the way to do something keeps the
 * help page the rules found for it (the sense, which learnt few such sentences, turned about 1,000 of them into a
 * figure, a count or "about the app"); and "why is your name Hudhud?" is told the story of his name.
 */
class HudhudWayTest {

    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val names = listOf(Hudhud.Name(1, "Ali Raza"), Hudhud.Name(2, "علی رضا"), Hudhud.Name(6, "Bashir Ahmad"))
    private val topics = listOf(
        Hudhud.Topic("faq_invoice", "Invoice انوائس bill", "Make an invoice for a customer and share it"),
        Hudhud.Topic("intro_cheques", "Cheques چیک", "Post-dated cheques: add a cheque, clear it when the bank pays"),
        Hudhud.Topic("intro_staff", "Staff and salary سٹاف تنخواہ", "Salary, advances and attendance of the staff")
    )

    private fun read(s: String) = Hudhud.understand(s, names, topics, null)

    @Test fun `a question of the way is the page's`() {
        assertEquals(Hudhud.Ask.Help("intro_cheques"), read("naya cheque kahan add karna hai"))
        assertEquals(Hudhud.Ask.Help("faq_invoice"), read("how do I make an invoice"))
    }

    @Test fun `the way's words are the asking, never the page`() {
        assertTrue(Hudhud.asksTheWay("cheque kahan add karun"))
        assertTrue(Hudhud.asksTheWay("انوائس کیسے بناؤں"))
        assertTrue(Hudhud.asksTheWay("where do I add a cheque"))
        assertFalse(Hudhud.asksTheWay("aaj ki sale kitni hai"))
        assertFalse(Hudhud.asksTheWay("Ali Raza ka balance"))
    }

    @Test fun `a named person is still his`() {
        val a = read("Ali Raza ka balance")
        assertTrue(a.toString(), a is Hudhud.Ask.Parties || a is Hudhud.Ask.Person)
    }

    @Test fun `why his name is Hudhud`() {
        val story = Hudhud.Ask.About(Hudhud.AboutTopic.NAME_STORY)
        for (q in listOf(
            "aap ka naam hudhud kyun hai", "hudhud naam kyun rakha", "why is your name hudhud", "why are you called hudhud",
            "آپ کا نام ہدہد کیوں ہے", "आपका नाम हुदहुद क्यों है", "لماذا اسمك هدهد", "چرا اسمت هدهد است", "kenapa namamu hudhud"
        )) assertEquals(q, story, read(q))
    }

    @Test fun `a why about somebody else's name is not his story`() {
        assertNotEquals(Hudhud.Ask.About(Hudhud.AboutTopic.NAME_STORY), read("customer ka naam kyun nahi aa raha"))
        assertNotEquals(Hudhud.Ask.About(Hudhud.AboutTopic.NAME_STORY), read("aap ka naam kya hai"))
    }
}
