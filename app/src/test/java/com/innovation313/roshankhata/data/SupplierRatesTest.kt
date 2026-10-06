package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupplierRatesTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 400 * day

    private fun line(rate: Double, at: Long, bill: Long, supplier: Long, unit: String? = "16 kg", product: Long? = 1) =
        SupplierRates.Line(product, "Ghee", unit, rate, at, bill, supplier, "S$supplier")

    @Test
    fun `the owner's example - dearer this time and cheaper elsewhere`() {
        val rows = SupplierRates.rows(listOf(
            line(8_950.0, now - 20 * day, bill = 1, supplier = 2),
            line(9_000.0, now - 10 * day, bill = 2, supplier = 1),
            line(9_200.0, now - 1 * day, bill = 3, supplier = 1)
        ), now)
        val r = rows.single()
        assertEquals(9_200.0, r.last.rate, 0.001)
        assertEquals(200.0, r.change, 0.001)
        assertEquals(2.22, r.percent, 0.01)
        assertEquals("S2", r.cheaperElsewhere!!.supplierName)
        assertEquals(8_950.0, r.cheaperElsewhere!!.rate, 0.001)
        assertTrue(r.needsLook)
    }

    @Test
    fun `different units are different products`() {
        val rows = SupplierRates.rows(listOf(line(9_000.0, now - 2 * day, 1, 1, unit = "16 kg"), line(600.0, now - 1 * day, 2, 2, unit = "kg")), now)
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.previous == null && it.cheaperElsewhere == null })
    }

    @Test
    fun `an old cheaper price is not an offer`() {
        val r = SupplierRates.rows(listOf(line(8_000.0, now - 300 * day, 1, 2), line(9_000.0, now - 1 * day, 2, 1)), now).single()
        assertNull(r.cheaperElsewhere)
    }

    @Test
    fun `a fall is not a warning, unpriced lines are ignored, the cheapest other supplier wins`() {
        val r = SupplierRates.rows(listOf(
            line(9_500.0, now - 9 * day, 1, 1), line(9_100.0, now - 8 * day, 2, 3),
            line(9_050.0, now - 7 * day, 3, 2), line(0.0, now - 6 * day, 4, 4),
            line(9_300.0, now - 1 * day, 5, 1)
        ), now).single()
        assertEquals(250.0, r.change, 0.001)              // against bill 3 (9,050)
        assertEquals("S2", r.cheaperElsewhere!!.supplierName)
        val fell = SupplierRates.rows(listOf(line(9_500.0, now - 5 * day, 1, 1), line(9_000.0, now - 1 * day, 2, 1)), now).single()
        assertFalse(fell.needsLook)
    }
}
