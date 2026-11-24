package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * BC-262 (24 Nov 2026, money, importance 10): a goods return reverses EXACTLY what its sold line was counted at. The sales
 * report counts a ticket's lines at their shares of the ticket discount cut to the coin over ALL the ticket's lines
 * (Rs 100 off 3 x 1,000: 966.67, 966.67, 966.66); a returned line was rebuilt alone and took 966.67 for the third line, a
 * coin more than it brought in. Also BC-271: what is left of a sold line to come back is kept to six places.
 * Invented figures; plain Kotlin, no database.
 */
class ReturnedLineSpreadTest {

    private fun ticket() = (1L..3L).map { ReturnedLines.TicketLine(it, 1.0, 1_000.0, 0.0, null, null) }

    private fun returned(itemId: Long, qty: Double = 1.0, sold: Double = 1.0, saleDiscount: Double? = 100.0) = ReturnedLine(
        quantity = qty, soldQuantity = sold, rate = 1_000.0, discount = 0.0, costRate = null, taxAmount = null, taxIncluded = null,
        saleSubtotal = 3_000.0, saleDiscount = saleDiscount, saleItemId = itemId, saleId = 7
    )

    @Test fun `returning the third of three lines reverses 966_66 not 966_67`() {
        val third = ReturnedLines.spreadOf(returned(3), ticket())
        assertEquals(966.66, third.revenue, 0.0)
        // what the report counted the three lines at, and what the three returns give back: the same
        val back = (1L..3L).sumOf { ReturnedLines.spreadOf(returned(it), ticket()).revenue }
        assertEquals(2_900.0, back, 0.0001)
    }

    @Test fun `a line read alone still takes the plain share, which is why the spread is needed`() {
        assertEquals(966.67, returned(3).revenue, 0.0)
    }

    @Test fun `half of a line comes back at half its spread`() {
        val line = ReturnedLine(quantity = 1.0, soldQuantity = 2.0, rate = 500.0, discount = 0.0, costRate = null, taxAmount = null,
            taxIncluded = null, saleSubtotal = 3_000.0, saleDiscount = 100.0, saleItemId = 3, saleId = 7)
        val ticket = listOf(ReturnedLines.TicketLine(1, 1.0, 1_000.0, 0.0, null, null), ReturnedLines.TicketLine(2, 1.0, 1_000.0, 0.0, null, null),
            ReturnedLines.TicketLine(3, 2.0, 500.0, 0.0, null, null))
        val whole = ReturnedLines.spreadOf(line.copy(quantity = 2.0), ticket).revenue
        assertEquals(966.66, whole, 0.0)
        assertEquals(483.33, ReturnedLines.spreadOf(line, ticket).revenue, 0.0)
    }

    @Test fun `no ticket discount leaves a line as it was`() {
        val plain = returned(3, saleDiscount = null)
        assertEquals(plain, ReturnedLines.spreadOf(plain, ticket()))
        assertEquals(plain, ReturnedLines.spreadOf(returned(3, saleDiscount = 0.0).copy(saleDiscount = 0.0), ticket()).copy(saleDiscount = null))
    }

    @Test fun `a sold line not among the ticket's lines is left as it was`() {
        val line = returned(99)
        assertEquals(line, ReturnedLines.spreadOf(line, ticket()))
    }

    @Test fun `a ticket's lines are read once however many of its lines came back`() = runBlocking {
        var reads = 0
        val out = ReturnedLines.spread(listOf(returned(1), returned(2), returned(3))) { reads++; ticket() }
        assertEquals(1, reads)
        assertEquals(listOf(966.67, 966.67, 966.66), out.map { it.revenue })
    }

    @Test fun `a ticket with no discount is never read`() = runBlocking {
        var reads = 0
        ReturnedLines.spread(listOf(returned(1, saleDiscount = null))) { reads++; ticket() }
        assertEquals(0, reads)
    }

    @Test fun `what is left to come back is kept to six places`() {
        assertEquals(0.0, ReturnedLines.soldLeft(0.3, 0.1 + 0.2), 0.0)
        assertEquals(0.5, ReturnedLines.soldLeft(1.0, 0.5), 0.0)
        assertEquals(0.7, ReturnedLines.soldLeft(1.0, 0.3), 0.0)
    }
}
