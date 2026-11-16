package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Products page's search (3 Nov 2026): a name, a second name, a barcode or its note. */
class ProductFindTest {

    @Test
    fun `a scanned code finds its product, a part of it only from four digits`() {
        assertTrue(ProductFind.matches("DAP 50 kg", null, "8964000123457", null, "8964000123457"))
        assertTrue(ProductFind.matches("DAP 50 kg", null, " 8964000123457 ", null, "8964000123457 "))
        assertTrue(ProductFind.matches("DAP 50 kg", null, "8964000123457", null, "3457"))
        assertFalse(ProductFind.matches("DAP 50 kg", null, "8964000123457", null, "345"))
        // Digits typed in another script are read as the code's own (an Urdu keyboard's).
        assertTrue(ProductFind.matches("DAP 50 kg", null, "8964000123457", null, "۳۴۵۷"))
        // Before, a code in the box found nothing: names alone were searched.
        assertFalse(ProductFind.matches("DAP 50 kg", null, null, null, "8964000123457"))
    }

    @Test
    fun `the note and the second name are searched as names are`() {
        assertTrue(ProductFind.matches("Confidor 250 ml", null, null, "Rack 3, store room", "rack 3"))
        assertTrue(ProductFind.matches("Confidor 250 ml", "Imidacloprid", null, null, "imidacloprid"))
        assertTrue(ProductFind.matches("Confidor 250 ml", "Bayer", null, null, "bayer"))
        assertFalse(ProductFind.matches("Confidor 250 ml", null, null, null, "rack"))
        assertTrue(ProductFind.matches("Confidor 250 ml", null, null, null, "   "))
    }

    @Test
    fun `a code names one product, never a deleted one or two at once`() {
        val a = Product(id = 1, name = "A", nameKey = "a", normalisedName = "a", barcode = "111222", createdAt = 0)
        val b = Product(id = 2, name = "B", nameKey = "b", normalisedName = "b", barcode = "333444", createdAt = 0)
        val gone = Product(id = 3, name = "C", nameKey = "c", normalisedName = "c", barcode = "555666", createdAt = 0, isDeleted = true)
        assertEquals(1L, ProductFind.byCode(listOf(a, b, gone), " 111222 ")?.id)
        assertNull(ProductFind.byCode(listOf(a, b, gone), "555666"))
        assertNull(ProductFind.byCode(listOf(a, a.copy(id = 9)), "111222"))
        assertNull(ProductFind.byCode(listOf(a, b), ""))
    }

    // The shop's other names for a product (schema 45, the audit's T34): "Urea" found by "یوریا" and "यूरिया".
    @Test
    fun `a product is found by any of the other names the shop wrote for it`() {
        val other = "یوریا، यूरिया"
        assertTrue(ProductFind.matches("Urea", null, null, null, "یوریا", other))
        assertTrue(ProductFind.matches("Urea", null, null, null, "यूरिया", other))
        assertTrue("typed as it is said, the name rule's own slip", ProductFind.matches("Urea", null, null, null, "یوری", other))
        assertFalse("no other names: the other script finds nothing", ProductFind.matches("Urea", null, null, null, "یوریا", null))
        assertFalse("another product's names are its own", ProductFind.matches("DAP", null, null, null, "یوریا", "ڈی اے پی"))
        assertTrue("a name of several words", ProductFind.matches("DAP", null, null, null, "ڈی اے پی", "ڈی اے پی"))
    }

    @Test
    fun `the other names are a short clean list, by comma, Arabic comma, semicolon or a new line`() {
        assertEquals(listOf("یوریا", "यूरिया", "urea", "بوری"), ProductFind.otherNamesOf("یوریا، यूरिया, urea ; UREA\nبوری"))
        assertEquals("یوریا, यूरिया", ProductFind.cleanOtherNames("  یوریا ,, यूरिया  "))
        assertNull(ProductFind.cleanOtherNames(" , "))
        assertNull(ProductFind.cleanOtherNames(null))
        assertEquals(ProductFind.MAX_OTHER_NAMES, ProductFind.otherNamesOf((1..20).joinToString(",") { "n$it" }).size)
        val long = ProductFind.cleanOtherNames((1..12).joinToString(",") { "x".repeat(40) + it })!!
        assertTrue(long.length <= ProductFind.MAX_OTHER_NAMES_CHARS)
        assertTrue("a name is left out whole, never cut", ProductFind.otherNamesOf(long).all { it.length == 41 || it.length == 42 })
    }
}
