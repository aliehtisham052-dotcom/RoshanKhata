package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PaymentDetailsTest {
    private fun lines(j: String?, b: String?, t: String?, i: String?) =
        PaymentDetails.lines(j, b, t, i, "JazzCash/Easypaisa", "Bank")

    @Test
    fun `only filled fields appear`() {
        assertEquals(listOf("JazzCash/Easypaisa: 03001234567"), lines(" 03001234567 ", null, "", null))
    }

    @Test
    fun `bank line needs an account number and joins what is known`() {
        assertEquals(emptyList<String>(), lines(null, "HBL", "Ali Traders", null))
        assertEquals(listOf("Bank: HBL · Ali Traders · PK36HABB0001"), lines(null, "HBL", "Ali Traders", "PK36HABB0001"))
    }

    @Test
    fun `nothing filled means no block`() {
        assertEquals(emptyList<String>(), lines(null, null, null, null))
    }
}
