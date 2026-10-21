package com.innovation313.roshankhata.data

import kotlinx.coroutines.launch

import android.content.Context
import java.io.File

/**
 * A quiet daily copy of the ledger, kept on the phone itself.
 *
 * This is the insurance under everything else. The file backup depends on the
 * owner remembering; the Drive backup depends on them turning it on; this
 * depends on nothing — once a day, the first time the ledger is opened, the
 * database is copied into the app's own private folder. Seven days are kept
 * and the eighth deletes itself. No permission, no network, no interaction.
 *
 * If the database is ever damaged — a dying flash chip, a power cut at the
 * wrong microsecond — the loss is capped at one day, not a shop's lifetime.
 *
 * Two rules keep the insurance itself honest:
 *
 * - The copy is checked before it is made. A sick database must never
 *   overwrite the healthy copies that are the whole point of keeping them, so
 *   PRAGMA integrity_check gates the snapshot: anything short of "ok" and
 *   today's snapshot simply does not happen, leaving yesterday's good ones
 *   untouched.
 * - The WAL is folded in first (wal_checkpoint TRUNCATE), so the copied file
 *   is the complete ledger and not the ledger minus its most recent writes.
 *
 * Restoring is wired up: see [list] and [restore]. The files existing was
 * never the whole job — insurance nobody can claim is not insurance, and for
 * the one failure that locks an owner out of their own book (a migration that
 * refuses to open the database) these copies were the only way back and had
 * no door.
 */
object Snapshots {

    private const val KEEP = 7
    private const val PREFS = "snapshots"
    private const val KEY_DAY = "last_day"

    /** One kept copy of the ledger. */
    data class Snapshot(
        val file: File,
        /** Midnight of the day it was taken, from the file name — not the file's own clock; the minute too for a copy kept before a restore. */
        val takenOn: Long,
        val bytes: Long,
        /** A copy kept just before a restore ([keepNow]): its name carries the time. */
        val beforeRestore: Boolean = false
    )

    /**
     * The active business's snapshots, newest first.
     *
     * Reads names only. Deliberately touches neither Room nor the database
     * file, so this still answers when the database is the thing that is
     * broken — which is the day it matters.
     */
    fun list(context: Context): List<Snapshot> {
        val business = Businesses.active(context)
        val prefix = if (business.id == 1L) "khata-" else "khata-b${business.id}-"
        val pattern = Regex("^" + Regex.escape(prefix) + "(\\d{8})(?:-(\\d{4}))?\\.db$")
        val dir = File(context.filesDir, "snapshots")

        return dir.listFiles().orEmpty().mapNotNull { f ->
            val m = pattern.find(f.name) ?: return@mapNotNull null
            val day = m.groupValues[1]
            val time = m.groupValues[2]
            val cal = java.util.Calendar.getInstance().apply {
                set(
                    day.substring(0, 4).toInt(),
                    day.substring(4, 6).toInt() - 1,
                    day.substring(6, 8).toInt(),
                    if (time.isEmpty()) 0 else time.substring(0, 2).toInt(),
                    if (time.isEmpty()) 0 else time.substring(2, 4).toInt(),
                    0
                )
                set(java.util.Calendar.MILLISECOND, 0)
            }
            Snapshot(f, cal.timeInMillis, f.length(), beforeRestore = time.isNotEmpty())
        }.sortedByDescending { it.takenOn }
    }

    sealed class RestoreResult {
        object Ok : RestoreResult()
        /** The snapshot itself is damaged. The live ledger was not touched. */
        object SnapshotUnhealthy : RestoreResult()
        object Failed : RestoreResult()
    }

    /**
     * Put the ledger back to the day this snapshot was taken.
     *
     * Order matters here more than anywhere else in the app:
     *
     * 1. The snapshot is opened and integrity-checked FIRST. A damaged copy
     *    must never be allowed to replace a working ledger — that would turn
     *    the insurance into the accident.
     * 2. Today's state is copied aside before anything is overwritten, so
     *    restoring the wrong day is itself undoable. It goes in beside the
     *    others under today's name, which is exactly where the owner would
     *    look for it.
     * 3. Room's handle is closed, and the -wal and -shm files are deleted.
     *    This is the step that is easy to miss and fatal to skip: a
     *    write-ahead log left behind belongs to the OLD database, and SQLite
     *    would replay it over the restored file and corrupt it.
     * 4. Only then is the file replaced, and via a temp-and-rename so that a
     *    crash mid-copy leaves the old file intact rather than half of each.
     */
    fun restore(context: Context, snapshot: Snapshot): RestoreResult {
        if (!snapshot.file.exists()) return RestoreResult.Failed

        if (!isHealthy(snapshot.file)) return RestoreResult.SnapshotUnhealthy

        return try {
            val business = Businesses.active(context)
            val live = context.getDatabasePath(business.file)
            val dir = File(context.filesDir, "snapshots").apply { mkdirs() }
            val prefix = if (business.id == 1L) "khata-" else "khata-b${business.id}-"
            // Kept under its own name, with the minute (21 Oct 2026, the critical review): it was
            // written as "today's" copy — the very file the owner may have just chosen to go back
            // to — so restoring today's copy wrote the broken book over it and then restored that.
            val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.ENGLISH).format(java.util.Date())

            // Closing folds the write-ahead log into the file, so the copy below is the whole book.
            KhataDatabase.closeActive()

            // The ledger as it stands, kept before it is replaced. No copy, no restore.
            if (live.exists()) {
                val keep = File(dir, "$prefix$stamp.db")
                if (keep.path == snapshot.file.path) return RestoreResult.Failed
                val keepTmp = File(dir, "$prefix$stamp.db.tmp")
                live.inputStream().use { i -> keepTmp.outputStream().use { o -> i.copyTo(o) } }
                if (!keepTmp.renameTo(keep)) { keepTmp.delete(); return RestoreResult.Failed }
                // A log the close could not fold in goes with it, so the kept copy opens whole.
                val wal = File(live.parentFile, live.name + "-wal")
                if (wal.isFile && wal.length() > 0) wal.inputStream().use { i -> File(keep.path + "-wal").outputStream().use { o -> i.copyTo(o) } }
            }

            // The old write-ahead log must not survive its database.
            File(live.parentFile, live.name + "-wal").delete()
            File(live.parentFile, live.name + "-shm").delete()

            val tmp = File(live.parentFile, live.name + ".restoring")
            snapshot.file.inputStream().use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
            live.delete()
            if (!tmp.renameTo(live)) {
                tmp.delete()
                return RestoreResult.Failed
            }

            RestoreResult.Ok
        } catch (e: Exception) {
            RestoreResult.Failed
        }
    }

    /** Copies kept before a restore, per business; the oldest beyond this go. */
    private const val KEEP_BEFORE_RESTORE = 3

    /**
     * The book as it stands, copied aside just before a restore replaces it
     * (21 Oct 2026, the critical review): a restore of the wrong copy is then
     * undone from this screen like any day's copy. Named with the minute
     * ("khata-20261021-1432.db") so two restores in a day keep both; the last
     * three are kept. Never throws. Returns whether the copy is on disk: the
     * restore or merge that asked stops when it is not (21 Oct 2026 — a failed
     * copy was passed over in silence and the book replaced anyway).
     *
     * A book SQLite finds damaged is still copied, under its own name, never
     * over a good one: the restore that follows is how the owner gets out of
     * it, and refusing to keep it would either block that or lose it.
     */
    fun keepNow(context: Context): Boolean {
        return try {
            val business = Businesses.active(context)
            val prefix = if (business.id == 1L) "khata-" else "khata-b${business.id}-"
            val database = KhataDatabase.get(context)
            val db = database.openHelper.writableDatabase
            db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
            val healthy = db.query("PRAGMA integrity_check").use {
                it.moveToFirst() && it.getString(0).equals("ok", ignoreCase = true)
            }
            val source = context.getDatabasePath(database.openHelper.databaseName ?: business.file)
            // A new, empty book has nothing to keep and nothing to lose.
            if (!source.exists()) return true
            val dir = File(context.filesDir, "snapshots").apply { mkdirs() }
            val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.ENGLISH).format(java.util.Date())
            val target = File(dir, "$prefix$stamp.db")
            val tmp = File(dir, "$prefix$stamp.db.tmp")
            source.inputStream().use { input -> tmp.outputStream().use { output -> input.copyTo(output) } }
            if (!tmp.renameTo(target)) { tmp.delete(); return false }
            // The sick copy does not push a good one out of the three kept.
            if (healthy) {
                val mine = Regex("^" + Regex.escape(prefix) + "\\d{8}-\\d{4}\\.db$")
                dir.listFiles { f -> mine.matches(f.name) }
                    ?.sortedByDescending { it.name }
                    ?.drop(KEEP_BEFORE_RESTORE)
                    ?.forEach { it.delete(); File(it.path + "-wal").delete() }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Is this file a database SQLite is willing to vouch for? */
    private fun isHealthy(file: File): Boolean = try {
        android.database.sqlite.SQLiteDatabase.openDatabase(
            file.absolutePath, null,
            android.database.sqlite.SQLiteDatabase.OPEN_READONLY
        ).use { db ->
            db.rawQuery("PRAGMA integrity_check", null).use { c ->
                c.moveToFirst() && c.getString(0).equals("ok", ignoreCase = true)
            }
        }
    } catch (e: Exception) {
        false
    }

    private var afterSaleJob: kotlinx.coroutines.Job? = null

    /**
     * After a sale, a return or a void (21 Oct 2026): today's copy written
     * again a minute after the last of them — a burst of tickets is one copy,
     * each new one restarting the minute. The same checks as the day's first
     * copy ([maybeToday]): the book folded whole, checked, written beside.
     */
    fun afterSale(context: Context) {
        val app = context.applicationContext
        afterSaleJob?.cancel()
        afterSaleJob = AppScope.launch {
            kotlinx.coroutines.delay(60_000)
            refreshToday(app)
        }
    }

    /** Today's copy, written again whatever the day marker says. */
    fun refreshToday(context: Context) {
        val business = Businesses.active(context)
        val dayKey = if (business.id == 1L) KEY_DAY else "${KEY_DAY}_b${business.id}"
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(dayKey).commit()
        maybeToday(context)
    }

    /** Once per calendar day per business, from a background thread. Cheap every other call. */
    fun maybeToday(context: Context) {
        // Resolve the business ONCE and use it for the day-marker, the file,
        // and the rotation below — the three must always agree on whose
        // ledger this is.
        val business = Businesses.active(context)
        // Business 1 keeps the pre-multi-business key and file names, so an
        // update to this version neither re-snapshots a day it already
        // covered nor orphans the snapshots it already made.
        val dayKey = if (business.id == 1L) KEY_DAY else "${KEY_DAY}_b${business.id}"
        val prefix = if (business.id == 1L) "khata-" else "khata-b${business.id}-"

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = android.text.format.DateFormat.format("yyyyMMdd", java.util.Date()).toString()
        if (prefs.getString(dayKey, null) == today) return

        try {
            val database = KhataDatabase.get(context)
            val db = database.openHelper.writableDatabase

            // Fold the write-ahead log into the main file, so the file on disk
            // is the whole ledger.
            db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }

            // Health gate. "ok" or no snapshot today.
            val healthy = db.query("PRAGMA integrity_check").use {
                it.moveToFirst() && it.getString(0).equals("ok", ignoreCase = true)
            }
            if (!healthy) return

            // Copy the file the checked database is actually open on — asked
            // of the connection itself, not re-derived, so the integrity
            // check and the copy can never disagree about which file they
            // mean.
            val sourceName = database.openHelper.databaseName ?: business.file
            val source = context.getDatabasePath(sourceName)
            if (!source.exists()) return

            val dir = File(context.filesDir, "snapshots").apply { mkdirs() }
            val target = File(dir, "$prefix$today.db")
            val tmp = File(dir, "$prefix$today.db.tmp")
            source.inputStream().use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            // Written whole, then renamed — a crash mid-copy leaves a .tmp to
            // ignore, never a half-file that looks like a snapshot.
            if (!tmp.renameTo(target)) {
                tmp.delete(); return
            }

            // Seven stay, the rest go — per business. Each business's
            // snapshots rotate among themselves only: Business 1's filter
            // must not match "khata-b2-…", and business N's prefix matches
            // nothing else, so no shop's insurance can delete another's.
            val own = Regex(
                if (business.id == 1L) "^khata-\\d{8}\\.db$"
                else "^khata-b${business.id}-\\d{8}\\.db$"
            )
            dir.listFiles { f -> f.name.endsWith(".tmp") }?.forEach { it.delete() }
            dir.listFiles { f -> own.matches(f.name) }
                ?.sortedByDescending { it.name }
                ?.drop(KEEP)
                ?.forEach { it.delete() }

            prefs.edit().putString(dayKey, today).apply()
        } catch (_: Exception) {
            // Insurance must never become the accident: whatever went wrong,
            // the app carries on and tries again tomorrow.
        }
    }
}
