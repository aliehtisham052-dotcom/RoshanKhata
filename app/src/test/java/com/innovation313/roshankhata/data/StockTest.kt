package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the stock figure is allowed to claim.
 *
 * Most of these are about the one case that matters: stock arriving in one
 * unit and leaving in another. The app must refuse to subtract those, and the
 * refusal must be visible rather than quietly reported as zero.
 */
class StockTest {

    private fun product(id: Long, name: String, unit: String? = null) =
        Product(id = id, name = name, nameKey = ProductName.key(name),
            normalisedName = ProductName.normalised(name), defaultUnit = unit)

    private fun qty(id: Long, q: Double, unit: String? = null) =
        Stock.ProductQty(productId = id, qty = q, unit = unit)

    @Test
    fun `bought minus sold, when both sides count the same way`() {
        val out = Stock.combine(
            products = listOf(product(1, "Urea")),
            bought = listOf(qty(1, 100.0, "bag")),
            sold = listOf(qty(1, 30.0, "bag")),
            returned = emptyList()
        ).single()

        assertEquals(70.0, out.onHand!!, 0.0001)
        assertEquals("bag", out.unit)
    }

    @Test
    fun `goods that came back go back on the shelf`() {
        val out = Stock.combine(
            products = listOf(product(1, "Urea")),
            bought = listOf(qty(1, 100.0, "bag")),
            sold = listOf(qty(1, 30.0, "bag")),
            returned = listOf(qty(1, 2.0, "bag"))
        ).single()

        assertEquals(72.0, out.onHand!!, 0.0001)
    }

    @Test
    fun `mismatched units refuse to produce a figure`() {
        // 40 bags in, 300 kg out. The honest answer is not -260.
        val out = Stock.combine(
            products = listOf(product(1, "Urea")),
            bought = listOf(qty(1, 40.0, "bag")),
            sold = listOf(qty(1, 300.0, "kg")),
            returned = emptyList()
        ).single()

        assertFalse(out.unitsAgree)
        assertNull("a figure was produced from units that cannot be subtracted", out.onHand)
        // Both sides remain readable on their own.
        assertEquals(40.0, out.boughtQty, 0.0001)
        assertEquals(300.0, out.soldQty, 0.0001)
    }

    @Test
    fun `a missing unit is not a disagreement`() {
        // Plenty of entries are written without a unit. Treating a blank as a
        // conflict would hide a figure the owner could have had.
        val out = Stock.combine(
            products = listOf(product(1, "Urea")),
            bought = listOf(qty(1, 100.0, "bag")),
            sold = listOf(qty(1, 30.0, null)),
            returned = emptyList()
        ).single()

        assertTrue(out.unitsAgree)
        assertEquals(70.0, out.onHand!!, 0.0001)
    }

    @Test
    fun `units differing only by case or spacing still agree`() {
        val out = Stock.combine(
            products = listOf(product(1, "Urea")),
            bought = listOf(qty(1, 10.0, "Bag")),
            sold = listOf(qty(1, 4.0, " bag ")),
            returned = emptyList()
        ).single()

        assertTrue(out.unitsAgree)
        assertEquals(6.0, out.onHand!!, 0.0001)
    }

    @Test
    fun `a product that has never moved reads as untouched, not as zero stock`() {
        val out = Stock.combine(
            products = listOf(product(1, "Karate")),
            bought = emptyList(), sold = emptyList(), returned = emptyList()
        ).single()

        assertTrue(out.isUntouched)
        assertEquals(0.0, out.onHand!!, 0.0001)
    }

    @Test
    fun `selling more than was bought reports a negative rather than hiding it`() {
        // Real, and worth seeing: it means stock arrived without a bill, or a
        // quantity was typed wrong. Clamping at zero would bury the mistake.
        val out = Stock.combine(
            products = listOf(product(1, "Urea")),
            bought = listOf(qty(1, 5.0, "bag")),
            sold = listOf(qty(1, 9.0, "bag")),
            returned = emptyList()
        ).single()

        assertEquals(-4.0, out.onHand!!, 0.0001)
    }

    @Test
    fun `each product gets its own totals and nothing bleeds across`() {
        val out = Stock.combine(
            products = listOf(product(1, "Urea"), product(2, "DAP"), product(3, "Karate")),
            bought = listOf(qty(1, 100.0, "bag"), qty(2, 50.0, "bag")),
            sold = listOf(qty(1, 20.0, "bag"), qty(3, 7.0, "bottle")),
            returned = listOf(qty(2, 1.0, "bag"))
        ).associateBy { it.name }

        assertEquals(80.0, out.getValue("Urea").onHand!!, 0.0001)
        assertEquals(51.0, out.getValue("DAP").onHand!!, 0.0001)
        assertEquals(-7.0, out.getValue("Karate").onHand!!, 0.0001)
    }

    @Test
    fun `the product's own default unit labels a side that recorded none`() {
        val out = Stock.combine(
            products = listOf(product(1, "Urea", unit = "bag")),
            bought = emptyList(),
            sold = listOf(qty(1, 3.0, "bag")),
            returned = emptyList()
        ).single()

        assertEquals("bag", out.boughtUnit)
        assertTrue(out.unitsAgree)
    }

    @Test
    fun `every product is returned, including ones with no movement at all`() {
        val out = Stock.combine(
            products = listOf(product(1, "Urea"), product(2, "DAP")),
            bought = listOf(qty(1, 10.0, "bag")),
            sold = emptyList(), returned = emptyList()
        )
        assertEquals(2, out.size)
    }
}
