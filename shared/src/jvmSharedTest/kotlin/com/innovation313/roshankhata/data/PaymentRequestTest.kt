package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.words.Words
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The payment-request record's two rules (29 Oct 2026), the same on the phone and the computer: when
 * Follow-up says "reminded, nothing received since", and what the reference box keeps.
 */
class PaymentRequestTest {

    private val words = object : Words {
        override fun text(name: String, vararg args: Any) = name + args.joinToString(",", prefix = "(", postfix = ")")
        override fun plural(name: String, count: Int, vararg args: Any) = name
        override fun list(name: String) = emptyList<String>()
    }

    @Test
    fun `waiting means reminded and nothing received since`() {
        assertFalse("never reminded", PaymentRequest.waitingSince(null, 500L))
        assertFalse("never reminded, never paid", PaymentRequest.waitingSince(null, null))
        assertTrue("reminded, never paid", PaymentRequest.waitingSince(1_000L, null))
        assertTrue("paid before the reminder", PaymentRequest.waitingSince(1_000L, 900L))
        assertFalse("paid after the reminder", PaymentRequest.waitingSince(1_000L, 1_001L))
        assertFalse("paid at the reminder's moment counts as received", PaymentRequest.waitingSince(1_000L, 1_000L))
    }

    @Test
    fun `each party's newest payment comes from the points Follow-up already reads`() {
        val points = listOf(
            LedgerPoint(1, 100L, 5_000.0, isGiven = true),
            LedgerPoint(1, 200L, 1_000.0, isGiven = false),
            LedgerPoint(1, 150L, 500.0, isGiven = false),
            LedgerPoint(2, 300L, 700.0, isGiven = true)
        )
        val got = PaymentRequest.lastGotAt(points)
        assertEquals(200L, got[1])
        assertNull("a sale is not a payment", got[2])
    }

    @Test
    fun `the reference box shows beside a way that has one, and keeps only what was typed`() {
        assertFalse(PaymentRef.applies(null))
        assertFalse(PaymentRef.applies(""))
        assertFalse(PaymentRef.applies(PaymentMethod.CASH))
        for (m in PaymentMethod.ALL - PaymentMethod.CASH) assertTrue(m, PaymentRef.applies(m))
        assertEquals("TID 778", PaymentRef.clean(PaymentMethod.WALLET, "  TID 778 "))
        assertNull(PaymentRef.clean(PaymentMethod.WALLET, "   "))
        assertNull("cash keeps no reference", PaymentRef.clean(PaymentMethod.CASH, "TID 778"))
        assertNull(PaymentRef.clean(null, "TID 778"))
    }

    @Test
    fun `every method has a label, and a payment is said with its reference`() {
        for (m in PaymentMethod.ALL) assertEquals("payment_method_$m()", PaymentMethod.label(words, m))
        assertEquals("payment_method_wallet()", PaymentMethod.label(words, PaymentMethod.WALLET))
        // A key this build does not know stays as it was stored.
        assertEquals("crypto", PaymentMethod.label(words, "crypto"))
        assertNull(PaymentMethod.said(words, null, null))
        assertNull(PaymentMethod.said(words, "", "  "))
        assertEquals("payment_method_cash()", PaymentMethod.said(words, PaymentMethod.CASH, null))
        val said = PaymentMethod.said(words, PaymentMethod.BANK, " TID 778 ")!!
        assertTrue(said, said.startsWith("payment_method_bank() \u00b7 payment_scan_reference(") && said.contains("TID 778"))
    }
}
