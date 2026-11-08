package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Names in the reader's alphabet, not in Unicode's (21 Oct 2026). */
class NameOrderTest {
    private fun order(vararg names: String): List<String> = NameOrder.sortedBy(names.toList()) { it }

    @Test
    fun `urdu letters sort where the alphabet puts them`() {
        // پ follows ب and ٹ follows ت; code-point order put both after ي.
        assertEquals(listOf("احمد", "بلال", "پرویز", "تنویر", "ٹیپو", "یوسف"), order("یوسف", "ٹیپو", "پرویز", "تنویر", "بلال", "احمد"))
    }

    @Test
    fun `persian and sindhi letters likewise`() {
        assertEquals(listOf("احمد", "کریم", "وحید"), order("وحید", "کریم", "احمد"))
        assertEquals(listOf("احمد", "ڪريم", "وحيد"), order("وحيد", "ڪريم", "احمد"))
    }

    @Test
    fun `case, vowel marks and the arabic or persian spelling of a letter do not separate names`() {
        assertEquals(listOf("ali", "Bilal", "Zara"), order("Zara", "ali", "Bilal"))
        assertEquals(0, NameOrder.byName.compare("عَلی", "علي"))
        assertEquals(0, NameOrder.byName.compare("کریم", "كريم"))
        assertEquals(listOf("Ali", "", "  "), NameOrder.sortedBy(listOf("", "Ali", "  ")) { it })
    }

    @Test
    fun `latin first, then arabic script, then hindi and bengali, each in its own alphabet`() {
        assertEquals(listOf("Bilal", "Zara", "علی", "अमित", "कमल", "राम", "আলী", "করিম", "রহিম"),
            order("राम", "রহিম", "علی", "Zara", "कमल", "করিম", "Bilal", "अमित", "আলী"))
    }
}
