package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** The default rule is the whole point: an existing book must still read as the agri shop it was. */
class TradeTest {

    @Test
    fun unsetUnknownOrBlankIsAgri() {
        assertEquals(Trade.AGRI, Trade.of(null))
        assertEquals(Trade.AGRI, Trade.of(""))
        assertEquals(Trade.AGRI, Trade.of("BAKERY"))
    }

    @Test
    fun storedNameRoundTripsCaseInsensitively() {
        for (t in Trade.entries) assertEquals(t, Trade.of(t.name))
        assertEquals(Trade.KIRYANA, Trade.of(" kiryana "))
    }
}
