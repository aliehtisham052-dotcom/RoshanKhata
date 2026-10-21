package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale

/** The shop's country (21 Oct 2026): what it decides, and how a new shop's is guessed. */
class ShopCountryTest {

    @Test
    fun `every country prints a currency the picker offers, with its own decimals`() {
        for (c in ShopCountry.entries) {
            assertTrue(c.name, Currency.CHOICES.any { it.first == c.currencySign })
            assertEquals(c.name, c.currencyDecimals, Currency.decimals(c.currencySign))
            assertTrue(c.name, c.weekStart in Calendar.SUNDAY..Calendar.SATURDAY)
            assertTrue(c.name, c.weeklyOff in Calendar.SUNDAY..Calendar.SATURDAY)
        }
    }

    @Test
    fun `a book from before the setting is a Pakistani shop`() {
        assertEquals(ShopCountry.PAKISTAN, ShopCountry.of(null))
        assertEquals(ShopCountry.PAKISTAN, ShopCountry.of(""))
        assertEquals(ShopCountry.BANGLADESH, ShopCountry.of("bd"))
        // A code the table does not know is not "other": it is a book whose key was never written.
        assertEquals(ShopCountry.PAKISTAN, ShopCountry.of("XX"))
    }

    @Test
    fun `a new shop starts in the device's region, else by currency, else by language`() {
        assertEquals(ShopCountry.BANGLADESH, ShopCountry.guess("BD", "Rs", "ur"))
        assertEquals(ShopCountry.SAUDI_ARABIA, ShopCountry.guess(null, "SAR", "ur"))
        assertEquals(ShopCountry.INDIA, ShopCountry.guess(null, "Rs", "hi-IN"))
        assertEquals(ShopCountry.IRAN, ShopCountry.guess(null, "Rs", "fa"))
        assertEquals(ShopCountry.INDONESIA, ShopCountry.guess(null, "Rs", "in"))
        assertEquals(ShopCountry.PAKISTAN, ShopCountry.guess("FR", "Rs", "en"))
        assertEquals(ShopCountry.PAKISTAN, ShopCountry.guess(null, "Rs", "ur-Latn"))
    }

    @Test
    fun `names come from the JDK in the app's language, the known shops first`() {
        assertEquals("Pakistan", ShopCountry.PAKISTAN.displayName(Locale.ENGLISH))
        assertEquals("پاکستان", ShopCountry.PAKISTAN.displayName(Locale.forLanguageTag("ur")))
        val choices = ShopCountry.choices(Locale.ENGLISH)
        assertEquals(ShopCountry.PAKISTAN, choices.first())
        assertEquals(ShopCountry.OTHER, choices.last())
        assertEquals(ShopCountry.entries.size, choices.size)
    }

    @Test
    fun `the clock and the week follow the country`() {
        val before = ShopCountry.current
        try {
            ShopCountry.current = ShopCountry.INDONESIA
            assertEquals("d MMM yyyy, HH:mm", DateWords.clock("d MMM yyyy, h:mm a"))
            ShopCountry.current = ShopCountry.PAKISTAN
            assertEquals("d MMM yyyy, h:mm a", DateWords.clock("d MMM yyyy, h:mm a"))
            assertTrue(PosterOccasionSeen.azadiFor(ShopCountry.PAKISTAN))
            assertTrue(!PosterOccasionSeen.azadiFor(ShopCountry.INDIA))
        } finally {
            ShopCountry.current = before
        }
    }

    /** PosterOccasion lives in the app module; the rule it reads is the country's own flag. */
    private object PosterOccasionSeen {
        fun azadiFor(c: ShopCountry) = c.pakistan
    }
}
