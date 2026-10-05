package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the rate list is allowed to put in front of a customer.
 *
 * Only selling prices, only products that have one, only what the owner
 * left ticked — and pages that never cut a row in half.
 */
class RateListTest {

    private var nextId = 1L
    private fun product(
        name: String, company: String? = null, cash: Double? = null, credit: Double? = null,
        unit: String? = "bag", deleted: Boolean = false
    ) = Product(
        id = nextId++, name = name, nameKey = ProductName.key(name), normalisedName = ProductName.normalised(name),
        company = company, defaultUnit = unit, salePrice = cash, creditPrice = credit, isDeleted = deleted
    )

    @Test
    fun `only priced, undeleted products, in name order`() {
        val list = listOf(
            product("Urea", cash = 4200.0),
            product("DAP", credit = 13500.0),
            product("Sulphur"),                          // no price
            product("Old spray", cash = 900.0, deleted = true),
            product("Zinc", cash = 0.0, credit = 0.0)     // zero is not a price
        )
        assertEquals(listOf("DAP", "Urea"), RateList.rows(list, null, emptySet()).map { it.name })
        assertEquals(2, RateList.unpricedCount(list))
    }

    @Test
    fun `a company is one company whatever its spacing or case`() {
        val list = listOf(
            product("A", company = "Engro", cash = 1.0),
            product("B", company = " engro ", cash = 1.0),
            product("C", company = "FFC", cash = 1.0),
            product("D", company = "Bayer")              // unpriced: not offered
        )
        assertEquals(listOf("Engro", "FFC"), RateList.companies(list))
        assertEquals(listOf("A", "B"), RateList.rows(list, "ENGRO", emptySet()).map { it.name })
    }

    @Test
    fun `unticked products stay off`() {
        val urea = product("Urea", cash = 4200.0)
        val dap = product("DAP", cash = 13000.0)
        assertEquals(listOf("Urea"), RateList.rows(listOf(urea, dap), null, setOf(dap.id)).map { it.name })
    }

    @Test
    fun `the owner's example - Urea, cash 4200, credit 4500`() {
        val row = RateList.rows(listOf(product("Urea 50 kg", cash = 4200.0, credit = 4500.0)), null, emptySet()).single()
        assertEquals(4200.0, row.cash!!, 0.0)
        assertEquals(4500.0, row.credit!!, 0.0)
        assertEquals("bag", row.unit)
    }

    @Test
    fun `a column nobody has a price for drops out`() {
        val rows = RateList.rows(listOf(product("Urea", cash = 4200.0)), null, emptySet())
        assertEquals(RateList.Columns(cash = true, credit = false), RateList.columns(rows, true, true))
    }

    @Test
    fun `the owner switching off the only priced column keeps it - a list needs prices`() {
        val rows = RateList.rows(listOf(product("Urea", cash = 4200.0)), null, emptySet())
        assertEquals(RateList.Columns(cash = true, credit = false), RateList.columns(rows, false, true))
    }

    @Test
    fun `on a cash-only list a credit-only product is left off`() {
        val rows = RateList.rows(listOf(product("Urea", cash = 4200.0), product("DAP", credit = 13500.0)), null, emptySet())
        val cols = RateList.columns(rows, true, false)
        assertTrue(cols.cash)
        assertFalse(cols.credit)
        assertEquals(listOf("Urea"), RateList.visible(rows, cols).map { it.name })
    }

    @Test
    fun `pages take whole rows and never split one`() {
        assertEquals(listOf(0..2, 3..4), RateList.paginate(listOf(100, 100, 100, 100, 100), 300))
        assertEquals(listOf(0..0, 1..1, 2..2), RateList.paginate(listOf(500, 250, 250), 300))
        assertEquals(emptyList<IntRange>(), RateList.paginate(emptyList(), 300))
    }

    @Test
    fun `a row taller than a page gets a page of its own instead of being cut`() {
        assertEquals(listOf(0..0, 1..1, 2..3), RateList.paginate(listOf(50, 900, 100, 100), 300))
    }
}
