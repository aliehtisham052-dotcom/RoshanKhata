package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/**
 * Units: shown in the app's language, saved as one key ([UnitWords]).
 * The rule under test is the one the stock depends on: whatever the owner
 * sees or types, one unit is saved one way.
 */
class UnitWordsTest {

    private val tags = listOf("en", "ur", "ur-Latn", "sd", "fa", "ar", "hi", "bn", "id")

    @Test
    fun `a saved key is shown in the app's language`() {
        assertEquals("बोरी", UnitWords.label("bag", Locale.forLanguageTag("hi")))
        assertEquals("বস্তা", UnitWords.label("bag", Locale.forLanguageTag("bn")))
        assertEquals("karung", UnitWords.label("bag", Locale.forLanguageTag("id")))
        assertEquals("بوری", UnitWords.label("bag", Locale.forLanguageTag("ur")))
        assertEquals("bori", UnitWords.label("bag", Locale.forLanguageTag("ur-Latn")))
        assertEquals("bag", UnitWords.label("bag", Locale.ENGLISH))
        assertEquals("karung", UnitWords.label("bag", Locale("in")))
    }

    @Test
    fun `the owner's own word is shown as he wrote it, in every language`() {
        for (tag in tags) assertEquals(tag, "tin", UnitWords.label("tin", Locale.forLanguageTag(tag)))
        assertEquals("", UnitWords.label(null, Locale.ENGLISH))
        assertEquals("", UnitWords.label("  ", Locale.ENGLISH))
    }

    @Test
    fun `what is picked in any language is saved as the same key`() {
        for (tag in tags) {
            val locale = Locale.forLanguageTag(tag)
            val shown = UnitWords.choices(locale)
            assertEquals(tag, UnitWords.KEYS.size, shown.size)
            for ((i, key) in UnitWords.KEYS.withIndex()) {
                assertEquals("$tag ${shown[i]}", key, UnitWords.canonical(shown[i]))
            }
        }
    }

    @Test
    fun `a word typed in one language is understood under another`() {
        assertEquals("bag", UnitWords.canonical("بوری"))
        assertEquals("bag", UnitWords.canonical(" Bori "))
        assertEquals("kg", UnitWords.canonical("किलो"))
        assertEquals("bottle", UnitWords.canonical("botol"))
        assertEquals("bag", UnitWords.canonical("BAG"))
    }

    @Test
    fun `an unknown unit is saved as typed, and nothing typed is nothing`() {
        assertEquals("tin", UnitWords.canonical(" tin "))
        assertEquals("تھیلا", UnitWords.canonical("تھیلا"))
        assertNull(UnitWords.canonical(""))
        assertNull(UnitWords.canonical("   "))
        assertNull(UnitWords.canonical(null))
    }
}
