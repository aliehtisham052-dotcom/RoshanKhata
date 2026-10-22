package com.innovation313.roshankhata.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * A customer's own page (22 Oct 2026, the owner: "sirf us ka hisaab"): a
 * link the shop sends on WhatsApp that shows that one customer their balance
 * and, if the shop wants, the last three entries — nothing else of the book.
 *
 * Checks and balances, as agreed with the owner:
 *  - At most [MAX_ENTRIES] entries ever leave the device, whatever is chosen;
 *    the shop can only show less ([Mode.BALANCE]), never more. The server
 *    (web/khata/api.php) refuses anything bigger on its own as well.
 *  - Only customers the shop gave a link to are uploaded; a customer is sent
 *    again only when what their page shows changed ([SENT] keeps a hash).
 *  - Changes go together, every few minutes, in one request ([flush]).
 *  - The page is a ready file: opening it costs the server no database work.
 *  - Sealed on the device: the page's text is encrypted with a key that is
 *    only in the link (after the #, which a browser never sends to a
 *    server). The server keeps sealed bytes it cannot read.
 *
 * Closed until [base] is set (after the owner puts web/khata on his hosting).
 */
object CustomerLink {

    /** Where web/khata lives, e.g. "https://ehtisham.shop/khata/"; null keeps customer links closed. */
    @Volatile var base: String? = null

    const val MAX_ENTRIES = 3
    /** Customers' pages per request, as the server allows. */
    const val PER_CALL = 200
    private const val AAD = "rk-clink-v1"

    /** What a customer's page shows; the book's choice, [Mode.LAST3] unless the owner chose less. */
    enum class Mode(val key: String) {
        BALANCE("balance"), LAST3("last3");
        companion object { fun of(v: String?): Mode = values().firstOrNull { it.key == v } ?: LAST3 }
    }

    // Book settings (they travel with the book, so the phone and the computer share the same links).
    const val MODE = "clink_mode"
    /** This shop at the server: "<shop id>.<token>" (hex), made on the first link. */
    const val SHOP = "clink_shop"
    /** + party uid → "<link id>.<key>". */
    const val PREFIX = "clink:"
    /** + party uid → a hash of the page last uploaded. */
    const val SENT = "clinksent:"
    /** + link id → a link taken back, to be removed at the server. */
    const val GONE = "clinkgone:"

    data class Link(val id: String, val key: String)

    private val random = SecureRandom()
    private fun hex(n: Int): String = ByteArray(n).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }
    private fun b64url(b: ByteArray): String = B64.encode(b).replace('+', '-').replace('/', '_').trimEnd('=')
    private fun unb64url(s: String): ByteArray = B64.decode(s.replace('-', '+').replace('_', '/').let { it + "=".repeat((4 - it.length % 4) % 4) })

    fun parse(v: String?): Link? {
        val parts = v?.split('.') ?: return null
        if (parts.size != 2 || !Regex("[0-9a-f]{32}").matches(parts[0]) || parts[1].length !in 16..32) return null
        return Link(parts[0], parts[1])
    }

    suspend fun linkOf(dao: KhataDao, partyUid: String): Link? = parse(dao.settingValue(PREFIX + partyUid))

    /** The customer's link, made the first time it is asked for. */
    suspend fun create(dao: KhataDao, partyUid: String): Link {
        linkOf(dao, partyUid)?.let { return it }
        val link = Link(hex(16), b64url(ByteArray(16).also { random.nextBytes(it) }))
        dao.setSetting(PREFIX + partyUid, link.id + "." + link.key)
        return link
    }

    /** Takes a customer's link back: it stops opening once the server has heard (next [flush]). */
    suspend fun remove(dao: KhataDao, partyUid: String) {
        val link = linkOf(dao, partyUid) ?: return
        dao.setSetting(GONE + link.id, "1")
        dao.setSetting(PREFIX + partyUid, null)
        dao.setSetting(SENT + partyUid, null)
    }

    fun url(link: Link, root: String? = base): String? = root?.let { it.trimEnd('/') + "/#" + link.id + "." + link.key }

    /** One line of the page: when, how much, which way, and a few words (the note, else the goods). */
    data class Line(val at: Long, val amount: Double, val gave: Boolean, val words: String?)

    /** The page's text, before it is sealed. [balance] > 0: the customer owes the shop. */
    fun page(shop: String?, name: String, balance: Double, currency: String, updated: Long, lines: List<Line>, mode: Mode): JSONObject =
        JSONObject().apply {
            put("v", 1)
            put("s", shop.orEmpty().take(60))
            put("n", name.take(60))
            put("b", balance)
            put("c", currency.take(5))
            put("t", updated)
            if (mode == Mode.LAST3) put("e", JSONArray().apply {
                lines.take(MAX_ENTRIES).forEach { l ->
                    put(JSONObject().apply {
                        put("d", l.at); put("a", l.amount); put("g", l.gave)
                        l.words?.trim()?.takeIf { it.isNotEmpty() }?.let { put("x", it.take(40)) }
                    })
                }
            })
        }

    /** [plain] sealed with the link's key (AES-128-GCM, a fresh nonce), as the page's script opens it. */
    fun seal(key: String, plain: String): String {
        val nonce = ByteArray(12).also { random.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(unb64url(key), "AES"), GCMParameterSpec(128, nonce))
        c.updateAAD(AAD.toByteArray(Charsets.US_ASCII))
        return b64url(nonce + c.doFinal(plain.toByteArray(Charsets.UTF_8)))
    }

    /** What [seal] made, opened again (for tests and the computer's own preview). */
    fun open(key: String, sealed: String): String? = runCatching {
        val b = unb64url(sealed)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(unb64url(key), "AES"), GCMParameterSpec(128, b, 0, 12))
        c.updateAAD(AAD.toByteArray(Charsets.US_ASCII))
        String(c.doFinal(b, 12, b.size - 12), Charsets.UTF_8)
    }.getOrNull()

    private fun hash(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).copyOf(12).joinToString("") { "%02x".format(it) }

    /** The way out to the server; a test passes its own. */
    fun interface Http {
        fun post(url: String, body: ByteArray): Pair<Int, ByteArray>
    }

    val plain = Http { url, body ->
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 8000
            c.readTimeout = 30_000
            c.doOutput = true
            c.setRequestProperty("content-type", "application/json")
            c.setFixedLengthStreamingMode(body.size)
            c.outputStream.use { it.write(body) }
            val code = c.responseCode
            val bytes = (if (code in 200..299) c.inputStream else c.errorStream)?.use { s ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                while (true) { val n = s.read(buf); if (n < 0) break; out.write(buf, 0, n); if (out.size() > 1 shl 20) break }
                out.toByteArray()
            } ?: ByteArray(0)
            code to bytes
        } finally {
            c.disconnect()
        }
    }

    sealed interface Result {
        data class Done(val sent: Int, val removed: Int) : Result
        object Off : Result
        object Idle : Result
        object Unreachable : Result
        data class Refused(val why: String) : Result
    }

    /**
     * Every customer page that changed since it was last sent, and every link
     * taken back, in as few requests as the server allows. [shop] is the
     * shop's name as the page shows it.
     */
    suspend fun flush(dao: KhataDao, shop: String?, currency: String, http: Http = plain, root: String? = base): Result {
        val api = root?.trimEnd('/')?.plus("/api.php") ?: return Result.Off
        val settings = dao.allSettingsForBackup().associate { it.key to it.value }
        val links = settings.filterKeys { it.startsWith(PREFIX) }.mapNotNull { (k, v) -> parse(v)?.let { k.removePrefix(PREFIX) to it } }
        val gone = settings.keys.filter { it.startsWith(GONE) }.map { it.removePrefix(GONE) }
        if (links.isEmpty() && gone.isEmpty()) return Result.Idle
        val mode = Mode.of(settings[MODE])

        val parties = dao.allPartiesForBackup().filter { it.uid != null }.associateBy { it.uid!! }
        val balances = dao.partiesWithBalanceOnce().associateBy { it.id }
        val put = ArrayList<Triple<String, String, String>>() // party uid, link id, sealed page
        val sentHash = HashMap<String, String>()
        val drop = ArrayList<String>(gone)
        for ((uid, link) in links) {
            val p = parties[uid]
            if (p == null || p.isDeleted) { drop += link.id; dao.setSetting(PREFIX + uid, null); continue }
            val b = balances[p.id]
            val lines = if (mode == Mode.LAST3) dao.latestEntriesWithItems(p.id, MAX_ENTRIES).map { e ->
                val goods = e.items.mapNotNull { it.itemName?.takeIf { n -> n.isNotBlank() } }.joinToString(", ")
                Line(e.entry.timestamp, e.entry.amount, e.entry.isGiven, e.entry.note?.takeIf { it.isNotBlank() } ?: goods.ifBlank { null })
            } else emptyList()
            val text = page(shop, p.name, b?.balance ?: 0.0, currency, b?.lastActivity ?: 0L, lines, mode).toString()
            val h = hash(text)
            if (settings[SENT + uid] == h) continue
            put += Triple(uid, link.id, seal(link.key, text))
            sentHash[uid] = h
        }
        if (put.isEmpty() && drop.isEmpty()) return Result.Idle

        val creds = settings[SHOP]?.split('.')?.takeIf { it.size == 2 } ?: run {
            val made = listOf(hex(16), hex(32))
            dao.setSetting(SHOP, made.joinToString("."))
            made
        }
        var sent = 0
        var removed = 0
        try {
            val dropLeft = ArrayList(drop)
            for (chunk in put.chunked(PER_CALL).ifEmpty { listOf(emptyList()) }) {
                val drops = dropLeft.take(PER_CALL - chunk.size).also { dropLeft.removeAll(it.toSet()) }
                val body = JSONObject().apply {
                    put("shop", creds[0]); put("token", creds[1])
                    put("put", JSONArray().apply { chunk.forEach { (_, id, blob) -> put(JSONObject().put("id", id).put("blob", blob)) } })
                    put("drop", JSONArray().apply { drops.forEach { put(it) } })
                }
                val (code, bytes) = http.post(api, body.toString().toByteArray(Charsets.UTF_8))
                val o = runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }.getOrNull()
                if (code != 200 || o?.optBoolean("ok") != true) return Result.Refused(o?.optString("why")?.ifBlank { null } ?: "http $code")
                for ((uid, _, _) in chunk) { sentHash[uid]?.let { dao.setSetting(SENT + uid, it) }; sent++ }
                for (id in drops) { dao.setSetting(GONE + id, null); removed++ }
            }
        } catch (e: java.io.IOException) {
            return Result.Unreachable
        }
        return Result.Done(sent, removed)
    }
}
