package com.innovation313.roshankhata.data

import android.content.Context

/**
 * The phone's door to the big book ([BigBookSeed], shared since 3 Nov 2026 so the computer is measured
 * against the same book): the on-device perf test seeds it, and so does the About screen's version line,
 * by seven taps, in a build with the dev tools only; a Play build has no such door. It goes into the open
 * book and comes out again by its mark, leaving the owner's own rows exactly as they were.
 */
object BigBook {
    const val CUSTOMERS = BigBookSeed.CUSTOMERS
    const val ENTRIES_EACH = BigBookSeed.ENTRIES_EACH
    /** The first seeded customer's lines: one account with years of dealings, the customer screen's hard case. */
    const val HEAVY_ENTRIES = BigBookSeed.HEAVY_ENTRIES
    const val MARK = BigBookSeed.MARK

    /** Writes the big book. Returns how many customers it added. Safe to call twice: it clears its own earlier seed first. */
    suspend fun seed(
        context: Context,
        customers: Int = CUSTOMERS,
        entriesEach: Int = ENTRIES_EACH,
        progress: ((done: Int) -> Unit)? = null
    ): Int = BigBookSeed.seed(KhataDatabase.get(context), customers, entriesEach, progress)

    /** Takes every seeded row back out. The owner's own book is untouched. */
    suspend fun clear(context: Context) = BigBookSeed.clear(KhataDatabase.get(context))
}
