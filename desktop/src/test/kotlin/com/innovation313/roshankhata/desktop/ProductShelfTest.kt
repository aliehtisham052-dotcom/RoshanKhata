package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The computer's product boxes (Marhala 4, part 5) offer what the phone's do: the shop's own products by the
 * shared SmartSuggest rule, the trade's starters only while the shop has fewer than five, never a starter
 * twice, and a picked row leads back to its product (for its unit) by the name rule products use.
 */
class ProductShelfTest {
    private fun product(name: String, unit: String? = null, company: String? = null) =
        Product(name = name, nameKey = name.trim().lowercase(), normalisedName = name.lowercase(), defaultUnit = unit, company = company)

    @Test
    fun `a small shop is offered its products first, then the starters it does not have`() {
        val shelf = ProductShelf(listOf(product("DAP", "bag", "Engro"), product("Confidor", "bottle")), listOf("Urea", "dap", "Zinc"), "Suggested")
        val all = shelf.shelf.rank("", 20).map { it.value }
        assertEquals(listOf("DAP", "Confidor", "Urea", "Zinc"), all)
        assertEquals("Suggested", shelf.shelf.rank("urea").single().subtitle)
        assertEquals("Engro · " + com.innovation313.roshankhata.data.UnitWords.label("bag"), shelf.shelf.rank("dap").first().subtitle)
    }

    @Test
    fun `a shop with five products of its own is offered no starters`() {
        val own = listOf("DAP", "Urea", "Confidor", "Sencor 70WP", "Nitrophos").map { product(it) }
        val shelf = ProductShelf(own, listOf("Zinc"), "Suggested")
        assertTrue(shelf.shelf.rank("zinc").isEmpty())
    }

    @Test
    fun `a picked name leads back to its product, a starter to none`() {
        val shelf = ProductShelf(listOf(product("DAP", "bag")), listOf("Urea"), "Suggested")
        assertEquals("bag", shelf.product("dap ")?.defaultUnit)
        assertNull(shelf.product("Urea"))
    }

    @Test
    fun `the computer's boxes rank by the phone's rule`() {
        // "kunfidor" is how the shop says it: the shared fold reaches Confidor, as the phone's dropdown does.
        val shelf = ProductShelf(listOf(product("Confidor 200SL"), product("Karate 2.5EC")), emptyList(), "")
        assertEquals("Confidor 200SL", shelf.shelf.rank("kunfidor").first().value)
    }
}
