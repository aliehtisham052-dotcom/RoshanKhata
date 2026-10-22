package com.innovation313.roshankhata.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

/**
 * Live sync's engine (Marhala 1, 22 Oct 2026): what changed in this book,
 * written out as a parcel, and a parcel from another device written in.
 *
 * The book stays the book. Nothing here owns a row; it only notes, in the
 * book file's own sync tables, which rows changed and in what order:
 *
 *  - sync_log: one line per write, made by SQLite triggers in the same
 *    transaction as the write itself, so no screen can write around it;
 *  - sync_meta: this book's sync identity (book id, its key, this device);
 *  - sync_peers: each device this book talks to, how far each side has read;
 *  - sync_alias, sync_pending, sync_conflict: one row met on two devices,
 *    a pointer waiting for the row it points at, a change that lost.
 *
 * None of these tables is Room's, and none goes into a backup: they are
 * this device's view of the conversation, not the book. Live sync is off
 * until the owner turns it on; until then no trigger exists and nothing
 * here runs.
 *
 * The rules a parcel is written in by:
 *  1. a row is found by its uid (an alias when the same thing met on a key);
 *  2. a pointer is written as the local id of the row it names, and waits in
 *     sync_pending when that row has not arrived yet;
 *  3. a row this device changed and the other device has not seen yet is a
 *     conflict: the device with the higher id keeps its version on both
 *     devices (so both end the same), and the losing version is kept in
 *     sync_conflict for the owner to see;
 *  4. a removal wins over a change made at the same time;
 *  5. nothing a parcel writes is noted again (sync_mute), so it never comes
 *     back as a change of this device.
 */
object LiveSync {

    /** The parcel format; a device refuses a parcel of another. */
    const val PROTOCOL = 1

    /** Changes per parcel; a longer log goes in several. */
    const val PAGE = 500

    /** Bumped after a parcel wrote rows, so open screens can read again. */
    private val changed = MutableStateFlow(0L)
    val changes: StateFlow<Long> get() = changed

    // ---------------------------------------------------------------- tables

    private val CREATE = listOf(
        "CREATE TABLE IF NOT EXISTS sync_meta (k TEXT PRIMARY KEY NOT NULL, v TEXT)",
        "CREATE TABLE IF NOT EXISTS sync_log (seq INTEGER PRIMARY KEY AUTOINCREMENT, tbl TEXT NOT NULL, uid TEXT NOT NULL, op TEXT NOT NULL)",
        "CREATE INDEX IF NOT EXISTS sync_log_row ON sync_log (tbl, uid)",
        "CREATE TABLE IF NOT EXISTS sync_mute (one INTEGER)",
        "CREATE TABLE IF NOT EXISTS sync_peers (device TEXT PRIMARY KEY NOT NULL, name TEXT, sent INTEGER NOT NULL DEFAULT 0, " +
            "recv INTEGER NOT NULL DEFAULT 0, host TEXT, port INTEGER, seen INTEGER)",
        "CREATE TABLE IF NOT EXISTS sync_alias (tbl TEXT NOT NULL, remote TEXT NOT NULL, local TEXT NOT NULL, PRIMARY KEY (tbl, remote))",
        "CREATE TABLE IF NOT EXISTS sync_pending (tbl TEXT NOT NULL, uid TEXT NOT NULL, col TEXT NOT NULL, target TEXT NOT NULL, " +
            "tuid TEXT NOT NULL, PRIMARY KEY (tbl, uid, col))",
        "CREATE TABLE IF NOT EXISTS sync_conflict (id INTEGER PRIMARY KEY AUTOINCREMENT, tbl TEXT NOT NULL, uid TEXT NOT NULL, " +
            "at INTEGER NOT NULL, device TEXT, kept TEXT, lost TEXT)"
    )

    private fun trig(kind: String, table: String) = "rksync_${kind}_$table"

    /** The three triggers that note every write to [t] in sync_log. */
    private fun triggers(t: SyncSchema.Table): List<String> {
        val n = t.name
        val mute = "NOT EXISTS (SELECT 1 FROM sync_mute)"
        return if (t.keyed) listOf(
            "CREATE TRIGGER IF NOT EXISTS ${trig("ins", n)} AFTER INSERT ON `$n` WHEN $mute BEGIN " +
                "INSERT INTO sync_log (tbl, uid, op) VALUES ('$n', NEW.`key`, 'U'); END",
            "CREATE TRIGGER IF NOT EXISTS ${trig("upd", n)} AFTER UPDATE ON `$n` WHEN $mute BEGIN " +
                "INSERT INTO sync_log (tbl, uid, op) VALUES ('$n', NEW.`key`, 'U'); END",
            "CREATE TRIGGER IF NOT EXISTS ${trig("del", n)} AFTER DELETE ON `$n` WHEN $mute BEGIN " +
                "INSERT INTO sync_log (tbl, uid, op) VALUES ('$n', OLD.`key`, 'D'); END"
        ) else listOf(
            // A new row gets a uid if it came without one, or with one another row already has
            // (a copy made with .copy(id = 0)); then the write is noted.
            "CREATE TRIGGER IF NOT EXISTS ${trig("ins", n)} AFTER INSERT ON `$n` BEGIN " +
                "UPDATE `$n` SET uid = lower(hex(randomblob(16))) WHERE rowid = NEW.rowid AND " +
                "(NEW.uid IS NULL OR EXISTS (SELECT 1 FROM `$n` o WHERE o.uid = NEW.uid AND o.rowid <> NEW.rowid)); " +
                "INSERT INTO sync_log (tbl, uid, op) SELECT '$n', uid, 'U' FROM `$n` WHERE rowid = NEW.rowid AND $mute; END",
            "CREATE TRIGGER IF NOT EXISTS ${trig("upd", n)} AFTER UPDATE ON `$n` " +
                "WHEN OLD.uid IS NOT NULL AND NEW.uid = OLD.uid AND $mute BEGIN " +
                "INSERT INTO sync_log (tbl, uid, op) VALUES ('$n', NEW.uid, 'U'); END",
            "CREATE TRIGGER IF NOT EXISTS ${trig("del", n)} AFTER DELETE ON `$n` WHEN OLD.uid IS NOT NULL AND $mute BEGIN " +
                "INSERT INTO sync_log (tbl, uid, op) VALUES ('$n', OLD.uid, 'D'); END"
        )
    }

    private suspend fun SyncTx.hasTables(): Boolean =
        rows("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'sync_meta'", types = "T").isNotEmpty()

    private suspend fun SyncTx.meta(k: String): String? =
        rows("SELECT v FROM sync_meta WHERE k = ?", listOf(k), "T").firstOrNull()?.get(0) as String?

    private suspend fun SyncTx.setMeta(k: String, v: String?) {
        if (v == null) exec("DELETE FROM sync_meta WHERE k = ?", listOf(k))
        else exec("INSERT OR REPLACE INTO sync_meta (k, v) VALUES (?, ?)", listOf(k, v))
    }

    private suspend fun SyncTx.installTriggers() {
        for (t in SyncSchema.tables) for (sql in triggers(t)) exec(sql)
    }

    private suspend fun SyncTx.dropTriggers() {
        for (t in SyncSchema.tables) for (k in listOf("ins", "upd", "del")) exec("DROP TRIGGER IF EXISTS ${trig(k, t.name)}")
    }

    /** Every row given a uid, and no two rows of a table sharing one (older copies made with .copy(id = 0)). */
    private suspend fun SyncTx.ensureUids() {
        for (t in SyncSchema.uidTables) {
            val n = t.name
            exec("UPDATE `$n` SET uid = lower(hex(randomblob(16))) WHERE uid IS NULL")
            exec(
                "UPDATE `$n` SET uid = lower(hex(randomblob(16))) WHERE rowid IN " +
                    "(SELECT a.rowid FROM `$n` a JOIN `$n` b ON a.uid = b.uid AND a.rowid > b.rowid)"
            )
        }
    }

    // ------------------------------------------------------------- identity

    /** This book's sync identity as this device holds it. */
    data class Identity(val book: String, val key: String, val device: String, val name: String)

    /** Who this device is in the book's conversation, and with whom it talks. */
    data class State(
        val on: Boolean,
        val identity: Identity?,
        val peers: List<Peer>,
        val conflicts: Int,
        val waiting: Long
    )

    data class Peer(val device: String, val name: String?, val sent: Long, val recv: Long, val host: String?, val port: Int?, val seen: Long?)

    fun newHex(bytes: Int): String {
        val b = ByteArray(bytes).also { SecureRandom().nextBytes(it) }
        val hex = "0123456789abcdef"
        return buildString(bytes * 2) { for (x in b) { append(hex[(x.toInt() shr 4) and 0xF]); append(hex[x.toInt() and 0xF]) } }
    }

    /**
     * Turns live sync on for this book on this device ([install] names this
     * install of the app; [name] what the other device shows). [adopt] is
     * another device's book identity (book id, key) when this device joins a
     * book that already syncs; otherwise this device's own is kept, or made.
     *
     * A book file brought from another install (a copied file, a restore)
     * starts its conversation over: the log, the peers and the aliases of
     * the other install are not this device's.
     */
    suspend fun enable(sql: SyncSql, install: String, name: String, adopt: Pair<String, String>? = null): Identity = sql.write {
        for (s in CREATE) exec(s)
        if (meta("install") != install) {
            for (t in listOf("sync_log", "sync_peers", "sync_alias", "sync_pending", "sync_conflict", "sync_mute")) exec("DELETE FROM $t")
            setMeta("install", install)
            setMeta("device", newHex(8))
        }
        if (adopt != null && adopt.first != meta("book")) {
            // Another book from now on: what was said about the old one no longer applies.
            for (t in listOf("sync_log", "sync_peers", "sync_alias", "sync_pending", "sync_conflict")) exec("DELETE FROM $t")
        }
        when {
            adopt != null -> { setMeta("book", adopt.first); setMeta("key", adopt.second) }
            meta("book") == null || meta("key") == null -> { setMeta("book", newHex(16)); setMeta("key", newHex(32)) }
        }
        setMeta("name", name)
        ensureUids()
        installTriggers()
        setMeta("on", "1")
        Identity(meta("book")!!, meta("key")!!, meta("device")!!, name)
    }

    /**
     * Turns live sync off on this device: no trigger notes a write any more,
     * and the conversation is forgotten. The book itself is untouched.
     */
    suspend fun disable(sql: SyncSql) = sql.write {
        if (!hasTables()) return@write
        dropTriggers()
        for (t in listOf("sync_log", "sync_peers", "sync_alias", "sync_pending", "sync_mute")) exec("DELETE FROM $t")
        setMeta("on", "0")
    }

    /**
     * On opening a book: a book that syncs keeps its triggers; a book file
     * that came from another install (its sync tables say so) stops noting
     * writes until the owner turns live sync on here. Returns whether this
     * device syncs this book.
     */
    suspend fun check(sql: SyncSql, install: String): Boolean = sql.write {
        if (!hasTables()) return@write false
        val on = meta("on") == "1" && meta("install") == install
        if (on) installTriggers() else { dropTriggers(); setMeta("on", "0") }
        on
    }

    suspend fun identity(sql: SyncSql): Identity? = sql.read {
        if (!hasTables() || meta("on") != "1") return@read null
        val book = meta("book") ?: return@read null
        val key = meta("key") ?: return@read null
        val device = meta("device") ?: return@read null
        Identity(book, key, device, meta("name").orEmpty())
    }

    suspend fun state(sql: SyncSql): State = sql.read {
        if (!hasTables()) return@read State(false, null, emptyList(), 0, 0)
        val on = meta("on") == "1"
        val id = run {
            val b = meta("book"); val k = meta("key"); val d = meta("device")
            if (on && b != null && k != null && d != null) Identity(b, k, d, meta("name").orEmpty()) else null
        }
        val peers = rows("SELECT device, name, sent, recv, host, port, seen FROM sync_peers ORDER BY seen DESC", types = "TTIITII").map {
            Peer(it[0] as String, it[1] as String?, it[2] as Long, it[3] as Long, it[4] as String?, (it[5] as Long?)?.toInt(), it[6] as Long?)
        }
        val conflicts = (rows("SELECT COUNT(*) FROM sync_conflict", types = "I").first()[0] as Long).toInt()
        val last = rows("SELECT COALESCE(MAX(seq), 0) FROM sync_log", types = "I").first()[0] as Long
        val acked = peers.minOfOrNull { it.sent } ?: last
        State(on, id, peers, conflicts, (last - acked).coerceAtLeast(0))
    }

    /** The highest line in this device's log: where a first copy leaves off. */
    suspend fun lastSeq(sql: SyncSql): Long = sql.read {
        if (!hasTables()) 0L else rows("SELECT COALESCE(MAX(seq), 0) FROM sync_log", types = "I").first()[0] as Long
    }

    /** Adds or updates what this device knows of [device]. A null keeps what was there. */
    suspend fun putPeer(
        sql: SyncSql, device: String, name: String? = null, sent: Long? = null, recv: Long? = null,
        host: String? = null, port: Int? = null, seen: Long? = null
    ) = sql.write {
        exec("INSERT OR IGNORE INTO sync_peers (device) VALUES (?)", listOf(device))
        if (name != null) exec("UPDATE sync_peers SET name = ? WHERE device = ?", listOf(name, device))
        if (sent != null) exec("UPDATE sync_peers SET sent = ? WHERE device = ?", listOf(sent, device))
        if (recv != null) exec("UPDATE sync_peers SET recv = ? WHERE device = ?", listOf(recv, device))
        if (host != null) exec("UPDATE sync_peers SET host = ? WHERE device = ?", listOf(host, device))
        if (port != null) exec("UPDATE sync_peers SET port = ? WHERE device = ?", listOf(port, device))
        if (seen != null) exec("UPDATE sync_peers SET seen = ? WHERE device = ?", listOf(seen, device))
    }

    suspend fun removePeer(sql: SyncSql, device: String) = sql.write { exec("DELETE FROM sync_peers WHERE device = ?", listOf(device)) }

    /** Log lines every device has read are not needed any more. */
    suspend fun prune(sql: SyncSql) = sql.write {
        val min = rows("SELECT MIN(sent) FROM sync_peers", types = "I").firstOrNull()?.get(0) as Long? ?: return@write
        exec("DELETE FROM sync_log WHERE seq <= ?", listOf(min))
    }

    // --------------------------------------------------------------- parcels

    /** One row's change: [op] "U" (here it is, as it now stands) or "D" (gone). */
    data class Change(val table: String, val uid: String, val op: String, val row: JSONObject?)

    /**
     * What one device says to another: its changes after line [from], up to
     * line [to] of its log; [more] when the log goes on past [to].
     */
    data class Parcel(
        val book: String,
        val device: String,
        val from: Long,
        val to: Long,
        val changes: List<Change>,
        val more: Boolean,
        val schema: Int = KHATA_DB_VERSION,
        val protocol: Int = PROTOCOL
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("p", protocol); put("schema", schema); put("book", book); put("dev", device)
            put("from", from); put("to", to); put("more", more)
            put("c", JSONArray().apply {
                changes.forEach { ch ->
                    put(JSONObject().apply {
                        put("t", ch.table); put("u", ch.uid); put("o", ch.op)
                        if (ch.row != null) put("r", ch.row)
                    })
                }
            })
        }

        companion object {
            fun of(o: JSONObject): Parcel {
                val arr = o.getJSONArray("c")
                val changes = (0 until arr.length()).map { i ->
                    val c = arr.getJSONObject(i)
                    Change(c.getString("t"), c.getString("u"), c.getString("o"), c.optJSONObject("r"))
                }
                return Parcel(
                    book = o.getString("book"), device = o.getString("dev"), from = o.getLong("from"), to = o.getLong("to"),
                    changes = changes, more = o.optBoolean("more", false), schema = o.getInt("schema"), protocol = o.getInt("p")
                )
            }
        }
    }

    /** A column as the book declares it. */
    private class Col(val name: String, val type: Char, val notNull: Boolean)

    private suspend fun SyncTx.columns(table: String, cache: MutableMap<String, List<Col>>): List<Col> =
        cache.getOrPut(table) {
            rows("PRAGMA table_info($table)", types = "ITTITI").map { r ->
                val declared = (r[2] as String?).orEmpty().uppercase()
                val type = when {
                    "INT" in declared -> 'I'
                    "REAL" in declared || "FLOA" in declared || "DOUB" in declared -> 'R'
                    else -> 'T'
                }
                Col(r[1] as String, type, (r[3] as Long?) == 1L)
            }
        }

    /** The uid of the row [id] of [table], or null. */
    private suspend fun SyncTx.uidOf(table: String, id: Long): String? =
        rows("SELECT uid FROM `$table` WHERE id = ?", listOf(id), "T").firstOrNull()?.get(0) as String?

    /** The local id of the row whose uid is [uid] in [table] — directly, or through an alias. */
    private suspend fun SyncTx.idOf(table: String, uid: String): Long? {
        rows("SELECT id FROM `$table` WHERE uid = ? LIMIT 1", listOf(uid), "I").firstOrNull()?.let { return it[0] as Long }
        val local = rows("SELECT local FROM sync_alias WHERE tbl = ? AND remote = ?", listOf(table, uid), "T").firstOrNull()?.get(0) as String?
            ?: return null
        return rows("SELECT id FROM `$table` WHERE uid = ? LIMIT 1", listOf(local), "I").firstOrNull()?.get(0) as Long?
    }

    /** [uid] of [table] as this device knows it (its alias, when it has one). */
    private suspend fun SyncTx.localUid(table: String, uid: String): String =
        rows("SELECT local FROM sync_alias WHERE tbl = ? AND remote = ?", listOf(table, uid), "T").firstOrNull()?.get(0) as String? ?: uid

    /** The row [uid] of [t] as it travels: no id, no file paths, every pointer a uid. Null when there is no such row. */
    private suspend fun SyncTx.rowOut(t: SyncSchema.Table, uid: String, cache: MutableMap<String, List<Col>>): JSONObject? {
        val cols = columns(t.name, cache).filter { it.name != "id" && it.name !in t.localOnly }
        val select = cols.joinToString(", ") { "`${it.name}`" }
        val types = String(CharArray(cols.size) { cols[it].type })
        val r = rows("SELECT $select FROM `${t.name}` WHERE `${t.idColumn}` = ? LIMIT 1", listOf(uid), types).firstOrNull() ?: return null
        val o = JSONObject()
        cols.forEachIndexed { i, c ->
            val v = r[i]
            val target = t.fks[c.name]
            val out: Any? = if (target != null && v != null) uidOf(target, v as Long) else v
            o.put(c.name, out ?: JSONObject.NULL)
        }
        return o
    }

    /**
     * This device's changes after line [after] of its log, at most [PAGE]
     * lines: each row once, where it first changed, as it stands now (a row
     * changed five times travels once). A row gone since travels as gone.
     */
    suspend fun export(sql: SyncSql, after: Long, page: Int = PAGE): Parcel = sql.read {
        val id = run {
            val b = meta("book") ?: error("live sync is off")
            val d = meta("device") ?: error("live sync is off")
            b to d
        }
        val lines = rows("SELECT seq, tbl, uid, op FROM sync_log WHERE seq > ? ORDER BY seq LIMIT ?", listOf(after, page.toLong()), "ITTT")
        if (lines.isEmpty()) return@read Parcel(id.first, id.second, after, after, emptyList(), false)
        val order = LinkedHashMap<String, Pair<String, String>>()
        val ops = HashMap<String, String>()
        for (l in lines) {
            val key = (l[1] as String) + "\u0000" + (l[2] as String)
            if (key !in order) order[key] = (l[1] as String) to (l[2] as String)
            ops[key] = l[3] as String
        }
        val cache = HashMap<String, List<Col>>()
        val changes = ArrayList<Change>()
        for ((key, tu) in order) {
            val t = SyncSchema.table(tu.first) ?: continue
            if (ops[key] == "D") {
                // Still here after all (removed, then made again with the same uid): it travels as it stands.
                val row = rowOut(t, tu.second, cache)
                changes.add(if (row == null) Change(t.name, tu.second, "D", null) else Change(t.name, tu.second, "U", row))
            } else {
                // Not here any more: a later "D" line says so, or the row's uid moved to the one
                // it met on another device, and its new uid is in the log already.
                val row = rowOut(t, tu.second, cache) ?: continue
                changes.add(Change(t.name, tu.second, "U", row))
            }
        }
        val to = lines.last()[0] as Long
        Parcel(id.first, id.second, after, to, changes, lines.size == page)
    }

    /**
     * Every row of the book as changes, for a first copy (a device that has
     * nothing yet). Parents before children; [after] and [to] are this
     * device's log line at the moment the copy was taken.
     */
    suspend fun snapshot(sql: SyncSql): Parcel = sql.read {
        val book = meta("book") ?: error("live sync is off")
        val device = meta("device") ?: error("live sync is off")
        val last = rows("SELECT COALESCE(MAX(seq), 0) FROM sync_log", types = "I").first()[0] as Long
        val cache = HashMap<String, List<Col>>()
        val changes = ArrayList<Change>()
        for (t in SyncSchema.tables) {
            val keys = rows("SELECT `${t.idColumn}` FROM `${t.name}` WHERE `${t.idColumn}` IS NOT NULL ORDER BY rowid", types = "T")
            for (k in keys) {
                val u = k[0] as String
                rowOut(t, u, cache)?.let { changes.add(Change(t.name, u, "U", it)) }
            }
        }
        Parcel(book, device, 0, last, changes, false)
    }

    /** How a parcel went in. */
    sealed interface Applied {
        /** Written: [rows] changes landed; this device has now read the sender's log up to [to]. */
        data class Done(val rows: Int, val conflicts: Int, val to: Long) : Applied
        /** Not this book, or another version of the app: nothing written. */
        data class Refused(val why: String) : Applied
        /** The parcel starts after a line this device never got: ask again from [need]. */
        data class Gap(val need: Long) : Applied
    }

    class MissingParent(message: String) : Exception(message)

    /**
     * Writes [p] (from the device [p].device) into this book, all of it or
     * none of it. [recv] is how far this device had read that device's log;
     * [sentToThem] how far that device had read this one's (a row this device
     * changed after that line is a change the other has not seen: a conflict).
     */
    suspend fun apply(sql: SyncSql, p: Parcel, recv: Long, sentToThem: Long, now: Long = System.currentTimeMillis()): Applied {
        if (p.protocol != PROTOCOL) return Applied.Refused("protocol ${p.protocol}")
        if (p.schema != KHATA_DB_VERSION) return Applied.Refused("schema ${p.schema}")
        if (p.from > recv) return Applied.Gap(recv)
        if (p.to <= recv) return Applied.Done(0, 0, recv)
        val result = sql.write {
            val book = meta("book")
            if (book != p.book) return@write Applied.Refused("another book")
            val mine = meta("device").orEmpty()
            exec("INSERT INTO sync_mute (one) VALUES (1)")
            val cache = HashMap<String, List<Col>>()
            var written = 0
            var conflicts = 0
            var waiting = p.changes
            while (waiting.isNotEmpty()) {
                val next = ArrayList<Change>()
                for (c in waiting) {
                    when (val r = applyOne(c, p.device, mine, sentToThem, now, cache)) {
                        One.Later -> next.add(c)
                        is One.Wrote -> { written++; if (r.conflict) conflicts++ }
                        One.Skipped -> {}
                    }
                }
                if (next.size == waiting.size) {
                    throw MissingParent(next.joinToString(limit = 5) { "${it.table}/${it.uid}" })
                }
                waiting = next
            }
            resolvePending()
            exec("DELETE FROM sync_mute")
            Applied.Done(written, conflicts, p.to)
        }
        if (result is Applied.Done && result.rows > 0) changed.value = changed.value + 1
        return result
    }

    private sealed interface One {
        data class Wrote(val conflict: Boolean) : One
        object Skipped : One
        /** A pointer that may not be empty names a row not here yet: try again after the rest. */
        object Later : One
    }

    private fun jsonValue(o: JSONObject, name: String): Any? = if (!o.has(name) || o.isNull(name)) null else o.get(name)

    private fun typed(v: Any?, type: Char): Any? = when {
        v == null -> null
        type == 'I' -> when (v) { is Number -> v.toLong(); is Boolean -> if (v) 1L else 0L; else -> v.toString().toLongOrNull() }
        type == 'R' -> when (v) { is Number -> v.toDouble(); else -> v.toString().toDoubleOrNull() }
        else -> v.toString()
    }

    /** A change this device made to [table]/[uid] after line [after], that is: one the other device has not seen. */
    private suspend fun SyncTx.unseen(table: String, uid: String, after: Long): Boolean =
        rows("SELECT 1 FROM sync_log WHERE tbl = ? AND uid = ? AND seq > ? LIMIT 1", listOf(table, uid, after), "I").isNotEmpty()

    private suspend fun SyncTx.applyOne(
        c: Change, from: String, mine: String, sentToThem: Long, now: Long, cache: MutableMap<String, List<Col>>
    ): One {
        val t = SyncSchema.table(c.table) ?: return One.Skipped
        if (t.keyed) return applySetting(c, from, mine, sentToThem, now)
        val n = t.name
        var localId = idOf(n, c.uid)
        var localUid = if (localId != null) localUid(n, c.uid) else c.uid

        if (c.op == "D") {
            if (localId == null) return One.Skipped
            exec("DELETE FROM `$n` WHERE id = ?", listOf(localId))
            exec("DELETE FROM sync_pending WHERE tbl = ? AND uid = ?", listOf(n, localUid))
            return One.Wrote(false)
        }
        val row = c.row ?: return One.Skipped
        val cols = columns(n, cache).filter { it.name != "id" && it.name != "uid" && it.name !in t.localOnly && row.has(it.name) }
        val values = ArrayList<Any?>(cols.size)
        val pending = ArrayList<Triple<String, String, String>>()
        for (col in cols) {
            val raw = jsonValue(row, col.name)
            val target = t.fks[col.name]
            if (target != null && raw != null) {
                val id = idOf(target, raw.toString())
                if (id == null) {
                    if (col.notNull) return One.Later
                    pending.add(Triple(col.name, target, raw.toString()))
                }
                values.add(id)
            } else {
                values.add(typed(raw, col.type))
            }
        }

        // The same thing made on two devices (a product's name, a day's close): one row, one uid.
        if (localId == null && t.natural.isNotEmpty()) {
            val keys = t.natural.map { k -> values.getOrNull(cols.indexOfFirst { it.name == k }) }
            if (keys.none { it == null } && t.natural.all { k -> cols.any { it.name == k } }) {
                val where = t.natural.joinToString(" AND ") { "`$it` = ?" }
                val hit = rows("SELECT id, uid FROM `$n` WHERE $where LIMIT 1", keys, "IT").firstOrNull()
                if (hit != null) {
                    localId = hit[0] as Long
                    val theirs = c.uid
                    val ours = hit[1] as String? ?: theirs
                    if (theirs < ours) {
                        // Both devices keep the smaller uid; this one's lines move with it.
                        exec("UPDATE `$n` SET uid = ? WHERE id = ?", listOf(theirs, localId))
                        exec("UPDATE sync_log SET uid = ? WHERE tbl = ? AND uid = ?", listOf(theirs, n, ours))
                        exec("INSERT OR REPLACE INTO sync_alias (tbl, remote, local) VALUES (?, ?, ?)", listOf(n, ours, theirs))
                        localUid = theirs
                    } else {
                        exec("INSERT OR REPLACE INTO sync_alias (tbl, remote, local) VALUES (?, ?, ?)", listOf(n, theirs, ours))
                        localUid = ours
                    }
                }
            }
        }

        var conflict = false
        if (localId != null && unseen(n, localUid, sentToThem)) {
            conflict = true
            val kept = if (mine > from) "mine" else "theirs"
            val ownRow = rowOut(t, localUid, cache)
            exec(
                "INSERT INTO sync_conflict (tbl, uid, at, device, kept, lost) VALUES (?, ?, ?, ?, ?, ?)",
                listOf(n, localUid, now, from, kept, (if (kept == "mine") row else ownRow)?.toString())
            )
            if (kept == "mine") return One.Wrote(true)
        }

        if (localId != null) {
            if (cols.isNotEmpty()) {
                val set = cols.joinToString(", ") { "`${it.name}` = ?" }
                exec("UPDATE `$n` SET $set WHERE id = ?", values + localId)
            }
        } else {
            val names = (listOf("uid") + cols.map { it.name }).joinToString(", ") { "`$it`" }
            val marks = (0..cols.size).joinToString(", ") { "?" }
            exec("INSERT INTO `$n` ($names) VALUES ($marks)", listOf<Any?>(c.uid) + values)
            localUid = c.uid
        }
        exec("DELETE FROM sync_pending WHERE tbl = ? AND uid = ?", listOf(n, localUid))
        for ((col, target, tuid) in pending) {
            exec("INSERT OR REPLACE INTO sync_pending (tbl, uid, col, target, tuid) VALUES (?, ?, ?, ?, ?)", listOf(n, localUid, col, target, tuid))
        }
        return One.Wrote(conflict)
    }

    private suspend fun SyncTx.applySetting(c: Change, from: String, mine: String, sentToThem: Long, now: Long): One {
        val n = SyncSchema.SETTINGS
        if (c.op == "D") {
            exec("DELETE FROM `$n` WHERE `key` = ?", listOf(c.uid))
            return One.Wrote(false)
        }
        val value = c.row?.let { jsonValue(it, "value") }?.toString()
        val here = rows("SELECT value FROM `$n` WHERE `key` = ?", listOf(c.uid), "T").firstOrNull()
        if (here != null && unseen(n, c.uid, sentToThem) && here[0] != value) {
            val kept = if (mine > from) "mine" else "theirs"
            exec(
                "INSERT INTO sync_conflict (tbl, uid, at, device, kept, lost) VALUES (?, ?, ?, ?, ?, ?)",
                listOf(n, c.uid, now, from, kept, if (kept == "mine") value else here[0] as String?)
            )
            if (kept == "mine") return One.Wrote(true)
        }
        exec("INSERT OR REPLACE INTO `$n` (`key`, value) VALUES (?, ?)", listOf(c.uid, value))
        return One.Wrote(false)
    }

    /** Pointers that waited for their row: written now that it is here. */
    private suspend fun SyncTx.resolvePending() {
        val waiting = rows("SELECT tbl, uid, col, target, tuid FROM sync_pending", types = "TTTTT")
        for (w in waiting) {
            val tbl = w[0] as String; val uid = w[1] as String; val col = w[2] as String
            val target = w[3] as String; val tuid = w[4] as String
            val t = SyncSchema.table(tbl) ?: continue
            if (t.fks[col] != target) continue
            val id = idOf(target, tuid) ?: continue
            exec("UPDATE `$tbl` SET `$col` = ? WHERE uid = ?", listOf(id, uid))
            exec("DELETE FROM sync_pending WHERE tbl = ? AND uid = ? AND col = ?", listOf(tbl, uid, col))
        }
    }

    // ------------------------------------------------------------ milaan

    /**
     * The whole book's fingerprint (check 6 of the plan): every synced row as
     * it travels, in uid order, through SHA-256. Two devices that hold the same
     * book give the same text, whatever their local ids.
     */
    suspend fun fingerprint(sql: SyncSql): String = sql.read {
        val d = java.security.MessageDigest.getInstance("SHA-256")
        val cache = HashMap<String, List<Col>>()
        for (t in SyncSchema.tables) {
            val keys = rows("SELECT `${t.idColumn}` FROM `${t.name}` WHERE `${t.idColumn}` IS NOT NULL ORDER BY `${t.idColumn}`", types = "T")
            d.update(t.name.toByteArray(Charsets.UTF_8))
            for (k in keys) {
                val u = k[0] as String
                val row = rowOut(t, u, cache) ?: continue
                d.update(u.toByteArray(Charsets.UTF_8))
                d.update(canonical(row).toByteArray(Charsets.UTF_8))
            }
        }
        d.digest().joinToString("") { "%02x".format(it) }
    }

    /** A row's text with its keys in order and numbers in one shape, so both devices write it alike. */
    private fun canonical(o: JSONObject): String = buildString {
        for (k in o.keys().asSequence().sorted()) {
            append(k).append('=')
            val v = o.opt(k)
            append(
                when (v) {
                    null, JSONObject.NULL -> "~"
                    is Number -> {
                        val d = v.toDouble()
                        if (d == Math.rint(d) && kotlin.math.abs(d) < 1e15) d.toLong().toString() else d.toString()
                    }
                    else -> v.toString()
                }
            )
            append('\u0001')
        }
    }

    /**
     * A conflict as the owner reads it: which kind of row, when, and each
     * field that differs — as it stands now, and as the other version (kept
     * aside) had it. A setting's other version is its value alone.
     */
    data class ConflictView(val id: Long, val table: String, val at: Long, val fields: List<Triple<String, String?, String?>>)

    suspend fun conflictViews(sql: SyncSql, limit: Int = 50): List<ConflictView> = sql.read {
        if (!hasTables()) return@read emptyList()
        val cache = HashMap<String, List<Col>>()
        rows("SELECT id, tbl, uid, at, lost FROM sync_conflict ORDER BY id DESC LIMIT ?", listOf(limit.toLong()), "ITTIT").map { r ->
            val table = r[1] as String
            val uid = r[2] as String
            val lostText = r[4] as String?
            val lost = lostText?.let { runCatching { JSONObject(it) }.getOrNull() }
            val now = SyncSchema.table(table)?.let { t -> rowOut(t, uid, cache) }
            fun text(o: JSONObject?, k: String): String? = o?.let { if (!it.has(k) || it.isNull(k)) null else it.get(k).toString() }
            val fields = if (lost != null && now != null) {
                (lost.keySet() + now.keySet()).filter { it != "uid" }.sorted().mapNotNull { k ->
                    val a = text(now, k); val b = text(lost, k)
                    if (a == b || (a?.toDoubleOrNull() != null && a.toDoubleOrNull() == b?.toDoubleOrNull())) null else Triple(k, a, b)
                }
            } else listOf(Triple(if (lost == null && lostText != null) "value" else "row", text(now, "name"), lostText?.take(200)))
            ConflictView(r[0] as Long, table, r[3] as Long, fields)
        }
    }

    /** The owner has seen it: the other version is let go. */
    suspend fun clearConflict(sql: SyncSql, id: Long) = sql.write { exec("DELETE FROM sync_conflict WHERE id = ?", listOf(id)) }

    /** The conflicts kept for the owner, newest first. */
    data class Conflict(val id: Long, val table: String, val uid: String, val at: Long, val device: String?, val kept: String?, val lost: String?)

    suspend fun conflicts(sql: SyncSql, limit: Int = 50): List<Conflict> = sql.read {
        if (!hasTables()) return@read emptyList()
        rows("SELECT id, tbl, uid, at, device, kept, lost FROM sync_conflict ORDER BY id DESC LIMIT ?", listOf(limit.toLong()), "ITTITTT").map {
            Conflict(it[0] as Long, it[1] as String, it[2] as String, it[3] as Long, it[4] as String?, it[5] as String?, it[6] as String?)
        }
    }
}
