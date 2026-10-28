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
 * The send (since 23 Oct 2026, the review report): "RKB2", then the book as
 * [SealedChunks] under [chunkKey] — a small head (the book's length) the
 * computer opens first, a byte back ([GO], or [BAD] / [TOO_BIG]), then the
 * book a record at a time, which the computer writes to a file as it comes.
 * The answer: one byte, [OK], [BAD] or [TOO_BIG]. A stranger without the
 * secret gets no further than the head's few bytes, and nothing of the book
 * is ever held whole on either side. "RKS1" (one sealed frame, gathered whole
 * in memory before it was opened) is refused unread: update the phone and the
 * computer together.
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
    /** The computer opened the head of a send: the book may follow (23 Oct 2026). */
    const val GO: Int = 4

    /** The one-frame seal's AAD ([seal], [open]): still the pull's question and a computer of before's answer. */
    private val MAGIC = "RKS1".toByteArray(Charsets.US_ASCII)
    /** A book sent as [SealedChunks] (23 Oct 2026); also the records' label, so no other stream opens as one. */
    private val MAGIC_SEND = "RKB2".toByteArray(Charsets.US_ASCII)
    /** A send's head: the book's length, 8 bytes. */
    private const val SEND_HEAD = 8
    /** How long a connection may take to show it is ours (its magic and the head), as LiveWire's hello. */
    private const val HELLO_MS = 5_000
    /** The pull's question is one short sealed word: nothing longer is read from a "RKG1" frame. */
    private const val MAX_ASK = 256
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
        /** The computer's book, written to [file] as it arrived (never held whole in memory, 23 Oct 2026). */
        data class Got(val file: java.io.File) : Pulled
        /** A computer answered but gave nothing (its window is not open to giving, or the secret is wrong). */
        object Refused : Pulled
        /** The book is bigger than this device can take (its free space, or its memory for reading the book after). */
        object TooBig : Pulled
        object Unreachable : Pulled
    }

    /**
     * The length a computer answers a pull with when the book follows as [SealedChunks] (23 Oct 2026): "RKC2",
     * larger than any book, so a phone of before refuses it rather than reading 1.3 GB.
     */
    private const val CHUNKED = 0x524B4332

    /** The key a [SealedChunks] stream under [secret] is sealed with: its own, never the one-frame key ([key]). */
    internal fun chunkKey(secret: String): SecretKeySpec {
        val d = MessageDigest.getInstance("SHA-256")
        d.update("roshan-khata-wifi-chunks-1:".toByteArray(Charsets.UTF_8))
        d.update(secret.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(d.digest(), "AES")
    }

    /**
     * Whether a book of [bytes] can be taken here: the folder has room for it, and the memory left could read it
     * after (the restore's parse keeps the book's rows; twice the text is the least it needs).
     */
    fun roomFor(bytes: Long, folder: java.io.File?): Boolean {
        if (bytes < 0 || bytes > MAX_BYTES) return false
        val space = folder?.usableSpace ?: 0L
        if (folder != null && space in 1 until bytes + (1L shl 20)) return false
        val rt = Runtime.getRuntime()
        val free = rt.maxMemory() - (rt.totalMemory() - rt.freeMemory())
        return bytes * 2 <= free
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
        return n.size == 4 && n.all { p -> p.isNotEmpty() && p.length <= 3 && p.all(Char::isDigit) && p.toInt() in 0..255 } &&
            onThisNetwork(n.map { it.toInt() })
    }

    /**
     * Only an address on the shop's own network may come from a code: 10.*, 172.16-31.*,
     * 192.168.*, link-local 169.254.*, and 127.* (this machine; the tests). Both makers of a
     * code advertise site-local addresses only, so a code naming a public host was not made by
     * this app — a poster or a forwarded picture would otherwise send the whole book (PIN
     * hashes too) to a server on the internet, sealed with the secret that same code carries.
     */
    private fun onThisNetwork(o: List<Int>): Boolean = when (o[0]) {
        10, 127 -> true
        172 -> o[1] in 16..31
        192 -> o[1] == 168
        169 -> o[1] == 254
        else -> false
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

    /** The computer's answer before the book went: [BAD] or [TOO_BIG]. */
    private class NoGo(val why: Int) : IOException("refused before the book")

    /**
     * Phone side: [file] sent to the first of the computer's addresses that
     * answers within [connectMs], streamed as it is read ([SealedChunks]),
     * never held whole. Blocking; call off the main thread.
     */
    fun sendFile(p: Pairing, file: java.io.File, connectMs: Int = 3000, readMs: Int = 60_000): Outcome {
        val length = file.length()
        if (length <= 0L || length > MAX_BYTES) return Outcome.Refused(TOO_BIG)
        return sendStream(p, length, connectMs, readMs) { file.inputStream().buffered(1 shl 16) }
    }

    /** Phone side: [payload] sent as [sendFile] sends a file. Blocking; call off the main thread. */
    fun send(p: Pairing, payload: ByteArray, connectMs: Int = 3000, readMs: Int = 60_000): Outcome {
        if (payload.isEmpty() || payload.size > MAX_BYTES) return Outcome.Refused(TOO_BIG)
        return sendStream(p, payload.size.toLong(), connectMs, readMs) { java.io.ByteArrayInputStream(payload) }
    }

    private fun sendStream(p: Pairing, length: Long, connectMs: Int, readMs: Int, body: () -> java.io.InputStream): Outcome {
        val head = java.nio.ByteBuffer.allocate(SEND_HEAD).putLong(length).array()
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
                val out = DataOutputStream(s.getOutputStream().buffered(1 shl 16))
                val input = s.getInputStream()
                out.write(MAGIC_SEND)
                try {
                    body().use { b ->
                        // The head goes first and the computer says whether the book may follow: a computer
                        // that will not take it is not sent a byte of it.
                        SealedChunks.write(out, chunkKey(p.secret), MAGIC_SEND, head, b) {
                            val go = input.read()
                            if (go != GO) throw NoGo(go)
                        }
                    }
                } catch (e: NoGo) {
                    // A computer of before reads "RKB2" as a stranger's and answers BAD: the same words for the owner.
                    return Outcome.Refused(if (e.why == TOO_BIG) TOO_BIG else BAD)
                }
                val answer = input.read()
                return if (answer == OK) Outcome.Sent(host) else Outcome.Refused(answer)
            }
        }
        return Outcome.Unreachable
    }

    /**
     * Phone side: asks the computer for its book, written to [into] as it
     * arrives. Blocking; call off the main thread. The answer is sealed with
     * the same secret, so only the computer that showed the code can have
     * written it. Since 23 Oct 2026 (the review report, R46) the book comes as
     * [SealedChunks]: its length first, checked against this device's room
     * ([roomFor]), then a record at a time onto the disk; a computer of before
     * (one sealed frame) is still read, only when the frame fits in memory.
     * [into] is deleted unless the answer is [Pulled.Got].
     */
    fun pull(p: Pairing, into: java.io.File, connectMs: Int = 3000, readMs: Int = 120_000): Pulled {
        val r = pullInto(p, into, connectMs, readMs)
        if (r !is Pulled.Got) runCatching { into.delete() }
        return r
    }

    private fun pullInto(p: Pairing, into: java.io.File, connectMs: Int, readMs: Int): Pulled {
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
                val input = DataInputStream(s.getInputStream().buffered(1 shl 16))
                val n = try { input.readInt() } catch (e: IOException) { return Pulled.Refused }
                if (n == CHUNKED) {
                    return try {
                        val r = SealedChunks.Reader(input, chunkKey(p.secret), MAGIC_GET)
                        // The head: the book's length, opened before a byte of the book is read.
                        val head = r.next(64) ?: return Pulled.Refused
                        if (head.size != 8) return Pulled.Refused
                        val length = java.nio.ByteBuffer.wrap(head).long
                        if (!roomFor(length, into.parentFile)) return Pulled.TooBig
                        if (!r.readTo(into, length) || into.length() != length) return Pulled.Refused
                        Pulled.Got(into)
                    } catch (e: IOException) { Pulled.Refused }
                }
                // A computer of before: one sealed frame, taken only when this device can hold it.
                if (n <= 0 || n > MAX_BYTES + NONCE + TAG_BITS / 8) return Pulled.Refused
                if (!roomFor(n.toLong() * 2, into.parentFile)) return Pulled.TooBig
                val sealed = try { readUpTo(input, n) } catch (e: IOException) { return Pulled.Refused }
                val book = open(p.secret, sealed) ?: return Pulled.Refused
                return try { into.writeBytes(book); Pulled.Got(into) } catch (e: IOException) { Pulled.Refused }
            }
        }
        return Pulled.Unreachable
    }

    /**
     * Computer side: waits on [server] until a phone sends a book that opens
     * with [secret], and returns it as a file in [spool] (the system's
     * temporary folder when null), written as it arrived; the caller owns it.
     * A connection that is not ours, or not sealed with this secret, is
     * answered [BAD] and the wait goes on: only the small head is read before
     * the secret is checked (23 Oct 2026, the review report), so a stranger
     * cannot make this computer hold or write anything of size.
     * A phone asking for the computer's book ("RKG1") is given [give]'s
     * bytes, sealed, when [give] is set, and the wait goes on; with no
     * [give] it is answered with a length of 0.
     * Returns null when [stop] says so (checked every second) or the socket closes.
     */
    fun receive(
        server: ServerSocket,
        secret: String,
        stop: () -> Boolean = { false },
        spool: java.io.File? = null,
        give: (() -> ByteArray?)? = null
    ): java.io.File? {
        server.soTimeout = 1000
        // The book is given once per code shown (21 Oct 2026): a phone that saw the code could
        // otherwise come back for the whole book again, as often as it liked, while the window stayed open.
        var given = false
        while (!stop()) {
            val s = try { server.accept() } catch (e: SocketTimeoutException) { continue } catch (e: IOException) { return null }
            s.use {
                it.soTimeout = HELLO_MS
                val input = DataInputStream(it.getInputStream().buffered(1 shl 16))
                val out = DataOutputStream(it.getOutputStream().buffered())
                val magic = ByteArray(MAGIC.size)
                if (runCatching { input.readFully(magic) }.isFailure) return@use
                when {
                    magic.contentEquals(MAGIC_SEND) -> {
                        val file = runCatching { takeBook(it, input, out, secret, spool) }.getOrNull()
                        if (file != null) return file
                    }
                    magic.contentEquals(MAGIC_GET) -> {
                        val asked = runCatching { readAsk(input, secret) }.getOrDefault(false)
                        if (!asked) { refuse(it, out, BAD); return@use }
                        val book = if (given) null else give?.invoke()
                        runCatching {
                            if (book == null) out.writeInt(0)
                            else {
                                it.soTimeout = 60_000
                                // A record at a time (SealedChunks), its length first: the phone never holds it whole.
                                out.writeInt(CHUNKED)
                                val head = java.nio.ByteBuffer.allocate(8).putLong(book.size.toLong()).array()
                                SealedChunks.write(out, chunkKey(secret), MAGIC_GET, head, java.io.ByteArrayInputStream(book))
                            }
                            out.flush()
                            if (book != null) given = true
                        }
                    }
                    // "RKS1" (a phone of before 23 Oct 2026, one frame gathered whole) and anything else: refused unread.
                    else -> refuse(it, out, BAD)
                }
            }
        }
        return null
    }

    /**
     * One send after its magic: the head opened with [secret] before anything more is read, [GO], then the book
     * onto a file in [spool]. The file when it came whole and is answered [OK]; null otherwise (answered, and the
     * file deleted). Throws when the connection fails (the file is deleted first).
     */
    private fun takeBook(s: Socket, input: DataInputStream, out: DataOutputStream, secret: String, spool: java.io.File?): java.io.File? {
        val r = SealedChunks.Reader(input, chunkKey(secret), MAGIC_SEND)
        val head = r.next(SEND_HEAD)
        if (head == null || head.size != SEND_HEAD) { refuse(s, out, BAD); return null }
        val length = java.nio.ByteBuffer.wrap(head).long
        if (length <= 0L) { refuse(s, out, BAD); return null }
        val dir = spool?.apply { mkdirs() }
        if (length > MAX_BYTES || !diskRoomFor(length, dir)) { refuse(s, out, TOO_BIG); return null }
        // The sender holds the code's secret: its book is read now, a record at a time, onto the disk.
        out.write(GO); out.flush()
        s.soTimeout = 60_000
        val f = java.io.File.createTempFile("rk-phone-", ".part", dir)
        var kept = false
        try {
            if (r.readTo(f, length) && f.length() == length) {
                out.write(OK); out.flush()
                kept = true
                return f
            }
            refuse(s, out, BAD)
            return null
        } finally {
            if (!kept) f.delete()
        }
    }

    /** Whether [folder] (the system's temporary folder when null) has room for a book of [bytes], and a megabyte over. */
    private fun diskRoomFor(bytes: Long, folder: java.io.File?): Boolean {
        val space = (folder ?: java.io.File(System.getProperty("java.io.tmpdir") ?: ".")).usableSpace
        // 0 is "not known" (a folder the runtime cannot ask about): the write itself will say.
        return space == 0L || space >= bytes + (1L shl 20)
    }

    /** A pull's question: a short sealed frame that says "pull" under [secret]. */
    private fun readAsk(input: DataInputStream, secret: String): Boolean {
        val n = input.readInt()
        if (n <= 0 || n > MAX_ASK) return false
        val sealed = ByteArray(n)
        input.readFully(sealed)
        return open(secret, sealed)?.contentEquals(PULL) == true
    }

    /**
     * [why], then a moment for the other side to read it and close first: a little of what it still sends is read
     * into a small buffer and dropped (closing with bytes unread resets the connection, and the answer could be
     * lost with it). Nothing is kept.
     */
    private fun refuse(s: Socket, out: DataOutputStream, why: Int) {
        try {
            out.write(why); out.flush()
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
}
