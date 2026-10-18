package com.innovation313.roshankhata.ui

/**
 * Bidi wrapping for figures and names placed in a translated sentence
 * (see Format.ltr and Format.isolate), in the app language's direction.
 * The phone uses androidx BidiFormatter; the computer a port of the same
 * rules, so both print the same marks.
 */
expect object BidiWrap {
    /** BidiFormatter.getInstance().unicodeWrap(text, TextDirectionHeuristicsCompat.LTR). */
    fun ltr(text: String): String

    /** BidiFormatter.getInstance().unicodeWrap(text): direction from the first strong letter. */
    fun auto(text: String): String
}
