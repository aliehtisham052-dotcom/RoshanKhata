package com.innovation313.roshankhata.data

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * A PIN kept only as a salted PBKDF2 hash (the computer's App Lock since
 * 1.8; the staff logins since POS Phase 5). Never the PIN itself, on either
 * device, in any file or backup.
 *
 * PBKDF2-HMAC-SHA256 is worked out here on HmacSHA256 itself (RFC 8018),
 * not asked of SecretKeyFactory (22 Oct 2026, the review): Android offers
 * "PBKDF2WithHmacSHA256" only from Android 8 (API 26), and the app runs from
 * Android 7 (API 24), where setting a PIN crashed and every PIN read as
 * wrong. HmacSHA256 is on every Android. The bytes are exactly the JDK's
 * (PinHashTest holds them to it), so every hash already kept still matches.
 */
object PinHash {
    private const val ITERATIONS = 120_000
    private const val BYTES = 32

    fun salt(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.let { B64.encode(it) }

    fun hash(pin: String, salt: String): String = B64.encode(pbkdf2(pin.toByteArray(Charsets.UTF_8), B64.decode(salt), ITERATIONS, BYTES))

    /**
     * PBKDF2 with HMAC-SHA256 (RFC 8018, section 5.2): [length] bytes from
     * [password] and [salt] after [iterations] rounds.
     */
    fun pbkdf2(password: ByteArray, salt: ByteArray, iterations: Int, length: Int): ByteArray {
        require(iterations > 0 && length > 0)
        val mac = Mac.getInstance("HmacSHA256")
        // An empty password is a valid PBKDF2 key, but SecretKeySpec refuses an empty key: one zero byte is the
        // same HMAC key (HMAC pads a short key with zeros).
        mac.init(SecretKeySpec(if (password.isEmpty()) ByteArray(1) else password, "HmacSHA256"))
        val out = ByteArray(length)
        val h = mac.macLength
        var block = 1
        var at = 0
        while (at < length) {
            mac.update(salt)
            mac.update(byteArrayOf((block ushr 24).toByte(), (block ushr 16).toByte(), (block ushr 8).toByte(), block.toByte()))
            var u = mac.doFinal()
            val t = u.copyOf()
            for (i in 1 until iterations) {
                u = mac.doFinal(u)
                for (j in t.indices) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
            }
            val n = minOf(h, length - at)
            System.arraycopy(t, 0, out, at, n)
            at += n
            block++
        }
        return out
    }

    /** Compared in constant time, so the time taken says nothing about how close a guess was. */
    fun matches(pin: String, salt: String, stored: String): Boolean =
        MessageDigest.isEqual(hash(pin, salt).toByteArray(), stored.toByteArray())

    /** 4 to 8 digits, as the form asks. */
    fun valid(pin: String): Boolean = pin.length in 4..8 && pin.all { it in '0'..'9' }
}
