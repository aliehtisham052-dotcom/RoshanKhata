package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.StockCover.Cover
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** How many days a shelf lasts (15 Nov 2026, #30): the figure, and every case where there is no figure to give. */
class StockCoverTest {
    @Test fun `a shelf is divided by the daily pace and rounded down`() {
        // 90 sold in 30 days is 3 a day; 100 on the shelf lasts 33 whole days, never 34.
        assertEquals(Cover.Days(33, 3.0), StockCover.of(100.0, 90.0))
        assertEquals(Cover.Days(0, 3.0), StockCover.of(2.0, 90.0))
        assertEquals(Cover.Days(10, 1.0), StockCover.of(10.0, 30.0))
    }

    @Test fun `no shelf, no sales and no figure are three different answers`() {
        assertEquals(Cover.NoStock, StockCover.of(0.0, 90.0))
        assertEquals(Cover.NoStock, StockCover.of(-4.0, 90.0))
        assertEquals(Cover.NoSales, StockCover.of(10.0, 0.0))
        assertEquals(Cover.Unknown, StockCover.of(null, 90.0))
        assertEquals(Cover.Unknown, StockCover.of(10.0, null))
        assertEquals(Cover.Unknown, StockCover.of(Double.NaN, 5.0))
        assertEquals(Cover.Unknown, StockCover.of(5.0, Double.POSITIVE_INFINITY))
        assertEquals(Cover.Unknown, StockCover.of(5.0, 5.0, windowDays = 0))
    }

    @Test fun `a very slow pace is capped, not overflowed`() {
        val c = StockCover.of(1.0e12, 0.0001)
        assertEquals(StockCover.MAX_DAYS, (c as Cover.Days).days)
    }

    @Test fun `a window of another length changes the pace`() {
        assertEquals(Cover.Days(20, 5.0), StockCover.of(100.0, 35.0, windowDays = 7))
    }

    @Test fun `no rows is nothing sold, rows that cannot be counted in the shelf's unit are not nothing`() {
        assertEquals(0.0, StockCover.soldBase(emptyList(), null, "bag")!!, 0.0)
        // Sold in kilos, shelf in bags, no pack to tie them: the kilos are left out and nothing is countable.
        assertNull(StockCover.soldBase(listOf(ProductStat("Urea", 50.0, "kg", 3)), null, "bag"))
        // Same unit counts; a row with no unit counts too.
        assertEquals(15.0, StockCover.soldBase(listOf(ProductStat("Urea", 10.0, "bag", 2), ProductStat("Urea", 5.0, null, 1)), null, "bag")!!, 0.0)
        assertTrue(StockCover.WINDOW_MS == 30L * 24 * 60 * 60 * 1000)
    }
}
