package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.VoiceQuestion.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The question each way of asking lands on, in the scripts a recogniser writes. */
class VoiceQuestionTest {

    private fun q(s: String) = VoiceQuestion.classify(s)

    @Test fun `profit in five scripts`() {
        listOf("is mahine ka munafa", "اس مہینے کا منافع", "इस महीने का मुनाफ़ा", "laba bulan ini", "এই মাসের লাভ", "سود این ماه")
            .forEach { assertEquals(it, Kind.PROFIT_MONTH, q(it)) }
    }

    @Test fun `who owes the most`() {
        listOf("sab se zyada baqaya kis ka hai", "سب سے زیادہ ادھار کس کا ہے", "सबसे ज्यादा उधार किसका है",
            "who owes the most", "সবচেয়ে বেশি বাকি কার", "siapa piutang paling besar")
            .forEach { assertEquals(it, Kind.WHO_OWES_MOST, q(it)) }
    }

    @Test fun `sales today and this month`() {
        assertEquals(Kind.SALES_TODAY, q("aaj ki bikri kitni hai"))
        assertEquals(Kind.SALES_TODAY, q("آج کتنا بیچا"))
        assertEquals(Kind.SALES_TODAY, q("आज की बिक्री"))
        assertEquals(Kind.SALES_MONTH, q("is mahine ki bikri"))
        assertEquals(Kind.SALES_MONTH, q("penjualan bulan ini"))
    }

    @Test fun `to get and to give stay apart`() {
        assertEquals(Kind.TO_GET, q("kitna lena hai"))
        assertEquals(Kind.TO_GET, q("کل کتنا لینا ہے؟"))
        assertEquals(Kind.TO_GIVE, q("mujhe kitna dena hai"))
        assertEquals(Kind.TO_GIVE, q("مجھے کتنا دینا ہے"))
        assertEquals(Kind.TO_GIVE, q("मुझे कितना देना है"))
        assertEquals(Kind.TO_GET, q("total piutang"))
    }

    @Test fun `who has not paid and what is expiring`() {
        assertEquals(Kind.NOT_PAID, q("is hafte kis ne paisa nahi diya"))
        assertEquals(Kind.NOT_PAID, q("کس نے پیسے نہیں دیے"))
        assertEquals(Kind.EXPIRING, q("kaun si dawai expire hone wali hai"))
        assertEquals(Kind.EXPIRING, q("কোন মালের মেয়াদ শেষ"))
    }

    @Test fun `arabic letters that differ from urdu still match`() {
        assertEquals(Kind.PROFIT_MONTH, q("كم الربح هذا الشهر"))
        assertEquals(Kind.SALES_TODAY, q("مبيعات اليوم"))
    }

    @Test fun `a figure's word counts only whole, so names stay names`() {
        // 12 Nov 2026 (Hudhud): the letters were looked for anywhere, so each of these asked for the sales or the profit.
        assertEquals(Kind.TO_GET, q("Saleem ka kitna baqaya hai"))
        assertNull(q("Ali Wholesale ka hisaab"))
        assertNull(q("Kalabagh Traders"))
        assertNull(q("سودا سلف"))
    }

    @Test fun `words with their joined pieces still count`() {
        assertEquals(Kind.SALES_TODAY, q("আজকের বিক্রি"))
        assertEquals(Kind.PROFIT_MONTH, q("الأرباح هذا الشهر"))
        assertEquals(Kind.SALES_MONTH, q("والمبيعات"))
        assertEquals(Kind.TO_GET, q("total dues"))
    }

    @Test fun `the words owners say most`() {
        assertEquals(Kind.PROFIT_MONTH, q("is mahine kitna faida hua"))
        assertEquals(Kind.SALES_TODAY, q("aaj kitna bika"))
        assertEquals(Kind.SALES_TODAY, q("how much did I sell today"))
        assertEquals(Kind.TO_GET, q("kitna baqi hai"))
    }

    @Test fun `a Bengali ya written either way is one word`() {
        val composed = "সবচেয়ে বেশি বাকি কার".replace("\u09AF\u09BC", "\u09DF")
        assertEquals(Kind.WHO_OWES_MOST, q(composed))
    }

    @Test fun `anything else is not guessed at`() {
        assertNull(q("mausam kaisa hai"))
        assertNull(q(""))
        assertEquals("aaj ki bikri" to Kind.SALES_TODAY, VoiceQuestion.classify(listOf("mausam", "aaj ki bikri")))
    }
}
