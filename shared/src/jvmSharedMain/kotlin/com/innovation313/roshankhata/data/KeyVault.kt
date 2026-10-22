package com.innovation313.roshankhata.data

/**
 * The device's own lock on the book's sync key (22 Oct 2026, the review: the
 * key, which is the whole say over the live ledger, lay in plain text in
 * `khata.db` — in every whole-file copy, and on a Windows user's disk).
 *
 * The key is kept in sync_meta wrapped by a key only this device can use: the
 * phone's Android Keystore (hardware-backed where the phone has it), Windows'
 * DPAPI (the signed-in user's own). A book file carried to another device, or
 * read by another user, holds a key that does not open — and live sync there
 * is simply off until the book is joined again, which is the rule for a
 * copied file anyway ([LiveSync.check]).
 *
 * Where there is no such lock (a Linux test, an older build), [wrap] gives
 * null and the key is kept as before, in plain. [KeyVault.read] takes both.
 */
expect object KeyVault {
    /** [plain] sealed for this device alone, or null when this device has no lock to seal with. */
    fun wrap(plain: ByteArray): ByteArray?

    /** What [wrapped] holds, or null when this device cannot open it (not its own, or no lock). */
    fun unwrap(wrapped: ByteArray): ByteArray?
}

/** How the key stands in sync_meta: "w1:" + the wrapped bytes (Base64), or the plain hex of before. */
object KeyAtRest {
    private const val WRAPPED = "w1:"

    /** [keyHex] as it is kept: wrapped when this device can, else as it is. */
    fun store(keyHex: String): String {
        val w = runCatching { KeyVault.wrap(keyHex.toByteArray(Charsets.US_ASCII)) }.getOrNull() ?: return keyHex
        return WRAPPED + B64.encode(w)
    }

    /** The key hex from what sync_meta holds, or null when this device cannot open it. */
    fun read(stored: String?): String? {
        if (stored == null) return null
        if (!stored.startsWith(WRAPPED)) return stored.takeIf { it.length == 64 }
        val w = runCatching { B64.decode(stored.substring(WRAPPED.length)) }.getOrNull() ?: return null
        val plain = runCatching { KeyVault.unwrap(w) }.getOrNull() ?: return null
        return String(plain, Charsets.US_ASCII).takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
    }

    fun isWrapped(stored: String?): Boolean = stored?.startsWith(WRAPPED) == true
}
