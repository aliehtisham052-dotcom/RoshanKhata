package com.innovation313.roshankhata.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import com.innovation313.roshankhata.data.BackupCodec.BusinessProfileData
import com.innovation313.roshankhata.data.BackupCodec.ImportResult
import com.innovation313.roshankhata.data.BackupCodec.ParsedBackup
import java.util.Locale

/**
 * Full backup and restore.
 *
 * Plain JSON, deliberately. Not an opaque binary blob and not the raw SQLite
 * file: a shopkeeper's entire book of debts should be readable and rescuable
 * with nothing more than a text editor, even years from now, even if this app
 * no longer exists. Data the owner cannot recover without our cooperation is
 * not really theirs.
 *
 * The file carries a schema version so a future release can migrate an old
 * backup rather than reject it.
 */
object Backup {

    /** Raised with the format; see [BackupCodec.FORMAT_VERSION]. */
    const val FORMAT_VERSION = BackupCodec.FORMAT_VERSION

    private val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.ENGLISH)

    /**
     * Named .txt, not .json.
     *
     * WhatsApp — which is how a Pakistani shopkeeper actually moves a file
     * between phones — refuses to send an extension it does not recognise. The
     * owner would watch the send appear to work and then find nothing at the
     * other end.
     *
     * The contents are unchanged: it is still JSON, and the app still reads it
     * back exactly the same way. Only the label differs, because the label is
     * what was standing between the owner and a backup that actually travelled.
     */
    fun suggestedFileName(): String =
        "RoshanKhata_Backup_${stamp.format(Date())}.txt"

    // ---------- Export ----------

    // Takes Context now, because the Business Profile (shop name, bank details,
    // STRN, terms) lives in SharedPreferences, not in Room — so it cannot be
    // read through the DAO like every other table. Its images (QR, signature,
    // stamp) are deliberately NOT here: those are the separate opt-in image
    // backup, kept out of the routine text file so it stays small.
    suspend fun export(context: Context, dao: KhataDao): String {
        val w = java.io.StringWriter()
        exportTo(context, dao, w)
        return w.toString()
    }

    /**
     * The backup, written straight to [out] by the shared format
     * ([BackupCodec.exportTo]), with this shop's name and Profile.
     */
    suspend fun exportTo(context: Context, dao: KhataDao, out: java.io.Writer) =
        BackupCodec.exportTo(dao, BusinessProfile.businessName(context), businessProfileToJson(context), out)

    /** The backup to a file, buffered, for the paths that hand a file on (Downloads, Drive, the helper's copy). */
    suspend fun exportToFile(context: Context, dao: KhataDao, file: File): File {
        file.parentFile?.mkdirs()
        java.io.BufferedWriter(java.io.OutputStreamWriter(java.io.FileOutputStream(file), Charsets.UTF_8), 64 * 1024)
            .use { exportTo(context, dao, it) }
        return file
    }

    /**
     * Write the backup where the owner can actually find it again: Downloads.
     *
     * It used to go to the cache directory, which was a mistake with real
     * consequences. Android empties the cache whenever it feels the need for
     * space — so the one file standing between a shopkeeper and the loss of
     * their entire ledger could vanish without anyone touching it. A backup
     * that quietly disappears is worse than no backup, because the owner
     * believes they are covered.
     *
     * Downloads survives. It is visible in every file manager, WhatsApp,
     * Drive, and the phone's own Files app. And on Android 10+ this needs no
     * storage permission at all — MediaStore hands the app a place to write
     * without handing it the run of the user's storage.
     *
     * @return a human-readable location to show the owner, or null on failure.
     */
    fun saveToDownloads(context: Context, json: String): String? {
        val name = suggestedFileName()

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    // Declared as plain text, not application/json. Android and
                    // most apps treat an unknown MIME type as a file to be
                    // hidden — which is exactly why the owner could not see
                    // their own backup afterwards. It IS text; saying so makes
                    // it visible everywhere.
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values
                ) ?: return null

                resolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray())
                } ?: return null

                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)

                "Downloads/$name"
            } else {
                // Pre-Android 10: write to the public Downloads folder directly.
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                )
                dir.mkdirs()
                val file = File(dir, name)
                file.writeText(json)
                file.absolutePath
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * A second copy inside the app's own storage.
     *
     * Kept so the owner is never left with nothing: even if the Downloads copy
     * is deleted, moved, or lost with the phone's file manager, the app can
     * still offer its own most recent backup. This is filesDir, not cacheDir —
     * the system does not clear it behind the owner's back.
     */
    /** The streamed backup's cache file (P6): the one copy every other path copies from. */
    suspend fun exportToCache(context: Context, dao: KhataDao): File {
        val dir = File(context.cacheDir, "backups").apply { mkdirs() }
        return exportToFile(context, dao, File(dir, suggestedFileName()))
    }

    /** [saveToDownloads], from a file — streamed, never held as one string. */
    fun saveToDownloads(context: Context, source: File): String? {
        val name = suggestedFileName()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
                resolver.openOutputStream(uri)?.use { out ->
                    source.inputStream().buffered().use { it.copyTo(out) }
                } ?: return null
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                "Downloads/$name"
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                val file = File(dir, name)
                source.copyTo(file, overwrite = true)
                file.absolutePath
            }
        } catch (e: Exception) {
            null
        }
    }

    /** [writeInternalCopy], from a file. */
    fun writeInternalCopy(context: Context, source: File): File? {
        return try {
            val dir = File(context.filesDir, "backups").apply { mkdirs() }
            val file = File(dir, suggestedFileName())
            source.copyTo(file, overwrite = true)
            dir.listFiles()
                ?.sortedByDescending { it.lastModified() }
                ?.drop(5)
                ?.forEach { it.delete() }
            file
        } catch (e: Exception) {
            null
        }
    }

    fun writeInternalCopy(context: Context, json: String): File? {
        return try {
            val dir = File(context.filesDir, "backups").apply { mkdirs() }
            val file = File(dir, suggestedFileName())
            file.writeText(json)

            // Keep the last few, then stop. An unbounded pile of backups would
            // quietly eat the phone's storage.
            dir.listFiles()
                ?.sortedByDescending { it.lastModified() }
                ?.drop(5)
                ?.forEach { it.delete() }

            file
        } catch (e: Exception) {
            null
        }
    }

    /** The app's own saved backups, newest first. */
    fun internalBackups(context: Context): List<File> {
        val dir = File(context.filesDir, "backups")
        if (!dir.exists()) return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    /** Shareable copy, for sending to Drive or another phone. */
    fun writeToCache(context: Context, json: String): File? {
        return try {
            val dir = File(context.cacheDir, "backups").apply { mkdirs() }
            val file = File(dir, suggestedFileName())
            file.writeText(json)
            file
        } catch (e: Exception) {
            null
        }
    }

    /** Read a backup the app saved itself. */
    fun parseFile(file: File): Pair<ImportResult, ParsedBackup?> = BackupCodec.parseFile(file)

    /**
     * Reads and validates a backup WITHOUT touching the database.
     *
     * The parse happens first, in full, so a corrupt or unrelated file is
     * caught before anything is wiped. Wiping first and discovering the file
     * was rubbish afterwards would destroy the owner's books to import nothing.
     */
    fun parse(context: Context, uri: Uri): Pair<ImportResult, ParsedBackup?> {
        // Streamed straight off the Uri (P7, 7 Oct): a backup the size of a
        // wholesaler's book is never held as one string.
        return try {
            context.contentResolver.openInputStream(uri)
                ?.buffered(64 * 1024)
                ?.use { parseStream(it) }
        } catch (e: Exception) {
            null
        } ?: (ImportResult.Failed(ImportResult.Why.UNREADABLE) to null)
    }

    /** The shop's name out of a backup, without reading its ledger ([BackupCodec.businessNameOf]). */
    fun businessNameOf(json: String): String? = BackupCodec.businessNameOf(json)

    fun parseText(text: String): Pair<ImportResult, ParsedBackup?> = BackupCodec.parseText(text)

    fun parseStream(input: java.io.InputStream): Pair<ImportResult, ParsedBackup?> = BackupCodec.parseStream(input)

    /**
     * Replaces everything. Only called after the user has confirmed.
     *
     * Takes Context now for the Business Profile, which lives in
     * SharedPreferences and so cannot ride inside the Room transaction. The
     * ledger is restored first, atomically; the profile is written after, and
     * only when the file actually carried one. If a file has no profile (an
     * old backup, or one taken before the owner set any details), the current
     * profile is LEFT ALONE rather than blanked — restoring "nothing" over a
     * shop's real name would be a silent loss, not a restore.
     */
    suspend fun restore(context: Context, dao: KhataDao, data: ParsedBackup) {
        // One transaction, all-or-nothing. If any step fails, the whole thing
        // rolls back and the existing ledger is left untouched — rather than the
        // old behaviour, where a failure partway through wiped data and restored
        // nothing, losing the customer the owner was trying to bring back.
        // The pictures kept by row id follow their rows to the copy's ids (ImageReattach).
        val before = ImageReattach.before(dao)
        BackupCodec.restoreLedger(dao, data)
        before?.let { ImageReattach.after(context, dao, it) }

        data.businessProfile?.let { restoreBusinessProfile(context, it) }
    }

    // ---------- Business Profile (SharedPreferences, not Room) ----------

    private fun businessProfileToJson(context: Context): JSONObject =
        BackupCodec.profileToJson(AndroidShopSettings(context))

    private fun restoreBusinessProfile(context: Context, p: BusinessProfileData) =
        BackupCodec.restoreProfile(AndroidShopSettings(context), p)
}
