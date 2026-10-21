package com.innovation313.roshankhata.data

/**
 * The one door live sync uses to reach the book's SQLite (live sync
 * Marhala 1, 22 Oct 2026). A sync reads and writes rows of every table by
 * name, which Room's typed DAO cannot do; each platform opens this door its
 * own way (androidMain: Room's open helper inside Room's own transaction, so
 * the screens' observers hear of the change; desktopMain: Room's writer
 * connection), and the sync logic above it is the same file on both.
 *
 * Everything a sync does inside [write] is one transaction: a parcel lands
 * whole or not at all.
 */
interface SyncSql {
    /** [block] in one write transaction: all of it, or none of it. */
    suspend fun <R> write(block: suspend SyncTx.() -> R): R

    /** [block] in one read transaction, so what it reads is one moment of the book. */
    suspend fun <R> read(block: suspend SyncTx.() -> R): R
}

/**
 * Statements inside a [SyncSql] transaction. Values bound are Long, Int,
 * Double, String, Boolean (as 1/0) or null.
 *
 * Nothing inside a transaction block may wait on anything but these calls:
 * on the phone the transaction belongs to the thread it began on.
 */
interface SyncTx {
    suspend fun exec(sql: String, args: List<Any?> = emptyList())

    /**
     * The rows of [sql]. Column i is read as [types][i]: 'I' a Long, 'R' a
     * Double, 'T' a String; an SQL NULL is null whatever the type.
     */
    suspend fun rows(sql: String, args: List<Any?> = emptyList(), types: String): List<Array<Any?>>
}

/** This book's door, as the platform opens it. */
expect fun KhataDatabase.syncSql(): SyncSql

/** A bound value as both drivers take it: whole numbers as Long, flags as 1/0. */
internal fun syncBindable(v: Any?): Any? = when (v) {
    null -> null
    is Boolean -> if (v) 1L else 0L
    is Int -> v.toLong()
    is Short -> v.toLong()
    is Byte -> v.toLong()
    is Float -> v.toDouble()
    else -> v
}
