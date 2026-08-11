package com.innovation313.roshankhata.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Puts a finished file where "Download" actually means something: the
 * phone's public Downloads folder, visible to every file manager, WhatsApp
 * and the Files app.
 *
 * Exists because the report screen's DOWNLOAD button used to only OPEN the
 * document — the owner pressed Download, nothing landed in Downloads, and
 * he was right to call that a broken promise. Same mechanics as
 * [Backup.saveToDownloads], which has been proven on his phone: MediaStore
 * on Android 10+ (no storage permission needed), the public folder
 * directly before that — but generalized to any file and MIME type, so the
 * PDF report and the CSV both go through one door.
 *
 * @return a human-readable location to show the owner, or null on failure.
 */
object Downloads {

    fun save(context: Context, file: File, mime: String): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
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
                "Downloads/${file.name}"
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                )
                dir.mkdirs()
                val out = File(dir, file.name)
                file.copyTo(out, overwrite = true)
                out.absolutePath
            }
        } catch (e: Exception) {
            null
        }
    }
}
