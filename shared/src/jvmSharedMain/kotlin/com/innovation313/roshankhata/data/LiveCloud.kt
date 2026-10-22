package com.innovation313.roshankhata.data

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
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
 * Open since the relay was deployed (22 Oct 2026); [base] = null closes it again.
 * A book uses it only when its owner turned it on ([BookSettings.LIVE_RELAY],
 * one switch for both devices, off until then): the WiFi road never needs
 * the internet, and the screens say plainly what the relay carries.
 */
object LiveCloud {

    /**
     * The relay's address; null keeps this road closed. Deployed 22 Oct 2026 by cloud-worker.yml on the
     * owner's Cloudflare account (free plan, subdomain "innovation313" — never the owner's e-mail).
     * Only this kmp-* test line opens it: decide before main whether Play users get it (free-plan limits).
     */
    @Volatile var base: String? = RELAY

    const val RELAY = "https://roshan-khata-daakiya.innovation313.workers.dev"

    /** The Supabase relay (supabase/functions/daakiya, project roshan-khata-live; deployed by relays.yml). */
    const val SUPABASE = "https://tcztkeyliletxshhkhnz.supabase.co/functions/v1/daakiya"

    /** The relay on the owner's own hosting (web/relay, uploaded by hand to public_html/r). */
    const val HOSTING = "https://ehtisham.shop/r"

    /**
     * One relay and the name its cursors are kept under. Cloudflare's tag is "" so a book that already
     * talked to it keeps its place (sync_meta "cloud_after:<book>" and the rest, as before 22 Oct 2026).
     */
    data class Road(val base: String, val tag: String)

    /** The relays after Cloudflare's, in the order they are tried (22 Oct 2026: the owner's second and third). */
    @Volatile var others: List<Road> = listOf(Road(SUPABASE, "sb"), Road(HOSTING, "hs"))

    /** Every relay in order; none while [base] is null (the whole relay road closed). */
    fun roads(): List<Road> {
        val first = base?.trimEnd('/') ?: return emptyList()
        return listOf(Road(first, "")) + others.filter { it.base.trimEnd('/') != first }
    }

    /** A relay a parcel was taken from this recently is one another device leaves its parcels at: this device sends there too. */
    const val USED_MS = 24 * 3_600_000L

    const val RESEND_MS = 2 * 60_000L

    /** The relay takes a parcel of at most 1 MB (cloud/worker MAX_PARCEL); a message is kept under it. */
    const val MAX_SEND = 900 * 1024

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
                while (true) { val n = s.read(buf); if (n < 0) break; out.write(buf, 0, n); if (out.size() > 32 shl 20) throw java.io.IOException("too big") }
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
        /** Live sync is stopped for everyone ([LiveControl]); the relay is not asked. */
        object Halted : Result
        object Unreachable : Result
        data class Refused(val why: String) : Result
    }

    // The relay's cursors, in sync_meta, per relay and book (a book changed is a conversation started again).
    private fun k(name: String, book: String, tag: String = "") =
        if (tag.isEmpty()) "cloud_${name}:$book" else "cloud_${tag}_${name}:$book"

    private suspend fun getMeta(sql: SyncSql, key: String): String? = sql.read {
        rows("SELECT v FROM sync_meta WHERE k = ?", listOf(key), "T").firstOrNull()?.get(0) as String?
    }

    private suspend fun setMeta(sql: SyncSql, key: String, value: String) = sql.write {
        exec("INSERT OR REPLACE INTO sync_meta (k, v) VALUES (?, ?)", listOf(key, value))
    }

    /** Whether this book's owner turned the relay on ([BookSettings.LIVE_RELAY]). */
    suspend fun allowed(sql: SyncSql): Boolean = sql.read {
        rows("SELECT value FROM book_settings WHERE `key` = ?", listOf(BookSettings.LIVE_RELAY), "T").firstOrNull()?.get(0) == "1"
    }

    /** The message this device leaves: its changes after [after] (at most [page] lines) and how far it has read every other device. */
    suspend fun message(sql: SyncSql, after: Long, page: Int = LiveSync.PAGE): JSONObject? {
        val id = LiveSync.identity(sql) ?: return null
        val acks = JSONObject()
        LiveSync.state(sql).peers.forEach { acks.put(it.device, it.recv) }
        return JSONObject().apply {
            put("dev", id.device); put("name", id.name)
            put("acks", acks)
            put("parcel", LiveSync.export(sql, after, page).toJson())
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
        if (LiveSync.state(sql).peers.none { it.device == dev }) return false
        val theirAck = msg.optJSONObject("acks")?.optLong(id.device, -1L)?.takeIf { it >= 0 }
        val parcel = LiveSync.Parcel.of(msg.getJSONObject("parcel"))
        // Written, and the pointers moved, in one transaction with the WiFi rounds' (LiveSync.applyFrom).
        // A gap: what came before is on its way again (the sender resends what is not acknowledged).
        return when (LiveSync.applyFrom(sql, parcel, theirAck, now, name = msg.optString("name").takeIf { it.isNotBlank() })) {
            is LiveSync.Applied.Refused -> false
            else -> true
        }
    }

    /**
     * One cloud round: say hello once, pick up the others' messages,
     * acknowledge them, leave this device's own. Nothing at all unless the
     * book's owner turned the relay on, and nothing while live sync is
     * stopped for everyone ([halted]).
     */
    suspend fun round(
        sql: SyncSql, http: Http = plain, relay: String? = base, now: Long = System.currentTimeMillis(),
        tag: String = "", send: Boolean = true,
        // Last, so a caller's trailing { } is this (22 Oct 2026: put before tag and send, the tests' { false } became send).
        halted: () -> Boolean = { LiveControl.halted() }
    ): Result {
        val root = relay?.trimEnd('/') ?: return Result.Off
        val id = LiveSync.identity(sql) ?: return Result.Off
        if (!allowed(sql)) return Result.Off
        if (halted()) return Result.Halted
        val peers = LiveSync.state(sql).peers
        if (peers.isEmpty()) return Result.NotPaired
        val url = "$root/v1/b/${id.book}/"
        val headers = mapOf(
            "authorization" to "RK " + token(id.key, id.book),
            // The same pass again: some hostings drop Authorization before PHP sees it (web/relay).
            "x-rk-pass" to token(id.key, id.book),
            // The relay's device id is 16 hex: this device's own sync id is already that shape.
            "x-device" to id.device.take(16).padEnd(16, '0')
        )
        fun call(method: String, path: String, body: ByteArray? = null, type: String? = null): Pair<Int, JSONObject?> {
            val h = if (type != null) headers + ("content-type" to type) else headers
            val (code, bytes) = http.call(method, url + path, h, body)
            return code to runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }.getOrNull()
        }
        try {
            suspend fun hello(): Result? {
                val (code, o) = call("POST", "hello")
                if (code != 200) return Result.Refused(o?.optString("why") ?: "hello $code")
                // A device new at the relay reads all that is there: a message read twice changes nothing,
                // a message skipped would be a change lost.
                setMeta(sql, k("after", id.book, tag), "0")
                setMeta(sql, k("hello", id.book, tag), id.device)
                // A device the relay meets anew has no wake-up address there: it is told again.
                setMeta(sql, k("wake", id.book, tag), "")
                return null
            }
            if (getMeta(sql, k("hello", id.book, tag)) != id.device) hello()?.let { return it }

            // 1. The others' messages, in order, from where this device last read.
            var after = getMeta(sql, k("after", id.book, tag))?.toLongOrNull() ?: 0L
            var took = 0
            var (code, o) = call("GET", "take?after=$after")
            if (code == 403 && o?.optString("why") == "say-hello-first") {
                // The relay no longer knows this device (its box emptied after a month unused, or made again):
                // hello again instead of asking for ever (22 Oct 2026, the review).
                hello()?.let { return it }
                after = 0L
                val again = call("GET", "take?after=0")
                code = again.first
                o = again.second
            }
            if (code == 503) return Result.Halted
            val got = o
            if (code != 200 || got == null) return Result.Refused(got?.optString("why") ?: "take $code")
            val list = got.getJSONArray("parcels")
            for (i in 0 until list.length()) {
                val p = list.getJSONObject(i)
                val sealed = B64.decode(p.getString("body"))
                val plainBytes = LiveWire.open(id.key, sealed)
                if (plainBytes != null) {
                    val msg = runCatching { JSONObject(String(plainBytes, Charsets.UTF_8)) }.getOrNull()
                    if (msg != null && absorb(sql, msg, now)) took++
                }
                after = maxOf(after, p.getLong("seq"))
                setMeta(sql, k("after", id.book, tag), after.toString())
            }
            if (list.length() > 0) call("POST", "ack", JSONObject().put("upTo", after).toString().toByteArray(), "application/json")
            if (took > 0) setMeta(sql, k("took_at", id.book, tag), now.toString())

            // 2. This device's own: what nobody acknowledged yet, or only the acknowledgements when they moved.
            val state = LiveSync.state(sql)
            val acked = state.peers.minOfOrNull { it.sent } ?: 0L
            var posted = getMeta(sql, k("sent", id.book, tag))?.toLongOrNull() ?: acked
            val postedAt = getMeta(sql, k("sent_at", id.book, tag))?.toLongOrNull() ?: 0L
            if (posted > acked && now - postedAt > RESEND_MS) posted = acked
            if (posted < acked) posted = acked
            val acksNow = state.peers.joinToString(",") { it.device + ":" + it.recv }
            val acksTold = getMeta(sql, k("acks", id.book, tag))
            val last = LiveSync.lastSeq(sql)
            var sent = false
            var more = got.optBoolean("more", false)
            if (send && (last > posted || acksNow != acksTold)) {
                // Kept under the relay's own limit: a page of long rows goes as several smaller ones
                // (22 Oct 2026, the review: a page over 1 MB was refused and sent again, unchanged, for ever).
                var page = LiveSync.PAGE
                var msg = message(sql, posted, page) ?: return Result.Off
                var sealed = LiveWire.seal(id.key, msg.toString().toByteArray(Charsets.UTF_8))
                while (sealed.size > MAX_SEND && page > 1) {
                    page /= 2
                    msg = message(sql, posted, page) ?: return Result.Off
                    sealed = LiveWire.seal(id.key, msg.toString().toByteArray(Charsets.UTF_8))
                }
                val parcel = msg.getJSONObject("parcel")
                val (sc, so) = call("POST", "send", sealed, "application/octet-stream")
                if (sc != 200) return Result.Refused(so?.optString("why") ?: "send $sc")
                setMeta(sql, k("sent", id.book, tag), maxOf(posted, parcel.getLong("to")).toString())
                setMeta(sql, k("sent_at", id.book, tag), now.toString())
                setMeta(sql, k("acks", id.book, tag), acksNow)
                sent = true
                more = more || parcel.optBoolean("more", false)
            }
            return Result.Done(took, sent, more)
        } catch (e: java.io.IOException) {
            return Result.Unreachable
        }
    }

    /**
     * One round over every relay (22 Oct 2026: Cloudflare, then Supabase, then the owner's hosting).
     *
     * The first relay that answers is this round's own: this device's changes go there and the others'
     * come from there. A relay after it is also read when [readOthers] (the callers do it every few
     * rounds), and sent to as well while a parcel was taken from it within [USED_MS]: another device
     * that cannot reach the first relay leaves its parcels there and reads them there, so this device's
     * changes must reach it there too. A parcel read twice, from two relays, changes nothing the second
     * time (every write lands by uid and each device's log line).
     *
     * Off, NotPaired and Halted (the kill switch) are the book's state and end the round at once; a relay
     * that answers 503 (its owner halted that one) is passed over like one that cannot be reached.
     */
    suspend fun roundAll(
        sql: SyncSql, http: Http = plain, roads: List<Road> = roads(), now: Long = System.currentTimeMillis(),
        halted: () -> Boolean = { LiveControl.halted() }, readOthers: Boolean = true
    ): Result {
        if (roads.isEmpty()) return Result.Off
        if (halted()) return Result.Halted
        val id = LiveSync.identity(sql)
        var mine: Result.Done? = null
        var failed: Result? = null
        var took = 0
        var more = false
        for (road in roads) {
            if (mine == null) {
                when (val r = round(sql, http, road.base, now, road.tag, send = true, halted = { false })) {
                    is Result.Done -> mine = r
                    Result.Off, Result.NotPaired -> return r
                    else -> if (failed == null) failed = r
                }
                continue
            }
            // Never taken from there: not used (a missing mark is not "long ago", it is "never").
            val tookAt = id?.let { getMeta(sql, k("took_at", it.book, road.tag))?.toLongOrNull() }
            val used = tookAt != null && tookAt > now - USED_MS
            if (!readOthers && !used) continue
            val r = round(sql, http, road.base, now, road.tag, send = used, halted = { false })
            if (r is Result.Done) { took += r.took; more = more || r.more }
        }
        val first = mine ?: return failed ?: Result.Unreachable
        return first.copy(took = first.took + took, more = first.more || more)
    }

    /**
     * Leaves this phone's wake-up address (a Firebase token, [address]) at the
     * relay, so a parcel left for it rings the phone while the app is closed;
     * a null [address] takes it away. Said once per address and book (kept in sync_meta),
     * and only while the owner keeps the relay on. The token says nothing of
     * the book: the relay only asks Google to deliver "rk-wake" to it.
     * True when the relay knows it now (or there was nothing to say).
     */
    suspend fun tellWake(
        sql: SyncSql, address: String?, http: Http = plain, relay: String? = base,
        halted: () -> Boolean = { LiveControl.halted() }
    ): Boolean {
        val root = relay?.trimEnd('/') ?: return false
        val id = LiveSync.identity(sql) ?: return false
        if (!allowed(sql)) return false
        if (halted()) return false
        val told = getMeta(sql, k("wake", id.book))
        if ((told ?: "") == (address ?: "")) return true
        val url = "$root/v1/b/${id.book}/"
        val headers = mapOf(
            "authorization" to "RK " + token(id.key, id.book),
            "x-rk-pass" to token(id.key, id.book),
            "x-device" to id.device.take(16).padEnd(16, '0')
        )
        return try {
            if (getMeta(sql, k("hello", id.book)) != id.device) {
                val (code, _) = http.call("POST", url + "hello", headers, null)
                if (code != 200) return false
                setMeta(sql, k("after", id.book), "0")
                setMeta(sql, k("hello", id.book), id.device)
            }
            val body = JSONObject().put("token", address ?: JSONObject.NULL).toString().toByteArray(Charsets.UTF_8)
            val (code, _) = http.call("POST", url + "wake", headers + ("content-type" to "application/json"), body)
            if (code != 200) return false
            setMeta(sql, k("wake", id.book), address ?: "")
            true
        } catch (e: java.io.IOException) {
            false
        }
    }
}
