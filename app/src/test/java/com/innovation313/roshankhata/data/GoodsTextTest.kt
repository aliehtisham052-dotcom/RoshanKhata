package com.innovation313.roshankhata.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.innovation313.roshankhata.ui.GoodsText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** The goods text on the khata list and the receipt (default English strings). */
@RunWith(AndroidJUnit4::class)
class GoodsTextTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val visit = listOf(
        EntryItem(entryId = 1, itemName = "Urea", quantity = 2.0, unit = "bori", rate = 4800.0),
        EntryItem(entryId = 1, itemName = "Sulphur", quantity = 5.0, unit = "bag", rate = 750.0),
        EntryItem(entryId = 1, itemName = "Sulphur", quantity = 1.0, unit = "bag", isBonus = true)
    )

    @Test
    fun aSingleOldStyleItemReadsExactlyAsBefore() {
        val old = listOf(EntryItem(entryId = 1, itemName = "Urea", quantity = 2.0, unit = "bori"))
        assertEquals("2 bori — Urea", GoodsText.row(context, old, null))
        assertEquals("2 bori — Urea", GoodsText.receipt(context, old, null, 9600.0))
        assertNull(GoodsText.row(context, emptyList(), null))
    }

    @Test
    fun theListRowNamesTheItemsAndCountsThem() {
        assertEquals(
            "Urea, Sulphur, Sulphur\nItems: 3 · Credit rate",
            GoodsText.row(context, visit, RateType.CREDIT)
        )
    }

    @Test
    fun theReceiptPricesEachItemAndShowsTheDiscount() {
        val text = GoodsText.receipt(context, visit, RateType.CREDIT, 13000.0)!!.lines()
        assertEquals("1. 2 bori — Urea × Rs 4,800 = Rs 9,600", text[0])
        assertEquals("2. 5 bag — Sulphur × Rs 750 = Rs 3,750", text[1])
        assertEquals("3. 1 bag — Sulphur (free)", text[2])
        assertEquals("Items total Rs 13,350 · Credit rate", text[3])
        assertEquals("Items Rs 13,350 · written Rs 13,000 · Rs 350 less", text[4])
    }
}
