package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.Hudhud.Ask
import com.innovation313.roshankhata.data.VoiceQuestion.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What Hudhud takes a question for (12 Nov 2026), in the scripts a recogniser writes. Made-up names only. */
class HudhudTest {

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

    private fun ask(s: String) = Hudhud.understand(s, names, topics)

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
}
