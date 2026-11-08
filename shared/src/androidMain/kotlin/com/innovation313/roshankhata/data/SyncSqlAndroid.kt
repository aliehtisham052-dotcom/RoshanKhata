package com.innovation313.roshankhata.data

import android.database.Cursor
import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.Callable

/**
 * The phone's side of the sync door: Room's own open helper, inside Room's
 * own transaction (runInTransaction), so the screens' observers hear of every
 * row a sync writes. The block runs to its end on the thread the transaction
 * began on (Android ties a transaction to its thread).
 */
actual fun KhataDatabase.syncSql(): SyncSql = AndroidSyncSql(this)

private class AndroidSyncSql(private val db: KhataDatabase) : SyncSql {

    private val tx = object : SyncTx {
        override suspend fun exec(sql: String, args: List<Any?>) {
            val w = db.openHelper.writableDatabase
            if (args.isEmpty()) w.execSQL(sql) else w.execSQL(sql, args.map(::syncBindable).toTypedArray())
        }

        override suspend fun rows(sql: String, args: List<Any?>, types: String): List<Array<Any?>> {
            val w = db.openHelper.writableDatabase
            return w.query(SimpleSQLiteQuery(sql, args.map(::syncBindable).toTypedArray())).use { c -> read(c, types) }
        }

        override fun isConstraint(e: Throwable): Boolean = e is android.database.sqlite.SQLiteConstraintException
    }

    private fun read(c: Cursor, types: String): List<Array<Any?>> {
        val out = ArrayList<Array<Any?>>()
        while (c.moveToNext()) {
            out.add(Array(types.length) { i ->
                if (c.isNull(i)) null else when (types[i]) {
                    'I' -> c.getLong(i)
                    'R' -> c.getDouble(i)
                    else -> c.getString(i)
                }
            })
        }
        return out
    }

    override suspend fun <R> write(block: suspend SyncTx.() -> R): R = withContext(Dispatchers.IO) {
        db.runInTransaction(Callable { runBlocking { tx.block() } })
    }

    override suspend fun <R> read(block: suspend SyncTx.() -> R): R = write(block)
}
