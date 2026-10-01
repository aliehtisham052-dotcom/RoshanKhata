package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The owner's three products from the Sayban bill, and the edges around them. */
class LabelGuessTest {

    @Test
    fun `leptokill 20 EC 800 ml`() {
        val g = LabelGuess.of("LEPTOKILL 20%EC 800 ML", null, "Sayban International")
        assertEquals("EC", g.formulation)
        assertEquals("Pesticide", g.type)
        assertEquals("bottle", g.unit)
        assertEquals("Sayban International", g.company)
    }

    @Test
    fun `anaaj goli tablets`() {
        val g = LabelGuess.of("ANAAJ GOLI 56% (TAB) 90 GM", null, "Sayban International")
        assertEquals("TAB", g.formulation)
        assertEquals("Pesticide", g.type)
        assertEquals("packet", g.unit)
    }

    @Test
    fun `naamvar urea phosphate is fertilizer by the bag`() {
        val g = LabelGuess.of("NAAMVAR UREA PHOSPHATE 1C", null, null)
        assertNull(g.formulation)
        assertEquals("Fertilizer", g.type)
        assertEquals("bag", g.unit)
        assertNull(g.company)
    }

    /** The bill's own unit wins over a guess from the name. */
    @Test
    fun `a unit on the bill line is kept`() {
        assertEquals("kg", LabelGuess.of("Coragen 20SC 100ml", "kg", null).unit)
    }

    /** Letters that merely contain a code are not a formulation: "SECURE", "DECIS". */
    @Test
    fun `codes inside words are not formulations`() {
        assertNull(LabelGuess.of("SECURE PLUS", null, null).formulation)
        assertNull(LabelGuess.of("Decis", null, null).type)
    }

    @Test
    fun `seed is seed`() {
        assertEquals("Seed", LabelGuess.of("Hybrid Maize Seed 10 KG", null, null).type)
    }
}
