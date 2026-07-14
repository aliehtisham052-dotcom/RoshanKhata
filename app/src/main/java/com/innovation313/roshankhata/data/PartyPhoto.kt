package com.innovation313.roshankhata.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * A party's photo.
 *
 * Stored in the app's private files directory — not the gallery, not the cloud.
 * It never leaves the phone: it is deliberately kept OFF statements and out of
 * every share, because a statement gets forwarded and a customer did not
 * consent to their face travelling with it. The photo exists for one purpose
 * only: to help the owner recognise who they are looking at in a long list.
 */
object PartyPhoto {

    /** Square, and small. This is a recognition thumbnail, not a portrait. */
    private const val EDGE = 400

    private fun dir(context: Context): File =
        File(context.filesDir, "party_photos").apply { mkdirs() }

    fun file(context: Context, partyId: Long): File =
        File(dir(context), "party_$partyId.jpg")

    fun exists(context: Context, partyId: Long): Boolean =
        file(context, partyId).exists()

    fun load(context: Context, partyId: Long): Bitmap? {
        val f = file(context, partyId)
        if (!f.exists()) return null
        return try {
            BitmapFactory.decodeFile(f.absolutePath)
        } catch (e: Exception) {
            null
        }
    }

    /** @return the saved path, or null if it could not be written. */
    fun save(context: Context, partyId: Long, source: Uri): String? {
        return try {
            val input = context.contentResolver.openInputStream(source) ?: return null
            val original = input.use { BitmapFactory.decodeStream(it) } ?: return null

            val square = cropToSquare(original)
            val scaled = if (square.width > EDGE) {
                Bitmap.createScaledBitmap(square, EDGE, EDGE, true)
            } else {
                square
            }

            val target = file(context, partyId)
            FileOutputStream(target).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }

            if (scaled !== square) square.recycle()
            if (square !== original) original.recycle()

            target.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun remove(context: Context, partyId: Long) {
        file(context, partyId).delete()
    }

    /** Centre-crop, so a portrait photo does not end up squashed in a round avatar. */
    private fun cropToSquare(src: Bitmap): Bitmap {
        val edge = minOf(src.width, src.height)
        if (src.width == src.height) return src

        val x = (src.width - edge) / 2
        val y = (src.height - edge) / 2
        return Bitmap.createBitmap(src, x, y, edge, edge)
    }
}
