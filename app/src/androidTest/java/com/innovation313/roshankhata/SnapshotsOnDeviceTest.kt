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
}
