package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.Hudhud.Ask
import com.innovation313.roshankhata.data.Hudhud.DatedOf
import com.innovation313.roshankhata.data.Hudhud.Measure
import com.innovation313.roshankhata.data.VoiceQuestion.Dated
import com.innovation313.roshankhata.data.VoiceQuestion.Window
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two questions in one sentence and two spans compared (16 Nov 2026, audit T32 / H-03, H-04), "kharab" that is not goods
 * written off, and a name told that is only offered (S-05). Through the whole pipeline with the shipped sense (lesson 145).
 */
class HudhudBothCompareTest {

    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val names = listOf("Ali Raza", "Bashir Ahmad").mapIndexed { i, n -> Hudhud.Name(i + 1L, n) }
    private val goods = listOf("Urea", "DAP").mapIndexed { i, n -> Hudhud.Name(100L + i, n) }

    private fun read(lang: String, said: String) = Hudhud.understand(said, names, HudhudRealPages.forApp(lang), lang, goods, null)

    private val monthToMonth = Ask.Compare(Ask.Period(Measure.SALES, Window.THIS_MONTH), Ask.Period(Measure.SALES, Window.LAST_MONTH))

    @Test fun `two questions joined by and are both answered`() {
        assertEquals(Ask.Both(Ask.Parties(listOf(1L)), Ask.Figure(VoiceQuestion.Kind.SALES_TODAY)),
            read("ur-Latn", "Ali Raza ka balance aur aaj ki sale batao"))
        assertEquals(Ask.Both(Ask.Person(listOf(2L), Hudhud.Facet.PHONE), Ask.Period(Measure.PROFIT, Window.TODAY)),
            read("ur-Latn", "Bashir Ahmad ka number aur aaj ka munafa"))
        assertEquals(Ask.Both(Ask.Figure(VoiceQuestion.Kind.SALES_TODAY), Ask.Collected(Window.TODAY)),
            read("ur-Latn", "aaj ki sale aur aaj kitne paise aaye"))
    }

    @Test fun `two spans with a word of comparing are compared, the later first`() {
        assertEquals(monthToMonth, read("ur-Latn", "is mahine aur pichle mahine ki sale ka farq"))
        assertEquals(monthToMonth, read("ur-Latn", "pichle mahine se is mahine ka muqabla karo"))
        assertEquals(monthToMonth, read("en", "compare this month's sales with last month"))
        assertEquals(monthToMonth, read("hi", "इस महीने और पिछले महीने की बिक्री का अंतर"))
        assertEquals(monthToMonth, read("ur", "اس مہینے اور پچھلے مہینے کی سیل کا فرق"))
        assertEquals(monthToMonth, read("in", "selisih penjualan bulan ini dan bulan lalu"))
        assertEquals(Ask.Compare(Ask.Period(Measure.PROFIT, Window.THIS_MONTH), Ask.Period(Measure.PROFIT, Window.LAST_MONTH)),
            read("ur-Latn", "is mahine aur pichle mahine ke munafe ka farq"))
        assertEquals(Ask.Compare(Ask.Period(Measure.SALES, Window.TODAY), Ask.Period(Measure.SALES, Window.YESTERDAY)),
            read("ur-Latn", "aaj aur kal ki sale ka farq"))
        // Which of two named months is the later depends on today (in October, November is last year's): either order.
        val nov = Ask.OnDays(DatedOf.SALES, Dated(month = 11))
        val oct = Ask.OnDays(DatedOf.SALES, Dated(month = 10))
        val months = read("ur-Latn", "November aur October ki sale ka farq")
        assertTrue("$months", months == Ask.Compare(nov, oct) || months == Ask.Compare(oct, nov))
    }

    // Each guard alone (lesson 129).
    @Test fun `one sentence about one thing is not split`() {
        assertEquals(Ask.Parties(listOf(1L, 2L)), read("ur-Latn", "Ali Raza aur Bashir Ahmad ka balance"))
        // The cashbook's card already holds money in and out.
        assertEquals(Ask.Cashbook(Window.TODAY), read("in", "kas masuk dan keluar hari ini"))
        // Talk is no question of the book: "how are you, and today's sale" is the sale.
        assertEquals(Ask.Figure(VoiceQuestion.Kind.SALES_TODAY), read("ur-Latn", "kya haal hai aur aaj ki sale batao"))
        // Entries are not questions: no card of two entries.
        assertFalse(read("ur-Latn", "Ali ko 500 diye aur Bashir se 300 liye") is Ask.Both)
    }

    @Test fun `no comparison with somebody named, with one span, or with no word of comparing`() {
        assertFalse(read("ur-Latn", "Ali Raza ki is mahine aur pichle mahine ki sale ka farq") is Ask.Compare)
        assertFalse(read("ur-Latn", "is mahine ki sale ka farq") is Ask.Compare)
        assertFalse(read("ur-Latn", "is mahine aur pichle mahine ki sale") is Ask.Compare)
    }

    @Test fun `kharab is goods written off only when the sentence is about goods`() {
        assertEquals(Ask.Figure(VoiceQuestion.Kind.SALES_TODAY), read("ur-Latn", "weather kharab tha to aaj ki sale kitni hui"))
        assertEquals(Ask.Figure(VoiceQuestion.Kind.SALES_TODAY), read("ur", "موسم خراب تھا آج کی سیل کتنی ہوئی"))
        assertEquals(Ask.Figure(VoiceQuestion.Kind.SALES_TODAY), read("hi", "मौसम खराब था आज की बिक्री कितनी हुई"))
        assertEquals(Ask.Collected(Window.TODAY), read("ur-Latn", "mausam kharab tha, aaj kitne paise aaye"))
        val off = Hudhud.CounterFacet.WRITTEN_OFF
        assertTrue(read("ur-Latn", "kitna maal kharab hua").let { it is Ask.Counter && it.facet == off })
        assertTrue(read("ur-Latn", "aaj ki sale mein kitna maal kharab hua").let { it is Ask.Counter && it.facet == off })
    }

    @Test fun `a name told is only offered, never kept by the reading`() {
        assertEquals(Ask.CallMe("Imran", save = false), read("ur-Latn", "mera naam Imran hai"))
    }
}
