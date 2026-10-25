package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.EntryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Editing an entry on the computer (25 Oct 2026, the audit): the first goods
 * line was rebuilt from what the form shows, so the free mark and the
 * dealer's spray advice were wiped, and a line with a product but no name was
 * dropped. Made-up goods only.
 */
class EntryLineEditTest {

    private val advised = EntryItem(
        id = 41, entryId = 7, itemName = "Emamectin", quantity = 2.0, unit = "litre", rate = 1_200.0,
        isBonus = true, productId = 9, crop = "Cotton", pest = "Whitefly", dose = "250 ml per acre"
    )

    @Test
    fun `an edit keeps the free mark and the spray advice`() {
        val saved = editedLine(advised, entryId = 7, name = "Emamectin", quantity = 3.0, unit = "litre", rate = 1_200.0, billItemId = null)!!
        assertEquals(3.0, saved.quantity!!, 0.0)
        assertTrue(saved.isBonus)
        assertEquals("Cotton", saved.crop)
        assertEquals("Whitefly", saved.pest)
        assertEquals("250 ml per acre", saved.dose)
        assertEquals("the product stays while the name is the same", 9L, saved.productId)
        assertNotEquals("a rebuilt line takes a new uid, as every editor's lines do", advised.uid, saved.uid)
    }

    @Test
    fun `a new name lets go of the product but keeps the advice`() {
        val saved = editedLine(advised, entryId = 7, name = "Lambda", quantity = 2.0, unit = "litre", rate = 900.0, billItemId = null)!!
        assertNull(saved.productId)
        assertEquals("Cotton", saved.crop)
    }

    @Test
    fun `a line with a product and no name is kept`() {
        val productOnly = EntryItem(id = 42, entryId = 7, itemName = null, quantity = 1.0, productId = 11)
        val saved = editedLine(productOnly, entryId = 7, name = "", quantity = 1.0, unit = null, rate = null, billItemId = null)
        assertNotNull(saved)
        assertEquals(11L, saved!!.productId)
        assertEquals(productOnly.uid, saved.uid)
    }

    @Test
    fun `a name the owner cleared removes the line, and an empty new form has none`() {
        assertNull(editedLine(advised, entryId = 7, name = "  ", quantity = 2.0, unit = "litre", rate = 1_200.0, billItemId = null))
        assertNull(editedLine(null, entryId = 0, name = "", quantity = null, unit = null, rate = null, billItemId = null))
    }

    @Test
    fun `a new entry's line is just what was typed`() {
        val saved = editedLine(null, entryId = 0, name = "Urea", quantity = 1.0, unit = "bag", rate = 4_550.0, billItemId = null)!!
        assertEquals("Urea", saved.itemName)
        assertEquals(false, saved.isBonus)
        assertNull(saved.crop)
    }

    @Test
    fun `the other lines read with their free mark`() {
        val text = goodsLineText(EntryItem(entryId = 7, itemName = "Urea", quantity = 1.0, isBonus = true), "free")
        assertTrue(text, text.startsWith("Urea") && text.endsWith("free"))
    }
}
