package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The units and stock-valuation contract written on [Units] (24 Nov 2026, plan B11, BC-253), held by its parts together:
 * the unit's name, a pack's conversion, mixed units (no figure rather than a wrong one), the cost rule, quantity rounding
 * (never the money's decimals), a return of a pack sold as pieces, a product with no cost, and the stock value.
 * Invented products and figures only; plain Kotlin (no database): the rules are the ones every screen calls.
 */
class UnitsContractTest {

    private fun product(id: Long, name: String, unit: String?, packUnit: String? = null, packSize: Double? = null, cost: Double? = null) =
        Product(id = id, name = name, nameKey = name.lowercase(), normalisedName = name.lowercase(), defaultUnit = unit,
            packUnit = packUnit, packSize = packSize, costPrice = cost)

    /** Soap in pieces, a carton of 12. */
    private val soap = product(1, "Soap", "piece", "carton", 12.0)
    /** Rice in kilos, a bag of 50. */
    private val rice = product(2, "Rice", "kg", "bag", 50.0)
    /** Urea in bags with no pack: kilos cannot be turned into bags. */
    private val urea = product(3, "Urea", "bag")

    private fun qty(id: Long, q: Double, unit: String?) = Stock.ProductQty(id, q, unit)

    // ---- 1 and 2: the unit's name and a pack's conversion --------------------------------------------------

    @Test
    fun aUnitIsOneNameInEveryLanguageAndCase() {
        assertEquals("kg", Units.norm(" KG "))
        assertEquals("kg", Units.norm("کلو"))
        assertEquals("kg", Units.norm("किलो"))
        assertEquals("tin", Units.norm("Tin")) // the owner's own word stays his own, lower-cased
        assertNull(Units.norm("  "))
        assertNull(Units.norm(null))
        assertTrue(Units.agree("Kg", "کلو"))
        assertTrue(Units.agree(null, "kg"))   // a missing unit disagrees with nothing
        assertTrue(Units.agree("kg", ""))
        assertFalse(Units.agree("bag", "kg"))
        // A pack is found by any spelling of its unit.
        assertTrue(Units.isPack(rice, "BAG"))
        assertTrue(Units.isPack(rice, "بوری"))
        assertFalse(Units.isPack(rice, "kg"))
    }

    @Test
    fun aPackOf12AndAPackOf50TurnIntoTheOwnUnitAndBack() {
        assertEquals(36.0 to "piece", Units.toBase(soap, 3.0, "carton"))
        assertEquals(100.0 to "kg", Units.toBase(rice, 2.0, "bag"))
        assertEquals(5.0 to "piece", Units.toBase(soap, 5.0, "piece"))
        // A unit with no stated conversion is left as written: no guess how many kilos a bag of urea holds.
        assertEquals(40.0 to "kg", Units.toBase(urea, 40.0, "kg"))
        assertEquals(3.0, Units.fromBase(soap, 36.0, "carton"), 0.0)
        assertEquals(1.5, Units.fromBase(rice, 75.0, "bag"), 0.0)
        assertEquals(7.0, Units.fromBase(soap, 7.0, "piece"), 0.0)
        // Packs convert to the millionth, not to a float's dust: 0.1 carton is 1.2 pieces.
        assertEquals(1.2, Units.toBase(soap, 0.1, "carton").first, 0.0)
        assertEquals(12.0, Units.factor(soap, "carton"), 0.0)
        assertEquals(1.0, Units.factor(soap, "piece"), 0.0)
    }

    // ---- 5: a quantity is not money ------------------------------------------------------------------------

    @Test
    fun aQuantityKeepsSixPlacesHalfUpWhateverTheMoneyDecimalsAre() {
        assertEquals(1.000001, Units.roundQty(1.0000005), 0.0)
        assertEquals(-1.000001, Units.roundQty(-1.0000005), 0.0)
        assertEquals(0.3, Units.roundQty(0.1 + 0.2), 0.0)
        assertEquals(0.125, Units.roundQty(0.125), 0.0)
        assertTrue(Units.roundQty(Double.NaN).isNaN())
        assertEquals(Double.POSITIVE_INFINITY, Units.roundQty(Double.POSITIVE_INFINITY), 0.0)
        assertEquals(6, Units.QTY_DECIMALS)
        // The same figure through the money's rule would lose it (whole-rupiah shops keep no decimals at all).
        assertEquals(0.0, Money.round(0.4, 0), 0.0)
        assertEquals(0.4, Units.roundQty(0.4), 0.0)
    }

    @Test
    fun aCountWritesTheDifferenceInQuantityNotInCoins() {
        assertEquals(0.4, StockCount.difference(10.0, 10.4)!!, 0.0)       // a kilo shop with no money decimals
        assertEquals(-0.125, StockCount.difference(5.0, 4.875)!!, 0.0)    // not -0.13
        assertNull(StockCount.difference(5.0, 5.0))
        assertNull(StockCount.difference(0.3, 0.1 + 0.2))                 // float dust is no difference
        assertNull(StockCount.difference(5.0, -1.0))                      // never below nothing
        assertNull(StockCount.difference(Double.NaN, 3.0))
    }

    // ---- 3: stock, mixed units, returns -------------------------------------------------------------------

    @Test
    fun packsOf12And50AddUpAndAMixedSideGivesNoFigure() {
        val s = Stock.combine(listOf(soap, rice),
            bought = listOf(qty(1, 3.0, "carton"), qty(1, 4.0, "piece"), qty(2, 4.0, "bag")),
            sold = listOf(qty(1, 10.0, "piece"), qty(1, 1.0, "carton"), qty(2, 30.0, "kg")),
            returned = emptyList()).associateBy { it.productId }
        assertEquals(18.0, s[1]!!.onHand!!, 0.0)   // 36 + 4 - 10 - 12
        assertEquals(170.0, s[2]!!.onHand!!, 0.0)  // 200 - 30
        assertEquals("piece", s[1]!!.unit)
        // Bags and kilos with no pack between them: no figure, rather than a wrong one.
        val mixed = Stock.combine(listOf(urea), listOf(qty(3, 10.0, "bag"), qty(3, 50.0, "kg")), emptyList(), emptyList()).single()
        assertNull(mixed.onHand)
        val sold = Stock.combine(listOf(urea), listOf(qty(3, 10.0, "bag")), listOf(qty(3, 5.0, "kg")), emptyList()).single()
        assertNull(sold.onHand)
        // A unit's other spelling is the same unit.
        val alias = Stock.combine(listOf(rice), listOf(qty(2, 100.0, "kg")), listOf(qty(2, 30.0, "کلو")), emptyList()).single()
        assertEquals(70.0, alias.onHand!!, 0.0)
    }

    @Test
    fun aReturnOfAPackSoldAsPiecesAndOfPiecesSoldAsAPackIsOneConversion() {
        // 5 cartons in (60), 2 cartons sold (24), 5 pieces back: 41.
        val a = Stock.combine(listOf(soap), listOf(qty(1, 5.0, "carton")), listOf(qty(1, 2.0, "carton")), listOf(qty(1, 5.0, "piece"))).single()
        assertEquals(41.0, a.onHand!!, 0.0)
        // 60 in, 30 pieces sold, one carton back: 42.
        val b = Stock.combine(listOf(soap), listOf(qty(1, 60.0, "piece")), listOf(qty(1, 30.0, "piece")), listOf(qty(1, 1.0, "carton"))).single()
        assertEquals(42.0, b.onHand!!, 0.0)
        // A walk-in's return counts the same way as a khata one.
        val c = Stock.combine(listOf(soap), listOf(qty(1, 60.0, "piece")), emptyList(), emptyList(),
            walkIn = listOf(qty(1, 1.0, "carton")), walkInReturned = listOf(qty(1, 3.0, "piece"))).single()
        assertEquals(51.0, c.onHand!!, 0.0)
        // Goods back in a unit nobody can convert: the shelf is not guessed.
        val d = Stock.combine(listOf(soap), listOf(qty(1, 60.0, "piece")), listOf(qty(1, 10.0, "piece")), listOf(qty(1, 1.0, "box"))).single()
        assertNull(d.onHand)
    }

    @Test
    fun aCountedShelfLeavesNoFloatDust() {
        // 0.3 in, 0.1 and 0.2 out: nothing is left, exactly (it was -5.5e-17).
        val s = Stock.combine(listOf(rice), listOf(qty(2, 0.3, "kg")), listOf(qty(2, 0.1, "kg"), qty(2, 0.2, "kg")), emptyList()).single()
        assertEquals(0.0, s.onHand!!, 0.0)
        // 0.1 bought and 0.2 returned against 0.3 sold: the sides are whole, their sum 0.30000000000000004 is not.
        val back = Stock.combine(listOf(rice), listOf(qty(2, 0.1, "kg")), listOf(qty(2, 0.3, "kg")), listOf(qty(2, 0.2, "kg"))).single()
        assertEquals(0.0, back.onHand!!, 0.0)
        // An adjustment (a count's difference, a write-off) is on the bought side, signed, in its own unit.
        val adj = Stock.combine(listOf(soap), listOf(qty(1, 5.0, "carton")), listOf(qty(1, 20.0, "piece")), emptyList(),
            adjusted = listOf(qty(1, -4.0, "piece"))).single()
        assertEquals(36.0, adj.onHand!!, 0.0)
    }

    // ---- 4: the cost rule ---------------------------------------------------------------------------------

    private fun rule(p: Product, purchases: List<Cost.Purchase>, newestFirst: List<Cost.Purchase> = purchases.reversed()) =
        Cost.rule(p, { purchases }, { newestFirst })

    @Test
    fun theCostIsTheOwnersThenTheWeightedAverageThenTheLastRateInOwnUnit() {
        val bought = listOf(Cost.Purchase(100.0, "kg", 10.0), Cost.Purchase(100.0, "kg", 12.0))
        assertEquals(11.0, rule(rice, bought)!!, 1e-9)
        // The owner's own cost comes first, and the lists are not even read.
        var read = 0
        assertEquals(9.5, Cost.rule(product(2, "Rice", "kg", cost = 9.5), { read++; bought }, { read++; bought })!!, 0.0)
        assertEquals(0, read)
        // Packs: 2 cartons at 1,080 and 12 pieces at 96 weigh as 36 pieces: 92 a piece.
        assertEquals(92.0, rule(soap, listOf(Cost.Purchase(2.0, "carton", 1_080.0), Cost.Purchase(12.0, "piece", 96.0)))!!, 1e-9)
        // A bag of 50 kg at 4,500 is 90 a kilo, as the last rate and as the average.
        assertEquals(90.0, rule(rice, listOf(Cost.Purchase(1.0, "bag", 4_500.0)))!!, 1e-9)
        // Only a line in a unit that cannot be turned is left out, never read as the own unit's.
        assertEquals(96.0, rule(soap, listOf(Cost.Purchase(12.0, "piece", 96.0), Cost.Purchase(1.0, "box", 5_000.0)))!!, 1e-9)
        assertNull(rule(urea, listOf(Cost.Purchase(1.0, "kg", 90.0))))
    }

    @Test
    fun theLastRateOfAPackIsTheRateOfOneOwnUnit() {
        // Newest first: a bag of 50 kg at 4,500 is 90 a kilo, a carton of 12 at 1,080 is 90 a piece; never the pack's price as the unit's.
        assertEquals(90.0, Cost.lastInOwnUnit(rice, listOf(Cost.Purchase(1.0, "bag", 4_500.0), Cost.Purchase(5.0, "kg", 80.0)))!!, 1e-9)
        assertEquals(90.0, Cost.lastInOwnUnit(soap, listOf(Cost.Purchase(1.0, "carton", 1_080.0)))!!, 1e-9)
        // A newer rate in the own unit wins over an older pack.
        assertEquals(80.0, Cost.lastInOwnUnit(rice, listOf(Cost.Purchase(5.0, "kg", 80.0), Cost.Purchase(1.0, "bag", 4_500.0)))!!, 0.0)
    }

    @Test
    fun aProductWithNoCostHasNone() {
        assertNull(rule(soap, emptyList()))
        assertNull(rule(soap, listOf(Cost.Purchase(3.0, "piece", -1.0))))
    }

    @Test
    fun aProductWithNoOwnUnitDoesNotAverageBagsWithKilos() {
        val loose = product(4, "Seed", null)
        assertNull(Cost.average(loose, listOf(Cost.Purchase(10.0, "bag", 4_500.0), Cost.Purchase(50.0, "kg", 95.0))))
        assertNull(Cost.lastInOwnUnit(loose, listOf(Cost.Purchase(1.0, "kg", 95.0), Cost.Purchase(1.0, "bag", 4_500.0))))
        // One unit throughout (or none written) is fine.
        assertEquals(4_600.0, Cost.average(loose, listOf(Cost.Purchase(10.0, "bag", 4_500.0), Cost.Purchase(10.0, "bag", 4_700.0)))!!, 1e-9)
        assertEquals(95.0, Cost.lastInOwnUnit(loose, listOf(Cost.Purchase(1.0, null, 95.0), Cost.Purchase(1.0, "kg", 90.0)))!!, 0.0)
    }

    @Test
    fun theCostOfALinesUnitIsNeverTheOwnUnitsRead() {
        // 33.333333 a piece: a carton of 12 costs 400, a piece 33.333333.
        val perPiece = 400.0 / 12
        assertEquals(400.0, Cost.forUnit(soap, "carton", perPiece)!!, 0.0)
        assertEquals(33.333333, Cost.forUnit(soap, "piece", perPiece)!!, 0.0)
        assertEquals(33.333333, Cost.forUnit(soap, null, perPiece)!!, 0.0)
        // A kilo of urea whose own unit is the bag has no cost here: a bag's price is not a kilo's.
        assertNull(Cost.forUnit(urea, "kg", 4_500.0))
        assertNull(Cost.forUnit(soap, "piece", null))
        assertEquals(4_500.0, Cost.forUnit(urea, "bag", 4_500.0)!!, 0.0)
        assertEquals(2_000.0, Cost.forUnit(rice, "bag", 40.0)!!, 0.0)
    }

    // ---- 6: the value -------------------------------------------------------------------------------------

    private fun row(p: Product, s: Stock.ProductStock, cost: Double?) = Valuation.Row.of(s, p, cost)

    @Test
    fun aShelfIsValuedOnceInBigDecimalAndNeverBelowNothing() {
        val s = Stock.combine(listOf(rice), listOf(qty(2, 0.1, "kg")), emptyList(), emptyList()).single()
        // 0.1 x 0.35 is 0.035: 0.04 as every line total is (the Double product 0.034999999999999996 said 0.03).
        assertEquals(0.04, row(rice, s, 0.35).value!!, 0.0)
        assertEquals(0.04, Cost.value(0.1, 0.35), 0.0)
        assertEquals(0.0, Cost.value(-3.0, 40.0), 0.0)
        // An unknown cost or an unknown shelf is no value, not nothing.
        assertNull(row(rice, s, null).value)
        val mixed = Stock.combine(listOf(urea), listOf(qty(3, 10.0, "bag"), qty(3, 50.0, "kg")), emptyList(), emptyList()).single()
        assertNull(row(urea, mixed, 4_500.0).value)
        // Oversold stock is worth nothing, and the total is what the rows say.
        val over = Stock.combine(listOf(rice), listOf(qty(2, 10.0, "kg")), listOf(qty(2, 15.0, "kg")), emptyList()).single()
        assertEquals(0.0, row(rice, over, 40.0).value!!, 0.0)
        val v = Valuation(listOf(row(rice, s, 0.35), row(rice, over, 40.0), row(urea, mixed, 4_500.0)))
        assertEquals(0.04, v.total, 0.0)
        assertEquals(2, v.known)
    }

    @Test
    fun aShelfCountedInAnotherUnitThanTheCostIsPerHasNoValue() {
        // Urea is a bag product; its bills came in kilos and no pack ties them. 40 kg x a bag's cost is not money.
        val inKilos = Stock.combine(listOf(urea), listOf(qty(3, 40.0, "kg")), emptyList(), emptyList()).single()
        assertEquals(40.0, inKilos.onHand!!, 0.0)
        assertEquals("kg", inKilos.unit)
        assertNull(row(urea, inKilos, 4_500.0).cost)
        assertNull(row(urea, inKilos, 4_500.0).value)
        // In its own unit (or a spelling of it) it is valued.
        val inBags = Stock.combine(listOf(urea), listOf(qty(3, 4.0, "بوری")), emptyList(), emptyList()).single()
        assertEquals(18_000.0, row(urea, inBags, 4_500.0).value!!, 0.0)
    }

    @Test
    fun theValueIsTheSumOfItsLotsAtTheCostRule() {
        val cost = rule(rice, listOf(Cost.Purchase(2.0, "bag", 4_500.0), Cost.Purchase(100.0, "kg", 90.0)))!!
        assertEquals(90.0, cost, 1e-9)
        val stock = Stock.combine(listOf(rice),
            bought = listOf(qty(2, 2.0, "bag"), qty(2, 100.0, "kg")),
            sold = listOf(qty(2, 30.0, "kg")), returned = listOf(qty(2, 5.0, "kg"))).single()
        assertEquals(175.0, stock.onHand!!, 0.0)
        // Batch 1: 2 bags (100 kg) with 30 kg sold and 5 back; batch 2: 100 kg.
        val lots = Fefo.lotsOf(rice,
            listOf(Fefo.Batch(1, "A", 500, 2.0, "bag"), Fefo.Batch(2, "B", 900, 100.0, "kg")),
            mapOf(1L to listOf(Fefo.Move(1, 30.0, "kg"), Fefo.Move(1, -5.0, "kg"))), stock.onHand)
        assertEquals(listOf(75.0, 100.0), lots.map { it.left })
        val row = row(rice, stock, cost)
        assertEquals(Cost.value(lots.sumOf { it.left }, cost), row.value!!, 0.0)
        assertEquals(15_750.0, row.value!!, 0.0)
        // Sold with no batch named: the batches are capped by the shelf, so the lots still add up to what it holds.
        val untagged = Stock.combine(listOf(rice), listOf(qty(2, 2.0, "bag"), qty(2, 100.0, "kg")), listOf(qty(2, 30.0, "kg")), emptyList()).single()
        val capped = Fefo.lotsOf(rice,
            listOf(Fefo.Batch(1, "A", 500, 2.0, "bag"), Fefo.Batch(2, "B", 900, 100.0, "kg")), emptyMap(), untagged.onHand)
        assertEquals(170.0, capped.sumOf { it.left }, 0.0)
        assertEquals(Cost.value(capped.sumOf { it.left }, cost), row(rice, untagged, cost).value!!, 0.0)
        assertNotNull(untagged.unit)
    }
}
