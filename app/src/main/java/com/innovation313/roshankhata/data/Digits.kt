package com.innovation313.roshankhata.data

import java.util.Locale

/**
 * Which digits the app writes and reads: always 0-9, in every language.
 *
 * Java's formatter takes its digits from the default locale, and the app's
 * language IS the default locale. For English and Urdu that locale's digits
 * are 0-9, so nothing ever looked wrong in testing. For Sindhi and Arabic
 * they are ٠-٩, for Persian ۰-۹, and three kinds of damage followed:
 *
 *  - an amount printed as "Rs ٢٧٬٦٢٨" beside a keypad that types 27628, so
 *    the owner checks a figure against digits he did not type;
 *  - invoice and receipt numbers ("INV-%06d", "RK-%06d") STORED in the
 *    database with those digits — permanent, printed, and unsearchable by
 *    anyone typing 0-9;
 *  - CSV figures a spreadsheet does not read as numbers at all, which is
 *    the only reason the CSV exists.
 *
 * And the reverse: a Persian or Arabic keyboard types ۲۷۶ or ٢٧٦, which
 * Kotlin's toDoubleOrNull() rejects, so a correct figure is refused as if
 * nothing were typed.
 *
 * One rule, stated once: figures are formatted with [FIGURES]; dates keep
 * their language's month names but take 0-9 through [latinIn]; anything a
 * person typed passes through [parse] (or [toLatin] first) before it is
 * read as a number.
 */
object Digits {

    /**
     * The locale every printed or stored FIGURE is formatted with.
     *
     * Locale.US rather than ROOT: it groups with "," and uses "." for the
     * decimal point, which is exactly what English and Urdu screens showed
     * before this existed — so for them nothing changes by a single pixel.
     */
    val FIGURES: Locale = Locale.US

    /**
     * [locale] with its digits forced to 0-9, everything else kept.
     *
     * For dates: "23 ستمبر" keeps the Urdu month and just stops the day
     * from becoming ٢٣. Built with the standard Unicode "nu-latn"
     * (numbering system: Latin) keyword, which Android's ICU and the JVM
     * both honour. A locale the builder cannot take falls back to English
     * rather than failing a screen over a date.
     */
    fun latinIn(locale: Locale = Locale.getDefault()): Locale =
        runCatching {
            Locale.Builder()
                .setLocale(locale)
                .setUnicodeLocaleKeyword("nu", "latn")
                .build()
        }.getOrElse { Locale.ENGLISH }

    /**
     * Arabic-Indic (٠-٩, Arabic and Sindhi keyboards) and Extended
     * Arabic-Indic (۰-۹, Persian and some Urdu keyboards) digits turned
     * into 0-9, with the Arabic decimal separator (٫) made "." and the
     * Arabic thousands separator (٬) dropped, as a typed "," never
     * reached the parser either. Everything else is left exactly as it
     * was, so the calculator's operators and a percent sign pass through.
     */
    fun toLatin(text: String): String {
        if (text.none { it in '\u0660'..'\u066C' || it in '\u06F0'..'\u06F9' }) return text
        val out = StringBuilder(text.length)
        for (c in text) {
            when (c) {
                in '\u0660'..'\u0669' -> out.append('0' + (c - '\u0660'))
                in '\u06F0'..'\u06F9' -> out.append('0' + (c - '\u06F0'))
                '\u066B' -> out.append('.')      // Arabic decimal separator
                '\u066A' -> out.append('%')      // Arabic percent sign, for the calculator
                '\u066C' -> Unit                 // Arabic thousands separator
                else -> out.append(c)
            }
        }
        return out.toString()
    }

    /**
     * A typed number, in whichever digits the keyboard used.
     *
     * Same contract as the `text.toString().trim().toDoubleOrNull()` it
     * replaces — null for blank or unreadable input — so every caller's
     * existing "is it missing / is it positive" check keeps its meaning.
     */
    fun parse(text: CharSequence?): Double? =
        text?.toString()?.trim()?.let { toLatin(it) }?.toDoubleOrNull()
}
