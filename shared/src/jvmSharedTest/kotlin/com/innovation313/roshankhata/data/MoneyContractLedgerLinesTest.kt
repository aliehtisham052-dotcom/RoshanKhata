package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * BC-263 (24 Nov 2026, money): "who owes" and "who has anything to age" were decided against a fixed half paisa (0.005). A
 * dinar shop's balance of 0.004 is more than nothing in its coin (half of 0.001 is 0.0005) and was left out; a no-decimal
 * coin's 0.3 is less than nothing and was counted. Both now ask [Money]. Invented parties.
 */
class MoneyContractLedgerLinesTest {

    private fun <T> withSign(sign: String, body: () -> T): T {
        val before = Currency.symbol
        Currency.symbol = sign
        try { return body() } finally { Currency.symbol = before }
    }

    private fun party(id: Long, balance: Double, customer: Boolean = true) =
        PartyWithBalance(id = id, name = "P$id", phone = null, isCustomer = customer, photoPath = null, balance = balance)

    @Test fun `a dinar balance of 0_004 is still owed`() = withSign("KWD") {
        val parties = listOf(party(1, 0.004), party(2, 0.0004), party(3, 12.5))
        assertEquals(listOf(1L, 3L), LedgerLines.owing(parties))
    }

    @Test fun `in a coin with no decimals 0_3 is nothing and 0_6 is a coin`() = withSign("Rp") {
        assertEquals(listOf(2L), LedgerLines.owing(listOf(party(1, 0.3), party(2, 0.6))))
    }

    @Test fun `owing is customers who are owed, never a supplier or a settled one`() = withSign("Rs") {
        val parties = listOf(party(1, 100.0), party(2, -100.0), party(3, 0.0), party(4, 50.0, customer = false), party(5, 0.004))
        assertEquals(listOf(1L), LedgerLines.owing(parties))
    }

    @Test fun `aging reads a balance either way and sorts the ids`() = withSign("KWD") {
        val parties = listOf(party(9, -0.004), party(2, 0.004), party(5, 0.0), party(7, 0.0003), party(1, -3.0))
        assertEquals(listOf(1L, 2L, 9L), LedgerLines.agingIds(parties))
    }

    @Test fun `aging in rupees ignores a fraction of a paisa`() = withSign("Rs") {
        assertEquals(listOf(2L), LedgerLines.agingIds(listOf(party(1, 0.004), party(2, 0.006))))
    }
}
