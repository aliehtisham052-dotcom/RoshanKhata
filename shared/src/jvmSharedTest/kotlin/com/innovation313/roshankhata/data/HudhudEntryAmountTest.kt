package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The amount an entry is offered with, through the whole pipeline (16 Nov 2026, audit T30 / H-01; lesson 145): "Ali Raza
 * ko 5k diye" opened the form with Rs 5. Invented names.
 */
class HudhudEntryAmountTest {

    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val names = listOf("Ali Raza", "Bashir Ahmad", "Tariq Butt").mapIndexed { i, n -> Hudhud.Name(i + 1L, n) }

    private fun entry(lang: String, said: String): Hudhud.Ask.Entry {
        val a = Hudhud.understand(said, names, HudhudRealPages.forApp(lang), lang, emptyList(), null)
        return (a as? Hudhud.Ask.Entry) ?: error("\"$said\" read as $a, not an entry")
    }

    @Test fun `shorthand amounts reach the form as the shop meant them`() {
        assertEquals(5000.0, entry("ur-Latn", "Ali Raza ko 5k diye").amount, 0.0)
        assertEquals(1500.0, entry("ur-Latn", "Ali Raza ko 1.5k diye").amount, 0.0)
        assertEquals(1500.0, entry("ur-Latn", "Ali Raza ko dedh hazar diye").amount, 0.0)
        assertEquals(2500.0, entry("ur-Latn", "Bashir Ahmad se dhai hazar liye").amount, 0.0)
        assertEquals(3500.0, entry("ur-Latn", "Ali Raza ko saade teen hazar diye").amount, 0.0)
        assertEquals(1500.0, entry("ur", "علی رضا کو ڈیڑھ ہزار دیے").amount, 0.0)
        assertEquals(200000.0, entry("ur-Latn", "Tariq Butt ko 2lac diye").amount, 0.0)
    }

    @Test fun `the person is still found beside the shorthand`() {
        val e = entry("ur-Latn", "Ali Raza ko 5k diye")
        assertEquals(listOf(1L), e.ids)
        assertEquals(true, e.isGiven)
    }
}
