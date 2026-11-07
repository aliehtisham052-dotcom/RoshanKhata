package com.innovation313.roshankhata.data

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * A Time Stamp Authority for tests (7 Nov 2026, the security review's M1): its own key (RSA 2048 or EC P-256) and a
 * self-made certificate, both made here with the JDK alone, and tokens signed with them as RFC 3161 and RFC 5652 shape
 * them — so the on-device checks of [Rfc3161.verify] meet real signatures, and each [Fault] breaks exactly one of them.
 * Made-up names only; nothing here is anybody's certificate.
 */
class TestTsa(
    val ec: Boolean = false,
    /** The extended key usage: present, and critical, as RFC 3161 §2.3 asks. */
    private val eku: Boolean = true,
    private val ekuCritical: Boolean = true,
    private val validFrom: Long = System.currentTimeMillis() - DAY,
    private val validTo: Long = System.currentTimeMillis() + 365 * DAY
) {
    enum class Fault {
        NONE,
        /** The token carries no certificate. */
        NO_CERT,
        /** Signed with another key than the certificate's. */
        OTHER_KEY,
        /** The message-digest attribute is of something else. */
        WRONG_DIGEST,
        /** No content-type attribute. */
        NO_CONTENT_TYPE,
        /** Two SignerInfos. */
        TWO_SIGNERS,
        /** No signature bytes that verify: the SignerInfo's signature is zeros. */
        ZERO_SIGNATURE
    }

    val keys: KeyPair = generate(ec)
    private val other: KeyPair = generate(ec)
    private val serial = BigInteger("1A2B3C4D5E", 16)
    private val name = seq(set(seq(oid("2.5.4.3"), tlv(0x0C, "Test TSA (made in the test)".toByteArray()))))
    private val keyId = sha1(keys.public.encoded).copyOf(20)

    /** The certificate's DER: self-signed, version 3, the extended key usage as asked, a subject key identifier. */
    val cert: ByteArray = run {
        val extensions = ArrayList<ByteArray>()
        if (eku) {
            val usage = seq(oid("1.3.6.1.5.5.7.3.8"))
            extensions += if (ekuCritical) seq(oid("2.5.29.37"), byteArrayOf(0x01, 0x01, 0xFF.toByte()), tlv(0x04, usage))
            else seq(oid("2.5.29.37"), tlv(0x04, usage))
        }
        extensions += seq(oid("2.5.29.14"), tlv(0x04, tlv(0x04, keyId)))
        val tbs = seq(
            tlv(0xA0, tlv(0x02, byteArrayOf(2))),
            tlv(0x02, serial.toByteArray()),
            certAlgorithm(),
            name,
            seq(utcTime(validFrom), utcTime(validTo)),
            name,
            keys.public.encoded,
            tlv(0xA3, seq(*extensions.toTypedArray()))
        )
        seq(tbs, certAlgorithm(), tlv(0x03, byteArrayOf(0) + sign(keys.private, tbs)))
    }

    /** A TimeStampResp granting [digest] with [nonce] at [genTime], or one with [fault]. */
    fun reply(digest: ByteArray, nonce: BigInteger?, genTime: Long = System.currentTimeMillis(), fault: Fault = Fault.NONE, byKeyId: Boolean = false): ByteArray =
        seq(seq(tlv(0x02, byteArrayOf(0))), token(digest, nonce, genTime, fault, byKeyId))

    /** The answer to a TimeStampReq as it came over the wire: its own imprint and nonce, stamped now. */
    fun answer(request: ByteArray, genTime: Long = System.currentTimeMillis(), fault: Fault = Fault.NONE): ByteArray {
        val req = Rfc3161.read(request, 0).children()
        val digest = req[1].children()[1].value
        val nonce = BigInteger(req[2].value)
        return reply(digest, nonce, genTime, fault)
    }

    /** As an [Rfc3161.Http]: every request answered with a good token, stamped now. */
    val http = Rfc3161.Http { _, body -> 200 to answer(body) }

    /** The TimeStampToken: ContentInfo → SignedData { TSTInfo, the certificate, one signer }. */
    fun token(digest: ByteArray, nonce: BigInteger?, genTime: Long, fault: Fault = Fault.NONE, byKeyId: Boolean = false): ByteArray {
        val info = seq(
            tlv(0x02, byteArrayOf(1)),
            oid("1.2.3.4.1"),
            seq(seq(oid("2.16.840.1.101.3.4.2.1"), byteArrayOf(0x05, 0x00)), tlv(0x04, digest)),
            tlv(0x02, byteArrayOf(0x2A)),
            tlv(0x18, generalized(genTime).toByteArray()),
            *(if (nonce != null) arrayOf(tlv(0x02, nonce.toByteArray())) else emptyArray<ByteArray>())
        )
        val attrs = ArrayList<ByteArray>()
        if (fault != Fault.NO_CONTENT_TYPE) attrs += seq(oid("1.2.840.113549.1.9.3"), set(oid("1.2.840.113549.1.9.16.1.4")))
        val signedOver = if (fault == Fault.WRONG_DIGEST) "something else".toByteArray() else info
        attrs += seq(oid("1.2.840.113549.1.9.4"), set(tlv(0x04, sha256(signedOver))))
        val sorted = attrs.sortedWith { a, b -> compare(a, b) }
        val asSet = set(*sorted.toTypedArray())
        val signer = if (fault == Fault.OTHER_KEY) other.private else keys.private
        val signature = if (fault == Fault.ZERO_SIGNATURE) ByteArray(if (ec) 72 else 256) else sign(signer, asSet)
        val sid = if (byKeyId) tlv(0x80, keyId) else seq(name, tlv(0x02, serial.toByteArray()))
        val signerInfo = seq(
            tlv(0x02, byteArrayOf(if (byKeyId) 3 else 1)),
            sid,
            seq(oid("2.16.840.1.101.3.4.2.1"), byteArrayOf(0x05, 0x00)),
            tlv(0xA0, *sorted.toTypedArray()),
            if (ec) seq(oid("1.2.840.10045.4.3.2")) else seq(oid("1.2.840.113549.1.1.1"), byteArrayOf(0x05, 0x00)),
            tlv(0x04, signature)
        )
        val infos = if (fault == Fault.TWO_SIGNERS) set(signerInfo, signerInfo) else set(signerInfo)
        val parts = ArrayList<ByteArray>()
        parts += tlv(0x02, byteArrayOf(3))
        parts += set(seq(oid("2.16.840.1.101.3.4.2.1"), byteArrayOf(0x05, 0x00)))
        parts += seq(oid("1.2.840.113549.1.9.16.1.4"), tlv(0xA0, tlv(0x04, info)))
        if (fault != Fault.NO_CERT) parts += tlv(0xA0, cert)
        parts += infos
        return seq(oid("1.2.840.113549.1.7.2"), tlv(0xA0, seq(*parts.toTypedArray())))
    }

    private fun certAlgorithm(): ByteArray =
        if (ec) seq(oid("1.2.840.10045.4.3.2")) else seq(oid("1.2.840.113549.1.1.11"), byteArrayOf(0x05, 0x00))

    private fun sign(key: PrivateKey, data: ByteArray): ByteArray {
        val s = Signature.getInstance(if (ec) "SHA256withECDSA" else "SHA256withRSA")
        s.initSign(key)
        s.update(data)
        return s.sign()
    }

    companion object {
        const val DAY = 24L * 60 * 60 * 1000

        /** One of each, made once: an RSA key takes a moment. */
        val rsa: TestTsa by lazy { TestTsa() }
        val ecdsa: TestTsa by lazy { TestTsa(ec = true) }

        private fun generate(ec: Boolean): KeyPair =
            if (ec) KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
            else KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

        fun sha256(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(b)
        private fun sha1(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-1").digest(b)

        private fun len(n: Int): ByteArray = when {
            n < 0x80 -> byteArrayOf(n.toByte())
            n < 0x100 -> byteArrayOf(0x81.toByte(), n.toByte())
            n < 0x10000 -> byteArrayOf(0x82.toByte(), (n ushr 8).toByte(), n.toByte())
            else -> byteArrayOf(0x83.toByte(), (n ushr 16).toByte(), (n ushr 8).toByte(), n.toByte())
        }

        fun tlv(tag: Int, vararg parts: ByteArray): ByteArray {
            val body = ByteArrayOutputStream().also { o -> parts.forEach { o.write(it) } }.toByteArray()
            return byteArrayOf(tag.toByte()) + len(body.size) + body
        }

        fun seq(vararg parts: ByteArray) = tlv(0x30, *parts)
        fun set(vararg parts: ByteArray) = tlv(0x31, *parts)

        /** An OBJECT IDENTIFIER from its dotted text. */
        fun oid(dotted: String): ByteArray {
            val arcs = dotted.split('.').map { it.toLong() }
            val out = ByteArrayOutputStream()
            fun arc(v: Long) {
                val groups = ArrayList<Int>()
                var x = v
                do { groups += (x and 0x7f).toInt(); x = x ushr 7 } while (x > 0)
                for (i in groups.indices.reversed()) out.write(groups[i] or (if (i > 0) 0x80 else 0))
            }
            arc(arcs[0] * 40 + arcs[1])
            for (a in arcs.drop(2)) arc(a)
            return tlv(0x06, out.toByteArray())
        }

        private fun compare(a: ByteArray, b: ByteArray): Int {
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = if (i < a.size) a[i].toInt() and 0xff else 0
                val y = if (i < b.size) b[i].toInt() and 0xff else 0
                if (x != y) return x - y
            }
            return 0
        }

        private fun utc(pattern: String) = SimpleDateFormat(pattern, Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }

        private fun utcTime(ms: Long): ByteArray = tlv(0x17, utc("yyMMddHHmmss'Z'").format(Date(ms)).toByteArray())

        /** GeneralizedTime with milliseconds, as a TSA with a precise clock writes it. */
        fun generalized(ms: Long): String = utc("yyyyMMddHHmmss.SSS'Z'").format(Date(ms))
    }
}
