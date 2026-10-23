package com.innovation313.roshankhata.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * When fixed-height buttons may grow (23 Oct review, R04): larger text on
 * every phone, and Arabic-script languages on Android 15+ where the elegant
 * font's taller lines cannot be turned off for an app targeting 36.
 */
class TextFitTest {

    private fun tag(t: String): Locale = Locale.forLanguageTag(t)

    @Test
    fun `larger text relaxes on every phone and language`() {
        assertTrue(TextFit.needed(1.15f, 24, tag("en")))
        assertTrue(TextFit.needed(1.30f, 36, tag("ur-Latn")))
    }

    @Test
    fun `Arabic script languages relax at normal size on Android 15 and later`() {
        for (t in listOf("ur", "ur-PK", "sd", "ar", "fa")) {
            assertTrue(t, TextFit.needed(1.0f, 35, tag(t)))
            assertTrue(t, TextFit.needed(1.0f, 36, tag(t)))
        }
    }

    @Test
    fun `below Android 15 the compact font stays and nothing changes`() {
        for (t in listOf("ur", "sd", "ar", "fa")) assertFalse(t, TextFit.needed(1.0f, 34, tag(t)))
    }

    @Test
    fun `Roman Urdu and the other languages keep the designed heights`() {
        for (t in listOf("ur-Latn", "en", "hi", "bn", "in", "id")) assertFalse(t, TextFit.needed(1.0f, 36, tag(t)))
        assertFalse(TextFit.needed(1.0f, 36, null))
        assertFalse(TextFit.needed(0.9f, 36, tag("en")))
    }
}
