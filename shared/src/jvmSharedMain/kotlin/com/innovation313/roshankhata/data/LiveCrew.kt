package com.innovation313.roshankhata.data

import org.json.JSONObject

/**
 * One shop, many devices (23 Oct 2026, the owner: "ek dukan ke teen mobile,
 * sab ki ek hi ID"). Until now a book synced between two devices: each sends
 * only its own changes, so a third device was never heard (its parcels came
 * from a device nobody knew) and never caught up with the others.
 *
 * How a device joins a shop that already syncs: a device that has the book
 * (the *sender*) gives it whole to the new one (the *joiner*) — over the
 * shop's WiFi, as the computer was always paired — and with it, for each of
 * its other devices, how far it had read that device's log when the copy was
 * taken ([offerPeers]). The joiner starts from there with each of them.
 *
 * The others learn of the joiner from the sender's next messages ("joins",
 * over the relay and the WiFi rounds alike): they add it, knowing how much of
 * their own log it already has. Until each of them shows it knows the joiner
 * (it names it among its own devices), the sender holds back what it tells
 * that device of its reading ([ackFor]) at the copy's point — so that device
 * never lets go of lines the joiner still needs. Then the hold goes.
 *
 * A joiner says which pairing it came from ("pair") until the sender has
 * answered it; a sender that took the joiner's yes through the relay (a phone
 * joiner serves no WiFi rounds) adds it on that word ([confirmFromRelay]).
 *
 * Everything is kept in sync_meta under "live_crew_", so turning live sync
 * off forgets it with the rest.
 */
object LiveCrew {

    private const val JOIN = "live_crew_join:"       // + joiner → {"name", "at": {device: line}}
    private const val HOLD = "live_crew_hold:"       // + device → the line of its log this device says it has read, at most
    private const val WAITS = "live_crew_waits:"     // + device → joiners that device has not shown it knows (comma list)
    private const val PAIR_WAIT = "live_crew_pairwait" // on a joiner: "pair|sender" until the sender is heard
    private const val P_PEERS = "live_crew_p_peers"  // on a sender: the copy's {device: line} while a pairing waits

    private suspend fun SyncTx.meta(k: String): String? =
        rows("SELECT v FROM sync_meta WHERE k = ?", listOf(k), "T").firstOrNull()?.get(0) as String?

    private suspend fun SyncTx.setMeta(k: String, v: String?) {
        if (v == null) exec("DELETE FROM sync_meta WHERE k = ?", listOf(k))
        else exec("INSERT OR REPLACE INTO sync_meta (k, v) VALUES (?, ?)", listOf(k, v))
    }

    private suspend fun SyncTx.metaLike(prefix: String): Map<String, String> =
        rows("SELECT k, v FROM sync_meta WHERE k LIKE ?", listOf("$prefix%"), "TT")
            .associate { (it[0] as String).removePrefix(prefix) to (it[1] as String? ?: "") }

    private suspend fun SyncTx.hasTables(): Boolean =
        rows("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'sync_peers'", types = "T").isNotEmpty()

    private suspend fun SyncTx.peers(): Map<String, Pair<String?, Long>> =
        rows("SELECT device, name, recv FROM sync_peers", types = "TTI").associate { it[0] as String to (it[1] as String? to it[2] as Long) }

    // ------------------------------------------------------------------ the sender

    /**
     * For an offer: this device's other devices and how far it has read each one's log right now — what the
     * joiner's copy will hold of them. Held at those lines until the pairing is answered or given up ([holdForCopy]).
     */
    suspend fun offerPeers(sql: SyncSql): JSONObject = sql.read {
        val out = JSONObject()
        if (!hasTables()) return@read out
        for ((d, p) in peers()) {
            val o = JSONObject().put("name", p.first ?: "").put("line", p.second)
            // Its letter in the book's numbers, as last heard (23 Oct 2026): the joiner never hands it out again.
            meta("mark:$d")?.takeIf { LiveSync.validMark(it) }?.let { o.put("mark", it) }
            out.put(d, o)
        }
        out
    }

    /**
     * The copy was taken with [peersAtCopy] ([offerPeers]): from now until each of those devices knows the
     * joiner, this device tells them it has read no further than that — so none lets go of what the joiner needs.
     */
    suspend fun holdForCopy(sql: SyncSql, peersAtCopy: JSONObject) = sql.write {
        setMeta(P_PEERS, peersAtCopy.toString())
        for (d in peersAtCopy.keys()) {
            val line = peersAtCopy.getJSONObject(d).optLong("line", 0L)
            val held = meta(HOLD + d)?.toLongOrNull()
            setMeta(HOLD + d, (if (held == null) line else minOf(held, line)).toString())
        }
    }

    /** The pairing was given up: the holds made for its copy go (those still waiting on an earlier joiner stay). */
    suspend fun dropCopyHold(sql: SyncSql) = sql.write {
        val copy = meta(P_PEERS)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return@write
        setMeta(P_PEERS, null)
        for (d in copy.keys()) if (meta(WAITS + d).isNullOrBlank()) setMeta(HOLD + d, null)
    }

    /**
     * The joiner [device] answered the pairing: it is one of this book's devices now, having this device's log up
     * to [from]. Every other device must hear of it ("joins") with the line of its own log the copy held.
     */
    suspend fun joined(sql: SyncSql, device: String, name: String?, from: Long) = sql.write {
        exec("INSERT OR IGNORE INTO sync_peers (device) VALUES (?)", listOf(device))
        exec("UPDATE sync_peers SET sent = MAX(sent, ?) WHERE device = ?", listOf(from, device))
        if (name != null) exec("UPDATE sync_peers SET name = ? WHERE device = ?", listOf(name, device))
        val copy = meta(P_PEERS)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
        setMeta(P_PEERS, null)
        val at = JSONObject()
        for (d in copy.keys()) if (d != device) at.put(d, copy.getJSONObject(d).optLong("line", 0L))
        setMeta(JOIN + device, JSONObject().put("name", name ?: "").put("at", at).toString())
        for (d in at.keys()) {
            val w = meta(WAITS + d).orEmpty().split(',').filter { it.isNotBlank() }.toMutableSet()
            w += device
            setMeta(WAITS + d, w.joinToString(","))
        }
    }

    /** What this device tells [device] it has read of its log: [recv], held back while that device must still learn of a joiner. */
    suspend fun ackFor(sql: SyncSql, device: String, recv: Long): Long = sql.read {
        if (!hasTables()) return@read recv
        meta(HOLD + device)?.toLongOrNull()?.let { minOf(it, recv) } ?: recv
    }

    /** The joiners still to be announced, for this device's messages; null when there are none. */
    suspend fun joinsOut(sql: SyncSql): JSONObject? = sql.read {
        if (!hasTables()) return@read null
        val j = metaLike(JOIN)
        if (j.isEmpty()) return@read null
        val out = JSONObject()
        for ((d, v) in j) runCatching { out.put(d, JSONObject(v)) }
        out.takeIf { it.length() > 0 }
    }

    /**
     * [from] names the devices it knows ([knows]): a joiner it knows is no longer waited on for it, and once
     * nothing is waited on for it the hold on what this device tells it goes. A joiner every device knows is no
     * longer announced.
     */
    suspend fun heard(sql: SyncSql, from: String, knows: Collection<String>) = sql.write {
        if (!hasTables()) return@write
        // A joiner that speaks for itself is heard: the pairing it waited on is answered.
        meta(PAIR_WAIT)?.split('|')?.let { if (it.size == 2 && it[1] == from) setMeta(PAIR_WAIT, null) }
        val w = meta(WAITS + from) ?: return@write
        val left = w.split(',').filter { it.isNotBlank() && it !in knows }
        if (left.isEmpty()) {
            setMeta(WAITS + from, null)
            if (meta(P_PEERS)?.let { runCatching { JSONObject(it) }.getOrNull()?.has(from) } != true) setMeta(HOLD + from, null)
        } else setMeta(WAITS + from, left.joinToString(","))
        // An announcement nobody still waits on is done.
        val waiting = metaLike(WAITS).values.flatMap { it.split(',') }.filter { it.isNotBlank() }.toSet()
        for (j in metaLike(JOIN).keys) if (j !in waiting) setMeta(JOIN + j, null)
    }

    // ------------------------------------------------------------------ everyone else

    /**
     * Joiners another device announced ([joinsOut]): each one this device does not know is added, as having this
     * device's log up to the line its copy held (or, when the copy held nothing of it, this device's floor — all
     * that is left to send). [me] is this device; its own announcement is never taken.
     */
    suspend fun takeJoins(sql: SyncSql, me: String, joins: JSONObject) = sql.write {
        if (!hasTables()) return@write
        val known = peers().keys
        val floor = meta("floor")?.toLongOrNull() ?: 0L
        for (d in joins.keys()) {
            if (d == me || d in known) continue
            if (!d.matches(Regex("[0-9a-f]{8,64}"))) continue
            val j = joins.optJSONObject(d) ?: continue
            val at = j.optJSONObject("at")
            val line = if (at != null && at.has(me)) maxOf(at.getLong(me), floor) else floor
            exec("INSERT OR IGNORE INTO sync_peers (device) VALUES (?)", listOf(d))
            exec("UPDATE sync_peers SET sent = ?, recv = 0 WHERE device = ?", listOf(line, d))
            j.optString("name").takeIf { it.isNotBlank() }?.let { exec("UPDATE sync_peers SET name = ? WHERE device = ?", listOf(it.take(60), d)) }
        }
    }

    /** The devices this one knows, for its messages: the others lift their holds on seeing a joiner here. */
    suspend fun knows(sql: SyncSql): List<String> = sql.read { if (!hasTables()) emptyList() else peers().keys.toList() }

    // ------------------------------------------------------------------ the joiner

    /**
     * The joiner's side of an offer, after its book was replaced with the copy: this book's identity, the sender
     * as a device that has read nothing of this one's log and whose log this one has up to the copy's line, and
     * every other device the sender knew, as far as the copy held of each. [pair] is said in this device's
     * messages until the sender is heard ([pairSaid]).
     */
    suspend fun join(
        sql: SyncSql, install: String, name: String, offer: LiveWire.Offer, peersAtCopy: JSONObject?, marks: LiveSync.LogMarks? = null
    ): LiveSync.Identity {
        val now = System.currentTimeMillis()
        // One transaction (23 Oct 2026, the handover: the identity, the sender and the pairing were three, and a
        // failure between them left a book that synced with nobody, or knew its sender without the pairing).
        return LiveSync.enableWith(sql, install, name, adopt = offer.book to offer.key, mark = offer.mark, marks = marks) { id ->
            exec("INSERT OR IGNORE INTO sync_peers (device) VALUES (?)", listOf(offer.device))
            exec(
                "UPDATE sync_peers SET name = ?, sent = 0, recv = ?, seen = ? WHERE device = ?",
                listOf(offer.name, offer.seq, now, offer.device)
            )
            setMeta("pair:" + offer.device, offer.pair)
            if (peersAtCopy != null) for (d in peersAtCopy.keys()) {
                if (d == id.device || d == offer.device) continue
                val p = peersAtCopy.optJSONObject(d) ?: continue
                exec("INSERT OR IGNORE INTO sync_peers (device) VALUES (?)", listOf(d))
                exec("UPDATE sync_peers SET sent = 0, recv = ? WHERE device = ?", listOf(p.optLong("line", 0L), d))
                p.optString("name").takeIf { it.isNotBlank() }?.let { exec("UPDATE sync_peers SET name = ? WHERE device = ?", listOf(it.take(60), d)) }
                p.optString("mark").takeIf { LiveSync.validMark(it) }?.let { LiveSync.rememberMarksIn(this, d, listOf(it)) }
            }
            // Every letter the sender knew was taken (its own and this device's included): never handed out from here.
            offer.marks?.let { LiveSync.rememberMarksIn(this, null, it) }
            if (offer.pair != null) setMeta(PAIR_WAIT, offer.pair + "|" + offer.device)
        }
    }

    /** The pairing this joiner still names in its messages ("pair"), until the sender is heard; null after. */
    suspend fun pairSaid(sql: SyncSql): String? = sql.read {
        if (!hasTables()) null else meta(PAIR_WAIT)?.substringBefore('|')?.takeIf { it.isNotBlank() }
    }

    /**
     * On a sender whose pairing waits: a message through the relay from a device it does not know, naming that
     * pairing, is the joiner's yes (a phone joiner serves no WiFi round to say it). True when it was.
     */
    suspend fun confirmFromRelay(sql: SyncSql, device: String, name: String?, pair: String?): Boolean {
        if (pair.isNullOrBlank()) return false
        val pending = LiveRound.pending(sql) ?: return false
        if (pending.pair != pair) return false
        joined(sql, device, name, pending.from)
        LiveRound.dropPending(sql)
        return true
    }

    // ------------------------------------------------------------------ a device removed

    /**
     * The owner removed [device] from this book on this device (23 Oct 2026): nothing is held or announced for it
     * any more. A device that never comes back would otherwise keep this one's acks toward the others held at a
     * copy's line for ever (it was a joiner they had to hear of), and the relays would keep every parcel for the
     * full seven days; and it would still be announced as a joiner and named in a waiting pairing's copy.
     */
    suspend fun forget(sql: SyncSql, device: String) = sql.write {
        if (!hasTables()) return@write
        setMeta(JOIN + device, null)
        setMeta(WAITS + device, null)
        setMeta(HOLD + device, null)
        meta(PAIR_WAIT)?.split('|')?.let { if (it.size == 2 && it[1] == device) setMeta(PAIR_WAIT, null) }
        val copy = meta(P_PEERS)?.let { runCatching { JSONObject(it) }.getOrNull() }
        if (copy != null && copy.has(device)) {
            copy.remove(device)
            setMeta(P_PEERS, copy.toString())
        }
        // Other devices waited on to hear of it: no longer; a device waited on for nothing else is held no more.
        for ((d, w) in metaLike(WAITS)) {
            val list = w.split(',').filter { it.isNotBlank() }
            if (device !in list) continue
            val left = list.filter { it != device }
            if (left.isEmpty()) {
                setMeta(WAITS + d, null)
                if (copy?.has(d) != true) setMeta(HOLD + d, null)
            } else setMeta(WAITS + d, left.joinToString(","))
        }
    }
}
