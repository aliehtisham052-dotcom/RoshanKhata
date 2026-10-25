package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.EntryItem
import com.innovation313.roshankhata.data.LastRate
import com.innovation313.roshankhata.data.LineDraft
import com.innovation313.roshankhata.data.Product
import com.innovation313.roshankhata.data.RateType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The computer's entry form and its goods (Marhala 4, part 6: the phone's list; before it, 25 Oct 2026, the
 * audit's C3 — an edit wiped the free mark and the dealer's spray advice). Made-up goods only.
 */
class EntryLineEditTest {

    private val advised = EntryItem(
        id = 41, entryId = 7, lineNo = 0, itemName = "Emamectin", quantity = 2.0, unit = "litre", rate = 1_200.0,
        isBonus = true, productId = 9, crop = "Cotton", pest = "Whitefly", dose = "250 ml per acre"
    )
    private val urea = EntryItem(id = 42, entryId = 7, lineNo = 1, itemName = "Urea", quantity = 3.0, unit = "bag", rate = 4_550.0, productId = 3)
    private val dap = Product(id = 5, name = "DAP", nameKey = "dap", normalisedName = "dap", defaultUnit = "bag", salePrice = 3_500.0, creditPrice = 3_700.0)

    @Test
    fun `an edited entry keeps every line, its free mark and its advice`() {
        val goods = GoodsList.of(listOf(advised, urea))
        val saved = goods.all(null).mapNotNull { it.toItem() }
        assertEquals(listOf("Emamectin", "Urea"), saved.map { it.itemName })
        assertTrue(saved[0].isBonus)
        assertEquals("Cotton", saved[0].crop)
        assertEquals("250 ml per acre", saved[0].dose)
        assertEquals("a saved rate is the owner's", true, goods.lines[1].rateEdited)
    }

    @Test
    fun `an item pulled back keeps its advice and returns to its own place`() {
        val goods = GoodsList.of(listOf(advised, urea))
        val was = goods.pullBack(0)!!
        assertEquals(listOf("Urea"), goods.lines.map { it.itemName })
        val corrected = boxesLine("Emamectin", 3.0, "litre", 1_200.0, rateTyped = true, product = null, batchId = null, was = was)!!
        assertEquals("Whitefly", corrected.pest)
        assertEquals("the product stays while the name is the same", 9L, corrected.productId)
        goods.add(corrected)
        assertEquals(listOf("Emamectin", "Urea"), goods.lines.map { it.itemName })
        assertEquals(3.0, goods.lines[0].quantity!!, 0.0)
    }

    @Test
    fun `a new name lets go of the product but keeps the advice`() {
        val goods = GoodsList.of(listOf(advised))
        val was = goods.pullBack(0)!!
        val renamed = boxesLine("Lambda", 2.0, "litre", 900.0, rateTyped = true, product = null, batchId = null, was = was)!!
        assertNull(renamed.productId)
        assertEquals("Cotton", renamed.crop)
    }

    @Test
    fun `leaving a correction puts the item back unchanged, and empty boxes are no line`() {
        val goods = GoodsList.of(listOf(advised, urea))
        goods.pullBack(1)
        goods.putBack()
        assertEquals(listOf("Emamectin", "Urea"), goods.lines.map { it.itemName })
        assertNull(goods.pulled)
        assertNull(boxesLine("  ", null, null, null, rateTyped = false, product = null, batchId = null, was = null))
    }

    @Test
    fun `removing an item above the one being corrected keeps its place`() {
        val goods = GoodsList.of(listOf(advised, urea, EntryItem(entryId = 7, itemName = "Zinc", quantity = 1.0)))
        goods.pullBack(2)
        goods.remove(0)
        assertEquals(1, goods.editingIndex)
        goods.add(boxesLine("Zinc", 2.0, null, null, rateTyped = false, product = null, batchId = null, was = goods.pulled)!!)
        assertEquals(listOf("Urea", "Zinc"), goods.lines.map { it.itemName })
    }

    @Test
    fun `the amount follows the items until the owner writes his own`() {
        val two = listOf(LineDraft("DAP", 2.0, "bag", 3_700.0), LineDraft("Urea", 1.0, "bag", 4_550.0))
        assertEquals("a figure typed before any goods is replaced by the first total", "11950", followedAmount(two, "8787", own = false, byItems = false))
        assertNull("the owner's own figure over goods stays", followedAmount(two, "11500", own = true, byItems = false))
        assertNull("already the total", followedAmount(two, "11950", own = false, byItems = true))
        assertEquals("the items' figure goes with the last item", "", followedAmount(emptyList(), "11950", own = false, byItems = true))
        assertNull("a plain entry's typed amount is never cleared", followedAmount(emptyList(), "500", own = false, byItems = false))
        assertNull("no total while a quantity is missing", followedAmount(listOf(LineDraft("DAP", null, "bag", 3_700.0)), "", own = false, byItems = false))
        assertNull("a total of nothing is not an amount", followedAmount(listOf(LineDraft("DAP", 2.0, "bag", 0.0)), "", own = false, byItems = false))
    }

    @Test
    fun `a switch to cash moves only the rates that came from the product`() {
        val fromProduct = LineDraft("DAP", 2.0, "bag", 3_700.0, productId = 5, creditPrice = 3_700.0, cashPrice = 3_500.0, productUnit = "bag")
        val bargained = fromProduct.copy(rate = 3_600.0, rateEdited = true)
        val goods = GoodsList(listOf(fromProduct, bargained))
        goods.reprice(RateType.CASH, isCustomer = true)
        assertEquals(3_500.0, goods.lines[0].rate!!, 0.0)
        assertEquals(3_600.0, goods.lines[1].rate!!, 0.0)
    }

    @Test
    fun `the rate offered is the product's for the chip, or a return's last price, never a guess`() {
        assertEquals(3_700.0, offeredRate(isSale = true, isReturn = false, product = dap, rateType = RateType.CREDIT, typedUnit = "bag", lastAny = null)!!, 0.0)
        assertEquals(3_500.0, offeredRate(isSale = true, isReturn = false, product = dap, rateType = RateType.CASH, typedUnit = "", lastAny = null)!!, 0.0)
        assertNull("priced per bag, not per kg", offeredRate(isSale = true, isReturn = false, product = dap, rateType = RateType.CREDIT, typedUnit = "kg", lastAny = null))
        assertNull("no cash price set", offeredRate(isSale = true, isReturn = false, product = dap.copy(salePrice = null), rateType = RateType.CASH, typedUnit = "bag", lastAny = null))
        assertEquals(3_650.0, offeredRate(isSale = false, isReturn = true, product = dap, rateType = RateType.CREDIT, typedUnit = "bag", lastAny = LastRate(3_650.0, "bag"))!!, 0.0)
        assertNull("a return per another unit", offeredRate(isSale = false, isReturn = true, product = dap, rateType = RateType.CREDIT, typedUnit = "kg", lastAny = LastRate(3_650.0, "bag")))
        assertNull("a supplier's goods are not priced", offeredRate(isSale = false, isReturn = false, product = dap, rateType = RateType.CREDIT, typedUnit = "bag", lastAny = null))
    }

    @Test
    fun `the other lines read with their free mark`() {
        val text = goodsLineText(EntryItem(entryId = 7, itemName = "Urea", quantity = 1.0, isBonus = true), "free")
        assertTrue(text, text.startsWith("Urea") && text.endsWith("free"))
    }
}
