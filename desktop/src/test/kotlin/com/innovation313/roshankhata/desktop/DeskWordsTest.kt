package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.PaymentRadar
import com.innovation313.roshankhata.data.ReminderLadder
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words
import com.innovation313.roshankhata.words.desktopWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * The computer's own words for the words it shares with the phone (17 Nov 2026, [DeskWords]): Hudhud's answers and the
 * Radar's reasons named "this phone" on the computer. A twin is picked by its name alone, so each must take the same
 * arguments as the word it stands for — in every language — or a formatted answer would fail on the computer only.
 */
class DeskWordsTest {

    private val languages = listOf("en", "ur", "ur-Latn", "sd", "ar", "fa", "hi", "bn", "in")

    /** The twins this change added: shared words that said "this phone" (or "a counter phone") on the computer. */
    private val added = listOf(
        "hudhud_privacy_once", "hudhud_hello", "hudhud_chat_who", "hudhud_chat_bye", "hudhud_about_app",
        "hudhud_about_privacy", "hudhud_counted_since", "radar_private", "hudhud_limit_manager", "hudhud_limit_owner"
    )

    private fun placeholders(s: String): List<String> =
        Regex("%(\\d+\\$)?[sdf]").findAll(s).map { it.value }.sorted().toList()

    @Test
    fun `every twin takes the same arguments as its word, in all nine languages`() {
        for (tag in languages) {
            val words = desktopWords(Locale.forLanguageTag(tag))
            val names = words.names()
            val twins = names.filter { it.endsWith(DeskWords.PC) && it.removeSuffix(DeskWords.PC) in names }
            assertTrue("$tag: the twins are read", twins.size >= added.size)
            for (twin in twins) {
                val base = twin.removeSuffix(DeskWords.PC)
                assertEquals("$tag $twin", placeholders(words.textRaw(base)), placeholders(words.textRaw(twin)))
            }
        }
    }

    @Test
    fun `each added twin is in all nine languages and in the computer's words`() {
        for (tag in languages) {
            val names = desktopWords(Locale.forLanguageTag(tag)).names()
            for (k in added) assertTrue("$tag has ${k}_pc", (k + DeskWords.PC) in names)
        }
        val en = desktopWords(Locale.US)
        for (k in added) {
            val text = en.textRaw(k + DeskWords.PC)
            assertFalse("$k: $text", text.contains("phone", ignoreCase = true))
        }
    }

    @Test
    fun `a word with a twin says the twin, any other word is as it was`() {
        val en = desktopWords(Locale.US)
        val desk = DeskWords.of(en)
        assertEquals(en.text(W.hudhud_privacy_once_pc), desk.text(W.hudhud_privacy_once))
        assertEquals(en.text(W.hudhud_counted_since_pc, "1 Nov"), desk.text(W.hudhud_counted_since, "1 Nov"))
        // A twin asked for by its own name is not looked for again ("_pc_pc").
        assertEquals(en.text(W.hudhud_intro_pc), desk.text(W.hudhud_intro_pc))
        // A word with no twin, a plural and a list pass through untouched.
        assertEquals(en.text(W.radar_title), desk.text(W.radar_title))
        assertSame(desk, DeskWords.of(desk))
        // Words that are not the strings files (a test's stand-in) are handed back as they are.
        val stub = object : Words {
            override fun text(name: String, vararg args: Any) = name
            override fun plural(name: String, count: Int, vararg args: Any) = name
            override fun list(name: String) = listOf(name)
        }
        assertSame(stub, DeskWords.of(stub))
    }

    @Test
    fun `the Radar dialog and Hudhud say the reasons by one rule, on this computer`() {
        // One text for both (17 Nov 2026: two copies). The lines as the dialog always said them, the last now the computer's.
        val desk = DeskWords.of(desktopWords(Locale.US))
        val r = PaymentRadar.Reading(
            light = PaymentRadar.Light.AMBER, repaid = 5, typicalDays = 12, shopDays = 1, oldestOpenDays = 40, payDay = 5,
            nextFrom = null, nextTo = null, promiseOverdue = false, balance = 100.0, behindHabit = true,
            promisesKept = 1, promisesBroken = 2
        )
        val text = radarReasonsText(desk, r, null, ReminderLadder.PLAIN)
        assertEquals(
            listOf(
                "• Often pays late",
                "• Usually pays back in 12 days",
                "• Your customers usually take 1 day",
                "• Oldest unpaid amount has waited 40 days",
                "• Usually pays around day 5 of the month",
                "• Later than his usual payments",
                "• Promises kept: 1 of 3",
                "• Reminder that worked last: Plain",
                "",
                desktopWords(Locale.US).text(W.radar_private_pc)
            ),
            text.split("\n")
        )
    }
}
