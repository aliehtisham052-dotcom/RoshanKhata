package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The counter's second night (21 Oct 2026): first expiry first out, packs,
 * the weighted average cost and a line's own tax — the arithmetic, held to the
 * paisa without a database.
 */
class FefoPackTaxTest {

    private val day = 24L * 60 * 60 * 1000
    private val today = 100 * day

    private val soap = Product(id = 1, name = "Soap", nameKey = "soap", normalisedName = "soap", defaultUnit = "piece",
        packUnit = "carton", packSize = 12.0, packPrice = 1_100.0, salePrice = 100.0)

    @Test
    fun theSoonestDatedBatchGoesFirstUndatedLastAndNeverAnExpiredOne() {
        val lots = listOf(
            Fefo.Lot(1, "OLD", today - day, 5.0),        // expired yesterday
            Fefo.Lot(2, "UNDATED", null, 10.0),
            Fefo.Lot(3, "SOON", today + 10 * day, 4.0),
            Fefo.Lot(4, "LATER", today + 90 * day, 6.0)
        ).sortedWith(compareBy<Fefo.Lot, Long?>(nullsLast()) { it.expiryDate })
        val a = Fefo.allocate(lots, 7.0, 1.0, today) as Fefo.Allocation.Ok
        assertEquals(listOf(Fefo.Take(3, 4.0), Fefo.Take(4, 3.0)), a.takes)
        val b = Fefo.allocate(lots, 15.0, 1.0, today) as Fefo.Allocation.Ok
        assertEquals(listOf(Fefo.Take(3, 4.0), Fefo.Take(4, 6.0), Fefo.Take(2, 5.0)), b.takes)
        // More than the good stock, with expired stock still on the books: refused.
        val c = Fefo.allocate(lots, 21.0, 1.0, today)
        assertTrue(c is Fefo.Allocation.ExpiredOnly)
        assertEquals(20.0, (c as Fefo.Allocation.ExpiredOnly).fresh, 0.0)
        // On its expiry day a batch still sells.
        val d = Fefo.allocate(listOf(Fefo.Lot(9, "TODAY", today, 2.0)), 1.0, 1.0, today) as Fefo.Allocation.Ok
        assertEquals(9L, d.takes.single().billItemId)
    }

    @Test
    fun noBatchesOrUsedUpBatchesSellUntaggedAndPacksCountInTheirOwnUnit() {
        assertEquals(listOf(Fefo.Take(null, 3.0)), (Fefo.allocate(emptyList(), 3.0, 1.0, today) as Fefo.Allocation.Ok).takes)
        val lots = listOf(Fefo.Lot(1, "A", today + day, 18.0))
        // 2 cartons of 12 = 24 pieces: 18 from batch A (1.5 cartons), the rest untagged.
        val a = Fefo.allocate(lots, 2.0, 12.0, today) as Fefo.Allocation.Ok
        assertEquals(listOf(Fefo.Take(1, 1.5), Fefo.Take(null, 0.5)), a.takes)
    }

    @Test
    fun aSplitLineKeepsItsMoneyToThePaisa() {
        val line = SaleItem(saleId = 0, itemName = "Urea", quantity = 5.0, rate = 4_850.0, discount = 50.0)
        val parts = Fefo.split(line, listOf(Fefo.Take(3, 2.0), Fefo.Take(4, 3.0)), tax = 1_000.0)
        assertEquals(2, parts.size)
        assertEquals(5.0, parts.sumOf { it.quantity }, 0.0)
        assertEquals(line.lineTotal, LineMath.round(parts.sumOf { it.lineTotal }), 0.0)
        assertEquals(1_000.0, LineMath.round(parts.sumOf { it.taxAmount ?: 0.0 }), 0.0)
        assertEquals(listOf(3L, 4L), parts.map { it.billItemId })
        // An odd fractional split that cannot be written exactly stays whole, on the first batch.
        val odd = SaleItem(saleId = 0, itemName = "Seed", quantity = 1.0, rate = 0.07)
        val kept = Fefo.split(odd, listOf(Fefo.Take(7, 0.333), Fefo.Take(8, 0.667)), null)
        assertEquals(odd.lineTotal, LineMath.round(kept.sumOf { it.lineTotal }), 0.0)
    }

    @Test
    fun aPackIsTurnedIntoTheProductsOwnUnitAndAStrayUnitIsNot() {
        assertEquals(36.0 to "piece", Units.toBase(soap, 3.0, "Carton"))
        assertEquals(5.0 to "piece", Units.toBase(soap, 5.0, "piece"))
        assertEquals(2.0 to "box", Units.toBase(soap, 2.0, "box"))
        assertEquals(12.0, Units.factor(soap, "carton"), 0.0)
        assertEquals(1_100.0, Units.rateFor(soap, "carton", 100.0), 0.0)
        assertEquals(1_200.0, Units.rateFor(soap.copy(packPrice = null), "carton", 100.0), 0.0)
        assertFalse(Units.hasPack(soap.copy(defaultUnit = null)))
        val stock = Stock.combine(
            products = listOf(soap),
            bought = listOf(Stock.ProductQty(1, 3.0, "carton"), Stock.ProductQty(1, 4.0, "piece")),
            sold = listOf(Stock.ProductQty(1, 10.0, "piece"), Stock.ProductQty(1, 1.0, "carton")),
            returned = emptyList()
        ).single()
        assertEquals(40.0, stock.boughtQty, 0.0)
        assertEquals(18.0, stock.onHand!!, 0.0)
        // Bags and kilos with no pack between them: no figure, rather than a wrong one.
        val urea = Product(id = 2, name = "Urea", nameKey = "urea", normalisedName = "urea", defaultUnit = "bag")
        val mixed = Stock.combine(listOf(urea), listOf(Stock.ProductQty(2, 10.0, "bag"), Stock.ProductQty(2, 50.0, "kg")), emptyList(), emptyList()).single()
        assertNull(mixed.onHand)
    }

    @Test
    fun theAverageCostWeighsEveryPurchaseInTheProductsOwnUnit() {
        // 2 cartons at 1,080 (90 a piece) and 12 pieces at 96: (2,160 + 1,152) / 36 = 92.
        val avg = Cost.average(soap, listOf(Cost.Purchase(2.0, "carton", 1_080.0), Cost.Purchase(12.0, "piece", 96.0)))
        assertEquals(92.0, avg!!, 1e-9)
        // A unit it cannot convert is left out, not guessed.
        assertEquals(96.0, Cost.average(soap, listOf(Cost.Purchase(12.0, "piece", 96.0), Cost.Purchase(1.0, "box", 5_000.0)))!!, 1e-9)
        assertNull(Cost.average(soap, emptyList()))
    }

    @Test
    fun aLinesOwnTaxIsInsideOrOnTopAndTheTicketsTaxCoversTheRest() {
        val inside = SaleItem(saleId = 0, itemName = "Juice", quantity = 1.0, rate = 118.0, taxRate = 18.0, taxIncluded = true)
        val onTop = SaleItem(saleId = 0, itemName = "Oil", quantity = 1.0, rate = 100.0, taxRate = 17.0, taxIncluded = false)
        val plain = SaleItem(saleId = 0, itemName = "Bread", quantity = 1.0, rate = 50.0)
        val t = SaleMath.totals(listOf(inside, onTop, plain), taxPercent = 10.0)
        assertEquals(268.0, t.subtotal, 0.0)
        assertEquals(18.0, t.taxIncluded, 0.0)
        assertEquals(22.0, t.tax, 0.0)            // 17 on the oil + 5 on the bread
        assertEquals(290.0, t.grandTotal, 0.0)    // the juice's tax is not added again
        assertEquals(listOf(18.0, 17.0, 5.0), SaleMath.lineTaxes(listOf(inside, onTop, plain), 0.0, 10.0))
        // A bill discount is shared over the lines before their tax.
        assertEquals(listOf(9.0), SaleMath.lineTaxes(listOf(inside), 59.0, null))
        // Profit leaves out the tax that was inside the price.
        val costed = listOf(inside.copy(costRate = 80.0), onTop.copy(costRate = 80.0), plain.copy(costRate = 40.0))
        assertEquals(268.0 - 18.0 - 200.0, SaleMath.profit(costed, t)!!, 0.0)
    }
}
