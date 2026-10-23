package com.innovation313.roshankhata.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
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

    /**
     * The relays after Cloudflare's, in the order they are tried. The owner's hosting is the second (22 Oct
     * 2026, the review: Supabase's free project pauses after seven quiet days and has a sixth of Cloudflare's
     * budget — it stays built and deployed, out of the list until it is needed: add Road(SUPABASE, "sb")).
     */
    @Volatile var others: List<Road> = listOf(Road(HOSTING, "hs"))

    /** Every relay in order; none while [base] is null (the whole relay road closed). */
    fun roads(): List<Road> {
        val first = base?.trimEnd('/') ?: return emptyList()
        return listOf(Road(first, "")) + others.filter { it.base.trimEnd('/') != first }
    }


    const val RESEND_MS = 2 * 60_000L
    /** A parcel nobody acknowledged is sent again after RESEND_MS, then twice as long each time, up to this. */
    const val RESEND_MAX_MS = 60 * 60_000L

    /** The relay takes a parcel of at most 1 MB (cloud/worker MAX_PARCEL); a message is kept under it. */
    const val MAX_SEND = 900 * 1024

    /** Relays that do not know the one-request "round" yet (answered 404): the three requests of before are used. */
    private val legacy = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

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

    /**
     * The book's pass at a relay: only a holder of the book's key can make it.
     * Each relay gets its own (22 Oct 2026, the review: one pass for all, and
     * whoever ran one relay held the book's pass at the others) — the name of
     * the relay's host is in it; Cloudflare's, the first, keeps the v1 pass
     * its boxes were opened with.
     */
    fun token(keyHex: String, book: String, relay: String? = null): String {
        val key = ByteArray(32) { i -> keyHex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }
        val host = relay?.let { runCatching { URL(it).host.lowercase() }.getOrNull() }
        val what = if (host == null || relay?.trimEnd('/') == RELAY) "rk-relay-v1:$book" else "rk-relay-v2:$host:$book"
        return mac.doFinal(what.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
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
        // What this device could not take from each other device (a gap): said back, so that device starts again there.
        val need = JSONObject()
        LiveSync.state(sql).peers.forEach { p ->
            val n = getMeta(sql, k("need_" + p.device, id.book))?.toLongOrNull()
            if (n != null) need.put(p.device, p.recv)
        }
        return JSONObject().apply {
            put("dev", id.device); put("name", id.name)
            put("acks", acks)
            if (need.length() > 0) put("need", need)
            put("parcel", LiveSync.export(sql, after, page).toJson())
        }
    }

    /**
     * Another device's message, written in. False when it is not for this
     * book or comes from a device this book was never paired with.
     */
    /** Why the last parcel [absorb] refused was refused (the engine's word, e.g. "schema 39"), for the round's answer. */
    @Volatile private var lastRefusal: String? = null

    suspend fun absorb(sql: SyncSql, msg: JSONObject, now: Long = System.currentTimeMillis()): Boolean {
        lastRefusal = null
        val id = LiveSync.identity(sql) ?: return false
        val dev = msg.optString("dev")
        if (dev.isBlank() || dev == id.device) return false
        if (LiveSync.state(sql).peers.none { it.device == dev }) return false
        val theirAck = msg.optJSONObject("acks")?.optLong(id.device, -1L)?.takeIf { it >= 0 }
        val parcel = LiveSync.Parcel.of(msg.getJSONObject("parcel"))
        // Written, and the pointers moved, in one transaction with the WiFi rounds' (LiveSync.applyFrom).
        // A gap: what came before is on its way again (the sender resends what is not acknowledged).
        // The other device's "need" (it never got lines after it) moves this one's pointer back, as a WiFi
        // round's answer does (LiveSync.lowerSent); without it a pointer behind stayed a gap for ever.
        msg.optJSONObject("need")?.optLong(id.device, -1L)?.takeIf { it >= 0 }?.let {
            LiveSync.lowerSent(sql, dev, it)
            setMeta(sql, k("lowered", id.book), "1")
        }
        return when (val r = LiveSync.applyFrom(sql, parcel, theirAck, now, name = msg.optString("name").takeIf { it.isNotBlank() })) {
            is LiveSync.Applied.Refused -> { lastRefusal = r.why; false }
            is LiveSync.Applied.Gap -> { setMeta(sql, k("need_" + dev, id.book), parcel.from.toString()); true }
            else -> { setMeta(sql, k("need_" + dev, id.book), ""); true }
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
        tag: String = "", send: Boolean = true, useLines: Boolean = true,
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
        val pass = token(id.key, id.book, root)
        val headers = mapOf(
            "authorization" to "RK $pass",
            // The same pass again: some hostings drop Authorization before PHP sees it (web/relay).
            "x-rk-pass" to pass,
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
                // 503: this relay halted or overloaded — its own trouble, as for any other call (roundAll passes over it).
                if (code == 503) return Result.Halted
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

            // What this device has to say: its changes after what the others acknowledged (resent after a wait),
            // or only the acknowledgements when they moved; nothing when there is nothing new.
            val state = LiveSync.state(sql)
            val acked = state.peers.minOfOrNull { it.sent } ?: 0L
            var posted = getMeta(sql, k("sent", id.book, tag))?.toLongOrNull() ?: acked
            val postedAt = getMeta(sql, k("sent_at", id.book, tag))?.toLongOrNull() ?: 0L
            // Not acknowledged in time: sent again, each time after twice the wait (2 min … 1 h), so a peer that is
            // away a week does not cost the book its daily limit at the relay (22 Oct 2026, the review).
            var resends = getMeta(sql, k("resends", id.book, tag))?.toIntOrNull() ?: 0
            val resendWait = minOf(RESEND_MS shl minOf(resends, 10), RESEND_MAX_MS)
            var resending = false
            // A peer said it never got what was sent (absorb lowered the pointer): sent again at once.
            if (getMeta(sql, k("lowered", id.book)) == "1") { setMeta(sql, k("lowered", id.book), ""); resends = 0; posted = acked }
            if (posted > acked && now - postedAt > resendWait) { posted = acked; resending = true }
            if (posted < acked) { posted = acked; resends = 0; setMeta(sql, k("resends", id.book, tag), "0") }
            val acksNow = state.peers.joinToString(",") { it.device + ":" + it.recv }
            val acksTold = getMeta(sql, k("acks", id.book, tag))
            // A gap found in the last round's take is said in this round's parcel (the parcel is composed before
            // the take, so a "need" not yet told is a reason to send on its own).
            val needNow = buildString {
                for (p in state.peers) {
                    if (isNotEmpty()) append(',')
                    append(p.device).append(':').append(getMeta(sql, k("need_" + p.device, id.book)).orEmpty())
                }
            }
            val needTold = getMeta(sql, k("need_told", id.book, tag))
            val last = LiveSync.lastSeq(sql)
            var sealed: ByteArray? = null
            var parcelTo = 0L
            var parcelMore = false
            if (send && (last > posted || acksNow != acksTold || needNow != needTold)) {
                // Kept under the relay's own limit: a page of long rows goes as several smaller ones
                // (22 Oct 2026, the review: a page over 1 MB was refused and sent again, unchanged, for ever).
                var page = LiveSync.PAGE
                // A peer asks for lines this device already let go of (its book went back to a copy older than
                // what both had acknowledged): it cannot be caught up, only joined again — said as the WiFi road
                // says it (LiveRound.LOST), never thrown out of the round (23 Oct 2026).
                fun lost(e: LiveSync.Lost) = Result.Refused(LiveRound.LOST)
                var msg = try { message(sql, posted, page) } catch (e: LiveSync.Lost) { return lost(e) } ?: return Result.Off
                var s = LiveWire.seal(id.key, msg.toString().toByteArray(Charsets.UTF_8), LiveWire.Road.RELAY)
                while (s.size > MAX_SEND && page > 1) {
                    page /= 2
                    msg = try { message(sql, posted, page) } catch (e: LiveSync.Lost) { return lost(e) } ?: return Result.Off
                    s = LiveWire.seal(id.key, msg.toString().toByteArray(Charsets.UTF_8), LiveWire.Road.RELAY)
                }
                sealed = s
                val parcel = msg.getJSONObject("parcel")
                parcelTo = parcel.getLong("to")
                parcelMore = parcel.optBoolean("more", false)
            }

            // The round itself: one request (ack + parcel + page) where the relay knows "round" (22 Oct 2026);
            // the three requests of before where it does not yet (a hand-uploaded relay).
            var after = getMeta(sql, k("after", id.book, tag))?.toLongOrNull() ?: 0L
            val ackDue = getMeta(sql, k("ack_due", id.book, tag))?.toLongOrNull()
            fun roundJson(a: Long): JSONObject = JSONObject().apply {
                put("after", a)
                put("upTo", ackDue ?: JSONObject.NULL)
                put("parcel", sealed?.let { B64.encode(it) } ?: JSONObject.NULL)
            }
            fun roundBody(a: Long): ByteArray = roundJson(a).toString().toByteArray(Charsets.UTF_8)
            // "round" where the relay has it; a relay that answers "no such path" (uploaded before it existed) is
            // asked the old way, take first, from then on — also when the path is only found after a hello.
            fun ask(a: Long): Pair<Int, JSONObject?> {
                // Over the live line when there is one (23 Oct 2026): the same round as one message, a twentieth of a
                // request; no line (none here, or it dropped) and the round goes over HTTP, unchanged.
                if (legacy[root] != true && useLines) LiveLines.ask(root, id.book, headers, roundJson(a), now)?.let { return it }
                if (legacy[root] != true) {
                    val r = call("POST", "round", roundBody(a), "application/json")
                    if (!(r.first == 404 && r.second?.optString("why") == "no-such-path")) return r
                    legacy[root] = true
                }
                return call("GET", "take?after=$a")
            }
            var (code, o) = ask(after)
            if (code == 403 && o?.optString("why") == "say-hello-first") {
                // The relay no longer knows this device (its box emptied after a month unused, or made again):
                // hello again instead of asking for ever (22 Oct 2026, the review).
                hello()?.let { return it }
                after = 0L
                val again = ask(0L)
                code = again.first
                o = again.second
            }
            val oneCall = legacy[root] != true
            if (code == 503) return Result.Halted
            val got = o
            if (code != 200 || got == null) return Result.Refused(got?.optString("why") ?: (if (oneCall) "round $code" else "take $code"))
            var sent = false
            if (oneCall) {
                if (ackDue != null) setMeta(sql, k("ack_due", id.book, tag), "")
                if (sealed != null) {
                    if (got.isNull("seq")) return Result.Refused("round no seq")
                    setMeta(sql, k("sent", id.book, tag), maxOf(posted, parcelTo).toString())
                    setMeta(sql, k("sent_at", id.book, tag), now.toString())
                    setMeta(sql, k("acks", id.book, tag), acksNow)
                    setMeta(sql, k("need_told", id.book, tag), needNow)
                    if (resending) setMeta(sql, k("resends", id.book, tag), (resends + 1).toString())
                    sent = true
                }
            }

            // The others' messages, in order, from where this device last read.
            var took = 0
            var refusedWhy: String? = null
            val list = got.getJSONArray("parcels")
            for (i in 0 until list.length()) {
                val p = list.getJSONObject(i)
                val sealedIn = B64.decode(p.getString("body"))
                val plainBytes = LiveWire.open(id.key, sealedIn, LiveWire.Road.RELAY)
                if (plainBytes != null) {
                    val msg = runCatching { JSONObject(String(plainBytes, Charsets.UTF_8)) }.getOrNull()
                    // The relay's word on who sent it must be the sealed message's own: a parcel the relay
                    // filed under another device (a bug or a lie there) is not taken as that device's.
                    val from = p.optString("from")
                    val same = msg != null && (from.isBlank() || from == msg.optString("dev"))
                    if (msg != null && same && absorb(sql, msg, now)) {
                        took++
                        // Which relay that device's parcels come by: this device sends there too (roundAll).
                        setMeta(sql, k("via_" + msg.optString("dev"), id.book), tag)
                    } else if (msg != null && same) {
                        // The book itself refused it (the other device runs a newer schema): said at the end of the
                        // round instead of "Live" (23 Oct 2026, the second review). The cursor still moves — the
                        // sender's pointer does not, so it comes again once this device is updated.
                        lastRefusal?.let { refusedWhy = it }
                    }
                }
                after = maxOf(after, p.getLong("seq"))
                setMeta(sql, k("after", id.book, tag), after.toString())
            }
            var more = got.optBoolean("more", false) || parcelMore
            if (list.length() > 0) {
                if (oneCall) setMeta(sql, k("ack_due", id.book, tag), after.toString())
                else call("POST", "ack", JSONObject().put("upTo", after).toString().toByteArray(), "application/json")
            }
            if (took == 0 && refusedWhy != null) return Result.Refused(refusedWhy!!)
            if (!oneCall && sealed != null) {
                val (sc, so) = call("POST", "send", sealed, "application/octet-stream")
                if (sc != 200) return Result.Refused(so?.optString("why") ?: "send $sc")
                setMeta(sql, k("sent", id.book, tag), maxOf(posted, parcelTo).toString())
                setMeta(sql, k("sent_at", id.book, tag), now.toString())
                setMeta(sql, k("acks", id.book, tag), acksNow)
                setMeta(sql, k("need_told", id.book, tag), needNow)
                if (resending) setMeta(sql, k("resends", id.book, tag), (resends + 1).toString())
                sent = true
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
     * rounds), and sent to as well while some peer's parcels last came by it: another device that
     * cannot reach the first relay leaves its parcels there and reads them there, so this device's
     * changes must reach it there too. A parcel read twice, from two relays, changes nothing the second
     * time (every write lands by uid and each device's log line).
     *
     * Off, NotPaired and Halted (the kill switch) are the book's state and end the round at once; a relay
     * that answers 503 (its owner halted that one) is passed over like one that cannot be reached.
     */
    /** One relay round at a time for a book: a wake-up's round and the screen's never run together (23 Oct 2026). */
    private val rounds = ConcurrentHashMap<String, Mutex>()

    suspend fun roundAll(
        sql: SyncSql, http: Http = plain, roads: List<Road> = roads(), now: Long = System.currentTimeMillis(),
        halted: () -> Boolean = { LiveControl.halted() }, readOthers: Boolean = true
    ): Result {
        if (roads.isEmpty()) return Result.Off
        if (halted()) return Result.Halted
        val id = LiveSync.identity(sql)
        val lock = rounds.getOrPut(id?.book ?: "") { Mutex() }
        return lock.withLock { roundAllLocked(sql, http, roads, now, id, readOthers) }
    }

    private suspend fun roundAllLocked(
        sql: SyncSql, http: Http, roads: List<Road>, now: Long, id: LiveSync.Identity?, readOthers: Boolean
    ): Result {
        var mine: Result.Done? = null
        var failed: Result? = null
        var took = 0
        var more = false
        for (road in roads) {
            if (mine == null) {
                when (val r = round(sql, http, road.base, now, road.tag, send = true, useLines = road.tag == "", halted = { false })) {
                    is Result.Done -> mine = r
                    Result.Off, Result.NotPaired -> return r
                    // The book's state, not the relay's: another relay would say the same.
                    is Result.Refused -> if (r.why == LiveRound.LOST || r.why.startsWith("schema")) return r else if (failed == null) failed = r
                    // A relay answering 503 is that relay halted or overloaded, never everyone stopped (the kill switch
                    // was read above): passed over like one not reached.
                    Result.Halted -> if (failed == null) failed = Result.Unreachable
                    else -> if (failed == null) failed = r
                }
                continue
            }
            // A relay some peer's parcels last came by is one that peer reads: this device's changes go there too
            // (per peer, not by time — 22 Oct 2026, the review: a 24 h mark went stale while the peer was quiet).
            val used = id != null && LiveSync.state(sql).peers.any { getMeta(sql, k("via_" + it.device, id.book)) == road.tag }
            if (!readOthers && !used) continue
            val r = round(sql, http, road.base, now, road.tag, send = used, useLines = road.tag == "", halted = { false })
            if (r is Result.Done) { took += r.took; more = more || r.more }
        }
        val first = mine ?: return failed ?: Result.Unreachable
        return first.copy(took = first.took + took, more = first.more || more)
    }

    /**
     * Tells every relay this device is done with the book (live sync stopped,
     * or the book left): its parcels there go, and with no device left the
     * relay forgets the book. Best effort; a relay not reached forgets the
     * device itself after a month unused.
     */
    suspend fun leaveAll(sql: SyncSql, http: Http = plain, roads: List<Road> = roads()) {
        val id = LiveSync.identity(sql) ?: return
        for (road in roads) {
            if (getMeta(sql, k("hello", id.book, road.tag)) != id.device) continue
            val pass = token(id.key, id.book, road.base.trimEnd('/'))
            val headers = mapOf("authorization" to "RK $pass", "x-rk-pass" to pass, "x-device" to id.device.take(16).padEnd(16, '0'))
            runCatching { http.call("POST", road.base.trimEnd('/') + "/v1/b/${id.book}/leave", headers, null) }
            setMeta(sql, k("hello", id.book, road.tag), "")
        }
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
        val pass = token(id.key, id.book, root)
        val headers = mapOf("authorization" to "RK $pass", "x-rk-pass" to pass, "x-device" to id.device.take(16).padEnd(16, '0'))
        return try {
            if (getMeta(sql, k("hello", id.book)) != id.device) {
                val (code, _) = http.call("POST", url + "hello", headers, null)
                if (code != 200) return false
                setMeta(sql, k("after", id.book), "0")
                setMeta(sql, k("hello", id.book), id.device)
            }
            val body = JSONObject().put("token", address ?: JSONObject.NULL).toString().toByteArray(Charsets.UTF_8)
            var (code, bytes) = http.call("POST", url + "wake", headers + ("content-type" to "application/json"), body)
            if (code == 403 && runCatching { JSONObject(String(bytes, Charsets.UTF_8)).optString("why") }.getOrNull() == "say-hello-first") {
                // The relay forgot this device (a month on the shop's WiFi, never a relay round): hello, then once more —
                // not a 403 every minute for ever (23 Oct 2026, the second review).
                val (hc, _) = http.call("POST", url + "hello", headers, null)
                if (hc != 200) return false
                setMeta(sql, k("after", id.book), "0")
                setMeta(sql, k("hello", id.book), id.device)
                val again = http.call("POST", url + "wake", headers + ("content-type" to "application/json"), body)
                code = again.first
            }
            if (code != 200) return false
            setMeta(sql, k("wake", id.book), address ?: "")
            true
        } catch (e: java.io.IOException) {
            false
        }
    }
}
