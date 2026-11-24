package com.innovation313.roshankhata.desktop

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.innovation313.roshankhata.data.BackupCodec
import com.innovation313.roshankhata.data.DesktopDatabase
import com.innovation313.roshankhata.data.FileShopSettings
import com.innovation313.roshankhata.data.KHATA_DB_VERSION
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
        val setAside: List<File> = emptyList(),
        /** The rebuilt book is the copy's, but the shop's details could not be written (BC-258): Business Profile to be set again. */
        val profileNotSet: Boolean = false
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
    fun check(file: File): Verdict = checkWhy(file).first

    /**
     * [check] with the error behind its verdict, when there was one (R85, 23 Oct 2026): an unreadable book
     * was said as "cannot open" with the reason thrown away, so errors.log and Report a problem had nothing.
     */
    fun checkWhy(file: File): Pair<Verdict, Throwable?> {
        if (!file.isFile) return Verdict.SOUND to null
        return try {
            val c = BundledSQLiteDriver().open(file.absolutePath)
            try {
                // A book another program is busy with is waited for a little, not judged.
                val wait = c.prepare("PRAGMA busy_timeout = 5000")
                try { wait.step() } finally { wait.close() }
                val s = c.prepare("PRAGMA quick_check")
                try { (if (s.step() && s.getText(0) == "ok") Verdict.SOUND else Verdict.DAMAGED) to null } finally { s.close() }
            } finally { c.close() }
        } catch (e: Throwable) {
            classify(e) to e
        }
    }

    /** True when SQLite finds [file] sound (kept for the tests and the old callers). */
    fun isSound(file: File): Boolean = check(file) == Verdict.SOUND

    private val SIDES = listOf("", "-wal", "-shm", "-journal")

    /**
     * [from] and its -wal/-shm/-journal moved to [to], all or nothing (BC-258, 24 Nov 2026; the phone's SnapshotSwap
     * order). The side files go FIRST and the main file LAST, and a side that will not go puts the ones already moved
     * back and leaves the main file where it was: before, the main file went first and a side that failed was passed
     * over, so the old log stayed beside the new file (SQLite would replay it over the wrong book) or the new book's
     * own log was left behind (its last writes lost). A log is never deleted: it moves with its book. What is left of
     * a book at [to] (a side file the source does not have) belongs to a file that is being replaced, and goes.
     */
    internal fun moveBook(from: File, to: File, move: (File, File) -> Unit = { a, b -> Files.move(a.toPath(), b.toPath(), StandardCopyOption.REPLACE_EXISTING) }): Boolean {
        if (!from.isFile) return false
        val moved = ArrayList<String>()
        try {
            for (side in SIDES.drop(1)) {
                val f = File(from.path + side)
                if (f.exists()) { move(f, File(to.path + side)); moved += side }
                else File(to.path + side).delete()
            }
            move(from, to)
            return true
        } catch (e: Throwable) {
            for (side in moved.asReversed()) runCatching { move(File(to.path + side), File(from.path + side)) }
            return false
        }
    }

    /** The version SQLite's `user_version` holds in [file] (Room keeps the schema version there); null when it cannot be read. */
    fun userVersionOf(file: File): Int? {
        if (!file.isFile) return null
        return try {
            val c = BundledSQLiteDriver().open(file.absolutePath)
            try {
                val s = c.prepare("PRAGMA user_version")
                try { if (s.step()) s.getLong(0).toInt() else null } finally { s.close() }
            } finally { c.close() }
        } catch (e: Throwable) { null }
    }

    /** A book made by this build or an older one: version 0 is a new, empty file. Room has no way down (the phone's Snapshots.canOpen). */
    fun canOpen(version: Int): Boolean = version <= KHATA_DB_VERSION

    /** The book is from a newer build than this one: refused before anything is touched. */
    class NewerBook(val version: Int) : Exception("This book was made by a newer version of Roshan Khata (schema $version; this one reads up to $KHATA_DB_VERSION). Update the app.")

    /** The log of [file] folded into it, so a book about to be moved carries no log (best effort; a book with none is left as it is). */
    private fun foldLog(file: File) {
        val wal = File(file.path + "-wal")
        if (!wal.isFile || wal.length() == 0L) return
        runCatching {
            val c = BundledSQLiteDriver().open(file.absolutePath)
            try {
                val s = c.prepare("PRAGMA wal_checkpoint(TRUNCATE)")
                try { s.step() } finally { s.close() }
            } finally { c.close() }
        }
    }

    /**
     * A rebuild killed between its two moves leaves no book, the finished rebuild beside it as ".recovering" and the
     * damaged one kept. The rebuild is put in place when SQLite finds it sound; otherwise nothing is touched.
     */
    internal fun finishInterrupted(file: File): Boolean {
        val building = File(file.path + ".recovering")
        if (file.exists() || !building.isFile || setAside(file).isEmpty()) return false
        if (check(building) != Verdict.SOUND) return false
        return moveBook(building, file)
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
        finishInterrupted(file)
        val (verdict, why) = checkWhy(file)
        when (verdict) {
            Verdict.SOUND -> {
                // A book from a newer build is refused before Room touches it (BC-258; the phone's Snapshots.canOpen).
                userVersionOf(file)?.let { if (!canOpen(it)) throw CannotOpen(file, NewerBook(it)) }
                return Opened(DesktopDatabase.open(file), setAside = setAside(file))
            }
            Verdict.UNREADABLE -> throw CannotOpen(file, why)
            Verdict.DAMAGED -> Unit
        }
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.ENGLISH).format(Date())
        val kept = File(file.path + ".corrupt-$stamp")
        // A copy in the outside folder may be locked with this computer's backup password (17 Nov 2026): it is
        // tried, so a book is rebuilt from it too; one locked with another password is passed over, as a damaged one is.
        val passwords = runCatching { DeskBackupPassword.toTry(null) }.getOrDefault(emptyList())
        val copy = DailyCopies.newestFirst(copyDirs).firstOrNull { good ->
            val (result, data) = BackupCodec.parseFile(good, passwords)
            result is BackupCodec.ImportResult.Ok && data != null
        }
        if (copy == null) {
            // Nothing to rebuild from: the damaged file is kept aside and the book starts empty, said once.
            if (!moveBook(file, kept)) throw CannotOpen(file, null)
            return Opened(DesktopDatabase.open(file), corruptKept = kept, nothingToRecover = true)
        }
        val data = BackupCodec.parseFile(copy, passwords).second!!
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
        // The rebuild's own log is folded in before it moves: the book goes as one file (best effort; moveBook carries a log along).
        foldLog(building)
        if (!moveBook(file, kept)) { deleteBook(building); throw CannotOpen(file, null) }
        if (!moveBook(building, file)) {
            // Put the damaged one back where it was rather than leave no book at all.
            moveBook(kept, file)
            deleteBook(building)
            throw CannotOpen(file, null)
        }
        // The ledger is the copy's from here (BC-258, the phone's BC-245): a profile that cannot be written is noted to
        // errors.log and told in [Opened.profileNotSet]; it never throws out of here, which said "cannot open" for a
        // book that had been rebuilt and swapped in.
        var profileNotSet = false
        val profile = data.businessProfile
        if (profile != null) {
            val result = runBlocking {
                RestoreAfterCommit.run(listOf(RestoreAfterCommit.Step(RestoreAfterCommit.PROFILE) { BackupCodec.restoreProfile(settings, profile) }))
            }
            profileNotSet = result.profileNotSet
        }
        return Opened(DesktopDatabase.open(file), corruptKept = kept, recoveredFrom = copy, profileNotSet = profileNotSet)
    }
}
