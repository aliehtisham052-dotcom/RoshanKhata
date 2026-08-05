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

    /**
     * Per business, with Business 1 on the legacy key it has always used.
     * Backing up one shop must not silence the reminder for another — each
     * book earns its own "last protected" time.
     */
    private fun key(context: Context) = keyOf(Businesses.active(context).id)

    /**
     * The key for a named business, whether or not it is the open one.
     *
     * Needed so a closed shop can be asked when it was last protected without
     * opening its database — the reminder sweep and the businesses list both
     * want that answer for every shop, and switching business to get it would
     * be an absurd price for reading one number.
     */
    private fun keyOf(businessId: Long) =
        KEY_LAST + if (businessId == 1L) "" else "_b$businessId"

    /** Record that a backup just succeeded. */
    fun recordBackup(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(key(context), System.currentTimeMillis()).apply()
    }

    /**
     * Forgets this business's last-backup time. Called only when the
     * business itself is being deleted — a shop that no longer exists has
     * nothing left to remind anyone about.
     */
    fun clear(context: Context, businessId: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(keyOf(businessId)).apply()
    }

    /** The last backup time, or 0 if the owner has never backed up. */
    fun lastBackupAt(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(key(context), 0)

    /** The last backup time for a named business. 0 if it has never been backed up. */
    fun lastBackupAtOf(context: Context, businessId: Long): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(keyOf(businessId), 0)

    /**
     * True when a shop that HAS been backed up before has now gone a week.
     *
     * Deliberately narrower than [isReminderDue], and used for shops that are
     * not open: it asks only the timestamp, never the ledger. A shop the owner
     * has protected before and then left for a week is worth a word; a shop
     * never backed up might equally be one created this morning and still
     * empty, and this check cannot tell the difference without opening its
     * database. So it stays quiet rather than guess — the open shop, whose
     * book can actually be counted, keeps the fuller rule.
     */
    fun isLapsedBackup(context: Context, businessId: Long): Boolean {
        val last = lastBackupAtOf(context, businessId)
        if (last == 0L) return false
        return System.currentTimeMillis() - last > WEEK_MS
    }

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
