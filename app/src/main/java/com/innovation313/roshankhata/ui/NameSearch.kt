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

        val digits = query.filter { it.isDigit() }
        if (digits.isEmpty()) return false
        return phone?.filter { it.isDigit() }?.contains(digits) == true
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
    fun <T> rankSpoken(items: List<T>, words: List<String>, nameOf: (T) -> String): List<T> {
        if (items.isEmpty()) return items
        val spoken = words.map { fold(it) }.filter { it.isNotEmpty() }.distinct()
        if (spoken.isEmpty()) return items

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
                for (w in spoken.indices) {
                    if (hits[i][w] > 0) score += hits[i][w] / 100.0 * weight[w]
                }
                i to score
            }
            .sortedWith(
                compareByDescending<Pair<Int, Double>> { it.second }
                    .thenBy { nameOf(items[it.first]).lowercase() }
            )
            .map { items[it.first] }
    }

    /** A stored name cut into folded words, ready to compare against. */
    private fun partsOf(name: String): List<String> =
        name.split(*SEPARATORS).map { fold(it) }.filter { it.isNotEmpty() }

    /** One stored word against one spoken word, both already folded. */
    private fun wordScore(part: String, spoken: String): Int = when {
        part == spoken -> 100
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
    fun fold(word: String): String {
        val letters = word.lowercase().filter { it.isLetterOrDigit() }
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
            // The same letter twice says nothing the once did not.
            if (sb.isNotEmpty() && sb.last() == c) continue
            sb.append(c)
        }
        return sb.toString()
    }

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
