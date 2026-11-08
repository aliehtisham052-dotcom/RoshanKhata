package com.innovation313.roshankhata

import com.innovation313.roshankhata.data.get

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.Party
import com.innovation313.roshankhata.data.Snapshots
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The copy the phone keeps before a restore is the whole book, newest writes
 * included, and checks out on its own (22 Oct 2026, the review: it was the
 * file copied as it lay, whether or not the log had been folded into it).
 */
@RunWith(AndroidJUnit4::class)
class SnapshotsOnDeviceTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun theCopyKeptBeforeARestoreHoldsTheNewestWrites(): Unit = runBlocking {
        val dao = KhataDatabase.get(context).khataDao()
        val name = "Snapshot check " + System.nanoTime()
        val id = dao.insertParty(Party(name = name))
        try {
            assertTrue("kept", Snapshots.keepNow(context))
            val kept = Snapshots.list(context).first { it.beforeRestore }
            try {
                assertFalse("no log left beside the copy", File(kept.file.path + "-wal").exists())
                SQLiteDatabase.openDatabase(kept.file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                    db.rawQuery("PRAGMA quick_check", null).use { c ->
                        assertTrue(c.moveToFirst())
                        assertEquals("ok", c.getString(0))
                    }
                    db.rawQuery("SELECT COUNT(*) FROM parties WHERE name = ?", arrayOf(name)).use { c ->
                        assertTrue(c.moveToFirst())
                        assertEquals("the newest write is in the copy", 1, c.getInt(0))
                    }
                }
            } finally {
                kept.file.delete()
            }
        } finally {
            dao.softDeleteParty(id)
            dao.purgeParty(id)
        }
        Unit
    }

    /**
     * A copy kept with its own log (a damaged book's, or a before-restore copy whose close could
     * not fold) comes back WITH the writes in that log (23 Oct 2026, review R07: the file was
     * copied alone and the book came back older than the copy chosen).
     */
    @Test
    fun aCopyWithItsOwnLogIsRestoredWithTheLog(): Unit = runBlocking {
        val before = "Wal restore " + System.nanoTime()
        val after = before + " (in the log)"
        val id = KhataDatabase.get(context).khataDao().insertParty(Party(name = before))
        val made = mutableListOf<File>()
        try {
            assertTrue("kept", Snapshots.keepNow(context))
            val base = Snapshots.list(context).first { it.beforeRestore }.file
            made += base
            // The copy with a write that lives only in its log: opened in WAL mode, no automatic
            // checkpoint, one change, and the file and its log copied while it is still open.
            val chosen = File(base.parentFile, base.name.removeSuffix(".db") + "-77.db")
            made += chosen
            val db = SQLiteDatabase.openDatabase(
                base.path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING
            )
            try {
                db.rawQuery("PRAGMA wal_autocheckpoint=0", null).use { it.moveToFirst() }
                db.execSQL("UPDATE parties SET name = ? WHERE id = ?", arrayOf<Any>(after, id))
                val wal = File(base.path + "-wal")
                assertTrue("the change is in the log", wal.isFile && wal.length() > 0)
                base.copyTo(chosen, overwrite = true)
                wal.copyTo(File(chosen.path + "-wal"), overwrite = true)
            } finally {
                db.close()
            }
            val snapshot = Snapshots.Snapshot(chosen, System.currentTimeMillis(), chosen.length(), beforeRestore = true)
            val result = Snapshots.restore(context, snapshot)
            assertTrue("restored", result is Snapshots.RestoreResult.Ok)
            val party = KhataDatabase.get(context).khataDao().getParty(id)
            assertEquals("the log's write came back with the copy", after, party?.name)
        } finally {
            val dao = KhataDatabase.get(context).khataDao()
            dao.softDeleteParty(id)
            dao.purgeParty(id)
            for (f in made) { f.delete(); File(f.path + "-wal").delete(); File(f.path + "-shm").delete() }
        }
        Unit
    }
}
