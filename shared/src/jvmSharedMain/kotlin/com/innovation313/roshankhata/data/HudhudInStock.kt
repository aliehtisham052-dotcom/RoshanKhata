package com.innovation313.roshankhata.data

/**
 * "Is there sugar?", "هل يوجد سكر", "شکر داریم", "apakah ada gula" (26 Nov 2026, BC-357): a question of whether the shop has a thing,
 * with the thing's own word and nothing else. The Arabic "هل يوجد سكر" was the privacy page: "سكر" (sugar), a word the bank never
 * taught, meets "secure" by its consonants ([HudhudMind.skeleton]: s-k-r), so both the rules' concept of safety and the sense's
 * sound of it read a privacy question; the Farsi "شکر داریم" was a thank-you (شکر is also thanks), Indonesian "apakah ada gula"
 * "can you talk". The shape says what is asked: the thing's stock.
 *
 * [thing] gives the words that stand for the thing, or null when the sentence is not this shape: one of the phrases of having
 * (before the thing, or after it) and one or two words besides. [Hudhud] decides what they are — a product of the book (its stock),
 * or a word no reader knows (which product?) — and leaves any other sentence alone. Sindhi, Bengali, Farsi and Arabic: native read.
 */
internal object HudhudInStock {

    fun thing(said: List<String>): List<String>? {
        if (said.size < 2) return null
        val t = said.joinToString(" ")
        for (p in BEFORE) if (t.startsWith("$p ")) return rest(t.removePrefix("$p "))
        for (p in AFTER) if (t.endsWith(" $p")) return rest(dropAsk(t.removeSuffix(" $p")))
        // "كم سكر عندنا", "how much sugar do we have" in Arabic: the thing between "how much" and "we have".
        for (h in HOW_MUCH) for (p in HAVE_AR) if (t.startsWith("$h ") && t.endsWith(" $p")) return rest(t.removePrefix("$h ").removeSuffix(" $p"))
        return null
    }

    /**
     * The thing's words: "any" and the words of "and" left out, and Arabic's "و" (and) joined to a later word taken off it
     * ("سكر وملح وأرز", sugar and salt and rice: 26 Nov 2026, the three were the privacy page). Up to four words: a list of a few
     * things, or one thing of two words; more is another sentence.
     */
    private fun rest(s: String): List<String>? {
        val w = s.split(' ').filter { it.isNotEmpty() && it !in ANY }
            .mapIndexed { i, x -> if (i > 0 && x.startsWith("و") && x.length >= 3) x.substring(1) else x }
            .filter { it !in AND }
        return w.takeIf { it.size in 1..MAX_THINGS }
    }

    private const val MAX_THINGS = 4

    /** A yes-or-no word in front of the thing ("kya", "آیا", "ڇا", "क्या", "কি"), which asks nothing more. */
    private fun dropAsk(s: String): String {
        val first = s.substringBefore(' ')
        return if (first in ASK && ' ' in s) s.substringAfter(' ') else s
    }

    private fun n(words: List<String>): List<String> = words.map { VoiceQuestion.normalise(it) }.sortedByDescending { it.length }

    /** Phrases of having said before the thing. */
    private val BEFORE = n(listOf(
        "is there any", "is there", "are there any", "are there", "do we have any", "do we have", "do you have any", "do you have",
        "have we got any", "have we got", "have you got any", "have you got",
        "هل يوجد لدينا", "هل يوجد عندنا", "هل يوجد", "هل توجد", "هل عندنا", "هل لدينا", "هل عندكم", "هل لديكم", "هل عندك", "هل لديك",
        "هل فيه", "هل عندي", "هل لدي", "يوجد", "توجد",
        "apakah ada", "apakah masih ada", "apa ada", "apa masih ada", "masih ada"
    ))

    /** Phrases of having said after the thing. */
    private val AFTER = n(listOf(
        "available", "available hai", "mojood hai", "maujood hai", "mojud hai", "majood hai", "available he",
        "موجود ہے", "موجود ہیں", "دستیاب ہے", "उपलब्ध है", "मौजूद है", "उपलब्ध हैं",
        "আছে কি", "আছে কী", "আছে কিনা", "পাওয়া যাবে", "داریم", "موجود داریم", "موجود است", "موجود هست", "دارید",
        "موجود آهي", "موجود آهن", "tersedia", "ada"
    ))

    private val ASK = n(listOf("kya", "kia", "کیا", "क्या", "কি", "কী", "آیا", "ڇا", "apakah", "apa")).toSet()
    private val ANY = n(listOf("any", "some", "koi", "کوئی", "कोई", "کوئي", "أي", "اي", "هیچ")).toSet()
    private val AND = n(listOf("and", "or", "aur", "ya", "اور", "یا", "و", "أو", "او", "और", "या", "আর", "ও", "বা", "dan", "atau", "۽", "يا")).toSet()
    private val HOW_MUCH = n(listOf("كم"))
    private val HAVE_AR = n(listOf("عندنا", "لدينا", "يوجد", "يوجد عندنا", "يوجد لدينا"))
}
