package com.innovation313.roshankhata.data

import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dates in the language the app is running in (4 Oct 2026).
 *
 * Until now almost every date in the app (the ledger, the reports, every
 * PDF) was printed with English month names, in every language, while the
 * date field on the entry form followed the app's language. A shop working
 * in Hindi read "4 Oct 2026, 5:17 PM" under a Hindi heading. The owner's
 * rule is one language on the screen, so the month now reads in it too.
 *
 * WHY THE APP CARRIES ITS OWN MONTH NAMES, and does not ask the phone:
 *
 *  - The phone's own tables depend on its Android version. An older phone
 *    has no Sindhi, and answers with "M10" for October.
 *  - Roman Urdu has no tables at all; the phone falls back to Urdu and
 *    prints an Arabic-script month in the middle of a Latin-script screen.
 *    A Roman Urdu shop says "Oct", so Roman Urdu keeps the English names.
 *  - The same names then appear on every phone and in every PDF, and can
 *    be unit-tested here.
 *
 * Digits stay 0-9 in every language (see [Digits]): the formatter is built
 * on English, and only its month and am/pm words are replaced.
 *
 * Short names where the language has them in common use (Hindi, Bengali,
 * Indonesian), full names where it does not (Urdu, Sindhi, Persian, Arabic:
 * their month names are already short when written).
 */
object DateWords {

    private val ENGLISH = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    private val MONTHS: Map<String, Array<String>> = mapOf(
        "ur" to arrayOf(
            "جنوری", "فروری", "مارچ", "اپریل", "مئی", "جون",
            "جولائی", "اگست", "ستمبر", "اکتوبر", "نومبر", "دسمبر"
        ),
        "sd" to arrayOf(
            "جنوري", "فيبروري", "مارچ", "اپريل", "مئي", "جون",
            "جولاءِ", "آگسٽ", "سيپٽمبر", "آڪٽوبر", "نومبر", "ڊسمبر"
        ),
        "fa" to arrayOf(
            "ژانویه", "فوریه", "مارس", "آوریل", "مه", "ژوئن",
            "ژوئیه", "اوت", "سپتامبر", "اکتبر", "نوامبر", "دسامبر"
        ),
        "ar" to arrayOf(
            "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
            "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
        ),
        "hi" to arrayOf(
            "जन॰", "फ़र॰", "मार्च", "अप्रैल", "मई", "जून",
            "जुल॰", "अग॰", "सित॰", "अक्टू॰", "नव॰", "दिस॰"
        ),
        "bn" to arrayOf(
            "জানু", "ফেব", "মার্চ", "এপ্রিল", "মে", "জুন",
            "জুলাই", "আগস্ট", "সেপ্টে", "অক্টো", "নভে", "ডিসে"
        ),
        "id" to arrayOf(
            "Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des"
        )
    )

    /** Before noon / after noon, where the language writes its own. */
    private val AM_PM: Map<String, Array<String>> = mapOf(
        "ar" to arrayOf("ص", "م"),
        "fa" to arrayOf("ق.ظ.", "ب.ظ.")
    )

    /**
     * Which table a locale reads from: its language, except that Roman Urdu
     * (ur written in Latin letters) reads English, and Indonesian answers to
     * both of its codes ("id", and Android's old "in").
     */
    internal fun keyOf(locale: Locale): String {
        val language = locale.language.lowercase(Locale.ROOT)
        return when {
            language == "ur" && locale.script.equals("Latn", ignoreCase = true) -> "en"
            language == "in" -> "id"
            else -> language
        }
    }

    /**
     * The app's language. The app's chosen language IS the default locale
     * (AppCompat and, from Android 13, the system both set it; [Digits]
     * relies on the same fact), and reading it costs nothing, which matters
     * here: a ledger PDF formats a date on every row.
     */
    internal fun appLocale(): Locale = Locale.getDefault()

    /** The twelve month names for [locale], as the app prints them. */
    fun months(locale: Locale = appLocale()): Array<String> = MONTHS[keyOf(locale)] ?: ENGLISH

    // One formatter per thread, language and pattern: SimpleDateFormat is not
    // safe to share between threads (lists format on the main thread, PDFs on
    // a background one), and building one for every row of a list is waste.
    // The language is part of the key, so a change of language is followed
    // at once, with nothing to clear.
    private val cache = object : ThreadLocal<HashMap<String, SimpleDateFormat>>() {
        override fun initialValue(): HashMap<String, SimpleDateFormat> = HashMap()
    }

    /**
     * A formatter for [pattern] that prints months (and am/pm) in [locale]'s
     * words and every figure in 0-9. Not to be kept in a field: ask each
     * time, so the language in use at that moment is the one printed.
     */
    fun formatter(pattern: String, locale: Locale = appLocale()): SimpleDateFormat {
        val key = keyOf(locale)
        return cache.get()!!.getOrPut("$key|$pattern") {
            SimpleDateFormat(pattern, Locale.ENGLISH).apply {
                val names = MONTHS[key]
                val amPm = AM_PM[key]
                if (names != null || amPm != null) {
                    dateFormatSymbols = DateFormatSymbols(Locale.ENGLISH).apply {
                        if (names != null) {
                            // Thirteen slots: the calendar keeps one for a
                            // thirteenth month that this calendar never uses.
                            val thirteen = Array(13) { i -> if (i < 12) names[i] else "" }
                            shortMonths = thirteen
                            months = thirteen
                        }
                        if (amPm != null) amPmStrings = amPm
                    }
                }
            }
        }
    }

    fun format(pattern: String, millis: Long, locale: Locale = appLocale()): String =
        formatter(pattern, locale).format(Date(millis))
}
