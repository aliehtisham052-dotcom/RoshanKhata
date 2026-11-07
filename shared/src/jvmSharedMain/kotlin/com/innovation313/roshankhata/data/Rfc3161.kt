package com.innovation313.roshankhata.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import javax.security.auth.x500.X500Principal

/**
 * RFC 3161 time-stamping, by hand (the book seal, 4 Nov 2026): the request a Time Stamp Authority takes, and a small,
 * careful reading of its reply — no BouncyCastle, nothing added to either build. Only a 32-byte SHA-256 digest is sent.
 *
 * What is checked here, said plainly (7 Nov 2026, the security review's M1): the reply's status is granted (0) or
 * granted with changes (1); it carries a TimeStampToken (ContentInfo → SignedData → encapContentInfo of type TSTInfo);
 * the TSTInfo's message imprint is SHA-256 of exactly our digest and its nonce is ours; its genTime reads; and the
 * token's one SignerInfo signs it ([verify]): its signed attributes name the content a TSTInfo and carry the digest of
 * exactly that TSTInfo, and their signature verifies with the public key of the certificate the token carries for that
 * signer — a certificate for time-stamping (the critical extended key usage of RFC 3161 §2.3), valid at genTime.
 * **Whose certificate it is, is NOT known here**: that is the TSA's CA chain, which the app does not hold. The proof
 * file tells how to check it with OpenSSL. So a token kept here is one that adds up, never one called verified; a token
 * is kept whole, as the TSA sent it (DER), so that check can be made by anyone, any time later.
 */
object Rfc3161 {

    /** 2.16.840.1.101.3.4.2.1, SHA-256. */
    private val SHA256 = byteArrayOf(0x60, 0x86.toByte(), 0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x01)
    /** 1.2.840.113549.1.7.2, signedData. */
    private val SIGNED_DATA = byteArrayOf(0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x07, 0x02)
    /** 1.2.840.113549.1.9.16.1.4, id-ct-TSTInfo. */
    private val TST_INFO = byteArrayOf(0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x09, 0x10, 0x01, 0x04)

    /** The content-type and message-digest signed attributes (RFC 5652 §11.1, §11.2). */
    private const val ATTR_CONTENT_TYPE = "1.2.840.113549.1.9.3"
    private const val ATTR_MESSAGE_DIGEST = "1.2.840.113549.1.9.4"
    /** The extended key usage extension and id-kp-timeStamping (RFC 3161 §2.3); the subject key identifier extension. */
    private const val EXT_KEY_USAGE = "2.5.29.37"
    private const val KP_TIME_STAMPING = "1.3.6.1.5.5.7.3.8"
    private const val EXT_SUBJECT_KEY_ID = "2.5.29.14"

    /** A SignerInfo's digest algorithm, by OID, as the JCA names it. */
    private val DIGESTS = mapOf(
        "1.3.14.3.2.26" to "SHA-1",
        "2.16.840.1.101.3.4.2.1" to "SHA-256",
        "2.16.840.1.101.3.4.2.2" to "SHA-384",
        "2.16.840.1.101.3.4.2.3" to "SHA-512"
    )

    const val CONTENT_TYPE = "application/timestamp-query"
    /** Connecting, and each read: a TSA that says nothing for this long is given up. */
    const val TIMEOUT_MS = 15_000
    /**
     * The whole request, from connecting to the reply's last byte (7 Nov 2026, the review's M4): [TIMEOUT_MS] bounds
     * each read, not the request, and an answer trickling one byte every 14 s kept one open for days.
     */
    const val DEADLINE_MS = 45_000L
    /** A reply bigger than this is not a time stamp (a token with its certificates is a few KB). */
    const val MAX_REPLY = 64 * 1024
    /** How far a TSA's genTime may stand from the moment it was asked (this device's clock, which may be off by hours). */
    const val GEN_TIME_WINDOW_MS = 24L * 60 * 60 * 1000
    /** The app's name only: the system's default agent names the phone's model and Android build (M8). */
    const val USER_AGENT = "RoshanKhata"

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
     * DER. [nonce] must be positive; it is written in its shortest two's-complement form. certReq asks the TSA to carry
     * its certificate in the token, which the signature check here and OpenSSL's later both need.
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

    /** An OBJECT IDENTIFIER's value as dotted text ("1.2.840.113549.1.9.4"); throws on a malformed one. */
    fun oid(value: ByteArray): String {
        require(value.isNotEmpty()) { "DER: empty OID" }
        val arcs = ArrayList<Long>()
        var v = 0L
        var fresh = true
        for (b in value) {
            val x = b.toInt() and 0xff
            require(!(fresh && x == 0x80)) { "DER: OID padding" }
            require(v < (1L shl 49)) { "DER: OID arc too big" }
            v = (v shl 7) or (x and 0x7f).toLong()
            fresh = x and 0x80 == 0
            if (fresh) { arcs += v; v = 0 }
        }
        require(fresh) { "DER: cut OID" }
        val first = arcs[0]
        val head = when {
            first < 40 -> listOf(0L, first)
            first < 80 -> listOf(1L, first - 40)
            else -> listOf(2L, first - 80)
        }
        return (head + arcs.drop(1)).joinToString(".")
    }

    // ---------- The reply ----------

    sealed class Reply {
        /** [token] is the TimeStampToken's DER; [genTime] the TSA's time in epoch milliseconds. */
        class Granted(val token: ByteArray, val genTime: Long, val status: Int) : Reply()
        class Refused(val why: String) : Reply()
    }

    /** What a token says when read on this device: [Holds] with its genTime, or [Fails] with why not. */
    sealed class Verdict {
        class Holds(val genTime: Long) : Verdict()
        class Fails(val why: String) : Verdict()
    }

    /**
     * Reads a TimeStampResp for [digest] and [nonce]: granted only when its token [verify]s. Never throws: anything
     * wrong is a [Reply.Refused], and a refused answer is not kept — its witness is asked again.
     */
    fun parseReply(bytes: ByteArray, digest: ByteArray, nonce: BigInteger): Reply = try {
        val resp = only(bytes).expect(0x30).children()
        require(resp.isNotEmpty()) { "empty reply" }
        val statusInfo = resp[0].expect(0x30).children()
        val status = BigInteger(statusInfo.first().expect(0x02).value).toInt()
        if (status != 0 && status != 1) Reply.Refused("status $status")
        else {
            val token = resp.getOrNull(1) ?: throw IllegalArgumentException("granted with no token")
            when (val v = verify(token.whole, digest, nonce)) {
                is Verdict.Holds -> Reply.Granted(token.whole, v.genTime, status)
                is Verdict.Fails -> Reply.Refused(v.why)
            }
        }
    } catch (e: Exception) {
        Reply.Refused(e.message ?: e.javaClass.simpleName)
    }

    /**
     * Everything about [token] (a TimeStampToken's DER) that can be checked without the TSA's CA certificates: it is
     * SignedData around a TSTInfo for [digest] (and [nonce], when given) whose genTime reads, and its one signer signs
     * it ([signerHolds]). Never throws.
     */
    fun verify(token: ByteArray, digest: ByteArray, nonce: BigInteger? = null): Verdict {
        return try {
            val t = only(token)
            val (content, info) = tstInfo(t)
            val wrong = checkInfo(info, digest, nonce)
            if (wrong != null) Verdict.Fails(wrong)
            else {
                val genTime = genTimeOf(info)
                val unsigned = signerHolds(t, content, genTime)
                if (unsigned != null) Verdict.Fails(unsigned) else Verdict.Holds(genTime)
            }
        } catch (e: Exception) {
            Verdict.Fails(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * A stored token read again (the proof, the screens): its genTime when it [verify]s for [digest] — the time is the
     * token's own, never one written beside it. Null when it does not.
     */
    fun tokenTime(token: ByteArray, digest: ByteArray): Long? = (verify(token, digest) as? Verdict.Holds)?.genTime

    /**
     * ContentInfo → [0] SignedData → encapContentInfo → [0] OCTET STRING: the eContent's octets (what the
     * message-digest attribute is the digest of) and the TSTInfo's children.
     */
    private fun tstInfo(token: Tlv): Pair<ByteArray, List<Tlv>> {
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
        return content to only(content).expect(0x30).children()
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

    // ---------- The signature (7 Nov 2026, the review's M1) ----------

    /**
     * Null when the token's one SignerInfo (RFC 5652 §5.3; RFC 3161 §2.4.2 allows no other) signs it: its signed
     * attributes name the content a TSTInfo and carry the digest of exactly [content] by the SignerInfo's own digest
     * algorithm, and their signature verifies — RSA PKCS #1 v1.5 or ECDSA, as the SignerInfo names it — with the public
     * key of the certificate the token carries for that signer (by issuer and serial number, or by key identifier),
     * a certificate whose critical extended key usage is time-stamping and which was valid at [genTime]. Else why not.
     */
    private fun signerHolds(token: Tlv, content: ByteArray, genTime: Long): String? {
        val signed = token.children()[1].children().single().children()
        // After version, digestAlgorithms and encapContentInfo: [0] certificates, [1] crls, then the signerInfos SET.
        val rest = signed.drop(3)
        val infos = rest.lastOrNull()?.takeIf { it.tag == 0x31 } ?: return "no signerInfos"
        if (rest.dropLast(1).any { it.tag != 0xA0 && it.tag != 0xA1 }) return "SignedData out of shape"
        val certs = rest.firstOrNull { it.tag == 0xA0 }?.children()?.filter { it.tag == 0x30 }.orEmpty()
        val signers = infos.children()
        if (signers.size != 1) return "${signers.size} signers"
        // version, sid, digestAlgorithm, [0] signedAttrs, signatureAlgorithm, signature, [1] unsignedAttrs
        val si = signers[0].expect(0x30).children()
        if (si.size < 6) return "short SignerInfo"
        si[0].expect(0x02)
        val sid = si[1]
        val digestName = DIGESTS[oid(si[2].expect(0x30).children()[0].expect(0x06).value)] ?: return "digest algorithm"
        val attrs = si[3].takeIf { it.tag == 0xA0 } ?: return "no signed attributes"
        val algorithm = oid(si[4].expect(0x30).children()[0].expect(0x06).value)
        val signature = si[5].expect(0x04).value
        val signatureName = signatureName(algorithm, digestName) ?: return "signature algorithm $algorithm"

        val byType = attrs.children().map { a ->
            val parts = a.expect(0x30).children()
            oid(parts[0].expect(0x06).value) to parts[1].expect(0x31).children()
        }
        val type = byType.filter { it.first == ATTR_CONTENT_TYPE }
        if (type.size != 1 || type[0].second.size != 1 || !type[0].second[0].expect(0x06).value.contentEquals(TST_INFO)) {
            return "content type not signed"
        }
        val md = byType.filter { it.first == ATTR_MESSAGE_DIGEST }
        if (md.size != 1 || md[0].second.size != 1) return "message digest not signed"
        if (!md[0].second[0].expect(0x04).value.contentEquals(MessageDigest.getInstance(digestName).digest(content))) {
            return "message digest is not the TSTInfo's"
        }

        val factory = CertificateFactory.getInstance("X.509")
        val cert = certs.asSequence()
            .mapNotNull { c -> runCatching { factory.generateCertificate(ByteArrayInputStream(c.whole)) as X509Certificate }.getOrNull() }
            .firstOrNull { identifies(sid, it) } ?: return "no certificate for the signer"
        val critical = cert.criticalExtensionOIDs ?: emptySet<String>()
        val usage = runCatching { cert.extendedKeyUsage }.getOrNull() ?: emptyList<String>()
        if (EXT_KEY_USAGE !in critical || KP_TIME_STAMPING !in usage) return "certificate not for time-stamping"
        if (runCatching { cert.checkValidity(Date(genTime)) }.isFailure) return "certificate not valid at genTime"

        // Signed as a SET OF (RFC 5652 §5.4): the attributes' own bytes with their [0] tag made 0x31 — or, for a signer
        // that sent them unsorted, sorted as DER sorts a SET OF.
        val asSent = attrs.whole.also { it[0] = 0x31 }
        val holds = verifies(signatureName, cert, asSent, signature) || verifies(signatureName, cert, derSorted(attrs), signature)
        return if (holds) null else "signature does not verify"
    }

    /** The JCA name of a SignerInfo's signature: by its own OID, or the key's OID with the SignerInfo's [digest]. */
    private fun signatureName(algorithm: String, digest: String): String? {
        val hash = digest.replace("-", "")
        return when (algorithm) {
            "1.2.840.113549.1.1.1" -> "${hash}withRSA"
            "1.2.840.113549.1.1.5" -> "SHA1withRSA"
            "1.2.840.113549.1.1.11" -> "SHA256withRSA"
            "1.2.840.113549.1.1.12" -> "SHA384withRSA"
            "1.2.840.113549.1.1.13" -> "SHA512withRSA"
            "1.2.840.10045.2.1" -> "${hash}withECDSA"
            "1.2.840.10045.4.1" -> "SHA1withECDSA"
            "1.2.840.10045.4.3.2" -> "SHA256withECDSA"
            "1.2.840.10045.4.3.3" -> "SHA384withECDSA"
            "1.2.840.10045.4.3.4" -> "SHA512withECDSA"
            else -> null
        }
    }

    /** Whether [cert] is the one a SignerInfo's [sid] names: IssuerAndSerialNumber, or [0] its subject key identifier. */
    private fun identifies(sid: Tlv, cert: X509Certificate): Boolean = runCatching {
        when (sid.tag) {
            0x30 -> {
                val parts = sid.children()
                X500Principal(parts[0].expect(0x30).whole) == cert.issuerX500Principal &&
                    BigInteger(parts[1].expect(0x02).value) == cert.serialNumber
            }
            // The extension's value is an OCTET STRING holding the key identifier's OCTET STRING.
            0x80 -> cert.getExtensionValue(EXT_SUBJECT_KEY_ID)?.let { ext ->
                only(only(ext).expect(0x04).value).expect(0x04).value.contentEquals(sid.value)
            } ?: false
            else -> false
        }
    }.getOrDefault(false)

    private fun verifies(name: String, cert: X509Certificate, data: ByteArray, signature: ByteArray): Boolean = runCatching {
        val s = Signature.getInstance(name)
        s.initVerify(cert.publicKey)
        s.update(data)
        s.verify(signature)
    }.getOrDefault(false)

    /** A SET OF's elements in DER's order (X.690 §11.6): ascending as octet strings, the shorter padded with zeros. */
    private fun derSorted(set: Tlv): ByteArray {
        val parts = set.children().map { it.whole }.sortedWith { a, b ->
            var order = 0
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = if (i < a.size) a[i].toInt() and 0xff else 0
                val y = if (i < b.size) b[i].toInt() and 0xff else 0
                if (x != y) { order = x - y; break }
            }
            order
        }
        return tlv(0x31, *parts.toTypedArray())
    }

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

    /**
     * The real road: one POST that must be over within [deadlineMs] (connecting and every byte of the answer), each
     * read within [timeoutMs]. The request runs on a thread of its own; at the deadline its connection is cut — which
     * ends a read blocked on it — and the caller is told at once, whatever the thread is still doing.
     */
    fun http(deadlineMs: Long = DEADLINE_MS, timeoutMs: Int = TIMEOUT_MS): Http = Http { url, body ->
        val c = URL(url).openConnection() as HttpURLConnection
        val until = System.nanoTime() + deadlineMs * 1_000_000
        var answer: Pair<Int, ByteArray>? = null
        var failure: Throwable? = null
        val worker = Thread({
            try { answer = exchange(c, body, timeoutMs, until) } catch (t: Throwable) { failure = t }
        }, "rk-tsa")
        worker.isDaemon = true
        worker.start()
        val over = try {
            worker.join(deadlineMs)
            !worker.isAlive
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        if (!over) {
            runCatching { c.disconnect() }
            throw SocketTimeoutException("no whole answer within $deadlineMs ms")
        }
        failure?.let { throw it }
        answer ?: throw IOException("no answer")
    }

    val plain: Http = http()

    private fun exchange(c: HttpURLConnection, body: ByteArray, timeoutMs: Int, until: Long): Pair<Int, ByteArray> {
        try {
            c.requestMethod = "POST"
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.useCaches = false
            c.instanceFollowRedirects = false
            c.setRequestProperty("Content-Type", CONTENT_TYPE)
            c.setRequestProperty("Accept", "application/timestamp-reply")
            c.setRequestProperty("User-Agent", USER_AGENT)
            c.doOutput = true
            c.setFixedLengthStreamingMode(body.size)
            c.outputStream.use { it.write(body) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val bytes = stream?.use { s ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                while (true) {
                    if (System.nanoTime() - until > 0) throw SocketTimeoutException("past the deadline")
                    val n = s.read(buf); if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_REPLY) throw IOException("reply too big")
                }
                out.toByteArray()
            } ?: ByteArray(0)
            return code to bytes
        } finally {
            c.disconnect()
        }
    }

    /**
     * Asks [url] to stamp [digest]; a refusal or any failure on the way is a [Reply.Refused]. A granted answer whose
     * genTime is more than [GEN_TIME_WINDOW_MS] from the moment it was asked ([clock]) is refused too.
     */
    fun stamp(
        http: Http,
        url: String,
        digest: ByteArray,
        random: SecureRandom = SecureRandom(),
        clock: () -> Long = { System.currentTimeMillis() }
    ): Reply {
        val nonce = newNonce(random)
        val sent = clock()
        val (code, body) = try { http.post(url, request(digest, nonce)) } catch (e: Exception) {
            return Reply.Refused("unreachable: ${e.javaClass.simpleName}")
        }
        if (code != 200) return Reply.Refused("http $code")
        val reply = parseReply(body, digest, nonce)
        if (reply is Reply.Granted && (reply.genTime < sent - GEN_TIME_WINDOW_MS || reply.genTime > clock() + GEN_TIME_WINDOW_MS)) {
            return Reply.Refused("genTime outside the window")
        }
        return reply
    }
}
