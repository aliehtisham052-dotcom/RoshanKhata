package com.innovation313.roshankhata.words

import com.ibm.icu.text.PluralRules
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * The computer's own plural rules (pluralCategory) against ICU4J, which they
 * replace in the MSI: every whole number 0..10,000 in every language the app
 * offers, plus "in" (Android's Indonesian) and a language it does not offer.
 */
class PluralRulesDesktopTest {
    @Test
    fun `every count reads as ICU reads it, in every language`() {
        val tags = AppLanguages.ALL.map { it.tag } + listOf("in", "de")
        for (tag in tags) {
            val locale = Locale.forLanguageTag(tag)
            val icu = PluralRules.forLocale(locale)
            for (n in 0..10_000) {
                assertEquals("$tag $n", icu.select(n.toDouble()), pluralCategory(locale, n))
            }
        }
    }
}
