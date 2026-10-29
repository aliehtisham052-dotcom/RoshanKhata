package com.innovation313.roshankhata.data

/**
 * The payment-request record (29 Oct 2026): the two small rules both platforms
 * keep about a payment that was asked for and a payment that came.
 *
 * [PaymentRef] is the reference box's rule on every form — the khata entry's
 * and the cashbook's, the phone's and the computer's: a transfer, a wallet
 * payment or a cheque has an id worth writing down; cash has none, so the box
 * is not shown and nothing is kept for it.
 *
 * [PaymentRequest] is Follow-up's "asked, nothing received since": the owner
 * reminded a customer and no money has been written against him since. Said
 * in one place so the phone's list and the computer's agree.
 */
object PaymentRef {

    /** True for a way of paying that carries a reference: anything recorded that is not cash. */
    fun applies(method: String?): Boolean = !method.isNullOrBlank() && method != PaymentMethod.CASH

    /** What is saved from the box: trimmed, null when blank or when [method] has no reference to keep. */
    fun clean(method: String?, typed: String?): String? =
        if (!applies(method)) null else typed?.trim()?.takeIf { it.isNotEmpty() }
}

object PaymentRequest {

    /**
     * True when a reminder went to the customer ([lastReminded], the ladder's
     * last time on this device) and no "I got" has been written for him since
     * ([lastGotAt], his newest live payment, null when he never paid).
     * Nothing reminded is nothing waited for.
     */
    fun waitingSince(lastReminded: Long?, lastGotAt: Long?): Boolean =
        lastReminded != null && (lastGotAt == null || lastGotAt < lastReminded)

    /** Each party's newest live "I got", from the points Follow-up already reads for the payment habit. */
    fun lastGotAt(points: List<LedgerPoint>): Map<Long, Long> {
        val out = HashMap<Long, Long>()
        for (p in points) if (!p.isGiven) out[p.partyId] = maxOf(out[p.partyId] ?: Long.MIN_VALUE, p.timestamp)
        return out
    }
}
