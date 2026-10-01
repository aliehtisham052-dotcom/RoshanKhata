package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** A khata sale becomes invoice rows without inventing any price. */
class EntryInvoiceTest {

    private fun sale(amount: Double, itemName: String? = null, qty: Double? = null, unit: String? = null, note: String? = null) =
        LedgerEntry(id = 7, partyId = 1, amount = amount, isGiven = true, entryNumber = "RK-000007",
            itemName = itemName, quantity = qty, unit = unit, note = note)

    private fun line(no: Int, name: String?, qty: Double?, rate: Double?, unit: String? = null, bonus: Boolean = false) =
        EntryItem(id = no + 1L, entryId = 7, lineNo = no, itemName = name, quantity = qty, unit = unit, rate = rate, isBonus = bonus)

    @Test
    fun `priced lines keep their own rates, in the typed order`() {
        val rows = EntryInvoice.rows(sale(12000.0), listOf(
            line(1, "DAP", 1.0, 9000.0, "bag"),
            line(0, "Urea", 1.0, 3000.0, "bag")
        ))
        assertEquals(listOf("Urea", "DAP"), rows.map { it.itemName })
        assertEquals(listOf(3000.0, 9000.0), rows.map { it.rate })
        assertEquals("bag", rows[0].unit)
    }

    @Test
    fun `one unpriced line takes what is left of the amount`() {
        val rows = EntryInvoice.rows(sale(10000.0), listOf(
            line(0, "Urea", 2.0, 3000.0),
            line(1, "Spray", 2.0, null)
        ))
        assertEquals(2000.0, rows[1].rate, 0.0)
    }

    @Test
    fun `two unpriced lines are left at zero for the owner`() {
        val rows = EntryInvoice.rows(sale(10000.0), listOf(line(0, "A", 1.0, null), line(1, "B", 1.0, null)))
        assertEquals(listOf(0.0, 0.0), rows.map { it.rate })
    }

    @Test
    fun `a free bag is shown at rate zero and does not eat the leftover`() {
        val rows = EntryInvoice.rows(sale(6000.0), listOf(
            line(0, "Urea", 2.0, null),
            line(1, "Urea", 1.0, 3000.0, bonus = true)
        ))
        assertEquals(3000.0, rows[0].rate, 0.0)
        assertEquals(0.0, rows[1].rate, 0.0)
    }

    @Test
    fun `an entry without lines becomes one row equal to its amount`() {
        val rows = EntryInvoice.rows(sale(5000.0, itemName = "Urea", qty = 3.0, unit = "bag"), emptyList())
        assertEquals(1, rows.size)
        assertEquals("Urea", rows[0].itemName)
        assertEquals(3.0, rows[0].quantity, 0.0)
        assertEquals(1666.67, rows[0].rate, 0.0)
    }

    @Test
    fun `a plain amount uses its note and quantity one`() {
        val rows = EntryInvoice.rows(sale(750.5, note = "Mazdoori"), emptyList())
        assertEquals("Mazdoori", rows[0].itemName)
        assertEquals(1.0, rows[0].quantity, 0.0)
        assertEquals(750.5, rows[0].rate, 0.0)
    }
}
