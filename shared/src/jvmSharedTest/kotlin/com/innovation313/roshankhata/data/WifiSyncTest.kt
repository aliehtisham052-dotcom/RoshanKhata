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
        // A code naming a host on the internet is refused: only the shop's own network.
        assertNull(WifiSync.parsePairing("rksync:1?h=8.8.8.8&p=50123&k=$secret"))
        assertNull(WifiSync.parsePairing("rksync:1?h=172.32.0.1,192.169.1.1,169.253.1.1&p=50123&k=$secret"))
        assertEquals(listOf("10.1.2.3", "172.16.0.9", "169.254.3.4", "127.0.0.1"),
            WifiSync.parsePairing("rksync:1?h=8.8.8.8,10.1.2.3,172.16.0.9,169.254.3.4,127.0.0.1&p=50123&k=$secret")!!.hosts)
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
        val spool = java.nio.file.Files.createTempDirectory("rk-from-phone").toFile()
        val book = ByteArray(300_000) { (it % 251).toByte() }
        var got: java.io.File? = null
        val t = thread { got = WifiSync.receive(server, secret, spool = spool) }
        try {
            // A stranger on the network, with nothing of ours to say.
            Socket(InetAddress.getLoopbackAddress(), server.localPort).use { s ->
                s.getOutputStream().write("GET / HTTP/1.1\r\n\r\n".toByteArray()); s.getOutputStream().flush()
                assertEquals(WifiSync.BAD, s.getInputStream().read())
            }
            // Someone who knows the frame but not this secret: refused on the head, before a byte of the book.
            val wrong = WifiSync.send(WifiSync.Pairing(listOf("127.0.0.1"), server.localPort, WifiSync.newSecret()), book)
            assertEquals(WifiSync.Outcome.Refused(WifiSync.BAD), wrong)
            assertEquals(0, spool.listFiles()?.size ?: 0)
            val sent = WifiSync.send(WifiSync.Pairing(listOf("127.0.0.1"), server.localPort, secret), book)
            assertEquals(WifiSync.Outcome.Sent("127.0.0.1"), sent)
            t.join(10_000)
            // The book arrived onto the disk (23 Oct 2026), never gathered in memory.
            val file = got
            assertNotNull(file)
            assertEquals(spool.canonicalPath, file!!.parentFile.canonicalPath)
            assertArrayEquals(book, file.readBytes())
        } finally {
            server.close()
            spool.deleteRecursively()
        }
    }

    @Test
    fun `a stranger's frame is refused before anything of it is kept`() {
        val server = ServerSocket(0, 4, InetAddress.getLoopbackAddress())
        val spool = java.nio.file.Files.createTempDirectory("rk-from-phone").toFile()
        var stop = false
        var got: java.io.File? = null
        val t = thread { got = WifiSync.receive(server, secret, { stop }, spool) }
        fun frame(write: (java.io.DataOutputStream) -> Unit): Int =
            Socket(InetAddress.getLoopbackAddress(), server.localPort).use { s ->
                s.soTimeout = 10_000
                val out = java.io.DataOutputStream(s.getOutputStream().buffered())
                write(out); out.flush()
                s.getInputStream().read()
            }
        try {
            // The one-frame send of before, even with the right secret: refused unread (update both devices).
            val sealed = WifiSync.seal(secret, ByteArray(1000) { 7 })
            assertEquals(WifiSync.BAD, frame { out -> out.write("RKS1".toByteArray()); out.writeInt(sealed.size); out.write(sealed) })
            // "RKB2" with a head sealed under another secret, and a lot after it: refused on the head alone.
            assertEquals(WifiSync.BAD, frame { out ->
                out.write("RKB2".toByteArray())
                val head = java.nio.ByteBuffer.allocate(8).putLong(200_000L).array()
                SealedChunks.write(out, WifiSync.chunkKey(WifiSync.newSecret()), "RKB2".toByteArray(), head, null)
                out.write(ByteArray(20_000))
            })
            // A record claiming to be longer than a head: refused without being read.
            assertEquals(WifiSync.BAD, frame { out ->
                out.write("RKB2".toByteArray()); out.write(ByteArray(8)); out.writeInt(200 * 1024 * 1024); out.writeByte(0)
            })
            // The right secret and a book larger than the computer takes: TOO_BIG before the book goes.
            assertEquals(WifiSync.TOO_BIG, frame { out ->
                out.write("RKB2".toByteArray())
                val head = java.nio.ByteBuffer.allocate(8).putLong(WifiSync.MAX_BYTES.toLong() + 1).array()
                SealedChunks.write(out, WifiSync.chunkKey(secret), "RKB2".toByteArray(), head, null)
            })
            // A pull's question claiming a huge length: refused, nothing set aside for it.
            assertEquals(WifiSync.BAD, frame { out -> out.write("RKG1".toByteArray()); out.writeInt(WifiSync.MAX_BYTES) })
            Thread.sleep(200)
            assertNull(got)
            assertEquals(0, spool.listFiles()?.size ?: 0)
        } finally {
            stop = true
            t.join(10_000)
            server.close()
            spool.deleteRecursively()
        }
    }

    @Test
    fun `an empty book is refused before anything is sent`() {
        val tooBig = WifiSync.Outcome.Refused(WifiSync.TOO_BIG)
        assertEquals(tooBig, WifiSync.send(WifiSync.Pairing(listOf("127.0.0.1"), 1, secret), ByteArray(0)))
    }

    @Test
    fun `the phone can take the computer's book, only while the computer gives it`() {
        val server = ServerSocket(0, 4, InetAddress.getLoopbackAddress())
        val pcBook = ByteArray(200_000) { (it % 97).toByte() }
        var giving = false
        var stop = false
        val t = thread { WifiSync.receive(server, secret, { stop }) { if (giving) pcBook else null } }
        val p = WifiSync.Pairing(listOf("127.0.0.1"), server.localPort, secret)
        // The book is written to this file as it arrives (23 Oct 2026), never held whole.
        val into = java.io.File.createTempFile("rk-pull", ".txt")
        try {
            assertEquals(WifiSync.Pulled.Refused, WifiSync.pull(p, into))
            assertTrue(!into.exists())
            giving = true
            assertEquals(WifiSync.Pulled.Refused, WifiSync.pull(p.copy(secret = WifiSync.newSecret()), into))
            val got = WifiSync.pull(p, into)
            assertTrue("$got", got is WifiSync.Pulled.Got)
            assertArrayEquals(pcBook, (got as WifiSync.Pulled.Got).file.readBytes())
            // Once per code shown: the same code does not hand the book out again (21 Oct 2026).
            assertEquals(WifiSync.Pulled.Refused, WifiSync.pull(p, into))
        } finally {
            into.delete()
            stop = true
            t.join(10_000)
            server.close()
        }
    }

    @Test
    fun `no computer listening is unreachable, not a crash`() {
        val port = ServerSocket(0).use { it.localPort }
        assertEquals(WifiSync.Outcome.Unreachable, WifiSync.send(WifiSync.Pairing(listOf("127.0.0.1"), port, secret), ByteArray(10), connectMs = 500))
    }
}
