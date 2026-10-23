package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words

/**
 * A live-sync reason code (the engine's, e.g. "another computer", or a relay's, e.g. "day-limit") in the owner's
 * language (23 Oct 2026, the review's R48: "another computer" stood in English inside an Urdu sentence). One table
 * for the phone's Backup screen and the computer's Live sync window, so the two say the same thing. A code not
 * known here (a relay's new word, "round 503") is shown as it is.
 */
object LiveWhy {

    /** An exception on this device, whose own text is shown beside it (the phone's LiveRunner.WHY_ERROR). */
    const val ERROR = "error"

    /** The word's name ([W]) for [why], or null when the code is not one of the known ones. */
    fun wordFor(why: String): String? = when (why) {
        ERROR -> W.live_why_error
        "another computer" -> W.live_why_other_computer
        "another book" -> W.live_why_other_book
        "unknown device" -> W.live_why_unknown_device
        "unreachable" -> W.live_why_unreachable
        // The relay's day: rounds ("day-limit") or bytes ("day-bytes", 23 Oct 2026) — both wait for tomorrow.
        "day-limit", "day-bytes" -> W.live_why_day_limit
        // No room at the relay: every book ("full"), today's new books, the book's devices, or the book's own box.
        "full", "new-books", "too-many-devices", "book-full" -> W.live_why_relay_full
        else -> null
    }

    /** [why] in [words]' language, or the code itself when it is not known. */
    fun text(words: Words, why: String): String = wordFor(why)?.let { words.text(it) } ?: why
}
