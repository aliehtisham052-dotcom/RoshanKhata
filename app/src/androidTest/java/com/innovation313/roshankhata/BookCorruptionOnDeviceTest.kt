package com.innovation313.roshankhata

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import com.innovation313.roshankhata.data.BookRecovery
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A book SQLite calls corrupt is kept, not deleted (8 Nov 2026 review, C3). Android's default callback deletes
 * the file; [BookRecovery.SafeFactory] moves it to "<book>.corrupt-<time>" and holds automatic Drive backups.
 */
class BookCorruptionOnDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val file = File(context.cacheDir, "corruption-probe.db")

    @After fun clean() {
        file.parentFile?.listFiles { f -> f.name.startsWith(file.name) }?.forEach { it.delete() }
    }

    @Test
    fun a_corrupt_book_is_kept_aside_and_automatic_backup_is_held() {
        file.writeBytes(ByteArray(8192) { 0x41 }) // not a database: SQLite answers "file is not a database"
        var moved = false
        val helper = BookRecovery.SafeFactory(context, onMovedAside = { moved = true }).create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(file.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {}
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )
        try { helper.writableDatabase.close() } catch (_: Exception) { /* the open itself fails once, as it always did */ }
        val kept = file.parentFile!!.listFiles { f -> f.name.startsWith(file.name + ".corrupt-") }.orEmpty()
        assertTrue("the damaged file is kept", kept.isNotEmpty())
        assertTrue("the framework did not delete it", kept.first().length() == 8192L)
        assertTrue("the open book was let go", moved)
        assertTrue("automatic Drive backup waits", BookRecovery.holdsBackup(context))
        BookRecovery.acknowledge(context)
        assertFalse("and runs again once read", BookRecovery.holdsBackup(context))
    }
}
