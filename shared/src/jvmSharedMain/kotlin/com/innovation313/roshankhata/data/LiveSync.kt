package com.innovation313.roshankhata.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.security.MessageDigest
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
 *    a pointer waiting for the row it points at, a change that lost;
 *  - sync_held: a change that could not be written yet (what it belongs to
 *    has not arrived, or a key of the book refused it), tried again after
 *    every parcel instead of stopping the whole conversation.
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
 *  4. a removal wins over a change made at the same time — a row removed for
 *     good, and a row put in the recycle bin alike (22 Oct 2026, the review);
 *  5. nothing a parcel writes is noted again (sync_mute), so it never comes
 *     back as a change of this device;
 *  6. a change already written once (its line is at or below what this
 *     device has read of the sender) is not written again: a parcel sent
 *     twice can no longer put an older row over a newer edit.
 */
object LiveSync {

    /** The parcel format; a device refuses a parcel of another. */
    const val PROTOCOL = 1

    /** Changes per parcel; a longer log goes in several. */
    const val PAGE = 500

    /** Bumped after a parcel wrote rows, so open screens can read again. */
    private val changed = MutableStateFlow(0L)
    val changes: StateFlow<Long> get() = changed

    /** Why live sync was turned off ([offReason]). */
    const val OFF_STOPPED = "stopped"
    /** The book was replaced by a restore, or went back to an older copy: the devices must be joined again. */
    const val OFF_RESTORED = "restored"

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
            "at INTEGER NOT NULL, device TEXT, kept TEXT, lost TEXT)",
        "CREATE TABLE IF NOT EXISTS sync_held (tbl TEXT NOT NULL, uid TEXT NOT NULL, op TEXT NOT NULL, row TEXT, device TEXT, " +
            "ack INTEGER NOT NULL DEFAULT 0, at INTEGER NOT NULL, PRIMARY KEY (tbl, uid))"
    )

    private val KINDS = listOf("ins", "upd", "del")

    private fun trig(kind: String, table: String) = "rksync_${kind}_$table"

    /**
     * The three triggers that note every write to [t] in sync_log. An update
     * is noted only when something that travels changed: a photo's path on
     * this device, or the very same values written again, is not a change the
     * other device should hear of — and could otherwise win a conflict
     * against the other device's real edit (22 Oct 2026, the review).
     */
    private fun triggers(t: SyncSchema.Table, cols: List<Col>): List<String> {
        val n = t.name
        val mute = "NOT EXISTS (SELECT 1 FROM sync_mute)"
        val moved = cols.filter { it.name != "id" && it.name != "uid" && it.name !in t.localOnly }
            .joinToString(" OR ") { "OLD.`${it.name}` IS NOT NEW.`${it.name}`" }
            .ifEmpty { "1" }
        return if (t.keyed) listOf(
            "CREATE TRIGGER ${trig("ins", n)} AFTER INSERT ON `$n` WHEN $mute BEGIN " +
                "INSERT INTO sync_log (tbl, uid, op) VALUES ('$n', NEW.`key`, 'U'); END",
            "CREATE TRIGGER ${trig("upd", n)} AFTER UPDATE ON `$n` WHEN $mute AND ($moved) BEGIN " +
                "INSERT INTO sync_log (tbl, uid, op) VALUES ('$n', NEW.`key`, 'U'); END",
            "CREATE TRIGGER ${trig("del", n)} AFTER DELETE ON `$n` WHEN $mute BEGIN " +
                "INSERT INTO sync_log (tbl, uid, op) VALUES ('$n', OLD.`key`, 'D'); END"
        ) else listOf(
            // A new row gets a uid if it came without one, or with one another row already has
            // (a copy made with .copy(id = 0)); then the write is noted.
            "CREATE TRIGGER ${trig("ins", n)} AFTER INSERT ON `$n` BEGIN " +
                "UPDATE `$n` SET uid = lower(hex(randomblob(16))) WHERE rowid = NEW.rowid AND " +
                "(NEW.uid IS NULL OR EXISTS (SELECT 1 FROM `$n` o WHERE o.uid = NEW.uid AND o.rowid <> NEW.rowid)); " +
                "INSERT INTO sync_log (tbl, uid, op) SELECT '$n', uid, 'U' FROM `$n` WHERE rowid = NEW.rowid AND $mute; END",
            "CREATE TRIGGER ${trig("upd", n)} AFTER UPDATE ON `$n` " +
                "WHEN OLD.uid IS NOT NULL AND NEW.uid = OLD.uid AND $mute AND ($moved) BEGIN " +
                "INSERT INTO sync_log (tbl, uid, op) VALUES ('$n', NEW.uid, 'U'); END",
            "CREATE TRIGGER ${trig("del", n)} AFTER DELETE ON `$n` WHEN OLD.uid IS NOT NULL AND $mute BEGIN " +
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

    /**
     * Written again every time, not only when missing (22 Oct 2026, the
     * review): a trigger corrected in a later version reaches books that
     * already sync, and a table rebuilt by a migration gets its triggers back
     * the next time the book opens.
     */
    private suspend fun SyncTx.installTriggers() {
        val cache = HashMap<String, List<Col>>()
        for (t in SyncSchema.tables) {
            for (k in KINDS) exec("DROP TRIGGER IF EXISTS ${trig(k, t.name)}")
            for (sql in triggers(t, columns(t.name, cache))) exec(sql)
        }
    }

    private suspend fun SyncTx.dropTriggers() {
        for (t in SyncSchema.tables) for (k in KINDS) exec("DROP TRIGGER IF EXISTS ${trig(k, t.name)}")
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

    /**
     * The highest line this file's log ever had: AUTOINCREMENT's own counter,
     * which lines let go ([prune]) never take back. A file put back from an
     * older copy carries an older counter.
     */
    private suspend fun SyncTx.logCounter(): Long =
        rows(
            "SELECT MAX(COALESCE((SELECT seq FROM sqlite_sequence WHERE name = 'sync_log'), 0), " +
                "COALESCE((SELECT MAX(seq) FROM sync_log), 0))",
            types = "I"
        ).firstOrNull()?.get(0) as Long? ?: 0L

    /** Live sync off in this book, the conversation forgotten, and [why] kept for the owner. The book is untouched. */
    private suspend fun SyncTx.turnOffIn(why: String) {
        for (s in CREATE) exec(s)
        dropTriggers()
        for (t in listOf("sync_log", "sync_peers", "sync_alias", "sync_pending", "sync_held", "sync_mute")) exec("DELETE FROM $t")
        // The phone's target (LiveRound's live_*), the relay's cursors, each device's pairing and the let-go line.
        exec(
            "DELETE FROM sync_meta WHERE k LIKE 'live\\_%' ESCAPE '\\' OR k LIKE 'cloud\\_%' ESCAPE '\\' " +
                "OR k LIKE 'pair:%' OR k = 'floor'"
        )
        setMeta("on", "0")
        setMeta("off_why", why)
        setMeta("paused", null)
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
        val waiting: Long,
        /** Changes from another device that could not be written yet (tried again after every parcel). */
        val held: Int = 0,
        /** Why it is off, when it was turned off ([OFF_STOPPED], [OFF_RESTORED]); null while on. */
        val offWhy: String? = null
    )

    data class Peer(val device: String, val name: String?, val sent: Long, val recv: Long, val host: String?, val port: Int?, val seen: Long?)

    /**
     * Where a device keeps, outside the book file, how far each book's log
     * had come when it last told another device (22 Oct 2026, the review: a
     * daily copy put back on the phone brought an older log, and its next
     * changes reused line numbers the computer had already read — and were
     * dropped there). Kept per book file and book id by the platform: the
     * phone's preferences, the computer's settings folder.
     */
    interface LogMarks {
        fun get(book: String): Long?
        fun put(book: String, seq: Long)
    }

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
    suspend fun enable(
        sql: SyncSql, install: String, name: String, adopt: Pair<String, String>? = null, mark: String? = null,
        marks: LogMarks? = null
    ): Identity {
        val (id, counter) = sql.write {
            for (s in CREATE) exec(s)
            // A book file that came from another device (copied whole) is not that device's: new device, new letter.
            val copied = meta("install") != null && meta("install") != install
            if (meta("install") != install) {
                for (t in listOf("sync_log", "sync_peers", "sync_alias", "sync_pending", "sync_conflict", "sync_held", "sync_mute")) exec("DELETE FROM $t")
                exec("DELETE FROM sync_meta WHERE k LIKE 'pair:%' OR k = 'floor'")
                setMeta("install", install)
                setMeta("device", newHex(8))
                setMeta("mark", null)
                setMeta("next_mark", null)
            }
            val sameBook = adopt != null && adopt.first == meta("book")
            if (adopt != null && !sameBook) {
                // Another book from now on: what was said about the old one no longer applies.
                for (t in listOf("sync_log", "sync_peers", "sync_alias", "sync_pending", "sync_conflict", "sync_held")) exec("DELETE FROM $t")
                exec("DELETE FROM sync_meta WHERE k LIKE 'pair:%' OR k = 'floor'")
            }
            val fresh = meta("book") == null || meta("key") == null
            when {
                adopt != null -> { setMeta("book", adopt.first); setMeta("key", adopt.second) }
                fresh -> { setMeta("book", newHex(16)); setMeta("key", newHex(32)) }
            }
            // This device's letter in document numbers (NumberMark): A for the device that made the book, the
            // letter the offering device chose for one that joins, else one from this device's own id.
            val device = meta("device")!!
            when {
                adopt == null && meta("mark") == null -> setMeta("mark", if (fresh && !copied) "A" else markFromDevice(device))
                adopt != null -> setMeta(
                    "mark",
                    mark?.takeIf { validMark(it) } ?: meta("mark")?.takeIf { sameBook && it != "A" } ?: markFromDevice(device)
                )
            }
            setMeta("name", name)
            setMeta("off_why", null)
            setMeta("paused", null)
            ensureUids()
            installTriggers()
            setMeta("on", "1")
            Identity(meta("book")!!, meta("key")!!, meta("device")!!, name) to logCounter()
        }
        // From here on this file's log only grows: a copy that comes back with less is caught by [check].
        marks?.put(id.book, counter)
        return id
    }

    /**
     * Turns live sync off on this device: no trigger notes a write any more,
     * and the conversation is forgotten. The book itself is untouched.
     * [why] is said to the owner ([offReason]).
     */
    suspend fun disable(sql: SyncSql, why: String = OFF_STOPPED) = sql.write {
        if (hasTables()) turnOffIn(why)
    }

    /**
     * Before a restore replaces this book's rows: nothing is noted and no
     * round runs while it does, but the conversation is kept, so a restore
     * that fails changes nothing at all ([resume]). A restore that went
     * through ends it ([disable] with [OFF_RESTORED]): the other device
     * still has the old book and the two must be joined again. Returns
     * whether the book was syncing.
     */
    suspend fun pause(sql: SyncSql): Boolean = sql.write {
        if (!hasTables() || meta("on") != "1") return@write false
        dropTriggers()
        setMeta("on", "0")
        setMeta("paused", "1")
        true
    }

    /** The restore did not go through: live sync goes on exactly where it was. */
    suspend fun resume(sql: SyncSql) = sql.write {
        if (!hasTables() || meta("paused") != "1") return@write
        setMeta("paused", null)
        setMeta("on", "1")
        installTriggers()
    }

    /**
     * On opening a book: a book that syncs keeps its triggers; a book file
     * that came from another install (its sync tables say so) stops noting
     * writes until the owner turns live sync on here. Returns whether this
     * device syncs this book.
     *
     * With [marks]: a book whose log is behind what this device already told
     * the other devices (an older copy of the file was put back) stops too,
     * and says so ([OFF_RESTORED]) — its next lines would reuse numbers the
     * others have passed, and they would never take them (22 Oct 2026).
     */
    suspend fun check(sql: SyncSql, install: String, marks: LogMarks? = null): Boolean {
        val book = sql.read { if (!hasTables()) null else meta("book") }
        val kept = book?.let { marks?.get(it) }
        val on = sql.write {
            if (!hasTables()) return@write false
            for (s in CREATE) exec(s)
            // Left paused by a restore that never said how it ended (the app was closed in between): the
            // book may have been replaced, so the conversation cannot go on.
            if (meta("paused") == "1") turnOffIn(OFF_RESTORED)
            var on = meta("on") == "1" && meta("install") == install
            if (on && kept != null && kept > logCounter()) {
                turnOffIn(OFF_RESTORED)
                on = false
            }
            if (on) installTriggers() else { dropTriggers(); setMeta("on", "0") }
            // A book paired before letters existed (22 Oct 2026) gets one now.
            if (on && meta("mark") == null) meta("device")?.let { setMeta("mark", markFromDevice(it)) }
            on
        }
        if (on && marks != null) noteMark(sql, marks)
        return on
    }

    /** After a round: how far this file's log has come, kept outside it ([LogMarks]). */
    suspend fun noteMark(sql: SyncSql, marks: LogMarks) {
        val (book, n) = sql.read {
            if (!hasTables() || meta("on") != "1") (null as String?) to 0L else meta("book") to logCounter()
        }
        if (book != null) marks.put(book, n)
    }

    /** Why live sync is off in this book ([OFF_STOPPED], [OFF_RESTORED]), or null while it is on or never was. */
    suspend fun offReason(sql: SyncSql): String? = sql.read {
        if (!hasTables() || meta("on") == "1") null else meta("off_why")
    }

    // ---------------------------------------------------------- number marks

    /** This device's letter in this book's document numbers, or null while it does not sync the book. */
    suspend fun mark(sql: SyncSql): String? = sql.read {
        if (!hasTables() || meta("on") != "1") null else meta("mark")
    }

    /** The letter for the next device that joins this book from here: one no device has had from it. */
    suspend fun nextJoinerMark(sql: SyncSql): String = sql.write {
        val n = meta("next_mark")?.toIntOrNull() ?: 1
        setMeta("next_mark", (n + 1).toString())
        markAt(n)
    }

    /** 0 → A, 1 → B … 25 → Z, then Z1, Z2 …. */
    fun markAt(n: Int): String = if (n < 26) ('A' + n).toString() else "Z${n - 25}"

    /** A letter as it may stand in a number: A–Z, then up to three more letters or digits. */
    fun validMark(m: String?): Boolean = m != null && Regex("[A-Z][A-Z0-9]{0,3}").matches(m)

    /** B to Z from a device id, for a device nobody gave a letter: never the book-maker's A. */
    private fun markFromDevice(device: String): String = markAt(1 + device.hashCode().mod(25))

    /**
     * Two devices that came to the same letter (a pairing made before letters
     * were given out, 22 Oct 2026: each took one from its own id) would hand
     * out the same numbers again. Told the other's letter in a round, the
     * device with the smaller id moves to another; the other keeps its own.
     * Returns this device's new letter, or null when nothing changed.
     */
    suspend fun settleMark(sql: SyncSql, theirDevice: String, theirMark: String?): String? = sql.write {
        if (!hasTables() || meta("on") != "1" || theirMark.isNullOrBlank()) return@write null
        val mine = meta("mark") ?: return@write null
        val me = meta("device") ?: return@write null
        if (mine != theirMark || me >= theirDevice) return@write null
        var n = 1 + (me + theirDevice).hashCode().mod(25)
        var next = markAt(n)
        var tries = 0
        while ((next == theirMark || next == "A") && tries < 30) { n = n % 25 + 1; next = markAt(n); tries++ }
        setMeta("mark", next)
        next
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
        val held = if (rows("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'sync_held'", types = "T").isEmpty()) 0
            else (rows("SELECT COUNT(*) FROM sync_held", types = "I").first()[0] as Long).toInt()
        val last = logCounter()
        val acked = peers.minOfOrNull { it.sent } ?: last
        State(on, id, peers, conflicts, (last - acked).coerceAtLeast(0), held, if (on) null else meta("off_why"))
    }

    /** The highest line in this device's log: where a first copy leaves off. */
    suspend fun lastSeq(sql: SyncSql): Long = sql.read { if (!hasTables()) 0L else logCounter() }

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

    /**
     * Forgets [device]. With nobody left to read this book's log, its lines
     * are let go as well (22 Oct 2026, the review: a log with no reader grew
     * for ever); a device that joins later starts from its own pairing.
     */
    suspend fun removePeer(sql: SyncSql, device: String) = sql.write {
        exec("DELETE FROM sync_peers WHERE device = ?", listOf(device))
        setMeta("pair:$device", null)
        val left = rows("SELECT COUNT(*) FROM sync_peers", types = "I").first()[0] as Long
        if (left == 0L && meta("live_p_from") == null) {
            val top = logCounter()
            exec("DELETE FROM sync_log")
            setMeta("floor", top.toString())
        }
    }

    /** The pairing [device] joined this book with (its offer's id), or null for a pairing made before there were any. */
    suspend fun peerPair(sql: SyncSql, device: String): String? = sql.read { if (!hasTables()) null else meta("pair:$device") }

    suspend fun setPeerPair(sql: SyncSql, device: String, pair: String?) = sql.write { setMeta("pair:$device", pair) }

    /** Log lines every device has read are not needed any more. */
    suspend fun prune(sql: SyncSql) = sql.write { pruneIn() }

    private suspend fun SyncTx.pruneIn() {
        var min = rows("SELECT MIN(sent) FROM sync_peers", types = "I").firstOrNull()?.get(0) as Long? ?: return
        // A pairing still waiting for the computer's yes needs every line after the one its copy was taken at.
        meta("live_p_from")?.toLongOrNull()?.let { min = minOf(min, it) }
        if (min <= 0L) return
        exec("DELETE FROM sync_log WHERE seq <= ?", listOf(min))
        val floor = meta("floor")?.toLongOrNull() ?: 0L
        if (min > floor) setMeta("floor", min.toString())
    }

    // --------------------------------------------------------------- parcels

    /**
     * One row's change: [op] "U" (here it is, as it now stands) or "D" (gone).
     * [seq] is the sender's last log line for the row in this parcel; null in
     * a first copy and from an older version.
     */
    data class Change(val table: String, val uid: String, val op: String, val row: JSONObject?, val seq: Long? = null)

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
                        if (ch.seq != null) put("s", ch.seq)
                    })
                }
            })
        }

        companion object {
            fun of(o: JSONObject): Parcel {
                val arr = o.getJSONArray("c")
                val changes = (0 until arr.length()).map { i ->
                    val c = arr.getJSONObject(i)
                    Change(c.getString("t"), c.getString("u"), c.getString("o"), c.optJSONObject("r"), if (c.has("s")) c.getLong("s") else null)
                }
                return Parcel(
                    book = o.getString("book"), device = o.getString("dev"), from = o.getLong("from"), to = o.getLong("to"),
                    changes = changes, more = o.optBoolean("more", false), schema = o.getInt("schema"), protocol = o.getInt("p")
                )
            }
        }
    }

    /**
     * Asked for lines this device has already let go (the asker's own book
     * went back to an older copy): sending what is left would be a silent
     * loss, so nothing is sent and the two devices must be joined again.
     */
    class Lost(val after: Long, val floor: Long) : Exception("asked for lines after $after; kept only after $floor")

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

    /** The columns of [t] that travel: all but the local id and file paths. */
    private suspend fun SyncTx.travelling(t: SyncSchema.Table, cache: MutableMap<String, List<Col>>): List<Col> =
        columns(t.name, cache).filter { it.name != "id" && it.name !in t.localOnly }

    /** The SELECT list of [cols] of [t] (aliased s), every pointer read as the uid of the row it names. */
    private fun selectList(t: SyncSchema.Table, cols: List<Col>): String = cols.joinToString(", ") { c ->
        val target = t.fks[c.name]
        if (target != null) "(SELECT x.uid FROM `$target` x WHERE x.id = s.`${c.name}`)" else "s.`${c.name}`"
    }

    private fun typesOf(t: SyncSchema.Table, cols: List<Col>): String =
        String(CharArray(cols.size) { i -> if (t.fks[cols[i].name] != null) 'T' else cols[i].type })

    private fun jsonOf(cols: List<Col>, r: Array<Any?>): JSONObject {
        val o = JSONObject()
        cols.forEachIndexed { i, c -> o.put(c.name, r[i] ?: JSONObject.NULL) }
        return o
    }

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
        val cols = travelling(t, cache)
        val r = rows(
            "SELECT ${selectList(t, cols)} FROM `${t.name}` s WHERE s.`${t.idColumn}` = ? LIMIT 1", listOf(uid), typesOf(t, cols)
        ).firstOrNull() ?: return null
        return jsonOf(cols, r)
    }

    /**
     * This device's changes after line [after] of its log, at most [PAGE]
     * lines: each row once, where it first changed, as it stands now (a row
     * changed five times travels once). A row gone since travels as gone.
     * Throws [Lost] when lines after [after] were already let go.
     */
    suspend fun export(sql: SyncSql, after: Long, page: Int = PAGE): Parcel = sql.read {
        val id = run {
            val b = meta("book") ?: error("live sync is off")
            val d = meta("device") ?: error("live sync is off")
            b to d
        }
        val floor = meta("floor")?.toLongOrNull() ?: 0L
        if (after < floor) throw Lost(after, floor)
        val lines = rows("SELECT seq, tbl, uid, op FROM sync_log WHERE seq > ? ORDER BY seq LIMIT ?", listOf(after, page.toLong()), "ITTT")
        if (lines.isEmpty()) return@read Parcel(id.first, id.second, after, after, emptyList(), false)
        val order = LinkedHashMap<String, Pair<String, String>>()
        val ops = HashMap<String, String>()
        val lastSeq = HashMap<String, Long>()
        for (l in lines) {
            val key = (l[1] as String) + "\u0000" + (l[2] as String)
            if (key !in order) order[key] = (l[1] as String) to (l[2] as String)
            ops[key] = l[3] as String
            lastSeq[key] = l[0] as Long
        }
        val cache = HashMap<String, List<Col>>()
        val changes = ArrayList<Change>()
        for ((key, tu) in order) {
            val t = SyncSchema.table(tu.first) ?: continue
            val seq = lastSeq[key]
            if (ops[key] == "D") {
                // Still here after all (removed, then made again with the same uid): it travels as it stands.
                val row = rowOut(t, tu.second, cache)
                changes.add(if (row == null) Change(t.name, tu.second, "D", null, seq) else Change(t.name, tu.second, "U", row, seq))
            } else {
                // Not here any more: a later "D" line says so, or the row's uid moved to the one
                // it met on another device, and its new uid is in the log already.
                val row = rowOut(t, tu.second, cache) ?: continue
                changes.add(Change(t.name, tu.second, "U", row, seq))
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
        val last = logCounter()
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

    /**
     * Writes [p] (from the device [p].device) into this book, all of it or
     * none of it. [recv] is how far this device had read that device's log;
     * [sentToThem] how far that device had read this one's (a row this device
     * changed after that line is a change the other has not seen: a conflict).
     * The pointers are the caller's; a round moves them with [applyFrom].
     */
    suspend fun apply(sql: SyncSql, p: Parcel, recv: Long, sentToThem: Long, now: Long = System.currentTimeMillis()): Applied {
        if (p.protocol != PROTOCOL) return Applied.Refused("protocol ${p.protocol}")
        if (p.schema != KHATA_DB_VERSION) return Applied.Refused("schema ${p.schema}")
        if (p.from > recv) return Applied.Gap(recv)
        if (p.to <= recv) return Applied.Done(0, 0, recv)
        val result = sql.write { applyIn(p, recv, sentToThem, now) }
        if (result is Applied.Done && result.rows > 0) changed.value = changed.value + 1
        return result
    }

    /**
     * [p] from a peer written in, with this device's pointers for that peer
     * read and moved in the same transaction (22 Oct 2026, the review: on the
     * computer the WiFi round and the relay ran at once, each read the same
     * pointer, and the slower one moved it back — so changes were written a
     * second time over newer edits). [theirAck] is how far that device has
     * read this one's log (null: not said, as before). Refused for a device
     * this book does not know.
     */
    suspend fun applyFrom(
        sql: SyncSql, p: Parcel, theirAck: Long?, now: Long = System.currentTimeMillis(),
        name: String? = null, host: String? = null, port: Int? = null
    ): Applied {
        if (p.protocol != PROTOCOL) return Applied.Refused("protocol ${p.protocol}")
        if (p.schema != KHATA_DB_VERSION) return Applied.Refused("schema ${p.schema}")
        val result = sql.write {
            val peer = rows("SELECT sent, recv FROM sync_peers WHERE device = ?", listOf(p.device), "II").firstOrNull()
                ?: return@write Applied.Refused("unknown device")
            if (meta("book") != p.book) return@write Applied.Refused("another book")
            val sent = peer[0] as Long
            val recv = peer[1] as Long
            val ack = theirAck ?: sent
            val r = when {
                p.from > recv -> Applied.Gap(recv)
                p.to <= recv -> Applied.Done(0, 0, recv)
                else -> applyIn(p, recv, ack, now)
            }
            val newRecv = if (r is Applied.Done) maxOf(recv, r.to) else recv
            val newSent = maxOf(sent, minOf(ack, logCounter()))
            exec("UPDATE sync_peers SET recv = ?, sent = ?, seen = ? WHERE device = ?", listOf(newRecv, newSent, now, p.device))
            if (name != null) exec("UPDATE sync_peers SET name = ? WHERE device = ?", listOf(name, p.device))
            if (host != null) exec("UPDATE sync_peers SET host = ? WHERE device = ?", listOf(host, p.device))
            if (port != null) exec("UPDATE sync_peers SET port = ? WHERE device = ?", listOf(port, p.device))
            pruneIn()
            r
        }
        if (result is Applied.Done && result.rows > 0) changed.value = changed.value + 1
        return result
    }

    private suspend fun SyncTx.applyIn(p: Parcel, recv: Long, sentToThem: Long, now: Long): Applied {
        if (meta("book") != p.book) return Applied.Refused("another book")
        val mine = meta("device").orEmpty()
        exec("INSERT INTO sync_mute (one) VALUES (1)")
        val cache = HashMap<String, List<Col>>()
        var written = 0
        var conflicts = 0
        // A change at or below what was read of the sender already landed (rule 6).
        var waiting: List<Change> = p.changes.filter { c -> c.seq == null || c.seq > recv }
        while (waiting.isNotEmpty()) {
            val next = ArrayList<Change>()
            for (c in waiting) {
                when (val r = tryOne(c, p.device, mine, sentToThem, now, cache)) {
                    One.Later -> next.add(c)
                    is One.Wrote -> { written++; if (r.conflict) conflicts++; unhold(c) }
                    One.Skipped -> unhold(c)
                    One.Unwritable -> hold(c, p.device, sentToThem, now)
                }
            }
            if (next.size == waiting.size) {
                // What they belong to is not here: kept aside and tried again later, the rest goes on.
                for (c in next) hold(c, p.device, sentToThem, now)
                break
            }
            waiting = next
        }
        resolvePending()
        written += retryHeld(mine, now, cache)
        exec("DELETE FROM sync_mute")
        return Applied.Done(written, conflicts, p.to)
    }

    private sealed interface One {
        data class Wrote(val conflict: Boolean) : One
        object Skipped : One
        /** A pointer that may not be empty names a row not here yet: try again after the rest. */
        object Later : One
        /** The book refused it (a key it would break): kept aside, never the end of the conversation. */
        object Unwritable : One
    }

    /**
     * One change in its own savepoint (22 Oct 2026, the review: one change
     * the book could not take — a unique name met, a pointer with nothing to
     * point at — rolled back the whole parcel, every round, for ever).
     */
    private suspend fun SyncTx.tryOne(
        c: Change, from: String, mine: String, sentToThem: Long, now: Long, cache: MutableMap<String, List<Col>>
    ): One {
        exec("SAVEPOINT rk_one")
        val r = try {
            applyOne(c, from, mine, sentToThem, now, cache)
        } catch (e: Exception) {
            if (!unwritable(e)) throw e
            One.Unwritable
        }
        // ";" first: Android takes a statement starting "ROLLBACK" for the end of its own transaction.
        if (r == One.Unwritable || r == One.Later) exec(";ROLLBACK TO rk_one")
        exec("RELEASE rk_one")
        return r
    }

    private fun SyncTx.unwritable(e: Exception): Boolean =
        isConstraint(e) || e is org.json.JSONException || e is NumberFormatException || e is ClassCastException

    private suspend fun SyncTx.hold(c: Change, device: String, ack: Long, now: Long) = exec(
        "INSERT OR REPLACE INTO sync_held (tbl, uid, op, row, device, ack, at) VALUES (?, ?, ?, ?, ?, ?, ?)",
        listOf(c.table, c.uid, c.op, c.row?.toString(), device, ack, now)
    )

    private suspend fun SyncTx.unhold(c: Change) = exec("DELETE FROM sync_held WHERE tbl = ? AND uid = ?", listOf(c.table, c.uid))

    /** Changes kept aside, tried again now that more of the other book is here; how many landed. */
    private suspend fun SyncTx.retryHeld(mine: String, now: Long, cache: MutableMap<String, List<Col>>): Int {
        var wrote = 0
        repeat(5) {
            val held = rows("SELECT tbl, uid, op, row, device, ack FROM sync_held ORDER BY at, tbl, uid", types = "TTTTTI")
            if (held.isEmpty()) return wrote
            var moved = 0
            for (h in held) {
                val row = (h[3] as String?)?.let { runCatching { JSONObject(it) }.getOrNull() }
                val c = Change(h[0] as String, h[1] as String, h[2] as String, row)
                when (tryOne(c, h[4] as String? ?: "", mine, h[5] as Long, now, cache)) {
                    is One.Wrote -> { unhold(c); moved++; wrote++ }
                    One.Skipped -> { unhold(c); moved++ }
                    else -> {}
                }
            }
            if (moved == 0) return wrote
        }
        return wrote
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

    /** [uid] of [table] was removed here after line [after] (the other device had not seen the removal). */
    private suspend fun SyncTx.removedHere(table: String, uid: String, after: Long): Boolean =
        rows(
            "SELECT 1 FROM sync_log WHERE tbl = ? AND uid = ? AND op = 'D' AND seq > ? LIMIT 1",
            listOf(table, localUid(table, uid), after), "I"
        ).isNotEmpty()

    /** [uid] of [table] was removed here, whenever. */
    private suspend fun SyncTx.removedEver(table: String, uid: String): Boolean =
        rows("SELECT 1 FROM sync_log WHERE tbl = ? AND uid = ? AND op = 'D' LIMIT 1", listOf(table, localUid(table, uid)), "I").isNotEmpty()

    private suspend fun SyncTx.keepAside(table: String, uid: String, now: Long, from: String, kept: String, lost: String?) = exec(
        "INSERT INTO sync_conflict (tbl, uid, at, device, kept, lost) VALUES (?, ?, ?, ?, ?, ?)",
        listOf(table, uid, now, from, kept, lost)
    )

    /** A row's version that is in the recycle bin. */
    private fun inBin(o: JSONObject?): Boolean {
        if (o == null || !o.has("isDeleted") || o.isNull("isDeleted")) return false
        return when (val v = o.opt("isDeleted")) {
            is Number -> v.toLong() != 0L
            is Boolean -> v
            else -> v.toString() == "1" || v.toString() == "true"
        }
    }

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
        // Removed here while the other device was changing it: the removal wins (rule 4), on both.
        if (localId == null && removedHere(n, c.uid, sentToThem)) {
            keepAside(n, c.uid, now, from, "mine", row.toString())
            return One.Skipped
        }
        val cols = columns(n, cache).filter { it.name != "id" && it.name != "uid" && it.name !in t.localOnly && row.has(it.name) }
        val values = ArrayList<Any?>(cols.size)
        val pending = ArrayList<Triple<String, String, String>>()
        for (col in cols) {
            val raw = jsonValue(row, col.name)
            val target = t.fks[col.name]
            if (target != null && raw != null) {
                val id = idOf(target, raw.toString())
                if (id == null) {
                    if (col.notNull) {
                        // What it belongs to was removed here: it goes with it (rule 4), and the owner can see it.
                        if (removedEver(target, raw.toString())) {
                            keepAside(n, c.uid, now, from, "mine", row.toString())
                            return One.Skipped
                        }
                        return One.Later
                    }
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
            val ownRow = rowOut(t, localUid, cache)
            val theirsInBin = inBin(row)
            val mineInBin = inBin(ownRow)
            val kept = when {
                // Put in the bin on one side while changed on the other: the bin wins on both (rule 4).
                theirsInBin != mineInBin -> if (mineInBin) "mine" else "theirs"
                mine > from -> "mine"
                else -> "theirs"
            }
            keepAside(n, localUid, now, from, kept, (if (kept == "mine") row else ownRow)?.toString())
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
            keepAside(n, c.uid, now, from, kept, if (kept == "mine") value else here[0] as String?)
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
     *
     * Read a table a thousand rows at a time, its pointers turned into uids by
     * SQLite itself (22 Oct 2026, the review: one query per row and one per
     * pointer held the phone's book for seconds every half hour).
     */
    suspend fun fingerprint(sql: SyncSql): String = sql.read {
        val d = MessageDigest.getInstance("SHA-256")
        val cache = HashMap<String, List<Col>>()
        for (t in SyncSchema.tables) {
            d.update(t.name.toByteArray(Charsets.UTF_8))
            val cols = travelling(t, cache)
            val idCol = t.idColumn
            val at = cols.indexOfFirst { it.name == idCol }
            if (at < 0) continue
            val select = selectList(t, cols)
            val types = typesOf(t, cols)
            var last: String? = null
            while (true) {
                val after = last
                val page = rows(
                    "SELECT $select FROM `${t.name}` s WHERE s.`$idCol` IS NOT NULL" +
                        (if (after != null) " AND s.`$idCol` > ?" else "") + " ORDER BY s.`$idCol` LIMIT $FINGER_PAGE",
                    if (after != null) listOf(after) else emptyList(), types
                )
                for (r in page) {
                    val u = r[at] as String
                    d.update(u.toByteArray(Charsets.UTF_8))
                    d.update(canonical(cols, r).toByteArray(Charsets.UTF_8))
                }
                if (page.size < FINGER_PAGE) break
                last = page.last()[at] as String
            }
        }
        d.digest().joinToString("") { "%02x".format(it) }
    }

    private const val FINGER_PAGE = 1000

    /**
     * A row's text with its columns in name order and numbers in one shape, so
     * both devices write it alike. A fraction is written exactly as the double
     * holds it (BigDecimal): the phone's and the computer's Java may print the
     * same double differently.
     */
    private fun canonical(cols: List<Col>, r: Array<Any?>): String = buildString {
        for (i in cols.indices.sortedBy { cols[it].name }) {
            append(cols[i].name).append('=')
            append(
                when (val v = r[i]) {
                    null -> "~"
                    is Long -> v.toString()
                    is Double -> if (v == Math.rint(v) && kotlin.math.abs(v) < 1e15) v.toLong().toString()
                        else if (v.isNaN() || v.isInfinite()) v.toString() else BigDecimal(v).toPlainString()
                    is Number -> v.toString()
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
            val fields = if (lost != null) {
                // keys(), not keySet(): Android's JSONObject has no keySet (22 Oct 2026, the phone's build).
                // A row no longer here (removed) shows what the other version had, field by field.
                val keys = lost.keys().asSequence().toSet() + (now?.keys()?.asSequence()?.toSet() ?: emptySet())
                keys.filter { it != "uid" }.sorted().mapNotNull { k: String ->
                    val a = text(now, k); val b = text(lost, k)
                    if (a == b || (a?.toDoubleOrNull() != null && a.toDoubleOrNull() == b?.toDoubleOrNull())) null else Triple(k, a, b)
                }
            } else listOf(Triple(if (lostText != null) "value" else "row", text(now, "name") ?: text(now, "value"), lostText?.take(200)))
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
