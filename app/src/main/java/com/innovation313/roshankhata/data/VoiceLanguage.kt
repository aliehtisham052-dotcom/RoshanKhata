package com.innovation313.roshankhata.data

/**
 * Which language the microphone should listen in.
 *
 * The recogniser was asked for Urdu and nothing else — the tag "ur-PK" was
 * written into the intent by hand. An owner who set the app to Arabic, Sindhi,
 * Persian or English still had their phone listening for Urdu, and the app
 * gave no sign that this was happening.
 *
 * The language now follows the one the owner chose on the language screen.
 *
 * Two things are deliberate here:
 *
 * Roman Urdu asks for Urdu. There is no such thing as a Roman Urdu voice —
 * the words are the same words, only written in Latin letters. The recogniser
 * returns Urdu script and [VoiceEntry] reads either script, so nothing is
 * lost.
 *
 * Nothing is assumed about what a phone can do. Which languages a recogniser
 * carries differs by phone, by region and by what the owner has downloaded,
 * and no list written here would stay true. The phone is asked instead, and
 * when the answer is not available yet the preferred tag is used as the best
 * guess rather than a claim.
 */
object VoiceLanguage {

    /**
     * What was settled on.
     *
     * [tag] is null when nothing suitable was found, meaning the intent should
     * carry no language at all and the phone should fall back to its own
     * setting. [exact] is false when this is not the language the owner asked
     * for — the caller is expected to say so rather than listen in a language
     * the owner did not choose and leave them guessing why nothing works.
     */
    data class Choice(val tag: String?, val exact: Boolean)

    /** The app's home language, and the last resort before the phone's own. */
    const val HOME = "ur-PK"

    /**
     * Preferred speech tags per app language, best first.
     *
     * More than one region is listed because a phone that has a language at
     * all may not have the region this app would pick. Any region of the right
     * language is still the right language.
     */
    private val PREFERRED = mapOf(
        "en" to listOf("en-PK", "en-IN", "en-GB", "en-US"),
        "ur" to listOf("ur-PK", "ur-IN"),
        "ur-latn" to listOf("ur-PK", "ur-IN"),
        "ar" to listOf("ar-SA", "ar-AE", "ar-EG"),
        "fa" to listOf("fa-IR"),
        "sd" to listOf("sd-PK", "sd-IN")
    )

    private fun key(tag: String) = tag.lowercase().replace('_', '-')

    /** App languages whose recogniser answers in Arabic script. */
    private val ARABIC_SCRIPT = setOf("ur", "ur-latn", "ar", "fa", "sd")

    /** Below this many customers a book has not said anything about itself. */
    private const val ENOUGH_NAMES = 20

    private const val MOSTLY = 0.70
    private const val HARDLY = 0.30

    /** Arabic, Urdu, Persian and Sindhi letters all live in this block. */
    private fun isArabicScript(c: Char) = c in '\u0600'..'\u06FF'

    /**
     * The share of names written in Arabic script, of those written in any
     * script at all — or null when not one name carries a letter, which is a
     * book with no opinion rather than a Latin one. A customer saved as a bare
     * phone number says nothing about either script.
     */
    private fun arabicShare(names: List<String>): Double? {
        var arabic = 0
        var counted = 0
        for (name in names) {
            val hasArabic = name.any { isArabicScript(it) }
            val hasLatin = name.any { it in 'a'..'z' || it in 'A'..'Z' }
            if (!hasArabic && !hasLatin) continue
            counted++
            if (hasArabic) arabic++
        }
        return if (counted == 0) null else arabic.toDouble() / counted
    }

    /**
     * The language to listen in, judged by the book rather than by the menu.
     *
     * Three rounds of real entries settled this. Asked in Urdu, the recogniser
     * answers in Urdu script — correctly, and the figures parse perfectly. But
     * this shop's customers are written down in Latin letters, and a name in
     * one script barely reaches a name in the other: "منگی" put Manga Matyky
     * at position 652 of 1163, "تیرو رکشے والے" put its customer at 1044, and
     * the sentence that found Ihsan Munchi at position one in English found it
     * at fourteen in Urdu. Nothing was misheard in any of them.
     *
     * The one Urdu attempt that landed first proves the rule rather than
     * breaking it: that customer is stored in Urdu script.
     *
     * So the script the names are KEPT in decides, not the language of the
     * menus. A shopkeeper who reads the app in Urdu but writes his customers
     * in Latin is served by an English recogniser, and one who writes them in
     * Urdu is served by an Urdu one, without either of them being asked a
     * question they have no way to answer.
     *
     * The owner's own choice stands unless the book is clearly one script or
     * the other, and a book too small to have an opinion is left alone.
     */
    fun forBook(appTag: String, names: List<String>): String {
        if (names.size < ENOUGH_NAMES) return appTag
        val share = arabicShare(names) ?: return appTag
        val k = key(appTag)
        return when {
            k == "en" && share >= MOSTLY -> "ur"
            k in ARABIC_SCRIPT && share <= HARDLY -> "en"
            else -> appTag
        }
    }

    /** The speech tags worth trying for [appTag], best first. */
    fun preferred(appTag: String): List<String> =
        PREFERRED[key(appTag)] ?: listOf(appTag)

    /**
     * The tag to listen in, given what this phone reports it can do.
     *
     * Pass null for [supported] when the phone has not answered yet: the
     * preferred tag is returned and marked exact, because it is the best that
     * can be said, not because anything has been checked.
     */
    fun choose(appTag: String, supported: List<String>?): Choice {
        val prefs = preferred(appTag)
        if (supported == null) return Choice(prefs.first(), true)

        val have = supported.map { key(it) }

        // The tag this app would pick, if the phone has it.
        prefs.firstOrNull { key(it) in have }?.let { return Choice(it, true) }

        // Any region of the same language is still the right language.
        val language = key(prefs.first()).substringBefore('-')
        supported.firstOrNull { key(it).substringBefore('-') == language }
            ?.let { return Choice(it, true) }

        // The owner's language is not on this phone. Fall back, and say so.
        supported.firstOrNull { key(it) == key(HOME) }?.let { return Choice(it, false) }
        return Choice(null, false)
    }
}
