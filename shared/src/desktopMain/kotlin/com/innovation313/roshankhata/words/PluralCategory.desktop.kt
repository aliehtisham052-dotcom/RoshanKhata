package com.innovation313.roshankhata.words

import java.util.Locale

/**
 * The CLDR plural category of a whole number, for the app's nine languages
 * (20 Oct). The computer used ICU4J for this one call, a 14.7 MB library in
 * the MSI; whole-number rules for these languages are a few lines (CLDR's
 * plurals.xml, integers only, so v = 0 and i = n). PluralRulesDesktopTest
 * holds every answer to ICU4J's, 0 to 10,000, in every language the app has.
 * A language the app does not offer reads as English, as its words do.
 */
actual fun pluralCategory(locale: Locale, n: Int): String {
    val v = kotlin.math.abs(n.toLong())
    return when (locale.language) {
        // zero: n = 0; one: n = 1; two: n = 2; few: n % 100 = 3..10; many: n % 100 = 11..99.
        "ar" -> when {
            v == 0L -> "zero"
            v == 1L -> "one"
            v == 2L -> "two"
            v % 100 in 3..10 -> "few"
            v % 100 in 11..99 -> "many"
            else -> "other"
        }
        // one: i = 0 or n = 1.
        "hi", "bn", "fa" -> if (v == 0L || v == 1L) "one" else "other"
        // No plural forms: everything is other.
        "id", "in" -> "other"
        // one: i = 1 and v = 0 (en, ur, Roman Urdu), n = 1 (sd); the same for whole numbers.
        else -> if (v == 1L) "one" else "other"
    }
}

/** The computer's words: the strings.xml files packed with the app, under words/. */
fun desktopWords(locale: Locale = Locale.getDefault()): XmlWords =
    XmlWords(locale) { folder, file -> XmlWords::class.java.getResourceAsStream("/words/$folder/$file") }
