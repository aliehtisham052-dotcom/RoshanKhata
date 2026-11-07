package com.innovation313.roshankhata.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.WeakHashMap

/**
 * One day's seal of the book (schema 44, [Schema44]): the khata entries with ids in (fromId, toId] — their leaf hashes
 * ([SealMath.leafTextV2]; e1 [SealMath.leafText] for seals made before 7 Nov 2026) concatenated in id order in
 * [leaves] (null for the first seal, and for a day with no new entry but a changed audit log) — the audit log's newest
 * seal at the time ([auditHead]), the seal before's [prevDigest], and the [digest] of all of it ([SealMath.digest]).
 * Which version a seal is, its digest says ([BookSeals.versionOf]). [sealedAt] is the device's clock. [tokens] is a
 * JSON object, witness name → {"token": base64 DER TimeStampToken, "time": genTime ms}; a witness not in it has not
 * answered yet. The time beside a token is never believed: it is read again from the token ([BookSeals.answers]).
 */
@Entity(tableName = "book_seals", indices = [Index("sealedAt")])
data class BookSeal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sealedAt: Long,
    val fromId: Long,
    val toId: Long,
    val leafCount: Int,
    val leaves: ByteArray? = null,
    val auditHead: String? = null,
    val prevDigest: String? = null,
    val digest: String,
    val tokens: String? = null
) {
    // A data class compares arrays by identity; a seal read back from a backup must equal its original.
    override fun equals(other: Any?): Boolean = other is BookSeal && id == other.id && sealedAt == other.sealedAt &&
        fromId == other.fromId && toId == other.toId && leafCount == other.leafCount &&
        sameLeaves(leaves, other.leaves) &&
        auditHead == other.auditHead && prevDigest == other.prevDigest && digest == other.digest && tokens == other.tokens

    override fun hashCode(): Int = listOf(id, sealedAt, fromId, toId, leafCount, leaves?.contentHashCode(), auditHead, prevDigest, digest, tokens).hashCode()

    private fun sameLeaves(a: ByteArray?, b: ByteArray?): Boolean = if (a == null || b == null) a == null && b == null else a.contentEquals(b)
}

/**
 * A seal without its leaves (7 Nov 2026, the security review's M5): what sealing, the witnesses and the screens read.
 * A seal's leaves can be 160 KB; only the proof, its chain and the backup read them, a few rows at a time.
 */
data class SealHead(
    val id: Long,
    val sealedAt: Long,
    val fromId: Long,
    val toId: Long,
    val leafCount: Int,
    val auditHead: String?,
    val prevDigest: String?,
    val digest: String,
    val tokens: String?
)

/**
 * Kitab ki Muhar, the book's seal (4 Nov 2026, the owner's approved plan). Purpose: to show a customer in a dispute ("you
 * wrote this entry afterwards") that an entry was already in the book on a day, and that the book was not rewritten
 * since. Once a calendar day, while the owner keeps it on, the entries written since the last seal and the audit log's
 * head are sealed into one digest chained to the seal before, and three independent free RFC 3161 Time Stamp
 * Authorities stamp that digest ([Rfc3161]). Only the 32-byte digest leaves the device — nothing readable of the book.
 *
 * What it is not, said plainly (and on the help screen): it is not a legal "advanced electronic signature"; a court
 * weighs it as evidence. Entries written before the seal was turned on are not individually provable. On the device an
 * answer is kept only when it adds up — it is for this seal's digest and its signature holds with the certificate it
 * carries ([Rfc3161.verify]) — but whose certificate that is, the device cannot tell without the TSAs' CA certificates:
 * the screens say answers were received, never verified, and the proof file tells how to check them with OpenSSL.
 * A seal's own date ([BookSeal.sealedAt]) is the device's clock and proves nothing alone; the witnesses' times are read
 * from their signed tokens.
 *
 * Since 7 Nov 2026 (the security review): an entry's leaf covers its goods lines, the way it was paid, the loan and bin
 * marks (e2, M3); a proof recomputes the seal's digest from its header and leaves, holds each answer to it and follows
 * the chain back (M2); a seal holds at most [MAX_LEAVES] entries (M5); each witness is asked on its own, each request
 * with its whole deadline, under a lock per book (M4); an answer lands only on the seal it was asked for (M6); the
 * switch is each device's own and written in the audit log (M9).
 *
 * Shared, pure JVM: the phone calls [daily] from where it takes the day's safety copy; the computer can call it the same
 * way later. The switch is per book and per device ([SWITCH], [BookSettings.BOOK_SEAL]); off by default.
 */
object BookSeals {

    /** The book setting: "1" while the owner keeps the seal on; unset = off. Never sent by live sync. */
    const val SWITCH = BookSettings.BOOK_SEAL

    /** The audit log's actions for the switch (the review's M9). */
    const val AUDIT_ON = "seal_on"
    const val AUDIT_OFF = "seal_off"

    /** A witness that has not stamped a seal within this long after it was made is given up. */
    const val RETRY_MS = 7L * 24 * 60 * 60 * 1000

    /** Between two requests to one TSA (Sectigo asks scripts to wait 15 s or more). */
    const val GAP_MS = 15_000L

    /** Entries read a page of ids at a time (lesson 70). */
    const val PAGE = 400

    /**
     * At most this many entries in one seal (the review's M5): 160 KB of leaves, a row Android reads whole (a cursor's
     * window is 2 MB; one seal of 65,000 entries could not be read back, and sealing and every backup stopped). A
     * catch-up past it is several seals, each chained to the one before, made the same day.
     */
    const val MAX_LEAVES = 5_000

    /** Seals read with their leaves this many at a time: the chain the proof follows, the backup. */
    const val WITH_LEAVES_PAGE = 8

    class Witness(val name: String, val url: String)

    val WITNESSES = listOf(
        Witness("FreeTSA", "https://freetsa.org/tsr"),
        // Plain http (network_security_config.xml allows it for these two hosts only): what the device checks is Rfc3161.verify.
        Witness("Sectigo", "http://timestamp.sectigo.com"),
        Witness("DigiCert", "http://timestamp.digicert.com")
    )

    /** One witness's stamp as it is kept: the token as base64 DER and the genTime read from it when it came. */
    class Stamp(val token: String, val time: Long)

    /** One book's locks (the review's M4): two books, or a slow witness of one, never wait for another. */
    private class Locks {
        val sealing = Mutex()
        val witnessing = Mutex()
    }

    private val locks = WeakHashMap<KhataDao, Locks>()

    private fun locksOf(dao: KhataDao): Locks = synchronized(locks) { locks.getOrPut(dao) { Locks() } }

    suspend fun isOn(dao: KhataDao): Boolean = BookSettings.isOn(dao.settingValue(SWITCH))

    /** The stamps a seal's JSON holds, by witness name, as written; a JSON that does not read holds none. */
    fun stamps(tokens: String?): Map<String, Stamp> {
        val text = tokens ?: return emptyMap()
        return runCatching {
            val o = JSONObject(text)
            buildMap {
                for (k in o.keys()) {
                    val s = o.optJSONObject(k) ?: continue
                    val token = s.optString("token", "").takeIf { it.isNotEmpty() } ?: continue
                    put(k, Stamp(token, s.optLong("time", 0L)))
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun stamps(seal: BookSeal): Map<String, Stamp> = stamps(seal.tokens)

    fun tokensJson(stamps: Map<String, Stamp>): String = JSONObject().apply {
        for ((k, s) in stamps) put(k, JSONObject().put("token", s.token).put("time", s.time))
    }.toString()

    /**
     * The answers in [tokens] that add up on this device for [digest] ([Rfc3161.tokenTime]), by witness name, each with
     * the time read from its own token — never the one written beside it. One that does not (an edited backup, or one
     * an older build kept without its signature checked) counts as none, and its witness is asked again while the
     * week lasts.
     */
    fun answers(tokens: String?, digest: String): Map<String, Long> {
        val d = runCatching { SealMath.unhex(digest) }.getOrNull()?.takeIf { it.size == SealMath.HASH_BYTES } ?: return emptyMap()
        return buildMap {
            for ((name, s) in stamps(tokens)) {
                val token = runCatching { B64.decode(s.token) }.getOrNull() ?: continue
                Rfc3161.tokenTime(token, d)?.let { put(name, it) }
            }
        }
    }

    fun answers(seal: SealHead): Map<String, Long> = answers(seal.tokens, seal.digest)

    /**
     * Which leaf version [seal] was made with — 2 (e2 leaves, the "rk-seal-v2" header) or 1 (e1, "rk-seal-v1") — read
     * from its digest itself: the header's tag is part of what the digest covers. Null when the digest is neither: its
     * leaves, or the fields its header is made of, were changed after it was made (the review's M2).
     */
    fun versionOf(seal: BookSeal): Int? {
        val leaves = seal.leaves
        if ((leaves?.size ?: 0) != seal.leafCount * SealMath.HASH_BYTES) return null
        if (SealMath.digest(SealMath.headerV2(seal.prevDigest, seal.sealedAt, seal.leafCount, seal.auditHead), leaves) == seal.digest) return 2
        if (SealMath.digest(SealMath.header(seal.prevDigest, seal.sealedAt, seal.fromId, seal.toId, seal.leafCount, seal.auditHead), leaves) == seal.digest) return 1
        return null
    }

    /**
     * The switch on: the setting written with its audit row and, when the book has never been sealed, the first seal
     * at once — no leaves, fromId = toId = the newest entry id: sealing starts from today. Returns the new first seal,
     * if one was made. Turned on again after an off, the chain goes on from the last seal (the entries since come in
     * the next one).
     */
    suspend fun turnOn(dao: KhataDao, now: Long = System.currentTimeMillis(), user: String? = Access.OWNER): BookSeal? =
        locksOf(dao).sealing.withLock {
            dao.setSealSwitch(true, user)
            dao.sealAsOne { if (dao.latestSealHead() == null) makeFirst(dao, now) else null }
        }

    /** The switch off, with its audit row: no more seals or requests; the seals made stay (they are the proofs). */
    suspend fun turnOff(dao: KhataDao, user: String? = Access.OWNER) {
        dao.setSealSwitch(false, user)
    }

    private suspend fun makeFirst(dao: KhataDao, now: Long): BookSeal {
        val max = dao.maxEntryId()
        val head = dao.lastAuditHash()
        val digest = SealMath.digest(SealMath.headerV2(null, now, 0, head), null)
        val seal = BookSeal(sealedAt = now, fromId = max, toId = max, leafCount = 0, auditHead = head, digest = digest)
        return seal.copy(id = dao.insertSeal(seal))
    }

    /**
     * Today's seal, when it is due: the switch on, no seal yet today (by the device's calendar day), and something new
     * since the last — entries past its toId, or another audit head. More than [MAX_LEAVES] new entries make several
     * seals, each chained to the one before. Each seal is read and written in one transaction ([KhataDao.sealAsOne]).
     * The newest made, or null when nothing was. [alive] is asked before each write (the phone: the book is still the
     * open one, of the same business).
     */
    suspend fun sealIfDue(dao: KhataDao, now: Long = System.currentTimeMillis(), alive: () -> Boolean = { true }): BookSeal? =
        locksOf(dao).sealing.withLock {
            if (!isOn(dao)) return@withLock null
            var made: BookSeal? = null
            while (true) {
                val continuing = made != null
                val seal = dao.sealAsOne { nextSeal(dao, now, continuing, alive) } ?: break
                made = seal
                // A full seal may have left entries for the next one; one that was not full reached the newest.
                if (seal.leafCount < MAX_LEAVES) break
            }
            made
        }

    /** The next seal, written; null when none is due. [continuing]: the next part of a catch-up, made the same day. */
    private suspend fun nextSeal(dao: KhataDao, now: Long, continuing: Boolean, alive: () -> Boolean): BookSeal? {
        val last = dao.latestSealHead() ?: return if (!continuing && alive()) makeFirst(dao, now) else null
        // Once a calendar day; a clock put back behind the last seal waits for it.
        if (!continuing && (now < last.sealedAt || DayMath.dayNumber(last.sealedAt) == DayMath.dayNumber(now))) return null
        val max = dao.maxEntryId()
        val head = dao.lastAuditHash()
        if (max <= last.toId && head == last.auditHead) return null
        val from = last.toId
        val out = java.io.ByteArrayOutputStream()
        var count = 0
        var after = from
        while (count < MAX_LEAVES) {
            val want = minOf(PAGE, MAX_LEAVES - count)
            val page = dao.sealEntriesPage(after, max, want)
            if (page.isEmpty()) break
            val goods = dao.sealGoodsBetween(after, page.last().id).groupBy { it.entryId }
            for (e in page) {
                out.write(SealMath.leafHash(SealMath.leafTextV2(e, goods[e.id].orEmpty())))
                count++
            }
            after = page.last().id
            if (page.size < want) break
        }
        // Full, with entries still past it: this seal ends at its last entry, and the next goes on from there.
        val to = if (count == MAX_LEAVES && dao.sealEntriesPage(after, max, 1).isNotEmpty()) after else maxOf(max, from)
        val leaves = if (count == 0) null else out.toByteArray()
        val digest = SealMath.digest(SealMath.headerV2(last.digest, now, count, head), leaves)
        if (!alive()) return null
        val seal = BookSeal(
            sealedAt = now, fromId = from, toId = to, leafCount = count, leaves = leaves,
            auditHead = head, prevDigest = last.digest, digest = digest
        )
        return seal.copy(id = dao.insertSeal(seal))
    }

    /**
     * Asks every witness that has no answer holding for a seal of the last [RETRY_MS] to stamp it. Each witness runs on
     * its own, side by side with the others (the review's M4) — one request at a time to one TSA, [GAP_MS] apart, each
     * request with its own whole deadline ([Rfc3161.DEADLINE_MS]) — so one slow TSA holds up no other. An answer that
     * does not add up ([Rfc3161.stamp] refuses it) is not kept and is asked for again another day; after [RETRY_MS]
     * the witness is given up quietly. An answer is written only onto the seal it was asked for (its digest unchanged,
     * [KhataDao.addSealStamp]). Nothing is asked while the kill switch says so ([halted], live.json's
     * `"seal": false`). Returns the answers added.
     */
    suspend fun witness(
        dao: KhataDao,
        now: Long = System.currentTimeMillis(),
        http: Rfc3161.Http = Rfc3161.plain,
        halted: () -> Boolean = { LiveControl.sealHalted() },
        alive: () -> Boolean = { true },
        pause: suspend (Long) -> Unit = { delay(it) }
    ): Int = locksOf(dao).witnessing.withLock {
        if (!isOn(dao)) return@withLock 0
        val pending = dao.sealHeadsSince(now - RETRY_MS).filter { it.sealedAt <= now }
            .map { it to answers(it).keys }
            .filter { (_, have) -> WITNESSES.any { it.name !in have } }
        if (pending.isEmpty() || halted()) return@withLock 0
        coroutineScope {
            WITNESSES.map { w ->
                async {
                    var added = 0
                    var asked = false
                    for ((s, have) in pending) {
                        if (w.name in have) continue
                        if (!alive()) break
                        if (asked) pause(GAP_MS)
                        asked = true
                        val digest = runCatching { SealMath.unhex(s.digest) }.getOrNull() ?: continue
                        val reply = Rfc3161.stamp(http, w.url, digest)
                        if (reply !is Rfc3161.Reply.Granted || !alive()) continue
                        if (dao.addSealStamp(s.id, s.digest, w.name, Stamp(B64.encode(reply.token), reply.genTime))) added++
                    }
                    added
                }
            }.awaitAll().sum()
        }
    }

    /** What the day asks: today's seal if due, then the witnesses. Off the main thread; never throws for a network. */
    suspend fun daily(
        dao: KhataDao,
        now: Long = System.currentTimeMillis(),
        http: Rfc3161.Http = Rfc3161.plain,
        halted: () -> Boolean = { LiveControl.sealHalted() },
        alive: () -> Boolean = { true }
    ): BookSeal? {
        if (!isOn(dao)) return null
        val made = sealIfDue(dao, now, alive)
        witness(dao, now, http, halted, alive)
        return made
    }

    // ---------- What the screens say ----------

    /** The switch, the newest seal, and how many witnesses' answers for it add up ([answered] of [of]). */
    class Status(val on: Boolean, val last: SealHead?, val answered: Int, val of: Int = WITNESSES.size)

    suspend fun status(dao: KhataDao): Status {
        val last = dao.latestSealHead()
        val held = last?.let { answers(it).keys }.orEmpty()
        return Status(isOn(dao), last, WITNESSES.count { it.name in held })
    }

    /** One witness's answer for a seal, as the proof shows it: [genTime] read from the [token] itself. */
    class Answer(val name: String, val url: String, val genTime: Long, val token: String)

    /**
     * How the seals before one stand: [seals] read back along its prevDigest links, and whether each recomputed to its
     * own digest and was the one the next pointed at ([intact]). It goes back as far as the seals go.
     */
    class Chain(val seals: Int, val intact: Boolean)

    /** Where one entry stands. */
    sealed class Proof {
        /**
         * In [seal] at [index], which adds up as version [version]: its leaf text and hash as the seal holds them, the
         * witnesses' [answers] that add up for its digest, and how the [chain] before it stands.
         */
        class Sealed(
            val seal: BookSeal,
            val version: Int,
            val leafText: String,
            val leafHex: String,
            val index: Int,
            val answers: List<Answer>,
            val chain: Chain
        ) : Proof()
        /** In [seal]'s range, but its leaf is not there any more: edited after the seal (the audit log says how). */
        class Changed(val seal: BookSeal) : Proof()
        /** The seal over the entry's range does not add up: its leaves or header were changed after it was made. */
        class Broken(val seal: BookSeal) : Proof()
        /** Written after the newest seal: it goes into the next one. */
        object NotYet : Proof()
        /** Written before the seal was first turned on. */
        object Before : Proof()
        /** No such entry. */
        object Unknown : Proof()
    }

    /**
     * Where entry [entryId] stands, worked out again from the book (the review's M2): the seal's digest recomputed from
     * its header and leaves (else [Proof.Broken]), the entry's leaf by the seal's own version (else [Proof.Changed]),
     * each answer held to that digest with its time read from its token, and the chain followed back.
     */
    suspend fun proofOf(dao: KhataDao, entryId: Long): Proof {
        val entry = dao.sealEntryOf(entryId) ?: return Proof.Unknown
        val seal = dao.sealCovering(entryId)
            ?: return if (dao.sealReachingId(entryId) != null) Proof.Before else Proof.NotYet
        val version = versionOf(seal) ?: return Proof.Broken(seal)
        val text = if (version == 1) SealMath.leafText(dao.sealLineOf(entryId) ?: return Proof.Unknown)
            else SealMath.leafTextV2(entry, dao.sealGoodsOf(entryId))
        val leaf = SealMath.leafHash(text)
        val at = SealMath.indexOf(seal.leaves, leaf)
        if (at < 0) return Proof.Changed(seal)
        val times = answers(seal.tokens, seal.digest)
        val raw = stamps(seal)
        val held = WITNESSES.mapNotNull { w -> times[w.name]?.let { t -> Answer(w.name, w.url, t, raw.getValue(w.name).token) } }
        return Proof.Sealed(seal, version, text, SealMath.hex(leaf), at, held, chainBefore(dao, seal))
    }

    /** The seals before [seal], followed back along their links a few rows at a time, each recomputed. */
    private suspend fun chainBefore(dao: KhataDao, seal: BookSeal): Chain {
        var expect = seal.prevDigest ?: return Chain(0, true)
        var before = seal.id
        var count = 0
        while (true) {
            val page = dao.sealsBefore(before, WITH_LEAVES_PAGE)
            if (page.isEmpty()) return Chain(count, true)
            for (s in page) {
                count++
                if (s.digest != expect || versionOf(s) == null) return Chain(count, false)
                expect = s.prevDigest ?: return Chain(count, true)
                before = s.id
            }
        }
    }

    /**
     * The proof file a customer, a lawyer or a court's expert can check without the app: the entry's leaf text and hash,
     * the seal's digest header and leaves (base64), each witness's token that adds up on the device with the time read
     * from it, and the steps with OpenSSL. English, on purpose: it is read by whoever checks it, wherever. No seal id
     * and no entry range (the review's M7): they would tell how many days and entries the shop has written; only a
     * version-1 seal's header carries its range, because its digest was made over it.
     */
    fun proofFile(p: Proof.Sealed, entryNumber: String, madeAt: Long = System.currentTimeMillis()): String {
        val s = p.seal
        val v1 = p.version == 1
        val header = if (v1) SealMath.header(s.prevDigest, s.sealedAt, s.fromId, s.toId, s.leafCount, s.auditHead)
            else SealMath.headerV2(s.prevDigest, s.sealedAt, s.leafCount, s.auditHead)
        return JSONObject().apply {
            put("format", "roshan-khata-seal-proof-v2")
            put("madeAtByPhoneClock", madeAt)
            put("entry", JSONObject().apply {
                put("entryNumber", entryNumber)
                put("leafText", p.leafText)
                put("leafSha256", p.leafHex)
                put("indexInSeal", p.index)
            })
            put("seal", JSONObject().apply {
                put("version", p.version)
                put("sealedAtByPhoneClock", s.sealedAt)
                put("leaves", s.leaves?.let { B64.encode(it) } ?: JSONObject.NULL)
                put("auditHead", s.auditHead ?: JSONObject.NULL)
                put("prevDigest", s.prevDigest ?: JSONObject.NULL)
                put("digest", s.digest)
                put("digestHeader", header)
            })
            put("witnesses", JSONArray().apply {
                for (a in p.answers) {
                    put(JSONObject().put("name", a.name).put("url", a.url).put("genTime", a.genTime)
                        .put("genTimeUtc", utc(a.genTime)).put("token", a.token))
                }
            })
            put("howToVerify", JSONArray().apply {
                if (v1) {
                    put("1. The entry: SHA-256 of the UTF-8 bytes of entry.leafText must equal entry.leafSha256. The leaf text (version 1) is: e1|entry uid|entry number|amount with 3 decimals|1 for gave or 0 for got|time in ms|note|item|quantity|unit as its saved key (bag, kg, ...)|payment reference|customer uid (a random id, not a name or phone).")
                } else {
                    put("1. The entry: SHA-256 of the UTF-8 bytes of entry.leafText must equal entry.leafSha256. The leaf text (version 2) is e2 followed by its fields, each written as |, its length in UTF-8 bytes, : and the field: entry uid, entry number, amount with 3 decimals, 1 for gave or 0 for got, time in ms, note, payment method, payment reference, 1 for a Qarz-e-Hasna loan or 0, 1 when the entry is in the bin or 0, customer uid (a random id, not a name or phone), the number of goods lines, then for each line: item, quantity, unit as its saved key (bag, kg, ...), rate, 1 for a free line or 0, batch uid (a random id of the supplier bill line it was sold from).")
                }
                put("2. The seal: base64-decode seal.leaves (32 bytes per entry, in the order the entries were written); the 32 bytes at position entry.indexInSeal must be entry.leafSha256.")
                if (v1) {
                    put("3. The digest: SHA-256 of the UTF-8 bytes of seal.digestHeader followed by the decoded leaves must equal seal.digest (lowercase hex). The header is: rk-seal-v1, prevDigest, sealedAt, fromId-toId, leafCount, auditHead, each followed by a newline.")
                } else {
                    put("3. The digest: SHA-256 of the UTF-8 bytes of seal.digestHeader followed by the decoded leaves must equal seal.digest (lowercase hex). The header is: rk-seal-v2, prevDigest, sealedAt, leafCount, auditHead, each followed by a newline.")
                }
                put("4. Each witness: base64-decode its token and save it as x.tsr. Read it with: openssl ts -reply -in x.tsr -token_in -text")
                put("5. Check its signature and its certificate with: openssl ts -verify -digest <seal.digest> -in x.tsr -token_in -CAfile <that TSA's CA certificate> (add -untrusted <its intermediate certificate> when it has one). Only this step shows the answer really came from that time service.")
                put("6. The chain: seal.prevDigest is the digest of the seal before; the book's own backup carries every seal.")
                put("7. The times: sealedAtByPhoneClock and madeAtByPhoneClock are the phone's own clock and prove nothing by themselves; each witness's genTime is read from its signed token.")
                put("On the phone, the app checked: the leaf is in the seal; the seal's digest is its header and leaves; each witness's answer is for this digest, its signature holds with the certificate the answer carries, and that certificate is marked for time-stamping and was valid at genTime. Whose certificate it is, the app does not know: step 5 checks that. This is evidence a court weighs, not a legal advanced electronic signature.")
            })
        }.toString(2)
    }

    private fun utc(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(ms))
}
