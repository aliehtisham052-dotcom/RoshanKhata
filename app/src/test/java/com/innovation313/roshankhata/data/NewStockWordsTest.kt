package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.NewStockWords.Family
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The New stock poster's words by the kind of shop (13 Nov 2026). "Stock" is a word for goods: a restaurant has a
 * menu, a salon, a tailor and a fuel station have something to offer, a baker's counter is fresh today. Every trade
 * is placed in a family ON PURPOSE below, so a trade added to the picker later fails here until somebody has
 * decided what its poster should say, instead of quietly saying "new stock has arrived" for a barber.
 */
class NewStockWordsTest {

    private val goods = setOf(
        Trade.AGRI, Trade.MEDICAL, Trade.MOBILE, Trade.KIRYANA, Trade.HARDWARE, Trade.ELECTRONICS, Trade.STATIONERY,
        Trade.BUILDING, Trade.SOLAR, Trade.AUTO, Trade.WHOLESALE, Trade.GENERAL, Trade.OTHER
    )
    private val fresh = setOf(Trade.DAIRY, Trade.BAKERY, Trade.MEAT, Trade.PRODUCE)
    private val collection = setOf(Trade.CLOTHING, Trade.FOOTWEAR, Trade.COSMETICS, Trade.FURNITURE)
    private val menu = setOf(Trade.RESTAURANT)
    private val service = setOf(Trade.SALON, Trade.TAILOR, Trade.FUEL)

    private val byFamily = mapOf(
        Family.GOODS to goods,
        Family.FRESH to fresh,
        Family.COLLECTION to collection,
        Family.MENU to menu,
        Family.SERVICE to service
    )

    @Test
    fun everyTradeIsPlacedOnPurposeInExactlyOneFamily() {
        val placed = byFamily.values.flatten()
        assertEquals("a trade is listed twice: ${placed.groupBy { it }.filter { it.value.size > 1 }.keys}", placed.size, placed.toSet().size)
        assertEquals("a trade has no family here", Trade.values().toSet(), placed.toSet())
    }

    @Test
    fun eachTradeLandsInItsFamily() {
        for ((family, trades) in byFamily) {
            for (trade in trades) {
                assertEquals(trade.name, family, NewStockWords.family(trade.name.lowercase()))
            }
        }
    }

    @Test
    fun theKeyIsReadWithoutCareOfCaseOrSpaces() {
        assertEquals(Family.MENU, NewStockWords.family(" Restaurant "))
        assertEquals(Family.SERVICE, NewStockWords.family("SALON"))
        assertEquals(Family.FRESH, NewStockWords.family("\tbakery\n"))
    }

    @Test
    fun aTradeNobodyNamedSellsGoodsAsBefore() {
        assertEquals(Family.GOODS, NewStockWords.family(""))
        assertEquals(Family.GOODS, NewStockWords.family("no-such-trade"))
    }
}
