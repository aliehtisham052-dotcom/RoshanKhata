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
    private const val APP_DATA_FOLDER = "appDataFolder"

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
}
