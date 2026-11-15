package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Four classes the everyday-phrasing review (set D) still read wrong (15 Nov 2026), each held through the whole pipeline
 * (the sense attached, the app's own pages; lesson 145): a request for the list of what he can tell, a Bengali "sale" with
 * the "the" ending, a loan said as "without interest", and stock that "came" over a span. Names and goods are invented.
 */
class HudhudReviewDClassesTest {

    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val names = listOf("Ali Raza", "Bashir Ahmad", "Tariq Butt", "Naeem Khan", "Saleem Bhai", "Kamran Traders")
        .mapIndexed { i, n -> Hudhud.Name(i + 1L, n) }
    private val goods = listOf("Urea", "DAP", "Sugar", "Paracetamol", "Atta", "Ghee")
        .mapIndexed { i, n -> Hudhud.Name(100L + i, n) }

    private fun read(lang: String, said: String): Hudhud.Ask =
        Hudhud.understand(said, names, HudhudRealPages.forApp(lang), lang, goods, null)

    private fun shown(lang: String, said: String) = HudhudShown.of(read(lang, said))

    @Test fun `somebody who does not know his way is given the list, never the owner's name`() {
        assertEquals("Abilities", shown("ur-Latn", "mujhe samajh nahi aa raha kya poochun"))
        assertEquals("Abilities", shown("ur-Latn", "mujhe samajh nahi aa raha"))
        assertEquals("Abilities", shown("ur", "مجھے سمجھ نہیں آ رہا کیا پوچھوں"))
        assertEquals("Abilities", shown("hi", "मुझे समझ नहीं आ रहा क्या पूछूं"))
        assertEquals("About:OWNER_NAME", shown("ur-Latn", "mera naam kya hai"))
    }

    @Test fun `a Bengali sale with the ending is today's sales`() {
        assertEquals("Figure:SALES_TODAY", shown("bn", "ভাই আজকের সেলটা বলো তো"))
        assertEquals("Figure:SALES_TODAY", shown("bn", "আজকের সেল বলো তো"))
        assertNotEquals("BackupStatus", shown("bn", "আজকের সেলটা কত"))
    }

    @Test fun `a loan without interest is the Qarz-e-Hasna`() {
        for ((l, s) in listOf(
            "en" to "how much interest free loan is outstanding",
            "en" to "how much have I lent as a loan without interest",
            "ur-Latn" to "bina sood ka qarz kitna baqi hai",
            "ur" to "بلا سود قرض کتنا باقی ہے",
            "hi" to "बिना ब्याज का कर्ज कितना बाकी है",
            "bn" to "সুদ ছাড়া ঋণ কত বাকি আছে",
            "id" to "berapa pinjaman tanpa bunga yang belum kembali"
        )) assertEquals("[$l] $s", "Qarz", shown(l, s))
        // A plain loan or the loan of a named customer is not it.
        assertNotEquals("Qarz", shown("en", "how much loan is outstanding"))
        assertNotEquals("Qarz", shown("en", "Ali Raza ka loan kitna hai"))
    }

    @Test fun `stock that came in over a span is the deliveries of that span`() {
        val a = read("en", "what stock came this week")
        assertTrue(a.toString(), a is Hudhud.Ask.Deliveries && a.window == VoiceQuestion.Window.THIS_WEEK)
        val b = read("en", "which stock came in today")
        assertTrue(b.toString(), b is Hudhud.Ask.Deliveries && b.window == VoiceQuestion.Window.TODAY)
        // Stock that is low, or the stock of a product, is not a delivery.
        assertEquals("Status:LOW_STOCK", shown("en", "which stock is low"))
        assertNotEquals("Deliveries", shown("en", "how much stock is left"))
    }
}
