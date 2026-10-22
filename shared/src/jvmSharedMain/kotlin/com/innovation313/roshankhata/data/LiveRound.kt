package com.innovation313.roshankhata.data

import org.json.JSONObject

/**
 * One live round between the phone and the computer (Marhala 1), the same
 * code on both sides so they cannot drift apart:
 *
 *  1. the phone asks ([request]): "here is what I changed after the line you
 *     last took, and I have read yours up to line N";
 *  2. the computer writes the phone's changes in, then answers ([answer]):
 *     "I have now read yours up to line M; here is what I changed after N";
 *  3. the phone writes those in ([take]).
 *
 * Each side keeps, per other device (sync_peers), how far it has read that
 * device's log (recv) and how far that device has read its own (sent). A
 * line is only forgotten once every device has read it ([LiveSync.prune]).
 * A round that failed half way is simply repeated: every write is idempotent
 * (a row lands by its uid) and the pointers only move with a write.
 *
 * A pairing waits until the computer says yes (22 Oct 2026, the review): the
 * phone keeps the computer it was talking to until the new one answers, and
 * every round names the pairing it belongs to, so a computer holding an older
 * pairing of the same book is never answered — nor answers — as the new one.
 */
object LiveRound {

    // Where the phone's live sync sends, in sync_meta (LiveSync forgets every "live_" key when sync is turned off).
    private const val HOSTS = "live_hosts"
    private const val PORT = "live_port"
    private const val FROM = "live_from"
    private const val COMPUTER = "live_computer"
    private const val PAIR = "live_pair"
    private const val DEVICE = "live_device"
    // A pairing sent, not yet said yes to (LiveSync keeps the log after live_p_from while it waits).
    private const val P_HOSTS = "live_p_hosts"
    private const val P_PORT = "live_p_port"
    private const val P_FROM = "live_p_from"
    private const val P_COMPUTER = "live_p_computer"
    private const val P_PAIR = "live_p_pair"
    private const val P_SINCE = "live_p_since"
    private const val P_WAS_OFF = "live_p_wasoff"

    private val TARGET = listOf(HOSTS, PORT, FROM, COMPUTER, PAIR, DEVICE)
    private val PENDING = listOf(P_HOSTS, P_PORT, P_FROM, P_COMPUTER, P_PAIR, P_SINCE, P_WAS_OFF)

    /**
     * Where the phone's rounds go: the computer's addresses and port, the
     * phone's log line its copy was taken at, the computer's name, the
     * pairing's own id, and the computer's device id once it has answered.
     * [since] is set while the pairing waits for the computer's yes.
     */
    data class Target(
        val hosts: List<String>, val port: Int, val from: Long, val computer: String?,
        val pair: String? = null, val since: Long? = null, val device: String? = null
    )

    private suspend fun SyncTx.put(k: String, v: String?) {
        if (v == null) exec("DELETE FROM sync_meta WHERE k = ?", listOf(k))
        else exec("INSERT OR REPLACE INTO sync_meta (k, v) VALUES (?, ?)", listOf(k, v))
    }

    private suspend fun SyncTx.hasMeta(): Boolean =
        rows("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'sync_meta'", types = "T").isNotEmpty()

    private suspend fun SyncTx.readKeys(keys: List<String>): Map<String, String?> {
        if (!hasMeta()) return emptyMap()
        val marks = keys.joinToString(", ") { "?" }
        return rows("SELECT k, v FROM sync_meta WHERE k IN ($marks)", keys, "TT").associate { it[0] as String to it[1] as String? }
    }

    /** The computer this phone syncs with (null: stopped). */
    suspend fun setTarget(sql: SyncSql, t: Target?) = sql.write {
        if (t == null) {
            for (k in TARGET) put(k, null)
        } else {
            put(HOSTS, t.hosts.joinToString(","))
            put(PORT, t.port.toString())
            put(FROM, t.from.toString())
            put(COMPUTER, t.computer.orEmpty())
            put(PAIR, t.pair)
            put(DEVICE, t.device)
        }
    }

    suspend fun target(sql: SyncSql): Target? = sql.read {
        val m = readKeys(TARGET)
        val hosts = m[HOSTS]?.split(',')?.filter { it.isNotBlank() }.orEmpty()
        val port = m[PORT]?.toIntOrNull() ?: return@read null
        if (hosts.isEmpty()) return@read null
        Target(hosts, port, m[FROM]?.toLongOrNull() ?: 0L, m[COMPUTER], m[PAIR]?.ifBlank { null }, null, m[DEVICE]?.ifBlank { null })
    }

    /**
     * A pairing just taken by the computer, waiting for its owner's yes.
     * [wasOff]: this phone did not sync before it, so giving up on it turns
     * live sync off again ([dropPending]).
     */
    suspend fun setPending(sql: SyncSql, t: Target, wasOff: Boolean, now: Long = System.currentTimeMillis()) = sql.write {
        put(P_HOSTS, t.hosts.joinToString(","))
        put(P_PORT, t.port.toString())
        put(P_FROM, t.from.toString())
        put(P_COMPUTER, t.computer.orEmpty())
        put(P_PAIR, t.pair)
        put(P_SINCE, now.toString())
        put(P_WAS_OFF, if (wasOff) "1" else null)
    }

    suspend fun pending(sql: SyncSql): Target? = sql.read {
        val m = readKeys(PENDING)
        val hosts = m[P_HOSTS]?.split(',')?.filter { it.isNotBlank() }.orEmpty()
        val port = m[P_PORT]?.toIntOrNull() ?: return@read null
        val since = m[P_SINCE]?.toLongOrNull() ?: return@read null
        if (hosts.isEmpty()) return@read null
        Target(hosts, port, m[P_FROM]?.toLongOrNull() ?: 0L, m[P_COMPUTER], m[P_PAIR]?.ifBlank { null }, since)
    }

    /** The pairing given up (not said yes to in time); true when live sync should go off again with it. */
    suspend fun dropPending(sql: SyncSql): Boolean = sql.write {
        val wasOff = readKeys(listOf(P_WAS_OFF))[P_WAS_OFF] == "1"
        for (k in PENDING) put(k, null)
        wasOff
    }

    /**
     * The computer said yes ([device] answered the pending pairing): it is now
     * the one computer of this phone's book. What this phone knew of any
     * other goes; this one has read the phone's log up to the copy's line and
     * nothing of its own yet.
     */
    private suspend fun confirm(sql: SyncSql, device: String, name: String?) = sql.write {
        val m = readKeys(PENDING)
        val from = m[P_FROM]?.toLongOrNull() ?: 0L
        put(HOSTS, m[P_HOSTS])
        put(PORT, m[P_PORT])
        put(FROM, from.toString())
        put(COMPUTER, m[P_COMPUTER].orEmpty())
        put(PAIR, m[P_PAIR])
        put(DEVICE, device)
        // The new peer first, then the others: never a moment with no reader of this phone's log.
        exec("INSERT OR IGNORE INTO sync_peers (device) VALUES (?)", listOf(device))
        exec("UPDATE sync_peers SET sent = ?, recv = 0 WHERE device = ?", listOf(from, device))
        if (name != null) exec("UPDATE sync_peers SET name = ? WHERE device = ?", listOf(name, device))
        exec("DELETE FROM sync_peers WHERE device <> ?", listOf(device))
        for (k in PENDING) put(k, null)
    }

    /** Step 1, on the phone: what to send. Null when this phone does not sync a book. */
    suspend fun request(sql: SyncSql, fingerprint: Boolean = false): JSONObject? {
        val id = LiveSync.identity(sql) ?: return null
        val pending = pending(sql)
        val t = pending ?: target(sql) ?: return null
        val peers = LiveSync.state(sql).peers
        // While a pairing waits, the computer has the copy and nothing after it; it has sent nothing yet.
        val peer = if (pending != null) null else (t.device?.let { d -> peers.firstOrNull { it.device == d } } ?: peers.firstOrNull())
        val sent = peer?.sent ?: t.from
        val parcel = LiveSync.export(sql, sent)
        return JSONObject().apply {
            put("dev", id.device); put("name", id.name)
            put("ack", peer?.recv ?: 0L)
            put("parcel", parcel.toJson())
            t.pair?.let { put("pair", it) }
            LiveSync.mark(sql)?.let { put("mark", it) }
            if (fingerprint && pending == null) put("fp", LiveSync.fingerprint(sql))
        }
    }

    /** Step 2, on the computer: the phone's changes written in, and the answer. Null for a phone this book was not paired with. */
    suspend fun answer(sql: SyncSql, request: JSONObject, now: Long = System.currentTimeMillis()): JSONObject? {
        val id = LiveSync.identity(sql) ?: return null
        val dev = request.getString("dev")
        if (LiveSync.state(sql).peers.none { it.device == dev }) return null
        // Only the pairing this computer said yes to: a phone joining again (or a request of an older pairing)
        // is not answered as this one until the owner says yes here.
        val want = LiveSync.peerPair(sql, dev)
        val got = request.optString("pair").takeIf { it.isNotBlank() }
        if (want != got) return null
        val theirAck = request.getLong("ack")
        val parcel = LiveSync.Parcel.of(request.getJSONObject("parcel"))
        val reply = JSONObject().apply { put("dev", id.device); put("name", id.name) }
        val name = request.optString("name").takeIf { it.isNotBlank() }
        when (val r = LiveSync.applyFrom(sql, parcel, theirAck, now, name = name)) {
            is LiveSync.Applied.Done -> {}
            is LiveSync.Applied.Gap -> reply.put("need", r.need)
            is LiveSync.Applied.Refused -> { reply.put("refused", r.why); return reply }
        }
        LiveSync.settleMark(sql, dev, request.optString("mark").takeIf { it.isNotBlank() })
        val recv = LiveSync.state(sql).peers.firstOrNull { it.device == dev }?.recv ?: 0L
        // The phone asks for lines this computer already let go: its book went back to an older copy.
        val mine = try { LiveSync.export(sql, theirAck) } catch (e: LiveSync.Lost) { reply.put("refused", LOST); return reply }
        reply.put("ack", recv)
        reply.put("parcel", mine.toJson())
        LiveSync.mark(sql)?.let { reply.put("mark", it) }
        if (request.has("fp")) reply.put("fp", LiveSync.fingerprint(sql))
        return reply
    }

    /** A refusal that means the two devices must be joined again. */
    const val LOST = "lost"

    /** What a round came to on the phone. [joined]: this round was the computer's first answer to a new pairing. */
    data class Took(val more: Boolean, val wrote: Int, val fingerprintMatches: Boolean?, val refused: String?, val joined: Boolean = false)

    /** Step 3, on the phone: the computer's changes written in. */
    suspend fun take(sql: SyncSql, request: JSONObject, reply: JSONObject, host: String, now: Long = System.currentTimeMillis()): Took {
        reply.optString("refused").takeIf { it.isNotBlank() }?.let { return Took(false, 0, null, it) }
        val dev = reply.getString("dev")
        val name = reply.optString("name").takeIf { it.isNotBlank() }
        val pending = pending(sql)
        val asked = request.optString("pair").takeIf { it.isNotBlank() }
        val joined = pending != null && asked != null && pending.pair == asked
        if (joined) {
            confirm(sql, dev, name)
        } else {
            val t = target(sql)
            val known = t?.device
            // Another computer holding this book answered: not this phone's computer.
            if (known != null && known != dev) return Took(false, 0, null, "another computer")
            if (LiveSync.state(sql).peers.none { it.device == dev }) LiveSync.putPeer(sql, dev, sent = t?.from ?: 0L, recv = 0L)
            // A pairing made before computers were named in the target: the one that answers is now named.
            if (t != null && known == null) setTarget(sql, t.copy(device = dev))
        }
        val t = target(sql)
        val theirAck = reply.getLong("ack")
        val parcel = LiveSync.Parcel.of(reply.getJSONObject("parcel"))
        var wrote = 0
        var refused: String? = null
        when (val r = LiveSync.applyFrom(sql, parcel, theirAck, now, name = name, host = host, port = t?.port)) {
            is LiveSync.Applied.Done -> wrote = r.rows
            is LiveSync.Applied.Gap -> {}
            is LiveSync.Applied.Refused -> refused = r.why
        }
        LiveSync.settleMark(sql, dev, reply.optString("mark").takeIf { it.isNotBlank() })
        val sentParcelMore = request.optJSONObject("parcel")?.optBoolean("more", false) == true
        // The two fingerprints say the same moment only when nothing was still on its way: the phone's was
        // taken before the computer's changes reached it (22 Oct 2026: a round that carried the computer's
        // own changes was read as two different books). Otherwise: not known yet, asked again next round.
        val quiet = parcel.changes.isEmpty() && !parcel.more && !sentParcelMore && !reply.has("need") && refused == null
        val fp = if (quiet && request.has("fp") && reply.has("fp")) request.getString("fp") == reply.getString("fp") else null
        return Took(parcel.more || sentParcelMore || reply.has("need"), wrote, fp, refused, joined)
    }
}
