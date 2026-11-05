package com.innovation313.roshankhata.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * One day's seal of the book (schema 44, [Schema44]): the khata entries with ids in (fromId, toId] — their leaf hashes
 * ([SealMath.leafHash]) concatenated in id order in [leaves] (null for the first seal, and for a day with no new entry
 * but a changed audit log) — the audit log's newest seal at the time ([auditHead]), the seal before's [prevDigest], and
 * the [digest] of all of it ([SealMath.digest]). [tokens] is a JSON object, witness name → {"token": base64 DER
 * TimeStampToken, "time": genTime ms}; a witness not in it has not stamped yet.
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
 * Kitab ki Muhar, the book's seal (4 Nov 2026, the owner's approved plan). Purpose: to show a customer in a dispute ("you
 * wrote this entry afterwards") that an entry was already in the book on a day, and that the book was not rewritten
 * since. Once a calendar day, while the owner keeps it on, the entries written since the last seal and the audit log's
 * head are sealed into one digest chained to the seal before, and three independent free RFC 3161 Time Stamp
 * Authorities stamp that digest ([Rfc3161]). Only the 32-byte digest leaves the device — nothing readable of the book.
 *
 * What it is not, said plainly (and on the help screen): it is not a legal "advanced electronic signature"; a court
 * weighs it as evidence. Entries written before the seal was turned on are not individually provable. The app checks
 * each stamp's form, imprint and nonce; the TSA's signature is checked with OpenSSL from the proof file.
 *
 * Shared, pure JVM: the phone calls [daily] from where it takes the day's safety copy; the computer can call it the same
 * way later. The switch is per book ([SWITCH], a book setting); off by default.
 */
object BookSeals {

    /** The book setting: "1" while the owner keeps the seal on; unset = off. */
    const val SWITCH = "book_seal"

    /** A witness that has not stamped a seal within this long after it was made is given up. */
    const val RETRY_MS = 7L * 24 * 60 * 60 * 1000

    /** Between two requests to one TSA (Sectigo asks scripts to wait 15 s or more). */
    const val GAP_MS = 15_000L

    /** Entries read a page of ids at a time (lesson 70). */
    const val PAGE = 400

    class Witness(val name: String, val url: String)

    val WITNESSES = listOf(
        Witness("FreeTSA", "https://freetsa.org/tsr"),
        Witness("Sectigo", "http://timestamp.sectigo.com"),
        Witness("DigiCert", "http://timestamp.digicert.com")
    )

    /** One witness's stamp: the token as base64 DER and the TSA's genTime. */
    class Stamp(val token: String, val time: Long)

    private val sealing = Mutex()
    private val witnessing = Mutex()

    suspend fun isOn(dao: KhataDao): Boolean = BookSettings.isOn(dao.settingValue(SWITCH))

    /** The stamps a seal holds, by witness name; a row whose JSON does not read holds none. */
    fun stamps(seal: BookSeal): Map<String, Stamp> {
        val text = seal.tokens ?: return emptyMap()
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

    fun tokensJson(stamps: Map<String, Stamp>): String = JSONObject().apply {
        for ((k, s) in stamps) put(k, JSONObject().put("token", s.token).put("time", s.time))
    }.toString()

    /**
     * The switch on: the setting written and, when the book has never been sealed, the first seal at once — no leaves,
     * fromId = toId = the newest entry id: sealing starts from today. Returns the new first seal, if one was made.
     * Turned on again after an off, the chain goes on from the last seal (the entries since come in the next one).
     */
    suspend fun turnOn(dao: KhataDao, now: Long = System.currentTimeMillis()): BookSeal? = sealing.withLock {
        dao.setSetting(SWITCH, "1")
        if (dao.latestSeal() == null) makeFirst(dao, now) else null
    }

    /** The switch off: no more seals or requests; the seals made stay (they are the proofs). */
    suspend fun turnOff(dao: KhataDao) {
        dao.setSetting(SWITCH, null)
    }

    private suspend fun makeFirst(dao: KhataDao, now: Long): BookSeal {
        val max = dao.maxEntryId()
        val head = dao.lastAuditHash()
        val digest = SealMath.digest(SealMath.header(null, now, max, max, 0, head), null)
        val seal = BookSeal(sealedAt = now, fromId = max, toId = max, leafCount = 0, auditHead = head, digest = digest)
        return seal.copy(id = dao.insertSeal(seal))
    }

    /**
     * Today's seal, when it is due: the switch on, no seal yet today (by the device's calendar day), and something new
     * since the last — entries past its toId, or another audit head. Null when nothing was made. [alive] is asked
     * before the write (the phone: the book is still the open one).
     */
    suspend fun sealIfDue(dao: KhataDao, now: Long = System.currentTimeMillis(), alive: () -> Boolean = { true }): BookSeal? =
        sealing.withLock {
            if (!isOn(dao)) return@withLock null
            val last = dao.latestSeal() ?: return@withLock if (alive()) makeFirst(dao, now) else null
            // Once a calendar day; a clock put back behind the last seal waits for it.
            if (now < last.sealedAt || DayMath.dayNumber(last.sealedAt) == DayMath.dayNumber(now)) return@withLock null
            val max = dao.maxEntryId()
            val head = dao.lastAuditHash()
            if (max <= last.toId && head == last.auditHead) return@withLock null
            val from = last.toId
            val to = maxOf(max, from)
            val out = java.io.ByteArrayOutputStream()
            var count = 0
            var after = from
            while (after < to) {
                val page = dao.sealLinesPage(after, to, PAGE)
                for (line in page) { out.write(SealMath.leafHash(SealMath.leafText(line))); count++ }
                if (page.size < PAGE) break
                after = page.last().id
            }
            val leaves = if (count == 0) null else out.toByteArray()
            val digest = SealMath.digest(SealMath.header(last.digest, now, from, to, count, head), leaves)
            if (!alive()) return@withLock null
            val seal = BookSeal(
                sealedAt = now, fromId = from, toId = to, leafCount = count, leaves = leaves,
                auditHead = head, prevDigest = last.digest, digest = digest
            )
            seal.copy(id = dao.insertSeal(seal))
        }

    /**
     * Asks every witness that has not stamped a seal of the last [RETRY_MS] to stamp it, one request at a time, [GAP_MS]
     * apart to one TSA. A failure leaves that witness waiting for another day; after [RETRY_MS] it is given up quietly.
     * Nothing is asked while the kill switch says so ([halted], live.json's `"seal": false`). Returns the stamps added.
     */
    suspend fun witness(
        dao: KhataDao,
        now: Long = System.currentTimeMillis(),
        http: Rfc3161.Http = Rfc3161.plain,
        halted: () -> Boolean = { LiveControl.sealHalted() },
        alive: () -> Boolean = { true },
        pause: suspend (Long) -> Unit = { delay(it) }
    ): Int = witnessing.withLock {
        if (!isOn(dao)) return@withLock 0
        val pending = dao.sealsSince(now - RETRY_MS).filter { s -> s.sealedAt <= now && WITNESSES.any { it.name !in stamps(s) } }
        if (pending.isEmpty() || halted()) return@withLock 0
        var added = 0
        for (w in WITNESSES) {
            var asked = false
            for (s in pending) {
                if (w.name in stamps(s)) continue
                if (!alive()) return@withLock added
                if (asked) pause(GAP_MS)
                asked = true
                val digest = runCatching { SealMath.unhex(s.digest) }.getOrNull() ?: continue
                val reply = Rfc3161.stamp(http, w.url, digest)
                if (reply !is Rfc3161.Reply.Granted || !alive()) continue
                val fresh = dao.sealById(s.id) ?: continue
                val all = stamps(fresh) + (w.name to Stamp(B64.encode(reply.token), reply.genTime))
                dao.setSealTokens(s.id, tokensJson(all))
                added++
            }
        }
        added
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

    class Status(val on: Boolean, val last: BookSeal?, val stamped: Int, val of: Int = WITNESSES.size)

    suspend fun status(dao: KhataDao): Status {
        val last = dao.latestSeal()
        return Status(isOn(dao), last, last?.let { s -> WITNESSES.count { it.name in stamps(s) } } ?: 0)
    }

    /** Where one entry stands. */
    sealed class Proof {
        /** In [seal] at [index]: its leaf text and hash as the seal holds them. */
        class Sealed(val seal: BookSeal, val leafText: String, val leafHex: String, val index: Int) : Proof()
        /** In [seal]'s range, but its leaf is not there any more: edited after the seal (the audit log says how). */
        class Changed(val seal: BookSeal) : Proof()
        /** Written after the newest seal: it goes into the next one. */
        object NotYet : Proof()
        /** Written before the seal was first turned on. */
        object Before : Proof()
        /** No such entry. */
        object Unknown : Proof()
    }

    suspend fun proofOf(dao: KhataDao, entryId: Long): Proof {
        val line = dao.sealLineOf(entryId) ?: return Proof.Unknown
        val seal = dao.sealCovering(entryId)
            ?: return if (dao.sealReaching(entryId) != null) Proof.Before else Proof.NotYet
        val text = SealMath.leafText(line)
        val leaf = SealMath.leafHash(text)
        val at = SealMath.indexOf(seal.leaves, leaf)
        return if (at >= 0) Proof.Sealed(seal, text, SealMath.hex(leaf), at) else Proof.Changed(seal)
    }

    /**
     * The proof file a customer, a lawyer or a court's expert can check without the app: the entry's leaf text and hash,
     * the seal's fields (leaves as base64), each witness's token, and the steps with OpenSSL. English, on purpose: it
     * is read by whoever checks it, wherever.
     */
    fun proofFile(p: Proof.Sealed, entryNumber: String, madeAt: Long = System.currentTimeMillis()): String {
        val s = p.seal
        val header = SealMath.header(s.prevDigest, s.sealedAt, s.fromId, s.toId, s.leafCount, s.auditHead)
        val stamps = stamps(s)
        return JSONObject().apply {
            put("format", "roshan-khata-seal-proof-v1")
            put("madeAt", madeAt)
            put("entry", JSONObject().apply {
                put("entryNumber", entryNumber)
                put("leafText", p.leafText)
                put("leafSha256", p.leafHex)
                put("indexInSeal", p.index)
            })
            put("seal", JSONObject().apply {
                put("id", s.id); put("sealedAt", s.sealedAt); put("fromId", s.fromId); put("toId", s.toId)
                put("leafCount", s.leafCount)
                put("leaves", s.leaves?.let { B64.encode(it) } ?: JSONObject.NULL)
                put("auditHead", s.auditHead ?: JSONObject.NULL)
                put("prevDigest", s.prevDigest ?: JSONObject.NULL)
                put("digest", s.digest)
                put("digestHeader", header)
            })
            put("witnesses", JSONArray().apply {
                for (w in WITNESSES) {
                    val st = stamps[w.name] ?: continue
                    put(JSONObject().put("name", w.name).put("url", w.url).put("genTime", st.time).put("token", st.token))
                }
            })
            put("howToVerify", JSONArray().apply {
                put("1. The entry: SHA-256 of the UTF-8 bytes of entry.leafText must equal entry.leafSha256.")
                put("2. The seal: base64-decode seal.leaves (32 bytes per entry, in id order); the 32 bytes at position entry.indexInSeal must be entry.leafSha256.")
                put("3. The digest: SHA-256 of the UTF-8 bytes of seal.digestHeader followed by the decoded leaves must equal seal.digest (lowercase hex). The header is: rk-seal-v1, prevDigest, sealedAt, fromId-toId, leafCount, auditHead, each followed by a newline.")
                put("4. Each witness: base64-decode its token and save it as x.tsr. Read it with: openssl ts -reply -in x.tsr -token_in -text")
                put("5. Check its signature with: openssl ts -verify -digest <seal.digest> -in x.tsr -token_in -CAfile <that TSA's CA certificate>")
                put("6. The chain: seal.prevDigest is the digest of the seal before; the book's own backup carries every seal.")
                put("The app itself checks each stamp's form, its fingerprint and its nonce only; the signature is checked with OpenSSL as above. This is evidence a court weighs, not a legal advanced electronic signature.")
            })
        }.toString(2)
    }
}
