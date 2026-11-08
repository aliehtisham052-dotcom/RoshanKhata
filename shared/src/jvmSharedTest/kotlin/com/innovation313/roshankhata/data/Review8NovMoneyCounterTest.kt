package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** The 8 Nov 2026 review, money and counter (Marhala 2): the arithmetic that needs no database. */
class Review8NovMoneyCounterTest {

    private fun item(name: String, qty: Double, rate: Double, taxRate: Double? = null) =
        SaleItem(saleId = 1, lineNo = 0, itemName = name, quantity = qty, rate = rate, taxRate = taxRate)

    @Test
    fun aReturnGivesBackOnlyTheTaxTheLineCarried() {
        // A 17% on top, B none: 1,000 each. The ticket comes to 2,170; B's return is 1,000, not 1,085 (M63).
        val a = item("A", 1.0, 1_000.0, 17.0)
        val b = item("B", 1.0, 1_000.0)
        val sale = Sale(subtotal = 2_000.0, taxAmount = 170.0, grandTotal = 2_170.0, paidTotal = 0.0)
        assertEquals(1_170.0, ReturnMath.paidRate(sale, a), 0.005)
        assertEquals(1_000.0, ReturnMath.paidRate(sale, b), 0.005)
    }

    @Test
    fun aTicketLevelTaxStillSpreadsOverEveryLine() {
        val a = item("A", 2.0, 500.0)
        val sale = Sale(subtotal = 1_000.0, taxPercent = 10.0, taxAmount = 100.0, grandTotal = 1_100.0, paidTotal = 0.0)
        assertEquals(550.0, ReturnMath.paidRate(sale, a), 0.005)
    }

    @Test
    fun untaggedStockCoversASaleBesideAnExpiredBatch() {
        val day = 24L * 60 * 60 * 1000
        val lots = listOf(Fefo.Lot(1, "OLD", 10 * day - day, 10.0))
        val ok = Fefo.allocate(lots, 5.0, 1.0, 10 * day, untagged = 50.0) as Fefo.Allocation.Ok
        assertEquals(listOf(Fefo.Take(null, 5.0)), ok.takes)
    }

    @Test
    fun theSalesReportKeepsAnItemsUnitsApart() {
        fun l(unit: String, qty: Double) = SoldLine(
            saleId = 1, timestamp = 0, customerName = null, createdBy = null, taxPercent = null, productId = 1,
            itemName = "Cola", category = null, quantity = qty, unit = unit, rate = 100.0, discount = 0.0, costRate = null
        )
        val r = SalesReport(0, 1, listOf(l("carton", 2.0), l("bottle", 10.0)), 0.0, 0.0, emptyList())
        assertEquals(2, r.byItem.size)
        assertEquals(setOf(2.0, 10.0), r.byItem.map { it.quantity }.toSet())
        val one = SalesReport(0, 1, listOf(l("carton", 2.0), l("carton", 3.0)), 0.0, 0.0, emptyList())
        assertEquals(listOf("Cola"), one.byItem.map { it.key })
    }

    @Test
    fun aPackOnCreditKeepsTheCreditShareOfItsPrice() {
        val p = Product(id = 1, name = "Soap", nameKey = "soap", normalisedName = "soap", defaultUnit = "piece",
            packUnit = "carton", packSize = 12.0, packPrice = 1_000.0, salePrice = 100.0)
        assertEquals(1_000.0, Units.rateFor(p, "carton", 100.0), 0.005)
        assertEquals(1_100.0, Units.rateFor(p, "carton", 110.0), 0.005)
    }
}
