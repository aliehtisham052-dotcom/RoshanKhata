package com.innovation313.roshankhata.desktop

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.innovation313.roshankhata.data.BackupCodec
import com.innovation313.roshankhata.data.DesktopDatabase
import com.innovation313.roshankhata.data.FileShopSettings
import com.innovation313.roshankhata.data.KhataDatabase
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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
 *
 * Only a book SQLite itself calls damaged is replaced (21 Oct 2026). Until
 * then any error at all — the file held by another program, the SQLite
 * library blocked, a full disk — counted as "damaged": the good book was
 * renamed away and an older copy put in its place, or, when the rename
 * failed, the older copy was written over the live book. Now such an error
 * leaves the book exactly as it is and says it could not be opened
 * ([CannotOpen]); the rebuild is made beside the book first and swapped in
 * only once it is complete, and every rename is checked.
 */
object BookHealth {

    /** What happened on opening. [recoveredFrom] is the copy's file when the book was rebuilt. */
    data class Opened(
        val db: KhataDatabase,
        val corruptKept: File? = null,
        val recoveredFrom: File? = null,
        val nothingToRecover: Boolean = false,
        /** Books set aside earlier that are still beside this one (shown when this one is empty). */
        val setAside: List<File> = emptyList()
    )

    /** The book could not be read for a reason other than damage; nothing was changed. */
    class CannotOpen(val file: File, cause: Throwable?) : Exception("The book could not be opened: ${file.name}", cause)

    enum class Verdict { SOUND, DAMAGED, UNREADABLE }

    /**
     * What an error from SQLite says about the file. Only "damaged" (11,
     * SQLITE_CORRUPT) and "not a database" (26, SQLITE_NOTADB) mean the file
     * itself is bad; a busy, locked or missing library is not the book's fault.
     * The bundled driver words its errors "Error code: N, message: …".
     */
    fun classify(error: Throwable): Verdict {
        val text = error.message.orEmpty()
        val code = Regex("""Error code:\s*(\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull()
        if (code != null && (code and 0xFF) in setOf(11, 26)) return Verdict.DAMAGED
        val lower = text.lowercase(Locale.ROOT)
        if ("malformed" in lower || "not a database" in lower) return Verdict.DAMAGED
        return Verdict.UNREADABLE
    }

    /** SQLite's quick_check on [file]. A missing file is a new book and is sound. */
    fun check(file: File): Verdict {
        if (!file.isFile) return Verdict.SOUND
        return try {
            val c = BundledSQLiteDriver().open(file.absolutePath)
            try {
                // A book another program is busy with is waited for a little, not judged.
                val wait = c.prepare("PRAGMA busy_timeout = 5000")
                try { wait.step() } finally { wait.close() }
                val s = c.prepare("PRAGMA quick_check")
                try { if (s.step() && s.getText(0) == "ok") Verdict.SOUND else Verdict.DAMAGED } finally { s.close() }
            } finally { c.close() }
        } catch (e: Throwable) {
            classify(e)
        }
    }

    /** True when SQLite finds [file] sound (kept for the tests and the old callers). */
    fun isSound(file: File): Boolean = check(file) == Verdict.SOUND

    private val SIDES = listOf("", "-wal", "-shm", "-journal")

    /** [from] and its -wal/-shm/-journal moved to [to]; false (and nothing half-moved) when the main file would not go. */
    private fun moveBook(from: File, to: File): Boolean {
        val main = runCatching { Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING); true }.getOrDefault(false)
        if (!main) return false
        for (side in SIDES.drop(1)) {
            val f = File(from.path + side)
            if (f.isFile) runCatching { Files.move(f.toPath(), File(to.path + side).toPath(), StandardCopyOption.REPLACE_EXISTING) }
        }
        return true
    }

    private fun deleteBook(file: File) { for (side in SIDES) File(file.path + side).delete() }

    /** Books set aside beside [file] earlier (khata.db.corrupt-…), newest first. */
    fun setAside(file: File): List<File> =
        file.parentFile?.listFiles { f -> f.name.startsWith(file.name + ".corrupt-") && !f.name.endsWith("-wal") && !f.name.endsWith("-shm") && !f.name.endsWith("-journal") }
            ?.sortedByDescending { it.name } ?: emptyList()

    /**
     * Opens [file] as the book; when SQLite finds it damaged, sets it aside
     * and rebuilds from the newest good copy in [copyDirs] (by the copies'
     * days, not their file times). Throws [CannotOpen] — having changed
     * nothing — when the file cannot be read for any other reason. Blocking:
     * call it off the window's thread.
     */
    fun open(file: File, settings: FileShopSettings, copyDirs: List<File>): Opened {
        when (check(file)) {
            Verdict.SOUND -> return Opened(DesktopDatabase.open(file), setAside = setAside(file))
            Verdict.UNREADABLE -> throw CannotOpen(file, null)
            Verdict.DAMAGED -> Unit
        }
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.ENGLISH).format(Date())
        val kept = File(file.path + ".corrupt-$stamp")
        val copy = DailyCopies.newestFirst(copyDirs).firstOrNull { good ->
            val (result, data) = BackupCodec.parseFile(good)
            result is BackupCodec.ImportResult.Ok && data != null
        }
        if (copy == null) {
            // Nothing to rebuild from: the damaged file is kept aside and the book starts empty, said once.
            if (!moveBook(file, kept)) throw CannotOpen(file, null)
            return Opened(DesktopDatabase.open(file), corruptKept = kept, nothingToRecover = true)
        }
        val data = BackupCodec.parseFile(copy).second!!
        // Rebuilt beside the book, and swapped in only once it is whole.
        val building = File(file.path + ".recovering")
        deleteBook(building)
        val fresh = DesktopDatabase.open(building)
        try {
            runBlocking { BackupCodec.restoreLedger(fresh.khataDao(), data) }
        } catch (e: Throwable) {
            fresh.close(); deleteBook(building)
            throw CannotOpen(file, e)
        }
        fresh.close()
        if (!moveBook(file, kept)) { deleteBook(building); throw CannotOpen(file, null) }
        if (!moveBook(building, file)) {
            // Put the damaged one back where it was rather than leave no book at all.
            moveBook(kept, file)
            deleteBook(building)
            throw CannotOpen(file, null)
        }
        data.businessProfile?.let { BackupCodec.restoreProfile(settings, it) }
        return Opened(DesktopDatabase.open(file), corruptKept = kept, recoveredFrom = copy)
    }
}
