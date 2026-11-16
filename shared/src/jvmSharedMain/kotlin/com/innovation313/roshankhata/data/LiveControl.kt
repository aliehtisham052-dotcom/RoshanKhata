package com.innovation313.roshankhata.data

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * The switch that stops live sync for everyone without a new update (plan,
 * change 16): a small file Innovation 313 keeps on GitHub. When it says
 * {"live": false}, every phone and computer stops its rounds; the books are
 * untouched and keep working, only "live" waits. Read at most every six
 * hours; a file that is not there, or a network that does not answer, keeps
 * whatever was last known (live, the first time).
 *
 * The same file stops the book seal's time-stamp requests (4 Nov 2026,
 * [BookSeals]): {"seal": false} — local sealing goes on, nothing is sent to
 * the TSAs. A file without the key, or no file, allows them, so every
 * live.json already published reads as before.
 */
object LiveControl {

    const val URL_TEXT = "https://github.com/aliehtisham052-dotcom/RoshanKhata/releases/download/live-control/live.json"
    private const val EVERY_MS = 6 * 60 * 60_000L
    private const val RETRY_MS = 10 * 60_000L
    /** Connecting, and each read. */
    const val TIMEOUT_MS = 5_000
    /**
     * The whole read, from connecting to the file's last byte (8 Nov 2026 review L013): [TIMEOUT_MS] bounds each read,
     * not the request, so a server trickling a byte every 4 s held a round (and a Firebase message's ~20 s) for ever.
     */
    const val DEADLINE_MS = 8_000L
    /** live.json is a few bytes; anything bigger is not it. */
    const val MAX_BYTES = 16 * 1024
    /** The app's name only, as the time-stamp requests send it: the default names the phone's model and Android build. */
    const val USER_AGENT = Rfc3161.USER_AGENT

    @Volatile private var checkedAt = 0L
    @Volatile private var stopped = false
    @Volatile private var sealStopped = false

    /**
     * Both words read at once, every six hours at most; a missed read keeps the last ones. One caller reads at a time:
     * the round, the seal and the heir's run asking together made one read each before (L013).
     */
    @Synchronized
    private fun refresh(now: Long) {
        if (now - checkedAt > EVERY_MS) {
            // A missed read counts as a read for ten minutes only, not six hours (8 Nov 2026 review L1).
            val got = fetch()
            if (got == null) { checkedAt = now - EVERY_MS + RETRY_MS; return }
            checkedAt = now
            got.let { (live, seal) -> stopped = live; sealStopped = seal }
        }
    }

    /** True when live sync is turned off for everyone. Blocking on a network read every six hours; call off the main thread. */
    fun halted(now: Long = System.currentTimeMillis()): Boolean {
        refresh(now)
        return stopped
    }

    /** The word as last read, without a network read: for a thread that must answer at once. */
    fun haltedCached(): Boolean = stopped

    /**
     * Like [haltedCached], but a process that has never read the word (Firebase started it cold, 8 Nov 2026 review M3)
     * reads it once first: one read bounded whole by [DEADLINE_MS], then the cached word as before. A missed read
     * keeps the word as it was and is tried again at the next call after the six hours.
     */
    fun haltedAfterFirstRead(now: Long = System.currentTimeMillis()): Boolean {
        if (checkedAt == 0L) refresh(now)
        return stopped
    }

    /** True when the book seal's time-stamp requests are stopped for everyone. Blocking like [halted]. */
    fun sealHalted(now: Long = System.currentTimeMillis()): Boolean {
        refresh(now)
        return sealStopped
    }

    /** What the file says: true = stopped, false = live, null = no answer (kept as it was). */
    fun read(): Boolean? = fetch()?.first

    /** (live stopped, seal stopped), or null for no answer. A 404 stops nothing. */
    private fun fetch(): Pair<Boolean, Boolean>? = fetchFrom(URL_TEXT)

    /**
     * One GET of [url] that is over within [deadlineMs] — connecting and every byte — or is no answer (null). The
     * request runs on a thread of its own; at the deadline its connection is cut, which ends a read blocked on it,
     * and the caller is answered at once (as [Rfc3161.http]). Sends [USER_AGENT]. Never throws.
     */
    internal fun fetchFrom(url: String, deadlineMs: Long = DEADLINE_MS, timeoutMs: Int = TIMEOUT_MS): Pair<Boolean, Boolean>? {
        val c = runCatching { URL(url).openConnection() as HttpURLConnection }.getOrNull() ?: return null
        val until = System.nanoTime() + deadlineMs * 1_000_000
        var answer: Pair<Boolean, Boolean>? = null
        val worker = Thread({
            answer = runCatching { exchange(c, timeoutMs, until) }.getOrNull()
        }, "rk-live-control")
        worker.isDaemon = true
        worker.start()
        val over = try {
            worker.join(deadlineMs)
            !worker.isAlive
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        if (!over) {
            runCatching { c.disconnect() }
            return null
        }
        return answer
    }

    private fun exchange(c: HttpURLConnection, timeoutMs: Int, until: Long): Pair<Boolean, Boolean>? {
        try {
            c.connectTimeout = timeoutMs; c.readTimeout = timeoutMs
            c.instanceFollowRedirects = true
            c.useCaches = false
            c.setRequestProperty("User-Agent", USER_AGENT)
            return when (c.responseCode) {
                200 -> {
                    val out = ByteArrayOutputStream()
                    c.inputStream.use { s ->
                        val buf = ByteArray(4096)
                        while (true) {
                            if (System.nanoTime() - until > 0) throw IOException("past the deadline")
                            val n = s.read(buf); if (n < 0) break
                            out.write(buf, 0, n)
                            if (out.size() > MAX_BYTES) throw IOException("too big for live.json")
                        }
                    }
                    val text = out.toString("UTF-8")
                    val live = parse(text)
                    val seal = parseSeal(text)
                    if (live == null || seal == null) null else live to seal
                }
                404 -> false to false
                else -> null
            }
        } finally { c.disconnect() }
    }

    fun parse(text: String): Boolean? = runCatching { !JSONObject(text).optBoolean("live", true) }.getOrNull()

    /** True when the file says {"seal": false}; false when the key is absent or true; null when it does not read. */
    fun parseSeal(text: String): Boolean? = runCatching { !JSONObject(text).optBoolean("seal", true) }.getOrNull()
}
