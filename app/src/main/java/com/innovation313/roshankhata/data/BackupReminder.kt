package com.innovation313.roshankhata.data

import android.content.Context

/**
 * Remembers when the owner last took a backup, and decides when a gentle nudge
 * is due. Nothing here sends or stores anything off the device — it is a single
 * timestamp in shared preferences, used only to show a reminder on the home
 * screen after a week of silence.
 */
object BackupReminder {

    private const val PREFS = "backup_reminder"
    private const val KEY_LAST = "last_backup_at"
    private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

    /** Record that a backup just succeeded. */
    fun recordBackup(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST, System.currentTimeMillis()).apply()
    }

    /** The last backup time, or 0 if the owner has never backed up. */
    fun lastBackupAt(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST, 0)

    /**
     * True when a reminder is worth showing: either the owner has never backed
     * up, or it has been more than a week. We only nudge once things are worth
     * nudging about — an empty book doesn't need protecting.
     */
    fun isReminderDue(context: Context, hasData: Boolean): Boolean {
        if (!hasData) return false
        val last = lastBackupAt(context)
        if (last == 0L) return true
        return System.currentTimeMillis() - last > WEEK_MS
    }
}
