package com.innovation313.roshankhata.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * The PIN hash worked out on HmacSHA256 itself (22 Oct 2026: Android 7 has no
 * "PBKDF2WithHmacSHA256") gives exactly the bytes the JDK's own PBKDF2 gives,
 * so every PIN already kept — App Lock, the owner's counter PIN, the staff's —
 * still matches after the update.
 */
class PinHashTest {

    private fun jdk(pin: String, salt: ByteArray, iterations: Int, bytes: Int): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, iterations, bytes * 8)).encoded

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    @Test
    fun `the RFC 7914 test vector`() {
        assertEquals(
            "55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc" +
                "49ca9cccf179b645991664b39d77ef317c71b845b1e30bd509112041d3a19783",
            hex(PinHash.pbkdf2("passwd".toByteArray(), "salt".toByteArray(), 1, 64))
        )
    }

    @Test
    fun `the same bytes as the JDK for PINs of every length, and blocks past the first`() {
        val r = java.util.Random(313)
        repeat(30) { n ->
            val pin = (1..(4 + n % 5)).map { '0' + r.nextInt(10) }.joinToString("")
            val salt = ByteArray(16).also { r.nextBytes(it) }
            val iterations = 1 + r.nextInt(2000)
            assertArrayEquals(pin, jdk(pin, salt, iterations, 32), PinHash.pbkdf2(pin.toByteArray(), salt, iterations, 32))
            assertArrayEquals(pin, jdk(pin, salt, iterations, 48), PinHash.pbkdf2(pin.toByteArray(), salt, iterations, 48))
        }
    }

    @Test
    fun `a PIN kept before the change still matches, and a wrong one does not`() {
        val salt = PinHash.salt()
        val keptBefore = B64.encode(jdk("4821", B64.decode(salt), 120_000, 32))
        assertEquals(keptBefore, PinHash.hash("4821", salt))
        assertTrue(PinHash.matches("4821", salt, keptBefore))
        assertFalse(PinHash.matches("4822", salt, keptBefore))
    }
}
