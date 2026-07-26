package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The customer QR format, pinned.
 *
 * A card in a customer's pocket cannot be updated, so whatever these tests
 * accept today must stay accepted for as long as the app lives. And a scanner
 * at a shop counter will be pointed at everything — payment QRs, WhatsApp
 * links, other shops' cards — so what these tests reject matters just as
 * much: a near-miss on an identity must be a miss, never a customer.
 */
class QrTagTest {

    @Test
    fun `a token goes through its own payload unchanged`() {
        val token = QrTag.newToken()
        assertEquals(token, QrTag.parse(QrTag.payload(token)))
    }

    @Test
    fun `payload is the versioned prefix and the token`() {
        assertEquals("RK1:" + "a".repeat(32), QrTag.payload("a".repeat(32)))
    }

    @Test
    fun `tokens are thirty-two hex characters`() {
        val t = QrTag.newToken()
        assertTrue("got: $t", Regex("[0-9a-f]{32}").matches(t))
    }

    @Test
    fun `two customers never share a token`() {
        assertNotEquals(QrTag.newToken(), QrTag.newToken())
    }

    @Test
    fun `a scanner's stray whitespace is forgiven`() {
        val token = QrTag.newToken()
        assertEquals(token, QrTag.parse("  ${QrTag.payload(token)}\n"))
    }

    /** Everything a counter's scanner will actually meet. */
    @Test
    fun `anything that is not ours reads as nothing`() {
        assertNull(QrTag.parse(null))
        assertNull(QrTag.parse(""))
        assertNull(QrTag.parse("https://wa.me/923001234567"))
        assertNull(QrTag.parse("00020101021102..."))            // payment QR
        assertNull(QrTag.parse("RK1:"))                          // empty token
        assertNull(QrTag.parse("RK1:tooshort"))
        assertNull(QrTag.parse("RK1:" + "g".repeat(32)))         // not hex
        assertNull(QrTag.parse("RK1:" + "A".repeat(32)))         // wrong case
        assertNull(QrTag.parse("RK2:" + "a".repeat(32)))         // future format
        assertNull(QrTag.parse("rk1:" + "a".repeat(32)))         // wrong prefix case
        assertNull(QrTag.parse("RK1:" + "a".repeat(33)))         // too long
    }
}
