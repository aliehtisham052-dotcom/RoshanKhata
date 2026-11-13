package com.innovation313.roshankhata.data

/**
 * How Hudhud's words are put to the phone's voice (13 Nov 2026, the owner: "apna naam bhi sahi tarah bolna nahi aata…
 * lafz achhi tarah ada hona chahiye", and "awaaz professional ho, gap kabhi kam kabhi zyada"). Only what is SAID
 * changes; the card keeps its own words.
 *
 *  - **His name** is written the way each language's voice reads it best, and the owner can hear the ways and keep
 *    the one that sounds right on his phone ([nameWays]; every phone's voice is its own). A voice reads "ہُدہُد"
 *    by guessing its vowels; "ہُود ہُود" carries them in its letters.
 *  - **Calm, even delivery**: "!" is said as a full stop (an exclamation lifts the voice and stretches the pause
 *    after it), "…" too, and runs of spaces are one.
 *  - **Money** is said in words of the language: "Rs 140,868" is "140868 روپے", not "R S" nor "140 … 868" (only the shop's rupee
 *    signs; another sign stays as written, which voices read as they know it).
 *  - **A range** "8 Nov – 12 Nov" says the language's "to" for the dash.
 */
object HudhudSay {

    /** Every way his name is written on a card, in the app's scripts (with and without its vowel marks). */
    private val WRITTEN = Regex(
        "(?i)(?<![\\p{L}\\p{M}])(hudhud|hud-hud|ہُدہُد|ہدہد|هُدهُد|هدهد|هُدْهُد|हुदहुद|হুদহুদ)(?![\\p{L}\\p{M}])"
    )

    /**
     * The ways [language]'s voice can be given his name, the one used unless the owner chose another first. The
     * vowel is written out where a voice could guess it wrong: Urdu's "ہُود ہُود" (the long u of Prophet Hud's
     * name), English "Hood-hood", Hindi and Bengali split in two so neither half takes an extra vowel.
     */
    fun nameWays(language: String): List<String> = when (language) {
        "en" -> listOf("Hood-hood", "Hud-hud", "Hoodhood")
        "ur" -> listOf("ہُود ہُود", "ہُد ہُد", "ہدہد")
        "sd" -> listOf("هُود هُود", "هُد هُد", "هدهد")
        "ar" -> listOf("هُدْهُد", "هدهد", "هُد هُد")
        "fa" -> listOf("هُدهُد", "هدهد", "هُد هُد")
        "hi" -> listOf("हुद-हुद", "हुदहुद", "हूद हूद")
        "bn" -> listOf("হুদ-হুদ", "হুদহুদ", "হূদ হূদ")
        "in", "id" -> listOf("Hud-hud", "Hudhud", "Hud hud")
        else -> listOf("Hudhud")
    }

    /** The language's word for the dash of a range of days or figures ("8 Nov – 12 Nov"). */
    private fun toWord(language: String): String? = when (language) {
        "en" -> "to"
        "ur" -> "سے"
        "sd" -> "کان"
        "ar" -> "إلى"
        "fa" -> "تا"
        "hi" -> "से"
        "bn" -> "থেকে"
        "in", "id" -> "sampai"
        else -> null
    }

    /** "Rs 140,868", "Rs.500", "₹ 1,250.50" — the shop's rupee signs before a figure. */
    private val RUPEES = Regex("(?<![\\p{L}])(?:Rs\\.?|₹)\\s?([0-9](?:[0-9,.]*[0-9])?)")

    /** "1,218", "SAR 12,400", "1,59,426": digits grouped by commas, the last group three digits. */
    private val COMMA_GROUPED = Regex("(?<![\\d.,])\\d{1,3}(?:,\\d{2,3})*,\\d{3}(?![\\d,])")

    /**
     * A figure as a voice must get it, whole (13 Nov 2026, the owner on his phone: "Rs 141,768" was said as 141 rupees and
     * 768 — the voice took the grouping comma for a break or a decimal mark). Grouping marks go; a mark followed by one or
     * two digits at the end is the decimal mark (written as "."), and decimals of only zeros go: "141,768" is "141768",
     * "1,59,426" is "159426", "1,250.50" is "1250.50", Indonesian "1.250,50" is "1250.50", "500.00" is "500".
     */
    internal fun plainFigure(figure: String): String {
        val last = figure.indexOfLast { it == ',' || it == '.' }
        val tail = if (last >= 0) figure.substring(last + 1) else ""
        val decimal = last >= 0 && tail.length in 1..2 && tail.all { it.isDigit() } &&
            (figure.count { it == figure[last] } == 1)
        val whole = (if (decimal) figure.substring(0, last) else figure).filter { it.isDigit() }.ifEmpty { "0" }
        return if (decimal && tail.any { it != '0' }) "$whole.$tail" else whole
    }

    /**
     * [text] as [language]'s voice should get it: his name the [nameWay] way (null: the language's first),
     * exclamations calm, a range's dash in words, the rupee sign as [rupees] ("%s روپے"; null leaves it).
     */
    fun forVoice(text: String, language: String, nameWay: Int? = null, rupees: ((String) -> String)? = null): String {
        val ways = nameWays(language)
        val name = ways.getOrNull(nameWay ?: 0) ?: ways.first()
        var out = WRITTEN.replace(text) { name }
        if (rupees != null) out = RUPEES.replace(out) { m -> rupees(plainFigure(m.groupValues[1])) }
        // Any other figure grouped with commas (another currency, a count): the commas go, for the same reason.
        out = COMMA_GROUPED.replace(out) { m -> m.value.replace(",", "") }
        toWord(language)?.let { to -> out = out.replace(Regex("\\s[\\u2013\\u2014]\\s"), " $to ") }
        out = out.replace('!', '.').replace('！', '.').replace("…", ".").replace(Regex("\\.{2,}"), ".")
            .replace(Regex("[ \\t\\u00A0]+"), " ").trim()
        return out
    }
}
