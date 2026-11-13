package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.Hudhud.Ask
import com.innovation313.roshankhata.data.VoiceQuestion.Kind
import com.innovation313.roshankhata.data.VoiceQuestion.Window
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What Hudhud takes a question for (12 Nov 2026), in the scripts a recogniser writes. Made-up names only. */
class HudhudTest {

    // understand() reads with the sense the app ships (the rules alone: byRules).
    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val names = listOf(
        Hudhud.Name(1, "Ali Raza"), Hudhud.Name(2, "Bashir Ahmad"), Hudhud.Name(3, "Sale Point"),
        Hudhud.Name(4, "Kabir Traders"), Hudhud.Name(5, "Asghar Spray Wala"), Hudhud.Name(6, "Asghar Ali")
    )

    private val topics = listOf(
        Hudhud.Topic("intro_backup", "Backup", "Keep a copy of your book on Google Drive or the phone."),
        Hudhud.Topic("intro_cheques", "Cheques", "Note every cheque you get or give and the day it is due."),
        Hudhud.Topic("intro_cashbook", "Cashbook", "Every evening, count the drawer and close the day."),
        Hudhud.Topic("intro_documents", "Documents", "Quotation, invoice and delivery challan, one after the other."),
        Hudhud.Topic("intro_staff", "Staff", "Salaries, advances and attendance of the people who work for you.")
    )

    // The rules' own reading: HudhudSenseTest measures the rules and the sense together on the held-out set.
    private fun ask(s: String) = Hudhud.byRules(s, names, topics)

    @Test fun `the eight figures are VoiceQuestion's`() {
        assertEquals(Ask.Figure(Kind.SALES_TODAY), ask("aaj ki bikri kitni hai"))
        assertEquals(Ask.Figure(Kind.SALES_TODAY), ask("आज की बिक्री"))
        assertEquals(Ask.Figure(Kind.PROFIT_MONTH), ask("اس مہینے کا منافع"))
        assertEquals(Ask.Figure(Kind.WHO_OWES_MOST), ask("sab se zyada baqaya kis ka hai"))
        assertEquals(Ask.Figure(Kind.TO_GIVE), ask("mujhe kitna dena hai"))
    }

    @Test fun `a figure wins over a party named like its word`() {
        assertEquals(Ask.Figure(Kind.SALES_TODAY), ask("aaj ki sale"))
    }

    @Test fun `a named customer is his balance`() {
        assertEquals(Ask.Parties(listOf(2L)), ask("Bashir ka kitna baqaya hai"))
        assertEquals(Ask.Parties(listOf(2L)), ask("bashir"))
    }

    @Test fun `a name holding a figure's letters is still a name`() {
        // Saleem holds "sale" and Ali Wholesale holds "sale": both asked for this month's sales before 12 Nov 2026.
        val more = names + listOf(Hudhud.Name(7, "Saleem Khan"), Hudhud.Name(8, "Ali Wholesale"))
        val saleem = Hudhud.understand("Saleem ka kitna baqaya hai", more, topics)
        assertTrue("$saleem", saleem is Ask.Parties && saleem.ids.first() == 7L)
        val ali = Hudhud.understand("Ali Wholesale ka hisaab", more, topics)
        assertTrue("$ali", ali is Ask.Parties && 8L in ali.ids)
    }

    @Test fun `two of one name are both offered, not guessed`() {
        val got = ask("Asghar ka hisaab") as Ask.Parties
        assertEquals(setOf(5L, 6L), got.ids.toSet())
    }

    @Test fun `a question word is never taken for a name`() {
        // "kab" begins Kabir; it is a question word and is set aside before names are looked for.
        assertEquals(Ask.BackupStatus, ask("backup kab hua"))
        assertEquals(Ask.BackupStatus, ask("بیک اپ کب ہوا"))
        assertEquals(Ask.BackupStatus, ask("बैकअप कब हुआ"))
    }

    @Test fun `how asks for the help page`() {
        assertEquals(Ask.Help("intro_backup"), ask("backup kaise karun"))
        assertEquals(Ask.Help("intro_cheques"), ask("how do I note a cheque"))
    }

    @Test fun `a feature named alone is its help page`() {
        assertEquals(Ask.Help("intro_cheques"), ask("cheque"))
        assertEquals(Ask.Help("intro_staff"), ask("staff ki salary"))
    }

    @Test fun `the drawer in six languages`() {
        listOf("galle mein kitna hona chahiye", "گلے میں کتنے پیسے ہونے چاہئیں", "गल्ले में कितना होना चाहिए",
            "berapa uang di laci", "how much cash in hand", "ড্রয়ার এ কত টাকা", "ড্রয়ারে কত টাকা")
            .forEach { assertEquals(it, Ask.Drawer, ask(it)) }
    }

    @Test fun `salaam is answered as salaam, hello as hello`() {
        assertEquals(Ask.Salaam, ask("Assalam o alaikum"))
        assertEquals(Ask.Salaam, ask("السلام علیکم"))
        assertEquals(Ask.Salaam, ask("سلام"))
        assertEquals(Ask.Hello, ask("hello hudhud"))
    }

    @Test fun `what he does not know he says`() {
        assertEquals(Ask.Unknown, ask("aaj mausam kaisa hai"))
        assertEquals(Ask.Unknown, ask(""))
        // Islamabad is not salaam: whole words only.
        assertEquals(Ask.Unknown, ask("اسلام آباد"))
    }

    @Test fun `the first guess he knows is taken`() {
        assertEquals("aaj ki bikri" to Ask.Figure(Kind.SALES_TODAY),
            Hudhud.understand(listOf("zzz qqq", "aaj ki bikri"), names, topics))
        assertEquals("zzz" to Ask.Unknown, Hudhud.understand(listOf("zzz"), names, topics))
    }
    // ------------------------------------------------------------------- Marhala 2 (12 Nov 2026)

    @Test fun `a figure over the span of days it names`() {
        assertEquals(Ask.Period(Hudhud.Measure.SALES, Window.YESTERDAY), ask("kal ki sale kitni hui"))
        assertEquals(Ask.Period(Hudhud.Measure.SALES, Window.YESTERDAY), ask("کل کی بکری"))
        assertEquals(Ask.Period(Hudhud.Measure.SALES, Window.YESTERDAY), ask("penjualan kemarin"))
        assertEquals(Ask.Period(Hudhud.Measure.SALES, Window.THIS_WEEK), ask("is hafte ki bikri"))
        assertEquals(Ask.Period(Hudhud.Measure.SALES, Window.LAST_WEEK), ask("pichle hafte ki sale"))
        assertEquals(Ask.Period(Hudhud.Measure.SALES, Window.LAST_MONTH), ask("last month's sales"))
        assertEquals(Ask.Period(Hudhud.Measure.SALES, Window.LAST_MONTH), ask("पिछले महीने की बिक्री"))
        assertEquals(Ask.Period(Hudhud.Measure.SALES, Window.LAST_MONTH), ask("গত মাসের বিক্রি"))
        assertEquals(Ask.Period(Hudhud.Measure.SALES, Window.LAST_MONTH), ask("فروش ماه گذشته"))
        assertEquals(Ask.Period(Hudhud.Measure.SALES, Window.LAST_MONTH), ask("مبيعات الشهر الماضي"))
        assertEquals(Ask.Period(Hudhud.Measure.PROFIT, Window.LAST_MONTH), ask("pichle mahine ka munafa"))
        assertEquals(Ask.Period(Hudhud.Measure.PROFIT, Window.TODAY), ask("aaj ka munafa"))
        assertEquals(Ask.Period(Hudhud.Measure.PROFIT, Window.THIS_WEEK), ask("is hafte kitna faida hua"))
        // The eight figures' own day and month stay as they were.
        assertEquals(Ask.Figure(Kind.SALES_TODAY), ask("aaj ki sale"))
        assertEquals(Ask.Figure(Kind.SALES_MONTH), ask("is mahine ki bikri"))
        assertEquals(Ask.Figure(Kind.PROFIT_MONTH), ask("is mahine ka munafa"))
    }

    @Test fun `a span of days runs as the screens read it`() {
        val tz = java.util.TimeZone.getTimeZone("Asia/Karachi")
        val f = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.ROOT).apply { timeZone = tz }
        fun at(s: String) = f.parse(s)!!.time
        val now = at("2026-11-12 10:30") // a Thursday; Pakistan's week starts on Monday
        val before = ShopCountry.current
        ShopCountry.current = ShopCountry.PAKISTAN
        try {
            assertEquals(at("2026-11-12 00:00") to at("2026-11-13 00:00"), Hudhud.span(Window.TODAY, now, tz))
            assertEquals(at("2026-11-11 00:00") to at("2026-11-12 00:00"), Hudhud.span(Window.YESTERDAY, now, tz))
            assertEquals(at("2026-11-09 00:00") to at("2026-11-13 00:00"), Hudhud.span(Window.THIS_WEEK, now, tz))
            assertEquals(at("2026-11-02 00:00") to at("2026-11-09 00:00"), Hudhud.span(Window.LAST_WEEK, now, tz))
            assertEquals(at("2026-11-01 00:00") to at("2026-12-01 00:00"), Hudhud.span(Window.THIS_MONTH, now, tz))
            assertEquals(at("2026-10-01 00:00") to at("2026-11-01 00:00"), Hudhud.span(Window.LAST_MONTH, now, tz))
        } finally {
            ShopCountry.current = before
        }
    }

    @Test fun `an entry said as a fact opens the form, never a question`() {
        assertEquals(Ask.Entry(500.0, true, listOf(2L), listOf("bashir")), ask("Bashir ko 500 diye"))
        assertEquals(Ask.Entry(500.0, false, listOf(2L), listOf("bashir")), ask("Bashir se 500 liye"))
        // Bashir is the one who gave: the money came in.
        assertEquals(Ask.Entry(500.0, false, listOf(2L), listOf("bashir")), ask("Bashir ne 500 diye"))
        assertEquals(Ask.Entry(500.0, true, emptyList(), emptyList()), ask("500 diye"))
        // Asked about, not said: his balance.
        assertEquals(Ask.Parties(listOf(2L)), ask("Bashir ko kitna diya"))
    }

    @Test fun `when he will pay is the Radar's`() {
        assertEquals(Ask.WhenPays(listOf(2L)), ask("Bashir kab dega"))
        assertEquals(Ask.WhenPays(listOf(2L)), ask("when will Bashir pay"))
        assertEquals(Ask.WhenPays(emptyList()), ask("aaj kaun dega"))
        assertEquals(Ask.WhenPays(emptyList()), ask("who will pay today"))
    }

    @Test fun `what he paid or took over a span`() {
        assertEquals(Ask.Moved(listOf(2L), Window.YESTERDAY, got = true), ask("Bashir ne kal kitna diya"))
        assertEquals(Ask.Moved(listOf(2L), Window.THIS_WEEK, got = false), ask("Bashir ko is hafte kitna diya"))
    }

    @Test fun `today's summary and good morning`() {
        assertEquals(Ask.Today, ask("aaj ka khulasa"))
        assertEquals(Ask.Today, ask("What's on today?"))
        assertEquals(Ask.Today, ask("good morning"))
        assertEquals(Ask.Today, ask("صبح بخیر"))
    }

    @Test fun `salaam in Pakistan and the OIC's countries, the hour's own greeting elsewhere`() {
        assertEquals(Hudhud.Greeting.SALAAM, Hudhud.greeting(ShopCountry.PAKISTAN, 20))
        assertEquals(Hudhud.Greeting.SALAAM, Hudhud.greeting(ShopCountry.INDONESIA, 9))
        assertEquals(Hudhud.Greeting.SALAAM, Hudhud.greeting(ShopCountry.NIGERIA, 15))
        assertEquals(Hudhud.Greeting.MORNING, Hudhud.greeting(ShopCountry.INDIA, 9))
        assertEquals(Hudhud.Greeting.AFTERNOON, Hudhud.greeting(ShopCountry.INDIA, 13))
        assertEquals(Hudhud.Greeting.EVENING, Hudhud.greeting(ShopCountry.UNITED_KINGDOM, 19))
        assertEquals(Hudhud.Greeting.EVENING, Hudhud.greeting(ShopCountry.OTHER, 2))
    }

    // 13 Nov 2026: what he can tell, and a little talk.

    @Test fun `what he can tell, asked in several languages and as a lone help`() {
        assertEquals(Ask.Abilities, ask("Aap kya bata sakte hain?"))
        assertEquals(Ask.Abilities, ask("what can you do"))
        assertEquals(Ask.Abilities, ask("آپ کیا بتا سکتے ہیں؟"))
        assertEquals(Ask.Abilities, ask("ماذا يمكنك أن تخبرني؟"))
        assertEquals(Ask.Abilities, ask("help"))
        assertEquals(Ask.Abilities, ask("Hudhud madad"))
    }

    @Test fun `small talk is answered, and a salaam with it is answered too`() {
        assertEquals(Ask.Chat(Hudhud.Talk.HOW_ARE_YOU), ask("kaise ho"))
        assertEquals(Ask.Chat(Hudhud.Talk.HOW_ARE_YOU), ask("Hudhud kya haal hai yaar"))
        assertEquals(Ask.Chat(Hudhud.Talk.HOW_ARE_YOU, salaam = true), ask("Assalam o alaikum, kaise hain?"))
        assertEquals(Ask.Chat(Hudhud.Talk.HOW_ARE_YOU), ask("how are you"))
        assertEquals(Ask.Chat(Hudhud.Talk.HOW_ARE_YOU), ask("apa kabar"))
        assertEquals(Ask.Chat(Hudhud.Talk.HOW_ARE_YOU), ask("কেমন আছেন"))
        assertEquals(Ask.Chat(Hudhud.Talk.WHO_ARE_YOU), ask("tum kaun ho"))
        assertEquals(Ask.Chat(Hudhud.Talk.WHO_ARE_YOU), ask("آپ کا نام کیا ہے"))
        assertEquals(Ask.Chat(Hudhud.Talk.THANKS), ask("shukriya"))
        assertEquals(Ask.Chat(Hudhud.Talk.THANKS), ask("धन्यवाद"))
        assertEquals(Ask.Chat(Hudhud.Talk.THANKS), ask("thanks a lot"))
        assertEquals(Ask.Chat(Hudhud.Talk.BYE), ask("Allah Hafiz"))
        assertEquals(Ask.Chat(Hudhud.Talk.PRAISE), ask("shabash"))
        assertEquals(Ask.Chat(Hudhud.Talk.FINE), ask("main theek hoon"))
    }

    @Test fun `a name that small talk also uses stays a name`() {
        val book = names + listOf(Hudhud.Name(7, "Hafiz Sabir"), Hudhud.Name(8, "Kamal Din"))
        val hafiz = Hudhud.understand("Hafiz Sabir ka baqaya", book, topics)
        assertTrue("$hafiz", hafiz is Ask.Parties && hafiz.ids.first() == 7L)
        val kamal = Hudhud.understand("Kamal", book, topics)
        assertTrue("$kamal", kamal is Ask.Parties && kamal.ids.first() == 8L)
        // A customer named beside "kaise ho" is still that customer.
        val ali = ask("Ali Raza kaise ho")
        assertTrue("$ali", ali is Ask.Parties && ali.ids.first() == 1L)
    }

    @Test fun `small talk never takes a figure, today's summary or an entry`() {
        assertEquals(Ask.Figure(Kind.SALES_TODAY), ask("shukriya, aaj ki bikri kitni hai"))
        assertEquals(Ask.Today, ask("good morning"))
        val entry = ask("shukriya Bashir ko 500 diye")
        assertTrue("$entry", entry is Ask.Entry)
    }
}
