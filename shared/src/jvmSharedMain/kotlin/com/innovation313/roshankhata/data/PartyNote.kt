package com.innovation313.roshankhata.data

/**
 * The owner's private note on a customer or supplier (schema 43, 4 Nov 2026): what the shop knows of a person that
 * no column holds — "pays on the 10th", "ask for his son, not him", "only cash". One rule on both platforms:
 *
 *  - It is the owner's, not the customer's: it is never printed on a statement, a reminder, a receipt or the
 *    customer's card, and the customer's page shows only that there is one (a chip); its words open on a tap, as
 *    the Payment Radar's do — the customer may be looking at the screen across the counter.
 *  - It is part of the book: kept by every backup, carried to the owner's other devices by a live sync. A helper's
 *    read-only copy carries NONE (`BackupCodec` writes it only with `secrets`, 8 Nov 2026 review m42), nor does a Waris
 *    snapshot: what the shop knows of a person is the owner's, not the helper's.
 *  - It is kept short ([MAX]): a note, not a diary.
 */
object PartyNote {

    /** The longest note kept. */
    const val MAX = 500

    /** What the owner typed, trimmed and cut to [MAX]; null for nothing. */
    fun clean(typed: String?): String? =
        typed?.trim()?.take(MAX)?.trim()?.ifEmpty { null }

    /**
     * Two customers merged into one ([KhataDao.mergeParty]): the kept one's note, then the merged-away one's on its
     * own line — nothing the owner wrote about either is lost. A note the kept one already holds is not repeated.
     */
    fun join(kept: String?, merged: String?): String? {
        val a = clean(kept)
        val b = clean(merged) ?: return a
        if (a == null) return b
        if (a.contains(b, ignoreCase = true)) return a
        return clean(a + "\n" + b)
    }
}
