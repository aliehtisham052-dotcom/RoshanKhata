package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The microphone must listen in the language the owner chose.
 *
 * It used to listen in Urdu always, because "ur-PK" was written into the
 * intent by hand. Five of the app's six languages were therefore spoken into a
 * recogniser set to a sixth, with nothing on screen to explain it.
 */
class VoiceLanguageTest {

    /** A fairly ordinary phone in Pakistan. */
    private val phone = listOf("en-US", "en-GB", "ur-PK", "ar-SA", "hi-IN", "fa-IR")

    @Test
    fun `each app language asks for its own language`() {
        assertEquals("ur-PK", VoiceLanguage.choose("ur", phone).tag)
        assertEquals("ar-SA", VoiceLanguage.choose("ar", phone).tag)
        assertEquals("fa-IR", VoiceLanguage.choose("fa", phone).tag)
        assertTrue(VoiceLanguage.choose("ar", phone).exact)
    }

    /**
     * Roman Urdu asks for Urdu, and that is correct rather than a fallback.
     * The words are Urdu words; only the letters differ, and the parser reads
     * either script.
     */
    @Test
    fun `roman urdu listens in urdu and counts as exact`() {
        val choice = VoiceLanguage.choose("ur-Latn", phone)
        assertEquals("ur-PK", choice.tag)
        assertTrue(choice.exact)
    }

    /** Pakistan English first, since that is who this app is for. */
    @Test
    fun `english prefers the nearest accent available`() {
        assertEquals("en-PK", VoiceLanguage.choose("en", listOf("en-PK", "en-US")).tag)
        assertEquals("en-GB", VoiceLanguage.choose("en", phone).tag)
    }

    /** A different region of the right language is still the right language. */
    @Test
    fun `any region of the same language will do`() {
        val choice = VoiceLanguage.choose("en", listOf("en-ZA"))
        assertEquals("en-ZA", choice.tag)
        assertTrue(choice.exact)
    }

    /**
     * The owner's language is not on this phone. Something must still be
     * listened to — but it is marked inexact so the app can say so instead of
     * appearing broken.
     */
    @Test
    fun `a missing language falls back and admits it`() {
        val choice = VoiceLanguage.choose("sd", phone)
        assertEquals(VoiceLanguage.HOME, choice.tag)
        assertFalse("the owner must be told", choice.exact)
    }

    /** Not even Urdu on this phone: ask for nothing, let the phone decide. */
    @Test
    fun `with nothing suitable no language is forced`() {
        val choice = VoiceLanguage.choose("ar", listOf("fr-FR"))
        assertNull(choice.tag)
        assertFalse(choice.exact)
    }

    /**
     * Before the phone has answered. The preferred tag is used because it is
     * the best guess available — the same guess as the old hard-coded one, but
     * only until the phone says otherwise.
     */
    @Test
    fun `an unanswered phone gets the preferred tag`() {
        assertEquals("sd-PK", VoiceLanguage.choose("sd", null).tag)
        assertEquals("ur-PK", VoiceLanguage.choose("ur-Latn", null).tag)
    }

    /** Tags arrive in whatever case and separator the phone feels like. */
    @Test
    fun `case and underscores do not matter`() {
        assertEquals("ur-PK", VoiceLanguage.choose("UR", listOf("ur_PK")).tag)
    }

    /** Every language on the language screen must be mapped. */
    @Test
    fun `all six app languages are covered`() {
        for (tag in listOf("en", "ur-Latn", "ur", "sd", "fa", "ar")) {
            assertTrue("$tag has no speech tag", VoiceLanguage.preferred(tag).isNotEmpty())
        }
    }

    // --------------------------------------------- the book decides, not the menu

    private val latinBook = (1..40).map { "Customer $it Khurpa" }
    private val urduBook = (1..40).map { "عمیر باجوہ $it" }

    /**
     * The reason this exists: asked in Urdu, the recogniser answers in Urdu
     * script, and a name kept in Latin is barely reached by it — one real
     * attempt put its customer at position 652 of 1163 with nothing misheard.
     */
    @Test
    fun `a Latin book is listened to in English whatever the menus say`() {
        assertEquals("en", VoiceLanguage.forBook("ur", latinBook))
        assertEquals("en", VoiceLanguage.forBook("ur-Latn", latinBook))
        assertEquals("en", VoiceLanguage.forBook("ar", latinBook))
        assertEquals("en", VoiceLanguage.forBook("sd", latinBook))
    }

    /** And the same rule the other way, which is the half that proves it. */
    @Test
    fun `an Urdu book is listened to in Urdu even from an English app`() {
        assertEquals("ur", VoiceLanguage.forBook("en", urduBook))
    }

    /** A book with no clear leaning leaves the owner's own choice alone. */
    @Test
    fun `a mixed book does not overrule the owner`() {
        val mixed = (1..20).map { "Customer $it" } + (1..20).map { "عمیر باجوہ $it" }
        assertEquals("ur", VoiceLanguage.forBook("ur", mixed))
        assertEquals("en", VoiceLanguage.forBook("en", mixed))
    }

    /** A book too small to have said anything is not read for an opinion. */
    @Test
    fun `a nearly empty book decides nothing`() {
        assertEquals("ur", VoiceLanguage.forBook("ur", listOf("Bilal", "Ahmad")))
        assertEquals("ur", VoiceLanguage.forBook("ur", emptyList()))
    }

    /** Names with no letters at all — a phone number saved as a name — abstain. */
    @Test
    fun `names without letters are not counted either way`() {
        val digits = (1..40).map { "+92348723946$it" }
        assertEquals("ur", VoiceLanguage.forBook("ur", digits))
    }
}
