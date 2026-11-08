package com.innovation313.roshankhata.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A book that SQLite calls corrupt is KEPT, never deleted (8 Nov 2026 review, C3).
 *
 * Android's default open-helper callback deletes the database file on a corruption error; the app then
 * opened an empty book without a word, and the owner's next entry (and the next automatic Drive backup)
 * replaced everything that was good. This is what the computer's `BookHealth` already did: the damaged file
 * is renamed to `<book>.corrupt-<time>` (with its -wal/-shm), the book is rebuilt from the newest daily
 * copy that is whole ([Snapshots.rebuildMissingBook]), and the owner is told once ([notice]). Until the
 * owner has read that notice the automatic Drive backup is held ([holdsBackup]).
 *
 * Nothing is rebuilt inside the callback (SQLite is mid-error there): it only moves the file aside and
 * leaves a note in preferences; the next [KhataDatabase.get] does the rebuild before Room opens the file.
 */
object BookRecovery {
    private const val PREFS = "book_recovery"
    private const val KEY_NOTICE = "notice"
    private const val KEY_KEPT = "kept"
    private const val KEY_COPY_AT = "copy_at"
    private const val KEY_HOLD = "hold_auto_backup"
    private const val KEEP_DAMAGED = 3
    private val SIDES = listOf("-wal", "-shm", "-journal")

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun pendingKey(name: String) = "pending:$name"

    /** What the owner is told once: where the damaged file was kept, and the copy the book was rebuilt from (null: none). */
    data class Notice(val kept: String?, val copyAt: Long?)

    /** Moves the damaged book (and its log files) aside. Returns where it went, or null when it could not be moved. */
    fun keepAside(context: Context, path: String?): File? {
        if (path.isNullOrEmpty()) return null
        val file = File(path)
        if (!file.isFile) return null
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.ENGLISH).format(Date())
        val kept = File(file.path + ".corrupt-$stamp")
        if (!file.renameTo(kept)) return null
        for (side in SIDES) {
            val f = File(file.path + side)
            if (f.isFile) f.renameTo(File(kept.path + side))
        }
        // The newest few are enough; each is a whole book.
        file.parentFile?.listFiles { f -> f.name.startsWith(file.name + ".corrupt-") && SIDES.none { f.name.endsWith(it) } }
            ?.sortedByDescending { it.name }?.drop(KEEP_DAMAGED)
            ?.forEach { old -> old.delete(); SIDES.forEach { File(old.path + it).delete() } }
        prefs(context).edit()
            .putBoolean(pendingKey(file.name), true)
            .putString(KEY_KEPT, kept.path)
            .putBoolean(KEY_HOLD, true)
            .commit()
        return kept
    }

    /**
     * Before Room opens [dbName]: when that book was moved aside as damaged and is not there, rebuild it from the
     * newest daily copy that is whole. No copy at all: the book starts empty and the notice says so.
     */
    fun recoverIfNeeded(context: Context, dbName: String) {
        val p = prefs(context)
        if (!p.getBoolean(pendingKey(dbName), false)) return
        // Only the open shop's own copies are searched ([Snapshots.list]); another shop waits until it is open.
        if (dbName != Businesses.activeDbFile(context)) return
        val live = context.getDatabasePath(dbName)
        if (live.isFile) { p.edit().remove(pendingKey(dbName)).apply(); return }
        val used = try { Snapshots.rebuildMissingBook(context, live) } catch (_: Exception) { null }
        p.edit()
            .remove(pendingKey(dbName))
            .putBoolean(KEY_NOTICE, true)
            .putLong(KEY_COPY_AT, used?.takenOn ?: -1L)
            .commit()
    }

    fun notice(context: Context): Notice? {
        val p = prefs(context)
        if (!p.getBoolean(KEY_NOTICE, false)) return null
        val at = p.getLong(KEY_COPY_AT, -1L)
        return Notice(p.getString(KEY_KEPT, null), at.takeIf { it > 0 })
    }

    /** The owner has read it: the notice goes and automatic Drive backups may run again. */
    fun acknowledge(context: Context) {
        prefs(context).edit().remove(KEY_NOTICE).putBoolean(KEY_HOLD, false).apply()
    }

    /** True from the moment a book was set aside until the owner has read the notice (an automatic backup waits). */
    fun holdsBackup(context: Context): Boolean = prefs(context).getBoolean(KEY_HOLD, false)

    /** Room's open helper with the corruption callback this file describes, in place of the framework's deleting one. */
    class SafeFactory(
        private val context: Context,
        private val onMovedAside: () -> Unit = {},
        private val inner: SupportSQLiteOpenHelper.Factory = FrameworkSQLiteOpenHelperFactory()
    ) : SupportSQLiteOpenHelper.Factory {
        override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
            val real = configuration.callback
            val guard = object : SupportSQLiteOpenHelper.Callback(real.version) {
                override fun onConfigure(db: SupportSQLiteDatabase) = real.onConfigure(db)
                override fun onCreate(db: SupportSQLiteDatabase) = real.onCreate(db)
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = real.onUpgrade(db, oldVersion, newVersion)
                override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = real.onDowngrade(db, oldVersion, newVersion)
                override fun onOpen(db: SupportSQLiteDatabase) = real.onOpen(db)
                override fun onCorruption(db: SupportSQLiteDatabase) {
                    if (keepAside(context, db.path) != null) onMovedAside()
                }
            }
            return inner.create(
                SupportSQLiteOpenHelper.Configuration.builder(configuration.context)
                    .name(configuration.name)
                    .callback(guard)
                    .noBackupDirectory(configuration.useNoBackupDirectory)
                    .allowDataLossOnRecovery(configuration.allowDataLossOnRecovery)
                    .build()
            )
        }
    }
}
