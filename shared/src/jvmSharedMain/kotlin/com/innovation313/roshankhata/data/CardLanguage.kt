package com.innovation313.roshankhata.data

import java.util.Locale

/**
 * The languages a business card is made in (21 Oct 2026, the owner: "ye kaart
 * 9 languages mein ho"). Until then a card was English or Urdu only.
 *
 * A card's language is its own, not the app's: a Sindhi-speaking owner may
 * hand out an English card. Each language keeps its own name, owner, address,
 * tagline and WhatsApp message; numbers, email and website are the same on
 * every card. The phone and the computer both read this one list.
 *
 * @property code BCP-47 tag (Roman Urdu is ur-Latn: Android's b+ur+Latn).
 * @property native The language's name in its own script, shown on the picker.
 * @property rtl The whole design is drawn in its mirror (Urdu, Sindhi, Arabic, Farsi).
 * @property nastaliq Arabic-script words are set in Nastaliq. Only Urdu: Sindhi,
 *   Arabic and Farsi print in Naskh, the phone's and Windows' own face.
 */
enum class CardLanguage(val code: String, val native: String, val rtl: Boolean, val nastaliq: Boolean = false) {
    ENGLISH("en", "English", false),
    URDU("ur", "اردو", true, nastaliq = true),
    ROMAN_URDU("ur-Latn", "Roman Urdu", false),
    SINDHI("sd", "سنڌي", true),
    ARABIC("ar", "العربية", true),
    FARSI("fa", "فارسی", true),
    HINDI("hi", "हिन्दी", false),
    BENGALI("bn", "বাংলা", false),
    INDONESIAN("id", "Bahasa Indonesia", false);

    /** The locale the card's own words (labels, examples, QR message) are read in. */
    val locale: Locale get() = Locale.forLanguageTag(code)

    /**
     * What this language's own fields are kept under. English keeps the keys
     * cards always used and Urdu its "_ur", so nothing typed before is lost.
     */
    fun key(base: String): String = when (this) {
        ENGLISH -> base
        URDU -> base + "_ur"
        else -> base + "_" + code.lowercase(Locale.ROOT).replace('-', '_')
    }

    /** Written in Latin letters, so the English name is a fair start for the shop's name. */
    val latin: Boolean get() = this == ENGLISH || this == ROMAN_URDU || this == INDONESIAN

    /** A short tag for the form's "on this card" chip. */
    val tag: String get() = when (this) {
        ROMAN_URDU -> "UR·LATN"
        else -> code.uppercase(Locale.ROOT)
    }

    companion object {
        /** A saved code back to its language; null for anything else. */
        fun of(code: String?): CardLanguage? = when (code) {
            null -> null
            "in" -> INDONESIAN
            else -> entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
        }

        /** The language a first card starts in: the app's own when cards speak it, else English. */
        fun forApp(locale: Locale): CardLanguage = when {
            locale.language == "ur" && locale.script == "Latn" -> ROMAN_URDU
            locale.language == "in" || locale.language == "id" -> INDONESIAN
            else -> entries.firstOrNull { it != ROMAN_URDU && it.code == locale.language } ?: ENGLISH
        }
    }
}
