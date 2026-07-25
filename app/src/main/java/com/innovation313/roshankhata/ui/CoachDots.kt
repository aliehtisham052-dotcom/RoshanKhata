package com.innovation313.roshankhata.ui

/**
 * How wide the walkthrough's progress dots are allowed to be.
 *
 * The dots share one row with Skip. They were given a fixed size written when
 * the tour was eight steps long; the tour grew to thirteen and the row ran
 * past the card's edge, clipping Skip. A shopkeeper who wants out of the tour
 * could still reach it, but only just, and the next step added would have
 * taken it away entirely.
 *
 * So the size is no longer fixed. The dots are told what room they have and
 * shrink to fit inside it. This lives on its own, away from any view, so the
 * arithmetic can be checked without a screen — which is what
 * `CoachDotsTest` does for every step count the tour is likely to reach.
 */
object CoachDots {

    /**
     * Everything between the screen edge and the dots, in dp: the card's own
     * margin on both sides (20) plus its padding on both sides (22). Both
     * come from `view_coach_bubble.xml` and must move with it.
     */
    const val CHROME_DP = 84f

    /**
     * Kept clear on the right for Skip.
     *
     * Generous on purpose. "Skip" is a translated word — Urdu, Sindhi, Farsi
     * and Arabic all set it differently, and the one that does not fit is the
     * one nobody tests. The layout reserves Skip's real width at run time
     * regardless; this only stops the dots from crowding it.
     */
    const val SKIP_DP = 80f

    /** One row of dots, in dp. */
    data class Sizes(val activeDp: Float, val dotDp: Float, val gapDp: Float)

    /** What the dots look like when there is room for them. */
    val PREFERRED = Sizes(activeDp = 26f, dotDp = 10f, gapDp = 6f)

    /** How wide [count] dots come out at [s]. Every dot carries one gap. */
    fun widthOf(count: Int, s: Sizes): Float =
        if (count <= 0) 0f else s.activeDp + (count - 1) * s.dotDp + count * s.gapDp

    /** The room the dots have on a screen [screenDp] wide. */
    fun budgetFor(screenDp: Float): Float =
        (screenDp - CHROME_DP - SKIP_DP).coerceAtLeast(60f)

    /**
     * Dots for [count] steps on a screen [screenDp] wide.
     *
     * Under the budget, nothing changes. Over it, all three measurements come
     * down by the same factor so the row keeps its proportions rather than
     * turning into a line of specks with wide gaps.
     *
     * The floors are where shrinking stops. Past them — a tour of dozens of
     * steps on a very narrow screen — the dots clip instead. That is the
     * right thing to give up: the layout gives Skip its width before the dots
     * get theirs, so the way out of the tour survives either way.
     */
    fun sizesFor(count: Int, screenDp: Float): Sizes {
        val budget = budgetFor(screenDp)
        val needed = widthOf(count, PREFERRED)
        if (needed <= budget) return PREFERRED

        val scale = budget / needed
        return Sizes(
            activeDp = (PREFERRED.activeDp * scale).coerceAtLeast(8f),
            dotDp = (PREFERRED.dotDp * scale).coerceAtLeast(3f),
            gapDp = (PREFERRED.gapDp * scale).coerceAtLeast(2f)
        )
    }
}
