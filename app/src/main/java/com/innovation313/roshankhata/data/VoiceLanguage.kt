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
