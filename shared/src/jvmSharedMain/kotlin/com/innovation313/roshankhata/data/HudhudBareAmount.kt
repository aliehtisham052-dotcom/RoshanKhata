package com.innovation313.roshankhata.data

/**
 * A bare amount asked after, with nobody, nothing and no side said (26 Nov 2026, BC-356; D15 of the owner's rules): "500 ka kya
 * bana?", "what about the 500?", "500 کا کیا ہوا", "500 টাকার কী হল". Whose 500, and which way, the sentence does not say: it was a
 * help page found by its figure (the percent page), the day's dealings, or the payment voice's page. It is asked first, as a
 * debt said without its side is ([Hudhud]'s side question): "lene hain ya dene?", the shop's to get and its to give each a chip,
 * nothing answered and never an entry opened.
 *
 * Read only when the sentence is nothing but the amount (digits in any script, with "ال" joined in Arabic), a word of money
 * ("rupay", "روپے", "টাকার", "تومان"…) and one of the phrases below: any other word — a name, a product, a verb of giving, a span —
 * leaves the sentence to the readers that know it. Sindhi, Bengali, Farsi and Arabic phrases: native read.
 */
internal object HudhudBareAmount {

    /**
     * When [said] (normalised words, as [Hudhud] splits a sentence) is an amount asked after: the words left beside the amount, the
     * phrase and the little words of "of" — empty for a bare amount, a name's words for "Ali ke 500 ka kya bana"; null when the
     * sentence is not this shape (no amount, no phrase, or more than three other words). [Hudhud] decides whose the words are.
     */
    fun whose(said: List<String>): List<String>? {
        var amount = false
        val rest = ArrayList<String>()
        for (w in said) {
            when {
                isAmount(w) -> amount = true
                w in MONEY -> {}
                else -> rest += w
            }
        }
        if (!amount || rest.isEmpty()) return null
        val joined = rest.joinToString(" ")
        val phrase = PHRASES.firstOrNull { p -> joined == p || joined.endsWith(" $p") || joined.startsWith("$p ") } ?: return null
        val left = when {
            joined == phrase -> ""
            joined.endsWith(" $phrase") -> joined.removeSuffix(" $phrase")
            else -> joined.removePrefix("$phrase ")
        }
        val who = left.split(' ').filter { it.isNotEmpty() && it !in OF }
        return who.takeIf { it.size <= 3 }
    }

    /** Whether [said] is a bare amount asked after, nobody's ([whose] with no words left). */
    fun asked(said: List<String>): Boolean = whose(said)?.isEmpty() == true

    /** Digits (any script's), perhaps with Arabic's article or a "rs"/"rp" joined: "500", "۵۰۰", "ال500", "rs500". */
    private fun isAmount(w: String): Boolean {
        val t = Digits.toLatin(w).removePrefix("ال").removePrefix("rs").removePrefix("rp").removeSuffix("rs")
        return t.isNotEmpty() && t.all { it in '0'..'9' }
    }

    private fun n(words: List<String>): Set<String> = words.map { VoiceQuestion.normalise(it) }.toSet()

    private fun longestFirst(words: List<String>): List<String> = words.map { VoiceQuestion.normalise(it) }.distinct().sortedByDescending { it.length }

    /** Words of money and "the one of" that may stand beside the amount. */
    private val MONEY = n(listOf(
        "rs", "rupay", "rupaye", "rupee", "rupees", "rupaiya", "rupaiye", "pkr", "wale", "wala", "waale", "wali",
        "روپے", "روپیہ", "روپئے", "روپوں", "والے", "والا", "रुपये", "रुपए", "रुपयों", "रूपये", "वाले",
        "টাকা", "টাকার", "রুপি", "রুপির", "رپيا", "روپيا", "رپين", "تومان", "ریال", "ريال", "دينار", "درهم", "جنيه",
        "rp", "rupiah", "the"
    ))

    /** The little words of "of" and "the" between a name and its amount ("Ali ke 500", "500 of Ali's"). */
    private val OF = n(listOf("ke", "ki", "ka", "kay", "کے", "کی", "کا", "के", "की", "का", "এর", "র", "جو", "جا", "جي", "of", "s", "the", "را", "رو", "milik", "punya"))

    /** What is asked of the amount: "what became of", in the nine languages, as the words are said after it (or before, in English). */
    private val PHRASES = longestFirst(listOf(
        // Roman Urdu
        "ka kya bana", "ka kya hua", "ka kia bana", "ka kia hua", "ki kya bani", "ke kya bane", "ka kya bna", "kya bana", "kya hua",
        // English
        "what about", "and what about", "what happened to", "what happened with", "what became of", "what about my",
        // Urdu
        "کا کیا بنا", "کا کیا ہوا", "کی کیا بنی", "کے کیا بنے", "کیا بنا", "کیا ہوا",
        // Hindi
        "का क्या हुआ", "का क्या बना", "की क्या बनी", "के क्या बने", "क्या हुआ",
        // Bengali (native read)
        "কী হল", "কি হল", "কী হলো", "কি হলো", "এর কী হল", "এর কি হল", "কী হলো এর",
        // Arabic (native read)
        "ماذا عن", "ما أخبار", "ما اخبار", "ماذا حدث", "ماذا حصل", "وماذا عن",
        // Farsi (native read)
        "چی شد", "چه شد", "چی شدن", "چه شدند", "رو چی شد", "را چه شد",
        // Sindhi (native read)
        "جو ڇا ٿيو", "جا ڇا ٿيا", "ڇا ٿيو",
        // Indonesian
        "bagaimana dengan", "gimana dengan", "gimana", "bagaimana", "kabarnya"
    ))
}
