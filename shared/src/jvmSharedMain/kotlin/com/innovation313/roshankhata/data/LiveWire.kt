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
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Live sync over the shop's own WiFi (Marhala 1, 22 Oct 2026): the phone and
 * the computer talk directly; nothing goes to the internet.
 *
 * Two kinds of frame, each: four letters, the sealed bytes' length, the
 * sealed bytes.
 *
 *  - "RKA1", pairing, once: the phone, having scanned the computer's code
 *    ("rklive:1?…", its addresses, its port and a secret made for that code),
 *    sends this book's sync identity and its whole book, sealed with the
 *    code's secret ([WifiSync.seal]). The computer answers one byte, [OK] or
 *    [BAD], and shows the owner its usual restore confirmation: nothing is
 *    replaced without the owner's yes.
 *  - "RKL1", a round, every few seconds after: a request sealed with the
 *    book's own key (AES-256-GCM, deflated first), answered by a reply sealed
 *    the same way, or a length of 0 when the computer does not hold this
 *    book's key (not confirmed yet, or another book).
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

    private val PAIR = "RKA1".toByteArray(Charsets.US_ASCII)
    private val LIVE = "RKL1".toByteArray(Charsets.US_ASCII)
    private const val NONCE = 12
    private const val TAG_BITS = 128

    /** What the computer's live QR says. */
    data class Invite(val hosts: List<String>, val port: Int, val secret: String, val computer: String = "")

    fun inviteText(i: Invite): String =
        PREFIX + "h=" + i.hosts.joinToString(",") + "&p=" + i.port + "&k=" + i.secret + "&n=" + URLEncoder.encode(i.computer, "UTF-8")

    fun parseInvite(text: String?): Invite? {
        val t = text?.trim() ?: return null
        if (!t.startsWith(PREFIX)) return null
        // Same shape as the one-off code, under its own prefix: one parser, one set of checks.
        val p = WifiSync.parsePairing(WifiSync.PREFIX + t.removePrefix(PREFIX)) ?: return null
        return Invite(p.hosts, p.port, p.secret, p.computer)
    }

    // ------------------------------------------------------------ sealing

    private fun keyOf(hex: String): SecretKeySpec {
        require(hex.length == 64) { "a book key is 32 bytes" }
        val b = ByteArray(32) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        return SecretKeySpec(b, "AES")
    }

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

    /** [plain] deflated and sealed with the book's key. */
    fun seal(keyHex: String, plain: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, keyOf(keyHex), GCMParameterSpec(TAG_BITS, nonce))
        c.updateAAD(LIVE)
        return nonce + c.doFinal(deflate(plain))
    }

    /** What [sealed] says, or null when it was not sealed with this book's key, or was changed on the way. */
    fun open(keyHex: String, sealed: ByteArray): ByteArray? {
        if (sealed.size < NONCE + TAG_BITS / 8) return null
        return runCatching {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, keyOf(keyHex), GCMParameterSpec(TAG_BITS, sealed, 0, NONCE))
            c.updateAAD(LIVE)
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
        val pair: String? = null
    )

    fun encodeOffer(o: Offer): ByteArray {
        val head = JSONObject().apply {
            put("book", o.book); put("key", o.key); put("dev", o.device); put("name", o.name); put("seq", o.seq)
            if (o.mark != null) put("mark", o.mark)
            if (o.pair != null) put("pair", o.pair)
        }.toString().toByteArray(Charsets.UTF_8)
        return head + byteArrayOf('\n'.code.toByte()) + o.backup
    }

    fun decodeOffer(bytes: ByteArray): Offer? = runCatching {
        val nl = bytes.indexOf('\n'.code.toByte())
        require(nl > 0)
        val o = JSONObject(String(bytes, 0, nl, Charsets.UTF_8))
        val key = o.getString("key")
        require(key.length == 64 && key.all { it in '0'..'9' || it in 'a'..'f' })
        Offer(
            o.getString("book"), key, o.getString("dev"), o.optString("name"), o.getLong("seq"), bytes.copyOfRange(nl + 1, bytes.size),
            o.optString("mark").takeIf { LiveSync.validMark(it) },
            o.optString("pair").takeIf { it.length in 8..64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
        )
    }.getOrNull()

    /** How a pairing went from the phone's side. */
    sealed interface Sent {
        data class Taken(val host: String) : Sent
        object Refused : Sent
        object Unreachable : Sent
    }

    /** Phone side: the offer sealed with the code's secret, to the first address that answers. Blocking. */
    fun sendOffer(invite: Invite, offer: Offer, connectMs: Int = 3000, readMs: Int = 120_000): Sent {
        val sealed = WifiSync.seal(invite.secret, encodeOffer(offer))
        for (host in invite.hosts) {
            val socket = Socket()
            try { socket.connect(InetSocketAddress(host, invite.port), connectMs) } catch (e: IOException) { runCatching { socket.close() }; continue }
            socket.use { s ->
                s.soTimeout = readMs
                val out = DataOutputStream(s.getOutputStream().buffered())
                out.write(PAIR); out.writeInt(sealed.size); out.write(sealed); out.flush()
                val answer = s.getInputStream().read()
                return if (answer == OK) Sent.Taken(host) else Sent.Refused
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
        val sealed = seal(keyHex, request.toString().toByteArray(Charsets.UTF_8))
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
                val plain = open(keyHex, bytes) ?: return Answer.NotHere
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
     */
    fun serve(
        server: ServerSocket,
        stop: () -> Boolean,
        pairSecret: () -> String?,
        onOffer: (Offer) -> Boolean,
        bookKey: () -> String?,
        onRound: (JSONObject) -> JSONObject?
    ) {
        server.soTimeout = 1000
        val busy = java.util.concurrent.atomic.AtomicInteger(0)
        while (!stop()) {
            val s = try { server.accept() } catch (e: SocketTimeoutException) { continue } catch (e: IOException) { return }
            if (busy.incrementAndGet() > AT_ONCE) { busy.decrementAndGet(); runCatching { s.close() }; continue }
            val worker = Thread({
                try { serveOne(s, pairSecret, onOffer, bookKey, onRound) } finally { busy.decrementAndGet() }
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
        onRound: (JSONObject) -> JSONObject?
    ) = socket.use {
        runCatching {
            it.soTimeout = HELLO_MS
            val input = DataInputStream(it.getInputStream().buffered())
            val out = DataOutputStream(it.getOutputStream().buffered())
            val magic = ByteArray(4)
            input.readFully(magic)
            val n = input.readInt()
            when {
                magic.contentEquals(PAIR) -> {
                    val secret = pairSecret()
                    if (secret == null || n <= 0 || n > MAX_BYTES + NONCE + TAG_BITS / 8) { out.write(BAD); out.flush(); return@runCatching }
                    it.soTimeout = 60_000
                    val sealed = readUpTo(input, n)
                    val offer = WifiSync.open(secret, sealed)?.let { b -> decodeOffer(b) }
                    out.write(if (offer != null && onOffer(offer)) OK else BAD); out.flush()
                }
                magic.contentEquals(LIVE) -> {
                    val key = bookKey()
                    if (key == null || n <= 0 || n > MAX_ROUND_BYTES) { out.writeInt(0); out.flush(); return@runCatching }
                    it.soTimeout = 60_000
                    val sealed = readUpTo(input, n)
                    val request = open(key, sealed)?.let { b -> runCatching { JSONObject(String(b, Charsets.UTF_8)) }.getOrNull() }
                    val reply = request?.let { r -> runCatching { onRound(r) }.getOrNull() }
                    if (reply == null) out.writeInt(0)
                    else { val b = seal(key, reply.toString().toByteArray(Charsets.UTF_8)); out.writeInt(b.size); out.write(b) }
                    out.flush()
                }
                else -> { out.write(BAD); out.flush(); return@runCatching }
            }
            // The phone closes first, so the wait that follows a closed connection (TIME_WAIT, four
            // minutes on Windows) is the phone's, not the computer's: a restarted computer then finds
            // its port free (22 Oct 2026). A phone that never closes is let go after three seconds.
            it.soTimeout = 3000
            try { while (it.getInputStream().read() >= 0) { /* nothing more is expected */ } } catch (e: IOException) {}
        }
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
