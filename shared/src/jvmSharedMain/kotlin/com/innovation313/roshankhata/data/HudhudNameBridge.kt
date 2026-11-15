package com.innovation313.roshankhata.data

import java.text.Normalizer

/**
 * An Arabic-script word (Urdu, Sindhi, Persian, Arabic) against a name written in Latin letters: are they one name?
 * (15 Nov 2026. Whoever holds the phone is told about the person the words name — never about another.)
 *
 * These scripts leave the short vowels unwritten and spell a long one with a letter that is also a consonant, so the two
 * spellings are never letter for letter the same: "بشیر" is Bashir, "عمران" Imran, "مصطفی" Mustafa, "شازیہ" Shazia, "نوید"
 * Naveed. Folding both to one alphabet ([NameSearch.fold]) loses what is needed to tell them apart — a final "ہ" kept as an
 * "h", a "ی" folded to "i" where Latin writes "ai", a "و" that is a "v", an "h" the fold takes for silent — so this reads
 * the letters themselves. The rules, each held by a test of its own:
 *  - the consonants are the same one for one: "ش" is "sh", "خ" "kh", "ث" "s" or "th", "ق" "q" or "k"; "ح" and "ہ" are an
 *    "h" that stays ("Rahim" is "رحیم", "Sohail" "سہیل");
 *  - a short vowel in the Latin spelling has no letter, so any vowel may stand between two consonants ("Umar", "عمر");
 *  - a long vowel that is written ("ا" "ع" "ء", "و" "ی") needs a vowel in the Latin spelling where it is written, and
 *    "ی" one of its own sort (i or e), "و" too (u or o), an "ا" inside a word an "a": "بشیر" is Bashir and neither Bushra nor
 *    Bashar, "بشار" is Bashar and not Bashir (the silent "ا" of the article in "عبداللہ" says nothing). At the end of a word
 *    "ی" may stand for an "a" (Mustafa, Musa, Bushra, Yahya);
 *  - "و" and "ی" are the consonants w, v and y where the Latin spelling has one ("Anwar", "Naveed", "Maryam", "Ilyas"), and
 *    only that at the start of a word: "وسیم" begins with a "w", so "Asim" ("اسیم") is not it;
 *  - a name that begins with a vowel is written with "ا", "آ" or "ع" ("Ahmad" is "احمد"): "حمد" is Hamad, not Ahmad;
 *  - a final "ہ" is an "h" ("Sarah") or a vowel ending, nothing written ("Shazia", "Fatima", "Aamna" — so "آمنہ" is never
 *    Aman: a name that ends on a consonant has no silent letter to say it);
 *  - doubled letters are one ("Abdullah", "Hussain", "Muhammad");
 *  - fewer than three letters is a word, not a name, and any letter that is not known fails the whole word.
 * Not met, on purpose: the Arabic article and "Abdul" and "Abdur" written as one word with the name that follows them, and
 * abbreviations ("Mohd").
 * Pure functions on strings, so a test holds each rule one at a time.
 */
internal object HudhudNameBridge {

    /** Fewest letters (doubled ones counted once) a word needs to be a name: "ام" is a word, "امر" may be a name. */
    const val MIN_LETTERS = 3

    /** Most letters either spelling may have: a longer one is not a name's word, and the search stays small. */
    private const val MAX_ARABIC = 24
    private const val MAX_LATIN = 40

    // The letters the matcher gives a part of its own (everything else is a consonant in [OPTIONS]).
    private const val WAW = 'و'    // و: w or v, or a long u or o
    private const val YEH = 'ی'    // ی: y, or a long i or e (and, at the end of a word, an a)
    private const val HEH = 'ہ'    // ہ: an h; at the end of a word often nothing at all
    private const val ALEF = 'ا'   // ا (hamza seats and alif madda are made this one)
    private const val AIN = 'ع'    // ع
    private const val HAMZA = 'ء'  // ء

    private const val LATIN_VOWELS = "aeiou"

    // What an open stretch of written long vowels needs from the Latin spelling at that place.
    private const val OPEN = 1  // at least one vowel
    private const val IE = 2    // an i or an e (a "ی" was written); at the end of a word an a will do too
    private const val UO = 4    // a u or an o (a "و" was written)
    private const val AA = 8    // an a (an "ا" inside a word was written): "بشار" is Bashar, not Bashir

    /** The masks above are at most 15: sixteen states a letter and a place can be reached in. */
    private const val STATES = 16

    private const val ABD = "عبد"             // عبد
    private const val ALLAH_END = "الہ"       // الہ: the end of "اللہ" once its doubled lam is one

    private val W_OPTIONS = arrayOf("w", "v")
    private val Y_OPTIONS = arrayOf("y")
    private val H_OPTIONS = arrayOf("h")

    /** The Latin letters each consonant of the scripts may be spelt with, the likeliest first. */
    private val OPTIONS: Map<Char, Array<String>> = HashMap<Char, Array<String>>().apply {
        fun add(letters: String, vararg latin: String) {
            for (c in letters) put(c, arrayOf(*latin))
        }
        add("ب", "b")                                  // ب
        add("پ", "p")                                  // پ
        add("تٹط", "t")                      // ت ٹ ط
        add("ث", "s", "th")                            // ث
        add("ج", "j")                                  // ج
        add("چ", "ch")                                 // چ
        add("ح", "h")                                  // ح
        add("خ", "kh", "k")                            // خ
        add("دڈ", "d")                            // د ڈ
        add("ذ", "z", "dh")                            // ذ
        add("رڑ", "r")                            // ر ڑ
        add("ز", "z")                                  // ز
        add("ژ", "zh", "z", "j")                       // ژ
        add("سص", "s")                            // س ص
        add("ش", "sh")                                 // ش
        add("ض", "z", "d")                             // ض
        add("ظ", "z")                                  // ظ
        add("غ", "gh", "g")                            // غ
        add("ف", "f")                                  // ف
        add("ق", "q", "k")                             // ق
        add("ک", "k", "c")                             // ک
        add("گ", "g")                                  // گ
        add("ل", "l")                                  // ل
        add("م", "m")                                  // م
        add("ن", "n")                                  // ن
        // Sindhi's own letters.
        add("ٻ", "b")                                  // ٻ
        add("ڀ", "bh", "b")                            // ڀ
        add("ٽ", "t")                                  // ٽ
        add("ٿ", "th", "t")                            // ٿ
        add("ڊڏ", "d")                            // ڊ ڏ
        add("ڌڍ", "dh", "d")                      // ڌ ڍ
        add("ڙ", "r")                                  // ڙ
        add("ڳ", "g")                                  // ڳ
        add("ڱ", "ng", "n")                            // ڱ
        add("ڄ", "j")                                  // ڄ
        add("ڃ", "ny", "n")                            // ڃ
        add("ڇ", "chh", "ch")                          // ڇ
        add("ڦ", "ph", "f")                            // ڦ
    }

    /** Whether [arabic] (as heard) and [latin] (as saved) are one name. */
    fun meets(arabic: String, latin: String): Boolean = meetsLetters(arabicLetters(arabic), latinLetters(latin))

    /** [meets] on words already put through [arabicLetters] and [latinLetters] (the search does that once per word). */
    fun meetsLetters(a: String, l: String): Boolean {
        if (a.length < MIN_LETTERS || a.length > MAX_ARABIC || l.length < 2 || l.length > MAX_LATIN) return false
        if (a.all { it == WAW || it == YEH || isLongVowel(it) }) return false
        // Most names fail on the first letter: ask that before any room is made for the rest.
        val first = a[0]
        if (isLongVowel(first)) {
            if (!isVowel(l[0])) return false
        } else {
            val opts = consonantOptions(first) ?: return false
            if (opts.none { l.startsWith(it) }) return false
        }
        return Matcher(a, l).run()
    }

    /**
     * The letters of an Arabic-script word as the matcher reads them: the marks (vowel signs, shadda, the superscript alef,
     * tatweel, joiners), digits and anything that is not a letter gone; every form of a letter made the one Urdu form
     * (ي ى ئ ے → ی, ك → ک, ه ة ۃ ھ → ہ, أ إ آ ٱ → ا, ؤ → و, ں → ن); a doubled letter one.
     */
    fun arabicLetters(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            val c = canonical(ch) ?: continue
            if (sb.isEmpty() || sb[sb.length - 1] != c) sb.append(c)
        }
        return sb.toString()
    }

    /** The letters of a Latin spelling as the matcher reads them: a to z only (accents dropped), lower case, a doubled letter one. */
    fun latinLetters(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in Normalizer.normalize(raw, Normalizer.Form.NFD)) {
            val c = ch.lowercaseChar()
            if (c in 'a'..'z' && (sb.isEmpty() || sb[sb.length - 1] != c)) sb.append(c)
        }
        return sb.toString()
    }

    private fun canonical(ch: Char): Char? = when {
        !ch.isLetter() -> null              // vowel signs and marks (U+064B-065F, U+0670), joiners, digits, punctuation
        ch == 'ـ' -> null              // tatweel
        ch == 'أ' || ch == 'إ' || ch == 'ٱ' || ch == 'آ' -> ALEF   // أ إ ٱ آ
        ch == 'ؤ' -> WAW                                                           // ؤ
        ch == 'ي' || ch == 'ى' || ch == 'ې' || ch == 'ۍ' || ch == 'ێ' ||
            ch == 'ئ' || ch == 'ے' || ch == 'ۓ' -> YEH                   // ي ى ې ۍ ێ ئ ے ۓ
        ch == 'ك' || ch == 'ڪ' -> 'ک'                                    // ك ڪ → ک
        ch == 'ه' || ch == 'ۀ' || ch == 'ة' || ch == 'ە' || ch == 'ۃ' || ch == 'ھ' -> HEH // ه ۀ ة ە ۃ ھ
        ch == 'ں' || ch == 'ڻ' -> 'ن'                                    // ں ڻ → ن
        else -> ch
    }

    private fun isLongVowel(c: Char) = c == ALEF || c == AIN || c == HAMZA

    private fun isVowel(c: Char) = c in LATIN_VOWELS

    /** The Latin spellings of a letter that is a consonant here (و, ی and ہ as well), none for a long vowel or an unknown letter. */
    private fun consonantOptions(c: Char): Array<String>? = when (c) {
        WAW -> W_OPTIONS
        YEH -> Y_OPTIONS
        HEH -> H_OPTIONS
        else -> OPTIONS[c]
    }

    private class Matcher(private val a: String, private val l: String) {
        private val memo = ByteArray((a.length + 1) * (l.length + 1) * STATES)

        /** Whether the "ا" at [i] is the silent alif of the article: "Abd" + "al-" ("عبداللہ", "عبدالکریم"), or "-ullah" ("اسداللہ"). */
        private fun articleAlif(i: Int): Boolean =
            (i == 3 && a.startsWith(ABD)) || (i == a.length - 3 && a.endsWith(ALLAH_END))

        fun run(): Boolean = go(0, 0, 0)

        /** [a] from letter [i] against [l] from letter [j]; [mask] is what the long vowels written just before ask of the Latin vowels. */
        private fun go(i: Int, j: Int, mask: Int): Boolean {
            if (i == a.length) {
                val k = vowelsAt(j)
                return j + k == l.length && gapOk(j, k, mask, true)
            }
            val key = (i * (l.length + 1) + j) * STATES + mask
            when (memo[key].toInt()) {
                1 -> return true
                2 -> return false
            }
            val c = a[i]
            var r = false
            if (isLongVowel(c)) {
                // An alif that does not begin the word says an "a" (the first letter of a name is any vowel: "Ahmad", "Umar").
                val asksA = c == ALEF && i > 0 && !articleAlif(i)
                r = go(i + 1, j, mask or OPEN or (if (asksA) AA else 0))
            } else {
                // Not the first letter: a "و" or a "ی" may be the long vowel (tried first) or, below, the consonant.
                if (i > 0 && c == WAW) r = go(i + 1, j, mask or OPEN or UO)
                if (!r && i > 0 && c == YEH) r = go(i + 1, j, mask or OPEN or IE)
                val opts = consonantOptions(c)
                if (!r && opts != null) r = consonant(i, j, mask, opts)
                // A "ہ" at the end of a word may say nothing, and then the name ends in a vowel ("آمنہ" is Aamna, never Aman).
                if (!r && c == HEH && i == a.length - 1 && vowelsAt(j) > 0) r = go(i + 1, j, mask)
            }
            memo[key] = if (r) 1 else 2
            return r
        }

        /** The consonant [opts] of letter [i] at [j]: only vowels may lie before it, as many as the written long vowels ask. */
        private fun consonant(i: Int, j: Int, mask: Int, opts: Array<String>): Boolean {
            val k = vowelsAt(j)
            // Nothing is written for a vowel the word starts with: "حمد" is not "Ahmad".
            if (i == 0 && k > 0) return false
            if (!gapOk(j, k, mask, false)) return false
            val p = j + k
            for (o in opts) {
                if (l.startsWith(o, p) && go(i + 1, p + o.length, 0)) return true
            }
            return false
        }

        private fun vowelsAt(j: Int): Int {
            var k = 0
            while (j + k < l.length && isVowel(l[j + k])) k++
            return k
        }

        /** Whether the [k] Latin vowels from [j] answer what the long vowels written before them ask. */
        private fun gapOk(j: Int, k: Int, mask: Int, atEnd: Boolean): Boolean {
            if (mask and OPEN != 0 && k == 0) return false
            if (mask and IE != 0 && !has(j, k, "ie") && !(atEnd && has(j, k, "a"))) return false
            if (mask and UO != 0 && !has(j, k, "uo")) return false
            if (mask and AA != 0 && !has(j, k, "a")) return false
            return true
        }

        private fun has(j: Int, k: Int, set: String): Boolean {
            for (x in j until j + k) if (l[x] in set) return true
            return false
        }
    }
}
