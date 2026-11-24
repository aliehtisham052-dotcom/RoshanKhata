package com.innovation313.roshankhata.data

import java.io.File

/**
 * The last step of a daily-copy restore: the folded copy ([tmp], "<book>.restoring") takes the place of the book.
 * Plain files only, so a test can run every order of it without Android (BC-245, 24 Nov 2026).
 *
 * The old book's write-ahead log belongs to the OLD file: left beside the new one SQLite would replay it over the
 * restored book. But it also holds committed writes of the old book, so it is never deleted before the new file is in
 * place and never lost when the rename fails:
 *
 *  1. the log is MOVED aside ("<book>-wal.old"), the shared-memory index (rebuilt from the log, no data) is deleted;
 *  2. one rename puts the copy over the book;
 *  3. only a rename that went through deletes the aside log; one that failed (or threw) puts the log back, so the
 *     live book is exactly as it was and the owner has lost nothing.
 *
 * A process killed between 1 and 2 leaves the log aside, the copy still named ".restoring" and the old book:
 * [recover] (to be called before the book is opened) puts the log back in that case, or removes it when the rename
 * had gone through.
 */
object SnapshotSwap {

    /** True when [tmp] is now the book. Never throws for a failed rename; the log is back where it was then. */
    fun swap(live: File, tmp: File, rename: (File, File) -> Boolean = { from, to -> from.renameTo(to) }): Boolean {
        val wal = File(live.path + "-wal")
        val shm = File(live.path + "-shm")
        val aside = File(live.path + "-wal.old")
        // A log left aside by an earlier killed run is dealt with first (it may hold the only copy of those writes).
        recover(live)
        var moved = false
        if (wal.exists()) {
            aside.delete()
            moved = rename(wal, aside)
            // A log that cannot be moved aside could not be kept: the book is left exactly as it is.
            if (!moved) return false
        }
        shm.delete()
        val done = try { rename(tmp, live) } catch (e: Exception) { putBack(wal, aside, moved); throw e }
        if (!done) { putBack(wal, aside, moved); return false }
        aside.delete()
        return true
    }

    private fun putBack(wal: File, aside: File, moved: Boolean) {
        if (moved && aside.exists() && !wal.exists()) aside.renameTo(wal)
    }

    /**
     * After a run killed inside [swap]: the copy still waiting as ".restoring" means the rename never happened, so
     * the log aside goes back to its place; no ".restoring" means the book was replaced and the old log is dropped.
     * Call it before the book is opened, never while it is open.
     */
    fun recover(live: File) {
        val aside = File(live.path + "-wal.old")
        if (!aside.exists()) return
        val wal = File(live.path + "-wal")
        if (File(live.path + ".restoring").exists() && live.exists()) {
            if (!wal.exists()) aside.renameTo(wal)
        } else if (live.exists()) {
            aside.delete()
        }
    }
}
