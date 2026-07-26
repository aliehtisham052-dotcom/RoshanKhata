package com.innovation313.roshankhata.data

import java.util.UUID

/**
 * What a customer's QR says, and how to read one back.
 *
 * The whole payload is `RK1:` followed by the customer's token — thirty-two
 * hex characters, nothing else. No name, no phone, no balance: the card has
 * to be safe to lose, and it is safe because it carries nothing. The token
 * only resolves to a customer inside this app on the owner's phone.
 *
 * `RK1` names the format, and the 1 is the version. If the payload ever has
 * to change shape, RK2 can exist alongside it and old printed cards keep
 * scanning — a card in a pocket cannot be updated, so the format it was
 * printed with has to stay readable for good.
 *
 * Pure functions, no Android in them, so the format is pinned by unit tests
 * rather than by hope: a scanner handing back a WhatsApp link, a payment QR,
 * or a corrupted read must come out as null here, never as a customer.
 */
object QrTag {

    private const val PREFIX = "RK1:"

    /** Exactly thirty-two lowercase hex characters — the shape of a token. */
    private val TOKEN = Regex("[0-9a-f]{32}")

    /** A fresh identity for a new customer. */
    fun newToken(): String = UUID.randomUUID().toString().replace("-", "")

    /** The text a customer's QR encodes. */
    fun payload(token: String): String = PREFIX + token

    /**
     * The token inside a scanned text, or null when the scan is not one of
     * ours. Whitespace from a scanner is forgiven; everything else is not —
     * a near-miss on an identity is a miss.
     */
    fun parse(scanned: String?): String? {
        val text = scanned?.trim() ?: return null
        if (!text.startsWith(PREFIX)) return null
        val token = text.removePrefix(PREFIX)
        return if (TOKEN.matches(token)) token else null
    }
}
