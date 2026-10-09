package com.innovation313.roshankhata.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * A party whose name is only a phone number — saved from a contact that had
 * none, the "?" avatar in the list. On the owner's Urdu, Arabic and Sindhi
 * screens it read "923487239466+": with no letter in it, the name took the
 * paragraph's right-to-left direction and the "+" went to the far end.
 * [Format.name] wraps such a name left-to-right; a name with letters in it
 * is left exactly as typed.
 */
@RunWith(AndroidJUnit4::class)
class FormatNameTest {

    // BidiFormatter embeds the run as LRE … PDF (or LRI … PDI), and on a
    // right-to-left screen adds a right-to-left mark on either side so the
    // "+" is not pulled across the boundary. The embedding is what matters.
    private val number = "+923487239466"
    private val embedded = listOf("\u202A$number\u202C", "\u2066$number\u2069")

    private fun under(tag: String, block: () -> Unit) {
        val saved = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag(tag))
        try { block() } finally { Locale.setDefault(saved) }
    }

    @Test
    fun `a number-only name is wrapped left-to-right on a right-to-left screen`() {
        for (tag in listOf("ur", "ar", "sd", "fa")) under(tag) {
            val out = Format.name(number)
            assertTrue("$tag: expected an LTR embedding, got '$out'", embedded.any { it in out })
        }
    }

    @Test
    fun `on an English screen a number-only name needs no wrap and gets none`() {
        under("en") { assertEquals(number, Format.name(number)) }
    }

    @Test
    fun `a name with letters is left exactly as typed`() {
        for (tag in listOf("en", "ur")) under(tag) {
            for (name in listOf("Aamar Baiee", "(Shari)Touseef Adnan", "بلال بھٹی", "ভাই রহিম", "M. Ashraf 2")) {
                assertEquals(name, Format.name(name))
            }
        }
    }
}
