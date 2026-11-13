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
