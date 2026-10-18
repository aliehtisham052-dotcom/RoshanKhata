package com.innovation313.roshankhata.words

/**
 * The nine languages the app speaks, each by its own name, in the order of
 * the phone's language screen (LanguageActivity). [tag] is what the phone
 * stores as the app's language: "ur-Latn" is Roman Urdu, "id" Indonesian.
 */
object AppLanguages {
    data class Language(val tag: String, val nativeName: String)

    val ALL = listOf(
        Language("en", "English"),
        Language("ur-Latn", "Roman Urdu"),
        Language("ur", "اردو"),
        Language("sd", "سنڌي"),
        Language("fa", "فارسی"),
        Language("ar", "العربية"),
        Language("hi", "हिन्दी"),
        Language("bn", "বাংলা"),
        Language("id", "Indonesia")
    )
}
