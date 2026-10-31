package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** The default rule is the whole point: an existing book must still read as the agri shop it was. */
class TradeTest {

    @Test
    fun unsetUnknownOrBlankIsAgri() {
        assertEquals(Trade.AGRI, Trade.of(null))
        assertEquals(Trade.AGRI, Trade.of(""))
        assertEquals(Trade.AGRI, Trade.of("SPACESHIP"))
    }

    @Test
    fun storedNameRoundTripsCaseInsensitively() {
        for (t in Trade.entries) assertEquals(t, Trade.of(t.name))
        assertEquals(Trade.KIRYANA, Trade.of(" kiryana "))
    }

    @Test
    fun pickerShowsEveryTradeOnceWithTheTownsShopsFirst() {
        // 31 Oct 2026: the app is for every shop; agriculture first said otherwise.
        assertEquals(Trade.entries.toSet(), Trade.PICKER.toSet())
        assertEquals(Trade.entries.size, Trade.PICKER.size)
        assertEquals(Trade.GENERAL, Trade.PICKER.first())
        assertEquals(Trade.OTHER, Trade.PICKER.last())
    }
}
