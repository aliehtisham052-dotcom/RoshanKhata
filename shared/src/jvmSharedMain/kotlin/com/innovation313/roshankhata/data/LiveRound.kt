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
 * (a row lands by its uid) and the pointers only move after a write.
 */
object LiveRound {

    /** Where the phone's live sync sends, in sync_meta: the computer's addresses, port, and where its log was at pairing. */
    private const val HOSTS = "live_hosts"
    private const val PORT = "live_port"
    private const val FROM = "live_from"
    private const val COMPUTER = "live_computer"

    /** The phone's target, set when a pairing offer was taken. */
    data class Target(val hosts: List<String>, val port: Int, val from: Long, val computer: String?)

    suspend fun setTarget(sql: SyncSql, t: Target?) = sql.write {
        if (t == null) {
            exec("DELETE FROM sync_meta WHERE k IN (?, ?, ?, ?)", listOf(HOSTS, PORT, FROM, COMPUTER))
        } else {
            exec("INSERT OR REPLACE INTO sync_meta (k, v) VALUES (?, ?)", listOf(HOSTS, t.hosts.joinToString(",")))
            exec("INSERT OR REPLACE INTO sync_meta (k, v) VALUES (?, ?)", listOf(PORT, t.port.toString()))
            exec("INSERT OR REPLACE INTO sync_meta (k, v) VALUES (?, ?)", listOf(FROM, t.from.toString()))
            exec("INSERT OR REPLACE INTO sync_meta (k, v) VALUES (?, ?)", listOf(COMPUTER, t.computer.orEmpty()))
        }
    }

    suspend fun target(sql: SyncSql): Target? = sql.read {
        val has = rows("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'sync_meta'", types = "T").isNotEmpty()
        if (!has) return@read null
        fun pick(rows: List<Array<Any?>>, k: String): String? = rows.firstOrNull { it[0] == k }?.get(1) as String?
        val all = rows("SELECT k, v FROM sync_meta WHERE k IN (?, ?, ?, ?)", listOf(HOSTS, PORT, FROM, COMPUTER), "TT")
        val hosts = pick(all, HOSTS)?.split(',')?.filter { it.isNotBlank() }.orEmpty()
        val port = pick(all, PORT)?.toIntOrNull() ?: return@read null
        if (hosts.isEmpty()) return@read null
        Target(hosts, port, pick(all, FROM)?.toLongOrNull() ?: 0L, pick(all, COMPUTER))
    }

    /** Step 1, on the phone: what to send. Null when this phone does not sync a book. */
    suspend fun request(sql: SyncSql, fingerprint: Boolean = false): JSONObject? {
        val id = LiveSync.identity(sql) ?: return null
        val t = target(sql) ?: return null
        val peer = LiveSync.state(sql).peers.firstOrNull()
        val sent = peer?.sent ?: t.from
        val parcel = LiveSync.export(sql, sent)
        return JSONObject().apply {
            put("dev", id.device); put("name", id.name)
            put("ack", peer?.recv ?: 0L)
            put("parcel", parcel.toJson())
            if (fingerprint) put("fp", LiveSync.fingerprint(sql))
        }
    }

    /** Step 2, on the computer: the phone's changes written in, and the answer. Null for a phone this book was not paired with. */
    suspend fun answer(sql: SyncSql, request: JSONObject, now: Long = System.currentTimeMillis()): JSONObject? {
        val id = LiveSync.identity(sql) ?: return null
        val dev = request.getString("dev")
        val peer = LiveSync.state(sql).peers.firstOrNull { it.device == dev } ?: return null
        val theirAck = request.getLong("ack")
        val parcel = LiveSync.Parcel.of(request.getJSONObject("parcel"))
        val reply = JSONObject().apply { put("dev", id.device); put("name", id.name) }
        var recv = peer.recv
        when (val r = LiveSync.apply(sql, parcel, recv = peer.recv, sentToThem = theirAck, now = now)) {
            is LiveSync.Applied.Done -> recv = r.to
            is LiveSync.Applied.Gap -> reply.put("need", r.need)
            is LiveSync.Applied.Refused -> { reply.put("refused", r.why); return reply }
        }
        LiveSync.putPeer(sql, dev, name = request.optString("name").takeIf { it.isNotBlank() }, sent = maxOf(peer.sent, theirAck), recv = recv, seen = now)
        val mine = LiveSync.export(sql, theirAck)
        reply.put("ack", recv)
        reply.put("parcel", mine.toJson())
        if (request.has("fp")) reply.put("fp", LiveSync.fingerprint(sql))
        LiveSync.prune(sql)
        return reply
    }

    /** What a round came to on the phone. */
    data class Took(val more: Boolean, val wrote: Int, val fingerprintMatches: Boolean?, val refused: String?)

    /** Step 3, on the phone: the computer's changes written in. */
    suspend fun take(sql: SyncSql, request: JSONObject, reply: JSONObject, host: String, now: Long = System.currentTimeMillis()): Took {
        reply.optString("refused").takeIf { it.isNotBlank() }?.let { return Took(false, 0, null, it) }
        val dev = reply.getString("dev")
        val t = target(sql)
        val before = LiveSync.state(sql).peers.firstOrNull { it.device == dev }
        val recvBefore = before?.recv ?: 0L
        val theirAck = reply.getLong("ack")
        val parcel = LiveSync.Parcel.of(reply.getJSONObject("parcel"))
        var recv = recvBefore
        var wrote = 0
        var refused: String? = null
        when (val r = LiveSync.apply(sql, parcel, recv = recvBefore, sentToThem = theirAck, now = now)) {
            is LiveSync.Applied.Done -> { recv = r.to; wrote = r.rows }
            is LiveSync.Applied.Gap -> {}
            is LiveSync.Applied.Refused -> refused = r.why
        }
        LiveSync.putPeer(
            sql, dev, name = reply.optString("name").takeIf { it.isNotBlank() }, sent = theirAck, recv = recv,
            host = host, port = t?.port, seen = now
        )
        LiveSync.prune(sql)
        val sentParcelMore = request.optJSONObject("parcel")?.optBoolean("more", false) == true
        // The two fingerprints say the same moment only when nothing was still on its way: the phone's was
        // taken before the computer's changes reached it (22 Oct 2026: a round that carried the computer's
        // own changes was read as two different books). Otherwise: not known yet, asked again next round.
        val quiet = parcel.changes.isEmpty() && !parcel.more && !sentParcelMore && !reply.has("need") && refused == null
        val fp = if (quiet && request.has("fp") && reply.has("fp")) request.getString("fp") == reply.getString("fp") else null
        return Took(parcel.more || sentParcelMore || reply.has("need"), wrote, fp, refused)
    }
}
