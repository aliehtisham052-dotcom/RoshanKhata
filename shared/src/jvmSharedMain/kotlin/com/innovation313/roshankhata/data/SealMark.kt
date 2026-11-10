package com.innovation313.roshankhata.data

/**
 * The words on the last Roshan Khata seal drawn (10 Nov 2026, the owner: the stamp should be seen landing when a PDF
 * is made). The PDF writers draw the seal; the screen that shows the PDF a moment later takes these words to play the
 * stamp landing with the same date and word that are printed. Kept in memory only, for one taking, and only while
 * fresh, so an older seal never lands on a newer paper.
 */
object SealMark {
    class Mark(val middle: String, val below: String, val at: Long)

    @Volatile
    private var last: Mark? = null

    /** Called by the seal's drawing. */
    fun drawn(middle: String, below: String, now: Long = System.currentTimeMillis()) {
        last = Mark(middle, below, now)
    }

    /** The last seal's words, once; null when none was drawn in the last [maxAgeMs]. */
    fun take(maxAgeMs: Long = 60_000L, now: Long = System.currentTimeMillis()): Mark? {
        val m = last
        last = null
        return m?.takeIf { now - it.at in 0..maxAgeMs }
    }

    /** Forgets what was drawn before a new paper is laid out. */
    fun clear() {
        last = null
    }
}
