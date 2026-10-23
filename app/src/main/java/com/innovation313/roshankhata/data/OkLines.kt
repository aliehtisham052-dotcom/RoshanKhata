package com.innovation313.roshankhata.data

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The phone's side of the live line ([LiveLines.Maker]): OkHttp's WebSocket.
 *
 * Opened only while the app is on screen (LiveRunner) and closed when it
 * leaves; while the app is away a relay parcel reaches the phone through
 * Firebase instead. OkHttp sends protocol pings every 30 s, which Cloudflare
 * does not bill and which keep a mobile network's NAT from dropping the line.
 */
object OkLines : LiveLines.Maker {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .build()
    }

    override fun open(
        url: String, headers: Map<String, String>, onText: (String) -> Unit, onClosed: (code: Int) -> Unit
    ): LiveLines.Line? {
        val opened = CountDownLatch(1)
        val ok = AtomicBoolean(false)
        val noLine = AtomicBoolean(false)
        val ended = AtomicBoolean(false)
        fun end(code: Int) { if (ended.compareAndSet(false, true)) onClosed(code) }
        val request = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        val ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { ok.set(true); opened.countDown() }
            override fun onMessage(webSocket: WebSocket, text: String) = onText(text)
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(1000, null) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = end(code)
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!ok.get()) {
                    // The relay answered the handshake without a line (404 / 426): rounds stay on HTTP there.
                    noLine.set(response?.code == 404 || response?.code == 426)
                    opened.countDown()
                } else end(-1)
            }
        })
        if (!opened.await(15, TimeUnit.SECONDS)) { ws.cancel(); return null }
        if (noLine.get()) throw LiveLines.NoLineHere()
        if (!ok.get()) return null
        return object : LiveLines.Line {
            override fun send(text: String): Boolean = ws.send(text)
            override fun close() { ws.close(1000, "bye") }
        }
    }
}
