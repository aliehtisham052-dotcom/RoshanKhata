package com.innovation313.roshankhata.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * The owner's own details: business name, and the payment QR they hand to
 * customers.
 *
 * The QR is stored as a plain image in the app's private files directory.
 * Roshan Khata does not read, decode, or interpret it — it is simply a picture
 * the owner attaches to statements. We deliberately do not try to parse the
 * account number out of it: getting that wrong would mean money going to the
 * wrong place, and there is nothing we could do with it that the customer's
 * own wallet app does not already do better by scanning.
 */
object BusinessProfile {

    private const val PREFS = "roshan_khata_prefs"
    private const val KEY_BUSINESS_NAME = "business_name"
    private const val KEY_QR_SAVED = "payment_qr_saved"
    private const val QR_FILE = "payment_qr.png"

    /** Long edge of the stored QR. Big enough to scan, small enough to attach. */
    private const val MAX_EDGE = 1000

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------- Business name ----------

    fun businessName(context: Context): String? =
        prefs(context).getString(KEY_BUSINESS_NAME, null)?.takeIf { it.isNotBlank() }

    fun setBusinessName(context: Context, name: String?) {
        prefs(context).edit()
            .putString(KEY_BUSINESS_NAME, name?.trim().orEmpty())
            .apply()
    }

    // ---------- Payment QR ----------

    fun qrFile(context: Context): File =
        File(context.filesDir, QR_FILE)

    fun hasQr(context: Context): Boolean =
        prefs(context).getBoolean(KEY_QR_SAVED, false) && qrFile(context).exists()

    fun loadQr(context: Context): Bitmap? {
        if (!hasQr(context)) return null
        return try {
            BitmapFactory.decodeFile(qrFile(context).absolutePath)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Copies the chosen image into our own storage, downscaled.
     *
     * @return true if it was saved. A QR that fails to save must not be
     *         silently marked as present — the owner would then send
     *         statements believing a payment code was attached when none was.
     */
    fun saveQr(context: Context, source: Uri): Boolean {
        return try {
            val input = context.contentResolver.openInputStream(source) ?: return false

            val original = input.use { BitmapFactory.decodeStream(it) } ?: return false

            val scaled = downscale(original)

            FileOutputStream(qrFile(context)).use { out ->
                // PNG, not JPEG: JPEG artefacts around the sharp black/white
                // edges of a QR can stop a scanner reading it at all.
                scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            if (scaled !== original) original.recycle()

            prefs(context).edit().putBoolean(KEY_QR_SAVED, true).apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun removeQr(context: Context) {
        qrFile(context).delete()
        prefs(context).edit().putBoolean(KEY_QR_SAVED, false).apply()
    }

    private fun downscale(src: Bitmap): Bitmap {
        val longEdge = maxOf(src.width, src.height)
        if (longEdge <= MAX_EDGE) return src

        val ratio = MAX_EDGE.toFloat() / longEdge
        return Bitmap.createScaledBitmap(
            src,
            (src.width * ratio).toInt(),
            (src.height * ratio).toInt(),
            true
        )
    }
}
