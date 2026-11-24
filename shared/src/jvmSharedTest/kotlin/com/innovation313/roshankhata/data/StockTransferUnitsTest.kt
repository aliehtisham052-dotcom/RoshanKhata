package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * BC-271 (24 Nov 2026): a stock transfer between the owner's two shops converts units by the units contract ([Units]) like
 * the counter and the shelf: one name per unit, the stated pack conversion, quantities to six places. Invented products.
 */
class StockTransferUnitsTest {

    private fun product(unit: String?, packUnit: String? = null, packSize: Double? = null) =
        Product(id = 1, name = "Urea", nameKey = "urea", normalisedName = "urea", defaultUnit = unit, packUnit = packUnit, packSize = packSize)

    @Test fun `one unit written two ways is one unit`() {
        assertEquals(7.0, StockTransfer.inUnitOf(product("kg"), 7.0, product("KG"))!!, 0.0)
        assertEquals(7.0, StockTransfer.inUnitOf(product(null), 7.0, product(null))!!, 0.0)
    }

    @Test fun `bags sent to a shop that counts kilos arrive as kilos`() {
        val kiloShop = product("kg", packUnit = "bag", packSize = 50.0)
        assertEquals(100.0, StockTransfer.inUnitOf(product("bag"), 2.0, kiloShop)!!, 0.0)
    }

    @Test fun `kilos sent to a shop that counts bags arrive as bags`() {
        val kiloHere = product("kg", packUnit = "bag", packSize = 50.0)
        assertEquals(2.0, StockTransfer.inUnitOf(kiloHere, 100.0, product("bag"))!!, 0.0)
        assertEquals(0.5, StockTransfer.inUnitOf(kiloHere, 25.0, product("bag"))!!, 0.0)
    }

    @Test fun `units that do not convert are not guessed`() {
        assertNull(StockTransfer.inUnitOf(product("bag"), 2.0, product("litre")))
        assertNull(StockTransfer.inUnitOf(product("kg", "bag", 50.0), 2.0, product("litre")))
    }
}
