package com.innovation313.roshankhata.data

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * The computer's live line (JdkLines, the JDK's own WebSocket) against a small
 * WebSocket server on this machine's loopback that answers as cloud/worker's
 * line does: the pass and device in the handshake's headers, a round's
 * message answered with its id, a nudge pushed unasked, 404 for a relay that
 * has no line. What the MSI's Java does on a shop's computer, short of the
 * internet in between.
 */
class JdkLinesTest {

    /** RFC 6455's server side, text frames only, enough for the line. */
    private class Server(private val noLine: Boolean = false) {
        val socket = ServerSocket(0, 10, InetAddress.getLoopbackAddress())
        val seenHeaders = ConcurrentHashMap<String, String>()
        @Volatile var client: Socket? = null
        /** Set when the test drops the line: nothing more is answered on any connection. */
        @Volatile var dead = false
        val port get() = socket.localPort

        init {
            thread(isDaemon = true) {
                while (!socket.isClosed) {
                    val s = runCatching { socket.accept() }.getOrNull() ?: break
                    thread(isDaemon = true) { serve(s) }
                }
            }
        }

        private fun serve(s: Socket) {
            val input = DataInputStream(s.getInputStream())
            val out = s.getOutputStream()
            val head = StringBuilder()
            while (!head.endsWith("\r\n\r\n")) head.append(input.read().toChar())
            val lines = head.lines().filter { it.isNotBlank() }
            lines.drop(1).forEach { l -> l.split(":", limit = 2).takeIf { it.size == 2 }?.let { seenHeaders[it[0].trim().lowercase()] = it[1].trim() } }
            if (noLine) {
                out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()); out.flush(); s.close(); return
            }
            val key = seenHeaders.getValue("sec-websocket-key")
            val accept = Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("SHA-1").digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray())
            )
            out.write("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: $accept\r\n\r\n".toByteArray())
            out.flush()
            client = s
            while (true) {
                val b0 = runCatching { input.readUnsignedByte() }.getOrNull() ?: break
                val b1 = input.readUnsignedByte()
                var n = (b1 and 0x7F).toLong()
                if (n == 126L) n = input.readUnsignedShort().toLong() else if (n == 127L) n = input.readLong()
                val mask = ByteArray(4).also { input.readFully(it) }
                val payload = ByteArray(n.toInt()).also { input.readFully(it) }
                for (i in payload.indices) payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
                if (dead) break
                when (b0 and 0x0F) {
                    0x1 -> {
                        val o = JSONObject(String(payload))
                        val reply = JSONObject().put("id", o.getInt("id")).put("status", 200).put("ok", true)
                            .put("echo", o.getJSONObject("round").getLong("after"))
                        send(out, reply.toString())
                    }
                    0x8 -> { runCatching { s.close() }; break }
                    0x9 -> synchronized(out) { out.write(byteArrayOf(0x8A.toByte(), 0)); out.flush() } // pong
                }
            }
        }

        fun send(out: OutputStream, text: String) = synchronized(out) {
            val b = text.toByteArray()
            out.write(0x81)
            if (b.size < 126) out.write(b.size) else { out.write(126); out.write(b.size shr 8); out.write(b.size and 0xFF) }
            out.write(b); out.flush()
        }

        fun nudge() { client?.let { send(it.getOutputStream(), "{\"t\":\"new\"}") } }

        fun close() { dead = true; runCatching { client?.close() }; runCatching { socket.close() } }
    }

    private val servers = ArrayList<Server>()

    @After
    fun tidy() {
        LiveLines.maker = null
        LiveLines.closeAll()
        servers.forEach { it.close() }
    }

    @Test
    fun `a round goes over the JDK's line with the book's headers, and a nudge wakes the wait`() {
        val server = Server().also { servers += it }
        LiveLines.maker = JdkLines
        val root = "http://127.0.0.1:${server.port}"
        val book = "0123456789abcdef0123456789abcdef"
        val headers = mapOf("x-rk-pass" to "p".repeat(64), "x-device" to "aaaaaaaaaaaaaaaa")
        val answer = LiveLines.ask(root, book, headers, JSONObject().put("after", 41L))
        assertNotNull(answer)
        assertEquals(200, answer!!.first)
        assertEquals(41L, answer.second!!.getLong("echo"))
        assertEquals("aaaaaaaaaaaaaaaa", server.seenHeaders["x-device"])
        assertEquals("p".repeat(64), server.seenHeaders["x-rk-pass"])
        assertTrue(LiveLines.isOpen(root, book))
        // A second round on the same line, with its own id.
        assertEquals(7L, LiveLines.ask(root, book, headers, JSONObject().put("after", 7L))!!.second!!.getLong("echo"))
        // The relay's nudge wakes a waiting runner well before its clock.
        val since = LiveLines.newsMark()
        thread { Thread.sleep(200); server.nudge() }
        val t0 = System.currentTimeMillis()
        assertTrue(LiveLines.waitForNews(10_000, since))
        assertTrue(System.currentTimeMillis() - t0 < 5_000)
    }

    @Test
    fun `a relay without a line answers 404 and the round stays on HTTP`() {
        val server = Server(noLine = true).also { servers += it }
        LiveLines.maker = JdkLines
        val root = "http://127.0.0.1:${server.port}"
        val book = "fedcba9876543210fedcba9876543210"
        val headers = mapOf("x-rk-pass" to "p".repeat(64), "x-device" to "bbbbbbbbbbbbbbbb")
        assertNull(LiveLines.ask(root, book, headers, JSONObject().put("after", 0L)))
        // Not asked again for an hour: the next round does not even try.
        server.close()
        assertNull(LiveLines.ask(root, book, headers, JSONObject().put("after", 0L)))
    }

    @Test
    fun `a line that drops leaves the next round to open another or go over HTTP`() {
        val server = Server().also { servers += it }
        LiveLines.maker = JdkLines
        val root = "http://127.0.0.1:${server.port}"
        val book = "00112233445566778899aabbccddeeff"
        val headers = mapOf("x-rk-pass" to "p".repeat(64), "x-device" to "cccccccccccccccc")
        assertNotNull(LiveLines.ask(root, book, headers, JSONObject().put("after", 1L)))
        server.close()
        // Whether the JDK has seen the line end yet or not, the round gets no answer from it (a held line times
        // out and is let go of; a seen one is gone and none can be opened): the round then goes over HTTP.
        val until = System.currentTimeMillis() + 5_000
        while (LiveLines.isOpen(root, book) && System.currentTimeMillis() < until) Thread.sleep(50)
        assertNull(LiveLines.ask(root, book, headers, JSONObject().put("after", 2L)))
    }
}
