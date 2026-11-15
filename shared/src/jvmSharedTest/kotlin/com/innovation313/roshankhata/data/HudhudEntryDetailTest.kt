package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.Hudhud.Ask
import com.innovation313.roshankhata.data.Hudhud.Facet
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 15 Nov 2026, the catalog's GAP group "transactions" (importance 7: money records). What an entry or a payment itself holds —
 * its note, number, reference, the way it was paid, who wrote it — is the customer's last entry (or last payment) card, asked
 * with the entry's own word. Read through the whole pipeline with the learnt sense (lesson 145). Names are invented (lesson 80).
 */
class HudhudEntryDetailTest {

    private val names = listOf(Hudhud.Name(1, "Ali Raza"), Hudhud.Name(2, "Bashir Ahmad"))
    private val topics = listOf(Hudhud.Topic("intro_cashbook", "Cashbook", "Money in and out"))

    private fun heard(s: String, lang: String) = Hudhud.understand(s, names, topics, lang)

    @Test fun `the note, number or writer of an entry is the last entry card`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        val want = Ask.Person(listOf(1L), Facet.LAST_ENTRY)
        assertEquals(want, heard("Ali Raza ki entry ka note dikhao", "ur-Latn"))
        assertEquals(want, heard("Ali Raza ki aakhri entry ka number", "ur-Latn"))
        assertEquals(want, heard("Ali Raza ki last entry kis ne likhi", "ur-Latn"))
        assertEquals(want, heard("What is the note on Ali Raza's last entry", "en"))
    }

    @Test fun `the reference or the way a payment came is the last payment card`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        assertEquals(Ask.Person(listOf(1L), Facet.LAST_PAYMENT), heard("Ali Raza ka payment reference kya hai", "ur-Latn"))
        assertEquals(Ask.Person(listOf(2L), Facet.LAST_PAYMENT), heard("Bashir Ahmad ne kis tareeqe se paise diye", "ur-Latn"))
    }

    @Test fun `a bare number or a count of entries is not an entry's detail`() {
        HudhudSense.use(HudhudModelTest.shippedModel())
        assertEquals(Ask.Person(listOf(1L), Facet.PHONE), heard("Ali Raza ka number", "ur-Latn"))
        assertEquals(Ask.Person(listOf(1L), Facet.ENTRIES), heard("Ali Raza ki kitni entries hain", "ur-Latn"))
    }
}
