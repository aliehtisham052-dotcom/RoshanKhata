package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.Hudhud.Ask
import com.innovation313.roshankhata.data.Hudhud.GoodsFacet
import com.innovation313.roshankhata.data.VoiceQuestion.Window
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Who bought a product over the days asked, and "kitni bik gayi" (15 Nov 2026, #26 step 4). A buyers question with a span is
 * about that span (it was wrapped in Ask.Now, "as it stands now"), a span the book cannot reach is told, and the stem "bik"
 * reads as selling. Sentences written by the maker of the tests, not by a native reader of each language.
 */
class HudhudBuyersSpanTest {
    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val goods = listOf(Hudhud.Name(100, "Urea"))
    private fun read(s: String, language: String? = null) = Hudhud.understand(s, emptyList(), emptyList(), language, goods = goods)
    private fun buyers(w: Window?) = Ask.Goods(listOf(100L), GoodsFacet.BUYERS, w)
    private fun sold(w: Window?) = Ask.Goods(listOf(100L), GoodsFacet.SOLD, w)

    @Test fun `buyers over a span are about that span, not wrapped as now`() {
        assertEquals(buyers(Window.TODAY), read("aaj Urea kis ne khareedi"))
        assertEquals(buyers(Window.YESTERDAY), read("who bought Urea yesterday", "en"))
        assertEquals(buyers(Window.THIS_MONTH), read("is mahine Urea kis kis ne li"))
    }

    @Test fun `buyers with no span stay every sale`() {
        assertEquals(buyers(null), read("Urea kis ne khareedi"))
    }

    @Test fun `buyers over a span the book cannot reach are told with the spans`() {
        val a = read("who bought Urea in the last 3 months", "en")
        assertTrue(a.toString(), a is Ask.SpanLimit && a.long && a.options.all { it is Ask.Goods && it.facet == GoodsFacet.BUYERS })
    }

    @Test fun `the stem bik reads as selling`() {
        assertEquals(sold(Window.YESTERDAY), read("kal Urea kitni bik gayi"))
        assertEquals(sold(Window.THIS_MONTH), read("Urea kitni bik gayi"))
    }

    @Test fun `bik said against is not a sale`() {
        assertEquals(Ask.Goods(listOf(100L), GoodsFacet.OVERVIEW), read("Urea nahi bik rahi kitni"))
    }
}
