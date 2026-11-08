package com.innovation313.roshankhata.data

import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.net.http.WebSocketHandshakeException
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * The computer's side of the live line ([LiveLines.Maker]): the JDK's own
 * java.net.http WebSocket (the MSI carries the java.net.http module). A
 * protocol ping every 30 s keeps the line through a router's NAT; Cloudflare
 * does not bill pings, and a sleeping line costs nothing.
 */
object JdkLines : LiveLines.Maker {

    private val client: HttpClient by lazy { HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build() }
    private val pinger = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "live-line-ping").apply { isDaemon = true } }

    override fun open(
        url: String, headers: Map<String, String>, onText: (String) -> Unit, onClosed: (code: Int) -> Unit
    ): LiveLines.Line? {
        val ended = AtomicBoolean(false)
        val ping = AtomicReference<ScheduledFuture<*>?>(null)
        fun end(code: Int) { if (ended.compareAndSet(false, true)) { ping.get()?.cancel(false); onClosed(code) } }
        val listener = object : WebSocket.Listener {
            private val text = StringBuilder()
            override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
                text.append(data)
                if (last) { val whole = text.toString(); text.setLength(0); onText(whole) }
                webSocket.request(1)
                return null
            }
            override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? { end(statusCode); return null }
            override fun onError(webSocket: WebSocket, error: Throwable) = end(-1)
        }
        val builder = client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(15))
        headers.forEach { (k, v) -> builder.header(k, v) }
        val ws = try {
            builder.buildAsync(URI(url), listener).get(20, TimeUnit.SECONDS)
        } catch (e: ExecutionException) {
            val c = e.cause
            if (c is WebSocketHandshakeException && c.response.statusCode() in setOf(404, 426)) throw LiveLines.NoLineHere()
            return null
        } catch (e: Exception) {
            return null
        }
        ping.set(pinger.scheduleAtFixedRate({ runCatching { ws.sendPing(ByteBuffer.allocate(0)) } }, 30, 30, TimeUnit.SECONDS))
        val lock = Any()
        return object : LiveLines.Line {
            // java.net.http allows one send at a time: each is finished before the next.
            override fun send(text: String): Boolean = synchronized(lock) {
                if (ended.get()) return false
                runCatching { ws.sendText(text, true).get(15, TimeUnit.SECONDS) }.isSuccess
            }
            override fun close() {
                ping.get()?.cancel(false)
                runCatching { ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye") }
                end(1000)
            }
        }
    }
}
