package com.innovation313.roshankhata.data

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.text.Normalizer
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * A backup locked with the owner's own password (17 Nov 2026, the owner's decision: a backup file was plain text, and
 * whoever held it — a lost USB stick, a WhatsApp forward, a shared Drive folder — read the whole book). Optional: with
 * no password set every backup is written and read exactly as before.
 *
 * The file ("RKX1", version 1 of this format; frozen once shipped — a change is "RKX2", read beside it):
 *
 *     "RKX1"            4 bytes, the mark a plain backup (which starts with "{") never has
 *     kdf               1 byte, 1 = PBKDF2-HMAC-SHA256
 *     iterations        4 bytes, big-endian; written as [ITERATIONS], read only in [MIN_ITERATIONS]..[MAX_ITERATIONS]
 *     salt length       1 byte, 16
 *     salt              16 random bytes
 *     nonce prefix      8 random bytes
 *     records           each: plain length (4 bytes), last (1 byte: 0 or 1), the bytes sealed with AES-256-GCM
 *
 * The key is PBKDF2-HMAC-SHA256 of the password ([normalize]d, UTF-8) and the salt, 32 bytes, worked out by
 * [PinHash.pbkdf2] (Android 7 has no PBKDF2WithHmacSHA256). Each record's nonce is the prefix and its number; its
 * authenticated data is [LABEL] (which names the format's version), the whole header, its number and its last-flag —
 * so a header edited, a record moved, dropped, repeated or cut short, or bytes added after the end, never open.
 *
 * Record 0 is a short fixed text ([CHECK]): a wrong password is known from it before anything of the book is read, and a
 * record that does not open after it is damage, never "wrong password". The rest are the exact bytes the plain backup
 * would have been, [CHUNK] at a time: neither writing nor reading ever holds more than one record, so a big book is
 * streamed as before. Records, not one GCM over the file, because the JDK's GCM keeps the whole text until it has
 * checked the tag — a wholesaler's book would be held in memory to be read.
 *
 * Its own records, not [SealedChunks]': that is a WiFi protocol free to change between versions; a backup is a file
 * that must still open in ten years.
 */
object BackupSeal {
    /** "RKX1". */
    private val MAGIC = byteArrayOf(0x52, 0x4B, 0x58, 0x31)
    const val KDF_PBKDF2_SHA256 = 1
    /** OWASP's 2023 figure for PBKDF2-HMAC-SHA256 — five times the PIN's 120,000: a backup file can be attacked at leisure. */
    const val ITERATIONS = 600_000
    /** What a file may ask, at least and at most: a crafted file cannot make a phone work for minutes. */
    const val MIN_ITERATIONS = 100_000
    const val MAX_ITERATIONS = 2_000_000
    /** A record's plain bytes, at most. */
    const val CHUNK = 64 * 1024
    private const val SALT = 16
    private const val PREFIX = 8
    private const val TAG = 16
    /** The header's length: mark, kdf, iterations, salt length, salt, nonce prefix. */
    const val HEADER = 4 + 1 + 4 + 1 + SALT + PREFIX
    private val CHECK = "RKX1 Roshan Khata backup".toByteArray(Charsets.US_ASCII)
    private val LABEL = "rk-backup-seal-v1|".toByteArray(Charsets.US_ASCII)

    /** A sealed file that stopped opening part way (changed, cut short, bytes added): never a book read in part. */
    class Damaged(why: String) : IOException(why)

    /**
     * The password as it is used: spaces at either end taken off (a phone keyboard adds one after a word) and the
     * letters in one Unicode form (NFC), so the same password typed on two keyboards is the same key.
     */
    fun normalize(password: String): String = Normalizer.normalize(password.trim(), Normalizer.Form.NFC)

    /** Whether the first [length] bytes of [head] are a locked backup's mark. */
    fun isSealed(head: ByteArray, length: Int = head.size): Boolean =
        length >= MAGIC.size && head.size >= MAGIC.size && (MAGIC.indices).all { head[it] == MAGIC[it] }

    /** Whether [file] is a locked backup (false when it cannot be read). */
    fun isSealed(file: File): Boolean = runCatching {
        file.inputStream().use { val b = ByteArray(MAGIC.size); isSealed(b, fill(it, b)) }
    }.getOrDefault(false)

    /** Whether [input] starts with the mark; [input] must support mark/reset and is left where it was. */
    fun startsSealed(input: InputStream): Boolean {
        require(input.markSupported()) { "mark needed" }
        input.mark(MAGIC.size)
        val b = ByteArray(MAGIC.size)
        val n = fill(input, b)
        input.reset()
        return isSealed(b, n)
    }

    /** The 32-byte AES key of [password] with [salt] after [iterations] rounds. */
    fun key(password: String, salt: ByteArray, iterations: Int): SecretKeySpec =
        SecretKeySpec(PinHash.pbkdf2(normalize(password).toByteArray(Charsets.UTF_8), salt, iterations, 32), "AES")

    private fun header(iterations: Int, salt: ByteArray, prefix: ByteArray): ByteArray {
        val b = ByteArrayOutputStream(HEADER)
        DataOutputStream(b).apply {
            write(MAGIC)
            writeByte(KDF_PBKDF2_SHA256)
            writeInt(iterations)
            writeByte(salt.size)
            write(salt)
            write(prefix)
            flush()
        }
        return b.toByteArray()
    }

    private fun nonce(prefix: ByteArray, i: Int): ByteArray =
        prefix + byteArrayOf((i ushr 24).toByte(), (i ushr 16).toByte(), (i ushr 8).toByte(), i.toByte())

    private fun aad(header: ByteArray, i: Int, last: Boolean): ByteArray =
        LABEL + header + ("|" + i + "|" + (if (last) "1" else "0")).toByteArray(Charsets.US_ASCII)

    /**
     * Where the plain backup is written to be sealed onto [out]: the header and the check go at once, the rest a record
     * at a time; [OutputStream.close] writes the last record and closes [out]. Nothing is sealed until close for a
     * book under [CHUNK] bytes. [iterations] other than [ITERATIONS] are for tests.
     */
    fun sealing(out: OutputStream, password: String, iterations: Int = ITERATIONS, random: SecureRandom = SecureRandom()): OutputStream {
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS) { "iterations" }
        val salt = ByteArray(SALT).also { random.nextBytes(it) }
        val prefix = ByteArray(PREFIX).also { random.nextBytes(it) }
        val head = header(iterations, salt, prefix)
        val sealer = Sealer(DataOutputStream(out), key(password, salt, iterations), prefix, head)
        sealer.start()
        return sealer
    }

    /** [plain] copied into [into], sealed with [password]; [into] is the sealed file. */
    fun sealFile(plain: File, into: File, password: String, iterations: Int = ITERATIONS): File {
        into.parentFile?.mkdirs()
        plain.inputStream().buffered(CHUNK).use { input ->
            sealing(into.outputStream().buffered(CHUNK), password, iterations).use { input.copyTo(it, CHUNK) }
        }
        return into
    }

    /**
     * A writer for a backup's text into [out]: sealed with [password] when one is given, else plain as it always was.
     * Closing it finishes the file.
     */
    fun writer(out: OutputStream, password: String?, iterations: Int = ITERATIONS): java.io.Writer {
        val to = if (password == null) out else sealing(out, password, iterations)
        return java.io.BufferedWriter(java.io.OutputStreamWriter(to, Charsets.UTF_8), CHUNK)
    }

    private class Sealer(
        private val out: DataOutputStream,
        private val key: SecretKeySpec,
        private val prefix: ByteArray,
        private val head: ByteArray
    ) : OutputStream() {
        private val buf = ByteArray(CHUNK)
        private var n = 0
        private var i = 0
        private var closed = false

        fun start() {
            out.write(head)
            record(CHECK, CHECK.size, last = false)
        }

        private fun record(b: ByteArray, len: Int, last: Boolean) {
            if (i == Int.MAX_VALUE) throw IOException("too long")
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG * 8, nonce(prefix, i)))
            c.updateAAD(aad(head, i, last))
            val sealed = c.doFinal(b, 0, len)
            out.writeInt(len)
            out.writeByte(if (last) 1 else 0)
            out.write(sealed)
            i++
        }

        override fun write(b: Int) {
            if (closed) throw IOException("closed")
            // A full buffer goes only once more bytes come: until then it may be the last record.
            if (n == CHUNK) { record(buf, n, last = false); n = 0 }
            buf[n++] = b.toByte()
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (closed) throw IOException("closed")
            if (off < 0 || len < 0 || off + len > b.size) throw IndexOutOfBoundsException()
            var at = off
            var left = len
            while (left > 0) {
                if (n == CHUNK) { record(buf, n, last = false); n = 0 }
                val k = minOf(left, CHUNK - n)
                System.arraycopy(b, at, buf, n, k)
                n += k; at += k; left -= k
            }
        }

        /** Only the bytes already sealed go on: a record is sealed when it is full, or at close. */
        override fun flush() = out.flush()

        override fun close() {
            if (closed) return
            closed = true
            try {
                record(buf, n, last = true)
                out.flush()
            } finally {
                out.close()
            }
        }
    }

    /** What [open] found. */
    sealed class Open {
        /** The plain backup, read a record at a time; a record that does not open throws [Damaged]. */
        class Ok(val plain: InputStream) : Open()
        /** A locked backup and no password to try. */
        object NeedPassword : Open()
        /** None of the passwords opens it. */
        object WrongPassword : Open()
        /** Not a backup this version can read: an unknown header, or the check is not ours. */
        class Damaged(val why: String) : Open()
    }

    /**
     * Opens a locked backup from [input] (at its first byte): the header is read and the check record tried with each
     * of [passwords] in turn (each a PBKDF2 of [ITERATIONS] rounds, about a second on a phone — pass few). Nothing of
     * the book is read here.
     */
    fun open(input: InputStream, passwords: List<String>): Open {
        val data = DataInputStream(input)
        return try {
            val head = ByteArray(HEADER)
            if (fill(data, head) < HEADER || !isSealed(head)) return Open.Damaged("header")
            val kdf = head[4].toInt() and 0xFF
            val iterations = ((head[5].toInt() and 0xFF) shl 24) or ((head[6].toInt() and 0xFF) shl 16) or
                ((head[7].toInt() and 0xFF) shl 8) or (head[8].toInt() and 0xFF)
            val saltLength = head[9].toInt() and 0xFF
            if (kdf != KDF_PBKDF2_SHA256) return Open.Damaged("kdf $kdf")
            if (saltLength != SALT) return Open.Damaged("salt")
            if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) return Open.Damaged("iterations")
            val salt = head.copyOfRange(10, 10 + SALT)
            val prefix = head.copyOfRange(10 + SALT, 10 + SALT + PREFIX)
            val len = data.readInt()
            val flag = data.readUnsignedByte()
            if (len != CHECK.size || flag != 0) return Open.Damaged("check record")
            val sealed = ByteArray(len + TAG)
            data.readFully(sealed)
            val tries = passwords.map { normalize(it) }.distinct()
            if (tries.isEmpty()) return Open.NeedPassword
            for (p in tries) {
                val key = key(p, salt, iterations)
                val plain = openRecord(key, prefix, head, 0, false, sealed) ?: continue
                if (!plain.contentEquals(CHECK)) return Open.Damaged("check")
                return Open.Ok(Unsealer(data, key, prefix, head))
            }
            Open.WrongPassword
        } catch (e: IOException) {
            Open.Damaged("cut short")
        }
    }

    /**
     * Reads [plain] to its end, so a file whose last record is missing, changed, or followed by more bytes is known for
     * damaged even when the text inside looked whole; throws [Damaged] then.
     */
    fun finish(plain: InputStream) {
        val buf = ByteArray(8192)
        while (plain.read(buf) >= 0) Unit
    }

    /** [input] whose close() does nothing: a reader that closes at its end leaves it for [finish]. */
    fun notClosing(input: InputStream): InputStream = object : java.io.FilterInputStream(input) {
        override fun close() = Unit
    }

    private fun openRecord(key: SecretKeySpec, prefix: ByteArray, head: ByteArray, i: Int, last: Boolean, sealed: ByteArray): ByteArray? =
        try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG * 8, nonce(prefix, i)))
            c.updateAAD(aad(head, i, last))
            c.doFinal(sealed)
        } catch (e: GeneralSecurityException) {
            null
        }

    private class Unsealer(
        private val data: DataInputStream,
        private val key: SecretKeySpec,
        private val prefix: ByteArray,
        private val head: ByteArray
    ) : InputStream() {
        private var i = 1
        private var cur = ByteArray(0)
        private var pos = 0
        private var done = false
        private var ended = false

        /** The next record into [cur]; false once the last was read and nothing follows it. */
        private fun next(): Boolean {
            if (done) {
                if (!ended) {
                    ended = true
                    if (data.read() != -1) throw Damaged("bytes after the end")
                }
                return false
            }
            val len = try { data.readInt() } catch (e: EOFException) { throw Damaged("cut short") }
            if (len < 0 || len > CHUNK) throw Damaged("record $i length")
            val flag = try { data.readUnsignedByte() } catch (e: EOFException) { throw Damaged("cut short") }
            if (flag > 1) throw Damaged("record $i flag")
            val last = flag == 1
            val sealed = ByteArray(len + TAG)
            try { data.readFully(sealed) } catch (e: EOFException) { throw Damaged("cut short") }
            cur = openRecord(key, prefix, head, i, last, sealed) ?: throw Damaged("record $i does not open")
            pos = 0
            if (i == Int.MAX_VALUE) throw Damaged("too long")
            i++
            if (last) done = true
            return true
        }

        override fun read(): Int {
            while (pos >= cur.size) if (!next()) return -1
            return cur[pos++].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (off < 0 || len < 0 || off + len > b.size) throw IndexOutOfBoundsException()
            if (len == 0) return 0
            while (pos >= cur.size) if (!next()) return -1
            val k = minOf(len, cur.size - pos)
            System.arraycopy(cur, pos, b, off, k)
            pos += k
            return k
        }

        override fun close() = data.close()
    }

    /** Reads until [buf] is full or [input] ends; how many bytes came. */
    private fun fill(input: InputStream, buf: ByteArray): Int {
        var got = 0
        while (got < buf.size) {
            val r = input.read(buf, got, buf.size - got)
            if (r < 0) break
            got += r
        }
        return got
    }
}

/**
 * The owner's backup password, kept on this device only (17 Nov 2026): automatic backups (Drive, the computer's
 * outside folder) need it with nobody there to type it, so it is kept — sealed by the device's own lock ([Lock]: the
 * phone's Keystore, Windows' DPAPI), never plain, never in the book, a backup, app.properties or anything synced. One
 * password per device, for every shop on it. The platform supplies where it is kept ([Keep]) and the lock.
 *
 * When a password is set but cannot be read ([State.Unreadable]: the lock refused it or did not answer), a backup that
 * leaves the device is NOT made — never written plain instead: the owner believed his files were locked.
 */
object BackupPassword {
    /** At least this many characters (after [BackupSeal.normalize]): six digits fall to a guessing program in a minute. */
    const val MIN = 8
    const val MAX = 128

    /** Who did what (the audit log): the password set, changed or removed. Never the password itself. */
    const val AUDIT_SET = "backup_pw_set"
    const val AUDIT_CHANGED = "backup_pw_changed"
    const val AUDIT_REMOVED = "backup_pw_removed"

    /** The device's lock: [wrap] null when the device has none (then nothing is kept); [unwrap] null when not this device's. */
    interface Lock {
        fun wrap(plain: ByteArray): ByteArray?
        fun unwrap(wrapped: ByteArray): ByteArray?
    }

    /** Where the kept text lives: the phone's private preferences, a file beside the computer's books. */
    interface Keep {
        fun read(): String?
        fun write(value: String?)
    }

    sealed class State {
        object None : State()
        /** [id]: a random mark of this password, changed by every set (a changed password re-sends Drive's copy). */
        class Set(val password: String, val id: String) : State()
        /** A password is set and cannot be read now: no backup leaves the device until it is set again. */
        class Unreadable(val why: String) : State()
    }

    enum class Saved { OK, TOO_SHORT, TOO_LONG, NO_LOCK }

    private const val PREFIX = "p1:"
    /** Inside the lock, so a sealed sync key (the same lock) can never read as a password. */
    private val DOMAIN = "rk-backup-password|".toByteArray(Charsets.US_ASCII)

    /** Whether [password] may be set. */
    fun check(password: String): Saved {
        val p = BackupSeal.normalize(password)
        val n = p.codePointCount(0, p.length)
        return when {
            n < MIN -> Saved.TOO_SHORT
            n > MAX -> Saved.TOO_LONG
            else -> Saved.OK
        }
    }

    /** Keeps [password]; NO_LOCK when this device cannot seal it (then nothing is kept). [KeyLockUnavailable] passes through. */
    fun save(keep: Keep, lock: Lock, password: String, random: SecureRandom = SecureRandom()): Saved {
        val ok = check(password)
        if (ok != Saved.OK) return ok
        val wrapped = lock.wrap(DOMAIN + BackupSeal.normalize(password).toByteArray(Charsets.UTF_8)) ?: return Saved.NO_LOCK
        val id = ByteArray(8).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }
        keep.write(PREFIX + id + ":" + B64.encode(wrapped))
        return Saved.OK
    }

    fun remove(keep: Keep) = keep.write(null)

    /** Where the password stands; never throws. */
    fun state(keep: Keep, lock: Lock): State {
        val stored = try { keep.read() } catch (e: Exception) { return State.Unreadable("read") } ?: return State.None
        if (stored.isEmpty()) return State.None
        if (!stored.startsWith(PREFIX)) return State.Unreadable("format")
        val rest = stored.substring(PREFIX.length)
        val id = rest.substringBefore(':', "")
        val body = rest.substringAfter(':', "")
        if (id.isEmpty() || body.isEmpty()) return State.Unreadable("format")
        val wrapped = runCatching { B64.decode(body) }.getOrNull() ?: return State.Unreadable("format")
        val plain = try { lock.unwrap(wrapped) } catch (e: Exception) { return State.Unreadable("lock") }
            ?: return State.Unreadable("not this device's")
        if (plain.size <= DOMAIN.size || !plain.copyOfRange(0, DOMAIN.size).contentEquals(DOMAIN)) return State.Unreadable("domain")
        val password = String(plain, DOMAIN.size, plain.size - DOMAIN.size, Charsets.UTF_8)
        return if (check(password) == Saved.OK) State.Set(password, id) else State.Unreadable("length")
    }

    /** The password to seal with, null when none is set; [Unavailable] when one is set and cannot be read. */
    fun forSealing(state: State): String? = when (state) {
        State.None -> null
        is State.Set -> state.password
        is State.Unreadable -> throw Unavailable(state.why)
    }

    /** A backup that would leave the device was not made: its password is set and cannot be read (see [State.Unreadable]). */
    class Unavailable(why: String) : IllegalStateException("backup password unavailable: $why")

    /**
     * How many passwords the owner may type for one locked file before he is told it does not open (17 Nov 2026, the
     * review: a wrong typed password and a wrong kept one asked again and again until Cancel).
     */
    const val MAX_TRIES = 3

    /** What a reader does after a locked file did not open, with [tries] passwords already typed for it. */
    enum class Next {
        /** Ask for its password (nothing typed yet: no "wrong" said). */
        ASK,
        /** Ask again, saying the last one was wrong. */
        ASK_AGAIN,
        /** Stop asking and say it did not open ([MAX_TRIES] were tried). */
        GIVE_UP
    }

    fun next(tries: Int): Next = when {
        tries <= 0 -> Next.ASK
        tries < MAX_TRIES -> Next.ASK_AGAIN
        else -> Next.GIVE_UP
    }

    /** A mark of the password in use for a "has anything changed" signature: "" with none, so old signatures still match. */
    fun mark(state: State): String = when (state) {
        State.None -> ""
        is State.Set -> "|pw:" + state.id
        is State.Unreadable -> "|pw:?"
    }
}
