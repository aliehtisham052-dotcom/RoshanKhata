package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A ticket that comes back whole credits exactly what was paid (3 Nov 2026 audit, M07). Each line's paid rate
 * rounded on its own made 3 × 100 with 20 off come back as 279.99 of the 280 paid. Made-up figures.
 */
class ReturnTotalTest {

    private val sale = Sale(id = 7, subtotal = 300.0, discountAmount = 20.0, grandTotal = 280.0, paidTotal = 280.0)
    private val items = (1L..3L).map { SaleItem(id = it, saleId = 7, itemName = "Item $it", quantity = 1.0, rate = 100.0) }

    private fun back(vararg ids: Long) = ids.map { id ->
        val item = items.single { it.id == id }
        SaleReturnItem(returnId = 0, saleItemId = id, itemName = item.itemName, quantity = 1.0, rate = ReturnMath.paidRate(sale, item))
    }

    @Test
    fun `the whole ticket back in one return credits what was paid`() {
        val left = items.associate { it.id to 1.0 }
        assertEquals(280.0, ReturnMath.totalFor(back(1, 2, 3), sale, left, returnedBefore = 0.0), 0.0)
    }

    @Test
    fun `back in two returns, the last credits what is left, so the two add up to what was paid`() {
        val first = ReturnMath.totalFor(back(1, 2), sale, items.associate { it.id to 1.0 }, returnedBefore = 0.0)
        assertEquals("two lines at 93.33, as before", 186.66, first, 0.0)
        val last = ReturnMath.totalFor(back(3), sale, mapOf(1L to 0.0, 2L to 0.0, 3L to 1.0), returnedBefore = first)
        assertEquals(93.34, last, 0.000001)
        assertEquals(280.0, first + last, 0.000001)
    }

    @Test
    fun `a part return is its lines, rounded as before`() {
        assertEquals(93.33, ReturnMath.totalFor(back(2), sale, items.associate { it.id to 1.0 }, returnedBefore = 0.0), 0.0)
        assertEquals("no ticket: its lines", 93.33, ReturnMath.totalFor(back(2), null, emptyMap(), 0.0), 0.0)
    }
}
