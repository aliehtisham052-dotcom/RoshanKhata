package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A WhatsApp order read into a sales order's lines (3 Nov 2026), from the shop's own products only. */
class OrderPasteTest {

    private fun product(id: Long, name: String, unit: String?, cash: Double?) =
        Product(id = id, name = name, nameKey = name.lowercase(), normalisedName = name.lowercase(), defaultUnit = unit, salePrice = cash)

    private val shelf = listOf(
        product(1, "DAP 50 kg", "bag", 13_500.0),
        product(2, "DAP 25 kg", "bag", 7_000.0),
        product(3, "Confidor 250 ml", "bottle", 1_250.0),
        product(4, "Urea", "bag", 4_500.0)
    )

    @Test
    fun `a message becomes one line per item, its greeting left out`() {
        val lines = OrderPaste.parse("Assalam o alaikum bhai\n2 bori DAP, 1 Confidor\nteen urea\n5 kg cotton seed", shelf)
        assertEquals(listOf("DAP 50 kg", "Confidor 250 ml", "Urea", "cotton seed"), lines.map { it.name })
        assertEquals(listOf(2.0, 1.0, 3.0, 5.0), lines.map { it.quantity })
        // "bori" is the bag; a line with no unit takes its product's.
        assertEquals(listOf("bag", "bottle", "bag", "kg"), lines.map { it.unit })
        assertEquals(listOf(13_500.0, 1_250.0, 4_500.0, 0.0), lines.map { it.rate })
        // What the shop does not stock keeps the customer's words, with no product.
        assertNull(lines.last().product)
    }

    @Test
    fun `the size written picks the product of that size`() {
        val line = OrderPaste.parse("DAP 25 kg 4 bori", shelf).single()
        assertEquals(2L, line.product?.id)
        assertEquals(4.0, line.quantity, 0.0)
        assertEquals("bag", line.unit)
    }

    @Test
    fun `figures in other scripts and a short word still find the product`() {
        val line = OrderPaste.parse("۳ بوتل confidor", shelf).single()
        assertEquals(3L, line.product?.id)
        assertEquals(3.0, line.quantity, 0.0)
        assertEquals("bottle", line.unit)
        assertTrue(OrderPaste.parse("   \n , ;", shelf).isEmpty())
    }
}
