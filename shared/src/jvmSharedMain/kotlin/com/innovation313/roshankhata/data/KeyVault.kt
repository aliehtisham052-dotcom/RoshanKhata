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
 * Where there is no such lock (a Linux test), [wrap] gives null and the key
 * is kept as before, in plain. [KeyAtRest.read] takes both.
 *
 * Two different failures, told apart (23 Oct 2026, the second review): a key
 * that is **not this device's** opens as null; a lock that **did not answer**
 * (the Keystore busy after an OS update, JNA's library quarantined, DPAPI's
 * master key gone after an administrator's password reset) throws
 * [KeyLockUnavailable]. The first turns live sync off, the second leaves
 * the book exactly as it is and is tried again next time — one hiccup of the
 * lock must never un-pair a shop, nor make a Windows build write the key in
 * plain.
 */
expect object KeyVault {
    /** [plain] sealed for this device alone, or null when this device has no lock to seal with. */
    fun wrap(plain: ByteArray): ByteArray?

    /** What [wrapped] holds, or null when it is not this device's; [KeyLockUnavailable] when the lock did not answer. */
    fun unwrap(wrapped: ByteArray): ByteArray?
}

/** The device's lock on the sync key could not be used right now (see [KeyVault]); nothing is decided on it. */
class KeyLockUnavailable(why: String, cause: Throwable? = null) : RuntimeException(why, cause)

/** How the key stands in sync_meta: "w1:" + the wrapped bytes (Base64), or the plain hex of before. */
object KeyAtRest {
    private const val WRAPPED = "w1:"

    /** The device's lock as [KeyAtRest] sees it; a test puts a lock that refuses or does not answer. */
    interface Lock {
        fun wrap(plain: ByteArray): ByteArray?
        fun unwrap(wrapped: ByteArray): ByteArray?
    }

    private val device = object : Lock {
        override fun wrap(plain: ByteArray) = KeyVault.wrap(plain)
        override fun unwrap(wrapped: ByteArray) = KeyVault.unwrap(wrapped)
    }

    @Volatile var lock: Lock = device

    /** The device's own lock again (after a test). */
    fun resetLock() { lock = device }

    /** [keyHex] as it is kept: wrapped when this device has a lock, plain where it has none; [KeyLockUnavailable] when the lock did not answer. */
    fun store(keyHex: String): String {
        val w = lock.wrap(keyHex.toByteArray(Charsets.US_ASCII)) ?: return keyHex
        return WRAPPED + B64.encode(w)
    }

    /** The key hex from what sync_meta holds, or null when it is not this device's; [KeyLockUnavailable] when the lock did not answer. */
    fun read(stored: String?): String? {
        if (stored == null) return null
        if (!stored.startsWith(WRAPPED)) return stored.takeIf { it.length == 64 }
        val w = runCatching { B64.decode(stored.substring(WRAPPED.length)) }.getOrNull() ?: return null
        val plain = lock.unwrap(w) ?: return null
        return String(plain, Charsets.US_ASCII).takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
    }

    fun isWrapped(stored: String?): Boolean = stored?.startsWith(WRAPPED) == true
}
