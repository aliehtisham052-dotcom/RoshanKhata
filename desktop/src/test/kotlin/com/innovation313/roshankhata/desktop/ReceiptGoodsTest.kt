package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.EntryItem
import com.innovation313.roshankhata.data.RateType
import com.innovation313.roshankhata.data.ReceiptGoods
import com.innovation313.roshankhata.words.desktopWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/**
 * The computer's receipt card says an entry's goods exactly as the phone's does (Marhala 4, part 6b: the phone's
 * GoodsText.receipt moved to the shared ReceiptGoods). The same made-up visit as the phone's GoodsTextTest, the
 * same lines expected.
 */
class ReceiptGoodsTest {
    private val words = desktopWords(Locale.ENGLISH)

    private val visit = listOf(
        EntryItem(entryId = 1, itemName = "Urea", quantity = 2.0, unit = "bori", rate = 4800.0),
        EntryItem(entryId = 1, itemName = "Sulphur", quantity = 5.0, unit = "bag", rate = 750.0),
        EntryItem(entryId = 1, itemName = "Sulphur", quantity = 1.0, unit = "bag", isBonus = true)
    )

    @Test
    fun `a single old-style item reads exactly as before`() {
        val old = listOf(EntryItem(entryId = 1, itemName = "Urea", quantity = 2.0, unit = "bori"))
        assertEquals("2 bori — Urea", ReceiptGoods.text(words, old, null, 9600.0))
        assertNull(ReceiptGoods.text(words, emptyList(), null, 0.0))
    }

    @Test
    fun `the receipt prices each item and shows the discount`() {
        val text = ReceiptGoods.text(words, visit, RateType.CREDIT, 13000.0)!!.lines()
        assertEquals("1. 2 bori — Urea × Rs 4,800 = Rs 9,600", text[0])
        assertEquals("2. 5 bag — Sulphur × Rs 750 = Rs 3,750", text[1])
        assertEquals("3. 1 bag — Sulphur (free)", text[2])
        assertEquals("Items total Rs 13,350 · Credit rate", text[3])
        assertEquals("Items Rs 13,350 · written Rs 13,000 · Rs 350 less", text[4])
    }

    @Test
    fun `the dealer's advice rides under its item, said to be advice`() {
        val advised = listOf(EntryItem(entryId = 1, itemName = "Emamectin", quantity = 2.0, unit = "litre", rate = 1200.0, crop = "Cotton", pest = "Whitefly", dose = "250 ml per acre"))
        val text = ReceiptGoods.text(words, advised, RateType.CASH, 2400.0)!!.lines()
        assertEquals("    Advice: Cotton · Whitefly · 250 ml per acre", text[1])
        assertEquals("Advice only, not a guarantee.", text[2])
        assertEquals("Items total Rs 2,400 · Cash rate", text[3])
    }
}
