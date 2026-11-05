package com.innovation313.roshankhata.data

import java.security.MessageDigest
import java.util.Locale

/**
 * One khata entry as the book seal reads it ([BookSeals], schema 44): the entry's own fields and its party's uid,
 * read a page of ids at a time ([KhataDao.sealLinesPage]). The names are the columns' (`transactions.*`,
 * `parties.uid AS partyUid`), so Room fills it as it is.
 */
data class SealLine(
    val id: Long,
    val uid: String?,
    val entryNumber: String,
    val amount: Double,
    val isGiven: Boolean,
    val timestamp: Long,
    val note: String?,
    val itemName: String?,
    val quantity: Double?,
    val unit: String?,
    val paymentRef: String?,
    val partyUid: String?
)

/**
 * The arithmetic of Kitab ki Muhar, the book's seal (4 Nov 2026): what one entry's fingerprint is made of, and what a
 * day's seal digest is made of. Pure JVM, the same on the phone and the computer, and written so that anyone with the
 * proof file and `sha256sum` can do it again by hand:
 *
 *  - leaf = SHA-256 of the UTF-8 text `e1|uid|entryNumber|amount (3 decimals)|1 or 0|timestamp|note|itemName|
 *    quantity|unit|paymentRef|party uid` — an absent value is an empty field. Three decimals, so a JOD, TND or LYD
 *    amount (three-decimal currencies) is sealed whole. The unit is written as [UnitWords.canonical] gives it: the
 *    book's own repair on opening ([UnitWords.normalizeStatements]) turns a saved label ("bori", "بوری") into its
 *    key ("bag") with the same table, so an old row repaired after it was sealed still matches its seal;
 *  - digest = SHA-256 of the UTF-8 header `rk-seal-v1\n<prevDigest>\n<sealedAt>\n<fromId>-<toId>\n<leafCount>\n
 *    <auditHead>\n` followed by the leaves' 32-byte hashes in id order, written as lowercase hex.
 *
 * A seal is chained to the one before it (prevDigest), and carries the audit log's newest seal ([AuditChain]), so an
 * entry edited or deleted afterwards is both visible (its leaf no longer matches) and recorded (the audit row).
 */
object SealMath {

    const val TAG = "rk-seal-v1"
    const val LEAF_TAG = "e1"
    const val HASH_BYTES = 32

    private val HEX = "0123456789abcdef".toCharArray()

    /** The text an entry's leaf hashes, exactly as the proof file shows it. */
    fun leafText(l: SealLine): String = listOf(
        LEAF_TAG,
        l.uid ?: "",
        l.entryNumber,
        String.format(Locale.ROOT, "%.3f", l.amount),
        if (l.isGiven) "1" else "0",
        l.timestamp.toString(),
        l.note ?: "",
        l.itemName ?: "",
        l.quantity?.toString() ?: "",
        UnitWords.canonical(l.unit) ?: "",
        l.paymentRef ?: "",
        l.partyUid ?: ""
    ).joinToString("|")

    fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    fun leafHash(text: String): ByteArray = sha256(text.toByteArray(Charsets.UTF_8))

    /** The header the digest starts with; the proof file carries it whole, so a verifier hashes exactly this. */
    fun header(prevDigest: String?, sealedAt: Long, fromId: Long, toId: Long, leafCount: Int, auditHead: String?): String =
        "$TAG\n${prevDigest ?: ""}\n$sealedAt\n$fromId-$toId\n$leafCount\n${auditHead ?: ""}\n"

    /** The seal's digest, lowercase hex: SHA-256 of [header] and then [leaves] (none for the first seal). */
    fun digest(header: String, leaves: ByteArray?): String {
        val d = MessageDigest.getInstance("SHA-256")
        d.update(header.toByteArray(Charsets.UTF_8))
        if (leaves != null) d.update(leaves)
        return hex(d.digest())
    }

    /** Where [leaf] stands among [leaves] (by 32-byte step), or -1. */
    fun indexOf(leaves: ByteArray?, leaf: ByteArray): Int {
        if (leaves == null || leaf.size != HASH_BYTES) return -1
        val n = leaves.size / HASH_BYTES
        outer@ for (i in 0 until n) {
            val at = i * HASH_BYTES
            for (k in 0 until HASH_BYTES) if (leaves[at + k] != leaf[k]) continue@outer
            return i
        }
        return -1
    }

    fun hex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        for (b in bytes) { val v = b.toInt() and 0xff; append(HEX[v ushr 4]); append(HEX[v and 0x0f]) }
    }

    /** Lowercase or uppercase hex to bytes; throws on anything else. */
    fun unhex(text: String): ByteArray {
        require(text.length % 2 == 0) { "odd hex" }
        return ByteArray(text.length / 2) { i ->
            val hi = Character.digit(text[i * 2], 16)
            val lo = Character.digit(text[i * 2 + 1], 16)
            require(hi >= 0 && lo >= 0) { "not hex" }
            ((hi shl 4) or lo).toByte()
        }
    }
}
