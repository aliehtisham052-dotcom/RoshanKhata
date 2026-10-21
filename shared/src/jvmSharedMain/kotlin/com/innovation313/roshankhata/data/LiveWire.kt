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
    const val MAX_BYTES = 256 * 1024 * 1024
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

    private fun inflate(b: ByteArray, limit: Int = MAX_BYTES): ByteArray {
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
    class Offer(val book: String, val key: String, val device: String, val name: String, val seq: Long, val backup: ByteArray)

    fun encodeOffer(o: Offer): ByteArray {
        val head = JSONObject().apply {
            put("book", o.book); put("key", o.key); put("dev", o.device); put("name", o.name); put("seq", o.seq)
        }.toString().toByteArray(Charsets.UTF_8)
        return head + byteArrayOf('\n'.code.toByte()) + o.backup
    }

    fun decodeOffer(bytes: ByteArray): Offer? = runCatching {
        val nl = bytes.indexOf('\n'.code.toByte())
        require(nl > 0)
        val o = JSONObject(String(bytes, 0, nl, Charsets.UTF_8))
        val key = o.getString("key")
        require(key.length == 64 && key.all { it in '0'..'9' || it in 'a'..'f' })
        Offer(o.getString("book"), key, o.getString("dev"), o.optString("name"), o.getLong("seq"), bytes.copyOfRange(nl + 1, bytes.size))
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
                if (n <= 0 || n > MAX_BYTES) return Answer.NotHere
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
        while (!stop()) {
            val s = try { server.accept() } catch (e: SocketTimeoutException) { continue } catch (e: IOException) { return }
            s.use {
                runCatching {
                    it.soTimeout = 60_000
                    val input = DataInputStream(it.getInputStream().buffered())
                    val out = DataOutputStream(it.getOutputStream().buffered())
                    val magic = ByteArray(4)
                    input.readFully(magic)
                    val n = input.readInt()
                    if (n <= 0 || n > MAX_BYTES + NONCE + TAG_BITS / 8) { out.write(BAD); out.flush(); return@runCatching }
                    val sealed = readUpTo(input, n)
                    when {
                        magic.contentEquals(PAIR) -> {
                            val secret = pairSecret()
                            val offer = secret?.let { k -> WifiSync.open(k, sealed) }?.let { b -> decodeOffer(b) }
                            out.write(if (offer != null && onOffer(offer)) OK else BAD); out.flush()
                        }
                        magic.contentEquals(LIVE) -> {
                            val key = bookKey()
                            val request = key?.let { k -> open(k, sealed) }?.let { b -> runCatching { JSONObject(String(b, Charsets.UTF_8)) }.getOrNull() }
                            val reply = request?.let { r -> runCatching { onRound(r) }.getOrNull() }
                            if (key == null || reply == null) out.writeInt(0)
                            else { val b = seal(key, reply.toString().toByteArray(Charsets.UTF_8)); out.writeInt(b.size); out.write(b) }
                            out.flush()
                        }
                        else -> { out.write(BAD); out.flush() }
                    }
                }
            }
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
