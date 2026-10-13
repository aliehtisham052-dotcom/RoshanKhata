package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** The invoice sum, the one the PDF, the list and the "more options" sheet all use. */
class InvoiceMathTest {

    private val packet = InvoiceItem(invoiceId = 0, itemName = "Packet", quantity = 1.0, rate = 200.0)
    private fun invoice() = Invoice(customerName = "Test")

    @Test
    fun theSheetsOwnExample() {
        // Items 200, 5% off, Delivery 50, received 100: total 240, 140 still due.
        val t = invoice().copy(
            discountPercent = 5.0, additionalChargeLabel = "Delivery", additionalChargeAmount = 50.0, receivedAmount = 100.0
        ).totals(listOf(packet))
        assertEquals(200.0, t.subtotal, 0.001)
        assertEquals(10.0, t.discountAmount, 0.001)
        assertEquals(240.0, t.grandTotal, 0.001)
        assertEquals(140.0, t.balanceDue, 0.001)
    }

    @Test
    fun aDiscountInMoneyIsTakenOffAsItIs() {
        val t = invoice().copy(discountAmount = 50.0).totals(listOf(packet))
        assertEquals(50.0, t.discountAmount, 0.001)
        assertEquals(150.0, t.grandTotal, 0.001)
    }

    @Test
    fun aMoneyDiscountWinsOverAPercentAndNeverPassesTheItems() {
        val both = invoice().copy(discountPercent = 10.0, discountAmount = 30.0).totals(listOf(packet))
        assertEquals("money discount is the discount", 30.0, both.discountAmount, 0.001)
        val tooBig = invoice().copy(discountAmount = 500.0).totals(listOf(packet))
        assertEquals("never more than the items", 200.0, tooBig.discountAmount, 0.001)
        assertEquals(0.0, tooBig.grandTotal, 0.001)
    }

    @Test
    fun taxComesAfterTheMoneyDiscount() {
        val t = invoice().copy(discountAmount = 100.0, taxPercent = 10.0).totals(listOf(packet))
        assertEquals(10.0, t.taxAmount, 0.001)
        assertEquals(110.0, t.grandTotal, 0.001)
    }

    @Test
    fun anOldInvoiceWithAPercentIsUnchanged() {
        val t = invoice().copy(discountPercent = 5.0, taxPercent = 17.0).totals(listOf(packet))
        assertEquals(10.0, t.discountAmount, 0.001)
        assertEquals(222.3, t.grandTotal, 0.001)
    }
}
