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
    fun `random text is read, or refused, exactly as the JDK reads it`() {
        // 22 Oct 2026, the review: "QUJD=garbage" read as ABC, "QUJDR" as ABC, "Q" as nothing.
        val r = Random(2026)
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=="
        repeat(200_000) {
            val s = String(CharArray(r.nextInt(12)) { alphabet[r.nextInt(alphabet.length)] })
            val jdk = runCatching { Base64.getDecoder().decode(s) }.getOrNull()
            val mine = runCatching { B64.decode(s) }.getOrNull()
            if (jdk == null) org.junit.Assert.assertNull(s, mine) else assertArrayEquals(s, jdk, mine)
        }
        for (bad in listOf("QUJD=garbage", "QUJDR", "Q", "QQ=x", "QQ=", "QUJD=")) {
            org.junit.Assert.assertTrue(bad, runCatching { B64.decode(bad) }.isFailure)
        }
        // Unpadded, as the customer links and the JDK's own withoutPadding() write it.
        for (n in 0..40) {
            val bytes = ByteArray(n) { r.nextInt().toByte() }
            assertArrayEquals(bytes, B64.decode(Base64.getEncoder().withoutPadding().encodeToString(bytes)))
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
