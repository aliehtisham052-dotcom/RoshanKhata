package com.innovation313.roshankhata.data

import java.security.MessageDigest

/**
 * The audit log's seal (schema 36). Every row carries the SHA-256 of the row
 * written before it and of its own fields, so a row edited or taken out of
 * the middle of the log breaks the seal of the rows after it. [verify] walks
 * the log and says where.
 *
 * What it does not do, said plainly: it cannot stop someone who rewrites the
 * whole file, every seal recomputed, and it cannot tell that the newest rows
 * were cut off the end. It makes quiet editing visible; it is not a lock.
 */
object AuditChain {

    private val HEX = "0123456789abcdef".toCharArray()

    /** The seal of [row] following a row sealed [previous] (null for the first, or after an unsealed row). */
    fun hashOf(previous: String?, row: AuditLog): String {
        val text = listOf(
            previous ?: "", row.timestamp.toString(), row.user ?: "", row.action,
            row.refTable ?: "", row.refId?.toString() ?: "", row.detail ?: ""
        ).joinToString("\u001F")
        val d = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return buildString(d.size * 2) {
            for (b in d) { val v = b.toInt() and 0xff; append(HEX[v ushr 4]); append(HEX[v and 0x0f]) }
        }
    }

    data class Result(
        /** Rows whose seal holds. */
        val sealed: Int,
        /** Rows written before the seal existed (a book from before schema 36), at the start of the log. */
        val beforeSeal: Int,
        /** The id of the first row whose seal does not hold; null when every one does. */
        val brokenAt: Long?
    ) {
        val intact: Boolean get() = brokenAt == null
    }

    /** [rows] in id order, as the log was written. */
    fun verify(rows: List<AuditLog>): Result {
        var previous: String? = null
        var sealed = 0
        var before = 0
        var started = false
        for (r in rows) {
            val h = r.hash
            if (h == null) {
                // Unsealed rows may only lead the log (an older book); one after a sealed row is a break.
                if (started) return Result(sealed, before, r.id)
                before++
                previous = null
                continue
            }
            started = true
            if (h != hashOf(previous, r)) return Result(sealed, before, r.id)
            sealed++
            previous = h
        }
        return Result(sealed, before, null)
    }
}
