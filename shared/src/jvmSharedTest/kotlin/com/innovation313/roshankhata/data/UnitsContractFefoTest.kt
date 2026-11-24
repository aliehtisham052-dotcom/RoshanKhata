package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The batch side of the units contract (written on [Units], BC-253): what is left of a lot, in which unit, after a sale,
 * a return of a pack sold as pieces, a write-off and a count; and that an expired lot is never sold. Plain Kotlin.
 */
class UnitsContractFefoTest {

    private val day = 24L * 60 * 60 * 1000
    private val today = 100 * day

    private fun product(unit: String?, packUnit: String? = null, packSize: Double? = null) =
        Product(id = 1, name = "P", nameKey = "p", normalisedName = "p", defaultUnit = unit, packUnit = packUnit, packSize = packSize)

    private val soap = product("piece", "carton", 12.0)

    @Test
    fun aLotLeftIsInTheOwnUnitAfterASaleInPacksAndAReturnInPieces() {
        // 5 cartons billed; 2 cartons sold from the batch, 5 pieces came back: 60 - 24 + 5 = 41 pieces.
        val lots = Fefo.lotsOf(soap, listOf(Fefo.Batch(1, "A", today + day, 5.0, "carton")),
            mapOf(1L to listOf(Fefo.Move(1, 2.0, "carton"), Fefo.Move(1, -5.0, "piece"))), onHand = null)
        assertEquals(41.0, lots.single().left, 0.0)
        assertEquals("piece", lots.single().unit)
        // The same lot told in the bill's own unit (a return to the supplier): 41 / 12 cartons.
        assertEquals(41.0 / 12.0, Units.fromBase(soap, 41.0, "carton"), 1e-6)
        // The other way: sold in pieces, returned as a whole carton.
        val back = Fefo.lotsOf(soap, listOf(Fefo.Batch(1, "A", today + day, 60.0, "piece")),
            mapOf(1L to listOf(Fefo.Move(1, 30.0, "piece"), Fefo.Move(1, -1.0, "carton"))), onHand = null)
        assertEquals(42.0, back.single().left, 0.0)
    }

    @Test
    fun aMovementInAUnitThatCannotBeConvertedIsLeftOutNotGuessed() {
        val lots = Fefo.lotsOf(soap, listOf(Fefo.Batch(1, "A", today + day, 60.0, "piece")),
            mapOf(1L to listOf(Fefo.Move(1, 10.0, "box"), Fefo.Move(1, 6.0, "piece"))), onHand = null)
        assertEquals(54.0, lots.single().left, 0.0)
    }

    @Test
    fun aLotKeepsNoFloatDust() {
        val lots = Fefo.lotsOf(product("kg"), listOf(Fefo.Batch(1, "A", null, 0.3, "kg")),
            mapOf(1L to listOf(Fefo.Move(1, 0.1, "kg"), Fefo.Move(1, 0.2, "kg"))), onHand = null)
        assertEquals(0.0, lots.single().left, 0.0)
    }

    @Test
    fun aWriteOffTakesExactlyWhatIsLeftInTheUnitTheLotIsCountedIn() {
        val oil = product("bottle", "carton", 12.0)
        val lot = Fefo.lotsOf(oil, listOf(Fefo.Batch(7, "X", today - day, 2.0, "carton")),
            mapOf(7L to listOf(Fefo.Move(7, 5.0, "bottle"))), onHand = null).single()
        assertEquals(19.0, lot.left, 0.0)
        assertTrue(lot.expired(today))
        // The write-off is 19 BOTTLES (the lot's unit), not 19 cartons and not the bill's 2.
        val plan = Fefo.writeOffOf(oil, lot)!!
        assertEquals(19.0, plan.qty, 0.0)
        assertEquals("bottle", plan.unit)
        // The adjustment the DAO writes (-19 bottles against that bill line) comes back as a movement of 19: nothing is left.
        val after = Fefo.lotsOf(oil, listOf(Fefo.Batch(7, "X", today - day, 2.0, "carton")),
            mapOf(7L to listOf(Fefo.Move(7, 5.0, "bottle"), Fefo.Move(7, plan.qty, plan.unit))), onHand = null).single()
        assertEquals(0.0, after.left, 0.0)
        assertNull(Fefo.writeOffOf(oil, after))
        // The shelf goes down by the same figure: 24 bought, 5 sold, 19 written off.
        val stock = Stock.combine(listOf(oil.copy(id = 1)), listOf(Stock.ProductQty(1, 2.0, "carton")), listOf(Stock.ProductQty(1, 5.0, "bottle")),
            emptyList(), adjusted = listOf(Stock.ProductQty(1, -plan.qty, plan.unit))).single()
        assertEquals(0.0, stock.onHand!!, 0.0)
    }

    @Test
    fun aLotBilledInAUnitNoPackExplainsIsWrittenOffInThatUnit() {
        val lot = Fefo.lotsOf(soap, listOf(Fefo.Batch(3, "Y", today - day, 4.0, "box")), emptyMap(), onHand = null).single()
        assertEquals("box", lot.unit)
        val plan = Fefo.writeOffOf(soap, lot)!!
        assertEquals(4.0, plan.qty, 0.0)
        assertEquals("box", plan.unit)
        val after = Fefo.lotsOf(soap, listOf(Fefo.Batch(3, "Y", today - day, 4.0, "box")), mapOf(3L to listOf(Fefo.Move(3, plan.qty, plan.unit))), null)
        assertEquals(0.0, after.single().left, 0.0)
        // A lot with no unit at all is written off in the product's own.
        val bare = Fefo.lotsOf(soap, listOf(Fefo.Batch(4, "Z", today - day, 9.0, null)), emptyMap(), null).single()
        assertEquals("piece", Fefo.writeOffOf(soap, bare)!!.unit)
    }

    @Test
    fun anExpiredLotIsNeverSoldAndAPackLineTakesItsOwnUnitsWorth() {
        val lots = listOf(Fefo.Lot(1, "OLD", today - day, 10.0, "piece"), Fefo.Lot(2, "NEW", today + day, 24.0, "piece"))
        // Two cartons (24 pieces) come entirely from the fresh batch, told back in cartons.
        val ok = Fefo.allocate(lots, 2.0, Units.factor(soap, "carton"), today) as Fefo.Allocation.Ok
        assertEquals(listOf(Fefo.Take(2, 2.0)), ok.takes)
        // A third carton would need the expired lot: refused, saying what is fresh (2 cartons).
        val refused = Fefo.allocate(lots, 3.0, 12.0, today)
        assertTrue(refused is Fefo.Allocation.ExpiredOnly)
        assertEquals(2.0, (refused as Fefo.Allocation.ExpiredOnly).fresh, 0.0)
    }

    @Test
    fun aCountSetsTheShelfToWhatWasFoundInItsOwnUnit() {
        // Book: 41 pieces. Found 3 cartons and 2 pieces = 38: the adjustment is -3 pieces, on the bought side, in pieces.
        val book = Stock.combine(listOf(soap), listOf(Stock.ProductQty(1, 5.0, "carton")), listOf(Stock.ProductQty(1, 24.0, "piece")),
            listOf(Stock.ProductQty(1, 5.0, "piece"))).single()
        assertEquals(41.0, book.onHand!!, 0.0)
        val diff = StockCount.difference(book.onHand!!, 38.0)!!
        assertEquals(-3.0, diff, 0.0)
        val after = Stock.combine(listOf(soap), listOf(Stock.ProductQty(1, 5.0, "carton")), listOf(Stock.ProductQty(1, 24.0, "piece")),
            listOf(Stock.ProductQty(1, 5.0, "piece")), adjusted = listOf(Stock.ProductQty(1, diff, book.unit))).single()
        assertEquals(38.0, after.onHand!!, 0.0)
    }
}
