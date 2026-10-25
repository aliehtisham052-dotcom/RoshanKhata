package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.InvoiceItem
import com.innovation313.roshankhata.data.InvoiceMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The invoice form's discount on the computer (25 Oct 2026, the audit): a
 * phone invoice's money discount was left out of the form's total while the
 * list and the print took it off, and a % typed here was lost on save.
 */
class InvoiceDiscountTest {

    private val lines = listOf(InvoiceItem(invoiceId = 1, itemName = "Urea", quantity = 2.0, rate = 5_000.0))

    @Test
    fun `a money discount is kept as money and comes off the total`() {
        val (percent, money) = invoiceDiscount("500", inMoney = true, subtotal = 10_000.0)
        assertNull(percent)
        assertEquals(500.0, money!!, 0.0)
        val t = InvoiceMath.totals(lines, percent, null, null, null, money)
        assertEquals(9_500.0, t.grandTotal, 0.001)
    }

    @Test
    fun `a share typed in the money box is worked out on the items`() {
        assertEquals(250.0, invoiceDiscount("10%", inMoney = true, subtotal = 2_500.0).second!!, 0.0)
    }

    @Test
    fun `a percent discount is kept as a percent`() {
        val (percent, money) = invoiceDiscount("10", inMoney = false, subtotal = 10_000.0)
        assertEquals(10.0, percent!!, 0.0)
        assertNull(money)
        assertEquals(9_000.0, InvoiceMath.totals(lines, percent, null, null, null, money).grandTotal, 0.001)
    }

    @Test
    fun `an empty or zero box is no discount`() {
        assertEquals(null to null, invoiceDiscount("", inMoney = true, subtotal = 10_000.0))
        assertEquals(null to null, invoiceDiscount("0", inMoney = false, subtotal = 10_000.0))
    }
}
