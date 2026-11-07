package com.innovation313.roshankhata.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * A customer's own page (22 Oct 2026, the owner: "sirf us ka hisaab"): a
 * link the shop sends on WhatsApp that shows that one customer their balance
 * and, if the shop wants, the last three entries — nothing else of the book.
 *
 * Checks and balances, as agreed with the owner:
 *  - At most [MAX_ENTRIES] entries ever leave the device, whatever is chosen;
 *    the shop can only show less ([Mode.BALANCE]), never more. Only the
 *    goods of an entry go with it, never the shop's own note.
 *  - Only customers the shop gave a link to are uploaded; a page goes again
 *    when what it shows changed, and once a month so the server keeps it.
 *  - Changes go together, every few minutes, in as few requests as can be.
 *  - The page is a ready file: opening it costs the server no database work.
 *  - Sealed on the device: the page's text is encrypted with a key that is
 *    only in the link (after the #, which a browser never sends to a
 *    server). The server keeps sealed bytes it cannot read.
 *
 * Made right after the review of the same day:
 *  - The page's text is written the same way, byte for byte, on the phone and
 *    the computer (fixed key order, amounts rounded to the currency's own
 *    decimals, the shop's name, currency and language from the book), so two
 *    devices keeping one book never send the same page back and forth.
 *  - What this device sent is its own note ([Place]), kept per link, never in
 *    the book or a backup: a restored book sends every page again, and a new
 *    link for the same customer is always sent.
 *  - The shop's pass, and each customer's link, come from the book's live
 *    sync key when the book syncs: two devices that make them while apart
 *    make the same ones, and never orphan a page.
 *  - One copy of the book writes the pages (its writer, [writer]); another
 *    copy — a restored phone, a book copied to a second business — is told
 *    [Result.NotHere] and claims them only when the owner says so.
 *  - A link is sent before it can be shared ([share]); a customer purged for
 *    good has his page taken back (kept as [GONE] until the server confirms);
 *    a customer in the bin gets a page that says only "paused".
 *  - What the server refused is not marked as sent: a page taken by another
 *    shop or already taken back costs the customer his link (a new one is
 *    made on asking), and a full server is tried again.
 *
 * After the security review of 7 Nov 2026:
 *  - The seal names its link ("rk-clink-v2|" + id), and the sealed text
 *    carries a counter that only grows ("q", this device's note per link):
 *    a reader keeps the highest it saw and can tell an older page served
 *    again from a newer one. The sealed text is padded to one of a few sizes.
 *  - A link stopped (or refused as no longer free) is never made again on
 *    this device, though a book put back from an older copy names it.
 *  - Another copy holding the pages ([Result.NotHere]) is noted for the
 *    owner ([otherCopy]); the owner's word to take over is kept until a
 *    request carries it.
 *
 * Closed until [base] is set (after the owner puts web/khata on his hosting).
 */
object CustomerLink {

    /**
     * Where web/khata lives; null keeps customer links closed. The shop's own subdomain on the owner's hosting
     * (7 Nov 2026, the owner's choice of 21 Oct): served as plain files, so a customer's look costs no relay
     * request and no paid credit — live sync's relays are never touched by it.
     */
    @Volatile var base: String? = HOME

    /** The customer pages' home, and the host the app's links open from (App Links, MeraKhata). */
    const val HOST = "khata.ehtisham.shop"
    const val HOME = "https://$HOST/"

    const val MAX_ENTRIES = 3
    /** Pages put or taken back per request, as the server allows. */
    const val PER_CALL = 200
    /** Every page goes again at least this often; the server lets a page go when it was not sent for 180 days. */
    const val REFRESH_DAYS = 30L
    /** The page's own version: 3 is sealed under its link's id and carries its counter and padding. */
    const val VERSION = 3
    /** The seal of a page made before 7 Nov 2026, which named no link: still opened, never made. */
    private const val AAD_V1 = "rk-clink-v1"
    /** The seal of a page now: it opens only under its own link's id. */
    private const val AAD_V2 = "rk-clink-v2|"
    /** The sizes a sealed page's text is padded to, in bytes: its length then says nothing of the name or the figures. */
    private val SIZES = intArrayOf(512, 1024, 2048)
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /**
     * What a customer's page shows; the book's choice. [Mode.BALANCE] unless the owner chose more (7 Nov 2026,
     * the owner: the customer sees his total balance — a few hundred bytes a page, nothing of his goods on a
     * phone the family also holds).
     */
    enum class Mode(val key: String) {
        BALANCE("balance"), LAST3("last3");
        companion object { fun of(v: String?): Mode = values().firstOrNull { it.key == v } ?: BALANCE }
    }

    // Book settings (they travel with the book, so the phone and the computer share the same links).
    const val MODE = "clink_mode"
    /** This shop at the server: "<shop id>.<token>" (32 and 64 hex). */
    const val SHOP = "clink_shop"
    /** + party uid → "<link id>.<key>". */
    const val PREFIX = "clink:"
    /** + link id → a link taken back, to be removed at the server. */
    const val GONE = "clinkgone:"
    /** + party uid → how many of this customer's links were taken back: the next is never an old one. */
    const val GEN = "clinkgen:"
    /** How pages look ([Look]): the shop's name, the currency sign, the page's language. */
    const val TITLE = "clink_title"
    const val MONEY = "clink_money"
    const val LANG = "clink_lang"

    /**
     * What this device keeps for itself, outside the book: the phone's
     * preferences, the computer's own file. Never in a backup, never synced.
     */
    interface Place {
        fun get(key: String): String?
        fun put(key: String, value: String?)
    }

    data class Link(val id: String, val key: String)

    /** How pages look: the shop's name, the currency sign it prints, the language of the page's words ([pageLanguage]). */
    data class Look(val title: String?, val currency: String, val language: String)

    /** The page's own words, in the app's nine languages: the app's language code made the page's. */
    fun pageLanguage(app: String?): String = when (val l = app?.trim().orEmpty()) {
        "b+ur+Latn", "ur-Latn", "ur_Latn", "ur-latn" -> "ur-Latn"
        "in", "id" -> "id"
        "en", "ur", "sd", "ar", "fa", "hi", "bn" -> l
        else -> "en"
    }

    private val random = SecureRandom()
    private val lock = Mutex()
    private val ID = Regex("[0-9a-f]{32}")
    private val KEY = Regex("[A-Za-z0-9_-]{22}")
    private val SHOP_CREDS = Regex("([0-9a-f]{32})\\.([0-9a-f]{64})")
    private fun hexOf(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }
    private fun hex(n: Int): String = hexOf(ByteArray(n).also { random.nextBytes(it) })
    private fun b64url(b: ByteArray): String = B64.encode(b).replace('+', '-').replace('/', '_').trimEnd('=')
    private fun unb64url(s: String): ByteArray = B64.decode(s.replace('-', '+').replace('_', '/').let { it + "=".repeat((4 - it.length % 4) % 4) })

    /** HMAC-SHA256 of [label] under the book's live sync key (64 hex). */
    private fun derive(bookKey: String, label: String): ByteArray {
        require(Regex("[0-9a-fA-F]{64}").matches(bookKey)) { "not a book key" }
        val key = ByteArray(32) { i -> bookKey.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }
        return mac.doFinal(label.toByteArray(Charsets.UTF_8))
    }

    /** A link as the book keeps it: a 32-hex id and a 16-byte key (22 characters); anything else is no link. */
    fun parse(v: String?): Link? {
        val parts = v?.split('.') ?: return null
        if (parts.size != 2 || !ID.matches(parts[0]) || !KEY.matches(parts[1])) return null
        if (runCatching { unb64url(parts[1]).size }.getOrNull() != 16) return null
        return Link(parts[0], parts[1])
    }

    suspend fun linkOf(dao: KhataDao, partyUid: String): Link? = parse(dao.settingValue(PREFIX + partyUid))

    /** The shop's name, currency and language for pages, set where the book has none yet. */
    private suspend fun keepLook(dao: KhataDao, look: Look) {
        if (dao.settingValue(MONEY) == null) dao.setSetting(MONEY, look.currency.take(5))
        if (dao.settingValue(LANG) == null) dao.setSetting(LANG, pageLanguage(look.language))
        // An empty name is kept as one: every device then shows the same page, whatever its own profile says.
        if (dao.settingValue(TITLE) == null) dao.setSetting(TITLE, clip(look.title?.trim().orEmpty(), 60))
    }

    /** The owner changes how pages look (name, currency, language): every page goes again with it. */
    suspend fun setLook(dao: KhataDao, look: Look) = lock.withLock {
        dao.setSetting(MONEY, look.currency.take(5))
        dao.setSetting(LANG, pageLanguage(look.language))
        dao.setSetting(TITLE, clip(look.title?.trim().orEmpty(), 60))
    }

    /**
     * The customer's link, made the first time it is asked for. [bookKey]: the live sync key while the book syncs.
     * [place]: this device's notes, so a link stopped here is never made again.
     */
    suspend fun create(dao: KhataDao, partyUid: String, look: Look, bookKey: String? = null, place: Place? = null): Link = lock.withLock {
        createLocked(dao, partyUid, look, bookKey, place)
    }

    private suspend fun createLocked(dao: KhataDao, partyUid: String, look: Look, bookKey: String?, place: Place?): Link {
        linkOf(dao, partyUid)?.let { old ->
            if (place?.get(stopKey(old.id)) == null) return old
            // Stopped here once: a book put back from an older copy names it again. Its removal goes with the next send.
            dao.setSetting(GONE + old.id, "1")
            nextGen(dao, partyUid)
        }
        keepLook(dao, look)
        var gen = dao.settingValue(GEN + partyUid)?.toIntOrNull() ?: 0
        val link = if (bookKey != null) {
            // The same customer linked on two devices while apart: the same link, not two. One this device stopped,
            // or the server refused as no longer free, is passed over (7 Nov 2026): a book put back from an older
            // copy counts its links lower than they went, and would make a stopped one again.
            var made = derived(bookKey, partyUid, gen)
            var passed = 0
            while (place != null && place.get(stopKey(made.id)) != null && passed < 1000) {
                gen++; passed++
                made = derived(bookKey, partyUid, gen)
            }
            if (passed > 0) dao.setSetting(GEN + partyUid, gen.toString())
            made
        } else {
            Link(hex(16), b64url(ByteArray(16).also { random.nextBytes(it) }))
        }
        dao.setSetting(PREFIX + partyUid, link.id + "." + link.key)
        return link
    }

    private fun derived(bookKey: String, partyUid: String, gen: Int): Link =
        Link(hexOf(derive(bookKey, "rk-clink-id:$partyUid:$gen").copyOf(16)), b64url(derive(bookKey, "rk-clink-key:$partyUid:$gen").copyOf(16)))

    /**
     * Takes a customer's link back: it stops opening once the server has heard (next [flush]; the book's
     * [GONE] mark goes when it has). [place]: this device's notes, where the link is kept as stopped for good.
     * The link taken back, or null when he had none.
     */
    suspend fun remove(dao: KhataDao, partyUid: String, place: Place? = null): Link? = lock.withLock { removeLocked(dao, partyUid, place) }

    private suspend fun removeLocked(dao: KhataDao, partyUid: String, place: Place?): Link? {
        val link = linkOf(dao, partyUid)
        // The removal is written before the link goes, so a crash between them still takes the page down.
        link?.let { dao.setSetting(GONE + it.id, "1"); place?.put(stopKey(it.id), "1") }
        nextGen(dao, partyUid)
        dao.setSetting(PREFIX + partyUid, null)
        return link
    }

    /** The customer's next link is never an old one. */
    private suspend fun nextGen(dao: KhataDao, partyUid: String) =
        dao.setSetting(GEN + partyUid, ((dao.settingValue(GEN + partyUid)?.toIntOrNull() ?: 0) + 1).toString())

    fun url(link: Link, root: String? = base): String? = root?.let { it.trimEnd('/') + "/#" + link.id + "." + link.key }

    /** One line of the page: when, how much, which way, whether goods went with it, and which. */
    data class Line(val at: Long, val amount: Double, val gave: Boolean, val goods: Boolean, val words: String?)

    // ---------------------------------------------------------------- the page's text

    /** [s] cut to [n] characters without splitting a letter written as two (an emoji, some scripts). */
    private fun clip(s: String, n: Int): String {
        if (s.length <= n) return s
        val end = if (Character.isHighSurrogate(s[n - 1])) n - 1 else n
        return s.substring(0, end)
    }

    private fun StringBuilder.str(s: String): StringBuilder {
        append('"')
        for (ch in s) when {
            ch == '"' -> append("\\\"")
            ch == '\\' -> append("\\\\")
            ch < ' ' -> append("\\u").append(String.format(java.util.Locale.ROOT, "%04x", ch.code))
            else -> append(ch)
        }
        return append('"')
    }

    /** A figure to the currency's own decimals, written plainly ("5400", "10.55"): the same on every device. */
    fun figure(v: Double, decimals: Int): String {
        if (!v.isFinite()) return "0"
        val s = BigDecimal.valueOf(v).setScale(decimals.coerceIn(0, 3), RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        return if (s == "-0") "0" else s
    }

    /** The month a page belongs to: a page goes again when it changes. */
    fun bucket(now: Long): Long = now / (REFRESH_DAYS * DAY_MS)

    /**
     * The page's text, before it is sealed: one JSON object, its keys in one
     * order, its figures in one shape. [balance] > 0: the customer owes the shop.
     */
    fun page(
        look: Look, name: String, supplier: Boolean, balance: Double, updated: Long,
        lines: List<Line>, mode: Mode, now: Long
    ): String {
        val k = Currency.decimals(look.currency)
        return buildString {
            append("{\"v\":").append(VERSION).append(",\"s\":").str(clip(look.title.orEmpty(), 60))
            append(",\"n\":").str(clip(name, 60))
            append(",\"p\":").str(if (supplier) "s" else "c")
            append(",\"b\":").append(figure(balance, k))
            append(",\"c\":").str(look.currency.take(5))
            append(",\"k\":").append(k)
            append(",\"l\":").str(pageLanguage(look.language))
            append(",\"t\":").append(updated)
            append(",\"r\":").append(bucket(now))
            if (mode == Mode.LAST3) {
                append(",\"e\":[")
                lines.take(MAX_ENTRIES).forEachIndexed { i, l ->
                    if (i > 0) append(',')
                    append("{\"d\":").append(l.at)
                    append(",\"a\":").append(figure(l.amount, k))
                    append(",\"g\":").append(if (l.gave) 1 else 0)
                    append(",\"m\":").append(if (l.goods) 1 else 0)
                    l.words?.trim()?.takeIf { it.isNotEmpty() }?.let { append(",\"x\":").str(clip(it, 40)) }
                    append('}')
                }
                append(']')
            }
            append('}')
        }
    }

    /** A customer in the bin: a page that says only that it is paused, in the page's language. */
    fun pausedPage(look: Look, now: Long): String = buildString {
        append("{\"v\":").append(VERSION).append(",\"z\":1,\"l\":").str(pageLanguage(look.language))
        append(",\"r\":").append(bucket(now)).append('}')
    }

    /**
     * What is sealed: the page's text with its counter [seq] ("q": it only grows, so a reader can tell an older
     * page from a newer one) and spaces ("_") to the first of [SIZES] it fits, so the sealed length says nothing of
     * the name, the shop or the figures. The page's own text, which says whether it changed, stays as it was.
     */
    fun stamped(text: String, seq: Long): String {
        require(text.startsWith("{") && text.endsWith("}")) { "not a page" }
        val head = text.dropLast(1) + ",\"q\":" + seq + ",\"_\":\""
        val used = head.toByteArray(Charsets.UTF_8).size + 2
        val size = SIZES.firstOrNull { it >= used } ?: used
        return head + " ".repeat(size - used) + "\"}"
    }

    /** [plain] sealed with [link]'s key (AES-128-GCM, a fresh nonce) and bound to its id, as the page's script opens it. */
    fun seal(link: Link, plain: String): String {
        val raw = unb64url(link.key)
        require(raw.size == 16) { "a link key is 16 bytes" }
        val nonce = ByteArray(12).also { random.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(raw, "AES"), GCMParameterSpec(128, nonce))
        c.updateAAD((AAD_V2 + link.id).toByteArray(Charsets.US_ASCII))
        return b64url(nonce + c.doFinal(plain.toByteArray(Charsets.UTF_8)))
    }

    /**
     * What [seal] made, opened again under [link]'s id; a page sealed before 7 Nov 2026 (no id in its seal) opens
     * too. Null when it does not open with this link. The customer's app reads pages through it ([CustomerPage]).
     */
    fun open(link: Link, sealed: String): String? {
        val b = runCatching { unb64url(sealed) }.getOrNull() ?: return null
        if (b.size < 12 + 16) return null
        return openWith(link.key, b, AAD_V2 + link.id) ?: openWith(link.key, b, AAD_V1)
    }

    private fun openWith(key: String, b: ByteArray, aad: String): String? = runCatching {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(unb64url(key), "AES"), GCMParameterSpec(128, b, 0, 12))
        c.updateAAD(aad.toByteArray(Charsets.US_ASCII))
        String(c.doFinal(b, 12, b.size - 12), Charsets.UTF_8)
    }.getOrNull()

    private fun hash(linkId: String, text: String): String =
        hexOf(MessageDigest.getInstance("SHA-256").digest((linkId + "\n" + text).toByteArray(Charsets.UTF_8)).copyOf(12))

    // ---------------------------------------------------------------- the device's own notes

    private fun sentKey(linkId: String) = "clinksent|$linkId"
    /** The counter this device last sealed a link's page with ([stamped]). */
    private fun seqKey(linkId: String) = "clinkq|$linkId"
    /** A link stopped on this device, or refused by the server as no longer free: never made or sent again here. */
    private fun stopKey(linkId: String) = "clinkstop|$linkId"
    /** The owner's word that this copy writes the book's pages, kept until a request carries it. */
    private fun claimKey(scope: String) = "clinkclaim|$scope"
    /** The last request found another copy of the book writing its pages ([Result.NotHere]). */
    private fun otherKey(scope: String) = "clinkother|$scope"

    /**
     * True while another copy of this book writes its customers' pages and this one has not taken over (the
     * background send found it; 7 Nov 2026, the review's L5): the owner is told where links are sent, with the
     * way to take over ([flush] with claim = true).
     */
    fun otherCopy(place: Place, scope: String): Boolean = place.get(otherKey(scope)) != null

    /**
     * Which copy of the book writes the pages. While the book syncs, the same
     * on every device that keeps it (from its key); otherwise this device's
     * own for this book ([scope]: the book's file).
     */
    fun writer(place: Place, scope: String, bookKey: String?): String =
        bookKey?.let { hexOf(derive(it, "rk-clink-writer").copyOf(16)) }
            ?: place.get("clinkw|$scope")?.takeIf { ID.matches(it) }
            ?: hex(16).also { place.put("clinkw|$scope", it) }

    // ---------------------------------------------------------------- the server

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
        /**
         * [sent] pages written, [removed] taken back; [lost] links the server
         * would not take (another shop's page, or one taken back already: the
         * customer needs a new link); [waiting] tried again next time (a full
         * or low server).
         */
        data class Done(val sent: Int, val removed: Int, val lost: Int = 0, val waiting: Int = 0) : Result
        object Off : Result
        object Idle : Result
        object Unreachable : Result
        /** Another copy of this book writes these pages; [flush] with claim = true once the owner says so. */
        object NotHere : Result
        data class Refused(val why: String) : Result
    }

    private class Put(val partyUid: String, val link: Link, val blob: String, val hash: String)

    /**
     * Every customer page that changed since this device last sent it, every
     * page due its monthly refresh, and every link taken back, in as few
     * requests as the server allows.
     *
     * [place] and [scope] are this device's own notes for this book (its
     * file's name); [bookKey] the live sync key while the book syncs;
     * [look] how pages look where the book has not said; [claim] the owner's
     * word that this copy of the book writes the pages from now on (kept
     * until a request carries it, should nothing be due now).
     */
    suspend fun flush(
        dao: KhataDao, place: Place, scope: String, look: Look,
        bookKey: String? = null, claim: Boolean = false,
        http: Http = plain, root: String? = base, now: Long = System.currentTimeMillis()
    ): Result = lock.withLock { flushLocked(dao, place, scope, look, bookKey, claim, http, root, now, only = null) }

    /** [refusals]: why the server would not take a page now (link id to its word: "full", "later", "disk"…). */
    private suspend fun flushLocked(
        dao: KhataDao, place: Place, scope: String, fallback: Look,
        bookKey: String?, claim: Boolean, http: Http, root: String?, now: Long, only: String?,
        refusals: MutableMap<String, String>? = null
    ): Result {
        val api = root?.trimEnd('/')?.plus("/api.php") ?: return Result.Off
        if (claim) {
            // The owner has said: this copy writes. Kept until a request carries it; the other copy is no longer news.
            place.put(claimKey(scope), "1")
            place.put(otherKey(scope), null)
        }
        val settings = dao.allSettingsForBackup().associate { it.key to it.value }
        val links = settings.filterKeys { it.startsWith(PREFIX) }
            .mapNotNull { (k, v) -> parse(v)?.let { k.removePrefix(PREFIX) to it } }
            .filter { only == null || it.first == only }
        val gone = if (only != null) emptyList() else settings.keys.filter { it.startsWith(GONE) }.map { it.removePrefix(GONE) }.filter { ID.matches(it) }
        if (links.isEmpty() && gone.isEmpty()) return Result.Idle
        val look = Look(settings[TITLE] ?: fallback.title, settings[MONEY] ?: fallback.currency, settings[LANG] ?: fallback.language)
        val mode = Mode.of(settings[MODE])

        val parties = dao.allPartiesForBackup().filter { it.uid != null }.associateBy { it.uid!! }
        val balances = dao.partiesWithBalanceOnce().associateBy { it.id }
        val puts = ArrayList<Put>()
        val drops = LinkedHashSet<String>(gone)
        for ((uid, link) in links) {
            val p = parties[uid]
            // Stopped on this device before: a book put back from an older copy names the link again (7 Nov 2026).
            val stopped = place.get(stopKey(link.id)) != null
            if (p == null || stopped) {
                // Purged for good, or stopped: the page goes. The removal is kept in the book until the server confirms it.
                dao.setSetting(GONE + link.id, "1")
                dao.setSetting(PREFIX + uid, null)
                if (p != null) nextGen(dao, uid)
                place.put(sentKey(link.id), null)
                drops += link.id
                continue
            }
            val text = if (p.isDeleted) pausedPage(look, now) else {
                val lines = if (mode == Mode.LAST3) latest(dao, p.id) else emptyList()
                val b = balances[p.id]
                page(look, p.name, !p.isCustomer, b?.balance ?: 0.0, b?.lastActivity ?: 0L, lines, mode, now)
            }
            val h = hash(link.id, text)
            if (place.get(sentKey(link.id)) == h) continue
            // The counter only grows on this device, whatever its clock or an older copy of the book says.
            val seq = maxOf(now, (place.get(seqKey(link.id))?.toLongOrNull() ?: 0L) + 1)
            // One link whose key cannot seal (a damaged setting) is left out; the others still go.
            val blob = runCatching { seal(link, stamped(text, seq)) }.getOrNull() ?: continue
            place.put(seqKey(link.id), seq.toString())
            puts += Put(uid, link, blob, h)
        }
        if (puts.isEmpty() && drops.isEmpty()) return Result.Idle

        val creds = shop(dao, bookKey)
        val me = writer(place, scope, bookKey)
        val last = place.get("clinklast|$scope")
        // A shared writer (a syncing book) or this device's own that changed (sync turned on or off) takes the
        // pages over by itself; any other copy waits for the owner's word (kept until a request carries it).
        var claimNow = place.get(claimKey(scope)) != null || bookKey != null || (last != null && last != me)

        var sent = 0; var removed = 0; var lost = 0; var waiting = 0
        val items: List<Any> = puts + drops.toList()
        try {
            for (chunk in items.chunked(PER_CALL)) {
                val chunkPuts = chunk.filterIsInstance<Put>()
                val chunkDrops = chunk.filterIsInstance<String>()
                val body = JSONObject().apply {
                    put("shop", creds.first); put("token", creds.second); put("writer", me)
                    if (claimNow) put("claim", true)
                    put("put", JSONArray().apply { chunkPuts.forEach { put(JSONObject().put("id", it.link.id).put("blob", it.blob)) } })
                    put("drop", JSONArray().apply { chunkDrops.forEach { put(it) } })
                }
                val (code, bytes) = http.post(api, body.toString().toByteArray(Charsets.UTF_8))
                val o = runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }.getOrNull()
                val why = o?.optString("why")?.ifBlank { null }
                if (code == 409 && why == "writer") {
                    // Another copy of the book writes these pages: noted for the owner until a request goes through.
                    place.put(otherKey(scope), "1")
                    return Result.NotHere
                }
                if (code != 200 || o?.optBoolean("ok") != true) {
                    // What went before this request stays sent; the rest waits for the next flush.
                    return if (sent + removed + lost > 0) Result.Done(sent, removed, lost, items.size - sent - removed - lost)
                    else Result.Refused(why ?: "http $code")
                }
                place.put("clinklast|$scope", me)
                place.put(claimKey(scope), null)
                place.put(otherKey(scope), null)
                claimNow = false
                val refused = HashMap<String, String>()
                o.optJSONArray("refused")?.let { arr ->
                    for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { r -> r.optString("id").takeIf { it.isNotEmpty() }?.let { refused[it] = r.optString("why") } }
                }
                for (p in chunkPuts) {
                    when (val no = refused[p.link.id]) {
                        null -> { place.put(sentKey(p.link.id), p.hash); sent++ }
                        "gone", "taken" -> {
                            // This id is no longer the customer's at the server: his link goes, and the next one is new.
                            if (dao.settingValue(PREFIX + p.partyUid) == p.link.id + "." + p.link.key) {
                                dao.setSetting(PREFIX + p.partyUid, null)
                                nextGen(dao, p.partyUid)
                            }
                            place.put(sentKey(p.link.id), null)
                            place.put(seqKey(p.link.id), null)
                            // Never this id again on this device, whatever an older copy of the book says.
                            place.put(stopKey(p.link.id), "1")
                            lost++
                        }
                        else -> { refusals?.put(p.link.id, no); waiting++ }
                    }
                }
                for (id in chunkDrops) {
                    if (refused[id] == null) {
                        dao.setSetting(GONE + id, null); place.put(sentKey(id), null); place.put(seqKey(id), null); removed++
                    } else waiting++
                }
            }
        } catch (e: java.io.IOException) {
            return if (sent + removed + lost > 0) Result.Done(sent, removed, lost, items.size - sent - removed - lost) else Result.Unreachable
        }
        return Result.Done(sent, removed, lost, waiting)
    }

    /** A customer's latest entries, newest first, in one order on every device (ties by uid). */
    private suspend fun latest(dao: KhataDao, partyId: Long): List<Line> =
        dao.latestEntriesWithItems(partyId, MAX_ENTRIES + 3)
            .sortedWith(compareByDescending<EntryWithItems> { it.entry.timestamp }.thenByDescending { it.entry.uid.orEmpty() })
            .take(MAX_ENTRIES)
            .map { e ->
                val goods = e.items.mapNotNull { it.itemName?.trim()?.takeIf { n -> n.isNotEmpty() } }
                    .sortedWith(compareBy<String> { it.lowercase(java.util.Locale.ROOT) }.thenBy { it })
                Line(e.entry.timestamp, e.entry.amount, e.entry.isGiven, goods.isNotEmpty(), goods.joinToString(", ").ifEmpty { null })
            }

    /** The shop at the server: from the book, else made — from the book's key while it syncs, so two devices make one. */
    private suspend fun shop(dao: KhataDao, bookKey: String?): Pair<String, String> {
        dao.settingValue(SHOP)?.let { SHOP_CREDS.matchEntire(it) }?.let { return it.groupValues[1] to it.groupValues[2] }
        val made = if (bookKey != null) hexOf(derive(bookKey, "rk-clink-shop").copyOf(16)) to hexOf(derive(bookKey, "rk-clink-token"))
            else hex(16) to hex(32)
        dao.setSetting(SHOP, made.first + "." + made.second)
        return made
    }

    /** What [share] gives the screen: the link to send, or why it cannot be sent yet. */
    sealed interface Shared {
        data class Ready(val url: String, val link: Link) : Shared
        data class NotYet(val why: Result) : Shared
    }

    /**
     * The customer's link, ready to send: made if need be, and his page sent
     * first, so the link never reaches WhatsApp before its page is the shop's
     * at the server (where the customer, holding the link, could otherwise
     * put a page of his own first).
     *
     * A link the server says is no longer free (a book put back from an older
     * copy names one stopped since) is made anew once, at once. Why the page
     * could not go now (the server busy or full, today's new links used up)
     * comes back as [Result.Refused] with the server's word, for the owner.
     */
    suspend fun share(
        dao: KhataDao, place: Place, scope: String, look: Look, partyUid: String,
        bookKey: String? = null, claim: Boolean = false,
        http: Http = plain, root: String? = base, now: Long = System.currentTimeMillis()
    ): Shared = lock.withLock {
        if (root == null) return@withLock Shared.NotYet(Result.Off)
        lateinit var link: Link
        var r: Result = Result.Idle
        val refusals = HashMap<String, String>()
        for (attempt in 0..1) {
            link = createLocked(dao, partyUid, look, bookKey, place)
            refusals.clear()
            r = flushLocked(dao, place, scope, look, bookKey, claim, http, root, now, only = partyUid, refusals = refusals)
            if (!(r is Result.Done && r.lost > 0)) break
        }
        val sent = place.get(sentKey(link.id)) != null && linkOf(dao, partyUid) == link
        if (sent) Shared.Ready(url(link, root)!!, link)
        else Shared.NotYet(refusals[link.id]?.let { Result.Refused(it) } ?: r)
    }
}
