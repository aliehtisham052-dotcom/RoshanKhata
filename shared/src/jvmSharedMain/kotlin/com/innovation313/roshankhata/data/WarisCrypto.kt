package com.innovation313.roshankhata.data

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Waris ki Chaabi's crypto (heir access, 4 Nov 2026; protocol v1, the relays treat all of it as opaque bytes).
 *
 * Everything here is pure JDK (javax.crypto, java.security) so the phone and the computer run the same lines:
 *  - passes: HMAC-SHA256 of the owner's or an heir's 32-byte secret, as lowercase hex ([ownerPass], [heirPass]);
 *    a relay keeps only SHA-256 of a pass ([passHash]);
 *  - an heir's key pair is EC P-256 (public as X.509 SubjectPublicKeyInfo DER, private as PKCS#8 DER);
 *  - a box ([sealBox]) carries the snapshot key W and its keyVersion to one heir: an ephemeral P-256 key,
 *    ECDH, HKDF-SHA256 (salt = the ephemeral public key, info = "rk-waris-box-v1:<vault>:<hid>"), AES-256-GCM
 *    with the same words as its authenticated data;
 *  - a snapshot ([sealSnapshot]) is AES-256-GCM under W, its version v in the authenticated data, cut into chunks
 *    of at most [CHUNK] bytes and at most [MAX_CHUNKS] of them;
 *  - the three codes: the owner's ([ownerCode], QR1), the heir's ([heirCode], QR2, with the pairing check c)
 *    and the heir's paper card ([card]).
 * WarisCryptoTest holds each to its rule, and an independent implementation (Python's `cryptography`) opened
 * what this seals and sealed what this opens before it was committed.
 */
object WarisCrypto {

    const val CHUNK = 524_288
    const val MAX_CHUNKS = 16
    /** The relays' own cap on one version's chunks together (8 MiB). */
    const val MAX_SEALED = CHUNK * MAX_CHUNKS
    /** A sealed snapshot's bytes around its gzip: version byte, keyVersion, nonce, tag. */
    const val SNAP_OVERHEAD = 1 + 4 + 12 + 16
    const val PREFIX_OWNER = "rkwaris:1?"
    const val PREFIX_HEIR = "rkwarisk:1?"
    const val PREFIX_CARD = "rkwariscard:1?"

    private const val NONCE = 12
    private const val TAG_BITS = 128
    private val rng = SecureRandom()

    fun random(n: Int): ByteArray = ByteArray(n).also { rng.nextBytes(it) }

    fun hex(b: ByteArray): String {
        val out = StringBuilder(b.size * 2)
        for (x in b) { val v = x.toInt() and 0xFF; out.append(HEX[v ushr 4]).append(HEX[v and 15]) }
        return out.toString()
    }

    fun unhex(s: String): ByteArray? {
        if (s.length % 2 != 0) return null
        return runCatching { ByteArray(s.length / 2) { i -> s.substring(i * 2, i * 2 + 2).toInt(16).toByte() } }.getOrNull()
    }

    private const val HEX = "0123456789abcdef"

    fun isHex(s: String?, chars: Int): Boolean = s != null && s.length == chars && s.all { it in '0'..'9' || it in 'a'..'f' }

    fun sha256hex(text: String): String = hex(MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)))

    fun hmac(key: ByteArray, msg: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(msg)

    /** HKDF-SHA256 (RFC 5869): extract with [salt] (zeros when empty), expand to [length] bytes with [info]. */
    fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..255 * 32) { "hkdf length" }
        val prk = hmac(if (salt.isEmpty()) ByteArray(32) else salt, ikm)
        val out = java.io.ByteArrayOutputStream(length)
        var t = ByteArray(0)
        var i = 1
        while (out.size() < length) {
            val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(prk, "HmacSHA256")) }
            mac.update(t); mac.update(info); mac.update(i.toByte())
            t = mac.doFinal()
            out.write(t)
            i++
        }
        return out.toByteArray().copyOf(length)
    }

    // ------------------------------------------------------------------ identities and passes

    fun newVault(): String = hex(random(16))
    fun newHid(): String = hex(random(8))
    fun newSecret(): ByteArray = random(32)

    fun ownerPass(ownerSecret: ByteArray, vault: String): String =
        hex(hmac(ownerSecret, "rk-waris-v1:$vault".toByteArray(Charsets.UTF_8)))

    fun heirPass(heirSecret: ByteArray, vault: String, hid: String): String =
        hex(hmac(heirSecret, "rk-waris-heir-v1:$vault:$hid".toByteArray(Charsets.UTF_8)))

    /** What a relay keeps of a pass: SHA-256 of its hex, as hex. */
    fun passHash(pass: String): String = sha256hex(pass)

    // ------------------------------------------------------------------ base64url (no padding), for the codes

    fun b64url(b: ByteArray): String = B64.encode(b).trimEnd('=').replace('+', '-').replace('/', '_')

    fun unb64url(s: String): ByteArray? {
        if (s.isEmpty() || s.any { !(it.isLetterOrDigit() && it.code < 128) && it != '-' && it != '_' }) return null
        val std = s.replace('-', '+').replace('_', '/')
        val pad = (4 - std.length % 4) % 4
        if (pad == 3) return null
        return runCatching { B64.decode(std + "=".repeat(pad)) }.getOrNull()
    }

    // ------------------------------------------------------------------ the heir's key pair

    class KeyPairBytes(val pub: ByteArray, val priv: ByteArray)

    fun newKeyPair(): KeyPairBytes {
        val g = KeyPairGenerator.getInstance("EC")
        g.initialize(ECGenParameterSpec("secp256r1"), rng)
        val kp = g.generateKeyPair()
        return KeyPairBytes(kp.public.encoded, kp.private.encoded)
    }

    private fun publicKey(spki: ByteArray) = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(spki))
    private fun privateKey(pkcs8: ByteArray) = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(pkcs8))

    /** Whether [spki] is a P-256 public key this JDK reads (a code's k is checked before it is kept). */
    fun isPublicKey(spki: ByteArray): Boolean = runCatching {
        val k = publicKey(spki) as java.security.interfaces.ECPublicKey
        k.params.curve.field.fieldSize == 256
    }.getOrDefault(false)

    // ------------------------------------------------------------------ the box: W and keyVersion for one heir

    class Unboxed(val w: ByteArray, val keyVersion: Int)

    private fun boxWords(vault: String, hid: String) = "rk-waris-box-v1:$vault:$hid".toByteArray(Charsets.UTF_8)

    fun sealBox(heirPub: ByteArray, w: ByteArray, keyVersion: Int, vault: String, hid: String): ByteArray {
        require(w.size == 32) { "W is 32 bytes" }
        val g = KeyPairGenerator.getInstance("EC")
        g.initialize(ECGenParameterSpec("secp256r1"), rng)
        val eph = g.generateKeyPair()
        val ephPub = eph.public.encoded
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(eph.private)
        ka.doPhase(publicKey(heirPub), true)
        val z = ka.generateSecret()
        val words = boxWords(vault, hid)
        val k = hkdf(z, ephPub, words, 32)
        val nonce = random(NONCE)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(k, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        c.updateAAD(words)
        val ct = c.doFinal(w + ByteBuffer.allocate(4).putInt(keyVersion).array())
        return ByteBuffer.allocate(1 + 2 + ephPub.size + NONCE + ct.size)
            .put(1).putShort(ephPub.size.toShort()).put(ephPub).put(nonce).put(ct).array()
    }

    /** W and its keyVersion out of [box], or null when it is not this heir's, not this vault's, or was changed. */
    fun openBox(heirPriv: ByteArray, box: ByteArray, vault: String, hid: String): Unboxed? = runCatching {
        val b = ByteBuffer.wrap(box)
        if (b.get().toInt() != 1) return null
        val len = b.short.toInt() and 0xFFFF
        if (len < 1 || len > box.size - 3 - NONCE - 16) return null
        val ephPub = ByteArray(len).also { b.get(it) }
        val nonce = ByteArray(NONCE).also { b.get(it) }
        val ct = ByteArray(b.remaining()).also { b.get(it) }
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(privateKey(heirPriv))
        ka.doPhase(publicKey(ephPub), true)
        val words = boxWords(vault, hid)
        val k = hkdf(ka.generateSecret(), ephPub, words, 32)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(k, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        c.updateAAD(words)
        val plain = c.doFinal(ct)
        if (plain.size != 36) return null
        Unboxed(plain.copyOf(32), ByteBuffer.wrap(plain, 32, 4).int)
    }.getOrNull()

    // ------------------------------------------------------------------ the snapshot

    private fun snapWords(vault: String, v: Int) = "rk-waris-snap-v1:$vault:$v".toByteArray(Charsets.UTF_8)

    /** [plain] (the gzip of the book's backup) sealed under [w] for version [v] of [vault]. */
    fun sealSnapshot(w: ByteArray, keyVersion: Int, vault: String, v: Int, plain: ByteArray, plainLen: Int = plain.size): ByteArray {
        val nonce = random(NONCE)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(w, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        c.updateAAD(snapWords(vault, v))
        val ct = c.doFinal(plain, 0, plainLen)
        return ByteBuffer.allocate(1 + 4 + NONCE + ct.size).put(1).putInt(keyVersion).put(nonce).put(ct).array()
    }

    /** The keyVersion a sealed snapshot says it was sealed with, or null for bytes that are not one. */
    fun snapshotKeyVersion(sealed: ByteArray): Int? =
        if (sealed.size < SNAP_OVERHEAD || sealed[0].toInt() != 1) null else ByteBuffer.wrap(sealed, 1, 4).int

    /** The gzip inside a sealed snapshot, or null when [w] or [v] is wrong or a byte was changed. */
    fun openSnapshot(w: ByteArray, sealed: ByteArray, vault: String, v: Int): ByteArray? {
        if (snapshotKeyVersion(sealed) == null) return null
        return runCatching {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, SecretKeySpec(w, "AES"), GCMParameterSpec(TAG_BITS, sealed, 5, NONCE))
            c.updateAAD(snapWords(vault, v))
            c.doFinal(sealed, 5 + NONCE, sealed.size - 5 - NONCE)
        }.getOrNull()
    }

    /** [sealed] cut into chunks of at most [CHUNK] bytes; null when it would need more than [MAX_CHUNKS]. */
    fun chunks(sealed: ByteArray): List<ByteArray>? {
        if (sealed.isEmpty() || sealed.size > MAX_SEALED) return null
        val out = ArrayList<ByteArray>()
        var at = 0
        while (at < sealed.size) {
            val end = minOf(sealed.size, at + CHUNK)
            out.add(sealed.copyOfRange(at, end))
            at = end
        }
        return out
    }

    // ------------------------------------------------------------------ the three codes

    /** QR1: what the owner shows; the heir's phone takes from it the vault, its own hid and secret, the wait, the shop. */
    class OwnerCode(val vault: String, val hid: String, val secret: ByteArray, val wait: Int, val shop: String)

    /** QR2: what the heir's phone shows back: its hid, its public key ([k] exactly as written) and the pairing check. */
    class HeirCode(val hid: String, val k: String, val pub: ByteArray, val c: String)

    /** The heir's paper card: everything the heir's phone keeps, so a new phone can take its place. */
    class Card(val vault: String, val hid: String, val secret: ByteArray, val priv: ByteArray, val wait: Int, val shop: String)

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun ownerCode(vault: String, hid: String, secret: ByteArray, wait: Int, shop: String): String =
        PREFIX_OWNER + "v=$vault&h=$hid&s=${b64url(secret)}&w=$wait&n=${enc(shop.take(80))}"

    fun pairCheck(secret: ByteArray, vault: String, hid: String, k: String): String =
        hex(hmac(secret, "rk-waris-pair-v1:$vault:$hid:$k".toByteArray(Charsets.UTF_8)).copyOf(8))

    fun heirCode(secret: ByteArray, vault: String, hid: String, pub: ByteArray): String {
        val k = b64url(pub)
        return PREFIX_HEIR + "h=$hid&k=$k&c=${pairCheck(secret, vault, hid, k)}"
    }

    fun card(vault: String, hid: String, secret: ByteArray, priv: ByteArray, wait: Int, shop: String): String =
        PREFIX_CARD + "v=$vault&h=$hid&s=${b64url(secret)}&p=${b64url(priv)}&w=$wait&n=${enc(shop.take(80))}"

    private fun fields(text: String?, prefix: String): Map<String, String>? {
        val t = text?.trim() ?: return null
        if (!t.startsWith(prefix)) return null
        val out = HashMap<String, String>()
        for (part in t.removePrefix(prefix).split('&')) {
            val i = part.indexOf('=')
            if (i <= 0) return null
            val key = part.substring(0, i)
            if (key in out) return null
            out[key] = part.substring(i + 1)
        }
        return out
    }

    private fun shopOf(raw: String?): String? = raw?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
        ?.trim()?.take(80)

    private fun waitOf(raw: String?): Int? = raw?.toIntOrNull()?.takeIf { it in 1..90 }

    fun parseOwnerCode(text: String?): OwnerCode? {
        val f = fields(text, PREFIX_OWNER) ?: return null
        val vault = f["v"]?.takeIf { isHex(it, 32) } ?: return null
        val hid = f["h"]?.takeIf { isHex(it, 16) } ?: return null
        val secret = f["s"]?.let { unb64url(it) }?.takeIf { it.size == 32 } ?: return null
        val wait = waitOf(f["w"]) ?: return null
        val shop = shopOf(f["n"].orEmpty()) ?: return null
        return OwnerCode(vault, hid, secret, wait, shop)
    }

    /** QR2 as written; whether it is the right phone's is [heirCodeMatches]. */
    fun parseHeirCode(text: String?): HeirCode? {
        val f = fields(text, PREFIX_HEIR) ?: return null
        val hid = f["h"]?.takeIf { isHex(it, 16) } ?: return null
        val k = f["k"] ?: return null
        val pub = unb64url(k)?.takeIf { it.size <= 200 && isPublicKey(it) } ?: return null
        val c = f["c"]?.takeIf { isHex(it, 16) } ?: return null
        return HeirCode(hid, k, pub, c)
    }

    /** The owner takes QR2 only from the phone that scanned his QR1: the check c is made with the secret QR1 gave it. */
    fun heirCodeMatches(code: HeirCode, secret: ByteArray, vault: String, hid: String): Boolean =
        code.hid == hid && MessageDigest.isEqual(
            pairCheck(secret, vault, hid, code.k).toByteArray(Charsets.US_ASCII), code.c.toByteArray(Charsets.US_ASCII)
        )

    fun parseCard(text: String?): Card? {
        val f = fields(text, PREFIX_CARD) ?: return null
        val vault = f["v"]?.takeIf { isHex(it, 32) } ?: return null
        val hid = f["h"]?.takeIf { isHex(it, 16) } ?: return null
        val secret = f["s"]?.let { unb64url(it) }?.takeIf { it.size == 32 } ?: return null
        val priv = f["p"]?.let { unb64url(it) }?.takeIf { it.size in 32..400 } ?: return null
        if (runCatching { privateKey(priv) }.isFailure) return null
        val wait = waitOf(f["w"]) ?: return null
        val shop = shopOf(f["n"].orEmpty()) ?: return null
        return Card(vault, hid, secret, priv, wait, shop)
    }

    /** The public key that belongs to [priv] (a card carries only the private key). */
    fun publicOf(priv: ByteArray): ByteArray? = runCatching {
        val k = privateKey(priv) as java.security.interfaces.ECPrivateKey
        val params = k.params
        // The public point is the generator times the private scalar.
        val q = EcMath.multiply(params, k.s)
        KeyFactory.getInstance("EC").generatePublic(java.security.spec.ECPublicKeySpec(q, params)).encoded
    }.getOrNull()
}

/** Just enough elliptic-curve arithmetic for [WarisCrypto.publicOf]: a card holds the private key alone. */
internal object EcMath {
    private val ZERO = java.math.BigInteger.ZERO
    private val TWO = java.math.BigInteger.valueOf(2)
    private val THREE = java.math.BigInteger.valueOf(3)

    fun multiply(params: java.security.spec.ECParameterSpec, k: java.math.BigInteger): java.security.spec.ECPoint {
        val p = (params.curve.field as java.security.spec.ECFieldFp).p
        val a = params.curve.a
        var result: java.security.spec.ECPoint = java.security.spec.ECPoint.POINT_INFINITY
        var addend = params.generator
        var n = k
        while (n.signum() > 0) {
            if (n.testBit(0)) result = add(result, addend, a, p)
            addend = add(addend, addend, a, p)
            n = n.shiftRight(1)
        }
        return result
    }

    private fun add(
        p1: java.security.spec.ECPoint, p2: java.security.spec.ECPoint,
        a: java.math.BigInteger, p: java.math.BigInteger
    ): java.security.spec.ECPoint {
        if (p1 == java.security.spec.ECPoint.POINT_INFINITY) return p2
        if (p2 == java.security.spec.ECPoint.POINT_INFINITY) return p1
        val lambda = if (p1.affineX == p2.affineX) {
            if ((p1.affineY + p2.affineY).mod(p) == ZERO) return java.security.spec.ECPoint.POINT_INFINITY
            (THREE * p1.affineX * p1.affineX + a) * (TWO * p1.affineY).modInverse(p)
        } else {
            (p2.affineY - p1.affineY) * (p2.affineX - p1.affineX).modInverse(p)
        }.mod(p)
        val x = (lambda * lambda - p1.affineX - p2.affineX).mod(p)
        val y = (lambda * (p1.affineX - x) - p1.affineY).mod(p)
        return java.security.spec.ECPoint(x, y)
    }
}
