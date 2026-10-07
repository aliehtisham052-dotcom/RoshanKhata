package com.innovation313.roshankhata.data

import android.content.Context
import com.innovation313.roshankhata.BuildConfig

/**
 * The few things the bell must remember between runs (7 Oct): when the last
 * automatic backup failed, when another phone was found holding the backup,
 * and which version of the app the owner has already been told about. The
 * daily worker writes the first two; the bell (HomeAlerts) reads all three.
 * Nothing here is the ledger — it is the bell's own notebook.
 */
object AlertNotes {
    private const val PREFS = "alert_notes"
    private const val KEY_FAILED_AT = "backup_failed_at"
    private const val KEY_OTHER_PHONE_AT = "backup_other_phone_at"
    private const val KEY_SEEN_VERSION = "seen_version"

    private fun p(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun backupFailed(ctx: Context) = p(ctx).edit().putLong(KEY_FAILED_AT, System.currentTimeMillis()).apply()
    fun backupFailedAt(ctx: Context): Long = p(ctx).getLong(KEY_FAILED_AT, 0L)

    fun otherPhone(ctx: Context) = p(ctx).edit().putLong(KEY_OTHER_PHONE_AT, System.currentTimeMillis()).apply()
    fun otherPhoneAt(ctx: Context): Long = p(ctx).getLong(KEY_OTHER_PHONE_AT, 0L)

    /**
     * True once per update: the app's version code is newer than the one the
     * owner was last told about. A fresh install is not an update — the first
     * call records the current version and says nothing.
     */
    fun updatePending(ctx: Context): Boolean {
        val prefs = p(ctx)
        if (!prefs.contains(KEY_SEEN_VERSION)) {
            prefs.edit().putInt(KEY_SEEN_VERSION, BuildConfig.VERSION_CODE).apply()
            return false
        }
        return prefs.getInt(KEY_SEEN_VERSION, 0) != BuildConfig.VERSION_CODE
    }

    fun updateSeen(ctx: Context) = p(ctx).edit().putInt(KEY_SEEN_VERSION, BuildConfig.VERSION_CODE).apply()
}
