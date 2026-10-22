package com.innovation313.roshankhata.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Base64
import kotlin.random.Random

/** The app's own Base64 (for Android 7) is the JDK's, byte for byte, both ways. */
class B64Test {
    @Test
    fun `same as the JDK for every length and random bytes`() {
        val r = Random(313)
        for (n in 0..300) {
            val bytes = ByteArray(n) { r.nextInt().toByte() }
            val jdk = Base64.getEncoder().encodeToString(bytes)
            assertEquals(jdk, B64.encode(bytes))
            assertArrayEquals(bytes, B64.decode(jdk))
        }
    }

    @Test
    fun `a PIN hash made before still matches`() {
        // Salts and hashes already stored were written by java.util.Base64: read the same way.
        val salt = Base64.getEncoder().encodeToString(ByteArray(16) { it.toByte() })
        assertArrayEquals(ByteArray(16) { it.toByte() }, B64.decode(salt))
        val hash = PinHash.hash("1234", salt)
        org.junit.Assert.assertTrue(PinHash.matches("1234", salt, hash))
    }
}
