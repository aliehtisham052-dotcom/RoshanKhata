package com.innovation313.roshankhata.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Waris ki Chaabi (heir access, 4 Nov 2026): a shop owner names up to five heirs; an heir can only ask; the owner
 * is told and can refuse; if he does not refuse within the wait he chose (7 to 90 days, 30 by default), the relays
 * hand that heir a key box only the heir's phone can open, and the heir gets a read-only copy of the book.
 *
 * Shared by the phone and (later) the computer: the book's settings keys ([Store], book_settings on both), the
 * relays' client over every road ([Relays], LiveCloud's roads and Http, the kill switch honoured), the owner's
 * steps ([Owner]) and the heir's ([Heir]). The crypto is [WarisCrypto]. Protocol v1 (WARIS protocol, the relays
 * cloud/worker, supabase/functions/daakiya and web/relay, held to it by scripts/test_relay.py's waris checks).
 *
 * What the owner writes goes to every relay, best effort; a relay that missed one is remembered ([K_DIRTY]) and the
 * next run does everything idempotent again for it ([Owner.retry]). A refusal is NOT left to that: every status run
 * sends it again to any relay still showing the refused request ([merge]'s rejectAgain) — a relay that missed it
 * would otherwise open the book early.
 *
 * The security review (7 Nov 2026): the owner's protective calls — the status, a refusal, an allow, turning off,
 * a removed heir's delete — never wait for live sync's kill switch, since the relays' wait clocks run on while it is
 * on; an heir a relay holds that this book does not name is deleted there, and an allow this phone never gave is
 * refused again ([merge]); W, the key every copy is sealed with, is in no backup any more, and a W that ever was (or
 * none, after a restore) is replaced before anything is sealed ([Owner.state]); removed heirs and vaults are
 * remembered for good ([K_REMOVED]), so a copy restored later cannot bring one back ([restored]).
 */
object Waris {

    // ------------------------------------------------------------------ the book's settings keys

    const val PREFIX = "waris_"
    // The owner's side.
    const val K_VAULT = "waris_vault"
    const val K_OWNER_SECRET = "waris_owner_secret"
    /** W, the key every copy is sealed with. Never written into any copy of the book, not even the owner's own backup. */
    const val K_KEY = "waris_key"
    const val K_KEY_VERSION = "waris_key_version"
    /** "1" once W was made by a build that never puts it into a backup (7 Nov 2026); without it W is replaced first. */
    const val K_KEY_SAFE = "waris_key_safe"
    const val K_HEIRS = "waris_heirs"
    const val K_SNAP_V = "waris_snap_v"
    const val K_SNAP_AT = "waris_snap_at"
    const val K_SNAP_BYTES = "waris_snap_bytes"
    const val K_SNAP_SIG = "waris_snap_sig"
    /** The key version the last copy was sealed with: a W moved since needs a new copy before its boxes go. */
    const val K_SNAP_KV = "waris_snap_kv"
    const val K_STATUS_AT = "waris_status_at"
    /** The merged status as last read (the screens and Home's question read it without a network call). */
    const val K_STATUS = "waris_status"
    /** Relays (by road tag) whose last owner write did not land, the snapshot version each holds, hids still to delete. */
    const val K_DIRTY = "waris_dirty"
    const val K_RELAY_V = "waris_relay_v"
    const val K_GONE = "waris_gone"
    /** A vault turned off while some relay could not be told: its id, its owner secret and the relays still holding it. */
    const val K_OFF = "waris_off"
    /**
     * Every heir id (16 hex) and vault id (32 hex) ever removed from this book, never cleared: such an heir is never
     * put on a relay again and such a vault never set up again, whatever a copy restored later says (W5).
     */
    const val K_REMOVED = "waris_removed"
    /** Relays (by road tag) a refusal has not reached yet: asked again in the background until each has it (W6). */
    const val K_REFUSE_LEFT = "waris_refuse_left"
    /** Relays (by road tag) that answer this vault's owner pass with a refusal: someone else holds the vault id there. */
    const val K_RELAY_NO = "waris_relay_no"
    // The heir's side.
    const val K_LINKS = "waris_links"

    /** Every key of the feature: none of them leaves the owner's hands (helper's copy, the Waris snapshot itself). */
    val SECRET_KEYS = setOf(
        K_VAULT, K_OWNER_SECRET, K_KEY, K_KEY_VERSION, K_KEY_SAFE, K_HEIRS, K_SNAP_V, K_SNAP_AT, K_SNAP_BYTES, K_SNAP_SIG,
        K_SNAP_KV, K_STATUS_AT, K_STATUS, K_DIRTY, K_RELAY_V, K_GONE, K_OFF, K_REMOVED, K_REFUSE_LEFT, K_RELAY_NO, K_LINKS
    )

    /** At most this many removed ids are remembered (the oldest go first); each is a few bytes. */
    const val MAX_REMOVED = 1000

    /** Whether a book setting is one of Waris ki Chaabi's (all of them are secret; a future key is too, by its prefix). */
    fun isSecret(key: String): Boolean = key in SECRET_KEYS || key.startsWith(PREFIX)

    val WAITS = listOf(7, 15, 30, 60, 90)
    const val DEFAULT_WAIT = 30
    const val MAX_HEIRS = 5
    const val DAY_MS = 86_400_000L
    const val STATUS_EVERY_MS = 6 * 3_600_000L
    const val ALIVE_EVERY_MS = DAY_MS
    const val SNAP_EVERY_MS = 7 * DAY_MS
    /** How far a relay's clock may be from the phone's for a refusal to still cover a request (status latency). */
    const val SLACK_MS = 2 * 60_000L
    /** The book the heir opens, unpacked, at most (a sealed copy is 8 MiB of gzip). */
    const val MAX_BOOK_BYTES = 512L * 1024 * 1024

    /** Where the settings live: the book's book_settings on both platforms. */
    interface Store {
        suspend fun get(key: String): String?
        suspend fun put(key: String, value: String?)
    }

    /** The book itself as backup JSON with secrets = false (the helper copy's path), written to [out]. */
    fun interface Book {
        suspend fun write(out: OutputStream)
    }

    // ------------------------------------------------------------------ outcomes

    /** [BUSY]: a relay took all it takes today (429, e.g. "waris-day"): tomorrow, not later today. */
    enum class Why { OFFLINE, HALTED, FULL, BUSY, TOO_BIG, BAD_CODE, TOO_MANY, NOT_ON, PASS, WAIT, WAITING, NO_COPY, BROKEN }

    sealed class Result {
        /** Done on [reached] of [total] relays. */
        data class Done(val reached: Int, val total: Int) : Result() { val partly: Boolean get() = reached < total }
        data class Failed(val why: Why, val at: Long? = null) : Result()
    }

    // ------------------------------------------------------------------ the relays

    sealed class Reply {
        class Ok(val json: JSONObject?, val bytes: ByteArray) : Reply()
        class No(val code: Int, val why: String, val json: JSONObject?) : Reply()
        object Unreachable : Reply()
    }

    /**
     * The relays, in LiveCloud's road order. While live sync is halted for everyone ([LiveControl], [halted]) no new
     * vault is made, no copy goes up and the heir's calls wait; the owner's protective calls never do (W1).
     */
    class Relays(
        val http: LiveCloud.Http = LiveCloud.plain,
        val roads: List<LiveCloud.Road> = LiveCloud.roads(),
        val halted: () -> Boolean = { LiveControl.halted() }
    ) {
        fun call(road: LiveCloud.Road, method: String, vault: String, path: String, pass: String, body: ByteArray? = null, type: String? = null): Reply {
            val url = road.base.trimEnd('/') + "/v1/w/" + vault + path
            val headers = HashMap<String, String>()
            headers["authorization"] = "RK $pass"
            // The same pass again: some hostings drop Authorization before PHP sees it (web/relay).
            headers["x-rk-pass"] = pass
            if (type != null) headers["content-type"] = type
            return try {
                val (code, bytes) = http.call(method, url, headers, body)
                val json = runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }.getOrNull()
                if (code in 200..299) Reply.Ok(json, bytes)
                // An old relay without the Waris routes (404 no-such-path, 405 method), a halted one (503), a server
                // error: that relay's trouble, said as unreachable so the next run tries it again.
                else if (code >= 500 && code != 507 || json?.optString("why") == "no-such-path" || code == 405) Reply.Unreachable
                else Reply.No(code, json?.optString("why").orEmpty(), json)
            } catch (e: java.io.IOException) {
                Reply.Unreachable
            } catch (e: RuntimeException) {
                Reply.Unreachable
            }
        }
    }

    // ------------------------------------------------------------------ the owner's state

    class HeirEntry(
        val hid: String,
        val name: String,
        /** The heir's public key (base64url SPKI); null until their code was scanned. */
        val pub: String?,
        val passHash: String?,
        /** The heir's secret, kept only while their code is awaited (base64url); gone once linked. */
        val secret: String?,
        val wait: Int,
        val addedAt: Long,
        val refusedAt: Long? = null,
        val allowedAt: Long? = null
    ) {
        val linked: Boolean get() = pub != null && passHash != null

        fun toJson(): JSONObject = JSONObject().apply {
            put("hid", hid); put("name", name); put("wait", wait); put("addedAt", addedAt)
            pub?.let { put("pub", it) }; passHash?.let { put("passHash", it) }; secret?.let { put("secret", it) }
            refusedAt?.let { put("refusedAt", it) }; allowedAt?.let { put("allowedAt", it) }
        }

        fun copy(refusedAt: Long? = this.refusedAt, allowedAt: Long? = this.allowedAt, pub: String? = this.pub,
                 passHash: String? = this.passHash, secret: String? = this.secret) =
            HeirEntry(hid, name, pub, passHash, secret, wait, addedAt, refusedAt, allowedAt)

        companion object {
            fun of(o: JSONObject): HeirEntry? {
                val hid = o.optString("hid").takeIf { WarisCrypto.isHex(it, 16) } ?: return null
                return HeirEntry(
                    hid, o.optString("name"), o.optString("pub").ifEmpty { null }, o.optString("passHash").ifEmpty { null },
                    o.optString("secret").ifEmpty { null }, o.optInt("wait", DEFAULT_WAIT).coerceIn(1, 90),
                    o.optLong("addedAt", 0L), longOrNull(o, "refusedAt"), longOrNull(o, "allowedAt")
                )
            }
        }
    }

    class OwnerState(
        val vault: String,
        val ownerSecret: ByteArray,
        val w: ByteArray,
        val keyVersion: Int,
        val heirs: List<HeirEntry>,
        val snapV: Int,
        val snapAt: Long,
        val snapBytes: Long,
        val snapSig: String?,
        /** The key version the last copy was sealed with; null before 7 Nov 2026 (unknown: a new copy is due). */
        val snapKv: Int? = null
    ) {
        val pass: String get() = WarisCrypto.ownerPass(ownerSecret, vault)
    }

    /** One heir as the relays together see them ([merge]); times in this phone's clock. */
    class HeirView(
        val hid: String,
        val asked: Boolean,
        val askedAt: Long?,
        val openAt: Long?,
        val allowed: Boolean,
        val lastSeen: Long?,
        val onRelays: Int
    ) {
        /** Asked, and the owner has neither refused nor allowed: Home asks him, a notification tells him. */
        val pending: Boolean get() = asked && !allowed

        fun toJson(): JSONObject = JSONObject().apply {
            put("hid", hid); put("asked", asked); put("allowed", allowed); put("onRelays", onRelays)
            askedAt?.let { put("askedAt", it) }; openAt?.let { put("openAt", it) }; lastSeen?.let { put("lastSeen", it) }
        }

        companion object {
            fun of(o: JSONObject) = HeirView(
                o.optString("hid"), o.optBoolean("asked"), longOrNull(o, "askedAt"), longOrNull(o, "openAt"),
                o.optBoolean("allowed"), longOrNull(o, "lastSeen"), o.optInt("onRelays", 0)
            )
        }
    }

    /** One relay's status answer, as it said it (its own clock). */
    class RelayStatus(val tag: String, val now: Long, val snapV: Int?, val heirs: List<RelayHeir>)

    class RelayHeir(
        val hid: String, val lastSeen: Long?, val askedAt: Long?, val rejectedAt: Long?, val allowedAt: Long?, val openAt: Long?
    )

    class Merged(
        val views: Map<String, HeirView>,
        val rejectAgain: List<Pair<String, String>>,
        val allowAgain: List<Pair<String, String>>,
        /** (relay tag, hid) of heirs a relay holds that this book does not name: deleted there, never shown (W2). */
        val unknown: List<Pair<String, String>> = emptyList()
    )

    /**
     * The relays' statuses as one (pure, MergeTest): an heir has asked when ANY relay says so; the earliest openAt
     * wins; the latest lastSeen. A relay's times are moved into this phone's clock by the difference of its `now`.
     *
     * The owner's own last word decides: a request made before he refused (with [SLACK_MS] for the clocks) is not a
     * request any more, and a relay still showing it is told again ([Merged.rejectAgain]); an allow this phone never
     * gave — given before a refusal, or by someone else holding the owner's pass (a stolen backup, W3) — is refused
     * again there and never counts; after the owner's own allow, a relay without it is told again
     * ([Merged.allowAgain]). An heir a relay holds that is none of [heirs] (linked or still awaiting their code) is
     * [Merged.unknown]: removed since, or put there by someone else (W2).
     */
    fun merge(heirs: List<HeirEntry>, statuses: List<RelayStatus>, localNow: Long): Merged {
        val views = LinkedHashMap<String, HeirView>()
        val rejectAgain = ArrayList<Pair<String, String>>()
        val allowAgain = ArrayList<Pair<String, String>>()
        val mine = heirs.map { it.hid }.toSet()
        val unknown = statuses.flatMap { s -> s.heirs.filter { it.hid !in mine }.map { s.tag to it.hid } }.distinct()
        for (h in heirs.filter { it.linked }) {
            val refused = h.refusedAt
            val allowedHere = h.allowedAt?.takeIf { refused == null || it > refused }
            var askedAt: Long? = null
            var openAt: Long? = null
            var lastSeen: Long? = null
            var allowed = allowedHere != null
            var on = 0
            for (s in statuses) {
                val r = s.heirs.firstOrNull { it.hid == h.hid } ?: continue
                on++
                val shift = s.now - localNow
                fun local(t: Long?) = t?.let { it - shift }
                local(r.lastSeen)?.let { lastSeen = maxOf(lastSeen ?: it, it) }
                val rAsked = local(r.askedAt)
                val rAllowed = local(r.allowedAt)
                val stale = rAsked != null && refused != null && allowedHere == null && rAsked <= refused + SLACK_MS
                if (allowedHere != null) {
                    if (r.allowedAt == null) allowAgain.add(s.tag to h.hid)
                } else if (stale || rAllowed != null) {
                    // A request from before the refusal, or an allow this phone never gave: refused there again.
                    rejectAgain.add(s.tag to h.hid)
                    continue
                }
                if (rAsked != null) askedAt = minOf(askedAt ?: rAsked, rAsked)
                if (rAllowed != null) allowed = true
                local(r.openAt)?.let { openAt = minOf(openAt ?: it, it) }
            }
            if (allowedHere != null && openAt == null) openAt = allowedHere
            views[h.hid] = HeirView(h.hid, askedAt != null || allowed, askedAt ?: allowedHere, openAt, allowed, lastSeen, on)
        }
        return Merged(views, rejectAgain, allowAgain, unknown)
    }

    // ------------------------------------------------------------------ the owner's steps

    class Owner(private val store: Store, private val relays: Relays = Relays()) {

        /**
         * The vault this book keeps, or null. A vault once turned off here is never one again (W5). W is made new
         * first when it ever could have gone into a backup — every W made before 7 Nov 2026 — or is missing, as after
         * a restore (no copy carries W any more): see [key].
         */
        suspend fun state(): OwnerState? {
            val vault = store.get(K_VAULT)?.takeIf { WarisCrypto.isHex(it, 32) } ?: return null
            if (vault in removed()) return null
            val secret = store.get(K_OWNER_SECRET)?.let { B64.decodeOrNull(it) }?.takeIf { it.size == 32 } ?: return null
            val (w, version) = key()
            return OwnerState(
                vault, secret, w, version, heirs(),
                store.get(K_SNAP_V)?.toIntOrNull() ?: 0, store.get(K_SNAP_AT)?.toLongOrNull() ?: 0L,
                store.get(K_SNAP_BYTES)?.toLongOrNull() ?: 0L, store.get(K_SNAP_SIG), store.get(K_SNAP_KV)?.toIntOrNull()
            )
        }

        /**
         * W and its version. A W not marked [K_KEY_SAFE] (one an older build kept in every full backup) or none at all
         * (a book restored from a copy) is replaced: a new W, the next version, every relay marked, so the next run
         * seals a new copy and every heir's box again ([push]) — a W in a backup opens nothing the relays hold from
         * then on (the security review, W3). Once per book: the new W is marked.
         */
        private suspend fun key(): Pair<ByteArray, Int> = keyLock.withLock {
            val w = store.get(K_KEY)?.let { B64.decodeOrNull(it) }?.takeIf { it.size == 32 }
            val version = store.get(K_KEY_VERSION)?.toIntOrNull() ?: 1
            if (w != null && store.get(K_KEY_SAFE) == "1") w to version else newKey(version + 1) to (version + 1)
        }

        /** A new W at [version], made here and never written into any copy of the book; every relay gets it again. */
        private suspend fun newKey(version: Int): ByteArray {
            val w = WarisCrypto.newSecret()
            store.put(K_KEY, B64.encode(w))
            store.put(K_KEY_VERSION, version.toString())
            store.put(K_KEY_SAFE, "1")
            setDirty(relays.roads.map { it.tag }.toSet())
            return w
        }

        /** The heirs this book names; one removed is never among them, whatever a restored copy's list said (W5). */
        suspend fun heirs(): List<HeirEntry> {
            val removed = removed()
            return jsonArray(store.get(K_HEIRS)).mapNotNull { HeirEntry.of(it) }.filter { it.hid !in removed }
        }

        /** Every heir and vault ever removed from this book ([K_REMOVED]). */
        suspend fun removed(): Set<String> = idList(store.get(K_REMOVED)).toSet()

        private suspend fun remember(ids: Collection<String>) {
            val all = LinkedHashSet(idList(store.get(K_REMOVED)))
            ids.filter { isId(it) }.forEach { all.remove(it); all.add(it) }
            store.put(K_REMOVED, JSONArray(all.toList().takeLast(MAX_REMOVED)).toString())
        }

        /** Relays a refusal has not reached yet ([K_REFUSE_LEFT]): Home says so, and the background asks them again. */
        suspend fun refuseLeft(): Set<String> = stringSet(store.get(K_REFUSE_LEFT))

        /** Relays that refuse this vault's owner pass ([K_RELAY_NO]): the screen says someone else holds it there. */
        suspend fun relaysRefusing(): Set<String> = stringSet(store.get(K_RELAY_NO))

        private suspend fun saveHeirs(list: List<HeirEntry>) =
            store.put(K_HEIRS, JSONArray().apply { list.forEach { put(it.toJson()) } }.toString())

        /** The merged status as last read; empty before the first. */
        suspend fun views(): Map<String, HeirView> {
            val o = store.get(K_STATUS)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return emptyMap()
            val out = LinkedHashMap<String, HeirView>()
            o.keys().forEach { k -> o.optJSONObject(k)?.let { out[k] = HeirView.of(it) } }
            return out
        }

        private suspend fun saveViews(views: Map<String, HeirView>) =
            store.put(K_STATUS, JSONObject().apply { views.forEach { (k, v) -> put(k, v.toJson()) } }.toString())

        suspend fun statusAt(): Long = store.get(K_STATUS_AT)?.toLongOrNull() ?: 0L

        private suspend fun dirty(): MutableSet<String> = stringSet(store.get(K_DIRTY))
        private suspend fun setDirty(s: Set<String>) = store.put(K_DIRTY, if (s.isEmpty()) null else JSONArray(s.toList()).toString())
        private suspend fun relayV(): MutableMap<String, Int> {
            val o = store.get(K_RELAY_V)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return HashMap()
            val m = HashMap<String, Int>(); o.keys().forEach { m[it] = o.optInt(it) }; return m
        }
        private suspend fun setRelayV(m: Map<String, Int>) = store.put(K_RELAY_V, JSONObject(m as Map<*, *>).toString())

        /**
         * Turns Waris on for this book: a new vault, owner secret and key W (keyVersion 1), set up on every relay.
         * Nothing is kept when no relay could be reached (no half-made vault on the phone alone).
         */
        suspend fun enable(): Result {
            if (relays.roads.isEmpty() || relays.halted()) return Result.Failed(Why.HALTED)
            state()?.let { return Result.Done(relays.roads.size, relays.roads.size) }
            // A vault turned off earlier that some relay still holds is told first.
            retryOff()
            val vault = WarisCrypto.newVault()
            val secret = WarisCrypto.newSecret()
            val pass = WarisCrypto.ownerPass(secret, vault)
            var ok = 0
            var full = false
            var busy = false
            val missed = HashSet<String>()
            for (road in relays.roads) {
                when (val r = relays.call(road, "POST", vault, "/setup", pass)) {
                    is Reply.Ok -> ok++
                    is Reply.No -> {
                        when (stepOf(r)) { Step.FULL -> full = true; Step.BUSY -> busy = true; else -> Unit }
                        missed.add(road.tag)
                    }
                    Reply.Unreachable -> missed.add(road.tag)
                }
            }
            if (ok == 0) return Result.Failed(why(full, busy))
            store.put(K_VAULT, vault)
            store.put(K_OWNER_SECRET, B64.encode(secret))
            newKey(1)
            store.put(K_SNAP_V, "0")
            setDirty(missed)
            return Result.Done(ok, relays.roads.size)
        }

        /** A new heir waits for their phone's code: their hid and secret are made, QR1 is the answer. */
        suspend fun startHeir(name: String, wait: Int, shop: String, now: Long = System.currentTimeMillis()): Pair<HeirEntry, String>? {
            val st = state() ?: return null
            if (st.heirs.size >= MAX_HEIRS) return null
            val secret = WarisCrypto.newSecret()
            val h = HeirEntry(WarisCrypto.newHid(), name.trim().take(60), null, null, WarisCrypto.b64url(secret), wait.coerceIn(1, 90), now)
            saveHeirs(st.heirs + h)
            return h to WarisCrypto.ownerCode(st.vault, h.hid, secret, h.wait, shop)
        }

        /** QR1 again for an heir whose code was not scanned yet. */
        suspend fun ownerCodeOf(hid: String, shop: String): String? {
            val st = state() ?: return null
            val h = st.heirs.firstOrNull { it.hid == hid && !it.linked } ?: return null
            val secret = h.secret?.let { WarisCrypto.unb64url(it) } ?: return null
            return WarisCrypto.ownerCode(st.vault, h.hid, secret, h.wait, shop)
        }

        /**
         * The heir's code (QR2) scanned: accepted only when its check c was made with the secret QR1 gave that heir.
         * Then the heir is put on every relay with their box, and a snapshot goes up.
         */
        suspend fun finishHeir(hid: String, scanned: String?, book: Book?, now: Long = System.currentTimeMillis()): Result {
            val st = state() ?: return Result.Failed(Why.NOT_ON)
            val h = st.heirs.firstOrNull { it.hid == hid } ?: return Result.Failed(Why.BAD_CODE)
            val code = WarisCrypto.parseHeirCode(scanned) ?: return Result.Failed(Why.BAD_CODE)
            val secret = h.secret?.let { WarisCrypto.unb64url(it) }
            if (h.linked && secret == null) {
                // Already linked: the same code again changes nothing.
                return if (h.pub == code.k) push(book, now) else Result.Failed(Why.BAD_CODE)
            }
            if (secret == null || !WarisCrypto.heirCodeMatches(code, secret, st.vault, hid)) return Result.Failed(Why.BAD_CODE)
            val linked = h.copy(pub = code.k, passHash = WarisCrypto.passHash(WarisCrypto.heirPass(secret, st.vault, hid)), secret = null)
            saveHeirs(st.heirs.map { if (it.hid == hid) linked else it })
            setDirty(relays.roads.map { it.tag }.toSet())
            return push(book, now, newSnapshot = true)
        }

        /**
         * An heir taken off: remembered for good (W5), deleted everywhere before anything else is sent (W2), a new W
         * (keyVersion + 1), new boxes for the others, a new snapshot.
         */
        suspend fun removeHeir(hid: String, book: Book?, now: Long = System.currentTimeMillis()): Result {
            val st = state() ?: return Result.Failed(Why.NOT_ON)
            val h = st.heirs.firstOrNull { it.hid == hid } ?: return Result.Done(0, 0)
            saveHeirs(st.heirs.filter { it.hid != hid })
            remember(listOf(hid))
            if (!h.linked) return Result.Done(relays.roads.size, relays.roads.size)
            // Saved before anything is sent: a run killed half way finds the new key and sends again.
            newKey(st.keyVersion + 1)
            store.put(K_GONE, JSONArray((stringSet(store.get(K_GONE)) + hid).toList()).toString())
            val views = views().toMutableMap(); views.remove(hid); saveViews(views)
            return push(book, now, newSnapshot = true)
        }

        /** A new copy of the book to every relay, now ([force]) or when it changed since the last. */
        suspend fun snapshot(book: Book, force: Boolean = true, now: Long = System.currentTimeMillis()): Result =
            push(book, now, newSnapshot = true, onlyIfChanged = !force)

        /** What the last runs left undone, done again: every relay marked dirty gets everything idempotent. */
        suspend fun retry(book: Book?, now: Long = System.currentTimeMillis()): Result {
            retryOff()
            if (state() == null) return Result.Failed(Why.NOT_ON)
            return push(book, now)
        }

        /**
         * Everything the relays should hold, to the relays marked dirty (the deletes of removed heirs first, then
         * setup and every linked heir's box) and, when [newSnapshot], when W moved since the last copy, or when some
         * relay lacks the current version, a snapshot to every relay.
         */
        private suspend fun push(book: Book?, now: Long, newSnapshot: Boolean = false, onlyIfChanged: Boolean = false): Result {
            val st = state() ?: return Result.Failed(Why.NOT_ON)
            if (relays.roads.isEmpty()) return Result.Failed(Why.HALTED)
            val dirty = dirty()
            val gone = stringSet(store.get(K_GONE))
            // A removed heir comes off every relay still marked first (W2): before any heir is put — a relay holding
            // five answers a new one "full" until the removed one is gone — and even while live sync is halted for
            // everyone (W1): taking a way in away is never held back.
            val cleared = relays.roads.filter { it.tag in dirty && deleteGone(it, st, gone) }.map { it.tag }.toSet()
            if (relays.halted()) return Result.Failed(Why.HALTED)
            val have = relayV()
            val linked = st.heirs.any { it.linked }
            // W moved since the copy the relays hold was sealed (an heir removed, a restored book, the review's
            // turn of every older W): a new copy is due, and the boxes of the new W wait for it.
            val keyMoved = linked && st.snapKv != st.keyVersion
            var packed: Packed? = null
            val needSnap = book != null && linked &&
                (newSnapshot || keyMoved || st.snapV == 0 || relays.roads.any { have[it.tag] != st.snapV })
            if (needSnap) {
                val v = maxOf(st.snapV, have.values.maxOrNull() ?: 0) + 1
                packed = try { pack(book!!, st, v) } catch (e: TooBig) { return Result.Failed(Why.TOO_BIG) }
                if (onlyIfChanged && !keyMoved && packed.sig == st.snapSig && now - st.snapAt < SNAP_EVERY_MS &&
                    relays.roads.all { have[it.tag] == st.snapV }) packed = null
            }
            // A box of a W no copy is sealed with yet, beside the copy of the old W, would open nothing for the heir.
            val boxesWait = keyMoved && packed == null
            var reached = 0
            var full = false
            var busy = false
            for (road in relays.roads) {
                var ok = road.tag !in dirty || road.tag in cleared
                // A new copy goes with every heir's box again (they are small): a box and the copy can then never be
                // of two keys, whatever a restore or a run killed half way left behind.
                if (ok && (road.tag in dirty || packed != null)) {
                    ok = !boxesWait && when (setupAndHeirs(road, st)) {
                        Step.OK -> true
                        Step.FULL -> { full = true; false }
                        Step.BUSY -> { busy = true; false }
                        Step.FAIL -> false
                    }
                }
                if (ok && packed != null) {
                    when (upload(road, st, packed)) {
                        Step.OK -> have[road.tag] = packed.v
                        Step.FULL -> { full = true; ok = false }
                        Step.BUSY -> { busy = true; ok = false }
                        Step.FAIL -> ok = false
                    }
                }
                if (ok) { dirty.remove(road.tag); reached++ } else dirty.add(road.tag)
            }
            setDirty(dirty)
            setRelayV(have)
            if (dirty.isEmpty()) store.put(K_GONE, null)
            if (packed != null && reached > 0) {
                store.put(K_SNAP_V, packed.v.toString())
                store.put(K_SNAP_AT, now.toString())
                store.put(K_SNAP_BYTES, packed.sealed.size.toString())
                store.put(K_SNAP_SIG, packed.sig)
                store.put(K_SNAP_KV, st.keyVersion.toString())
            }
            return if (reached == 0) Result.Failed(why(full, busy)) else Result.Done(reached, relays.roads.size)
        }

        /** Every hid in [gone] deleted on one relay (an heir already gone there is gone): true when all went. */
        private fun deleteGone(road: LiveCloud.Road, st: OwnerState, gone: Set<String>): Boolean =
            gone.all { hid -> landed(relays.call(road, "DELETE", st.vault, "/heir/$hid", st.pass)) }

        /** The vault set up and every linked heir's box put, on one relay (the removed ones are gone by then). */
        private fun setupAndHeirs(road: LiveCloud.Road, st: OwnerState): Step {
            val pass = st.pass
            when (val r = relays.call(road, "POST", st.vault, "/setup", pass)) {
                is Reply.Ok -> Unit
                is Reply.No -> return stepOf(r)
                Reply.Unreachable -> return Step.FAIL
            }
            for (h in st.heirs.filter { it.linked }) {
                val pub = WarisCrypto.unb64url(h.pub!!) ?: continue
                val box = WarisCrypto.sealBox(pub, st.w, st.keyVersion, st.vault, h.hid)
                val body = JSONObject().put("passHash", h.passHash).put("box", B64.encode(box)).put("wait", h.wait)
                    .toString().toByteArray(Charsets.UTF_8)
                when (val r = relays.call(road, "PUT", st.vault, "/heir/${h.hid}", pass, body, "application/json")) {
                    is Reply.Ok -> Unit
                    is Reply.No -> return stepOf(r)
                    Reply.Unreachable -> return Step.FAIL
                }
            }
            return Step.OK
        }

        private fun upload(road: LiveCloud.Road, st: OwnerState, p: Packed): Step {
            val n = p.chunks.size
            for ((i, c) in p.chunks.withIndex()) {
                when (val r = relays.call(road, "PUT", st.vault, "/snap?v=${p.v}&i=$i&n=$n", st.pass, c, "application/octet-stream")) {
                    is Reply.Ok -> if (i == n - 1 && r.json?.optBoolean("complete", false) != true) return Step.FAIL
                    is Reply.No -> return stepOf(r)
                    Reply.Unreachable -> return Step.FAIL
                }
            }
            return Step.OK
        }

        /**
         * Every relay's status, merged ([merge]); a refusal a relay missed is sent to it again, an allow likewise;
         * a relay that no longer knows the vault or a linked heir is marked to be set up again. Cached for the screens.
         *
         * Never held back by live sync's kill switch (W1): the relays' wait clocks run on while it is on, and the owner
         * must still see a request and refuse it. An heir a relay holds that this book does not name is deleted there
         * and remembered for good (W2); a relay that refuses this vault's pass is noted for the screen (W9); a refusal
         * still owed to a relay is settled once that relay answered and every call sent to it landed (W6).
         */
        suspend fun status(now: Long = System.currentTimeMillis()): Map<String, HeirView>? {
            val st = state() ?: return null
            if (relays.roads.isEmpty()) return null
            val statuses = ArrayList<RelayStatus>()
            val dirty = dirty()
            val have = relayV()
            val answered = HashSet<String>()
            val refusing = HashSet<String>()
            for (road in relays.roads) {
                when (val r = relays.call(road, "GET", st.vault, "/status", st.pass)) {
                    is Reply.Ok -> {
                        val o = r.json ?: continue
                        val s = relayStatus(road.tag, o) ?: continue
                        statuses.add(s)
                        answered.add(road.tag)
                        if (s.snapV == null) have.remove(road.tag) else have[road.tag] = s.snapV
                        if (st.heirs.any { h -> h.linked && s.heirs.none { it.hid == h.hid } }) dirty.add(road.tag)
                    }
                    is Reply.No -> when (r.code) {
                        // No vault there (yet, or any more): set up again; nothing there can open.
                        404 -> { dirty.add(road.tag); have.remove(road.tag); answered.add(road.tag) }
                        // Another pass holds this vault id there: nothing of this book's can be done on that relay.
                        403 -> { refusing.add(road.tag); answered.add(road.tag) }
                        else -> Unit
                    }
                    Reply.Unreachable -> Unit
                }
            }
            setDirty(dirty)
            setRelayV(have)
            val no = relaysRefusing().toMutableSet()
            no.removeAll(answered)
            no.addAll(refusing)
            store.put(K_RELAY_NO, if (no.isEmpty()) null else JSONArray(no.sorted()).toString())
            val missed = HashSet<String>()
            var views: Map<String, HeirView>? = null
            if (statuses.isNotEmpty()) {
                // The heirs read again: one named while the relays answered is not a stranger.
                val merged = merge(heirs(), statuses, now)
                for ((tag, hid) in merged.rejectAgain) roadOf(tag)?.let {
                    if (!landed(relays.call(it, "POST", st.vault, "/heir/$hid/reject", st.pass))) missed.add(tag)
                }
                for ((tag, hid) in merged.allowAgain) roadOf(tag)?.let { relays.call(it, "POST", st.vault, "/heir/$hid/allow", st.pass) }
                // Removed since, a copy restored over the list, or put there by someone else holding the owner's pass:
                // never put back, and deleted wherever it is.
                if (merged.unknown.isNotEmpty()) remember(merged.unknown.map { it.second })
                for ((tag, hid) in merged.unknown) roadOf(tag)?.let {
                    if (!landed(relays.call(it, "DELETE", st.vault, "/heir/$hid", st.pass))) missed.add(tag)
                }
                saveViews(merged.views)
                store.put(K_STATUS_AT, now.toString())
                views = merged.views
            }
            // A refusal is owed to a relay until it answered and took everything sent to it; one refusing this vault's
            // pass stays owed (a refusal never lands there), and a relay no longer on the road is not asked.
            val left = refuseLeft().toMutableSet()
            if (left.isNotEmpty()) {
                left.removeAll(answered - refusing - missed)
                left.retainAll(relays.roads.map { it.tag }.toSet())
                store.put(K_REFUSE_LEFT, if (left.isEmpty()) null else JSONArray(left.sorted()).toString())
            }
            return views
        }

        /**
         * The owner refuses: remembered first (every later status run holds every relay to it), then said everywhere —
         * never held back by live sync's kill switch (W1). A relay it did not reach is owed it ([refuseLeft], W6).
         */
        suspend fun refuse(hid: String, now: Long = System.currentTimeMillis()): Result {
            val st = state() ?: return Result.Failed(Why.NOT_ON)
            saveHeirs(st.heirs.map { if (it.hid == hid) it.copy(refusedAt = now, allowedAt = null) else it })
            views()[hid]?.let { v ->
                saveViews(views() + (hid to HeirView(hid, false, null, null, false, v.lastSeen, v.onRelays)))
            }
            // A relay without this heir (or without the vault) has nothing to open: refused there too.
            val (result, missed) = everyRelay(st, "/heir/$hid/reject", goneIsDone = true)
            if (missed.isNotEmpty()) store.put(K_REFUSE_LEFT, JSONArray((refuseLeft() + missed).sorted()).toString())
            return result
        }

        /** The owner opens the book to this heir now. */
        suspend fun allow(hid: String, now: Long = System.currentTimeMillis()): Result {
            val st = state() ?: return Result.Failed(Why.NOT_ON)
            saveHeirs(st.heirs.map { if (it.hid == hid) it.copy(allowedAt = now) else it })
            views()[hid]?.let { v ->
                saveViews(views() + (hid to HeirView(hid, true, v.askedAt ?: now, now, true, v.lastSeen, v.onRelays)))
            }
            return everyRelay(st, "/heir/$hid/allow").first
        }

        /** [path] posted to every relay; the result and the relays it did not reach. */
        private fun everyRelay(st: OwnerState, path: String, goneIsDone: Boolean = false): Pair<Result, Set<String>> {
            if (relays.roads.isEmpty()) return Result.Failed(Why.HALTED) to emptySet()
            var ok = 0
            val missed = HashSet<String>()
            for (road in relays.roads) {
                val r = relays.call(road, "POST", st.vault, path, st.pass)
                if (r is Reply.Ok || (goneIsDone && landed(r))) ok++ else missed.add(road.tag)
            }
            return (if (ok == 0) Result.Failed(Why.OFFLINE) else Result.Done(ok, relays.roads.size)) to missed
        }

        /**
         * Waris off: the vault deleted on every relay — never held back by live sync's kill switch (W1) — and every key
         * cleared here but the removed list, which now holds the vault too (a copy restored later never sets it up
         * again, W5). A relay not reached keeps the vault id and owner secret in [K_OFF] and is told on the next run
         * ([retryOff]) — never left able to open.
         */
        suspend fun turnOff(): Result {
            retryOff()
            val st = state() ?: return Result.Done(0, 0)
            val left = HashSet<String>()
            var ok = 0
            for (road in relays.roads) {
                if (landed(relays.call(road, "DELETE", st.vault, "", st.pass))) ok++ else left.add(road.tag)
            }
            for (k in SECRET_KEYS) if (k != K_LINKS && k != K_REMOVED) store.put(k, null)
            remember(listOf(st.vault))
            if (left.isNotEmpty()) {
                store.put(K_OFF, JSONObject().put("vault", st.vault).put("secret", B64.encode(st.ownerSecret))
                    .put("left", JSONArray(left.toList())).toString())
            }
            return Result.Done(ok, relays.roads.size)
        }

        private suspend fun retryOff() {
            val o = store.get(K_OFF)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return
            val vault = o.optString("vault")
            val secret = B64.decodeOrNull(o.optString("secret")) ?: run { store.put(K_OFF, null); return }
            // Never held back by live sync's kill switch (W1): a vault left on a relay is a way in.
            val left = stringSet(o.optJSONArray("left")?.toString())
            val pass = WarisCrypto.ownerPass(secret, vault)
            for (road in relays.roads.filter { it.tag in left }) {
                if (landed(relays.call(road, "DELETE", vault, "", pass))) left.remove(road.tag)
            }
            store.put(K_OFF, if (left.isEmpty()) null else o.put("left", JSONArray(left.toList())).toString())
        }

        /** Whether a vault waits to be removed from some relay (the screen says the off is not finished). */
        suspend fun offPending(): Boolean = store.get(K_OFF) != null

        private fun roadOf(tag: String): LiveCloud.Road? = relays.roads.firstOrNull { it.tag == tag }

        private enum class Step { OK, FULL, BUSY, FAIL }

        /** A relay's refusal read: full (507, or 413 for a chunk), taken all it takes today (429), else a failure. */
        private fun stepOf(r: Reply.No): Step = when (r.code) {
            507, 413 -> Step.FULL
            429 -> Step.BUSY
            else -> Step.FAIL
        }

        private fun why(full: Boolean, busy: Boolean): Why = when {
            full -> Why.FULL
            busy -> Why.BUSY
            else -> Why.OFFLINE
        }

        /** Done there, or nothing there to do: an answer of 404 is "already gone". */
        private fun landed(r: Reply): Boolean = r is Reply.Ok || (r is Reply.No && r.code == 404)
    }

    // ------------------------------------------------------------------ a restore

    /**
     * The Waris settings a restore leaves in the book (7 Nov 2026, the security review: W3, W5), from this book's own
     * before the restore ([here]: none on a live pairing, which keeps nothing of the device's) and the copy's ([copy];
     * any settings, only Waris's are read). The keys are the device's, never the copy's to impose:
     *  - a vault this book keeps stays exactly as it is — its heirs, its W, its marks: an older copy never brings back
     *    an heir removed since, nor its own list over heirs named since;
     *  - a book without one takes the copy's (a new phone restored from the owner's backup), but never on a live
     *    pairing (one device keeps a vault: two would seal its copies under two keys), never a vault turned off, and
     *    never a W ([K_KEY]): no copy carries one since this review, an older copy's is dropped, and the book makes a
     *    new one before anything is sealed ([Owner.state]) — every heir's box and the copy then go again;
     *  - the heirs and vaults removed ([K_REMOVED]) are both lists together, and an heir in them leaves the list;
     *  - a turn-off still to finish ([K_OFF]) and the heir's own links ([K_LINKS]): this book's, else the copy's (not
     *    on a pairing: another device's links are that device's heir keys).
     */
    fun restored(here: List<BookSetting>, copy: List<BookSetting>, pairing: Boolean): List<BookSetting> {
        fun settings(list: List<BookSetting>): Map<String, String> =
            list.mapNotNull { s -> s.value?.takeIf { isSecret(s.key) }?.let { s.key to it } }.toMap()
        val h = settings(here)
        val c = settings(copy)
        val removed = LinkedHashSet(idList(h[K_REMOVED])).apply { addAll(idList(c[K_REMOVED])) }
        fun keeps(m: Map<String, String>): Boolean {
            val vault = m[K_VAULT] ?: return false
            return WarisCrypto.isHex(vault, 32) && vault !in removed && B64.decodeOrNull(m[K_OWNER_SECRET].orEmpty())?.size == 32
        }
        val owner: Map<String, String> = when {
            keeps(h) -> h
            !pairing && keeps(c) -> c - K_KEY - K_KEY_SAFE
            else -> emptyMap()
        }
        val out = LinkedHashMap<String, String>()
        for ((k, v) in owner) if (k != K_LINKS && k != K_REMOVED && k != K_OFF) out[k] = v
        out[K_HEIRS]?.let { text ->
            out[K_HEIRS] = JSONArray().apply { jsonArray(text).filter { it.optString("hid") !in removed }.forEach { put(it) } }.toString()
        }
        (h[K_OFF] ?: c[K_OFF]?.takeIf { !pairing })?.let { out[K_OFF] = it }
        (h[K_LINKS] ?: c[K_LINKS]?.takeIf { !pairing })?.let { out[K_LINKS] = it }
        if (removed.isNotEmpty()) out[K_REMOVED] = JSONArray(removed.toList().takeLast(MAX_REMOVED)).toString()
        return out.map { (k, v) -> BookSetting(k, v) }
    }

    // ------------------------------------------------------------------ the snapshot, packed

    class TooBig : Exception("the book is too big for a Waris copy")

    /** Thrown by a [Book] whose shop is no longer the one it was taken for (a switch, a helper's copy opened): W8. */
    class BookMoved : Exception("the book's shop changed while its copy was being made")

    class Packed(val v: Int, val sealed: ByteArray, val sig: String) {
        val chunks: List<ByteArray> = WarisCrypto.chunks(sealed) ?: throw TooBig()
    }

    /** The book written by [book], gzipped (refused past the relays' 8 MiB), sealed for version [v]. */
    suspend fun pack(book: Book, st: OwnerState, v: Int): Packed {
        val gz = Capped(WarisCrypto.MAX_SEALED - WarisCrypto.SNAP_OVERHEAD)
        val sig = Signature()
        try {
            GZIPOutputStream(gz, 64 * 1024).use { z -> book.write(Tee(z, sig)) }
        } catch (e: Throwable) {
            // The cap may come back wrapped (the export runs in the book's transaction): found by its cause.
            if (generateSequence(e) { it.cause }.any { it is Capped.Over }) throw TooBig()
            throw e
        }
        val sealed = WarisCrypto.sealSnapshot(st.w, st.keyVersion, st.vault, v, gz.buffer(), gz.size())
        return Packed(v, sealed, sig.done())
    }

    /** A gzip over [cap] bytes stops the write at once (a book too big is said, not uploaded). */
    private class Capped(private val cap: Int) : ByteArrayOutputStream(64 * 1024) {
        class Over : java.io.IOException("over")
        fun buffer(): ByteArray = buf
        override fun write(b: Int) { if (count + 1 > cap) throw Over(); super.write(b) }
        override fun write(b: ByteArray, off: Int, len: Int) { if (count + len > cap) throw Over(); super.write(b, off, len) }
    }

    /**
     * SHA-256 of the book as written, its "exportedAt" figure left out (an unchanged book hashes the same, as
     * DriveBackup's content signature): the field is among the first bytes, so the head is held and cleaned once.
     */
    private class Signature {
        private val d = MessageDigest.getInstance("SHA-256")
        private val head = ByteArrayOutputStream()
        private var headDone = false
        fun write(b: ByteArray, off: Int, len: Int) {
            if (headDone) { d.update(b, off, len); return }
            head.write(b, off, len)
            if (head.size() >= 2048) flushHead()
        }
        private fun flushHead() {
            headDone = true
            val text = String(head.toByteArray(), Charsets.ISO_8859_1).replace(Regex("\"exportedAt\"\\s*:\\s*\\d+"), "")
            d.update(text.toByteArray(Charsets.ISO_8859_1))
        }
        fun done(): String { if (!headDone) flushHead(); return "sha256:" + WarisCrypto.hex(d.digest()) }
    }

    private class Tee(private val out: OutputStream, private val sig: Signature) : OutputStream() {
        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
        override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len); sig.write(b, off, len) }
        override fun flush() = out.flush()
    }

    // ------------------------------------------------------------------ the heir's side

    class Link(
        val vault: String,
        val hid: String,
        val secret: String,
        val priv: String,
        val pub: String,
        val shop: String,
        val wait: Int,
        val addedAt: Long,
        val askedAt: Long? = null,
        val openAt: Long? = null,
        val rejectedAt: Long? = null,
        val retryAt: Long? = null,
        val lastAliveAt: Long? = null,
        val open: Boolean = false,
        /** A relay has answered this heir (the owner scanned their code and put them on): until then, "waiting". */
        val known: Boolean = false
    ) {
        val secretBytes: ByteArray get() = WarisCrypto.unb64url(secret)!!
        val pass: String get() = WarisCrypto.heirPass(secretBytes, vault, hid)
        val key: String get() = "$vault:$hid"

        fun toJson(): JSONObject = JSONObject().apply {
            put("vault", vault); put("hid", hid); put("secret", secret); put("priv", priv); put("pub", pub)
            put("shop", shop); put("wait", wait); put("addedAt", addedAt); put("open", open)
            askedAt?.let { put("askedAt", it) }; openAt?.let { put("openAt", it) }; rejectedAt?.let { put("rejectedAt", it) }
            retryAt?.let { put("retryAt", it) }; lastAliveAt?.let { put("lastAliveAt", it) }
            put("known", known)
        }

        fun with(askedAt: Long? = this.askedAt, openAt: Long? = this.openAt, rejectedAt: Long? = this.rejectedAt,
                 retryAt: Long? = this.retryAt, lastAliveAt: Long? = this.lastAliveAt, open: Boolean = this.open,
                 known: Boolean = this.known) =
            Link(vault, hid, secret, priv, pub, shop, wait, addedAt, askedAt, openAt, rejectedAt, retryAt, lastAliveAt, open, known)

        companion object {
            fun of(o: JSONObject): Link? {
                val vault = o.optString("vault").takeIf { WarisCrypto.isHex(it, 32) } ?: return null
                val hid = o.optString("hid").takeIf { WarisCrypto.isHex(it, 16) } ?: return null
                val secret = o.optString("secret").takeIf { WarisCrypto.unb64url(it)?.size == 32 } ?: return null
                val priv = o.optString("priv").ifEmpty { return null }
                return Link(
                    vault, hid, secret, priv, o.optString("pub"), o.optString("shop"), o.optInt("wait", DEFAULT_WAIT),
                    o.optLong("addedAt", 0L), longOrNull(o, "askedAt"), longOrNull(o, "openAt"), longOrNull(o, "rejectedAt"),
                    longOrNull(o, "retryAt"), longOrNull(o, "lastAliveAt"), o.optBoolean("open", false), o.optBoolean("known", false)
                )
            }
        }
    }

    sealed class Opened {
        /** The book's backup JSON was written to the caller's stream. */
        object Done : Opened()
        class Failed(val why: Why, val at: Long? = null) : Opened()
    }

    class Heir(private val store: Store, private val relays: Relays = Relays()) {

        suspend fun links(): List<Link> = jsonArray(store.get(K_LINKS)).mapNotNull { Link.of(it) }

        private suspend fun save(list: List<Link>) =
            store.put(K_LINKS, if (list.isEmpty()) null else JSONArray().apply { list.forEach { put(it.toJson()) } }.toString())

        private suspend fun update(l: Link) = save(links().map { if (it.key == l.key) l else it })

        /**
         * The owner's code (QR1) or the heir's own card, scanned. A code: a key pair is made (or the one already made
         * for it kept), and the answer carries the heir's code (QR2) for the owner to scan. A card: the link comes
         * back whole, no code needed.
         */
        suspend fun take(scanned: String?, now: Long = System.currentTimeMillis()): Pair<Link, String?>? {
            WarisCrypto.parseCard(scanned)?.let { c ->
                val pub = WarisCrypto.publicOf(c.priv) ?: return null
                val l = Link(c.vault, c.hid, WarisCrypto.b64url(c.secret), WarisCrypto.b64url(c.priv), WarisCrypto.b64url(pub), c.shop, c.wait, now)
                val old = links().firstOrNull { it.key == l.key }
                // A card is of an heir the owner already put on: known.
                val kept = old?.let { l.with(it.askedAt, it.openAt, it.rejectedAt, it.retryAt, it.lastAliveAt, it.open, true) } ?: l.with(known = true)
                save(links().filter { it.key != l.key } + kept)
                return kept to null
            }
            val code = WarisCrypto.parseOwnerCode(scanned) ?: return null
            val secret = WarisCrypto.b64url(code.secret)
            links().firstOrNull { it.vault == code.vault && it.hid == code.hid && it.secret == secret }?.let { return it to heirCode(it) }
            val kp = WarisCrypto.newKeyPair()
            val l = Link(code.vault, code.hid, secret, WarisCrypto.b64url(kp.priv), WarisCrypto.b64url(kp.pub), code.shop, code.wait, now)
            save(links().filter { it.key != l.key } + l)
            return l to heirCode(l)
        }

        fun heirCode(l: Link): String = WarisCrypto.heirCode(l.secretBytes, l.vault, l.hid, WarisCrypto.unb64url(l.pub)!!)

        fun card(l: Link): String = WarisCrypto.card(l.vault, l.hid, l.secretBytes, WarisCrypto.unb64url(l.priv)!!, l.wait, l.shop)

        suspend fun forget(l: Link) = save(links().filter { it.key != l.key })

        /** "Still here" to every relay, at most once a day per link unless [force]; what they say of the request is kept. */
        suspend fun alive(l: Link, now: Long = System.currentTimeMillis(), force: Boolean = false): Link {
            if (!force && l.lastAliveAt != null && now - l.lastAliveAt < ALIVE_EVERY_MS) return l
            if (relays.roads.isEmpty() || relays.halted()) return l
            var asked: Long? = null; var openAt: Long? = null; var rejected: Long? = null; var open = false; var any = false
            for (road in relays.roads) {
                val r = relays.call(road, "POST", l.vault, "/heir/${l.hid}/alive", l.pass) as? Reply.Ok ?: continue
                val o = r.json ?: continue
                any = true
                longOrNull(o, "askedAt")?.let { asked = minOf(asked ?: it, it) }
                longOrNull(o, "openAt")?.let { openAt = minOf(openAt ?: it, it) }
                longOrNull(o, "rejectedAt")?.let { rejected = maxOf(rejected ?: it, it) }
                if (o.optBoolean("open", false)) open = true
            }
            if (!any) return l
            val next = l.with(askedAt = asked, openAt = openAt, rejectedAt = rejected, lastAliveAt = now, open = open,
                retryAt = rejected?.let { it + DAY_MS }, known = true)
            update(next)
            return next
        }

        /** The request, to every relay. */
        suspend fun ask(l: Link, now: Long = System.currentTimeMillis()): Pair<Link, Result> {
            if (relays.roads.isEmpty() || relays.halted()) return l to Result.Failed(Why.HALTED)
            var asked: Long? = null; var openAt: Long? = null; var retry: Long? = null; var ok = 0; var pass = false; var gone = false
            for (road in relays.roads) {
                when (val r = relays.call(road, "POST", l.vault, "/heir/${l.hid}/ask", l.pass)) {
                    is Reply.Ok -> {
                        ok++
                        r.json?.let { o ->
                            longOrNull(o, "askedAt")?.let { asked = minOf(asked ?: it, it) }
                            longOrNull(o, "openAt")?.let { openAt = minOf(openAt ?: it, it) }
                        }
                    }
                    is Reply.No -> when {
                        r.code == 429 -> longOrNull(r.json, "retryAt")?.let { retry = maxOf(retry ?: it, it) }
                        r.code == 403 -> pass = true
                        r.code == 404 -> gone = true
                    }
                    Reply.Unreachable -> Unit
                }
            }
            if (ok == 0) {
                val why = when { retry != null -> Why.WAIT; pass || gone -> Why.PASS; else -> Why.OFFLINE }
                if (retry != null) update(l.with(retryAt = retry, known = true))
                return l to Result.Failed(why, retry)
            }
            val next = l.with(askedAt = asked ?: now, openAt = openAt, rejectedAt = null, retryAt = null, lastAliveAt = now, known = true)
            update(next)
            return next to Result.Done(ok, relays.roads.size)
        }

        /**
         * The book, when some relay opens it: the box opened with this phone's private key (W, keyVersion), the chunks
         * read from that relay, their keyVersion checked, opened, unpacked into [out] as the backup JSON.
         */
        suspend fun open(l: Link, out: OutputStream): Opened {
            if (relays.roads.isEmpty() || relays.halted()) return Opened.Failed(Why.HALTED)
            val priv = WarisCrypto.unb64url(l.priv) ?: return Opened.Failed(Why.BROKEN)
            var waitingUntil: Long? = null
            var reached = false
            var why = Why.OFFLINE
            for (road in relays.roads) {
                val r = relays.call(road, "GET", l.vault, "/heir/${l.hid}/open", l.pass)
                if (r is Reply.No) {
                    reached = true
                    if (r.code == 403 && r.why == "waiting") {
                        why = Why.WAITING
                        longOrNull(r.json, "openAt")?.let { waitingUntil = minOf(waitingUntil ?: it, it) }
                    } else if (why == Why.OFFLINE) why = Why.PASS
                    continue
                }
                if (r !is Reply.Ok) continue
                reached = true
                val o = r.json ?: continue
                val box = runCatching { B64.decode(o.optString("box")) }.getOrNull()
                val unboxed = box?.let { WarisCrypto.openBox(priv, it, l.vault, l.hid) }
                if (unboxed == null) { why = Why.BROKEN; continue }
                val snap = o.optJSONObject("snap")
                if (snap == null) { if (why != Why.BROKEN) why = Why.NO_COPY; continue }
                val v = snap.optInt("v"); val n = snap.optInt("n"); val bytes = snap.optLong("bytes")
                if (v < 1 || n !in 1..WarisCrypto.MAX_CHUNKS || bytes !in 1..WarisCrypto.MAX_SEALED.toLong()) { why = Why.BROKEN; continue }
                val all = ByteArrayOutputStream(bytes.toInt())
                var whole = true
                for (i in 0 until n) {
                    val c = relays.call(road, "GET", l.vault, "/heir/${l.hid}/snap?v=$v&i=$i", l.pass)
                    if (c !is Reply.Ok || all.size() + c.bytes.size > bytes) { whole = false; break }
                    all.write(c.bytes)
                }
                if (!whole || all.size().toLong() != bytes) { why = Why.BROKEN; continue }
                val sealed = all.toByteArray()
                if (WarisCrypto.snapshotKeyVersion(sealed) != unboxed.keyVersion) { why = Why.BROKEN; continue }
                val gz = WarisCrypto.openSnapshot(unboxed.w, sealed, l.vault, v) ?: run { why = Why.BROKEN; null } ?: continue
                return try {
                    gunzip(gz, out, MAX_BOOK_BYTES)
                    update(l.with(open = true))
                    Opened.Done
                } catch (e: java.io.IOException) {
                    Opened.Failed(Why.BROKEN)
                }
            }
            if (!reached) return Opened.Failed(Why.OFFLINE)
            return Opened.Failed(why, waitingUntil)
        }
    }

    fun gunzip(gz: ByteArray, out: OutputStream, limit: Long) {
        GZIPInputStream(gz.inputStream(), 64 * 1024).use { input ->
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > limit) throw java.io.IOException("too big")
                out.write(buf, 0, n)
            }
        }
        out.flush()
    }

    // ------------------------------------------------------------------ small helpers

    fun relayStatus(tag: String, o: JSONObject): RelayStatus? = runCatching {
        val arr = o.optJSONArray("heirs") ?: JSONArray()
        val heirs = (0 until arr.length()).mapNotNull { i ->
            val h = arr.optJSONObject(i) ?: return@mapNotNull null
            RelayHeir(h.optString("hid"), longOrNull(h, "lastSeen"), longOrNull(h, "askedAt"), longOrNull(h, "rejectedAt"),
                longOrNull(h, "allowedAt"), longOrNull(h, "openAt"))
        }
        val snap = o.optJSONObject("snap")
        RelayStatus(tag, o.optLong("now", System.currentTimeMillis()), snap?.optInt("v")?.takeIf { it > 0 }, heirs)
    }.getOrNull()

    internal fun longOrNull(o: JSONObject?, key: String): Long? =
        if (o == null || !o.has(key) || o.isNull(key)) null else o.optLong(key).takeIf { o.opt(key) is Number }

    private fun jsonArray(text: String?): List<JSONObject> {
        val a = text?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
    }

    private fun stringSet(text: String?): MutableSet<String> {
        val a = text?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return HashSet()
        return (0 until a.length()).map { a.optString(it) }.toMutableSet()
    }

    /** An heir's id (16 hex) or a vault's (32 hex). */
    private fun isId(s: String): Boolean = WarisCrypto.isHex(s, 16) || WarisCrypto.isHex(s, 32)

    /** The well-formed ids of a JSON array, in their order. */
    private fun idList(text: String?): List<String> {
        val a = text?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return emptyList()
        return (0 until a.length()).map { a.optString(it) }.filter { isId(it) }.distinct()
    }

    /** One for the process: W is made new at most once, whichever screen or run asks first ([Owner.state]). */
    private val keyLock = Mutex()
}

/** Standard Base64 that answers null instead of throwing (a setting that does not read is no key). */
internal fun B64.decodeOrNull(text: String): ByteArray? = runCatching { decode(text) }.getOrNull()
