package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.Hudhud.Ask
import com.innovation313.roshankhata.data.VoiceQuestion.Dated
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * An entry said with its day, a day written with figures (16 Nov 2026), through the whole pipeline
 * with the shipped sense (lesson 145) and through the Khata screen's own reader ([VoiceEntry.parse]). Invented names only.
 */
class HudhudEntryDayTest {

    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val names = listOf("Ali Raza", "Bashir Ahmad", "Tariq Butt", "June Traders").mapIndexed { i, n -> Hudhud.Name(i + 1L, n) }
    private val goods = listOf("Urea", "DAP").mapIndexed { i, n -> Hudhud.Name(100L + i, n) }

    private fun read(lang: String, said: String) = Hudhud.understand(said, names, HudhudRealPages.forApp(lang), lang, goods, null)
    private fun entry(lang: String, said: String) = read(lang, said) as Ask.Entry

    // ------------------------------------------------------------------------------------------------ the day of an entry

    @Test fun `an entry said with a day keeps that day`() {
        assertEquals(Dated(daysBack = 1), entry("ur-Latn", "Ali ko kal 500 diye").day)
        assertEquals(Dated(daysBack = 2), entry("ur-Latn", "parson Bashir se 300 liye").day)
        assertEquals(Dated(daysBack = 1), entry("hi", "अली को कल 500 दिए").day)
        assertEquals(Dated(day = 12, month = 11), entry("ur-Latn", "Ali ko 12 November ko 500 diye").day)
        assertEquals(Dated(weekday = Calendar.FRIDAY), entry("ur-Latn", "Ali ko pichle jumme 700 diye").day)
        // Its amount and its customer are as before: the day's words are no amount and no part of a name.
        val e = entry("ur-Latn", "Ali ko kal 500 diye")
        assertEquals(500.0, e.amount, 0.0)
        assertEquals(listOf(1L), e.ids)
        assertFalse("kal" in e.nameWords)
        assertFalse("jumme" in entry("ur-Latn", "Ali ko pichle jumme 700 diye").nameWords)
    }

    @Test fun `today, a month alone and a promise name no day`() {
        assertNull(entry("ur-Latn", "Ali ko 500 diye").day)
        assertNull(entry("ur-Latn", "Ali ko aaj 500 diye").day)
        assertNull(VoiceQuestion.entryDay("Ali kal 500 dega"))
        assertNull(VoiceQuestion.entryDay("Ali ko November mein 500 diye"))
        // A month in a customer's name is never a date ("June Traders").
        assertNull(VoiceQuestion.entryDay("June Traders ko 500 diye") { it == "june" || it == "traders" })
    }

    @Test fun `a promise is no entry (BC-113)`() {
        // "Ali kal 500 dega" was an entry of 500 received today: money that has not moved.
        for ((lang, said) in listOf("ur-Latn" to "Ali kal 500 dega", "ur-Latn" to "Bashir parson 300 denge",
            "en" to "Ali will pay 500 tomorrow", "hi" to "अली कल 500 देगा")) {
            val r = read(lang, said)
            assertFalse("[$lang] $said -> $r", r is Ask.Entry || r is Ask.Entries || (r is Ask.Guessed && r.ask is Ask.Entry))
        }
        assertTrue(VoiceQuestion.saysFuture("Ali kal 500 dega"))
        assertFalse(VoiceQuestion.saysFuture("Ali ko kal 500 diye"))
        // Money that moved stays an entry.
        assertTrue(read("ur-Latn", "Ali ne 500 diye") is Ask.Entry)
    }

    // ------------------------------------------------------------------------------------------------ figures and a slash

    @Test fun `a day written with a slash or a dash is a day, never the amount`() {
        for (said in listOf("12/11 ko Ali ko 500 diye", "12-11 ko Ali ko 500 diye", "Ali ko 12/11/2026 ko 500 diye", "Ali ko 12/11/26 ko 500 diye")) {
            val e = entry("ur-Latn", said)
            assertEquals(said, 500.0, e.amount, 0.0)
            assertEquals(said, 12, e.day?.day)
            assertEquals(said, 11, e.day?.month)
        }
        assertEquals(2026, entry("ur-Latn", "Ali ko 12/11/26 ko 500 diye").day?.year)
        // The questions read it the same way.
        assertEquals(Ask.OnDays(Hudhud.DatedOf.SALES, Dated(day = 12, month = 11, year = 2026)), read("ur-Latn", "12/11/2026 ki sale"))
        assertEquals(Ask.OnDays(Hudhud.DatedOf.SALES, Dated(day = 25, month = 10)), read("en", "sales on 25/10"))
    }

    @Test fun `day first, and month first only when the second figure can be no month`() {
        assertEquals(Dated(day = 11, month = 12), VoiceQuestion.datedOf("sale on 11/12"))
        assertEquals(Dated(day = 25, month = 11), VoiceQuestion.datedOf("sale on 11/25"))
        assertNull(VoiceQuestion.datedOf("sale on 13/13"))
        assertNull(VoiceQuestion.datedOf("sale on 32/1"))
        // No date: a decimal, a phone number, a sum with spaces, a year out of reach.
        assertNull(VoiceQuestion.datedOf("Ali ko 2.5 hazar diye"))
        assertNull(VoiceQuestion.datedOf("0300-1234567 Ali ka number"))
        assertNull(VoiceQuestion.datedOf("12 / 4 kitne hote hain"))
        assertNull(VoiceQuestion.datedOf("12/11/1999 ki sale"))
        assertEquals(2500.0, entry("ur-Latn", "Ali ko 2.5 hazar diye").amount, 0.0)
    }

    @Test fun `the Khata screen's own reader drops the date's figures and keeps the day`() {
        val book = names.map { it.name }
        for ((said, day) in listOf(
            "12/11 ko Ali ko 500 diye" to Dated(day = 12, month = 11),
            "Ali ko 12 November ko 500 diye" to Dated(day = 12, month = 11),
            "Ali ko kal 500 diye" to Dated(daysBack = 1)
        )) {
            val p = VoiceEntry.parse(said, book)
            assertEquals(said, 500.0, p.amount!!, 0.0)
            assertEquals(said, true, p.isGiven)
            assertEquals(said, day, p.day)
        }
        // "June Traders" is a customer, not a month.
        assertNull(VoiceEntry.parse("June Traders ko 500 diye", listOf("June Traders")).day)
        assertEquals(500.0, VoiceEntry.parse("June Traders ko 500 diye", listOf("June Traders")).amount!!, 0.0)
    }

    // ------------------------------------------------------------------------------------------------ the moment written

    private val tz = TimeZone.getTimeZone("Asia/Karachi")
    private fun at(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0): Long =
        Calendar.getInstance(tz).apply { clear(); set(y, m - 1, d, h, min) }.timeInMillis

    @Test fun `the moment is that day at the time it is now, never in the future`() {
        val now = at(2026, 11, 16, 15, 30)
        assertEquals(at(2026, 11, 15, 15, 30), Hudhud.entryTime(Dated(daysBack = 1), now, tz))
        assertEquals(at(2026, 11, 12, 15, 30), Hudhud.entryTime(Dated(day = 12, month = 11), now, tz))
        // Today's own date is now.
        assertEquals(now, Hudhud.entryTime(Dated(day = 16, month = 11, year = 2026), now, tz))
        // A day not yet come: no moment (the form opens on now).
        assertNull(Hudhud.entryTime(Dated(day = 20, month = 11, year = 2026), now, tz))
        // Never past the day's own end.
        val t = Hudhud.entryTime(Dated(daysBack = 1), now, tz)!!
        assertTrue(t < at(2026, 11, 16))
    }
}
