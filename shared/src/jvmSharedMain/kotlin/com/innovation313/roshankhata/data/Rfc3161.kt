package com.innovation313.roshankhata.data

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.util.Calendar
import java.util.TimeZone

/**
 * RFC 3161 time-stamping, by hand (the book seal, 4 Nov 2026): the request a Time Stamp Authority takes, and a small,
 * careful reading of its reply — no BouncyCastle, nothing added to either build. Only a 32-byte SHA-256 digest is sent.
 *
 * What is checked here, said plainly: the reply's status is granted (0) or granted with changes (1); it carries a
 * TimeStampToken (ContentInfo → SignedData → encapContentInfo of type TSTInfo); the TSTInfo's message imprint is
 * SHA-256 of exactly our digest and its nonce is ours; its genTime reads. **The TSA's signature is NOT checked here**
 * (that needs its certificate chain): the proof file tells how to check it with OpenSSL. A token is kept whole, as
 * the TSA sent it (DER), so that check can be made by anyone, any time later.
 */
object Rfc3161 {

    /** 2.16.840.1.101.3.4.2.1, SHA-256. */
    private val SHA256 = byteArrayOf(0x60, 0x86.toByte(), 0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x01)
    /** 1.2.840.113549.1.7.2, signedData. */
    private val SIGNED_DATA = byteArrayOf(0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x07, 0x02)
    /** 1.2.840.113549.1.9.16.1.4, id-ct-TSTInfo. */
    private val TST_INFO = byteArrayOf(0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x09, 0x10, 0x01, 0x04)

    const val CONTENT_TYPE = "application/timestamp-query"
    const val TIMEOUT_MS = 15_000
    /** A reply bigger than this is not a time stamp (a token with its certificates is a few KB). */
    const val MAX_REPLY = 64 * 1024

    /** A random positive 64-bit nonce. */
    fun newNonce(random: SecureRandom = SecureRandom()): BigInteger {
        val b = ByteArray(8).also { random.nextBytes(it) }
        val n = BigInteger(1, b)
        return if (n.signum() == 0) BigInteger.ONE else n
    }

    // ---------- DER out ----------

    private fun length(n: Int): ByteArray = when {
        n < 0x80 -> byteArrayOf(n.toByte())
        n < 0x100 -> byteArrayOf(0x81.toByte(), n.toByte())
        n < 0x10000 -> byteArrayOf(0x82.toByte(), (n ushr 8).toByte(), n.toByte())
        else -> byteArrayOf(0x83.toByte(), (n ushr 16).toByte(), (n ushr 8).toByte(), n.toByte())
    }

    private fun tlv(tag: Int, vararg parts: ByteArray): ByteArray {
        val body = ByteArrayOutputStream().also { o -> parts.forEach { o.write(it) } }.toByteArray()
        return byteArrayOf(tag.toByte()) + length(body.size) + body
    }

    /**
     * TimeStampReq { version 1, messageImprint { sha256 with NULL params, OCTET STRING digest }, nonce, certReq TRUE },
     * DER. [nonce] must be positive; it is written in its shortest two's-complement form.
     */
    fun request(digest: ByteArray, nonce: BigInteger): ByteArray {
        require(digest.size == 32) { "a SHA-256 digest is 32 bytes" }
        require(nonce.signum() > 0) { "the nonce is positive" }
        val algorithm = tlv(0x30, tlv(0x06, SHA256), byteArrayOf(0x05, 0x00))
        val imprint = tlv(0x30, algorithm, tlv(0x04, digest))
        return tlv(0x30, tlv(0x02, byteArrayOf(1)), imprint, tlv(0x02, nonce.toByteArray()), byteArrayOf(0x01, 0x01, 0xFF.toByte()))
    }

    // ---------- DER in ----------

    /** One TLV inside [buf]: [start] is its tag, its value runs [valueStart, end). Definite lengths only. */
    class Tlv(val buf: ByteArray, val start: Int, val tag: Int, val valueStart: Int, val end: Int) {
        val value: ByteArray get() = buf.copyOfRange(valueStart, end)
        val whole: ByteArray get() = buf.copyOfRange(start, end)
        val constructed: Boolean get() = tag and 0x20 != 0
        fun children(): List<Tlv> {
            val out = ArrayList<Tlv>()
            var at = valueStart
            while (at < end) { val t = read(buf, at, end); out += t; at = t.end }
            return out
        }
    }

    /** The TLV at [at], which must end by [limit]; throws on a long tag, an indefinite or oversized length. */
    fun read(buf: ByteArray, at: Int, limit: Int = buf.size): Tlv {
        require(at < limit) { "DER: nothing to read" }
        val tag = buf[at].toInt() and 0xff
        require(tag and 0x1f != 0x1f) { "DER: long tag" }
        require(at + 1 < limit) { "DER: no length" }
        var p = at + 1
        val first = buf[p++].toInt() and 0xff
        val len: Int = if (first < 0x80) first else {
            val n = first and 0x7f
            require(n in 1..3) { "DER: length form" }
            var v = 0
            repeat(n) { require(p < limit) { "DER: cut length" }; v = (v shl 8) or (buf[p++].toInt() and 0xff) }
            v
        }
        require(len >= 0 && p + len <= limit) { "DER: value past its end" }
        return Tlv(buf, at, tag, p, p + len)
    }

    /** A whole buffer that is exactly one TLV. */
    private fun only(buf: ByteArray): Tlv = read(buf, 0, buf.size).also { require(it.end == buf.size) { "DER: bytes after the value" } }

    private fun Tlv.expect(tag: Int): Tlv { require(this.tag == tag) { "DER: tag ${"%02x".format(this.tag)} where ${"%02x".format(tag)} was due" }; return this }

    // ---------- The reply ----------

    sealed class Reply {
        /** [token] is the TimeStampToken's DER; [genTime] the TSA's time in epoch milliseconds. */
        class Granted(val token: ByteArray, val genTime: Long, val status: Int) : Reply()
        class Refused(val why: String) : Reply()
    }

    /** Reads a TimeStampResp for [digest] and [nonce]. Never throws: anything wrong is a [Reply.Refused]. */
    fun parseReply(bytes: ByteArray, digest: ByteArray, nonce: BigInteger): Reply = try {
        val resp = only(bytes).expect(0x30).children()
        require(resp.isNotEmpty()) { "empty reply" }
        val statusInfo = resp[0].expect(0x30).children()
        val status = BigInteger(statusInfo.first().expect(0x02).value).toInt()
        if (status != 0 && status != 1) Reply.Refused("status $status")
        else {
            val token = resp.getOrNull(1) ?: throw IllegalArgumentException("granted with no token")
            val info = tstInfo(token)
            checkInfo(info, digest, nonce)?.let { Reply.Refused(it) } ?: Reply.Granted(token.whole, genTimeOf(info), status)
        }
    } catch (e: Exception) {
        Reply.Refused(e.message ?: e.javaClass.simpleName)
    }

    /** A stored token read again (the proof screen): its genTime, imprint and nonce-free checks. Null when it does not read. */
    fun tokenTime(token: ByteArray, digest: ByteArray): Long? = runCatching {
        val info = tstInfo(only(token))
        if (checkInfo(info, digest, null) != null) null else genTimeOf(info)
    }.getOrNull()

    /** ContentInfo → [0] SignedData → encapContentInfo → [0] OCTET STRING → TSTInfo's children. */
    private fun tstInfo(token: Tlv): List<Tlv> {
        val ci = token.expect(0x30).children()
        require(ci.size >= 2 && ci[0].expect(0x06).value.contentEquals(SIGNED_DATA)) { "not SignedData" }
        val signed = ci[1].expect(0xA0).children().single().expect(0x30).children()
        // version INTEGER, digestAlgorithms SET, encapContentInfo SEQUENCE, …
        val encap = signed.getOrNull(2)?.expect(0x30)?.children() ?: throw IllegalArgumentException("no encapContentInfo")
        require(encap.size >= 2 && encap[0].expect(0x06).value.contentEquals(TST_INFO)) { "not a TSTInfo" }
        val octet = encap[1].expect(0xA0).children().single()
        val content = when (octet.tag) {
            0x04 -> octet.value
            // A constructed OCTET STRING (BER): its pieces in order.
            0x24 -> ByteArrayOutputStream().also { o -> octet.children().forEach { o.write(it.expect(0x04).value) } }.toByteArray()
            else -> throw IllegalArgumentException("eContent is not an OCTET STRING")
        }
        return only(content).expect(0x30).children()
    }

    /** Null when the TSTInfo is for [digest] (and [nonce], when given); else why not. */
    private fun checkInfo(info: List<Tlv>, digest: ByteArray, nonce: BigInteger?): String? {
        // version INTEGER, policy OID, messageImprint, serialNumber INTEGER, genTime, [accuracy], [ordering], [nonce], …
        if (info.size < 5) return "short TSTInfo"
        info[0].expect(0x02); info[1].expect(0x06)
        val imprint = info[2].expect(0x30).children()
        val alg = imprint[0].expect(0x30).children()
        if (!alg[0].expect(0x06).value.contentEquals(SHA256)) return "not SHA-256"
        if (!imprint[1].expect(0x04).value.contentEquals(digest)) return "imprint is not our digest"
        info[3].expect(0x02); info[4].expect(0x18)
        if (nonce != null) {
            val theirs = info.drop(5).firstOrNull { it.tag == 0x02 } ?: return "no nonce"
            if (BigInteger(theirs.value) != nonce) return "nonce is not ours"
        }
        return null
    }

    private fun genTimeOf(info: List<Tlv>): Long = generalizedTime(String(info[4].expect(0x18).value, Charsets.US_ASCII))

    private val TIME = Regex("^(\\d{4})(\\d{2})(\\d{2})(\\d{2})(\\d{2})(\\d{2})(?:[.,](\\d+))?Z\$")

    /** "YYYYMMDDHHMMSS[.fff…]Z" as epoch milliseconds (UTC); throws on any other shape. */
    fun generalizedTime(text: String): Long {
        val m = TIME.matchEntire(text) ?: throw IllegalArgumentException("genTime $text")
        val g = m.groupValues
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(g[1].toInt(), g[2].toInt() - 1, g[3].toInt(), g[4].toInt(), g[5].toInt(), g[6].toInt())
        }
        val ms = g[7].takeIf { it.isNotEmpty() }?.padEnd(3, '0')?.substring(0, 3)?.toInt() ?: 0
        return cal.timeInMillis + ms
    }

    // ---------- The road ----------

    /** One POST; a test passes its own. Throws when the TSA cannot be reached. */
    fun interface Http {
        fun post(url: String, body: ByteArray): Pair<Int, ByteArray>
    }

    val plain = Http { url, body ->
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = TIMEOUT_MS
            c.readTimeout = TIMEOUT_MS
            c.useCaches = false
            c.instanceFollowRedirects = false
            c.setRequestProperty("Content-Type", CONTENT_TYPE)
            c.setRequestProperty("Accept", "application/timestamp-reply")
            c.doOutput = true
            c.setFixedLengthStreamingMode(body.size)
            c.outputStream.use { it.write(body) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val bytes = stream?.use { s ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                while (true) {
                    val n = s.read(buf); if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_REPLY) throw java.io.IOException("reply too big")
                }
                out.toByteArray()
            } ?: ByteArray(0)
            code to bytes
        } finally {
            c.disconnect()
        }
    }

    /** Asks [url] to stamp [digest]; a refusal or any failure on the way is a [Reply.Refused]. */
    fun stamp(http: Http, url: String, digest: ByteArray, random: SecureRandom = SecureRandom()): Reply {
        val nonce = newNonce(random)
        val (code, body) = try { http.post(url, request(digest, nonce)) } catch (e: Exception) {
            return Reply.Refused("unreachable: ${e.javaClass.simpleName}")
        }
        if (code != 200) return Reply.Refused("http $code")
        return parseReply(body, digest, nonce)
    }
}
