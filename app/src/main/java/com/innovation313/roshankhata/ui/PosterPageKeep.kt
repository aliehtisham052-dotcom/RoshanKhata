package com.innovation313.roshankhata.ui

/**
 * Which of the poster screen's finished pages are kept in memory (pure, so a test holds it).
 *
 * The pages were kept by how lately each was ASKED for (an LruCache of 5). The design pager binds the chosen design
 * and two pages either side, and now and then one more; the chosen design's page is drawn first, so it was the
 * least lately asked for, and the sixth page to arrive pushed out exactly the page on show. Nothing asked for it
 * again until the pager bound it afresh: sliding away and back showed the small picture blown up, then the page:
 * one more "first one thing, then another" (found by the Urdu screen proof, 19 Nov 2026: the chosen design's page
 * "never came" on some openings).
 *
 * Kept by nearness instead: the chosen design's own page always stays, and the pages farthest from it in the row go
 * first.
 */
object PosterPageKeep {

    /** The pages kept: the chosen design, the two either side that the pager binds, and one more each way. */
    const val ROOM = 7

    /**
     * Of the pages [kept] (each by its place in the row of designs), the ones to let go so that at most [room]
     * stay: the farthest from [chosen] first, the chosen design's own page never.
     */
    fun letGo(kept: Collection<Int>, chosen: Int, room: Int = ROOM): List<Int> {
        val over = kept.size - room.coerceAtLeast(1)
        if (over <= 0) return emptyList()
        return kept.filter { it != chosen }.sortedByDescending { kotlin.math.abs(it - chosen) }.take(over)
    }
}
