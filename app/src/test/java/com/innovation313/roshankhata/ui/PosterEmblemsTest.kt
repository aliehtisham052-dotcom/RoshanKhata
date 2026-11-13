package com.innovation313.roshankhata.ui

import com.innovation313.roshankhata.data.Trade
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The New stock poster's flat crate wears the shop's own glyph (13 Nov 2026). Every trade the picker offers has one:
 * the table is written from res/drawable/ic_trade_*.xml by scripts/gen_desktop_poster.py, which CI checks.
 */
class PosterEmblemsTest {

    @Test
    fun everyTradeHasAGlyph() {
        for (trade in Trade.values()) {
            assertTrue(trade.name, PosterEmblems.has(trade.name.lowercase()))
        }
    }

    @Test
    fun aKeyNobodyKnowsHasNoGlyphOfItsOwn() {
        assertTrue(!PosterEmblems.has("no-such-trade"))
    }
}
