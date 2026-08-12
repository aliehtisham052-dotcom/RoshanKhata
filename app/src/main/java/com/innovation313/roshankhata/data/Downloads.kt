package com.innovation313.roshankhata.data

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.innovation313.roshankhata.R
import java.io.File

/**
 * Puts a finished file where "Download" actually means something: the
 * phone's public Downloads folder, visible to every file manager, WhatsApp
 * and the Files app — and then SAYS SO the way every download on the phone
 * says so, with a notification that opens the file on tap. The owner's
 * expectation, stated in his own words: when a file downloads, its
 * notification should appear. A toast alone vanishes in two seconds and
 * leaves no way back to the file.
 *
 * Two roads, one behaviour:
 * - Android 10+: MediaStore write (no storage permission), then our own
 *   notification on a dedicated Downloads channel, tap = ACTION_VIEW on
 *   the saved uri with read permission granted.
 * - Before 10: the classic DownloadManager.addCompletedDownload, which IS
 *   the system's own download notification — nothing to build by hand.
 *
 * Same MediaStore mechanics as [Backup.saveToDownloads], already proven on
 * the owner's phone, generalized to any file and MIME type.
 *
 * @return a human-readable location to show the owner, or null on failure.
 */
object Downloads {

    private const val CHANNEL_ID = "downloads"

    /**
     * Everything the app downloads lands in its own room: a RoshanKhata
     * folder inside Downloads — the owner's ask, and the right one: a
     * shopkeeper's reports should not be loose grains scattered through
     * every other download on the phone. On Android 10+ the system creates
     * the folder itself from RELATIVE_PATH; before that, mkdirs does.
     */
    private const val FOLDER = "RoshanKhata"

    fun save(context: Context, file: File, mime: String): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER
                    )
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                ) ?: return null
                resolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                } ?: return null
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)

                notifyDone(context, file.name, uri, mime)
                "Downloads/$FOLDER/${file.name}"
            } else {
                @Suppress("DEPRECATION")
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS
                    ),
                    FOLDER
                )
                dir.mkdirs()
                val out = File(dir, file.name)
                file.copyTo(out, overwrite = true)

                // The system's own download notification, complete with its
                // own open-on-tap handling. Deprecated from Android 10, which
                // is exactly why it is only used before Android 10.
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager)
                    .addCompletedDownload(
                        out.name,
                        context.getString(R.string.report_download_tap_open),
                        true,
                        mime,
                        out.absolutePath,
                        out.length(),
                        true
                    )
                out.absolutePath
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * The notification every download on the phone gets. Tap opens the file
     * in whatever the owner reads PDFs or spreadsheets with; read permission
     * travels with the intent. If notifications are switched off for the
     * app, this silently does nothing — the toast in the report screen has
     * already named the location, so the file is never lost either way.
     */
    private fun notifyDone(context: Context, name: String, uri: Uri, mime: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notif_downloads_channel),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }

        val open = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val pending = PendingIntent.getActivity(
            context,
            (System.currentTimeMillis() % 100000).toInt() + 2000,
            Intent.createChooser(open, name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(name)
            .setContentText(context.getString(R.string.report_download_tap_open))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context)
                .notify((System.currentTimeMillis() % 100000).toInt() + 2000, notification)
        } catch (_: SecurityException) {
            // Notifications denied; the toast already named the location.
        }
    }
}
