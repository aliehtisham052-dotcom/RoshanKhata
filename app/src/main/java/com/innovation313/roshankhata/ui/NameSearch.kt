package com.innovation313.roshankhata.ui

/**
 * How this app searches names.
 *
 * One definition, used by every screen that has a search box, because a
 * shopkeeper who learns that typing "tou" finds Touqeer on one screen expects
 * the same on the next. Two screens with two rules is two apps.
 */
object NameSearch {

    /** Word boundaries as they actually appear in a shop's contact list. */
    private val SEPARATORS = charArrayOf(' ', '(', ')', '-', '.', ',', '/', '\'', '"')

    /**
     * How well a name answers what was typed. Lower is better.
     *
     * 0 — the name begins with it: "ali" finds "Ali Raza".
     * 1 — a word in the name begins with it: "ali" finds "Muhammad Ali",
     *     which is how half the names in a Pakistani ledger are recalled.
     * 2 — it appears mid-word: "ali" inside "Wali". A real match, and last,
     *     because it is almost never the one meant.
     * 3 — the name does not match; the number did.
     */
    fun rank(name: String, query: String): Int {
        val n = name.lowercase()
        return when {
            n.startsWith(query) -> 0
            n.split(*SEPARATORS).any { it.startsWith(query) } -> 1
            n.contains(query) -> 2
            else -> 3
        }
    }

    /**
     * Whether this name or number answers the query at all.
     *
     * The number is only consulted when digits were typed. Otherwise the
     * digits extracted from a query like "ali" are an empty string, which
     * every phone number contains — and the whole list comes back.
     */
    fun matches(name: String, phone: String?, query: String): Boolean {
        if (query.isEmpty()) return true
        if (name.lowercase().contains(query)) return true

        // A name typed the way it is said, rather than the way it was saved.
        //
        // Until now this test was the substring above and nothing else, so the
        // search box only ever answered an exact fragment of the stored
        // spelling. The owner reported the consequence himself: the village
        // names half his book is known by would not come up at all. "Khurpa"
        // typed as "Kurpa" is nothing; "Lappay Wali" typed as "lapewali" is
        // nothing, because the space is in the query and not in the name.
        //
        // [fold] already evens out both — the silent h, the doubled letter,
        // the spaces, the sounds a Pakistani ear treats as one — and the
        // spoken side of this file has been leaning on it for months. The
        // typed side simply never asked. It asks now, and only once the plain
        // substring has failed, so exact typing behaves exactly as it did.
        //
        // The same floor as everywhere else: below three letters a fold is a
        // coincidence, not a name, and would hand back half the book.
        val typed = fold(query)
        if (typed.length >= MIN_OVERLAP && fold(name).contains(typed)) return true

        // One slip of the finger, tried last.
        //
        // Folding evens out how a name is SAID, and it does that well: bhati
        // finds Bhatti, abas finds Abbas, touseef finds Tousif. What it cannot
        // do is forgive a letter that is simply wrong — nazer for Nazeer, aslm
        // for Aslam, matyki for Matyky — because those fold to different
        // sounds, not the same one badly spelled. In a book of a thousand
        // names, on a phone keyboard, that happens all day.
        //
        // So: after everything else has failed, allow a single edit. Only
        // whole words are compared, never the whole line, or "abc" would come
        // within one edit of half a dozen unrelated names.
        //
        // The floor is four folded letters, and that number was measured
        // rather than guessed — the guess was five, and the unit test caught
        // it, because "aslm" for Aslam is four and is precisely the typo a
        // dropped vowel produces. Against a book of real names, four returns
        // at most two candidates even in the worst case built to break it:
        // "alim" finds Ali and Salim, "amin" finds Amin and Amir. Both are
        // names worth showing. Below four the fold itself is already a
        // coincidence, so the shorter queries never reach here anyway.
        if (typed.length >= MIN_TYPO_LEN && foldedWords(name).any { withinOneEdit(typed, it) }) {
            return true
        }

        val digits = query.filter { it.isDigit() }
        if (digits.isEmpty()) return false
        return phone?.filter { it.isDigit() }?.contains(digits) == true
    }

    /** Shortest query worth forgiving a typo in. Measured, not guessed — see matches(). */
    private const val MIN_TYPO_LEN = 4

    /** Each word of a name, folded, so a typo is matched against one word at a time. */
    private fun foldedWords(name: String): List<String> =
        name.split(*SEPARATORS)
            .mapNotNull { fold(it).takeIf { f -> f.isNotEmpty() } }

    /**
     * True when [a] and [b] are the same but for one letter — changed, missing
     * or extra. Not a general edit distance: it stops the moment a second
     * difference appears, which is both faster over a thousand names and
     * exactly the question being asked.
     */
    internal fun withinOneEdit(a: String, b: String): Boolean {
        if (a == b) return true
        val (short, long) = if (a.length <= b.length) a to b else b to a
        if (long.length - short.length > 1) return false

        var i = 0
        var j = 0
        var slipped = false
        while (i < short.length && j < long.length) {
            if (short[i] == long[j]) {
                i++; j++
                continue
            }
            if (slipped) return false
            slipped = true
            if (short.length == long.length) {
                // A wrong letter: step over it on both sides.
                i++; j++
            } else {
                // A missing or extra letter: step over it on the longer side.
                j++
            }
        }
        // Whatever is left over is the one trailing edit, if it has not been
        // spent already.
        return (short.length - i) + (long.length - j) == 0 || !slipped
    }

    /**
     * Order every name by how well it answers what was spoken. Nothing is
     * removed.
     *
     * This exists because filtering was the wrong shape for the problem. A
     * spoken name comes back spelled however the recogniser felt: Asgar for
     * Asghar, Yasin for Yaseen, Tahseen for Tehseen. Filter on any of those
     * and the customer is simply absent, and the owner is left believing they
     * are not on the books. Rank instead and the worst case is that the right
     * name sits a little lower — never that it disappears.
     *
     * The variance is handled by one measure and not by a rule per name.
     * [fold] evens out the spellings that differ without sounding different,
     * and what fold cannot even out is measured as an edit distance, so a name
     * one letter away scores near a name spelled exactly. That is the whole
     * of it — there is no list of special cases to keep adding to.
     *
     * Any word of the stored name counts, not just the first. Half the names
     * in a shop's book are phrases — "Abbas Ghala Mandi Pasrur Dokandar" — and
     * the word the owner says is as likely to be in the middle as at the
     * start.
     *
     * Deliberately looser than the matching that writes an entry by itself.
     * This orders a list for someone to read and choose from; being generous
     * costs a glance. Deciding on someone's money is strict, and stays where
     * it is.
     */
    fun <T> rankSpoken(items: List<T>, words: List<String>, nameOf: (T) -> String): List<T> =
        scoreSpoken(items, words, nameOf).map { it.item }

    /**
     * Every name with what it scored, best first.
     *
     * [Scored.strongHits] counts the spoken words a name properly recognised
     * — matched outright, or began with — as against merely came near. The
     * ordering does not need that distinction. Deciding unasked does, which
     * is why it is carried out of here rather than worked out twice.
     */
    fun <T> scoreSpoken(
        items: List<T>,
        words: List<String>,
        nameOf: (T) -> String
    ): List<Scored<T>> {
        if (items.isEmpty()) return emptyList()
        val heard = words.map { fold(it) }.filter { it.isNotEmpty() }
        if (heard.isEmpty()) return items.map { Scored(it, 0.0, 0) }

        // The split runs the other way too. "Matyky" is one word on the
        // customer's row and came back as two, "mate ki"; neither half
        // reaches it, so only the first name matched and Rana Matyky finished
        // thirteenth. Adjacent spoken words joined are offered alongside the
        // words themselves, and a join that answers nothing simply scores
        // nothing — the weighting below already pays a word by what it
        // narrows, so a join that lands on one name in a thousand is worth a
        // great deal and one that lands on half the book is worth almost
        // nothing.
        val spoken = (heard + heard.zipWithNext { a, b -> a + b }).distinct()

        // How well each name answers each spoken word, worked out once.
        val parts = items.map { partsOf(nameOf(it)) }
        val hits = Array(items.size) { i ->
            IntArray(spoken.size) { w ->
                var best = 0
                for (p in parts[i]) best = maxOf(best, wordScore(p, spoken[w]))
                best
            }
        }

        // What each spoken word is worth, decided by the book itself.
        //
        // A shop's names are full of words that say nothing about which
        // customer is meant — spray, wala, muhammad, khan. Counted equally
        // they drown the one word that identifies anybody: "Asghar Spray
        // Wala" spoken against a book of eighty spray walas put every spray
        // wala level with both Asghars, and one of the Asghars finished
        // below a man named Abdul Latif.
        //
        // So a word is worth what it narrows. Matching half the book earns
        // almost nothing; matching four names out of a thousand earns a lot.
        // Nothing is listed anywhere — the book is asked each time, and a
        // word that is common in one shop and rare in the next is weighed
        // correctly in both.
        val n = items.size.toDouble()
        val weight = DoubleArray(spoken.size) { w ->
            var found = 0
            for (i in items.indices) if (hits[i][w] > 0) found++
            if (found == 0) 0.0 else kotlin.math.ln(1.0 + n / found)
        }

        return items.indices
            .map { i ->
                var score = 0.0
                var strong = 0
                for (w in spoken.indices) {
                    if (hits[i][w] > 0) score += hits[i][w] / 100.0 * weight[w]
                    if (hits[i][w] >= STRONG) strong++
                }
                Scored(items[i], score, strong)
            }
            .sortedWith(
                compareByDescending<Scored<T>> { it.score }
                    .thenBy { nameOf(it.item).lowercase() }
            )
    }

    /** At or above this, a name recognised the word rather than neared it. */
    private const val STRONG = 80

    /**
     * A stored name cut into folded words — and those words joined in pairs.
     *
     * The joins are here because a recogniser does not put the spaces where
     * the book does. "Lappay Wali" is two words on the customer's row and came
     * back as one, "lapewali", and word-against-word matching had nothing to
     * compare it to: neither stored half is that word, and neither is near
     * enough to it to count. The customer sat at position five in a list of
     * eleven hundred while the sentence naming them was heard perfectly.
     *
     * Folding the two halves and running them together gives exactly
     * "lapewali", so the name answers what was said.
     */
    private fun partsOf(name: String): List<String> {
        val words = name.split(*SEPARATORS).map { fold(it) }.filter { it.isNotEmpty() }
        if (words.size < 2) return words
        return words + words.zipWithNext { a, b -> a + b }
    }

    /**
     * Below this, only an exact match counts. Anything less is a coincidence
     * of spelling, not a recognition.
     */
    private const val MIN_OVERLAP = 3

    /** One stored word against one spoken word, both already folded. */
    private fun wordScore(part: String, spoken: String): Int = when {
        part == spoken -> 100

        // A near match has to rest on enough letters to mean something.
        //
        // Without this floor a customer stored as "B" scored 70 — a near
        // recognition — against every spoken word beginning with b, because
        // "bobi".startsWith("b") is true. So did "U", "Bh", "Aaa", and every
        // name carrying a lone initial: "Chacho Zahid U.S" cuts into
        // chacho / zahid / u / s, and that stray "u" answered any word
        // starting with u. Meanwhile the customer actually meant, whose name
        // differed by a single letter, earned 50 through edit distance and
        // finished below them.
        //
        // Three logs showed the same handful of one-letter names sitting at
        // the top of nearly every result: "A Bilal" and "Aaa" above every
        // real Afzal, "Chacho Zahid U.S" above "Shahzad Steno", "B" and "Bh"
        // above "Naeem Bobi Jajupur". A single letter is not evidence.
        minOf(part.length, spoken.length) < MIN_OVERLAP -> 0

        part.startsWith(spoken) -> 80
        spoken.startsWith(part) -> 70
        part.contains(spoken) -> 60
        else -> {
            // Nothing lined up, so ask how far apart they are. One letter of
            // difference is the ordinary case — a recogniser's spelling of a
            // name it has never been taught.
            val allowed = if (maxOf(part.length, spoken.length) >= 6) 2 else 1
            val d = distance(part, spoken, allowed)
            if (d in 1..allowed) 50 - (d - 1) * 15 else 0
        }
    }

    /**
     * Levenshtein distance, abandoned once it passes [limit].
     *
     * Bounded because this runs against every name in the book on every
     * spoken sentence, and a shop can have thousands. Anything further apart
     * than [limit] is not a near miss and the exact figure would not be used.
     */
    private fun distance(a: String, b: String, limit: Int): Int {
        if (a == b) return 0
        if (kotlin.math.abs(a.length - b.length) > limit) return limit + 1

        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            var rowBest = cur[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
                rowBest = minOf(rowBest, cur[j])
            }
            if (rowBest > limit) return limit + 1
            val swap = prev; prev = cur; cur = swap
        }
        return prev[b.length]
    }

    /**
     * Even out the spellings of one word that differ without sounding
     * different.
     *
     * Roman Urdu has no settled spelling, and a speech recogniser has less of
     * one still. These are the differences that carry no meaning:
     *
     * - a trailing h on a consonant — Asghar and Asgar, Bhai and Bai
     * - doubled letters — Abbas and Abas
     * - long vowels written twice — Yaseen and Yasin, Masood and Masud
     *
     * Evening them out here means none of them needs its own rule anywhere
     * else. Folding is for looking things up only; the stored name is never
     * changed and is always what the owner sees.
     */
    /**
     * Urdu and Arabic letters as their nearest Latin spelling.
     *
     * A shop saves "Abu G" in Latin and says it in Urdu, or the other way
     * about, and both have to reach the same customer. The script is carried
     * here rather than in a matcher of its own: one measure that reads both
     * is one measure to get right.
     */
    private val SCRIPT = mapOf(
        'ا' to "a", 'آ' to "a", 'ب' to "b", 'پ' to "p", 'ت' to "t", 'ٹ' to "t",
        'ث' to "s", 'ج' to "j", 'چ' to "ch", 'ح' to "h", 'خ' to "kh", 'د' to "d",
        'ڈ' to "d", 'ذ' to "z", 'ر' to "r", 'ڑ' to "r", 'ز' to "z", 'ژ' to "zh",
        'س' to "s", 'ش' to "sh", 'ص' to "s", 'ض' to "z", 'ط' to "t", 'ظ' to "z",
        'ع' to "a", 'غ' to "gh", 'ف' to "f", 'ق' to "q", 'ک' to "k", 'ك' to "k",
        'گ' to "g", 'ل' to "l", 'م' to "m", 'ن' to "n", 'ں' to "n", 'و' to "u",
        'ہ' to "h", 'ھ' to "h", 'ة' to "h", 'ء' to "", 'ی' to "i", 'ي' to "i",
        'ے' to "e", 'أ' to "a", 'إ' to "a", 'ؤ' to "u", 'ئ' to "i"
    )

    /** Sounds a Pakistani ear treats as the same when spelling a name. */
    private val SAME_SOUND = mapOf('g' to 'j', 'q' to 'k', 'v' to 'b', 'c' to 'k')

    fun fold(word: String): String {
        val latin = StringBuilder()
        for (c in word.lowercase()) {
            when {
                SCRIPT.containsKey(c) -> latin.append(SCRIPT[c])
                c.isLetterOrDigit() && c.code < 128 -> latin.append(c)
                // Anything else — punctuation, emoji, a script not listed —
                // carries no sound and is dropped rather than guessed at.
            }
        }
        val letters = latin.toString()
        if (letters.isEmpty()) return ""

        // Long vowels first. They are written double, and collapsing doubles
        // before reading them would throw the evidence away — "yaseen" would
        // become "yasen" and no longer meet "yasin" at all.
        val vowelled = letters
            .replace("ee", "i").replace("ea", "i")
            .replace("oo", "u").replace("ou", "u")
            .replace("ai", "e").replace("ay", "e")

        val sb = StringBuilder()
        for ((i, c) in vowelled.withIndex()) {
            // A silent h riding on the consonant before it.
            if (c == 'h' && i > 0 && vowelled[i - 1] !in "aeiou") continue
            val sound = SAME_SOUND[c] ?: c
            // The same letter twice says nothing the once did not.
            if (sb.isNotEmpty() && sb.last() == sound) continue
            sb.append(sound)
        }
        return sb.toString()
    }

    /**
     * The one customer a spoken sentence names, or null when it is not clear
     * enough to write against their money unasked.
     *
     * Two conditions, and both earned their place.
     *
     * A word has to have been *recognised*, not merely come closest. "Maine
     * Ahsaan Munshi ko 5000 diya", against a book holding a Hassan and no
     * Ahsaan, used to write five thousand rupees against Hassan — the old
     * matcher kept consonants only, and h-s-n is h-s-n. Coming nearest in a
     * field of one is not recognition.
     *
     * And the winner has to be clearly ahead of the next. Two customers named
     * Asghar, or an Ahsaan beside a Hassan, are a question for the owner and
     * not a coin to toss. The picker is one tap and it is already in order.
     *
     * Everything else falls through to that picker. Declining to answer costs
     * a tap; answering wrongly writes a stranger's name against money.
     */
    fun <T> confidentMatch(items: List<T>, words: List<String>, nameOf: (T) -> String): T? {
        val scored = scoreSpoken(items, words, nameOf)
        val best = scored.firstOrNull() ?: return null
        if (best.strongHits == 0) return null
        val runnerUp = scored.getOrNull(1)?.score ?: 0.0
        if (best.score < runnerUp * CLEARLY_AHEAD) return null
        return best.item
    }

    /** How far in front the winner must be before it is answered unasked. */
    private const val CLEARLY_AHEAD = 1.35

    /** A name, what it scored against what was said, and how well it landed. */
    data class Scored<T>(val item: T, val score: Double, val strongHits: Int)

    /**
     * Order results by how well they match, then alphabetically.
     *
     * Whatever sort the screen was using steps aside while a query is in the
     * box: recency is a fine default for browsing and useless once a name has
     * been typed.
     */
    fun <T> sort(items: List<T>, query: String, nameOf: (T) -> String): List<T> {
        if (query.isEmpty()) return items
        return items.sortedWith(
            compareBy<T> { rank(nameOf(it), query) }
                .thenBy { nameOf(it).lowercase() }
        )
    }
}
