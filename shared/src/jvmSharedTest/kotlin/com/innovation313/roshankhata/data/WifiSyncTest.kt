package com.innovation313.roshankhata.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/** Phone to computer over WiFi: the pairing code and the sealed frame, pinned. */
class WifiSyncTest {

    private val secret = WifiSync.newSecret()

    @Test
    fun `a pairing code reads back as written`() {
        val p = WifiSync.Pairing(listOf("192.168.1.20", "10.0.0.5"), 50123, secret, "Shop PC")
        val back = WifiSync.parsePairing(WifiSync.pairingText(p))
        assertEquals(p, back)
    }

    @Test
    fun `a code that is not ours is no pairing`() {
        assertNull(WifiSync.parsePairing("https://wa.me/923000000000"))
        assertNull(WifiSync.parsePairing("rksync:1?h=192.168.1.20&p=50123&k=short"))
        assertNull(WifiSync.parsePairing("rksync:1?h=not.an.ip&p=50123&k=$secret"))
        assertNull(WifiSync.parsePairing("rksync:1?h=192.168.1.20&p=0&k=$secret"))
        assertNull(WifiSync.parsePairing(null))
    }

    @Test
    fun `a secret is 64 hex digits and never the same twice`() {
        assertTrue(secret.matches(Regex("[0-9a-f]{64}")))
        assertTrue(WifiSync.newSecret() != secret)
    }

    @Test
    fun `a sealed book opens only with its own secret, and not once changed`() {
        val book = "{\"backupVersion\": 9}".toByteArray()
        val sealed = WifiSync.seal(secret, book)
        assertArrayEquals(book, WifiSync.open(secret, sealed))
        assertNull(WifiSync.open(WifiSync.newSecret(), sealed))
        val changed = sealed.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        assertNull(WifiSync.open(secret, changed))
    }

    @Test
    fun `the computer takes the phone's book and ignores a stranger first`() {
        val server = ServerSocket(0, 4, InetAddress.getLoopbackAddress())
        val book = ByteArray(300_000) { (it % 251).toByte() }
        var got: ByteArray? = null
        val t = thread { got = WifiSync.receive(server, secret) }
        // A stranger on the network, with nothing of ours to say.
        Socket(InetAddress.getLoopbackAddress(), server.localPort).use { s ->
            s.getOutputStream().write("GET / HTTP/1.1\r\n\r\n".toByteArray()); s.getOutputStream().flush()
            assertEquals(WifiSync.BAD, s.getInputStream().read())
        }
        // Someone who knows the frame but not this secret.
        val wrong = WifiSync.send(WifiSync.Pairing(listOf("127.0.0.1"), server.localPort, WifiSync.newSecret()), book)
        assertEquals(WifiSync.Outcome.Refused(WifiSync.BAD), wrong)
        val sent = WifiSync.send(WifiSync.Pairing(listOf("127.0.0.1"), server.localPort, secret), book)
        assertEquals(WifiSync.Outcome.Sent("127.0.0.1"), sent)
        t.join(10_000)
        server.close()
        assertNotNull(got)
        assertArrayEquals(book, got)
    }

    @Test
    fun `no computer listening is unreachable, not a crash`() {
        val port = ServerSocket(0).use { it.localPort }
        assertEquals(WifiSync.Outcome.Unreachable, WifiSync.send(WifiSync.Pairing(listOf("127.0.0.1"), port, secret), ByteArray(10), connectMs = 500))
    }
}
