package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.ui.Format
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

    /**
     * Which device a version refusal ([LiveSync.needsUpdate]) asks the owner to update (24 Nov 2026, BC-248): [THIS]
     * (the other runs a newer version), [OTHER] (it runs an older one) or [EITHER] (no number to compare: the wire
     * itself differs, so both are updated).
     */
    enum class Update { THIS, OTHER, EITHER }

    /** What [skew] reads out of a refusal: the version kind ([SCHEMA], [PROTOCOL] or [WIRE]) and the two numbers. */
    data class Skew(val update: Update, val kind: String, val theirs: Int?, val ours: Int?)

    const val SCHEMA = "schema"
    const val PROTOCOL = "protocol"
    /** The wire's own refusal ([LiveWire.WIRE_OTHER], "protocol wire"): there is no number in it. */
    const val WIRE = "wire"

    /**
     * A version refusal read for the owner, or null when [why] is not one ([LiveSync.needsUpdate]). The numbers in
     * [why] are the OTHER device's ("schema 47": [LiveSync.skewOf] names the sender's, [LiveSync.oursFor] the
     * refuser's own), so a higher one than this build's means the other is newer and this device is to be updated; a
     * lower one, the other. "protocol wire" and anything with no figure in it: both ([Update.EITHER]).
     */
    fun skew(why: String, ourSchema: Int = KHATA_DB_VERSION, ourProtocol: Int = LiveSync.PROTOCOL): Skew? {
        if (!LiveSync.needsUpdate(why)) return null
        // The envelope itself came from a newer / older wire (BC-272): no number, but which device is newer is known.
        if (why == LiveWire.WIRE_NEWER) return Skew(Update.THIS, WIRE, null, null)
        if (why == LiveWire.WIRE_OLDER) return Skew(Update.OTHER, WIRE, null, null)
        val kind = if (why.startsWith(SCHEMA)) SCHEMA else PROTOCOL
        val theirs = why.substringAfter(' ', "").trim().toIntOrNull()
        val ours = if (kind == SCHEMA) ourSchema else ourProtocol
        if (theirs == null) return Skew(Update.EITHER, if (kind == SCHEMA) SCHEMA else WIRE, null, ours)
        val update = when {
            theirs > ours -> Update.THIS
            theirs < ours -> Update.OTHER
            else -> Update.EITHER
        }
        return Skew(update, kind, theirs, ours)
    }

    private fun kindText(words: Words, s: Skew, n: Int?): String =
        words.text(if (s.kind == PROTOCOL) W.live_skew_kind_protocol else W.live_skew_kind_schema, n ?: 0)

    /**
     * The sentence for a version refusal, naming which device to update: "[device] runs a newer version (data version
     * 47; this device: 46). Update this device". [device] is the other device's name (a blank one reads "the other
     * device"); [computer] words "this computer" for the computer's window, "this device" for the phone's card.
     * Null when [why] is not a version refusal.
     */
    fun skewText(
        words: Words, why: String, device: String? = null, computer: Boolean = false,
        ourSchema: Int = KHATA_DB_VERSION, ourProtocol: Int = LiveSync.PROTOCOL
    ): String? {
        val s = skew(why, ourSchema, ourProtocol) ?: return null
        val who = device?.takeIf { it.isNotBlank() }?.let { Format.isolate(it) } ?: words.text(W.live_skew_other_device)
        val key = when (s.update) {
            Update.THIS -> if (computer) W.live_skew_pc_this else W.live_skew_this
            Update.OTHER -> if (computer) W.live_skew_pc_other else W.live_skew_other
            Update.EITHER -> if (computer) W.live_skew_pc_unknown else W.live_skew_unknown
        }
        // No figure to compare ("protocol wire"): the sentence names no number either.
        return if (s.theirs == null) words.text(key, who, "", "")
        else words.text(key, who, kindText(words, s, s.theirs), kindText(words, s, s.ours))
    }

    /** The word's name ([W]) for [why], or null when the code is not one of the known ones. */
    fun wordFor(why: String): String? = skew(why)?.let {
        when (it.update) { Update.THIS -> W.live_skew_this; Update.OTHER -> W.live_skew_other; Update.EITHER -> W.live_skew_unknown }
    } ?: when (why) {
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
    fun text(words: Words, why: String): String =
        skewText(words, why) ?: wordFor(why)?.let { words.text(it) } ?: why
}
