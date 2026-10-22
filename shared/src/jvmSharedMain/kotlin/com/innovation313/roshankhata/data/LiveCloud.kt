package com.innovation313.roshankhata.data

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Live sync's second road (Marhala 2, 22 Oct 2026): when the phone and the
 * computer are not on one WiFi, each leaves its changes at the relay
 * (cloud/worker, "daakiya") and picks up the other's.
 *
 * What goes there is the same message a WiFi round carries — this device's
 * changes after the line every other device has read, and how far this
 * device has read each of theirs — sealed with the book's key
 * ([LiveWire.seal]) before it leaves the device. The relay can open nothing.
 *
 * Unlike a WiFi round there is no answer on the spot: a message is written in
 * whenever the other device next picks it up ([absorb]). A message that went
 * missing (the relay forgets after seven days) is simply sent again: when no
 * one has acknowledged what was sent for [RESEND_MS], the next round sends
 * from the line the others last acknowledged. Every write lands by uid, so a
 * message read twice changes nothing the second time.
 *
 * Inert until [base] is set (after the owner deploys the relay).
 */
object LiveCloud {

    /** The relay's address, e.g. "https://roshan-khata-daakiya.<account>.workers.dev"; null keeps this road closed. */
    @Volatile var base: String? = null

    const val RESEND_MS = 2 * 60_000L

    /** The way out to the relay; a test passes its own. */
    fun interface Http {
        /** The status and body of one call. Throws when the relay cannot be reached. */
        fun call(method: String, url: String, headers: Map<String, String>, body: ByteArray?): Pair<Int, ByteArray>
    }

    val plain = Http { method, url, headers, body ->
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 8000
            c.readTimeout = 30_000
            c.useCaches = false
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) {
                c.doOutput = true
                c.setFixedLengthStreamingMode(body.size)
                c.outputStream.use { it.write(body) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val bytes = stream?.use { s ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(1 shl 14)
                while (true) { val n = s.read(buf); if (n < 0) break; out.write(buf, 0, n); if (out.size() > 64 shl 20) error("too big") }
                out.toByteArray()
            } ?: ByteArray(0)
            code to bytes
        } finally {
            c.disconnect()
        }
    }

    /** The book's pass at the relay: only a holder of the book's key can make it. */
    fun token(keyHex: String, book: String): String {
        val key = ByteArray(32) { i -> keyHex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }
        return mac.doFinal(("rk-relay-v1:$book").toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    /** How a cloud round went. */
    sealed interface Result {
        /** [took] messages written in, [sent] whether one was left for the others. */
        data class Done(val took: Int, val sent: Boolean, val more: Boolean) : Result
        object Off : Result
        object NotPaired : Result
        object Unreachable : Result
        data class Refused(val why: String) : Result
    }

    // The relay's cursors, in sync_meta, per book (a book changed is a conversation started again).
    private fun k(name: String, book: String) = "cloud_${name}:$book"

    private suspend fun getMeta(sql: SyncSql, key: String): String? = sql.read {
        rows("SELECT v FROM sync_meta WHERE k = ?", listOf(key), "T").firstOrNull()?.get(0) as String?
    }

    private suspend fun setMeta(sql: SyncSql, key: String, value: String) = sql.write {
        exec("INSERT OR REPLACE INTO sync_meta (k, v) VALUES (?, ?)", listOf(key, value))
    }

    /** The message this device leaves: its changes after [after] and how far it has read every other device. */
    suspend fun message(sql: SyncSql, after: Long): JSONObject? {
        val id = LiveSync.identity(sql) ?: return null
        val acks = JSONObject()
        LiveSync.state(sql).peers.forEach { acks.put(it.device, it.recv) }
        return JSONObject().apply {
            put("dev", id.device); put("name", id.name)
            put("acks", acks)
            put("parcel", LiveSync.export(sql, after).toJson())
        }
    }

    /**
     * Another device's message, written in. False when it is not for this
     * book or comes from a device this book was never paired with.
     */
    suspend fun absorb(sql: SyncSql, msg: JSONObject, now: Long = System.currentTimeMillis()): Boolean {
        val id = LiveSync.identity(sql) ?: return false
        val dev = msg.optString("dev")
        if (dev.isBlank() || dev == id.device) return false
        val peer = LiveSync.state(sql).peers.firstOrNull { it.device == dev } ?: return false
        val theirAck = msg.optJSONObject("acks")?.optLong(id.device, -1L)?.takeIf { it >= 0 } ?: peer.sent
        val parcel = LiveSync.Parcel.of(msg.getJSONObject("parcel"))
        var recv = peer.recv
        when (val r = LiveSync.apply(sql, parcel, recv = peer.recv, sentToThem = theirAck, now = now)) {
            is LiveSync.Applied.Done -> recv = r.to
            is LiveSync.Applied.Gap -> {} // what came before is on its way again (the sender resends what is not acknowledged)
            is LiveSync.Applied.Refused -> return false
        }
        LiveSync.putPeer(
            sql, dev, name = msg.optString("name").takeIf { it.isNotBlank() },
            sent = maxOf(peer.sent, minOf(theirAck, LiveSync.lastSeq(sql))), recv = recv, seen = now
        )
        LiveSync.prune(sql)
        return true
    }

    /** One cloud round: say hello once, pick up the others' messages, acknowledge them, leave this device's own. */
    suspend fun round(sql: SyncSql, http: Http = plain, relay: String? = base, now: Long = System.currentTimeMillis()): Result {
        val root = relay?.trimEnd('/') ?: return Result.Off
        val id = LiveSync.identity(sql) ?: return Result.Off
        val peers = LiveSync.state(sql).peers
        if (peers.isEmpty()) return Result.NotPaired
        val url = "$root/v1/b/${id.book}/"
        val headers = mapOf(
            "authorization" to "RK " + token(id.key, id.book),
            // The relay's device id is 16 hex: this device's own sync id is already that shape.
            "x-device" to id.device.take(16).padEnd(16, '0')
        )
        fun call(method: String, path: String, body: ByteArray? = null, type: String? = null): Pair<Int, JSONObject?> {
            val h = if (type != null) headers + ("content-type" to type) else headers
            val (code, bytes) = http.call(method, url + path, h, body)
            return code to runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }.getOrNull()
        }
        try {
            if (getMeta(sql, k("hello", id.book)) != id.device) {
                val (code, o) = call("POST", "hello")
                if (code != 200) return Result.Refused(o?.optString("why") ?: "hello $code")
                // A device new at the relay reads all that is there: a message read twice changes nothing,
                // a message skipped would be a change lost.
                setMeta(sql, k("after", id.book), "0")
                setMeta(sql, k("hello", id.book), id.device)
            }

            // 1. The others' messages, in order, from where this device last read.
            var after = getMeta(sql, k("after", id.book))?.toLongOrNull() ?: 0L
            var took = 0
            val (code, o) = call("GET", "take?after=$after")
            if (code == 503) return Result.Refused("halted")
            if (code != 200 || o == null) return Result.Refused(o?.optString("why") ?: "take $code")
            val list = o.getJSONArray("parcels")
            for (i in 0 until list.length()) {
                val p = list.getJSONObject(i)
                val sealed = Base64.getDecoder().decode(p.getString("body"))
                val plainBytes = LiveWire.open(id.key, sealed)
                if (plainBytes != null) {
                    val msg = runCatching { JSONObject(String(plainBytes, Charsets.UTF_8)) }.getOrNull()
                    if (msg != null && absorb(sql, msg, now)) took++
                }
                after = maxOf(after, p.getLong("seq"))
                setMeta(sql, k("after", id.book), after.toString())
            }
            if (list.length() > 0) call("POST", "ack", JSONObject().put("upTo", after).toString().toByteArray(), "application/json")

            // 2. This device's own: what nobody acknowledged yet, or only the acknowledgements when they moved.
            val state = LiveSync.state(sql)
            val acked = state.peers.minOfOrNull { it.sent } ?: 0L
            var posted = getMeta(sql, k("sent", id.book))?.toLongOrNull() ?: acked
            val postedAt = getMeta(sql, k("sent_at", id.book))?.toLongOrNull() ?: 0L
            if (posted > acked && now - postedAt > RESEND_MS) posted = acked
            if (posted < acked) posted = acked
            val acksNow = state.peers.joinToString(",") { it.device + ":" + it.recv }
            val acksTold = getMeta(sql, k("acks", id.book))
            val last = LiveSync.lastSeq(sql)
            var sent = false
            var more = o.optBoolean("more", false)
            if (last > posted || acksNow != acksTold) {
                val msg = message(sql, posted) ?: return Result.Off
                val parcel = msg.getJSONObject("parcel")
                val sealed = LiveWire.seal(id.key, msg.toString().toByteArray(Charsets.UTF_8))
                val (sc, so) = call("POST", "send", sealed, "application/octet-stream")
                if (sc != 200) return Result.Refused(so?.optString("why") ?: "send $sc")
                setMeta(sql, k("sent", id.book), maxOf(posted, parcel.getLong("to")).toString())
                setMeta(sql, k("sent_at", id.book), now.toString())
                setMeta(sql, k("acks", id.book), acksNow)
                sent = true
                more = more || parcel.optBoolean("more", false)
            }
            return Result.Done(took, sent, more)
        } catch (e: java.io.IOException) {
            return Result.Unreachable
        }
    }
}
