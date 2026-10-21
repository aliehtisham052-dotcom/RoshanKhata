package com.innovation313.roshankhata.data

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The phone's book to the computer over the shop's own WiFi (20 Oct), one
 * way: phone to computer. Nothing goes to the internet.
 *
 * Pairing is a QR code the computer shows: its addresses on the network, a
 * port, and a secret made fresh each time the window opens. The phone scans
 * it and sends its backup (the same text BackupCodec writes) sealed with
 * AES-256-GCM under a key from that secret, so another device on the same
 * WiFi can neither read the book nor slip a different one in: a frame that
 * does not open with the secret is refused and the computer keeps waiting.
 * The computer then shows the book in its usual restore confirmation;
 * nothing is replaced without the owner's yes.
 *
 * Plain sockets rather than HTTP: Android refuses clear-text HTTP to an
 * address it was not configured for, and the computer's runtime then needs
 * no extra module. Shared by both sides, so the two cannot drift apart.
 *
 * The frame: "RKS1", a 12-byte nonce, the sealed bytes' length (4 bytes),
 * the sealed bytes. The answer: one byte, [OK], [BAD] or [TOO_BIG].
 *
 * The other way (20 Oct, later the same day): the phone may ASK the computer
 * for its book with a "RKG1" frame carrying the sealed word "pull". The
 * computer answers with its own book sealed the same way (length, bytes),
 * or a length of 0 when it will not give it. Still a whole copy, never a
 * merge; the phone shows its usual restore confirmation before anything is
 * replaced. The computer gives its book only while its window is open:
 * the secret on the screen is the owner's consent.
 */
object WifiSync {

    const val PREFIX = "rksync:1?"
    const val MAX_BYTES = 256 * 1024 * 1024
    const val OK: Int = 1
    const val BAD: Int = 2
    const val TOO_BIG: Int = 3

    private val MAGIC = "RKS1".toByteArray(Charsets.US_ASCII)
    private val MAGIC_GET = "RKG1".toByteArray(Charsets.US_ASCII)
    private val PULL = "pull".toByteArray(Charsets.US_ASCII)
    private const val NONCE = 12
    private const val TAG_BITS = 128

    /** What the computer's QR code says: where to send, and the secret to seal with. */
    data class Pairing(val hosts: List<String>, val port: Int, val secret: String, val computer: String = "")

    /** How a send ended. */
    sealed interface Outcome {
        /** The computer opened it: [host] is the address that answered. */
        data class Sent(val host: String) : Outcome
        /** A computer answered but would not take it ([why] is BAD or TOO_BIG). */
        data class Refused(val why: Int) : Outcome
        /** No address answered. */
        object Unreachable : Outcome
    }

    /** How a pull ended. */
    sealed interface Pulled {
        data class Got(val book: ByteArray) : Pulled
        /** A computer answered but gave nothing (its window is not open to giving, or the secret is wrong). */
        object Refused : Pulled
        object Unreachable : Pulled
    }

    /** 32 random bytes, as 64 hex digits (no Base64: the phone runs from Android 7). */
    fun newSecret(): String {
        val b = ByteArray(32)
        SecureRandom().nextBytes(b)
        val hex = "0123456789abcdef"
        return buildString(64) { for (x in b) { append(hex[(x.toInt() shr 4) and 0xF]); append(hex[x.toInt() and 0xF]) } }
    }

    fun pairingText(p: Pairing): String =
        PREFIX + "h=" + p.hosts.joinToString(",") + "&p=" + p.port + "&k=" + p.secret +
            "&n=" + URLEncoder.encode(p.computer, "UTF-8")

    /** The pairing in a scanned code, or null when the code is not one of ours (or is damaged). */
    fun parsePairing(text: String?): Pairing? {
        val t = text?.trim() ?: return null
        if (!t.startsWith(PREFIX)) return null
        val parts = t.removePrefix(PREFIX).split('&').mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i) to it.substring(i + 1)
        }.toMap()
        val hosts = parts["h"]?.split(',')?.map { it.trim() }?.filter { looksLikeIpv4(it) }.orEmpty()
        val port = parts["p"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        val secret = parts["k"]?.lowercase()?.takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } } ?: return null
        if (hosts.isEmpty()) return null
        val name = runCatching { URLDecoder.decode(parts["n"].orEmpty(), "UTF-8") }.getOrDefault("")
        return Pairing(hosts.take(6), port, secret, name.take(80))
    }

    private fun looksLikeIpv4(s: String): Boolean {
        val n = s.split('.')
        return n.size == 4 && n.all { p -> p.isNotEmpty() && p.length <= 3 && p.all(Char::isDigit) && p.toInt() in 0..255 }
    }

    private fun key(secret: String): SecretKeySpec {
        val d = MessageDigest.getInstance("SHA-256")
        d.update("roshan-khata-wifi-sync-1:".toByteArray(Charsets.UTF_8))
        d.update(secret.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(d.digest(), "AES")
    }

    /** [plain] sealed under [secret]: nonce then ciphertext with its tag. */
    fun seal(secret: String, plain: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(secret), GCMParameterSpec(TAG_BITS, nonce))
        c.updateAAD(MAGIC)
        return nonce + c.doFinal(plain)
    }

    /** What [sealed] says, or null when it does not open with [secret] (another key, or changed on the way). */
    fun open(secret: String, sealed: ByteArray): ByteArray? {
        if (sealed.size < NONCE + TAG_BITS / 8) return null
        return runCatching {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(secret), GCMParameterSpec(TAG_BITS, sealed, 0, NONCE))
            c.updateAAD(MAGIC)
            c.doFinal(sealed, NONCE, sealed.size - NONCE)
        }.getOrNull()
    }

    /**
     * Phone side: [payload] sealed and sent to the first of the computer's
     * addresses that answers within [connectMs]. Blocking; call off the main thread.
     */
    fun send(p: Pairing, payload: ByteArray, connectMs: Int = 3000, readMs: Int = 60_000): Outcome {
        val sealed = seal(p.secret, payload)
        for (host in p.hosts) {
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(host, p.port), connectMs)
            } catch (e: IOException) {
                runCatching { socket.close() }
                continue
            }
            socket.use { s ->
                s.soTimeout = readMs
                val out = DataOutputStream(s.getOutputStream().buffered())
                out.write(MAGIC)
                out.writeInt(sealed.size)
                out.write(sealed)
                out.flush()
                val answer = s.getInputStream().read()
                return if (answer == OK) Outcome.Sent(host) else Outcome.Refused(answer)
            }
        }
        return Outcome.Unreachable
    }

    /**
     * Phone side: asks the computer for its book. Blocking; call off the main thread.
     * The answer is sealed with the same secret, so only the computer that
     * showed the code can have written it.
     */
    fun pull(p: Pairing, connectMs: Int = 3000, readMs: Int = 120_000): Pulled {
        val ask = seal(p.secret, PULL)
        for (host in p.hosts) {
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(host, p.port), connectMs)
            } catch (e: IOException) {
                runCatching { socket.close() }
                continue
            }
            socket.use { s ->
                s.soTimeout = readMs
                val out = DataOutputStream(s.getOutputStream().buffered())
                out.write(MAGIC_GET)
                out.writeInt(ask.size)
                out.write(ask)
                out.flush()
                val input = DataInputStream(s.getInputStream().buffered())
                val n = try { input.readInt() } catch (e: IOException) { return Pulled.Refused }
                if (n <= 0 || n > MAX_BYTES + NONCE + TAG_BITS / 8) return Pulled.Refused
                val sealed = try { readUpTo(input, n) } catch (e: IOException) { return Pulled.Refused }
                return open(p.secret, sealed)?.let { Pulled.Got(it) } ?: Pulled.Refused
            }
        }
        return Pulled.Unreachable
    }

    /**
     * Computer side: waits on [server] until a phone sends a book that opens
     * with [secret], and returns it. A connection that is not ours, or not
     * sealed with this secret, is answered [BAD] and the wait goes on.
     * A phone asking for the computer's book ("RKG1") is given [give]'s
     * bytes, sealed, when [give] is set, and the wait goes on; with no
     * [give] it is answered with a length of 0.
     * Returns null when [stop] says so (checked every second) or the socket closes.
     */
    fun receive(server: ServerSocket, secret: String, stop: () -> Boolean = { false }, give: (() -> ByteArray?)? = null): ByteArray? {
        server.soTimeout = 1000
        // The book is given once per code shown (21 Oct 2026): a phone that saw the code could
        // otherwise come back for the whole book again, as often as it liked, while the window stayed open.
        var given = false
        while (!stop()) {
            val s = try { server.accept() } catch (e: SocketTimeoutException) { continue } catch (e: IOException) { return null }
            s.use {
                it.soTimeout = 60_000
                val frame = runCatching { readOne(it, secret) }.getOrNull()
                when (frame) {
                    is Frame.Ask -> {
                        val book = if (given) null else give?.invoke()
                        val out = DataOutputStream(it.getOutputStream().buffered())
                        runCatching {
                            if (book == null) out.writeInt(0) else { val sealed = seal(secret, book); out.writeInt(sealed.size); out.write(sealed) }
                            out.flush()
                            if (book != null) given = true
                        }
                    }
                    is Frame.Book -> {
                        runCatching { it.getOutputStream().apply { write(OK); flush() } }
                        return frame.bytes
                    }
                    Frame.TooBig -> runCatching { it.getOutputStream().apply { write(TOO_BIG); flush() } }
                    null -> runCatching { it.getOutputStream().apply { write(BAD); flush() } }
                }
            }
        }
        return null
    }

    /**
     * [n] bytes, read as they arrive rather than reserved first (21 Oct 2026):
     * a stranger on the shop's network could announce the largest size and
     * make the computer set aside 256 MB before a single byte was checked.
     */
    private fun readUpTo(input: DataInputStream, n: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream(minOf(n, 1 shl 16))
        val chunk = ByteArray(1 shl 16)
        var left = n
        while (left > 0) {
            val got = input.read(chunk, 0, minOf(chunk.size, left))
            if (got < 0) throw java.io.EOFException()
            out.write(chunk, 0, got)
            left -= got
        }
        return out.toByteArray()
    }

    private sealed interface Frame {
        class Book(val bytes: ByteArray) : Frame
        object Ask : Frame
        object TooBig : Frame
    }

    /** One frame: null when it is not ours or will not open. */
    private fun readOne(s: Socket, secret: String): Frame? {
        val input = DataInputStream(s.getInputStream().buffered())
        val magic = ByteArray(MAGIC.size)
        input.readFully(magic)
        val ask = magic.contentEquals(MAGIC_GET)
        if (!ask && !magic.contentEquals(MAGIC)) return null
        val n = input.readInt()
        if (n <= 0) return null
        if (n > MAX_BYTES + NONCE + TAG_BITS / 8) return Frame.TooBig
        val sealed = readUpTo(input, n)
        val plain = open(secret, sealed) ?: return null
        return if (ask) (if (plain.contentEquals(PULL)) Frame.Ask else null) else Frame.Book(plain)
    }
}
