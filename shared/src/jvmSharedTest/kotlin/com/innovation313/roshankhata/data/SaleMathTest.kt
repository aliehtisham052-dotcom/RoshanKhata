package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The counter's arithmetic and checks, held to the paisa (POS Phase 1). */
class SaleMathTest {

    private fun line(qty: Double, rate: Double, discount: Double = 0.0, name: String = "x") =
        SaleItem(saleId = 0, itemName = name, quantity = qty, rate = rate, discount = discount)

    @Test
    fun lineTotalsAreToThePaisa() {
        assertEquals(99.99, SaleMath.lineTotal(3.0, 33.33), 0.0)
        assertEquals(1000.0, SaleMath.lineTotal(2.0, 525.0, 50.0), 0.0)
        // 0.1 + 0.2 style drift does not reach the ticket.
        assertEquals(0.3, SaleMath.totals(listOf(line(1.0, 0.1), line(1.0, 0.2))).grandTotal, 0.0)
    }

    @Test
    fun discountIsCappedAndTaxFollowsIt() {
        val t = SaleMath.totals(listOf(line(2.0, 500.0)), discount = 5000.0, taxPercent = 17.0)
        assertEquals(1000.0, t.subtotal, 0.0)
        assertEquals(1000.0, t.discount, 0.0)
        assertEquals(0.0, t.taxable, 0.0)
        assertEquals(0.0, t.grandTotal, 0.0)
        val u = SaleMath.totals(listOf(line(2.0, 500.0)), discount = 100.0, taxPercent = 17.0)
        assertEquals(153.0, u.tax, 0.0)
        assertEquals(1053.0, u.grandTotal, 0.0)
        assertEquals(0.0, SaleMath.totals(listOf(line(1.0, 10.0)), discount = -5.0).discount, 0.0)
    }

    @Test
    fun roundingToAWholeKeepsTheDifference() {
        val t = SaleMath.totals(listOf(line(3.0, 33.33)), roundToWhole = true)
        assertEquals(100.0, t.grandTotal, 0.0)
        assertEquals(0.01, t.roundOff, 0.0)
        val d = SaleMath.totals(listOf(line(1.0, 100.49)), roundToWhole = true)
        assertEquals(100.0, d.grandTotal, 0.0)
        assertEquals(-0.49, d.roundOff, 0.0)
    }

    @Test
    fun theChecksRefuseWhatTheBookMustNeverHold() {
        val ok = listOf(line(1.0, 100.0))
        val cash = { a: Double -> listOf(SalePayment(saleId = 0, method = PaymentMethod.CASH, amount = a)) }
        assertNull(SaleMath.check(ok, cash(100.0), 100.0, hasParty = false))
        assertNull(SaleMath.check(ok, emptyList(), 100.0, hasParty = true))
        assertEquals(SaleMath.Problem.NO_LINES, SaleMath.check(emptyList(), cash(1.0), 0.0, true))
        assertEquals(SaleMath.Problem.BAD_QUANTITY, SaleMath.check(listOf(line(0.0, 5.0)), emptyList(), 0.0, true))
        assertEquals(SaleMath.Problem.BAD_RATE, SaleMath.check(listOf(line(1.0, -5.0)), emptyList(), 0.0, true))
        assertEquals(SaleMath.Problem.BAD_DISCOUNT, SaleMath.check(listOf(line(1.0, 5.0, 6.0)), emptyList(), 0.0, true))
        assertEquals(SaleMath.Problem.EMPTY_NAME, SaleMath.check(listOf(line(1.0, 5.0, name = " ")), emptyList(), 5.0, true))
        assertEquals(SaleMath.Problem.BAD_PAYMENT, SaleMath.check(ok, cash(0.0), 100.0, true))
        assertEquals(SaleMath.Problem.BAD_METHOD, SaleMath.check(ok, listOf(SalePayment(saleId = 0, method = "gold", amount = 1.0)), 100.0, true))
        assertEquals(SaleMath.Problem.OVERPAID, SaleMath.check(ok, cash(100.01), 100.0, true))
        assertEquals(SaleMath.Problem.WALK_IN_UNPAID, SaleMath.check(ok, cash(99.0), 100.0, false))
    }

    @Test
    fun profitNeedsEveryCost() {
        val t = SaleMath.totals(listOf(line(2.0, 500.0)))
        assertNull(SaleMath.profit(listOf(line(2.0, 500.0)), t))
        assertEquals(200.0, SaleMath.profit(listOf(line(2.0, 500.0).copy(costRate = 400.0)), t)!!, 0.0)
    }

    @Test
    fun numbersAreSixDigitsFromTheHighest() {
        assertEquals("S-000001", SaleNumber.next(0))
        assertEquals("S-000124", SaleNumber.next(123))
    }

    @Test
    fun theDraftAddsAScannedPackToItsLine() {
        val d = CounterDraft()
        val dap = Product(id = 1, name = "DAP", nameKey = "dap", normalisedName = "dap", defaultUnit = "bag", salePrice = 3500.0, creditPrice = 3700.0, barcode = "890")
        d.add(dap, onHand = 2.0)
        d.add(dap, onHand = 2.0)
        assertEquals(1, d.lines.size)
        assertEquals("2", d.lines[0].qty)
        assertEquals(7000.0, d.totals.grandTotal, 0.0)
        assertTrue(d.short.isEmpty())
        d.lines[0].qty = "3"
        assertEquals(1.0, d.short.single().shortBy!!, 0.0)
        // A walk-in must pay in full; the cash button pays what is owed.
        assertEquals(SaleMath.Problem.WALK_IN_UNPAID, d.problem)
        d.payRestInCash()
        assertNull(d.problem)
        assertEquals(0.0, d.remaining, 0.0)
        // On an account the credit price is offered.
        val e = CounterDraft().apply { setParty(5, "Bilal"); add(dap) }
        assertEquals("3700", e.lines[0].rate)
        assertNull(e.problem)
        assertEquals(listOf(dap), CounterDraft.search(listOf(dap), "890"))
        assertEquals(listOf(dap), CounterDraft.search(listOf(dap), "da"))
        assertTrue(CounterDraft.search(listOf(dap), "urea").isEmpty())
    }
}
