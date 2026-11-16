package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.Hudhud.Ask
import com.innovation313.roshankhata.data.Hudhud.DatedOf
import com.innovation313.roshankhata.data.VoiceQuestion.Dated
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * A day or a month named outright (16 Nov 2026, audit T31 / H-02, H-09): "15 tareekh ki sale", "12 November ki sale",
 * "November ki sale", "parson ki sale" were this month's figure or a choice of spans. Through the whole pipeline with the
 * shipped sense (lesson 145); the days themselves by [Hudhud.span] against a fixed clock. Invented names only.
 */
class HudhudDatesTest {

    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val names = listOf("Ali Raza", "Bashir Ahmad", "June Traders").mapIndexed { i, n -> Hudhud.Name(i + 1L, n) }
    private val goods = listOf("Urea", "DAP").mapIndexed { i, n -> Hudhud.Name(100L + i, n) }

    private fun read(lang: String, said: String) = Hudhud.understand(said, names, HudhudRealPages.forApp(lang), lang, goods, null)

    private fun sales(d: Dated) = Ask.OnDays(DatedOf.SALES, d)

    @Test fun `a day or a month named outright is the sale of that day or month`() {
        assertEquals(sales(Dated(day = 15)), read("ur-Latn", "15 tareekh ki sale"))
        assertEquals(sales(Dated(day = 15)), read("ur-Latn", "15 date ki sale"))
        assertEquals(sales(Dated(day = 12, month = 11)), read("ur-Latn", "12 November ki sale"))
        assertEquals(sales(Dated(month = 11)), read("ur-Latn", "November ki sale"))
        assertEquals(sales(Dated(daysBack = 2)), read("ur-Latn", "parson ki sale"))
        assertEquals(sales(Dated(day = 15, monthsBack = 1)), read("ur-Latn", "pichle mahine ki 15 tareekh ki sale"))
        assertEquals(sales(Dated(weekday = Calendar.FRIDAY)), read("ur-Latn", "pichle jumme ki sale"))
    }

    @Test fun `the nine languages name their days and months`() {
        for ((lang, said, d) in listOf(
            Triple("ur", "15 تاریخ کی سیل", Dated(day = 15)), Triple("ur", "12 نومبر کی سیل", Dated(day = 12, month = 11)),
            Triple("ur", "نومبر کی سیل", Dated(month = 11)), Triple("ur", "پرسوں کی سیل", Dated(daysBack = 2)),
            Triple("hi", "15 तारीख की बिक्री", Dated(day = 15)), Triple("hi", "12 नवंबर की बिक्री", Dated(day = 12, month = 11)),
            Triple("hi", "परसों की बिक्री", Dated(daysBack = 2)), Triple("en", "sales on 12 November", Dated(day = 12, month = 11)),
            Triple("en", "November sales", Dated(month = 11)), Triple("en", "sales on the 15th", Dated(day = 15)),
            Triple("en", "sales the day before yesterday", Dated(daysBack = 2)), Triple("in", "penjualan 12 November", Dated(day = 12, month = 11)),
            Triple("in", "penjualan bulan November", Dated(month = 11)), Triple("bn", "১২ নভেম্বরের বিক্রি", Dated(day = 12, month = 11)),
            Triple("ar", "مبيعات 12 نوفمبر", Dated(day = 12, month = 11)), Triple("fa", "فروش ۱۲ نوامبر", Dated(day = 12, month = 11)),
            Triple("sd", "12 نومبر جو وڪرو", Dated(day = 12, month = 11))
        )) assertEquals("[$lang] $said", sales(d), read(lang, said))
    }

    @Test fun `the profit, money in, the day's dealings and a customer's payment are asked over the day too`() {
        assertEquals(Ask.OnDays(DatedOf.PROFIT, Dated(daysBack = 2)), read("ur-Latn", "parson kitna munafa hua"))
        assertEquals(Ask.OnDays(DatedOf.PROFIT, Dated(day = 3, month = 11)), read("en", "profit on 3 November"))
        assertEquals(Ask.OnDays(DatedOf.COLLECTED, Dated(day = 12, month = 11)), read("ur-Latn", "12 November ko kitne paise aaye"))
        assertEquals(Ask.OnDays(DatedOf.PAID, Dated(daysBack = 2), listOf(1L)), read("ur-Latn", "Ali Raza ne parson kitna diya"))
        // What happened that day, not today's date.
        assertEquals(Ask.OnDays(DatedOf.ACTIVITY, Dated(day = 15)), read("ur-Latn", "15 tareekh ko kya hua"))
    }

    @Test fun `a date's figures are never the amount of an entry`() {
        val e = read("ur-Latn", "Ali Raza ko 12 November ko 500 diye")
        assertTrue("$e", e is Ask.Entry && e.amount == 500.0 && e.isGiven)
        assertEquals("Ali Raza ko November ko 500 diye", VoiceQuestion.withoutDate("Ali Raza ko 12 November ko 500 diye"))
    }

    @Test fun `the talk goes on from a day named, two questions and a comparison`() {
        val topics = HudhudRealPages.forApp("ur-Latn")
        fun after(first: String, then: String) =
            Hudhud.understand(then, names, topics, "ur-Latn", goods, Hudhud.understand(first, names, topics, "ur-Latn", goods, null))
        assertEquals(Ask.Period(Hudhud.Measure.SALES, VoiceQuestion.Window.YESTERDAY), after("15 tareekh ki sale", "aur kal ki?"))
        assertEquals(Ask.Period(Hudhud.Measure.SALES, VoiceQuestion.Window.YESTERDAY), after("Ali Raza ka balance aur aaj ki sale batao", "aur kal ki?"))
    }

    // Each guard alone (lesson 129).
    @Test fun `may is Roman Urdu's mein unless a day, a year or a word of of stands beside it`() {
        assertEquals(Ask.Figure(VoiceQuestion.Kind.SALES_TODAY), read("ur-Latn", "aaj may kitni sale hui"))
        assertEquals(sales(Dated(month = 5)), read("ur-Latn", "may ki sale"))
        assertEquals(sales(Dated(day = 3, month = 5)), read("ur-Latn", "3 may ki sale"))
        assertNull(VoiceQuestion.datedOf("sale may kitni hui"))
    }

    @Test fun `parson with the future of a verb is no day of the book`() {
        assertNull(VoiceQuestion.datedOf("parson kitne paise aayenge"))
        assertNull(VoiceQuestion.datedOf("parson Ali dega"))
        assertTrue(read("ur-Latn", "parson kitne paise aayenge") !is Ask.OnDays)
    }

    @Test fun `a month in a name is the name`() {
        assertEquals(Ask.Parties(listOf(3L)), read("ur-Latn", "June Traders ka hisaab"))
        assertNull(VoiceQuestion.datedOf("June Traders ka hisaab") { it == "june" })
    }

    @Test fun `a date beside a span it cannot stand with is not read`() {
        assertNull(VoiceQuestion.datedOf("aaj 15 tareekh hai"))
        assertNull(VoiceQuestion.datedOf("is hafte 12 November ki sale"))
        assertTrue(read("ur-Latn", "aaj 15 tareekh hai") !is Ask.OnDays)
    }

    @Test fun `a weekday is the last one only after a last word, and since a weekday is a span to now`() {
        assertNull(VoiceQuestion.datedOf("jumme ki sale"))
        assertNull(VoiceQuestion.datedOf("is jumme ki sale"))
        assertNull(VoiceQuestion.datedOf("pichle jumme se ab tak ki sale"))
        assertTrue(read("ur-Latn", "pichle jumme se ab tak ki sale") !is Ask.OnDays)
    }

    @Test fun `a lone number with no date word is no day`() {
        assertNull(VoiceQuestion.datedOf("15 ki sale"))
        assertNull(VoiceQuestion.datedOf("15th customer"))
        assertNull(VoiceQuestion.datedOf("45 tareekh ki sale"))
    }

    @Test fun `a question that cannot be asked over a day says the spans he gives, never another span's figure`() {
        assertTrue(read("ur-Latn", "12 November ko kitne bill bane") is Ask.SpanLimit)
        assertTrue(read("ur-Latn", "Urea 12 November ko kitni biki") is Ask.SpanLimit)
        // How a customer stands is said as it stands now, the card says so.
        assertEquals(Ask.Now(Ask.Parties(listOf(1L))), read("ur-Latn", "Ali Raza ka 12 November ka hisaab"))
    }

    // ---- the days themselves, against Monday 16 November 2026 at noon in Karachi

    private val tz: TimeZone = TimeZone.getTimeZone("Asia/Karachi")
    private val now: Long = at(2026, 11, 16) + 12 * 3_600_000L

    private fun at(y: Int, m: Int, d: Int): Long =
        Calendar.getInstance(tz).apply { clear(); set(y, m - 1, d) }.timeInMillis

    private fun day(y: Int, m: Int, d: Int) = at(y, m, d) to Calendar.getInstance(tz).apply { timeInMillis = at(y, m, d); add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis

    private fun span(d: Dated) = Hudhud.span(d, now, tz)

    @Test fun `a day said alone is the most recent such day that has come`() {
        assertEquals(day(2026, 11, 15), span(Dated(day = 15)))
        assertEquals(day(2026, 11, 16), span(Dated(day = 16)))
        assertEquals(day(2026, 10, 20), span(Dated(day = 20)))
        assertEquals(day(2026, 10, 31), span(Dated(day = 31)))
        assertEquals(day(2026, 10, 15), span(Dated(day = 15, monthsBack = 1)))
        assertNull(span(Dated(day = 20, monthsBack = 0)))
    }

    @Test fun `a month is the whole month, the most recent one that has begun`() {
        assertEquals(at(2026, 11, 1) to at(2026, 12, 1), span(Dated(month = 11)))
        assertEquals(at(2025, 12, 1) to at(2026, 1, 1), span(Dated(month = 12)))
        assertEquals(at(2025, 11, 1) to at(2025, 12, 1), span(Dated(month = 11, yearsBack = 1)))
        assertNull(span(Dated(month = 12, year = 2026)))
        assertNull(span(Dated(month = 12, yearsBack = 0)))
    }

    @Test fun `a day of a month is that day, never one ahead`() {
        assertEquals(day(2026, 11, 12), span(Dated(day = 12, month = 11)))
        assertEquals(day(2025, 11, 20), span(Dated(day = 20, month = 11)))
        assertNull(span(Dated(day = 20, month = 11, year = 2026)))
        assertEquals(day(2025, 11, 20), span(Dated(day = 20, month = 11, year = 2025)))
        assertEquals(day(2024, 2, 29), span(Dated(day = 29, month = 2)))
    }

    @Test fun `the day before yesterday and the last friday`() {
        assertEquals(day(2026, 11, 14), span(Dated(daysBack = 2)))
        assertEquals(day(2026, 11, 13), span(Dated(weekday = Calendar.FRIDAY)))
        // On a Monday the last Monday is a week ago, never today.
        assertEquals(day(2026, 11, 9), span(Dated(weekday = Calendar.MONDAY)))
    }
}
