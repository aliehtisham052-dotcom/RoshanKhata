package com.innovation313.roshankhata.data

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.zip.Deflater
import java.util.zip.Inflater
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Live sync over the shop's own WiFi (Marhala 1, 22 Oct 2026): the phone and
 * the computer talk directly; nothing goes to the internet.
 *
 * Two kinds of frame, each: four letters, the sealed bytes' length, the
 * sealed bytes.
 *
 *  - "RKA2", pairing, once: the phone, having scanned the computer's code
 *    ("rklive:1?…", its addresses, its port and a secret made for that code),
 *    sends this book's sync identity and its whole book as [SealedChunks]
 *    under the code's secret: the identity first, opened before anything
 *    more is read, then the book a record at a time onto the disk (23 Oct
 *    2026: "RKA1" sealed it as one frame, gathered whole in memory before the
 *    secret was checked — a stranger's four bytes could make a device hold
 *    256 MB). The computer answers one byte, [OK] or [BAD], and shows the
 *    owner its usual restore confirmation: nothing is replaced without the
 *    owner's yes. "RKA1" is refused unread: update both devices.
 *  - "RKL1", a round, every few seconds after: a request sealed with the
 *    book's own key (AES-256-GCM, deflated first; since 22 Oct 2026 with a
 *    key derived for that road, [Road]), answered by a reply sealed the same
 *    way for its own road, or a length of 0 when the computer does not hold
 *    this book's key (not confirmed yet, or another book).
 *
 * A stranger on the shop's network can neither read nor write a book: a
 * frame that does not open is answered and forgotten.
 */
object LiveWire {

    const val PREFIX = "rklive:1?"
    /** The computer's port, the same every time so the phone finds it again ("313"). */
    const val PORT = 47313
    /** A pairing's whole book, at most. */
    const val MAX_BYTES = 256 * 1024 * 1024
    /**
     * A round, at most (22 Oct 2026, the review: a round's length was read
     * and its bytes gathered up to 256 MB before anything was opened, so one
     * stranger's four bytes could make a phone run out of memory). A round
     * carries at most [LiveSync.PAGE] rows; a few MB is already generous.
     */
    const val MAX_ROUND_BYTES = 16 * 1024 * 1024
    /** How long a connection may take to say what it is, before anything is read of it. */
    private const val HELLO_MS = 5_000
    /** Connections the computer serves at once; more are let go at once. */
    private const val AT_ONCE = 4
    const val OK: Int = 1
    const val BAD: Int = 2

    /** Pairing since 23 Oct 2026 ([SealedChunks]); "RKA1", one sealed frame, is refused without being read. */
    private val PAIR = "RKA2".toByteArray(Charsets.US_ASCII)
    private val LIVE = "RKL1".toByteArray(Charsets.US_ASCII)
    private const val NONCE = 12
    private const val TAG_BITS = 128

    /**
     * What a live QR says: where to send the book, the code's secret, the receiving device's name, and whether
     * it is a phone (23 Oct 2026: a phone may take a book too; it serves no WiFi rounds after, so the two meet
     * through the relay, and the sender learns the phone's yes from there).
     */
    data class Invite(val hosts: List<String>, val port: Int, val secret: String, val computer: String = "", val phone: Boolean = false)

    fun inviteText(i: Invite): String =
        PREFIX + "h=" + i.hosts.joinToString(",") + "&p=" + i.port + "&k=" + i.secret + "&n=" + URLEncoder.encode(i.computer, "UTF-8") +
            (if (i.phone) "&t=phone" else "")

    fun parseInvite(text: String?): Invite? {
        val t = text?.trim() ?: return null
        if (!t.startsWith(PREFIX)) return null
        // Same shape as the one-off code, under its own prefix: one parser, one set of checks.
        val p = WifiSync.parsePairing(WifiSync.PREFIX + t.removePrefix(PREFIX)) ?: return null
        val phone = t.removePrefix(PREFIX).split('&').any { it == "t=phone" }
        return Invite(p.hosts, p.port, p.secret, p.computer, phone)
    }

    // ------------------------------------------------------------ sealing

    /**
     * Which way a sealed message goes (22 Oct 2026, the live-sync review).
     * Every road seals with its own key, derived from the book's key by HKDF
     * (RFC 5869, SHA-256), and names itself in the authenticated data: a
     * phone's request played back to it as the computer's reply, or a relay
     * parcel pushed down the WiFi, does not open. The book's key itself is
     * never used as a cipher key; the relay pass and the seek tag are HMACs
     * of it with their own labels, which keeps them apart the same way.
     */
    enum class Road(val label: String) {
        /** The phone's request, over the shop's WiFi. */
        REQUEST("wifi-request"),
        /** The computer's answer to it. */
        REPLY("wifi-reply"),
        /** A parcel left at a relay for the book's other devices. */
        RELAY("relay")
    }

    private const val SEAL_V = "RKL2"
    private val HKDF_SALT = "rk-live-keys-v2".toByteArray(Charsets.US_ASCII)
    private val subkeys = ConcurrentHashMap<String, SecretKeySpec>()

    private fun bookKeyBytes(hex: String): ByteArray {
        require(hex.length == 64) { "a book key is 32 bytes" }
        return ByteArray(32) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    /** HKDF-SHA256: extract with [HKDF_SALT], expand one 32-byte block with [info]. */
    fun hkdf(ikm: ByteArray, info: ByteArray, salt: ByteArray = HKDF_SALT): ByteArray {
        val extract = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(salt, "HmacSHA256")) }
        val prk = extract.doFinal(ikm)
        val expand = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(prk, "HmacSHA256")) }
        expand.update(info)
        expand.update(1.toByte())
        return expand.doFinal()
    }

    /** The road's own AES key for this book, derived once and kept (a few books, three roads). */
    private fun keyOf(hex: String, road: Road): SecretKeySpec =
        subkeys.getOrPut("$road:$hex") { SecretKeySpec(hkdf(bookKeyBytes(hex), "$SEAL_V:${road.label}".toByteArray(Charsets.US_ASCII)), "AES") }

    private fun aad(road: Road): ByteArray = "$SEAL_V|${road.label}".toByteArray(Charsets.US_ASCII)

    private fun deflate(b: ByteArray): ByteArray {
        val d = Deflater(Deflater.BEST_SPEED)
        d.setInput(b); d.finish()
        val out = ByteArrayOutputStream(maxOf(64, b.size / 3))
        val buf = ByteArray(1 shl 14)
        while (!d.finished()) { val n = d.deflate(buf); out.write(buf, 0, n) }
        d.end()
        return out.toByteArray()
    }

    private fun inflate(b: ByteArray, limit: Int = 4 * MAX_ROUND_BYTES): ByteArray {
        val i = Inflater()
        i.setInput(b)
        val out = ByteArrayOutputStream(b.size * 3)
        val buf = ByteArray(1 shl 14)
        while (!i.finished()) {
            val n = i.inflate(buf)
            if (n == 0 && (i.needsInput() || i.needsDictionary())) break
            out.write(buf, 0, n)
            if (out.size() > limit) { i.end(); throw IOException("too big") }
        }
        i.end()
        return out.toByteArray()
    }

    /** [plain] deflated and sealed with the book's key for [road]. */
    fun seal(keyHex: String, plain: ByteArray, road: Road): ByteArray {
        val nonce = ByteArray(NONCE).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, keyOf(keyHex, road), GCMParameterSpec(TAG_BITS, nonce))
        c.updateAAD(aad(road))
        return nonce + c.doFinal(deflate(plain))
    }

    /** What [sealed] says, or null when it was not sealed with this book's key for [road], or was changed on the way. */
    fun open(keyHex: String, sealed: ByteArray, road: Road): ByteArray? {
        if (sealed.size < NONCE + TAG_BITS / 8) return null
        return runCatching {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, keyOf(keyHex, road), GCMParameterSpec(TAG_BITS, sealed, 0, NONCE))
            c.updateAAD(aad(road))
            inflate(c.doFinal(sealed, NONCE, sealed.size - NONCE))
        }.getOrNull()
    }

    // ------------------------------------------------------------ pairing

    /** This book's sync identity and its whole book, from the phone. [seq] is the phone's log line the book was taken at. */
    class Offer(
        val book: String, val key: String, val device: String, val name: String, val seq: Long, val backup: ByteArray,
        /** The letter the joining device takes in document numbers ([NumberMark]); null from an older phone. */
        val mark: String? = null,
        /** This pairing's own id: every round names it, so only the computer that said yes to it answers. */
        val pair: String? = null,
        /** On the computer: the code the phone shows for this pairing ([pairCode]), set as the offer is read. */
        val code: String? = null,
        /** The sender's other devices, as far as the copy holds of each ([LiveCrew.offerPeers]); null from an older phone. */
        val peers: JSONObject? = null,
        /**
         * Every letter the sender knows is taken in the book's numbers, its own and the one it gives here included
         * ([LiveSync.knownMarks], 23 Oct 2026): the joiner never hands one of them to a device it pairs in turn.
         */
        val marks: List<String>? = null,
        /** On the receiving side: the book as it arrived, on the disk ([serve]); [backup] is then empty. */
        val file: java.io.File? = null
    ) {
        fun withCode(c: String?) = Offer(book, key, device, name, seq, backup, mark, pair, c, peers, marks, file)
        fun withFile(f: java.io.File?) = Offer(book, key, device, name, seq, backup, mark, pair, code, peers, marks, f)
    }

    /**
     * Four digits both screens show for one pairing (22 Oct 2026, the review:
     * a pairing had no code to compare, so a book sent by whoever else had
     * photographed the computer's QR looked like the owner's own). Made from
     * the code's secret and the pairing's id, which only the phone that sent
     * it and this computer hold.
     */
    fun pairCode(secret: String, pair: String): String {
        val d = java.security.MessageDigest.getInstance("SHA-256").digest(("rk-pair-code:" + secret + ":" + pair).toByteArray(Charsets.UTF_8))
        val n = (((d[0].toInt() and 0xff) shl 16) or ((d[1].toInt() and 0xff) shl 8) or (d[2].toInt() and 0xff)) % 10000
        return n.toString().padStart(4, '0')
    }

    fun encodeOffer(o: Offer): ByteArray = offerHead(o) + o.backup

    /** What comes before the book in an offer: its identity as one line of JSON, and the line's end. */
    fun offerHead(o: Offer): ByteArray {
        val head = JSONObject().apply {
            put("book", o.book); put("key", o.key); put("dev", o.device); put("name", o.name); put("seq", o.seq)
            if (o.mark != null) put("mark", o.mark)
            if (o.pair != null) put("pair", o.pair)
            if (o.peers != null && o.peers.length() > 0) put("peers", o.peers)
            if (!o.marks.isNullOrEmpty()) put("marks", org.json.JSONArray(o.marks))
        }.toString().toByteArray(Charsets.UTF_8)
        return head + byteArrayOf('\n'.code.toByte())
    }

    fun decodeOffer(bytes: ByteArray): Offer? = runCatching {
        val nl = bytes.indexOf('\n'.code.toByte())
        require(nl > 0)
        decodeHead(bytes.copyOfRange(0, nl))!!.let { h ->
            Offer(h.book, h.key, h.device, h.name, h.seq, bytes.copyOfRange(nl + 1, bytes.size), h.mark, h.pair, null, h.peers, h.marks)
        }
    }.getOrNull()

    /** An offer's identity ([offerHead]) without its book, or null when it is not one. */
    fun decodeHead(bytes: ByteArray): Offer? = runCatching {
        val o = JSONObject(String(bytes, Charsets.UTF_8).trim())
        val key = o.getString("key")
        require(key.length == 64 && key.all { it in '0'..'9' || it in 'a'..'f' })
        val marks = o.optJSONArray("marks")?.let { a -> (0 until minOf(a.length(), 256)).map { a.optString(it) }.filter { LiveSync.validMark(it) } }
        Offer(
            o.getString("book"), key, o.getString("dev"), o.optString("name"), o.getLong("seq"), ByteArray(0),
            o.optString("mark").takeIf { LiveSync.validMark(it) },
            o.optString("pair").takeIf { it.length in 8..64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } },
            null,
            o.optJSONObject("peers"),
            marks
        )
    }.getOrNull()

    /** How a pairing went from the phone's side. */
    sealed interface Sent {
        /** [code]: the pairing's code, for the owner to see on both screens ([pairCode]); null without a pair id. */
        data class Taken(val host: String, val code: String? = null) : Sent
        object Refused : Sent
        object Unreachable : Sent
    }

    /**
     * Phone side: the offer with the book read from [backup] as it goes out
     * ([SealedChunks]), never held whole (22 Oct 2026, the review); the
     * computer reads the same frame [sendOffer] sends. [offer]'s own backup
     * bytes are not sent. Blocking.
     */
    fun sendOfferFile(invite: Invite, offer: Offer, backup: java.io.File, connectMs: Int = 3000, readMs: Int = 120_000): Sent {
        val head = offerHead(offer)
        for (host in invite.hosts) {
            val socket = Socket()
            try { socket.connect(InetSocketAddress(host, invite.port), connectMs) } catch (e: IOException) { runCatching { socket.close() }; continue }
            socket.use { s ->
                s.soTimeout = readMs
                val out = DataOutputStream(s.getOutputStream().buffered(1 shl 16))
                out.write(PAIR)
                backup.inputStream().buffered(1 shl 16).use { body -> SealedChunks.write(out, WifiSync.chunkKey(invite.secret), PAIR, head, body) }
                val answer = s.getInputStream().read()
                return if (answer == OK) Sent.Taken(host, offer.pair?.let { pairCode(invite.secret, it) }) else Sent.Refused
            }
        }
        return Sent.Unreachable
    }

    /** Phone side: the offer (its book in [Offer.backup]) under the code's secret, to the first address that answers. Blocking. */
    fun sendOffer(invite: Invite, offer: Offer, connectMs: Int = 3000, readMs: Int = 120_000): Sent {
        val head = offerHead(offer)
        for (host in invite.hosts) {
            val socket = Socket()
            try { socket.connect(InetSocketAddress(host, invite.port), connectMs) } catch (e: IOException) { runCatching { socket.close() }; continue }
            socket.use { s ->
                s.soTimeout = readMs
                val out = DataOutputStream(s.getOutputStream().buffered(1 shl 16))
                out.write(PAIR)
                SealedChunks.write(out, WifiSync.chunkKey(invite.secret), PAIR, head, java.io.ByteArrayInputStream(offer.backup))
                val answer = s.getInputStream().read()
                return if (answer == OK) Sent.Taken(host, offer.pair?.let { pairCode(invite.secret, it) }) else Sent.Refused
            }
        }
        return Sent.Unreachable
    }

    // ------------------------------------------------------------- rounds

    /** How a round went from the phone's side. */
    sealed interface Answer {
        data class Got(val reply: JSONObject, val host: String) : Answer
        /** The computer answered but does not hold this book (not confirmed yet, or another book). */
        object NotHere : Answer
        object Unreachable : Answer
    }

    /** Phone side: [request] to the computer, first of [hosts] that answers; its reply. Blocking. */
    fun round(hosts: List<String>, port: Int, keyHex: String, request: JSONObject, connectMs: Int = 1500, readMs: Int = 60_000): Answer {
        val sealed = seal(keyHex, request.toString().toByteArray(Charsets.UTF_8), Road.REQUEST)
        for (host in hosts) {
            val socket = Socket()
            try { socket.connect(InetSocketAddress(host, port), connectMs) } catch (e: IOException) { runCatching { socket.close() }; continue }
            socket.use { s ->
                s.soTimeout = readMs
                val out = DataOutputStream(s.getOutputStream().buffered())
                out.write(LIVE); out.writeInt(sealed.size); out.write(sealed); out.flush()
                val input = DataInputStream(s.getInputStream().buffered())
                val n = try { input.readInt() } catch (e: IOException) { return Answer.NotHere }
                if (n <= 0 || n > MAX_ROUND_BYTES) return Answer.NotHere
                val bytes = try { readUpTo(input, n) } catch (e: IOException) { return Answer.NotHere }
                val plain = open(keyHex, bytes, Road.REPLY) ?: return Answer.NotHere
                return runCatching { Answer.Got(JSONObject(String(plain, Charsets.UTF_8)), host) }.getOrElse { Answer.NotHere }
            }
        }
        return Answer.Unreachable
    }

    /**
     * Computer side: answers on [server] until [stop]. A pairing frame is
     * opened with [pairSecret] (null when no code is on the screen) and given
     * to [onOffer], which says whether it was taken. A round is opened with
     * [bookKey] (null while this computer does not sync a book) and answered
     * with [onRound]'s reply. Blocking; run it on its own thread.
     *
     * Each connection is served on its own short-lived thread, a few at once
     * (22 Oct 2026, the review: one connection that said nothing held the
     * only thread for a minute, and with it every phone's sync); a
     * connection must say what it is within a few seconds, and a pairing is
     * only read while a code is on the screen.
     *
     * A pairing's book is written to a file in [spool] (the system's temporary
     * folder when null) as it arrives, after its identity opened with the
     * code's secret ([SealedChunks]); [onOffer] gets it as [Offer.file] and
     * owns the file when it says yes — otherwise the file is deleted here.
     */
    fun serve(
        server: ServerSocket,
        stop: () -> Boolean,
        pairSecret: () -> String?,
        onOffer: (Offer) -> Boolean,
        bookKey: () -> String?,
        spool: java.io.File? = null,
        onRound: (JSONObject) -> JSONObject?
    ) {
        server.soTimeout = 1000
        val busy = java.util.concurrent.atomic.AtomicInteger(0)
        while (!stop()) {
            val s = try { server.accept() } catch (e: SocketTimeoutException) { continue } catch (e: IOException) { return }
            if (busy.incrementAndGet() > AT_ONCE) { busy.decrementAndGet(); runCatching { s.close() }; continue }
            val worker = Thread({
                try { serveOne(s, pairSecret, onOffer, bookKey, spool, onRound) } finally { busy.decrementAndGet() }
            }, "live-conn")
            worker.isDaemon = true
            worker.start()
        }
    }

    private fun serveOne(
        socket: Socket,
        pairSecret: () -> String?,
        onOffer: (Offer) -> Boolean,
        bookKey: () -> String?,
        spool: java.io.File?,
        onRound: (JSONObject) -> JSONObject?
    ) = socket.use {
        try {
            it.soTimeout = HELLO_MS
            val input = DataInputStream(it.getInputStream().buffered(1 shl 16))
            val out = DataOutputStream(it.getOutputStream().buffered())
            val magic = ByteArray(4)
            input.readFully(magic)
            when {
                magic.contentEquals(PAIR) -> {
                    val secret = pairSecret()
                    if (secret == null) { refuse(it, out); return@use }
                    // The identity first, at most a small record, opened with the code's secret: a stranger gets no further.
                    val r = SealedChunks.Reader(input, WifiSync.chunkKey(secret), PAIR)
                    val head = r.next(SealedChunks.MAX_HEAD)?.let { b -> decodeHead(b) }
                    if (head == null) { refuse(it, out); return@use }
                    // The sender holds the code's secret: its book is read now, a record at a time, onto the disk.
                    it.soTimeout = 60_000
                    val f = java.io.File.createTempFile("rk-pair-", ".part", spool?.apply { mkdirs() })
                    var taken = false
                    try {
                        if (r.readTo(f, MAX_BYTES.toLong())) {
                            val offer = head.withFile(f).withCode(head.pair?.let { p -> pairCode(secret, p) })
                            taken = try { onOffer(offer) } catch (e: Exception) { LiveTrouble.note("pairing offer", e); false }
                        }
                    } finally {
                        if (!taken) f.delete()
                    }
                    out.write(if (taken) OK else BAD); out.flush()
                }
                magic.contentEquals(LIVE) -> {
                    val n = input.readInt()
                    val key = bookKey()
                    if (key == null || n <= 0 || n > MAX_ROUND_BYTES) { out.writeInt(0); out.flush(); return@use }
                    it.soTimeout = 60_000
                    val sealed = readUpTo(input, n)
                    val request = open(key, sealed, Road.REQUEST)?.let { b -> runCatching { JSONObject(String(b, Charsets.UTF_8)) }.getOrNull() }
                    // A round the book could not answer (a database error) is written down, not only dropped (23 Oct 2026).
                    val reply = request?.let { r ->
                        try { onRound(r) } catch (e: Exception) { LiveTrouble.note("wifi round", e); null }
                    }
                    if (reply == null) out.writeInt(0)
                    else { val b = seal(key, reply.toString().toByteArray(Charsets.UTF_8), Road.REPLY); out.writeInt(b.size); out.write(b) }
                    out.flush()
                }
                // "RKA1" (a phone of before 23 Oct 2026) and anything else: refused, nothing more read.
                else -> { refuse(it, out); return@use }
            }
            // The phone closes first, so the wait that follows a closed connection (TIME_WAIT, four
            // minutes on Windows) is the phone's, not the computer's: a restarted computer then finds
            // its port free (22 Oct 2026). A phone that never closes is let go after three seconds.
            it.soTimeout = 3000
            try { while (it.getInputStream().read() >= 0) { /* nothing more is expected */ } } catch (e: IOException) {}
        } catch (e: IOException) {
            // A connection that stalled or closed: the network's ordinary trouble, not written down.
        } catch (e: Exception) {
            LiveTrouble.note("wifi connection", e)
            Unit
        }
    }

    /**
     * [BAD], then a moment for the other side to read it and close first: a little of what it still sends is read
     * and dropped (closing with bytes unread resets the connection, and the answer could be lost with it).
     */
    private fun refuse(s: Socket, out: DataOutputStream) {
        out.write(BAD); out.flush()
        try {
            s.soTimeout = 1000
            val input = s.getInputStream()
            val buf = ByteArray(4096)
            var left = 64 * 1024
            val until = System.currentTimeMillis() + 3000
            while (left > 0 && System.currentTimeMillis() < until) {
                val n = input.read(buf, 0, minOf(buf.size, left))
                if (n < 0) break
                left -= n
            }
        } catch (e: IOException) {}
    }

    // ------------------------------------------------- finding the computer again

    private val SEEK = "RKS2".toByteArray(Charsets.US_ASCII)
    private val HERE = "RKH2".toByteArray(Charsets.US_ASCII)
    private const val NONCE_SEEK = 16
    private const val TAG = 16

    /**
     * The computer's answer to a phone's call: proof it holds the book's key,
     * made over the phone's fresh nonce and the port it serves rounds on
     * (22 Oct 2026, the review: the call used to carry a fixed tag made from
     * the key, which anyone on the WiFi could copy and answer with, sending
     * the phone's rounds to a stranger; and the port was never said, so a
     * computer on another port was never found again).
     */
    fun hereTag(keyHex: String, nonce: ByteArray, port: Int): ByteArray {
        val key = ByteArray(32) { i -> keyHex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        val mac = javax.crypto.Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }
        mac.update("rk-here2".toByteArray(Charsets.US_ASCII))
        mac.update(nonce)
        mac.update(byteArrayOf((port shr 8).toByte(), port.toByte()))
        return mac.doFinal().copyOf(TAG)
    }

    /** Every address a call to the whole WiFi can go to: the general one and each network's own. */
    fun broadcastTargets(): List<java.net.InetAddress> {
        val out = ArrayList<java.net.InetAddress>()
        runCatching {
            java.net.NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.interfaceAddresses }
                .mapNotNull { it.broadcast }
                .forEach { out += it }
        }
        runCatching { out += java.net.InetAddress.getByName("255.255.255.255") }
        return out.distinct()
    }

    /** Where the phone found its computer: its address and the port it serves rounds on. */
    data class Found(val host: String, val port: Int)

    /** Phone side: the computer of this book on this WiFi, or null when none answered. Blocking. */
    fun seek(keyHex: String, port: Int = PORT, targets: List<java.net.InetAddress> = broadcastTargets(), waitMs: Int = 1500): Found? {
        val nonce = ByteArray(NONCE_SEEK).also { SecureRandom().nextBytes(it) }
        val call = SEEK + nonce
        return runCatching {
            java.net.DatagramSocket().use { s ->
                s.broadcast = true
                for (t in targets) runCatching { s.send(java.net.DatagramPacket(call, call.size, t, port)) }
                val until = System.currentTimeMillis() + waitMs
                val buf = ByteArray(64)
                var found: Found? = null
                while (found == null) {
                    val left = (until - System.currentTimeMillis()).toInt()
                    if (left <= 0) break
                    s.soTimeout = left
                    val p = java.net.DatagramPacket(buf, buf.size)
                    try { s.receive(p) } catch (e: SocketTimeoutException) { break }
                    if (p.length != HERE.size + 2 + TAG || !buf.copyOfRange(0, HERE.size).contentEquals(HERE)) continue
                    val served = ((buf[HERE.size].toInt() and 0xFF) shl 8) or (buf[HERE.size + 1].toInt() and 0xFF)
                    val proof = buf.copyOfRange(HERE.size + 2, HERE.size + 2 + TAG)
                    if (served in 1..65535 && java.security.MessageDigest.isEqual(proof, hereTag(keyHex, nonce, served))) {
                        found = Found(p.address.hostAddress, served)
                    }
                }
                found
            }
        }.getOrNull()
    }

    /**
     * Computer side: answers phones' calls with proof of this book's key
     * ([bookKey]) and the port rounds are served on ([servedPort]), until
     * [stop]. Blocking.
     */
    fun answerSeeks(socket: java.net.DatagramSocket, stop: () -> Boolean, bookKey: () -> String?, servedPort: () -> Int = { PORT }) {
        socket.soTimeout = 1000
        val buf = ByteArray(64)
        while (!stop()) {
            val p = java.net.DatagramPacket(buf, buf.size)
            try { socket.receive(p) } catch (e: SocketTimeoutException) { continue } catch (e: IOException) { return }
            if (p.length != SEEK.size + NONCE_SEEK || !buf.copyOfRange(0, SEEK.size).contentEquals(SEEK)) continue
            val key = bookKey() ?: continue
            val nonce = buf.copyOfRange(SEEK.size, SEEK.size + NONCE_SEEK)
            val port = servedPort()
            val reply = HERE + byteArrayOf((port shr 8).toByte(), port.toByte()) + hereTag(key, nonce, port)
            runCatching { socket.send(java.net.DatagramPacket(reply, reply.size, p.socketAddress)) }
        }
    }

    /** [n] bytes read as they arrive, never reserved up front (a stranger could announce the largest size). */
    private fun readUpTo(input: DataInputStream, n: Int): ByteArray {
        val out = ByteArrayOutputStream(minOf(n, 1 shl 16))
        val chunk = ByteArray(1 shl 16)
        var left = n
        while (left > 0) {
            val got = input.read(chunk, 0, minOf(chunk.size, left))
            if (got < 0) throw EOFException()
            out.write(chunk, 0, got)
            left -= got
        }
        return out.toByteArray()
    }
}
