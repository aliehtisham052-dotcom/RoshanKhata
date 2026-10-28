package com.innovation313.roshankhata.data

import org.json.JSONObject
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * The live line to a relay (23 Oct 2026): one WebSocket per open app and
 * book, held while the app is on screen (the phone) or open (the computer).
 *
 * Why: a round asked over HTTP is one request each time, whether or not
 * anything changed, and the free plan's budget went on asking. Over the line
 * a round is one message — Cloudflare bills incoming WebSocket messages twenty
 * to a request, and its outgoing ones, protocol pings and a sleeping line not
 * at all — and the relay nudges the line the moment another device leaves a
 * parcel, so nobody asks every few seconds "anything new?".
 *
 * The line carries exactly the HTTP round's body and answer (cloud/worker's
 * `roundOf`), so [LiveCloud.round] is the same code over either; when there is
 * no line (a relay without one answers 404, the line dropped, no platform
 * socket), the round goes over HTTP as before. Nothing is ever only on the
 * line: a reply that does not come in time is a round that did not happen.
 *
 * The platform gives the socket ([maker]): OkHttp on the phone, the JDK's
 * java.net.http on the computer; a test gives its own.
 */
object LiveLines {

    /** One open WebSocket. */
    interface Line {
        /** False when the text could not be queued (the line is gone). */
        fun send(text: String): Boolean
        fun close()
    }

    /** What a [Maker] throws when the relay answered the handshake with 404/426: it has no line (rounds over HTTP). */
    class NoLineHere : Exception("no live line on this relay")

    /**
     * Opens a line to [url] with [headers], blocking until the handshake is answered; [onText] gets each text
     * message, [onClosed] once when it ends. Null when it cannot be opened now; [NoLineHere] for a relay without one.
     */
    fun interface Maker {
        fun open(url: String, headers: Map<String, String>, onText: (String) -> Unit, onClosed: (code: Int) -> Unit): Line?
    }

    /** The platform's socket; null leaves every round on HTTP. */
    @Volatile var maker: Maker? = null

    /** How long a round over the line may take before it counts as not answered. */
    const val ANSWER_MS = 20_000L
    /** After a failed open the relay is not tried again for this long, doubling to [RETRY_MAX_MS]. */
    private const val RETRY_MS = 30_000L
    private const val RETRY_MAX_MS = 10 * 60_000L
    /** A relay that answered "no such path" (no line there) is asked again only after this long. */
    private const val NO_LINE_MS = 60 * 60_000L

    private class Open(val line: Line) {
        val ids = AtomicInteger(0)
        val waiting = ConcurrentHashMap<Int, CompletableFuture<JSONObject?>>()
        @Volatile var closed = false
    }

    private val lines = ConcurrentHashMap<String, Open>()
    private val retryAt = ConcurrentHashMap<String, Long>()
    private val retryWait = ConcurrentHashMap<String, Long>()

    /** Counts the relay's nudges ("a parcel waits") and lines that ended: a runner waiting on [waitForNews] wakes. */
    private val news = AtomicLong(0)
    private val newsLock = Object()

    /** A line is this device's, to one relay, for one book (one process holds one device; a test holds two). */
    private fun key(root: String, book: String, device: String = "") = root.trimEnd('/') + "|" + book + "|" + device

    /** Whether any line is open now (a runner then waits for news instead of asking on a clock). */
    fun anyOpen(): Boolean = lines.values.any { !it.closed }

    /** Whether a line to [root] for [book] is open now. */
    fun isOpen(root: String, book: String): Boolean = lines.entries.any { it.key.startsWith(key(root, book)) && !it.value.closed }

    /** The WebSocket address for a relay's base: https → wss. */
    fun urlOf(root: String, book: String): String {
        val r = root.trimEnd('/')
        val ws = when {
            r.startsWith("https://") -> "wss://" + r.removePrefix("https://")
            r.startsWith("http://") -> "ws://" + r.removePrefix("http://")
            else -> r
        }
        return "$ws/v1/b/$book/live"
    }

    /**
     * One round's body over the line to [root]: the relay's answer as (status, body), or null when there is
     * no line and none could be opened now — the caller then asks over HTTP. Blocking, like an HTTP call.
     */
    fun ask(root: String, book: String, headers: Map<String, String>, round: JSONObject, now: Long = System.currentTimeMillis()): Pair<Int, JSONObject?>? {
        val k = key(root, book, headers["x-device"].orEmpty())
        val open = lines[k]?.takeIf { !it.closed } ?: openLine(k, root, book, headers, now) ?: return null
        val id = open.ids.incrementAndGet()
        val f = CompletableFuture<JSONObject?>()
        open.waiting[id] = f
        if (!open.line.send(JSONObject().put("id", id).put("round", round).toString())) {
            open.waiting.remove(id)
            drop(k, open)
            return null
        }
        val reply = try { f.get(ANSWER_MS, TimeUnit.MILLISECONDS) } catch (e: Exception) { null } finally { open.waiting.remove(id) }
        if (reply == null) {
            // Not answered in time: the line is not trusted again; the next round opens a new one or goes over HTTP.
            drop(k, open)
            return null
        }
        val status = reply.optInt("status", 200)
        reply.remove("id"); reply.remove("status")
        return status to reply
    }

    /** One lock per line (R130): a slow handshake to one relay or for one book no longer holds every other road. */
    private val opening = ConcurrentHashMap<String, Any>()

    private fun openLine(k: String, root: String, book: String, headers: Map<String, String>, now: Long): Open? {
        val m = maker ?: return null
        if (now < (retryAt[k] ?: 0L)) return null
        synchronized(opening.computeIfAbsent(k) { Any() }) {
            lines[k]?.takeIf { !it.closed }?.let { return it }
            // The line's own [Open] once made; before that, a nudge still wakes the runner and an early close is
            // remembered (R130: what came during the handshake was dropped, and a closed line kept as open).
            val holder = java.util.concurrent.atomic.AtomicReference<Open?>(null)
            val endedEarly = java.util.concurrent.atomic.AtomicBoolean(false)
            val line = try {
                m.open(
                    urlOf(root, book), headers,
                    { text -> onText(holder.get(), text) },
                    { _ -> holder.get()?.let { onClosed(k, it) } ?: endedEarly.set(true) }
                )
            } catch (e: NoLineHere) {
                retryAt[k] = now + NO_LINE_MS
                return null
            } catch (e: Exception) { null }
            if (line == null || endedEarly.get()) {
                if (line != null) runCatching { line.close() }
                val wait = minOf((retryWait[k] ?: (RETRY_MS / 2)) * 2, RETRY_MAX_MS)
                retryWait[k] = wait
                retryAt[k] = now + wait
                return null
            }
            val o = Open(line)
            holder.set(o)
            lines[k] = o
            retryWait.remove(k)
            retryAt.remove(k)
            return o
        }
    }

    private fun onText(open: Open?, text: String) {
        val o = runCatching { JSONObject(text) }.getOrNull() ?: return
        if (o.optString("t") == "new") { bump(); return }
        val id = o.optInt("id", 0)
        if (id > 0) open?.waiting?.remove(id)?.complete(o)
    }

    /** The line ended (the relay closed it, the network went): waiting rounds get no answer; the next opens anew. */
    private fun onClosed(k: String, open: Open) {
        open.closed = true
        lines.remove(k, open)
        open.waiting.values.forEach { it.complete(null) }
        open.waiting.clear()
        bump()
    }

    private fun drop(k: String, open: Open) {
        open.closed = true
        lines.remove(k, open)
        runCatching { open.line.close() }
        open.waiting.values.forEach { it.complete(null) }
        open.waiting.clear()
    }

    /** Every line closed (the app left the screen, the book closed, live sync stopped). */
    fun closeAll() {
        for ((k, o) in lines.entries.toList()) drop(k, o)
        bump()
    }

    /** The lines of one book closed (a switch, a stop). */
    fun close(book: String) {
        for ((k, o) in lines.entries.toList()) if (k.split("|").getOrNull(1) == book) drop(k, o)
        // A runner waiting for this book's news wakes now, as after closeAll (R55).
        bump()
    }

    private fun bump() {
        news.incrementAndGet()
        synchronized(newsLock) { newsLock.notifyAll() }
    }

    /** What [waitForNews] compares against. */
    fun newsMark(): Long = news.get()

    /**
     * Waits up to [ms] for something worth a round: a nudge from the relay, a line that ended, or
     * [localGrew] answering true (this device wrote something), checked every [checkMs]. Blocking.
     */
    fun waitForNews(ms: Long, since: Long = news.get(), checkMs: Long = 1000L, localGrew: () -> Boolean = { false }): Boolean {
        val until = System.currentTimeMillis() + ms
        while (true) {
            if (news.get() != since) return true
            if (runCatching(localGrew).getOrDefault(false)) return true
            val left = until - System.currentTimeMillis()
            if (left <= 0) return false
            synchronized(newsLock) { if (news.get() == since) newsLock.wait(minOf(left, checkMs)) }
        }
    }
}
