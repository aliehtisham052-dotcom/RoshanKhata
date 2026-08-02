package com.innovation313.roshankhata.data

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
 * Restoring from a snapshot is deliberately not wired to a button yet. It is
 * a recovery act, wants doing carefully, and the files being present is what
 * matters — they can be restored by hand or by a future guided screen.
 */
object Snapshots {

    private const val KEEP = 7
    private const val PREFS = "snapshots"
    private const val KEY_DAY = "last_day"

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
