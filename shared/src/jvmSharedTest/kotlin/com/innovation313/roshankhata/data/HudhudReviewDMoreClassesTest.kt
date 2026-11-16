package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Three more classes the everyday-phrasing review (set D) read wrong (16 Nov 2026), each held through the whole pipeline (the sense
 * attached, the app's own pages; lesson 145): what the shop owes a supplier said as "how much must I pay" (Arabic, Indonesian: "how
 * many" and "how much" are one word, so it was a count of suppliers), a head count of staff in Bengali ("how many people work": it was
 * the percent help page), and what is running short in Arabic and Farsi (the sense took it for the day's summary, and for the expiring
 * batches). The controls keep their old readings. Names and goods are invented.
 */
class HudhudReviewDMoreClassesTest {

    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val names = listOf("Ali Raza", "Bashir Ahmad", "Tariq Butt", "Naeem Khan", "Saleem Bhai", "Kamran Traders")
        .mapIndexed { i, n -> Hudhud.Name(i + 1L, n) }
    private val goods = listOf("Urea", "DAP", "Sugar", "Paracetamol", "Atta", "Ghee")
        .mapIndexed { i, n -> Hudhud.Name(100L + i, n) }

    private fun shown(lang: String, said: String) =
        HudhudShown.of(Hudhud.understand(said, names, HudhudRealPages.forApp(lang), lang, goods, null))

    @Test fun `what the shop must pay a supplier is what it owes, not how many suppliers it has`() {
        assertEquals("Figure:TO_GIVE", shown("ar", "كم يجب أن أدفع للمورد"))
        assertEquals("Figure:TO_GIVE", shown("in", "berapa yang harus saya bayar ke pemasok"))
        assertEquals("Figure:TO_GIVE", shown("in", "berapa utang saya ke pemasok"))
        // the count of suppliers is still a count
        assertEquals("Count:SUPPLIERS:null", shown("ar", "كم عدد الموردين"))
        assertEquals("Count:SUPPLIERS:null", shown("in", "berapa jumlah pemasok"))
    }

    @Test fun `how many people work is the staff count in Bengali`() {
        assertEquals("Count:STAFF:null", shown("bn", "কতজন কাজ করে"))
        assertEquals("Count:STAFF:null", shown("bn", "কতজন কাজ করেন"))
        // somebody's own customers are not staff
        assertNotEquals("Count:STAFF:null", shown("bn", "কতজন কাস্টমার আছে"))
    }

    @Test fun `what is running short is the low stock in Arabic and Farsi`() {
        assertEquals("Status:LOW_STOCK", shown("ar", "ما الذي ينقص"))
        assertEquals("Status:LOW_STOCK", shown("ar", "ما الذي ينفد"))
        assertEquals("Status:LOW_STOCK", shown("fa", "چه چیزهایی تمام می‌شود"))
        // expiry stays expiry
        assertEquals("Figure:EXPIRING", shown("fa", "چه چیزهایی تاریخ انقضا دارد"))
    }
}
