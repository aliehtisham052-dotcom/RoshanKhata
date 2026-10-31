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
        assertEquals(ShopCountry.PAKISTAN, ShopCountry.of("PK"))
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

    @Test
    fun `the countries of the nine languages are their own, not Saudi Arabia or India`() {
        // 31 Oct 2026: an Iraqi or Moroccan phone was taken for a Saudi shop (SAR, +966, Hijri).
        assertEquals(ShopCountry.IRAQ, ShopCountry.guess("IQ", "Rs", "ar"))
        assertEquals("964", ShopCountry.IRAQ.dialing)
        assertEquals(Calendar.FRIDAY, ShopCountry.IRAQ.weeklyOff)
        assertEquals(ShopCountry.MOROCCO, ShopCountry.guess("ma", "Rs", "ar"))
        assertEquals(Calendar.SUNDAY, ShopCountry.MOROCCO.weeklyOff)
        assertEquals(ShopCountry.NEPAL, ShopCountry.guess("NP", "Rs", "hi"))
        assertEquals(ShopCountry.NEPAL, ShopCountry.guess(null, "Rs", "ne"))
        assertEquals(Calendar.SATURDAY, ShopCountry.NEPAL.weeklyOff)
        assertEquals(ShopCountry.PALESTINE, ShopCountry.of("PS"))
        assertEquals(3, Currency.decimals("JOD"))
        assertEquals(0, Currency.decimals("IQD"))
        // Every code is its own: no two countries share one.
        val codes = ShopCountry.entries.filter { it != ShopCountry.OTHER }.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun `a blank tax name is the language's own word`() {
        assertTrue(ShopCountry.IRAQ.taxIdName.isEmpty())
        assertEquals("PAN", ShopCountry.NEPAL.taxIdName)
    }

    @Test
    fun `Afghanistan's solar months have their own names`() {
        assertEquals("حمل", ShopCalendar.monthNames(ShopCalendar.Kind.JALALI, "fa", ShopCountry.AFGHANISTAN)[0])
        assertEquals("Hut", ShopCalendar.monthNames(ShopCalendar.Kind.JALALI, "en", ShopCountry.AFGHANISTAN)[11])
        assertEquals("فروردین", ShopCalendar.monthNames(ShopCalendar.Kind.JALALI, "fa", ShopCountry.IRAN)[0])
        // A language without its own Afghan names reads them in Latin letters, never Iran's.
        assertEquals("Hamal", ShopCalendar.monthNames(ShopCalendar.Kind.JALALI, "id", ShopCountry.AFGHANISTAN)[0])
    }

    /** PosterOccasion lives in the app module; the rule it reads is the country's own flag. */
    private object PosterOccasionSeen {
        fun azadiFor(c: ShopCountry) = c.pakistan
    }
}
