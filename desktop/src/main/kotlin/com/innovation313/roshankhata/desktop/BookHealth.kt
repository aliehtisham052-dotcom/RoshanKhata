package com.innovation313.roshankhata.desktop

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.innovation313.roshankhata.data.BackupCodec
import com.innovation313.roshankhata.data.DesktopDatabase
import com.innovation313.roshankhata.data.FileShopSettings
import com.innovation313.roshankhata.data.KhataDatabase
import kotlinx.coroutines.runBlocking
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The book checked before it is opened (20 Oct), the phone's own insurance
 * brought to the computer: a power cut mid-write, a failing disk or a bad
 * copy can leave the SQLite file unreadable, and Room would then fail at the
 * first query with the owner looking at a blank window. Here SQLite's own
 * quick_check runs first; a file that fails it is set aside (never deleted —
 * `khata.db.corrupt-<time>`) and the book is rebuilt from the newest daily
 * copy that reads back whole: this computer's own, then the outside folder's.
 * The owner is told which day's copy came back, or that none could.
 */
object BookHealth {

    /** What happened on opening. [recoveredFrom] is the copy's file when the book was rebuilt. */
    data class Opened(val db: KhataDatabase, val corruptKept: File? = null, val recoveredFrom: File? = null, val nothingToRecover: Boolean = false)

    /** SQLite's quick_check on [file]: true when it answers "ok"; a missing file is a new book and passes. */
    fun isSound(file: File): Boolean {
        if (!file.isFile) return true
        return runCatching {
            val c = BundledSQLiteDriver().open(file.absolutePath)
            try {
                val s = c.prepare("PRAGMA quick_check")
                try { s.step() && s.getText(0) == "ok" } finally { s.close() }
            } finally { c.close() }
        }.getOrDefault(false)
    }

    /**
     * Opens [file] as the book; when it is not sound, sets it aside and
     * rebuilds from the newest good copy in [copyDirs] (first dir first).
     */
    fun open(file: File, settings: FileShopSettings, copyDirs: List<File>): Opened {
        if (isSound(file)) return Opened(DesktopDatabase.open(file))
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.ENGLISH).format(Date())
        val kept = File(file.path + ".corrupt-$stamp")
        file.renameTo(kept)
        // The write-ahead log and its index belong to the bad file; a fresh book must not inherit them.
        for (suffix in listOf("-wal", "-shm", "-journal")) {
            val side = File(file.path + suffix)
            if (side.isFile) side.renameTo(File(kept.path + suffix))
        }
        val db = DesktopDatabase.open(file)
        val copy = copyDirs.flatMap { DailyCopies.list(it) }.sortedByDescending { it.lastModified() }.firstOrNull { good ->
            val (result, data) = BackupCodec.parseFile(good)
            result is BackupCodec.ImportResult.Ok && data != null
        }
        if (copy == null) return Opened(db, corruptKept = kept, nothingToRecover = true)
        val data = BackupCodec.parseFile(copy).second!!
        runBlocking {
            BackupCodec.restoreLedger(db.khataDao(), data)
            data.businessProfile?.let { BackupCodec.restoreProfile(settings, it) }
        }
        return Opened(db, corruptKept = kept, recoveredFrom = copy)
    }
}
