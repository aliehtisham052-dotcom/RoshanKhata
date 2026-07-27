package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Turning a shop's book into the four figures Zakat works from.
 *
 * The case these exist for was reported off the owner's own screen: the
 * calculator summed the whole book into one number per category before
 * deciding what was owed and what was owing, so a customer the shop owed
 * money to quietly cancelled out a customer who owed money to the shop. The
 * Zakat came out right by arithmetic accident; the breakdown on the screen —
 * the part the owner reads before paying a religious obligation — did not.
 */
class ZakatTest {

    private fun party(
        id: Long,
        certain: Double = 0.0,
        doubtful: Double = 0.0,
        qarz: Double = 0.0
    ) = PartyZakatBalance(id, certain, doubtful, qarz)

    private val cents = 0.001

    /** The reported case: two customers, opposite directions. */
    @Test
    fun `money owed to the shop and money the shop owes stay separate`() {
        val inputs = Zakat.fromParties(
            listOf(party(1, certain = 500.0), party(2, certain = -300.0))
        )

        assertEquals(500.0, inputs.certainReceivables, cents)
        assertEquals(300.0, inputs.payables, cents)
    }

    /** And the total the owner actually pays on is unchanged by the fix. */
    @Test
    fun `the wealth figure is the same as before`() {
        val inputs = Zakat.fromParties(
            listOf(party(1, certain = 500.0), party(2, certain = -300.0))
        )
        assertEquals(200.0, Zakat.zakatableWealth(inputs, cashAndStock = 0.0), cents)
    }

    /** The ordinary shop: everybody owes, nobody is owed. */
    @Test
    fun `a book with no payables reports none`() {
        val inputs = Zakat.fromParties(
            listOf(party(1, certain = 200.0), party(2, certain = 50.0))
        )
        assertEquals(250.0, inputs.certainReceivables, cents)
        assertEquals(0.0, inputs.payables, cents)
    }

    /** Each kind is counted as itself. */
    @Test
    fun `the three kinds are kept apart`() {
        val inputs = Zakat.fromParties(
            listOf(party(1, certain = 100.0, doubtful = 60.0, qarz = 40.0))
        )
        assertEquals(100.0, inputs.certainReceivables, cents)
        assertEquals(60.0, inputs.doubtfulReceivables, cents)
        assertEquals(40.0, inputs.qarzeHasnaGiven, cents)
        assertEquals(0.0, inputs.payables, cents)
    }

    /**
     * A customer in credit overall, with one kind in the red. Nothing may be
     * counted from them beyond what they actually owe, and the shrinking falls
     * on every kind in proportion rather than on one chosen kind.
     */
    @Test
    fun `a customer never yields more than their own balance`() {
        val inputs = Zakat.fromParties(
            listOf(party(1, certain = 500.0, doubtful = -300.0))
        )

        assertEquals(200.0, inputs.certainReceivables, cents)
        assertEquals(0.0, inputs.doubtfulReceivables, cents)
        assertEquals(0.0, inputs.payables, cents)
    }

    /** In proportion, so no kind is quietly favoured over another. */
    @Test
    fun `a shortfall is shared across the kinds in proportion`() {
        // 300 certain and 200 doubtful, less 100 of Qarz-e-Hasna repaid:
        // 400 collectable out of 500 in credit, so each keeps four fifths.
        val inputs = Zakat.fromParties(
            listOf(party(1, certain = 300.0, doubtful = 200.0, qarz = -100.0))
        )

        assertEquals(240.0, inputs.certainReceivables, cents)
        assertEquals(160.0, inputs.doubtfulReceivables, cents)
        assertEquals(0.0, inputs.qarzeHasnaGiven, cents)
        assertEquals(0.0, inputs.payables, cents)
        assertEquals(
            "the customer's own total is the ceiling",
            400.0,
            inputs.certainReceivables + inputs.doubtfulReceivables + inputs.qarzeHasnaGiven,
            cents
        )
    }

    /** A customer the shop owes is a liability whole, whatever the kinds say. */
    @Test
    fun `a customer in the red is deducted and yields nothing`() {
        val inputs = Zakat.fromParties(
            listOf(party(1, certain = 100.0, doubtful = -400.0))
        )

        assertEquals(0.0, inputs.certainReceivables, cents)
        assertEquals(0.0, inputs.doubtfulReceivables, cents)
        assertEquals(300.0, inputs.payables, cents)
    }

    /** A settled customer is neither. */
    @Test
    fun `a settled customer counts for nothing either way`() {
        val inputs = Zakat.fromParties(listOf(party(1, certain = 0.0)))
        assertEquals(0.0, inputs.certainReceivables, cents)
        assertEquals(0.0, inputs.payables, cents)
    }

    /** An empty book is not an error. */
    @Test
    fun `an empty book gives zeroes`() {
        val inputs = Zakat.fromParties(emptyList())
        assertEquals(0.0, inputs.certainReceivables, cents)
        assertEquals(0.0, inputs.doubtfulReceivables, cents)
        assertEquals(0.0, inputs.qarzeHasnaGiven, cents)
        assertEquals(0.0, inputs.payables, cents)
    }

    /** Doubtful debts stay out unless the owner asks for them. */
    @Test
    fun `doubtful debts are excluded by default and counted on request`() {
        val inputs = Zakat.fromParties(
            listOf(party(1, certain = 1000.0), party(2, doubtful = 500.0))
        )

        assertEquals(1000.0, Zakat.zakatableWealth(inputs, 0.0), cents)
        assertEquals(1500.0, Zakat.zakatableWealth(inputs, 0.0, includeDoubtful = true), cents)
    }

    /** Owing more than you hold is not negative wealth. */
    @Test
    fun `wealth never goes below zero`() {
        val inputs = Zakat.fromParties(listOf(party(1, certain = -900.0)))
        assertEquals(0.0, Zakat.zakatableWealth(inputs, cashAndStock = 100.0), cents)
    }

    // ------------------------------------------------------------------ nisab

    /**
     * The unit the owner will actually have a number for. A sarafa bazaar
     * quotes per tola and nothing else, and asking for a per-gram figure is
     * what produced a silver price of 66 on the owner's own screen.
     */
    @Test
    fun `the same rate gives the same nisab in either unit`() {
        // Roughly today's silver: about Rs 6,300 a tola, about Rs 540 a gram.
        val byTola = Zakat.nisab(6300.0, gold = false, perTola = true)
        val byGram = Zakat.nisab(6300.0 / Zakat.GRAMS_PER_TOLA, gold = false, perTola = false)

        assertEquals(byTola, byGram, 50.0)
        assertEquals(330_750.0, byTola, 1.0)
    }

    /** Gold is the higher threshold, by roughly ten times at today's rates. */
    @Test
    fun `gold nisab is far above silver nisab`() {
        val silver = Zakat.nisab(6300.0, gold = false, perTola = true)
        val gold = Zakat.nisab(432_500.0, gold = true, perTola = true)

        assertEquals(3_243_750.0, gold, 1.0)
        assertTrue("gold should tower over silver", gold > silver * 5)
    }

    /** The weights themselves, in whichever unit is being quoted. */
    @Test
    fun `the nisab weight is stated in the unit asked for`() {
        assertEquals(52.5, Zakat.nisabWeight(gold = false, perTola = true), 0.0001)
        assertEquals(7.5, Zakat.nisabWeight(gold = true, perTola = true), 0.0001)
        assertEquals(612.36, Zakat.nisabWeight(gold = false, perTola = false), 0.0001)
        assertEquals(87.48, Zakat.nisabWeight(gold = true, perTola = false), 0.0001)
    }

    /** No price, no nisab — and no pretending otherwise. */
    @Test
    fun `no price gives no nisab`() {
        assertEquals(0.0, Zakat.nisab(0.0, gold = false, perTola = true), cents)
        assertEquals(0.0, Zakat.nisab(-5.0, gold = true, perTola = false), cents)
    }

    // ------------------------------------------------------- the wrong unit

    /** The mistake that actually happened, on the owner's own phone. */
    @Test
    fun `66 for silver is flagged whichever unit is chosen`() {
        assertTrue(Zakat.priceLooksOff(66.0, gold = false, perTola = true))
        assertTrue(Zakat.priceLooksOff(66.0, gold = false, perTola = false))
    }

    /** A tola figure typed into the gram box, and the reverse. */
    @Test
    fun `a rate out by an order of magnitude is flagged`() {
        // Silver quoted per tola, entered as though per gram.
        assertTrue(Zakat.priceLooksOff(6300.0, gold = false, perTola = false))
        // Gold quoted per tola, entered as though per gram.
        assertTrue(Zakat.priceLooksOff(432_500.0, gold = true, perTola = false))
        // Gold quoted per gram, entered as though per tola.
        assertTrue(Zakat.priceLooksOff(37_000.0, gold = true, perTola = true))
    }

    /** And a rate that is simply correct is left alone. */
    @Test
    fun `a sensible rate is not flagged`() {
        assertFalse(Zakat.priceLooksOff(6300.0, gold = false, perTola = true))
        assertFalse(Zakat.priceLooksOff(540.0, gold = false, perTola = false))
        assertFalse(Zakat.priceLooksOff(432_500.0, gold = true, perTola = true))
        assertFalse(Zakat.priceLooksOff(37_000.0, gold = true, perTola = false))
    }

    /**
     * The bands are a unit smell test, not a price check, so they have to sit
     * still while prices move. Silver at five times today's rate, and at a
     * fifth of it, must both still pass.
     */
    @Test
    fun `the check survives prices moving`() {
        assertFalse(Zakat.priceLooksOff(6300.0 * 5, gold = false, perTola = true))
        assertFalse(Zakat.priceLooksOff(6300.0 / 5, gold = false, perTola = true))
        assertFalse(Zakat.priceLooksOff(432_500.0 * 4, gold = true, perTola = true))
        assertFalse(Zakat.priceLooksOff(432_500.0 / 4, gold = true, perTola = true))
    }

    /** An empty box is not an error to shout about. */
    @Test
    fun `nothing typed is not flagged`() {
        assertFalse(Zakat.priceLooksOff(0.0, gold = false, perTola = true))
    }
}
