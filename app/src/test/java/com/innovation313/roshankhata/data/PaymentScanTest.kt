package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Reading money received. The rows are the common shapes of a payment app's
 * receipt and a bank transfer slip as the phone reads them — not yet the
 * owner's own screenshots, which will be added here as they come.
 */
class PaymentScanTest {

    private val utc = TimeZone.getTimeZone("UTC")

    private fun day(y: Int, m: Int, d: Int): Long =
        Calendar.getInstance(utc).apply { clear(); set(y, m - 1, d, 12, 0, 0) }.timeInMillis

    @Test fun walletReceipt_amountOnNextRow_tidAndDate() {
        val p = PaymentScan.parse(listOf(
            "Transaction Successful",
            "Amount",
            "Rs. 5,000.00",
            "Fee  Rs. 0.00",
            "To  Roshan Traders",
            "TID: 012345678901",
            "01 Oct 2026  10:15 AM"
        ), utc)
        assertEquals(5000.0, p.amount!!, 0.001)
        assertEquals("012345678901", p.reference)
        assertEquals(day(2026, 10, 1), p.date)
    }

    @Test fun bankSlip_sameRow_labels() {
        val p = PaymentScan.parse(listOf(
            "Funds Transfer Receipt",
            "Amount Transferred: PKR 1,25,000",
            "Transaction Reference No. FT26274ABC991",
            "Date: 30/09/2026",
            "Available Balance PKR 48,210.55"
        ), utc)
        assertEquals(125000.0, p.amount!!, 0.001)
        assertEquals("FT26274ABC991", p.reference)
        assertEquals(day(2026, 9, 30), p.date)
    }

    @Test fun rsRunsIntoFigure() {
        assertEquals(2750.0, PaymentScan.amountIn(listOf("Paid Rs2,750"))!!, 0.001)
    }

    @Test fun feeTaxAndBalanceRowsNeverGiveTheAmount() {
        assertNull(PaymentScan.amountIn(listOf("Fee Rs. 25", "Available Balance Rs 9,900", "Tax Rs 4")))
    }

    @Test fun twoDifferentFiguresLeaveTheAmountEmpty() {
        assertNull(PaymentScan.amountIn(listOf("Amount Rs 5,000", "Received Rs 6,000")))
    }

    @Test fun theSameFigureTwiceIsStillOneAmount() {
        assertEquals(5000.0, PaymentScan.amountIn(listOf("Rs 5,000", "Amount Rs. 5,000.00"))!!, 0.001)
    }

    @Test fun phoneNumberAndCnicAreNeverReferences() {
        assertNull(PaymentScan.referenceIn(listOf("Ref: 03001234567")))
        assertNull(PaymentScan.referenceIn(listOf("Ref No: 34603-1234567-1")))
        assertNull(PaymentScan.referenceIn(listOf("Ref: 1234")))
    }

    @Test fun referenceOnTheNextRow() {
        assertEquals("88A7731920", PaymentScan.referenceIn(listOf("Transaction ID", "88A7731920")))
    }

    @Test fun amountRowWithADateNextIsNotReadAsMoney() {
        assertNull(PaymentScan.amountIn(listOf("Amount", "01/10/2026")))
    }

    @Test fun monthFirstDate() {
        assertEquals(day(2026, 10, 1), PaymentScan.dateIn(listOf("Oct 01, 2026 9:41 PM"), utc))
    }

    @Test fun nothingUsefulIsEmpty() {
        assertTrue(PaymentScan.parse(listOf("Thank you", "Have a nice day"), utc).isEmpty)
    }

    @Test fun transactionAmountIsStillMoney() {
        assertEquals(5000.0, PaymentScan.amountIn(listOf("Transaction Amount Rs 5,000"))!!, 0.001)
    }

    @Test fun successfulIsNotAReference() {
        assertNull(PaymentScan.referenceIn(listOf("Transaction Successful")))
    }
}
