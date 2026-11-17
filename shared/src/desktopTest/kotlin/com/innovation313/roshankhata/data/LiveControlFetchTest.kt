package com.innovation313.roshankhata.data

import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

/**
 * The kill switch's one read ([LiveControl.fetchFrom], 8 Nov 2026 review L013) against a loopback server: it names
 * itself as the app and nothing more, reads the two words, and is over within its whole deadline even when the
 * server answers one byte at a time just inside the per-read timeout.
 */
class LiveControlFetchTest {

    private lateinit var server: HttpServer
    private val agent = AtomicReference<String?>(null)
    private val base get() = "http://127.0.0.1:${server.address.port}"

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/live.json") { x ->
            agent.set(x.requestHeaders.getFirst("User-Agent"))
            val body = """{"live": false, "seal": false}""".toByteArray()
            x.sendResponseHeaders(200, body.size.toLong())
            x.responseBody.use { it.write(body) }
        }
        server.createContext("/missing") { x -> x.sendResponseHeaders(404, -1); x.close() }
        server.createContext("/slow") { x ->
            // 60 bytes, one every 300 ms: every read is well inside the 5 s timeout, the whole is 18 s.
            x.sendResponseHeaders(200, 60)
            runCatching {
                x.responseBody.use { out -> repeat(60) { out.write(' '.code); out.flush(); Thread.sleep(300) } }
            }
        }
        server.createContext("/stall") { x ->
            // The headers, then 4 s of silence before the body: inside the 5 s per-read timeout, past a 1 s deadline.
            x.sendResponseHeaders(200, 2)
            runCatching { x.responseBody.use { out -> out.write(' '.code); out.flush(); Thread.sleep(4_000); out.write(' '.code) } }
        }
        server.createContext("/big") { x ->
            val body = ByteArray(LiveControl.MAX_BYTES + 10) { ' '.code.toByte() }
            x.sendResponseHeaders(200, body.size.toLong())
            runCatching { x.responseBody.use { it.write(body) } }
        }
        server.executor = java.util.concurrent.Executors.newCachedThreadPool()
        server.start()
    }

    @After
    fun stop() = server.stop(0)

    @Test
    fun `it reads both words and names itself only as the app`() {
        assertEquals(true to true, LiveControl.fetchFrom("$base/live.json"))
        assertEquals(LiveControl.USER_AGENT, agent.get())
        assertEquals("RoshanKhata", agent.get())
    }

    @Test
    fun `a missing file stops nothing`() {
        assertEquals(false to false, LiveControl.fetchFrom("$base/missing"))
    }

    @Test
    fun `an answer trickled one byte at a time is given up at the whole deadline`() {
        val t0 = System.nanoTime()
        assertNull(LiveControl.fetchFrom("$base/slow", deadlineMs = 1_500L))
        val tookMs = (System.nanoTime() - t0) / 1_000_000
        assertTrue("took $tookMs ms", tookMs < 3_000)
    }

    @Test
    fun `a read blocked past the deadline is cut at the deadline, not when the read gives up`() {
        // Only the caller's own wait can end this one: the loop's own check runs between reads, and the read blocks 4 s.
        val t0 = System.nanoTime()
        assertNull(LiveControl.fetchFrom("$base/stall", deadlineMs = 1_000L))
        val tookMs = (System.nanoTime() - t0) / 1_000_000
        assertTrue("took $tookMs ms", tookMs < 2_500)
    }

    @Test
    fun `a file bigger than live dot json can be is no answer`() {
        assertNull(LiveControl.fetchFrom("$base/big"))
    }

    @Test
    fun `nobody listening is no answer, not an exception`() {
        val port = server.address.port
        server.stop(0)
        assertNull(LiveControl.fetchFrom("http://127.0.0.1:$port/live.json", deadlineMs = 2_000L))
        // Started again so @After has something to stop.
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).also { it.start() }
    }
}
