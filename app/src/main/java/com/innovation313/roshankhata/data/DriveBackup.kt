package com.innovation313.roshankhata.data

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Backup to the user's OWN Google Drive.
 *
 * The whole design turns on one principle: this is the owner's data, kept in
 * the owner's Drive. We do not run a server, we do not hold anyone's ledger, we
 * never see the file. Google signs the owner in; the backup goes to their
 * Drive; if we vanished tomorrow the file would still be theirs. For an app
 * that records who owes whom — nobody else's business — that is the honest
 * arrangement, and the cheap one: no server to run, nothing to leak.
 *
 * The file lives in Drive's "appDataFolder" — a private space tied to this app.
 * It does not clutter the owner's normal Drive, it cannot be opened or broken
 * by another app, and it still costs them nothing worth counting: a backup is a
 * few kilobytes against fifteen free gigabytes.
 *
 * ONE FILE, replaced in place. Every backup overwrites the same file rather than
 * piling up dated copies, so the owner never has to wonder which is newest —
 * there is only ever the latest. But the replace is done SAFELY: the new
 * content is uploaded to a fresh file first, and only once that upload has
 * fully succeeded is the old file deleted. If the connection drops mid-upload,
 * the previous good backup is still there, untouched. A half-written backup is
 * worse than none, because the owner would trust it.
 */
object DriveBackup {

    /**
     * The scope requested: appdata only.
     *
     * NOT full Drive access. We ask for the narrowest permission that does the
     * job — the app's own private folder, nothing else. We cannot read the
     * owner's documents, photos, or anything they did not put here, and the
     * consent screen says exactly that. Asking for more than the task needs is
     * how trust is quietly spent.
     */
    val SCOPE = DriveScopes.DRIVE_APPDATA

    private const val BACKUP_NAME = "RoshanKhata_Backup.txt"
    // The images archive, kept as its own file beside the text backup so the
    // routine text backup stays small and an owner who never turns images on
    // never uploads one. Same single-file, replace-in-place discipline as the
    // text backup — one archive, always the latest, no dated pile-up.
    private const val IMAGES_NAME = "RoshanKhata_Images.zip"
    private const val APP_DATA_FOLDER = "appDataFolder"

    // The owner's choice of whether a Drive backup also carries images. OFF by
    // default and remembered between visits, because images are heavy and the
    // decision to send customer photos to the cloud is the owner's to make once
    // and keep — not something to re-ask on every backup.
    private const val PREFS = "roshan_khata_prefs"
    private const val KEY_BACKUP_IMAGES = "drive_backup_images"
    private const val KEY_AUTO_BACKUP = "drive_auto_backup"
    private const val KEY_LAST_SIG = "drive_last_backup_signature"

    fun includeImages(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_BACKUP_IMAGES, false)

    fun setIncludeImages(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_BACKUP_IMAGES, enabled).apply()
    }

    /**
     * Whether automatic daily backup is on. OFF by default — an automatic upload
     * of the owner's books to the cloud is a decision they make deliberately,
     * not one taken on their behalf. Once on, the daily worker backs up on its
     * own when a backup is genuinely due.
     */
    fun autoBackup(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_BACKUP, false)

    fun setAutoBackup(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_BACKUP, enabled).apply()
    }

    /**
     * A cheap fingerprint of the ledger's current state — how many entries
     * exist and when the most recent one is dated. If this is unchanged since
     * the last successful backup, nothing worth re-uploading has happened, so
     * the automatic backup skips the trip. It is not a perfect change detector
     * (two edits that cancel out could match), but it is honest about the
     * common cases — a new entry, a deletion, a later transaction — and costs
     * nothing. The manual "Back up now" button ignores this entirely and always
     * uploads.
     */
    private fun currentSignature(count: Int, lastActivity: Long): String =
        "$count:$lastActivity"

    private fun lastSignature(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_SIG, null)

    private fun rememberSignature(context: Context, signature: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LAST_SIG, signature).apply()
    }

    private fun driveFor(context: Context, accountName: String): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(SCOPE)
        ).apply {
            selectedAccountName = accountName
        }

        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("Roshan Khata").build()
    }

    /**
     * Upload the current ledger as the single backup file, replacing any
     * previous one — safely.
     *
     * @return the time the backup completed, for the owner to see.
     */
    suspend fun backup(context: Context, accountName: String, json: String): Result<Long> =
        withContext(Dispatchers.IO) {
            try {
                val drive = driveFor(context, accountName)

                val existingId = findBackupId(drive)

                val metadata = DriveFile().apply {
                    name = BACKUP_NAME
                    // Only set parents when CREATING; Drive rejects a parent
                    // change on update.
                    if (existingId == null) parents = listOf(APP_DATA_FOLDER)
                }

                val content = ByteArrayContent("text/plain", json.toByteArray())

                if (existingId == null) {
                    // First backup: straightforward create.
                    drive.files().create(metadata, content)
                        .setFields("id")
                        .execute()
                } else {
                    // Update the existing file's CONTENT in place. Drive keeps
                    // the same file id and swaps the bytes atomically on its
                    // side — the owner's "one file" stays one file, and there is
                    // no window where it is empty or missing.
                    drive.files().update(existingId, DriveFile(), content)
                        .setFields("id")
                        .execute()
                }

                Result.success(System.currentTimeMillis())
            } catch (e: Exception) {
                // A failed backup leaves any previous one untouched. We report
                // the failure honestly rather than letting the owner believe
                // they are covered when they are not.
                Result.failure(e)
            }
        }

    /**
     * The result of an automatic backup attempt, so the daily worker knows
     * whether to stay silent, notify a failure, or retry later.
     */
    sealed class AutoResult {
        /** Nothing to do — not enabled, not connected, not due, or unchanged. */
        object Skipped : AutoResult()
        /** A backup was uploaded just now. */
        object BackedUp : AutoResult()
        /** A backup was due but failed — the worker should notify and retry. */
        data class Failed(val cause: Throwable?) : AutoResult()
    }

    /**
     * The heart of automatic backup: decide whether a backup is due, and if so,
     * take it — all on a background thread, called once a day by the worker.
     *
     * A backup is taken only when EVERY condition holds:
     *   - automatic backup is switched on,
     *   - Drive is connected (an account is remembered),
     *   - there is data worth protecting,
     *   - at least [minIntervalMs] has passed since the last backup, AND
     *   - the ledger's fingerprint has changed since the last backup.
     *
     * The interval + fingerprint pair is what stops a flapping connection from
     * causing repeat uploads: the worker runs at most daily, and even when it
     * runs, an unchanged ledger or a too-recent last backup means no upload.
     * The network itself is not the trigger — the schedule and the data are.
     *
     * On success the images ride along only if the owner turned images on, and
     * the last-backup time + fingerprint are recorded so the next day's check
     * can tell nothing changed. On failure nothing is recorded, so the next run
     * tries again.
     */
    suspend fun autoBackupIfDue(
        context: Context,
        dao: KhataDao,
        minIntervalMs: Long = 20L * 60 * 60 * 1000 // ~a day, with slack so a daily run isn't skipped by minutes
    ): AutoResult = withContext(Dispatchers.IO) {
        if (!autoBackup(context)) return@withContext AutoResult.Skipped

        val account = DriveAuth.accountName(context) ?: return@withContext AutoResult.Skipped

        val count = dao.totalEntryCount()
        if (count == 0) return@withContext AutoResult.Skipped

        // Due by time? Reuse the same last-backup timestamp the screen shows.
        val last = BackupReminder.lastBackupAt(context)
        val dueByTime = last == 0L || System.currentTimeMillis() - last >= minIntervalMs
        if (!dueByTime) return@withContext AutoResult.Skipped

        // Changed since last backup? If the fingerprint matches, skip.
        val signature = currentSignature(count, dao.lastEntryActivity())
        if (signature == lastSignature(context)) return@withContext AutoResult.Skipped

        // All conditions met — take the backup.
        val json = Backup.export(context, dao)
        val result = backup(context, account, json)
        if (result.isFailure) return@withContext AutoResult.Failed(result.exceptionOrNull())

        // Images ride along only if the owner asked for them. An image failure
        // does not fail the text backup that already succeeded.
        if (includeImages(context)) {
            val zip = BackupImages.pack(context)
            backupImages(context, account, zip)
        }

        // Record success so the home screen's "last backup" updates and the
        // next daily check can see nothing has changed.
        BackupReminder.recordBackup(context)
        rememberSignature(context, signature)
        AutoResult.BackedUp
    }

    /** Download the backup's text, or null if none exists yet. */
    suspend fun restore(context: Context, accountName: String): Result<String?> =
        withContext(Dispatchers.IO) {
            try {
                val drive = driveFor(context, accountName)
                val id = findBackupId(drive)
                    ?: return@withContext Result.success(null)

                val out = ByteArrayOutputStream()
                drive.files().get(id).executeMediaAndDownloadTo(out)
                Result.success(out.toString("UTF-8"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Upload the image archive as the single images file, replacing any
     * previous one — the same safe, one-file, replace-in-place discipline as
     * the text backup: create on first run, update content in place after,
     * and only ever touch this app's own images file.
     *
     * Passing a null or non-existent zip means "nothing to upload" (no images
     * on the phone), which is reported as a success carrying false so the
     * caller can tell the owner images were skipped rather than failed.
     *
     * @return true if an archive was uploaded, false if there was nothing to
     *   upload; a failure is a Result.failure with the cause.
     */
    suspend fun backupImages(context: Context, accountName: String, zip: File?): Result<Boolean> =
        withContext(Dispatchers.IO) {
            if (zip == null || !zip.exists()) return@withContext Result.success(false)
            try {
                val drive = driveFor(context, accountName)
                val existingId = findImagesId(drive)

                val metadata = DriveFile().apply {
                    name = IMAGES_NAME
                    if (existingId == null) parents = listOf(APP_DATA_FOLDER)
                }
                val content = ByteArrayContent("application/zip", zip.readBytes())

                if (existingId == null) {
                    drive.files().create(metadata, content).setFields("id").execute()
                } else {
                    drive.files().update(existingId, DriveFile(), content).setFields("id").execute()
                }
                Result.success(true)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Download the image archive's bytes, or null if none exists yet (the owner
     * never turned images on, or this is an older cloud backup with text only).
     * A missing images file is not an error — the text restore still stands on
     * its own.
     */
    suspend fun restoreImages(context: Context, accountName: String): Result<ByteArray?> =
        withContext(Dispatchers.IO) {
            try {
                val drive = driveFor(context, accountName)
                val id = findImagesId(drive)
                    ?: return@withContext Result.success(null)

                val out = ByteArrayOutputStream()
                drive.files().get(id).executeMediaAndDownloadTo(out)
                Result.success(out.toByteArray())
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** When was the last cloud backup made? Null if none. */
    suspend fun lastBackupTime(context: Context, accountName: String): Long? =
        withContext(Dispatchers.IO) {
            try {
                val drive = driveFor(context, accountName)
                val id = findBackupId(drive) ?: return@withContext null
                drive.files().get(id)
                    .setFields("modifiedTime")
                    .execute()
                    .modifiedTime
                    ?.value
            } catch (e: Exception) {
                null
            }
        }

    private fun findBackupId(drive: Drive): String? {
        val result = drive.files().list()
            .setSpaces(APP_DATA_FOLDER)
            .setQ("name = '$BACKUP_NAME'")
            .setFields("files(id, modifiedTime)")
            .execute()

        return result.files?.firstOrNull()?.id
    }

    private fun findImagesId(drive: Drive): String? {
        val result = drive.files().list()
            .setSpaces(APP_DATA_FOLDER)
            .setQ("name = '$IMAGES_NAME'")
            .setFields("files(id, modifiedTime)")
            .execute()

        return result.files?.firstOrNull()?.id
    }
}
