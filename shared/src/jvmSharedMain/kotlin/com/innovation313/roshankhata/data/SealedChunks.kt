package com.innovation313.roshankhata.data

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * A whole book over the shop's WiFi a small piece at a time (23 Oct 2026, the
 * review report: a pairing frame, and the phone's pull of the computer's book,
 * were gathered whole in memory — up to 256 MB — and only then opened, so a
 * stranger on the WiFi could make a device hold a quarter of a gigabyte before
 * anything was checked, and a big book ran a phone out of memory).
 *
 * The stream: an 8-byte random prefix, then records. Each record is its plain
 * length (4 bytes), a byte saying whether it is the last, and its bytes sealed
 * with AES-256-GCM under a nonce made of the prefix and the record's number;
 * the record's number and the last-flag are in the authenticated data, so a
 * record moved, dropped, repeated, or a stream cut short, does not open.
 *
 * Record 0 is a small head (the pairing's identity, a length) and is opened
 * before anything more is read: only a sender holding the code's secret gets
 * further than [MAX_HEAD] bytes. The rest is written to a file record by
 * record ([Reader.readTo]); neither side ever holds more than one record.
 */
object SealedChunks {

    /** A record's plain bytes, at most. */
    const val CHUNK = 64 * 1024
    /** The head record, at most: read before the sender is known. */
    const val MAX_HEAD = 64 * 1024
    private const val PREFIX = 8
    private const val TAG = 16

    private fun nonce(prefix: ByteArray, i: Int): ByteArray =
        prefix + byteArrayOf((i ushr 24).toByte(), (i ushr 16).toByte(), (i ushr 8).toByte(), i.toByte())

    private fun aad(label: ByteArray, i: Int, last: Boolean): ByteArray =
        label + ("|" + i + "|" + (if (last) "1" else "0")).toByteArray(Charsets.US_ASCII)

    /** Reads until [buf] is full or [input] ends; how many bytes came. */
    private fun fill(input: InputStream, buf: ByteArray): Int {
        var got = 0
        while (got < buf.size) {
            val n = input.read(buf, got, buf.size - got)
            if (n < 0) break
            got += n
        }
        return got
    }

    /**
     * [head] as record 0, then [body] to its end, sealed under [key] for [label]
     * and written to [out]. Neither is held whole: [body] is read a record at a time.
     * [afterHead], when set and there is a body, runs once record 0 is flushed and
     * before the body goes (the phone's send waits there for the computer's go,
     * 23 Oct 2026); what it throws ends the write.
     */
    fun write(out: DataOutputStream, key: SecretKeySpec, label: ByteArray, head: ByteArray, body: InputStream?, afterHead: (() -> Unit)? = null) {
        require(head.size <= MAX_HEAD) { "head too long" }
        val prefix = ByteArray(PREFIX).also { SecureRandom().nextBytes(it) }
        out.write(prefix)
        var i = 0
        fun record(b: ByteArray, len: Int, last: Boolean) {
            if (i == Int.MAX_VALUE) throw IOException("too long")
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG * 8, nonce(prefix, i)))
            c.updateAAD(aad(label, i, last))
            val sealed = c.doFinal(b, 0, len)
            out.writeInt(len)
            out.writeByte(if (last) 1 else 0)
            out.write(sealed)
            i++
        }
        if (body == null) {
            record(head, head.size, true)
        } else {
            var cur = ByteArray(CHUNK)
            var next = ByteArray(CHUNK)
            var curLen = fill(body, cur)
            record(head, head.size, curLen == 0)
            if (afterHead != null) { out.flush(); afterHead() }
            while (curLen > 0) {
                // A short record is the end of the body: nothing more is read after it.
                val nextLen = if (curLen < CHUNK) 0 else fill(body, next)
                record(cur, curLen, nextLen == 0)
                val t = cur; cur = next; next = t
                curLen = nextLen
            }
        }
        out.flush()
    }

    /** The receiving side of one stream from [input], opened under [key] for [label]. Reads the prefix at once. */
    class Reader(private val input: DataInputStream, private val key: SecretKeySpec, private val label: ByteArray) {
        private val prefix = ByteArray(SealedChunks.PREFIX).also { input.readFully(it) }
        private var i = 0

        /** The last record was read: the stream is whole. */
        var done = false
            private set

        /**
         * The next record's plain bytes; null when it is longer than [max], or does not open (another key, changed
         * on the way, out of order). Throws an IOException when the connection ends or stalls.
         */
        fun next(max: Int): ByteArray? {
            if (done) return null
            val len = input.readInt()
            if (len < 0 || len > max) return null
            val flag = input.readUnsignedByte()
            if (flag > 1) return null
            val last = flag == 1
            val sealed = ByteArray(len + SealedChunks.TAG)
            input.readFully(sealed)
            val plain = try {
                val c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(SealedChunks.TAG * 8, SealedChunks.nonce(prefix, i)))
                c.updateAAD(SealedChunks.aad(label, i, last))
                c.doFinal(sealed)
            } catch (e: java.security.GeneralSecurityException) {
                return null
            }
            i++
            if (last) done = true
            return plain
        }

        /**
         * Every record left, written to [file] in order. False when one does not open, or the whole would pass [cap]
         * bytes (the file is then left for the caller to delete). Throws an IOException when the connection fails.
         */
        fun readTo(file: File, cap: Long): Boolean {
            var total = 0L
            file.outputStream().buffered(1 shl 16).use { out ->
                while (!done) {
                    val b = next(SealedChunks.CHUNK) ?: return false
                    total += b.size
                    if (total > cap) return false
                    out.write(b)
                }
            }
            return true
        }
    }
}
