package com.innovation313.roshankhata.data

import org.json.JSONObject
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

    @Volatile private var checkedAt = 0L
    @Volatile private var stopped = false
    @Volatile private var sealStopped = false

    /** Both words read at once, every six hours at most; a missed read keeps the last ones. */
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
     * reads it once first: one bounded read (5 s connect + 5 s read), then the cached word as before. A missed read
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
    private fun fetch(): Pair<Boolean, Boolean>? = runCatching {
        val c = URL(URL_TEXT).openConnection() as HttpURLConnection
        c.connectTimeout = 5000; c.readTimeout = 5000
        c.instanceFollowRedirects = true
        try {
            when (c.responseCode) {
                200 -> c.inputStream.bufferedReader().use { it.readText() }.let { text ->
                    val live = parse(text)
                    val seal = parseSeal(text)
                    if (live == null || seal == null) null else live to seal
                }
                404 -> false to false
                else -> null
            }
        } finally { c.disconnect() }
    }.getOrNull()

    fun parse(text: String): Boolean? = runCatching { !JSONObject(text).optBoolean("live", true) }.getOrNull()

    /** True when the file says {"seal": false}; false when the key is absent or true; null when it does not read. */
    fun parseSeal(text: String): Boolean? = runCatching { !JSONObject(text).optBoolean("seal", true) }.getOrNull()
}
