package com.innovation313.roshankhata.data

import java.text.Normalizer

/**
 * The A-Z order of names (21 Oct 2026, the nine-language review).
 *
 * Lists were sorted by `name.lowercase()`, which is the order of Unicode
 * code points: Urdu's پ چ ٹ ڈ ک گ ی and Sindhi's ڪ sit at the end of the
 * Arabic block, so "پرویز" came after "یوسف" and Persian "کریم" after
 * "وحید". Microsoft's guideline, in its words: do not assume sorting is
 * alphabetical in code-point order.
 *
 * The JDK's Collator has no Urdu, Sindhi or Persian tailoring (Android's
 * ICU does, Windows' JVM does not), so the order is this file's own and
 * the same on both: letter by letter, each letter weighed by its place in
 * its alphabet. Latin names first, then Arabic-script ones in the
 * Urdu/Sindhi/Persian alphabet (a superset in the order the primers teach:
 * ا ب پ ت ٹ ث ج چ ح خ د ڈ ذ ر ڑ ز ژ س ش ص ض ط ظ ع غ ف ق ک گ ل م ن و ہ ء ی ے,
 * Sindhi's extra letters beside the ones they derive from), then
 * Devanagari and Bengali, whose code points already run alphabetically.
 * Case, Arabic vowel marks, tatweel and the Arabic/Persian spelling of the
 * same letter (ي/ی, ك/ک, ة/ہ) do not separate names.
 */
object NameOrder {

    private const val ALPHABET =
        "اآأإءبٻڀپتٿٺٽٹثجڄڃچڇحخدڌڏڊڈڍذرڙڑزژسشصضطظعغفڦقکڪكگڳڱلمنڻںوؤهھہۀةیيےۓئ"

    private val arabicRank: Map<Char, Int> = ALPHABET.withIndex().associate { (i, c) -> c to i }

    /** The letters the ranking treats as one: Persian/Urdu and Arabic spellings of the same sound. */
    private val same = mapOf('ي' to 'ی', 'ك' to 'ک', 'ة' to 'ہ', 'ۀ' to 'ہ', 'ه' to 'ہ', 'ھ' to 'ہ', 'أ' to 'ا', 'إ' to 'ا', 'آ' to 'ا', 'ؤ' to 'و', 'ئ' to 'ی')

    private fun isMark(c: Char) = c in 'ً'..'ْ' || c == 'ٰ' || c == 'ـ' || c == 'ٓ' || c == 'ٔ' || c == 'ٕ'

    /** One character's weight: its script's band, then its place in that script's alphabet. */
    private fun weight(c: Char): Int = when {
        c in 'a'..'z' -> 0x1000 + (c - 'a')
        c in '0'..'9' -> 0x0100 + (c - '0')
        arabicRank.containsKey(c) -> 0x2000 + arabicRank.getValue(c)
        c in '؀'..'ۿ' || c in 'ݐ'..'ݿ' -> 0x2800 + (c.code - 0x0600)
        c in 'ऀ'..'ॿ' -> 0x3000 + (c.code - 0x0900)
        c in 'ঀ'..'৿' -> 0x3800 + (c.code - 0x0980)
        c == ' ' -> 0x0001
        else -> 0x4000 + c.code
    }

    /** The name as compared: lowercase, decomposed, marks and tatweel gone, same-sound letters one. */
    fun key(name: String): IntArray {
        val s = Normalizer.normalize(name.trim(), Normalizer.Form.NFD).lowercase()
        val out = ArrayList<Int>(s.length)
        for (c in s) {
            if (isMark(c) || Character.getType(c) == Character.NON_SPACING_MARK.toInt() && c !in 'ऀ'..'৿') continue
            out += weight(same[c] ?: c)
        }
        return out.toIntArray()
    }

    private fun compareKeys(a: IntArray, b: IntArray): Int {
        val n = minOf(a.size, b.size)
        for (i in 0 until n) {
            val d = a[i].compareTo(b[i])
            if (d != 0) return d
        }
        return a.size.compareTo(b.size)
    }

    /** Names in the reader's order; a null or blank name last. */
    val byName: Comparator<String?> = Comparator { a, b ->
        when {
            a.isNullOrBlank() && b.isNullOrBlank() -> 0
            a.isNullOrBlank() -> 1
            b.isNullOrBlank() -> -1
            else -> compareKeys(key(a), key(b))
        }
    }

    fun <T> sortedBy(items: Iterable<T>, name: (T) -> String?): List<T> = items.sortedWith(compareBy(byName, name))
    fun <T> sortedByDescending(items: Iterable<T>, name: (T) -> String?): List<T> = items.sortedWith(compareByDescending(byName, name))
}
