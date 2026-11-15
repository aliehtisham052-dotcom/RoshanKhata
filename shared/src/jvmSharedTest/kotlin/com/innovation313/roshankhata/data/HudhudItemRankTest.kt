package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.Hudhud.Ask
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 15 Nov 2026, the owner's 1,000 sentences, by class: the best or the worst of the goods by profit (Insights ranks them), and the
 * goods that do not move or sell least (the sales report names the dead stock). Read only with an item word. A count ("kitne"),
 * a figure with no item word ("is mahine ka munafa") and the plain "sab se zyada bikta hai" (the top sellers) are other readings.
 * Every sentence below is through the whole pipeline with the learnt sense, because the sense overruled the rules (lesson 145).
 */
class HudhudItemRankTest {
    private val topics = listOf("screen_insights", "screen_sales_report", "screen_lapsed").map { Hudhud.Topic(it, it, it) }
    init { HudhudSense.use(HudhudModelTest.shippedModel()) }
    private fun read(s: String, lang: String = "ur-Latn") = Hudhud.understand(s, emptyList(), topics, lang)

    @Test fun `profit or loss on an item opens Insights`() {
        for (s in listOf("Sab se zyada kis item se kamai hui", "Kaun si cheez ne sab se zyada faida diya", "Kaun si cheez par nuqsan hai",
            "Sab se zyada munafa kis item par hai", "which item gives the most profit", "which product is making a loss"))
            assertEquals(s, Ask.Help("screen_insights"), read(s, if (s[0].isLowerCase()) "en" else "ur-Latn"))
    }

    @Test fun `goods that do not move or sell least open the sales report`() {
        for (s in listOf("Sab se kam kya bikta hai", "Kaun sa maal chal nahi raha", "Kaun sa maal mein paisa phansa hua hai"))
            assertEquals(s, Ask.Help("screen_sales_report"), read(s))
    }

    @Test fun `the top sellers stay the top sellers and a count is not a ranking`() {
        assertEquals(Ask.TopGoods(null), read("Sab se zyada kya bik raha hai"))
        assertEquals(false, read("Kitni cheezon par nuqsan hai") == Ask.Help("screen_insights"))
    }

    @Test fun `a figure with no item word is not the ranking`() {
        assertEquals(null, Hudhud.itemRankPage("is mahine ka munafa kitna hai"))
        assertEquals(null, Hudhud.itemRankPage("sab se zyada kya bik raha hai"))
        // The least sale of a day or a customer is a figure of the book, not the goods that sell least.
        assertEquals(null, Hudhud.itemRankPage("sab se kam sale kis din hui"))
        assertEquals(null, Hudhud.itemRankPage("sab se kam bikri kis customer ki hai"))
    }
}
