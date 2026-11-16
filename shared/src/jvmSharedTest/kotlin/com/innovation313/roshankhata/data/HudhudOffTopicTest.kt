package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Not a question of the book (16 Nov 2026, audit T7 / H-05, H-08): a sum is never an entry form, and the weather, cricket or a
 * joke is answered plainly, not with the shop's profile or the owner's name. Through the whole pipeline (lesson 145).
 * Invented names; a customer named like a topic is still found.
 */
class HudhudOffTopicTest {

    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val names = listOf("Ali Raza", "Bashir Ahmad", "Cricket Club").mapIndexed { i, n -> Hudhud.Name(i + 1L, n) }
    private val goods = listOf("Urea", "DAP").mapIndexed { i, n -> Hudhud.Name(100L + i, n) }

    private fun read(lang: String, said: String, book: List<Hudhud.Name> = names) =
        Hudhud.understand(said, book, HudhudRealPages.forApp(lang), lang, goods, null)

    /** The same book without the customer named like a topic. */
    private val plain = names.filter { it.name != "Cricket Club" }

    @Test fun `a sum is never an entry`() {
        assertEquals(Hudhud.Ask.OffTopic(Hudhud.OffTopicWhy.SUM), read("ur-Latn", "500 aur 300 ka total kya hai"))
        assertEquals(Hudhud.Ask.OffTopic(Hudhud.OffTopicWhy.SUM), read("ur-Latn", "1200 aur 800 jama karo"))
        assertEquals(Hudhud.Ask.OffTopic(Hudhud.OffTopicWhy.SUM), read("en", "add 450 and 300"))
    }

    @Test fun `a sum written with signs is worked out by the calculator`() {
        assertEquals(Hudhud.Ask.OffTopic(Hudhud.OffTopicWhy.SUM, "2+2", 4.0), read("ur-Latn", "2+2 kitne hote hain"))
        assertEquals(Hudhud.Ask.OffTopic(Hudhud.OffTopicWhy.SUM, "500 × 3", 1500.0), read("ur-Latn", "500 × 3 kitna hua"))
        assertEquals(Hudhud.Ask.OffTopic(Hudhud.OffTopicWhy.SUM, "1200 / 4", 300.0), read("en", "what is 1200 / 4"))
        assertEquals(Hudhud.Ask.OffTopic(Hudhud.OffTopicWhy.SUM, "12x450", 5400.0), read("ur-Latn", "12x450 kitna banta hai"))
    }

    @Test fun `topics no book holds are refused plainly in nine languages`() {
        val general = Hudhud.Ask.OffTopic(Hudhud.OffTopicWhy.GENERAL)
        for ((lang, said) in listOf(
            "ur-Latn" to "cricket score kya hai", "ur-Latn" to "joke sunao", "ur-Latn" to "Pakistan ka capital kya hai",
            "ur-Latn" to "aaj mausam kaisa hai", "en" to "what is the weather today", "en" to "tell me a joke",
            "ur" to "ایک لطیفہ سناؤ", "hi" to "एक चुटकुला सुनाओ", "bn" to "একটা কৌতুক বলো", "ar" to "ما هي عاصمة باكستان",
            "fa" to "یک جوک بگو", "in" to "ceritakan lelucon", "sd" to "ڪرڪيٽ جو اسڪور ڇا آهي"
        )) assertEquals("[$lang] $said", general, read(lang, said, plain))
        // With a customer of that name in the book, his name comes first (a name is never lost to a topic word).
        assertEquals(Hudhud.Ask.Parties(listOf(3L)), read("ur-Latn", "Cricket Club ka baqaya"))
    }

    @Test fun `questions of the book stay questions of the book`() {
        val kept = listOf(
            "ur-Latn" to "Kis cheez ki sale mausam ke saath badhti hai", "ur-Latn" to "is mausam mein kya bikta hai",
            "ur-Latn" to "Ali Raza ka total kya hai", "ur-Latn" to "12 / 11 ki sale", "ur-Latn" to "12/11 ki sale",
            "ur-Latn" to "0300-1234567 kis ka number hai", "ur-Latn" to "aaj ki sale aur kal ki sale ka total",
            "ur-Latn" to "Cricket Club ka baqaya", "ur-Latn" to "aaj ki sale kitni hui"
        )
        for ((lang, said) in kept) {
            val a = read(lang, said)
            assertTrue("[$lang] $said read as $a", a !is Hudhud.Ask.OffTopic)
        }
        // An entry with figures in it stays an entry.
        assertTrue(read("ur-Latn", "Ali Raza ko 500 diye") is Hudhud.Ask.Entry)
        assertTrue(read("ur-Latn", "Ali Raza ko 2x450 ka saman diya") is Hudhud.Ask.Entry)
        assertTrue(read("ur-Latn", "500 aur 300 diye") is Hudhud.Ask.Entry)
    }

    // Each guard alone (lesson 129): a sentence only that guard keeps from being taken off the book.
    @Test fun `a giving verb keeps an entry even beside a word of adding`() {
        assertTrue(read("ur-Latn", "500 aur 300 total diye") is Hudhud.Ask.Entry)
    }

    @Test fun `somebody or something named keeps the reading even beside a sum`() {
        assertEquals(Hudhud.Ask.Parties(listOf(1L)), read("ur-Latn", "Ali Raza ka hisaab 500 + 300"))
        assertTrue(read("ur-Latn", "Urea 50 x 2 ka rate") is Hudhud.Ask.Goods)
    }

    @Test fun `a word of the book's figures keeps a topic word the book's`() {
        assertTrue(read("ur-Latn", "cricket match wale din ki sale kitni thi") !is Hudhud.Ask.OffTopic)
    }

    @Test fun `a help page found beside a topic word stays the help page`() {
        assertEquals(Hudhud.Ask.Help("screen_posters"), read("ur-Latn", "poster par cricket wala design kaise banaun"))
    }
}
