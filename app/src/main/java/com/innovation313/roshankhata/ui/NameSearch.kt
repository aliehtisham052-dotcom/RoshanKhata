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
