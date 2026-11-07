package com.innovation313.roshankhata.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.util.Locale

/**
 * One khata entry as an e1 leaf reads it (seals made before 7 Nov 2026): the entry's own fields and its party's uid.
 * The names are the columns' (`transactions.*`, `parties.uid AS partyUid`), so Room fills it as it is. itemName,
 * quantity and unit are the entry's legacy goods columns, which nothing has written since schema 20 — an e1 leaf never
 * held the goods; it is kept only so the seals made with it still check.
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
 * One khata entry as an e2 leaf reads it (7 Nov 2026, the security review's M3), read a page of ids at a time
 * ([KhataDao.sealEntriesPage]): what the entry says and its party's uid. Column names, so Room fills it as it is.
 */
data class SealEntry(
    val id: Long,
    val uid: String?,
    val entryNumber: String,
    val amount: Double,
    val isGiven: Boolean,
    val timestamp: Long,
    val note: String?,
    val paymentMethod: String?,
    val paymentRef: String?,
    val isQarzeHasna: Boolean,
    val isDeleted: Boolean,
    val partyUid: String?
)

/**
 * One goods line of an entry as an e2 leaf reads it: entry_items, with its batch's uid (the supplier bill line it was
 * sold out of) in place of its row id, so the same line reads alike on every copy of the book. The product link is not
 * read: it is filled in after the sale (Products' "tie the goods", [KhataDao.linkGoodsToProducts]) and live sync gives
 * a product another uid when two devices made the same name — a sealed entry would read as changed when nothing of the
 * dealing was. The item's name, as written on the line, is what the leaf holds.
 */
data class SealGoods(
    val entryId: Long,
    val lineNo: Int,
    val itemName: String?,
    val quantity: Double?,
    val unit: String?,
    val rate: Double?,
    val isBonus: Boolean,
    val batchUid: String?
)

/**
 * The arithmetic of Kitab ki Muhar, the book's seal (4 Nov 2026): what one entry's fingerprint is made of, and what a
 * day's seal digest is made of. Pure JVM, the same on the phone and the computer, and written so that anyone with the
 * proof file and `sha256sum` can do it again by hand.
 *
 * Version 2 (7 Nov 2026, the security review's M3), what every seal is made with since:
 *
 *  - leaf = SHA-256 of the UTF-8 text "e2" followed by its fields, each written as "|", its length in UTF-8 bytes,
 *    ":" and the field itself — so no field can be read as part of another, whatever it holds: the entry's uid,
 *    number, amount (3 decimals), 1 for gave or 0 for got, time in ms, note, payment method (its saved key), payment
 *    reference, 1 for a Qarz-e-Hasna loan or 0, 1 while the entry is in the bin or 0, the party's uid, the number of
 *    goods lines, then each line in its order: item, quantity, unit as [UnitWords.canonical] gives it, rate, 1 for a
 *    free (bonus) line or 0, the batch's uid ([SealGoods] says why not the product's). An absent value is an empty
 *    field. Figures are read from the double exactly ([figure], [plain]), so the phone and the computer write the same
 *    text;
 *  - digest = SHA-256 of the UTF-8 header `rk-seal-v2\n<prevDigest>\n<sealedAt>\n<leafCount>\n<auditHead>\n`
 *    followed by the leaves' 32-byte hashes in id order, written as lowercase hex. No entry id is in it (M7): the ids
 *    would tell how many entries the shop has written.
 *
 * Version 1 (4–6 Nov 2026), kept so those seals still check: leaf = SHA-256 of `e1|uid|entryNumber|amount (3
 * decimals)|1 or 0|timestamp|note|itemName|quantity|unit|paymentRef|party uid`; header `rk-seal-v1\n<prevDigest>\n
 * <sealedAt>\n<fromId>-<toId>\n<leafCount>\n<auditHead>\n`. Which one a seal is, is read from its digest: the header's
 * tag is inside what the digest covers. **Both formats are frozen**: a change is a new version.
 *
 * The unit is written as [UnitWords.canonical] gives it: the book's own repair on opening
 * ([UnitWords.normalizeStatements]) turns a saved label ("bori", "بوری") into its key ("bag") with the same table, so
 * an old row repaired after it was sealed still matches its seal.
 *
 * A seal is chained to the one before it (prevDigest), and carries the audit log's newest seal ([AuditChain]), so an
 * entry edited or deleted afterwards is both visible (its leaf no longer matches) and recorded (the audit row).
 */
object SealMath {

    const val TAG = "rk-seal-v1"
    const val TAG_V2 = "rk-seal-v2"
    const val LEAF_TAG = "e1"
    const val LEAF_TAG_V2 = "e2"
    const val HASH_BYTES = 32

    private val HEX = "0123456789abcdef".toCharArray()

    /** The text an e1 leaf hashes, exactly as the proof file shows it (seals made before 7 Nov 2026). */
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

    /** The text an e2 leaf hashes, exactly as the proof file shows it: [e] and its [goods], every field length-prefixed. */
    fun leafTextV2(e: SealEntry, goods: List<SealGoods>): String {
        val fields = ArrayList<String>()
        fields += e.uid ?: ""
        fields += e.entryNumber
        fields += figure(e.amount, 3)
        fields += if (e.isGiven) "1" else "0"
        fields += e.timestamp.toString()
        fields += e.note ?: ""
        fields += e.paymentMethod ?: ""
        fields += e.paymentRef ?: ""
        fields += if (e.isQarzeHasna) "1" else "0"
        fields += if (e.isDeleted) "1" else "0"
        fields += e.partyUid ?: ""
        // The lines in their order on the entry; lines of one number keep the order they were read in (by row id).
        val lines = goods.sortedBy { it.lineNo }
        fields += lines.size.toString()
        for (g in lines) {
            fields += g.itemName ?: ""
            fields += g.quantity?.let { plain(it) } ?: ""
            fields += UnitWords.canonical(g.unit) ?: ""
            fields += g.rate?.let { plain(it) } ?: ""
            fields += if (g.isBonus) "1" else "0"
            fields += g.batchUid ?: ""
        }
        return buildString {
            append(LEAF_TAG_V2)
            for (f in fields) append('|').append(f.toByteArray(Charsets.UTF_8).size).append(':').append(f)
        }
    }

    /**
     * [value] with exactly [decimals] decimals, rounded half up from the double's own exact value (BigDecimal), never
     * from the shortest text the platform prints for it: the phone's and the computer's Java may print one double
     * differently. "1250.500" for 1250.5 at 3.
     */
    fun figure(value: Double, decimals: Int): String =
        if (value.isNaN() || value.isInfinite()) value.toString()
        else BigDecimal(value).setScale(decimals, RoundingMode.HALF_UP).toPlainString()

    /** A quantity or a rate: up to six decimals, as [figure] rounds them, without trailing zeros — "2.5", "10", "0". */
    fun plain(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return value.toString()
        if (value == 0.0) return "0"
        val rounded = BigDecimal(value).setScale(6, RoundingMode.HALF_UP)
        return if (rounded.signum() == 0) "0" else rounded.stripTrailingZeros().toPlainString()
    }

    fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    fun leafHash(text: String): ByteArray = sha256(text.toByteArray(Charsets.UTF_8))

    /** A version-1 header (seals made before 7 Nov 2026); the proof file carries it whole, so a verifier hashes exactly this. */
    fun header(prevDigest: String?, sealedAt: Long, fromId: Long, toId: Long, leafCount: Int, auditHead: String?): String =
        "$TAG\n${prevDigest ?: ""}\n$sealedAt\n$fromId-$toId\n$leafCount\n${auditHead ?: ""}\n"

    /** The version-2 header every seal is made with since 7 Nov 2026: no entry ids in it. */
    fun headerV2(prevDigest: String?, sealedAt: Long, leafCount: Int, auditHead: String?): String =
        "$TAG_V2\n${prevDigest ?: ""}\n$sealedAt\n$leafCount\n${auditHead ?: ""}\n"

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
