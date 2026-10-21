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

    /**
     * The owner's choice to print a language's own digits (21 Oct 2026, the
     * nine-language review): Arabic and Sindhi ١٢٣, Farsi ۱۲۳, Bengali ১২৩
     * on invoices, statements and reports, where a Saudi, Iranian or
     * Bangladeshi customer reads them. Off by default, as in Odoo and
     * Android: 0-9 is what Pakistan, India, Indonesia and the Gulf print.
     *
     * Only the printed sheet changes. The screen keeps 0-9 beside a keypad
     * that types 0-9, and nothing stored, exported or searched changes.
     * Set from the phone's or the computer's own preference when it opens.
     */
    @Volatile
    var nativeOnPrint: Boolean = false

    /** Languages with digits of their own that a printed sheet may use. */
    fun hasOwnDigits(locale: Locale = Locale.getDefault()): Boolean = zeroOf(locale) != null

    private fun zeroOf(locale: Locale): Char? = when (locale.language) {
        "ar", "sd" -> '\u0660'
        "fa" -> '\u06F0'
        "bn" -> '\u09E6'
        else -> null
    }

    /** [text] as a printed sheet shows it: in the language's own digits when the owner chose so. */
    fun forPrint(text: String, locale: Locale = Locale.getDefault()): String =
        if (nativeOnPrint) native(text, locale) else text

    /**
     * Every figure in [text] in [locale]'s own digits. A word with a Latin
     * letter or an @ in it - an invoice number "INV-000123", an email, a
     * website - stays as written: it is a code, typed and searched in 0-9.
     * Between digits, Arabic script groups with ٬ and marks the decimal with ٫.
     */
    fun native(text: String, locale: Locale = Locale.getDefault()): String {
        val zero = zeroOf(locale) ?: return text
        if (text.none { it in '0'..'9' }) return text
        val arabicMarks = zero != '\u09E6'
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            var j = i
            while (j < text.length && !text[j].isWhitespace()) j++
            val word = text.substring(i, j)
            if (word.any { (it in 'a'..'z') || (it in 'A'..'Z') || it == '@' }) out.append(word)
            else for (k in word.indices) {
                val c = word[k]
                val between = k > 0 && k < word.length - 1 && word[k - 1].isDigit() && word[k + 1].isDigit()
                out.append(
                    when {
                        c in '0'..'9' -> zero + (c - '0')
                        arabicMarks && between && c == ',' -> '\u066C'
                        arabicMarks && between && c == '.' -> '\u066B'
                        else -> c
                    }
                )
            }
            while (j < text.length && text[j].isWhitespace()) out.append(text[j++])
            i = j
        }
        return out.toString()
    }

    // quantity() and string(), which read Android's Resources, are in
    // shared/src/androidMain (DigitsAndroid.kt): the desktop app has no
    // Resources. Called the same way, Digits.quantity(res, ...).
}
