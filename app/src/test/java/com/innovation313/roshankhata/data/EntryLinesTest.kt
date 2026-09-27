package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The add-entry form's list of items: totals, re-pricing and the amount check. */
class EntryLinesTest {

    private fun urea(qty: Double = 2.0, rate: Double? = 4800.0, edited: Boolean = false) = LineDraft(
        itemName = "Urea", quantity = qty, unit = "bori", rate = rate, productId = 1,
        creditPrice = 4800.0, cashPrice = 4500.0, productUnit = "bori", rateEdited = edited
    )
    private val sulphur = LineDraft(
        itemName = "Sulphur", quantity = 5.0, unit = "bag", rate = 750.0, productId = 2,
        creditPrice = 750.0, cashPrice = 700.0, productUnit = "bag"
    )
    private val chlorpyrifos = LineDraft(
        itemName = "Chlorpyrifos", quantity = 3.0, unit = "litre", rate = 1950.0, productId = 3,
        creditPrice = 1950.0, cashPrice = 1800.0, productUnit = "litre"
    )

    @Test
    fun `the visit adds up to 19,200 on udhar and 17,900 on naqd`() {
        val visit = listOf(urea(), sulphur, chlorpyrifos)
        assertEquals(19200.0, EntryLines.total(visit)!!, 0.0)

        val naqd = visit.map { it.repriced(RateType.CASH, isCustomer = true) }
        assertEquals(listOf(4500.0, 700.0, 1800.0), naqd.map { it.rate })
        assertEquals(17900.0, EntryLines.total(naqd)!!, 0.0)
    }

    @Test
    fun `a rate the owner typed survives a chip switch`() {
        val bargained = urea(rate = 4700.0, edited = true)
        assertEquals(4700.0, bargained.repriced(RateType.CASH, isCustomer = true).rate!!, 0.0)
    }

    @Test
    fun `switching to a type with no price leaves the rate unknown, not the other price`() {
        val noCash = urea().copy(cashPrice = null)
        assertNull(noCash.repriced(RateType.CASH, isCustomer = true).rate)
    }

    @Test
    fun `an item with no product is never re-priced`() {
        val typed = LineDraft(itemName = "Zinc Sulphate", quantity = 1.0, unit = "bag", rate = 900.0)
        assertEquals(typed, typed.repriced(RateType.CASH, isCustomer = true))
    }

    @Test
    fun `one item without a rate means no total, and no items means no total`() {
        assertNull(EntryLines.total(listOf(urea(), urea(rate = null))))
        assertNull(EntryLines.total(emptyList()))
    }

    @Test
    fun `the written amount is compared to the paisa`() {
        assertEquals(EntryLines.AmountCheck.Equal, EntryLines.compare(19200.0, 19200.0))
        assertEquals(EntryLines.AmountCheck.Less(100.0), EntryLines.compare(17900.0, 17800.0))
        assertEquals(EntryLines.AmountCheck.More(50.0), EntryLines.compare(19200.0, 19250.0))
        // Double residue is not a difference.
        assertEquals(EntryLines.AmountCheck.Equal, EntryLines.compare(5850.3, 3 * 1950.1))
    }

    @Test
    fun `an item needs a name and a quantity to go into the list`() {
        assertTrue(urea().isComplete())
        assertFalse(urea(qty = 0.0).isComplete())
        assertFalse(urea().copy(itemName = " ").isComplete())
        assertFalse(urea().copy(quantity = null).isComplete())
    }

    @Test
    fun `a draft becomes the saved line with its rate`() {
        val item = urea().toItem()!!
        assertEquals("Urea", item.itemName)
        assertEquals(4800.0, item.rate!!, 0.0)
        assertEquals(1L, item.productId)
    }
}
