package com.innovation313.roshankhata.data

/**
 * The words on the last Roshan Khata seal drawn (10 Nov 2026, the owner: the stamp should be seen landing when a PDF
 * is made). The PDF writers draw the seal; the screen that shows the PDF a moment later takes these words to play the
 * stamp landing with the same date and word that are printed. Kept in memory only, for one taking, and only while
 * fresh, so an older seal never lands on a newer paper.
 */
object SealMark {
    /**
     * [own]: the shop's own stamp picture was printed in the seal's place ([SealChoice.OWN]), not the Roshan Khata seal.
     * Where it landed (11 Nov 2026, the owner: the stamp lands on the paper itself, where it is printed): [x] and [size]
     * as shares of the page's width, [y] of its height, [aspect] the page's height over its width; -1 when not known.
     * The seal is always drawn on a document's last page.
     */
    class Mark(
        val middle: String, val below: String, val at: Long, val own: Boolean = false, val top: String = "ROSHAN KHATA",
        val x: Float = -1f, val y: Float = -1f, val size: Float = -1f, val aspect: Float = -1f
    ) {
        /** Whether the place on the page is known. */
        val placed: Boolean get() = x >= 0f && y >= 0f && size > 0f && aspect > 0f
    }

    @Volatile
    private var last: Mark? = null

    /** Called by the seal's drawing. */
    fun drawn(
        middle: String, below: String, now: Long = System.currentTimeMillis(), own: Boolean = false, top: String = "ROSHAN KHATA",
        place: FloatArray? = null
    ) {
        last = if (place != null && place.size == 4) Mark(middle, below, now, own, top, place[0], place[1], place[2], place[3])
        else Mark(middle, below, now, own, top)
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
