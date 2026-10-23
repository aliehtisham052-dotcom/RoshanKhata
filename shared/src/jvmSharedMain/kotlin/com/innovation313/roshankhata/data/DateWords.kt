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

    /**
     * Weekday names, Sunday first (21 Oct 2026): the computer's Home title
     * read "Wednesday" on a Hindi, Bengali or Indonesian screen because the
     * formatter is built on English symbols. Taken from Unicode's CLDR
     * (JDK 21's tables), kept here so a phone without them prints the same.
     */
    private val WEEKDAYS: Map<String, Array<String>> = mapOf(
        "ur" to arrayOf("اتوار", "پیر", "منگل", "بدھ", "جمعرات", "جمعہ", "ہفتہ"),
        "sd" to arrayOf("آچر", "سومر", "اڱارو", "اربع", "خميس", "جمعو", "ڇنڇر"),
        "ar" to arrayOf("الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت"),
        "fa" to arrayOf("یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه"),
        "hi" to arrayOf("रविवार", "सोमवार", "मंगलवार", "बुधवार", "गुरुवार", "शुक्रवार", "शनिवार"),
        "bn" to arrayOf("রবিবার", "সোমবার", "মঙ্গলবার", "বুধবার", "বৃহস্পতিবার", "শুক্রবার", "শনিবার"),
        "id" to arrayOf("Minggu", "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu")
    )

    /** Roman Urdu's days, as its posters write them; its months stay English. */
    private val ROMAN_WEEKDAYS = arrayOf("Itwar", "Peer", "Mangal", "Budh", "Jumeraat", "Jumma", "Hafta")

    /** Before noon / after noon, where the language writes its own. */
    private val AM_PM: Map<String, Array<String>> = mapOf(
        "ar" to arrayOf("ص", "م"),
        "fa" to arrayOf("ق.ظ.", "ب.ظ."),
        // "AM" and "PM" are English letters on an Urdu, Sindhi, Hindi or
        // Bengali line. These are each language's own formal pair, "before
        // noon" and "after noon". Indonesian is left with AM/PM: it has no
        // pair of its own in use (it writes the 24-hour clock), and those
        // are Latin letters on a Latin-letter screen. Roman Urdu reads the
        // English table throughout.
        "ur" to arrayOf("قبل دوپہر", "بعد دوپہر"),
        "sd" to arrayOf("صبح", "منجهند"),
        "hi" to arrayOf("पूर्वाह्न", "अपराह्न"),
        "bn" to arrayOf("পূর্বাহ্ণ", "অপরাহ্ণ")
    )

    /**
     * A weekday's whole name from the app's own tables (Calendar.SUNDAY = 1
     * … Calendar.SATURDAY = 7), or null where the language has none here
     * (English, which the phone's own tables always carry). For the poster
     * (23 Oct 2026, the review): an older phone without Sindhi data printed
     * Latin "Mon" in a Sindhi panel.
     */
    fun weekday(locale: Locale, calDay: Int): String? {
        val roman = locale.language.equals("ur", ignoreCase = true) && locale.script.equals("Latn", ignoreCase = true)
        val days = if (roman) ROMAN_WEEKDAYS else WEEKDAYS[keyOf(locale)]
        return days?.getOrNull(calDay - 1)
    }

    /** Before noon and after noon in [locale]'s own words, or null where AM/PM is right. */
    fun amPm(locale: Locale): Array<String>? = AM_PM[keyOf(locale)]?.copyOf()

    /**
     * Which table a locale reads from: its language, except that Roman Urdu
     * (ur written in Latin letters) reads English, and Indonesian answers to
     * both of its codes ("id", and Android's old "in").
     */
    fun keyOf(locale: Locale): String {
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
    fun appLocale(): Locale = Locale.getDefault()

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
        val roman = locale.language == "ur" && locale.script.equals("Latn", ignoreCase = true)
        return formatterFor(localPunctuation(clock(pattern), key), key, roman)
    }

    /**
     * The 24-hour clock where the shop's country writes it (Iran, Indonesia,
     * Turkey, the UK; 21 Oct 2026): every "h:mm a" in the app becomes
     * "HH:mm" there, in one place, so no screen or sheet has to know.
     */
    fun clock(pattern: String): String =
        if (ShopCountry.current.clock24 && pattern.contains("h:mm a")) pattern.replace("h:mm a", "HH:mm") else pattern

    /**
     * Arabic-script languages write their own comma, "،" (U+060C). Every
     * pattern in the app separates date from time with ", ", which on the
     * owner's Urdu and Sindhi screens read "7 اکتوبر 2026, 1:15" — a Latin
     * comma inside an Urdu line. The separator is swapped here, once, so
     * the list, the customer screen, the date picker and every report agree.
     * Roman Urdu keys as "en" and keeps the Latin comma.
     */
    fun localPunctuation(pattern: String, key: String): String =
        if (key in ARABIC_COMMA) pattern.replace(", ", "\u060C ") else pattern

    private val ARABIC_COMMA = setOf("ur", "sd", "ar", "fa")

    private fun formatterFor(pattern: String, key: String, roman: Boolean = false): SimpleDateFormat {
        val kind = ShopCalendar.current
        // The shop's calendar (5 Oct) applies to dates that name a DAY. A
        // pattern with no day ("MMM", "MMM yyyy", "h:mm a") labels a Gregorian
        // bucket — a month of the sales chart — or a time, and a Hijri name on
        // a Gregorian month would be a wrong label, so those stay as they are.
        if (kind != ShopCalendar.Kind.GREGORIAN && hasDay(pattern)) {
            return cache.get()!!.getOrPut("$key|$roman|$pattern|${kind.key}") {
                CalendarDateFormat(pattern, gregorian(pattern, key, roman), kind, ShopCalendar.monthNames(kind, key))
            }
        }
        return cache.get()!!.getOrPut("$key|$roman|$pattern") { gregorian(pattern, key, roman) }
    }

    /** True when [pattern] has a day-of-month letter outside quotes. */
    fun hasDay(pattern: String): Boolean {
        var quoted = false
        for (c in pattern) {
            if (c == '\'') quoted = !quoted else if (!quoted && c == 'd') return true
        }
        return false
    }

    private fun gregorian(pattern: String, key: String, roman: Boolean = false): SimpleDateFormat =
            SimpleDateFormat(pattern, Locale.ENGLISH).apply {
                val names = MONTHS[key]
                val amPm = AM_PM[key]
                val days = if (roman) ROMAN_WEEKDAYS else WEEKDAYS[key]
                if (names != null || amPm != null || days != null) {
                    dateFormatSymbols = DateFormatSymbols(Locale.ENGLISH).apply {
                        if (days != null) {
                            // Slot 0 is unused: the calendar counts Sunday as 1.
                            val eight = Array(8) { i -> if (i == 0) "" else days[i - 1] }
                            weekdays = eight
                            shortWeekdays = eight
                        }
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

    fun format(pattern: String, millis: Long, locale: Locale = appLocale()): String =
        formatter(pattern, locale).format(Date(millis))
}
