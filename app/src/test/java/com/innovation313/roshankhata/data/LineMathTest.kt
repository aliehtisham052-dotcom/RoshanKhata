package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The rate a line goes at, and the money it comes to, must agree to the paisa.
 *
 * A plain Double multiply gives 3 × 1,950.10 = 5850.299999999999. Put into the
 * amount box, or summed across lines, that residue becomes a figure the owner
 * never typed. These pin the one place the arithmetic is done.
 */
class LineMathTest {

    @Test
    fun `a decimal rate comes to the exact paisa, not a residue`() {
        // The raw Double product really is off — this is the bug being guarded.
        assertEquals(5850.299999999999, 3 * 1950.1, 0.0)
        assertEquals(5850.30, LineMath.lineTotal(3.0, 1950.1)!!, 0.0)
    }

    @Test
    fun `whole rates and fractional quantities come out exact`() {
        assertEquals(9600.0, LineMath.lineTotal(2.0, 4800.0)!!, 0.0)
        assertEquals(4499.25, LineMath.lineTotal(2.5, 1799.7)!!, 0.0)
        assertEquals(585.0, LineMath.lineTotal(0.3, 1950.0)!!, 0.0)
    }

    @Test
    fun `a bonus line costs nothing whatever its rate`() {
        assertEquals(0.0, LineMath.lineTotal(1.0, 750.0, isBonus = true)!!, 0.0)
    }

    @Test
    fun `a missing rate or quantity is unknown, never zero`() {
        assertNull(LineMath.lineTotal(2.0, null))
        assertNull(LineMath.lineTotal(null, 4800.0))
    }

    @Test
    fun `lines add up exactly, and one unknown line makes the total unknown`() {
        val visit = listOf(
            EntryItem(entryId = 1, itemName = "Urea", quantity = 2.0, rate = 4800.0),
            EntryItem(entryId = 1, itemName = "Sulphur", quantity = 5.0, rate = 750.0),
            EntryItem(entryId = 1, itemName = "Chlorpyrifos", quantity = 3.0, rate = 1950.0)
        )
        assertEquals(19200.0, LineMath.linesTotal(visit)!!, 0.0)

        val withBonus = visit + EntryItem(entryId = 1, itemName = "Sulphur", quantity = 1.0, rate = 750.0, isBonus = true)
        assertEquals(19200.0, LineMath.linesTotal(withBonus)!!, 0.0)

        val oneUnknown = visit + EntryItem(entryId = 1, itemName = "DAP", quantity = 1.0, rate = null)
        assertNull(LineMath.linesTotal(oneUnknown))
    }

    @Test
    fun `rounding is half-up to the paisa`() {
        assertEquals(10.01, LineMath.round(10.005), 0.0)
        assertEquals(10.0, LineMath.round(10.004), 0.0)
    }

    @Test
    fun `the goods predicate is the same for the form and for old entries`() {
        // Nothing about goods: no line.
        assertNull(EntryItem.ofGoods(null, null, null, null, null))
        assertNull(EntryItem.ofGoods("  ", null, "bori", null, null))
        // Any one fact about goods: a line.
        assertEquals("Urea", EntryItem.ofGoods("Urea", null, null, null, null)!!.itemName)
        assertEquals(2.0, EntryItem.ofGoods(null, 2.0, null, null, null)!!.quantity!!, 0.0)
        assertEquals(7L, EntryItem.ofGoods(null, null, null, 7L, null)!!.productId)
        assertEquals(9L, EntryItem.ofGoods(null, null, null, null, 9L)!!.billItemId)

        val old = LedgerEntry(
            id = 42, partyId = 1, amount = 9600.0, isGiven = true, entryNumber = "RK-000042",
            itemName = "Urea", quantity = 2.0, unit = "bori"
        )
        val line = EntryItem.fromLegacy(old)!!
        assertEquals(42L, line.entryId)
        assertEquals("bori", line.unit)
        assertNull("an old entry never recorded a rate; none is invented", line.rate)

        val moneyOnly = LedgerEntry(id = 43, partyId = 1, amount = 500.0, isGiven = false, entryNumber = "RK-000043")
        assertNull(EntryItem.fromLegacy(moneyOnly))
    }
}
