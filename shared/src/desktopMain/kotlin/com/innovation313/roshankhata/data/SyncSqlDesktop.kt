package com.innovation313.roshankhata.data

import androidx.room.PooledConnection
import androidx.room.deferredTransaction
import androidx.room.immediateTransaction
import androidx.room.useReaderConnection
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteStatement

/**
 * The computer's side of the sync door: Room's own writer connection, in an
 * IMMEDIATE transaction (the book is locked for writing from the first
 * statement, so nothing slips in between a sync's read and its write).
 * The Windows app's panes reload on [LiveSync.changes] after a sync wrote.
 */
actual fun KhataDatabase.syncSql(): SyncSql = DesktopSyncSql(this)

private class DesktopSyncSql(private val db: KhataDatabase) : SyncSql {
    override suspend fun <R> write(block: suspend SyncTx.() -> R): R =
        db.useWriterConnection { t -> t.immediateTransaction { ConnectionTx(this).block() } }

    override suspend fun <R> read(block: suspend SyncTx.() -> R): R =
        db.useReaderConnection { t -> t.deferredTransaction { ConnectionTx(this).block() } }
}

private class ConnectionTx(private val c: PooledConnection) : SyncTx {
    override suspend fun exec(sql: String, args: List<Any?>) {
        c.usePrepared(sql) { st ->
            bind(st, args)
            while (st.step()) { /* a statement's rows, if any, are not wanted */ }
        }
    }

    override suspend fun rows(sql: String, args: List<Any?>, types: String): List<Array<Any?>> =
        c.usePrepared(sql) { st ->
            bind(st, args)
            val out = ArrayList<Array<Any?>>()
            while (st.step()) {
                out.add(Array(types.length) { i ->
                    if (st.isNull(i)) null else when (types[i]) {
                        'I' -> st.getLong(i)
                        'R' -> st.getDouble(i)
                        else -> st.getText(i)
                    }
                })
            }
            out
        }

    private fun bind(st: SQLiteStatement, args: List<Any?>) {
        args.forEachIndexed { i, raw ->
            val n = i + 1
            when (val v = syncBindable(raw)) {
                null -> st.bindNull(n)
                is Long -> st.bindLong(n, v)
                is Double -> st.bindDouble(n, v)
                else -> st.bindText(n, v.toString())
            }
        }
    }
}
