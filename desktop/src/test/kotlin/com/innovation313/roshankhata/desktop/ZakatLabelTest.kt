package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.Zakat
import com.innovation313.roshankhata.words.W
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Zakat price box said "per gram" while "Per tola" was chosen (25 Oct
 * 2026, the audit). Its label now names the metal and the unit both choices
 * name, so the figure typed is the one the nisab multiplies.
 */
class ZakatLabelTest {

    @Test
    fun `the label names the chosen metal and unit`() {
        assertEquals(W.silver_price_hint_tola, zakatPriceLabel(gold = false, perTola = true))
        assertEquals(W.silver_price_hint, zakatPriceLabel(gold = false, perTola = false))
        assertEquals(W.gold_price_hint_tola, zakatPriceLabel(gold = true, perTola = true))
        assertEquals(W.gold_price_hint, zakatPriceLabel(gold = true, perTola = false))
    }

    @Test
    fun `a gram's price read as a tola's puts the nisab far too low`() {
        // Made-up rate: silver at 540 a gram is about 6,300 a tola.
        val right = Zakat.nisab(540.0 * Zakat.GRAMS_PER_TOLA, gold = false, perTola = true)
        val misread = Zakat.nisab(540.0, gold = false, perTola = true)
        assertTrue("the old label's mistake: about 11.66 times too low", right / misread > 11.6)
    }
}
