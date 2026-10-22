package com.innovation313.roshankhata.data

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * A PIN kept only as a salted PBKDF2 hash (the computer's App Lock since
 * 1.8; the staff logins since POS Phase 5). Never the PIN itself, on either
 * device, in any file or backup.
 */
object PinHash {
    private const val ITERATIONS = 120_000

    fun salt(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.let { B64.encode(it) }

    fun hash(pin: String, salt: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), B64.decode(salt), ITERATIONS, 256)
        return try {
            B64.encode(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded)
        } finally {
            spec.clearPassword()
        }
    }

    /** Compared in constant time, so the time taken says nothing about how close a guess was. */
    fun matches(pin: String, salt: String, stored: String): Boolean =
        MessageDigest.isEqual(hash(pin, salt).toByteArray(), stored.toByteArray())

    /** 4 to 8 digits, as the form asks. */
    fun valid(pin: String): Boolean = pin.length in 4..8 && pin.all { it in '0'..'9' }
}

