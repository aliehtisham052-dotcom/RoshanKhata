package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.Hudhud.AboutTopic
import com.innovation313.roshankhata.data.Hudhud.Ask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 14 Nov 2026, the owner: "app kab bani, kab publish hui, Hudhud kab add hua" and "Hudhud apni aap-beeti sunaye". The whole
 * of Hudhud (the rules, the learnt sense and the how-to pages), not only the strict reader: the history is answered, and
 * neither a version, a maker, a name's story, a how-to page nor a customer's story is taken for it.
 */
class HudhudStoryWayTest {

    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val names = listOf(Hudhud.Name(1, "Ali Raza"), Hudhud.Name(2, "علی رضا"), Hudhud.Name(6, "Bashir Ahmad"))
    private val topics = listOf(
        Hudhud.Topic("faq_invoice", "Invoice انوائس bill", "Make an invoice for a customer and share it"),
        Hudhud.Topic("intro_app", "App ایپ application", "What the app is and how it began, how to use it and how old it is"),
        Hudhud.Topic("intro_staff", "Staff and salary سٹاف تنخواہ", "Salary, advances and attendance of the staff")
    )

    private fun read(s: String, language: String? = null, who: List<Hudhud.Name> = names) = Hudhud.understand(s, who, topics, language)

    private fun about(t: AboutTopic) = Ask.About(t)

    @Test fun `the history is answered over the how-to pages and the sense`() {
        for ((q, t) in listOf(
            "Hudhud apni aap beeti sunao" to AboutTopic.HOOPOE_STORY, "Hudhud ki kahani sunao" to AboutTopic.HOOPOE_STORY,
            "tell me your story" to AboutTopic.HOOPOE_STORY, "ہدہد اپنی آپ بیتی سناؤ" to AboutTopic.HOOPOE_STORY,
            "app kab publish hui" to AboutTopic.APP_PUBLISHED, "when was the app published" to AboutTopic.APP_PUBLISHED,
            "ایپ کب پبلش ہوئی" to AboutTopic.APP_PUBLISHED, "Hudhud kab add hua" to AboutTopic.HUDHUD_ADDED,
            "how old is Hudhud" to AboutTopic.HUDHUD_ADDED, "ہدہد کی عمر کیا ہے" to AboutTopic.HUDHUD_ADDED,
            "how old is the app" to AboutTopic.APP_JOURNEY, "app ki history" to AboutTopic.APP_JOURNEY,
            "Roshan Khata kab bana" to AboutTopic.APP_JOURNEY, "ye app kab bani" to AboutTopic.APP_JOURNEY,
            "app pe kaam kab shuru hua" to AboutTopic.APP_STARTED
        )) assertEquals(q, about(t), read(q))
    }

    @Test fun `Persian's ki is a when only in a Persian app`() {
        assertEquals(about(AboutTopic.APP_JOURNEY), read("برنامه کی ساخته شد", language = "fa"))
        assertNotEquals(about(AboutTopic.APP_JOURNEY), read("برنامه کی ساخته شد", language = "ur"))
    }

    @Test fun `a version, a maker, what the app is and why his name is Hudhud keep their answers`() {
        assertEquals(about(AboutTopic.VERSION), read("app ka version kya hai"))
        assertEquals(about(AboutTopic.MAKER), read("kis ne banayi yeh app"))
        assertEquals(about(AboutTopic.APP), read("Roshan Khata kya hai"))
        assertEquals(about(AboutTopic.NAME_STORY), read("why are you called hudhud"))
        assertEquals(about(AboutTopic.NAME_STORY), read("aap ka naam hudhud kyun hai"))
    }

    @Test fun `a customer's story or a question of the book is never the history`() {
        for (q in listOf("Ali Raza ki kahani", "Ali Raza kab aaya", "Ali Raza ka khata kab bana", "aaj ki sale kab hui", "mera khata kab bana")) {
            val a = read(q)
            assertTrue("\"$q\" -> $a", a !is Ask.About || a.topic !in Hudhud.STORY_TOPICS)
        }
    }

    @Test fun `a customer called Roshan keeps his own khata`() {
        val who = names + Hudhud.Name(7, "Roshan Din")
        val a = read("Roshan Khata kab bana", who = who)
        assertTrue(a.toString(), a !is Ask.About || a.topic !in Hudhud.STORY_TOPICS)
    }

    @Test fun `the history is no label of the bank`() {
        for (t in Hudhud.STORY_TOPICS) assertEquals(null, Hudhud.labelOf(Ask.About(t)))
    }
}
