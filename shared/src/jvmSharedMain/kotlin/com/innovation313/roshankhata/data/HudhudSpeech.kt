package com.innovation313.roshankhata.data

/**
 * Hudhud's words made ready for the ear (13 Nov 2026, the owner: "achhe tareeqe se samjha sake"): what the screen
 * writes for the eye — direction marks around a name, bullets, line breaks — is taken out, and the answer is said a
 * sentence at a time, with a short pause after each, as a person reads aloud. A long help page is said up to a few
 * sentences, and then that the rest is on the screen.
 */
object HudhudSpeech {

    /** Bidi marks and isolates (Format.isolate puts them around a name): a voice may say them or stumble on them. */
    private val CONTROLS = Regex("[\u200E\u200F\u202A-\u202E\u2066-\u2069]")

    /** Marks that list things on the screen and mean nothing said aloud. */
    private val BULLETS = Regex("[\u2022\u00B7\u25AA\u25CF]")

    private val SPACES = Regex("[ \t\u00A0]+")

    /** A sentence ends here when a space or the end follows: "1.5" and "Rs.500" stay whole. */
    private val ENDS = setOf('.', '!', '?', '؟', '۔', '।', '॥')

    /** [text] without the marks the eye needs and the ear does not, its spaces made single. */
    fun clean(text: String): String =
        text.replace(CONTROLS, "").replace(BULLETS, " ").lines()
            .joinToString("\n") { it.replace(SPACES, " ").trim() }
            .trim()

    /** A verse's address written after its words — "(27:26)", "(27:16–17)" — which the eye wants and the ear does not. */
    private val VERSE_REFS = Regex("\\s*\\(\\d{1,3}:\\d{1,3}(?:\\s*[\u2013\\-]\\s*\\d{1,3})?\\)")

    /**
     * [text] with its verse addresses taken out, for the voice (14 Nov 2026): the faith and story cards print "(27:22)"
     * after each telling so the owner can find the verse, and a voice would read it as "twenty-seven, twenty-two". Only
     * this exact shape goes — a time such as "(9:30 pm)" or a name in brackets stays — so it is asked for by the cards
     * that carry addresses, never done to every answer.
     */
    fun withoutVerseRefs(text: String): String = text.replace(VERSE_REFS, "")

    /** The sentences of [text], in order, none empty; one part when it has no sentence end at all. */
    fun parts(text: String): List<String> {
        val s = clean(text)
        val out = ArrayList<String>()
        val sb = StringBuilder()
        fun flush() {
            val p = sb.toString().trim()
            if (p.isNotEmpty()) out += p
            sb.setLength(0)
        }
        for (i in s.indices) {
            val c = s[i]
            if (c == '\n') {
                flush()
                continue
            }
            sb.append(c)
            if (c in ENDS && (i + 1 == s.length || s[i + 1].isWhitespace())) flush()
        }
        flush()
        return out
    }

    /**
     * Whether [heard] stops on a word that joins it to more ("Ali ka", "the price of", "علی کی", "از"), so a pause
     * there is someone thinking of the rest, not the end of the question (13 Nov 2026, Hudhud's own end of speech:
     * he waits a little longer after such a word before he answers).
     */
    fun endsOpen(heard: String): Boolean {
        val last = VoiceQuestion.normalise(heard).split(' ').lastOrNull { it.isNotEmpty() } ?: return false
        return last in JOINING
    }

    /** Words that join a sentence to what comes next, in the app's nine languages and the scripts they are said in. */
    private val JOINING: Set<String> by lazy {
        listOf(
            // Roman Urdu and Hindi in Latin letters
            "ka", "ki", "ke", "ko", "se", "ne", "mein", "me", "aur", "ya", "par", "pe", "tak", "wala", "wali", "wale",
            "jo", "keh",
            // Urdu
            "کا", "کی", "کے", "کو", "سے", "نے", "میں", "اور", "یا", "پر", "تک", "والا", "والی", "والے", "جو",
            // Hindi
            "का", "की", "के", "को", "से", "ने", "में", "और", "या", "पर", "तक", "वाला", "वाली", "वाले", "जो",
            // English
            "of", "the", "a", "an", "and", "or", "to", "for", "from", "in", "on", "at", "with", "my", "his", "her",
            "their", "our", "your", "is", "are", "was", "about", "than",
            // Bengali
            "এবং", "আর", "বা", "থেকে", "জন্য", "সাথে", "এর",
            // Arabic
            "من", "الى", "إلى", "على", "في", "عن", "مع", "او", "أو", "ثم", "يا", "حق",
            // Farsi
            "از", "به", "با", "در", "و", "را", "که", "برای", "تا",
            // Sindhi
            "جو", "جي", "جا", "سان", "۾", "۽", "تي", "کان", "کي",
            // Indonesian
            "dan", "atau", "di", "ke", "dari", "untuk", "yang", "dengan", "si", "punya"
        ).map { VoiceQuestion.normalise(it) }.toSet()
    }

    /**
     * Whole sentences of [text] up to [max] characters (always at least the first), and [more] after them when any
     * were left out — a help page said aloud, its rest on the screen.
     */
    fun upTo(text: String, max: Int, more: String): String {
        val all = parts(text)
        if (all.isEmpty()) return ""
        val kept = ArrayList<String>()
        var length = 0
        for (p in all) {
            if (kept.isNotEmpty() && length + 1 + p.length > max) break
            kept += p
            length += (if (kept.size > 1) 1 else 0) + p.length
        }
        val said = kept.joinToString(" ")
        return if (kept.size < all.size && more.isNotBlank()) "$said $more" else said
    }
}
