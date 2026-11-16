package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.Hudhud.Ask
import com.innovation313.roshankhata.data.Hudhud.GoodsFacet
import com.innovation313.roshankhata.data.Hudhud.Measure
import com.innovation313.roshankhata.data.Hudhud.Thing
import com.innovation313.roshankhata.data.VoiceQuestion.Kind
import com.innovation313.roshankhata.data.VoiceQuestion.Window
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * The spans of days Hudhud can give (15 Nov 2026): the last six months, this year, last year and the whole book, in the nine
 * languages — and, as important, the spans he cannot give. The class of fault this holds: a span he has no window for ("last 3
 * months", "10 days ago", the fiscal year, "next month") was silently read as one he has (this month), so the owner was told a
 * wrong figure for a month he did not ask about. Now it is kept as the question it is and he says which spans he can give. The
 * sentences are written by the maker of the tests, not by a native reader of each language (said plainly in the notes). Names
 * are made up (lesson 80).
 */
class HudhudSpansTest {

    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val names = listOf(Hudhud.Name(1, "Ali Raza"), Hudhud.Name(2, "Bashir Ahmad"))
    private val goods = listOf(Hudhud.Name(100, "Urea"))
    private val topics = listOf(
        Hudhud.Topic("screen_cashflow", "Cash flow کیش فلو", "What is coming in and going out in the next weeks"),
        Hudhud.Topic("intro_documents", "Documents invoices دستاویز", "Invoices, quotations and challans")
    )

    private fun read(s: String, language: String? = null, before: Ask? = null) =
        Hudhud.understand(s, names, topics, language, goods = goods, before = before)

    private class Said(
        val lang: String?, val sixMonths: String, val thisYear: String, val lastYear: String, val sinceStart: String,
        val threeMonths: String
    )

    private val nine = listOf(
        Said("en", "sales last 6 months", "sales this year", "sales last year", "total sales since the beginning", "sales last 3 months"),
        Said(null, "pichle 6 mahine ki bikri", "is saal ki bikri", "pichle saal ki bikri", "shuru se ab tak ki bikri", "pichle 3 mahine ki bikri"),
        Said("ur", "پچھلے 6 مہینے کی بکری", "اس سال کی بکری", "پچھلے سال کی بکری", "شروع سے اب تک کی بکری", "پچھلے 3 مہینے کی بکری"),
        Said("hi", "पिछले 6 महीने की बिक्री", "इस साल की बिक्री", "पिछले साल की बिक्री", "शुरू से अब तक की बिक्री", "पिछले 3 महीने की बिक्री"),
        Said("bn", "গত ৬ মাসের বিক্রি", "এই বছরের বিক্রি", "গত বছরের বিক্রি", "শুরু থেকে এখন পর্যন্ত বিক্রি", "গত ৩ মাসের বিক্রি"),
        Said("fa", "فروش ۶ ماه گذشته", "فروش امسال", "فروش سال گذشته", "فروش از ابتدا تا الان", "فروش ۳ ماه گذشته"),
        Said("ar", "مبيعات آخر 6 أشهر", "مبيعات هذه السنة", "مبيعات السنة الماضية", "مبيعات منذ البداية", "مبيعات آخر 3 أشهر"),
        Said("sd", "گذريل 6 مهينن جي وڪرو", "هن سال جو وڪرو", "گذريل سال جو وڪرو", "شروعات کان وٺي وڪرو", "گذريل 3 مهينن جو وڪرو"),
        Said("in", "penjualan 6 bulan terakhir", "penjualan tahun ini", "penjualan tahun lalu", "penjualan sejak awal", "penjualan 3 bulan terakhir")
    )

    // ------------------------------------------------------------------------------------------ the words

    @Test fun `the four longer spans are read in nine languages`() {
        for (s in nine) {
            assertEquals(s.sixMonths, Window.LAST_6_MONTHS, VoiceQuestion.windowOf(s.sixMonths))
            assertEquals(s.thisYear, Window.THIS_YEAR, VoiceQuestion.windowOf(s.thisYear))
            assertEquals(s.lastYear, Window.LAST_YEAR, VoiceQuestion.windowOf(s.lastYear))
            assertEquals(s.sinceStart, Window.ALL_TIME, VoiceQuestion.windowOf(s.sinceStart))
            for (q in listOf(s.sixMonths, s.thisYear, s.lastYear, s.sinceStart)) assertFalse(q, VoiceQuestion.unsupportedSpan(q))
        }
    }

    @Test fun `three months is no span of his, in nine languages - it is never read as another`() {
        for (s in nine) {
            assertTrue(s.threeMonths, VoiceQuestion.unsupportedSpan(s.threeMonths))
            assertNull(s.threeMonths, VoiceQuestion.windowOf(s.threeMonths))
        }
    }

    @Test fun `a count of days, weeks or months he has no span for is unsupported, never a span`() {
        for (q in listOf(
            "sales last 3 days", "pichle 3 din ki bikri", "pichle 2 hafte ki bikri", "pichle ek hafte ki bikri", "1 month sales",
            "ek mahine ki bikri", "do mahine ka munafa", "2 saal ki bikri", "profit for the first 3 months", "sales 3 months ago",
            "sales 6 months ago", "sales for the first 6 months", "twenty six months sales",
            // a six next to another span is not "the last six months"
            "is saal ke pichle 6 mahine ki bikri", "aaj ki bikri pichle 6 mahine mein", "kal ki bikri pichle 6 mahine mein",
            "is hafte ki bikri pichle 6 mahine mein", "sales for the last 6 months of the year", "maali saal ke pichle 6 mahine",
            // six months ahead is not six months back
            "agle 6 mahine ki bikri", "sales next 6 months",
            // the fiscal year, the day before yesterday, and ahead
            "sales in the fiscal year", "maali saal ki bikri", "parson ki bikri", "agle mahine ki bikri", "next month's sales",
            "sales next week"
        )) {
            assertTrue(q, VoiceQuestion.unsupportedSpan(q))
            assertNull(q, VoiceQuestion.windowOf(q))
        }
    }

    @Test fun `the spans he can give are still read, and a sentence with no span has none`() {
        assertEquals(Window.TODAY, VoiceQuestion.windowOf("aaj ki bikri"))
        assertEquals(Window.YESTERDAY, VoiceQuestion.windowOf("kal ki bikri"))
        assertEquals(Window.LAST_WEEK, VoiceQuestion.windowOf("pichle hafte ki bikri"))
        assertEquals(Window.THIS_MONTH, VoiceQuestion.windowOf("is mahine ki bikri"))
        assertEquals(Window.LAST_MONTH, VoiceQuestion.windowOf("pichle mahine ka munafa"))
        // "aaj ab tak" is today so far; "aaj tak" is the book so far
        assertEquals(Window.TODAY, VoiceQuestion.windowOf("aaj ab tak ki bikri"))
        for (q in listOf("Ali ka balance", "how much do I have to get", "Bashir ki aakhri adaigi")) {
            assertNull(q, VoiceQuestion.windowOf(q))
            assertFalse(q, VoiceQuestion.unsupportedSpan(q))
        }
        for (q in listOf("pichle 6 mahine ki bikri", "is saal ki bikri", "pichle saal ki bikri", "shuru se ab tak ki bikri"))
            assertFalse(q, VoiceQuestion.unsupportedSpan(q))
    }

    @Test fun `the count in a span is not an amount`() {
        assertEquals(setOf(6.0), VoiceQuestion.spanFigures("pichle 6 mahine ki bikri"))
        assertEquals(setOf(2.0), VoiceQuestion.spanFigures("do mahine ka munafa"))
        assertEquals(setOf(6.0), VoiceQuestion.spanFigures("Bashir ne pichle 6 mahine mein 500 diye"))
        assertEquals(emptySet<Double>(), VoiceQuestion.spanFigures("Bashir ne 500 diye"))
        assertEquals(emptySet<Double>(), VoiceQuestion.spanFigures("is saal ki bikri"))
        // a count written in several words is no figure at all, and is never read as its last word
        assertEquals(emptySet<Double>(), VoiceQuestion.spanFigures("twenty six months sales"))
    }

    @Test fun `last as a span is not last as the latest`() {
        for (q in listOf("how much did Bashir pay last month", "Bashir ne pichle mahine kitna diya", "Bashir ne pichle 3 mahine kitna diya"))
            assertTrue(q, VoiceQuestion.lastIsSpan(q))
        for (q in listOf("Bashir ki last payment", "Bashir ki aakhri adaigi", "when was Bashir's last entry"))
            assertFalse(q, VoiceQuestion.lastIsSpan(q))
    }

    // ------------------------------------------------------------------------------------------ the days

    private fun day(tz: TimeZone, y: Int, m: Int, d: Int, h: Int = 0): Long =
        Calendar.getInstance(tz).apply { clear(); set(y, m - 1, d, h, 0, 0) }.timeInMillis

    @Test fun `the four longer spans start and end on whole days`() {
        val utc = TimeZone.getTimeZone("UTC")
        val now = day(utc, 2026, 11, 15, 12)
        assertEquals(day(utc, 2026, 5, 16) to day(utc, 2026, 11, 16), Hudhud.span(Window.LAST_6_MONTHS, now, utc))
        assertEquals(day(utc, 2026, 1, 1) to day(utc, 2027, 1, 1), Hudhud.span(Window.THIS_YEAR, now, utc))
        assertEquals(day(utc, 2025, 1, 1) to day(utc, 2026, 1, 1), Hudhud.span(Window.LAST_YEAR, now, utc))
        assertEquals(0L to day(utc, 2026, 11, 16), Hudhud.span(Window.ALL_TIME, now, utc))
    }

    @Test fun `six months back never counts a day twice or loses one, at a month's end`() {
        val utc = TimeZone.getTimeZone("UTC")
        // 31 August: six months back is 28 February (29 in a leap year); the first day shown is the 1st of March
        assertEquals(day(utc, 2026, 3, 1) to day(utc, 2026, 9, 1), Hudhud.span(Window.LAST_6_MONTHS, day(utc, 2026, 8, 31, 23), utc))
        assertEquals(day(utc, 2028, 3, 1) to day(utc, 2028, 9, 1), Hudhud.span(Window.LAST_6_MONTHS, day(utc, 2028, 8, 31, 9), utc))
        // 31 March: six months back is 30 September
        assertEquals(day(utc, 2025, 10, 1) to day(utc, 2026, 4, 1), Hudhud.span(Window.LAST_6_MONTHS, day(utc, 2026, 3, 31, 12), utc))
        // the first second of a year: this year has just begun, the six months run into the old one
        val newYear = day(utc, 2026, 1, 1)
        assertEquals(day(utc, 2026, 1, 1) to day(utc, 2027, 1, 1), Hudhud.span(Window.THIS_YEAR, newYear, utc))
        assertEquals(day(utc, 2025, 1, 1) to day(utc, 2026, 1, 1), Hudhud.span(Window.LAST_YEAR, newYear, utc))
        assertEquals(day(utc, 2025, 7, 2) to day(utc, 2026, 1, 2), Hudhud.span(Window.LAST_6_MONTHS, newYear, utc))
    }

    @Test fun `a year begins at the shop's own midnight, in a country that moves its clocks`() {
        for (id in listOf("Europe/London", "America/New_York", "Asia/Karachi", "Africa/Cairo")) {
            val tz = TimeZone.getTimeZone(id)
            val now = day(tz, 2026, 7, 10, 12)
            assertEquals(id, day(tz, 2026, 1, 1) to day(tz, 2027, 1, 1), Hudhud.span(Window.THIS_YEAR, now, tz))
            assertEquals(id, day(tz, 2025, 1, 1) to day(tz, 2026, 1, 1), Hudhud.span(Window.LAST_YEAR, now, tz))
            assertEquals(id, day(tz, 2026, 1, 11) to day(tz, 2026, 7, 11), Hudhud.span(Window.LAST_6_MONTHS, now, tz))
            assertEquals(id, 0L to day(tz, 2026, 7, 11), Hudhud.span(Window.ALL_TIME, now, tz))
        }
    }

    @Test fun `every span is a proper stretch and the years meet`() {
        val tz = TimeZone.getTimeZone("Asia/Karachi")
        val now = day(tz, 2026, 11, 15, 12)
        for (w in Window.values()) {
            val (from, to) = Hudhud.span(w, now, tz)
            assertTrue("$w", from < to)
            assertTrue("$w holds today unless it is a past span", w in setOf(Window.YESTERDAY, Window.LAST_WEEK, Window.LAST_MONTH, Window.LAST_YEAR) || now in from until to)
        }
        assertEquals(Hudhud.span(Window.LAST_YEAR, now, tz).second, Hudhud.span(Window.THIS_YEAR, now, tz).first)
    }

    // ------------------------------------------------------------------------------------------ the answers

    private val salesLimit = Ask.SpanLimit(
        listOf(Ask.Figure(Kind.SALES_MONTH)) + listOf(Window.LAST_MONTH, Window.LAST_6_MONTHS, Window.THIS_YEAR, Window.LAST_YEAR, Window.ALL_TIME)
            .map { Ask.Period(Measure.SALES, it) },
        long = true
    )
    private val profitLimit = Ask.SpanLimit(
        listOf(Ask.Figure(Kind.PROFIT_MONTH)) + listOf(Window.LAST_MONTH, Window.LAST_6_MONTHS, Window.THIS_YEAR, Window.LAST_YEAR, Window.ALL_TIME)
            .map { Ask.Period(Measure.PROFIT, it) },
        long = true
    )
    private val shortWindows = listOf(Window.TODAY, Window.THIS_WEEK, Window.THIS_MONTH, Window.LAST_MONTH)
    private val longWindows = listOf(
        Window.THIS_MONTH, Window.LAST_MONTH, Window.LAST_6_MONTHS, Window.THIS_YEAR, Window.LAST_YEAR, Window.ALL_TIME
    )

    @Test fun `sales over a longer span are the sum for that span, in nine languages`() {
        for (s in nine) {
            assertEquals(s.sixMonths, Ask.Period(Measure.SALES, Window.LAST_6_MONTHS), read(s.sixMonths, s.lang))
            assertEquals(s.thisYear, Ask.Period(Measure.SALES, Window.THIS_YEAR), read(s.thisYear, s.lang))
            assertEquals(s.lastYear, Ask.Period(Measure.SALES, Window.LAST_YEAR), read(s.lastYear, s.lang))
            assertEquals(s.sinceStart, Ask.Period(Measure.SALES, Window.ALL_TIME), read(s.sinceStart, s.lang))
        }
    }

    @Test fun `three months of sales is never answered with another span's figure, in nine languages`() {
        for (s in nine) assertEquals(s.threeMonths, salesLimit, read(s.threeMonths, s.lang))
    }

    @Test fun `profit over a longer span is the profit of that span`() {
        assertEquals(Ask.Period(Measure.PROFIT, Window.THIS_YEAR), read("profit this year"))
        assertEquals(Ask.Period(Measure.PROFIT, Window.LAST_YEAR), read("profit last year"))
        assertEquals(Ask.Period(Measure.PROFIT, Window.LAST_6_MONTHS), read("profit last 6 months"))
        assertEquals(profitLimit, read("do mahine ka munafa"))
        assertEquals(profitLimit, read("profit for the first 3 months"))
    }

    @Test fun `a span he cannot count is told with the spans he can, and the figure for another is never said`() {
        for (q in listOf("ek mahine ki bikri", "1 month sales", "pichle ek hafte ki bikri", "2 saal ki bikri", "sales 3 months ago",
            "sales in the fiscal year", "maali saal ki bikri", "is saal ke pichle 6 mahine ki bikri"))
            assertEquals(q, salesLimit, read(q))
        // "Parson ki bikri" is the day before yesterday's sale since 16 Nov 2026 (audit T31, HudhudDatesTest).
    }

    @Test fun `a span ahead is no span of the book`() {
        for (q in listOf("agle mahine ki bikri", "next month's sales", "sales next week", "sales next month")) {
            val a = read(q)
            assertFalse("$q -> $a", a is Ask.Figure || a is Ask.Period)
        }
    }

    @Test fun `what a customer paid over a longer span is his sum, the way round the sentence says`() {
        assertEquals(Ask.Moved(listOf(2L), Window.THIS_YEAR, got = true), read("Bashir ne is saal kitna diya"))
        assertEquals(Ask.Moved(listOf(2L), Window.LAST_6_MONTHS, got = true), read("Bashir ne pichle 6 mahine mein kitna diya"))
        assertEquals(Ask.Moved(listOf(2L), Window.ALL_TIME, got = false), read("Bashir ne aaj tak kitna liya"))
        assertEquals(Ask.Moved(listOf(2L), Window.THIS_YEAR, got = true), read("how much did Bashir pay this year"))
        assertEquals(Ask.Moved(listOf(2L), Window.LAST_MONTH, got = true), read("how much did Bashir pay last month"))
        assertEquals(Ask.SpanLimit(longWindows.map { Ask.Moved(listOf(2L), it, got = true) }, long = true),
            read("Bashir ne pichle 3 mahine mein kitna diya"))
    }

    @Test fun `the book so far is the whole book`() {
        assertEquals(Ask.Period(Measure.SALES, Window.ALL_TIME), read("total sales so far"))
        assertEquals(Ask.Period(Measure.SALES, Window.ALL_TIME), read("ab tak ki bikri"))
        assertEquals(Ask.Moved(listOf(2L), Window.ALL_TIME, got = true), read("how much has Bashir paid so far"))
        assertEquals(Window.ALL_TIME, VoiceQuestion.windowOf("till date sales"))
        // but today so far is today
        assertEquals(Window.TODAY, VoiceQuestion.windowOf("sales today so far"))
    }

    @Test fun `a product sold over a count of months he has no span for is told with the spans`() {
        assertEquals(Ask.SpanLimit(longWindows.map { Ask.Goods(listOf(100L), GoodsFacet.SOLD, it) }, long = true),
            read("Urea kitna bika pichle 3 mahine"))
    }

    @Test fun `a product sold over a longer span, and the best sellers`() {
        assertEquals(Ask.Goods(listOf(100L), GoodsFacet.SOLD, Window.LAST_6_MONTHS), read("Urea kitna bika pichle 6 mahine"))
        assertEquals(Ask.Goods(listOf(100L), GoodsFacet.SOLD, Window.THIS_YEAR), read("Urea ki bikri is saal"))
        assertEquals(Ask.TopGoods(Window.LAST_6_MONTHS), read("sab se zyada kya bika pichle 6 mahine"))
        assertEquals(Ask.TopGoods(Window.THIS_YEAR), read("what sold most this year"))
        assertEquals(Ask.SpanLimit(longWindows.map { Ask.TopGoods(it) }, long = true), read("sab se zyada kya bika pichle 3 mahine"))
    }

    @Test fun `rows are never listed for a longer span, only a day, a week or a month at a time`() {
        assertEquals(Ask.SpanLimit(shortWindows.map { Ask.Cashbook(it) }, long = false), read("cashbook this year"))
        assertEquals(Ask.SpanLimit(shortWindows.map { Ask.Cashbook(it) }, long = false), read("money in this year"))
        assertEquals(Ask.SpanLimit(shortWindows.map { Ask.Count(Thing.INVOICES, it) }, long = false), read("how many invoices this year"))
        assertEquals(Ask.SpanLimit(shortWindows.map { Ask.Count(Thing.BILLS, it) }, long = false), read("kitne bills aaye is saal"))
    }

    /** True for an ask that lists rows, or reads what the phone keeps for forty days, over a span longer than a month. */
    private fun rowsOverLongSpan(a: Ask): Boolean {
        val inner = if (a is Ask.Guessed) a.ask else a
        val w = when (inner) {
            is Ask.Collected -> inner.window
            is Ask.Cashbook -> inner.window
            is Ask.Returns -> inner.window
            is Ask.Deliveries -> inner.window
            is Ask.Activity -> inner.window
            is Ask.Count -> if (inner.thing in setOf(Thing.ENTRIES, Thing.INVOICES, Thing.BILLS, Thing.TICKETS, Thing.CALCULATIONS, Thing.APP_OPENS)) inner.window else null
            else -> null
        }
        return w?.isLong == true
    }

    @Test fun `no way of asking reaches a row-listing reader with a longer span`() {
        for (q in listOf(
            "cashbook this year", "money out last year", "returns this year", "returns last 6 months", "how many invoices this year",
            "invoices since the beginning", "bills this year", "kitne bills aaye is saal", "tickets this year", "what happened this year",
            "activity last year", "who did what last year", "deliveries this year", "supplier se kya aaya is saal",
            "how many times did I open the app this year", "app opens all time", "how many calculations this year", "collected this year",
            "kitni wusooli hui is saal", "payments received last year", "wusooli ab tak", "is saal ka cashbook", "pichle saal ke returns",
            "is saal kitne invoices bane", "kitni entries is saal"
        )) assertFalse("$q -> ${read(q)}", rowsOverLongSpan(read(q)))
        // a question carried on from the last one: "and this year?" after a day's rows
        for (b in listOf<Ask>(Ask.Collected(Window.TODAY), Ask.Cashbook(Window.TODAY), Ask.Returns(Window.TODAY),
            Ask.Count(Thing.INVOICES, Window.TODAY), Ask.Activity(Window.TODAY), Ask.Deliveries(Window.TODAY)))
            for (q in listOf("aur is saal?", "and this year?", "aur pichle saal?"))
                assertFalse("$q after $b -> ${read(q, before = b)}", rowsOverLongSpan(read(q, before = b)))
    }

    @Test fun `keepWithinReach holds every ask to the spans its reader can give`() {
        // rows: a longer span becomes the offer of the short ones
        val rows = listOf<(Window) -> Ask>(
            { Ask.Collected(it) }, { Ask.Cashbook(it) }, { Ask.Returns(it) }, { Ask.Deliveries(it) }, { Ask.Activity(it) },
            { Ask.Count(Thing.ENTRIES, it) }, { Ask.Count(Thing.INVOICES, it) }, { Ask.Count(Thing.BILLS, it) },
            { Ask.Count(Thing.TICKETS, it) }, { Ask.Count(Thing.CALCULATIONS, it) }, { Ask.Count(Thing.APP_OPENS, it) }
        )
        for (make in rows) {
            for (w in listOf(Window.LAST_6_MONTHS, Window.THIS_YEAR, Window.LAST_YEAR, Window.ALL_TIME))
                assertEquals("${make(w)}", Ask.SpanLimit(shortWindows.map(make), long = false), Hudhud.keepWithinReach(make(w)))
            for (w in listOf(Window.TODAY, Window.YESTERDAY, Window.THIS_WEEK, Window.LAST_WEEK, Window.THIS_MONTH, Window.LAST_MONTH))
                assertEquals("${make(w)}", make(w), Hudhud.keepWithinReach(make(w)))
        }
        // sums: any span goes through
        for (w in Window.values()) {
            assertEquals(Ask.Period(Measure.SALES, w), Hudhud.keepWithinReach(Ask.Period(Measure.SALES, w)))
            assertEquals(Ask.Period(Measure.PROFIT, w), Hudhud.keepWithinReach(Ask.Period(Measure.PROFIT, w)))
            assertEquals(Ask.Moved(listOf(2L), w, true), Hudhud.keepWithinReach(Ask.Moved(listOf(2L), w, true)))
            assertEquals(Ask.TopGoods(w), Hudhud.keepWithinReach(Ask.TopGoods(w)))
            assertEquals(Ask.Goods(listOf(100L), GoodsFacet.SOLD, w), Hudhud.keepWithinReach(Ask.Goods(listOf(100L), GoodsFacet.SOLD, w)))
        }
        // what a span does not change is left alone
        assertEquals(Ask.Count(Thing.CUSTOMERS, Window.THIS_YEAR), Hudhud.keepWithinReach(Ask.Count(Thing.CUSTOMERS, Window.THIS_YEAR)))
        assertEquals(Ask.Figure(Kind.TO_GET), Hudhud.keepWithinReach(Ask.Figure(Kind.TO_GET)))
        assertEquals(Ask.Figure(Kind.SALES_MONTH), Hudhud.keepWithinReach(Ask.Figure(Kind.SALES_MONTH)))
        // a guess stays a guess
        assertEquals(Ask.Guessed(Ask.SpanLimit(shortWindows.map { Ask.Collected(it) }, long = false)),
            Hudhud.keepWithinReach(Ask.Guessed(Ask.Collected(Window.THIS_YEAR))))
    }

    @Test fun `asks that need no span are left as they were`() {
        assertEquals(Ask.Count(Thing.CUSTOMERS, null), read("how many customers so far"))
        assertEquals(Ask.Count(Thing.CUSTOMERS, null), read("aaj tak kitne customers hain"))
        assertEquals(Ask.Figure(Kind.TO_GET), read("aaj tak ka udhaar kitna hai"))
        assertEquals(Ask.Figure(Kind.TO_GET), read("how much do I have to get"))
        assertEquals(Ask.Figure(Kind.SALES_TODAY), read("aaj ki bikri"))
        assertEquals(Ask.Figure(Kind.SALES_TODAY), read("aaj ab tak ki bikri"))
        assertEquals(Ask.Period(Measure.SALES, Window.YESTERDAY), read("kal ki bikri"))
        assertEquals(Ask.Period(Measure.SALES, Window.LAST_WEEK), read("pichle hafte ki bikri"))
        assertEquals(Ask.Figure(Kind.SALES_MONTH), read("is mahine ki bikri"))
        assertEquals(Ask.Period(Measure.PROFIT, Window.LAST_MONTH), read("pichle mahine ka munafa"))
    }

    // ---- the survivors of the mutation pass (15 Nov 2026, lesson 129): each guard gets a sentence only it can refuse

    @Test fun `six months beside this year or last year is none he tells, in words no other guard knows`() {
        // None of these holds a word of the plain year list ("saal", "سال"), so only the guard on this year and last year refuses them.
        for (q in listOf(
            "فروش ۶ ماه امسال", "مبيعات آخر 6 أشهر هذا العام", "مبيعات 6 أشهر العام الحالي",
            "parsaal ke 6 mahine ki bikri", "پارسال کے 6 مہینے کی بکری",
            "مبيعات 6 أشهر العام الماضي", "مبيعات 6 أشهر العام الفائت", "مبيعات 6 أشهر العام السابق"
        )) {
            assertTrue(q, VoiceQuestion.unsupportedSpan(q))
            assertNull(q, VoiceQuestion.windowOf(q))
            assertEquals(q, salesLimit, read(q))
        }
    }

    @Test fun `half a year beside the fiscal year is none he tells`() {
        for (q in listOf(
            "sales for the fiscal half year", "tahun fiskal setengah tahun penjualan", "वित्तीय वर्ष आधे साल की बिक्री",
            "مالی سال کے آدھے سال کی بکری", "المبيعات نصف عام السنة المالية", "অর্থবছর অর্ধ বছর বিক্রি"
        )) {
            assertTrue(q, VoiceQuestion.unsupportedSpan(q))
            assertNull(q, VoiceQuestion.windowOf(q))
        }
    }

    @Test fun `six days, six weeks or six years are none of his spans, in nine languages`() {
        for (q in listOf(
            "sales last 6 days", "pichle 6 din ki bikri", "پچھلے 6 دن کی بکری", "पिछले 6 दिन की बिक्री", "গত ৬ দিনের বিক্রি",
            "فروش ۶ روز گذشته", "مبيعات آخر 6 أيام", "گذريل 6 ڏينهن جو وڪرو", "penjualan 6 hari terakhir",
            "sales last 6 weeks", "pichle 6 hafte ki bikri", "sales last 6 years", "pichle 6 saal ki bikri"
        )) {
            assertTrue(q, VoiceQuestion.unsupportedSpan(q))
            assertNull(q, VoiceQuestion.windowOf(q))
        }
    }

    @Test fun `a span he cannot give is held to the spans he has before the sense has loaded too`() {
        HudhudSense.use(null)
        try {
            assertFalse(HudhudSense.ready)
            assertEquals(salesLimit, read("sales last 3 months"))
            assertEquals(Ask.SpanLimit(shortWindows.map { Ask.Cashbook(it) }, long = false), read("cashbook this year"))
            assertEquals(Ask.Period(Measure.SALES, Window.THIS_YEAR), read("sales this year"))
        } finally {
            HudhudSense.use(HudhudModelTest.shippedModel())
        }
    }

    // ------------------------------------------------------------ a span said after an answer, or on its own

    /** Six spans in one language: the little word that joins ("and"), and the spans as the app's own words say them. */
    private class Follow(val lang: String?, val and: String, val spans: List<String>)

    /** The windows of [Follow.spans], in order. */
    private val followWindows = listOf(
        Window.LAST_WEEK, Window.LAST_MONTH, Window.THIS_YEAR, Window.LAST_YEAR, Window.LAST_6_MONTHS, Window.ALL_TIME
    )

    private val follow = listOf(
        Follow("en", "and ", listOf("last week?", "last month?", "this year?", "last year?", "the last 6 months?", "since the beginning?")),
        Follow(null, "aur ", listOf("pichle hafte?", "pichle mahine?", "is saal?", "pichle saal?", "pichle 6 mahine?", "shuru se ab tak?")),
        Follow("ur", "اور ", listOf("پچھلے ہفتے؟", "پچھلے مہینے؟", "اس سال؟", "پچھلے سال؟", "پچھلے 6 مہینے؟", "شروع سے اب تک؟")),
        Follow("hi", "और ", listOf("पिछले हफ्ते?", "पिछले महीने?", "इस साल?", "पिछले साल?", "पिछले 6 महीने?", "शुरू से अब तक?")),
        Follow("bn", "আর ", listOf("গত সপ্তাহে?", "গত মাসে?", "এই বছর?", "গত বছর?", "শেষ ৬ মাস?", "শুরু থেকে এখন পর্যন্ত?")),
        Follow("fa", "و ", listOf("هفته گذشته؟", "ماه گذشته؟", "امسال؟", "پارسال؟", "۶ ماه گذشته؟", "از اول تا حالا؟")),
        Follow("ar", "و ", listOf("الأسبوع الماضي؟", "الشهر الماضي؟", "هذه السنة؟", "السنة الماضية؟", "آخر 6 أشهر؟", "منذ البداية؟")),
        Follow("sd", "۽ ", listOf("گذريل هفتي؟", "گذريل مهيني؟", "هن سال؟", "گذريل سال؟", "آخري 6 مهينا؟", "شروع کان هينئر تائين؟")),
        Follow("in", "dan ", listOf("minggu lalu?", "bulan lalu?", "tahun ini?", "tahun lalu?", "6 bulan terakhir?", "sejak awal?"))
    )

    @Test fun `a span said after the sales or the profit is that figure over the span, in nine languages`() {
        for (f in follow) for ((i, bare) in f.spans.withIndex()) for (q in listOf(bare, f.and + bare)) {
            val w = followWindows[i]
            assertEquals(q, Ask.Period(Measure.SALES, w), read(q, f.lang, before = Ask.Figure(Kind.SALES_MONTH)))
            assertEquals(q, Ask.Period(Measure.PROFIT, w), read(q, f.lang, before = Ask.Figure(Kind.PROFIT_MONTH)))
        }
        // The same figure whichever span came before, and a span that is itself the answer before.
        assertEquals(Ask.Period(Measure.SALES, Window.LAST_YEAR), read("and last year?", "en", before = Ask.Figure(Kind.SALES_TODAY)))
        assertEquals(Ask.Period(Measure.PROFIT, Window.ALL_TIME), read("aur shuru se ab tak?", before = Ask.Period(Measure.PROFIT, Window.THIS_YEAR)))
        assertEquals(Ask.Period(Measure.SALES, Window.LAST_YEAR), read("what about last year?", "en", before = Ask.Figure(Kind.SALES_MONTH)))
    }

    @Test fun `a span said on its own is the sales over it taken as a guess, never a question back`() {
        for (f in follow) for ((i, bare) in f.spans.withIndex())
            assertEquals(bare, Ask.Guessed(Ask.Period(Measure.SALES, followWindows[i])), read(bare, f.lang))
        assertEquals(Ask.Guessed(Ask.Period(Measure.SALES, Window.LAST_YEAR)), read("what about last year?", "en"))
        // Today is the day itself; yesterday, said alone, is its sales.
        assertEquals(Ask.Today, read("aaj?"))
        assertEquals(Ask.Today, read("today?", "en"))
        assertEquals(Ask.Guessed(Ask.Period(Measure.SALES, Window.YESTERDAY)), read("kal?"))
        assertEquals(Ask.Guessed(Ask.Period(Measure.SALES, Window.YESTERDAY)), read("kal ki?"))
        assertEquals(Ask.Guessed(Ask.Period(Measure.SALES, Window.YESTERDAY)), read("yesterday?", "en"))
    }

    @Test fun `a span said after what a customer paid is his sum over the span`() {
        val before = Ask.Moved(listOf(1L), Window.THIS_MONTH, got = true)
        for (f in follow) for ((i, bare) in f.spans.withIndex()) for (q in listOf(bare, f.and + bare))
            assertEquals(q, Ask.Moved(listOf(1L), followWindows[i], got = true), read(q, f.lang, before = before))
    }

    @Test fun `a span said after a count, the cashbook or the best sellers goes with it or is told with the spans he has`() {
        for (f in follow) for ((i, bare) in f.spans.withIndex()) {
            val w = followWindows[i]
            val q = f.and + bare
            // The customers are the same over any span: the count is today's and the card says so first, except for the
            // whole book, which is what "now" already is. What lists rows or goods is held to the spans it can list (the
            // carried question is never answered over a span its reader cannot give, never over another one in its place).
            val customers = Ask.Count(Thing.CUSTOMERS, w)
            assertEquals(q, if (w == Window.ALL_TIME) customers else Ask.Now(customers),
                read(q, f.lang, before = Ask.Count(Thing.CUSTOMERS, Window.THIS_MONTH)))
            val cash = read(q, f.lang, before = Ask.Cashbook(Window.TODAY))
            assertEquals(q, if (w.isLong) Ask.SpanLimit(shortWindows.map { Ask.Cashbook(it) }, long = false) else Ask.Cashbook(w), cash)
        }
    }

    // ------------------------------------------------------------ what a customer paid over a span the sentence states

    /** A sentence, its language, the span it says and who did the paying: [got] is true when the customer paid the shop. */
    private class Paid(val said: String, val lang: String?, val window: Window, val got: Boolean?)

    private val paid = listOf(
        // Roman Urdu, Urdu and Hindi mark the doer with ne: the customer paid.
        Paid("Ali Raza ne pichle mahine diye", null, Window.LAST_MONTH, true),
        Paid("Ali Raza ne pichle saal diye", null, Window.LAST_YEAR, true),
        Paid("Ali Raza ne pichle hafte diye", null, Window.LAST_WEEK, true),
        Paid("Ali Raza ne pichle 6 mahine diye", null, Window.LAST_6_MONTHS, true),
        Paid("Ali Raza ne pichle saal kitna diya", null, Window.LAST_YEAR, true),
        Paid("Ali Raza ne shuru se ab tak kitna diya", null, Window.ALL_TIME, true),
        Paid("Ali Raza ne aaj tak kitna diya", null, Window.ALL_TIME, true),
        Paid("Ali Raza se shuru se ab tak kitna wasool hua", null, Window.ALL_TIME, true),
        Paid("Ali Raza نے پچھلے مہینے دیے", "ur", Window.LAST_MONTH, true),
        Paid("Ali Raza نے پچھلے سال کتنا دیا", "ur", Window.LAST_YEAR, true),
        Paid("Ali Raza نے شروع سے اب تک کتنا دیا", "ur", Window.ALL_TIME, true),
        Paid("Ali Raza ने पिछले साल दिए", "hi", Window.LAST_YEAR, true),
        Paid("Ali Raza ने शुरू से अब तक कितना दिया", "hi", Window.ALL_TIME, true),
        // The shop paid: he is spoken of with ko, or the shop is the doer.
        Paid("maine Ali Raza ko pichle saal kitna diya", null, Window.LAST_YEAR, false),
        Paid("میں نے Ali Raza کو پچھلے سال کتنا دیا", "ur", Window.LAST_YEAR, false),
        Paid("मैंने Ali Raza को पिछले साल कितना दिया", "hi", Window.LAST_YEAR, false),
        // English says who paid by the helping verb or by "me": one word of name, two, a name that holds "shop".
        Paid("How much did Ali Raza pay last year", "en", Window.LAST_YEAR, true),
        Paid("How much did Ali Raza pay in the last 6 months", "en", Window.LAST_6_MONTHS, true),
        Paid("How much has Ali Raza paid this year", "en", Window.THIS_YEAR, true),
        Paid("How much has Ali Raza paid since the beginning", "en", Window.ALL_TIME, true),
        Paid("Ali Raza paid me last year", "en", Window.LAST_YEAR, true),
        Paid("Ali Raza has paid this year", "en", Window.THIS_YEAR, true),
        Paid("How much did I pay Ali Raza last year", "en", Window.LAST_YEAR, false),
        Paid("How much did I give Ali Raza in the last 6 months", "en", Window.LAST_6_MONTHS, false),
        // The other languages put the doer in the verb's ending or in its order; the span is read whichever way round.
        Paid("Ali Raza গত মাসে দিয়েছে", "bn", Window.LAST_MONTH, null),
        Paid("Ali Raza গত বছর দিয়েছে", "bn", Window.LAST_YEAR, null),
        Paid("Ali Raza گذريل مهيني ڏنو", "sd", Window.LAST_MONTH, null),
        Paid("Ali Raza گذريل سال ڏنو", "sd", Window.LAST_YEAR, null),
        Paid("Ali Raza bayar bulan lalu", "in", Window.LAST_MONTH, null),
        Paid("Ali Raza membayar tahun lalu", "in", Window.LAST_YEAR, null)
    )

    @Test fun `what a customer paid over a span the sentence states is a sum for that span, whatever the sense learnt of the verb`() {
        for (p in paid) {
            val ask = read(p.said, p.lang)
            assertTrue(p.said + " -> " + ask, ask is Ask.Moved)
            ask as Ask.Moved
            assertEquals(p.said, listOf(1L), ask.ids)
            assertEquals(p.said, p.window, ask.window)
            if (p.got != null) assertEquals(p.said, p.got, ask.got)
        }
    }

    @Test fun `since when is he a customer is still his first day, and the whole book beside a sum is the span`() {
        for (q in listOf("since when is Ali Raza a customer", "Ali Raza kab se customer hai", "Ali Raza ki pehli entry"))
            assertEquals(q, Ask.Person(listOf(1L), Hudhud.Facet.SINCE), read(q, if (q.startsWith("since")) "en" else null))
        assertEquals(Ask.Person(listOf(1L), Hudhud.Facet.SINCE), read("Ali Raza کب سے گاہک ہے", "ur"))
    }

    @Test fun `a span in a sentence the sense could turn is held where the rules read it`() {
        // The sense learnt "pichle ... diye" as his last payment from sentences with no span in them: it must not win here.
        for (w in listOf("pichle mahine", "pichle saal", "pichle hafte", "pichle 6 mahine"))
            assertTrue(w, read("Ali Raza ne $w diye") is Ask.Moved)
        // A last payment asked as such is still the last payment.
        assertEquals(Ask.Person(listOf(1L), Hudhud.Facet.LAST_PAYMENT), read("Ali Raza ki aakhri adaigi kab thi"))
    }
}
