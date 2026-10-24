package com.innovation313.roshankhata.data

/**
 * How much of a long list a screen reads (23 Oct 2026, the review: the
 * Cashbook and Invoices screens read every row ever written, again after each
 * write, to show a screenful). A screen starts with [PAGE] rows, newest
 * first, and asks for [PAGE] more when the owner scrolls near the end of what
 * it holds — nothing older is hidden, it is read when it is reached. Figures
 * (in, out, counts, this month) are never taken from the window: they keep
 * their own queries over the whole table.
 */
object ListWindow {
    const val PAGE = 200

    /** How close to the end (in rows) the next page is asked for. */
    const val NEAR = 30

    /**
     * The limit to ask for next: [limit] grows by [PAGE] once the screen holds
     * all it asked for ([shown] == [limit], so there may be more) and the last
     * row on screen ([lastVisible], 0-based) is within [NEAR] of the end.
     */
    fun grown(limit: Int, shown: Int, lastVisible: Int): Int =
        if (shown >= limit && lastVisible >= shown - NEAR) limit + PAGE else limit
}
