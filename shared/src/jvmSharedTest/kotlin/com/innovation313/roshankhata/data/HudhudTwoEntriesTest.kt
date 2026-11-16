package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.Hudhud.Ask
import com.innovation313.roshankhata.data.VoiceQuestion.Dated
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two entries in one sentence (16 Nov 2026): "Ali ko 500 diye aur Bashir se 300 liye" was one entry of 500 for a choice of Ali and
 * Bashir. Each is offered on its own, one at a time ([Ask.Entries]); one amount for two names stays one entry. Through the whole
 * pipeline with the shipped sense (lesson 145). Invented names only.
 */
class HudhudTwoEntriesTest {

    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val names = listOf("Ali Raza", "Bashir Ahmad", "Tariq Butt", "June Traders").mapIndexed { i, n -> Hudhud.Name(i + 1L, n) }
    private val goods = listOf("Urea", "DAP").mapIndexed { i, n -> Hudhud.Name(100L + i, n) }

    private fun read(lang: String, said: String) = Hudhud.understand(said, names, HudhudRealPages.forApp(lang), lang, goods, null)

    // ------------------------------------------------------------------------------------------------ two entries

    @Test fun `two entries in one sentence are offered one at a time, never one wrong entry`() {
        val two = read("ur-Latn", "Ali ko 500 diye aur Bashir se 300 liye") as Ask.Entries
        assertEquals(listOf(500.0, 300.0), two.entries.map { it.amount })
        assertEquals(listOf(true, false), two.entries.map { it.isGiven })
        assertEquals(listOf(listOf(1L), listOf(2L)), two.entries.map { it.ids })
        for ((lang, said) in listOf(
            "en" to "I gave Ali Raza 500 and got 300 from Bashir",
            "ur" to "علی کو 500 دیے اور بشیر سے 300 لیے",
            "hi" to "अली को 500 दिए और बशीर से 300 लिए",
            "bn" to "আলীকে ৫০০ টাকা দিলাম আর বশিরের কাছ থেকে ৩০০ টাকা পেলাম",
            "ar" to "أعطيت علي 500 و استلمت من بشير 300",
            "fa" to "به علی 500 دادم و از بشیر 300 گرفتم",
            "sd" to "علي کي 500 ڏنا ۽ بشير کان 300 ورتا",
            "in" to "saya kasih Ali 500 dan terima 300 dari Bashir"
        )) {
            val r = read(lang, said)
            assertTrue("[$lang] $said -> $r", r is Ask.Entries && r.entries.size == 2)
        }
        // The day said once is each half's.
        val dated = read("ur-Latn", "kal Ali ko 500 diye aur Bashir se 300 liye") as Ask.Entries
        assertEquals(listOf(Dated(daysBack = 1), Dated(daysBack = 1)), dated.entries.map { it.day })
    }

    @Test fun `one amount for two names, or one entry, stays one entry`() {
        assertTrue(read("ur-Latn", "Ali aur Bashir ko 500 diye") is Ask.Entry)
        assertTrue(read("ur-Latn", "Ali ko 500 diye") is Ask.Entry)
        assertFalse(read("ur-Latn", "500 aur 300 ka total") is Ask.Entries)
    }
}
