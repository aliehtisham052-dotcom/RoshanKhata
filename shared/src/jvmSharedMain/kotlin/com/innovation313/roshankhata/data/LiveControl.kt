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
 */
object LiveControl {

    const val URL_TEXT = "https://github.com/aliehtisham052-dotcom/RoshanKhata/releases/download/live-control/live.json"
    private const val EVERY_MS = 6 * 60 * 60_000L

    @Volatile private var checkedAt = 0L
    @Volatile private var stopped = false

    /** True when live sync is turned off for everyone. Blocking on a network read every six hours; call off the main thread. */
    fun halted(now: Long = System.currentTimeMillis()): Boolean {
        if (now - checkedAt > EVERY_MS) {
            checkedAt = now
            read()?.let { stopped = it }
        }
        return stopped
    }

    /** The word as last read, without a network read: for a thread that must answer at once. */
    fun haltedCached(): Boolean = stopped

    /** What the file says: true = stopped, false = live, null = no answer (kept as it was). */
    fun read(): Boolean? = runCatching {
        val c = URL(URL_TEXT).openConnection() as HttpURLConnection
        c.connectTimeout = 5000; c.readTimeout = 5000
        c.instanceFollowRedirects = true
        try {
            when (c.responseCode) {
                200 -> parse(c.inputStream.bufferedReader().use { it.readText() })
                404 -> false
                else -> null
            }
        } finally { c.disconnect() }
    }.getOrNull()

    fun parse(text: String): Boolean? = runCatching { !JSONObject(text).optBoolean("live", true) }.getOrNull()
}
