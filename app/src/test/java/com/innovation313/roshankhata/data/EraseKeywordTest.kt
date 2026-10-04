package com.innovation313.roshankhata.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The word that confirms "erase everything" ([EraseAll.confirms]). */
class EraseKeywordTest {

    @Test
    fun `the word shown is accepted, whatever the case or the spaces around it`() {
        assertTrue(EraseAll.confirms("ERASE", "ERASE"))
        assertTrue(EraseAll.confirms("  erase ", "ERASE"))
        assertTrue(EraseAll.confirms("Mitao", "MITAO"))
        assertTrue(EraseAll.confirms("hapus", "HAPUS"))
    }

    @Test
    fun `each language's own word is accepted`() {
        assertTrue(EraseAll.confirms("حذف", "حذف"))
        assertTrue(EraseAll.confirms("ختم", "ختم"))
        assertTrue(EraseAll.confirms("مسح", "مسح"))
        assertTrue(EraseAll.confirms("मिटाओ", "मिटाओ"))
        assertTrue(EraseAll.confirms("মুছুন", "মুছুন"))
    }

    @Test
    fun `an invisible joiner from the keyboard does not spoil it`() {
        assertTrue(EraseAll.confirms("حذف\u200C", "حذف"))
        assertTrue(EraseAll.confirms("\u200Fمسح", "مسح"))
    }

    @Test
    fun `the English word works in every language, for a phone with only an English keyboard`() {
        assertTrue(EraseAll.confirms("ERASE", "حذف"))
        assertTrue(EraseAll.confirms("erase", "मिटाओ"))
    }

    @Test
    fun `nothing, a part of the word, or another word does not confirm`() {
        assertFalse(EraseAll.confirms(null, "ERASE"))
        assertFalse(EraseAll.confirms("", "ERASE"))
        assertFalse(EraseAll.confirms("   ", "حذف"))
        assertFalse(EraseAll.confirms("ERAS", "ERASE"))
        assertFalse(EraseAll.confirms("मिटा", "मिटाओ"))
        assertFalse(EraseAll.confirms("HAPUS", "MITAO"))
    }
}
