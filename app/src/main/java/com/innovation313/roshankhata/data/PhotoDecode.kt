package com.innovation313.roshankhata.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

/**
 * Reading a photograph without opening it at full size first.
 *
 * Both places this app accepts a picture — a customer's face and a bill —
 * ended up small on disk, and both got there the expensive way: decode the
 * whole thing, then shrink it. A phone camera today produces forty or fifty
 * megapixels, and Android holds a decoded bitmap at four bytes a pixel, so
 * that first step alone asks for two hundred megabytes of memory to produce a
 * four hundred pixel thumbnail. On a mid-range phone it does not get it, and
 * the app dies where the shopkeeper was only trying to attach a bill.
 *
 * The decoder can do the shrinking itself while it reads, in powers of two,
 * if it is told the size first. So the file is opened twice: once for its
 * dimensions alone, which costs nothing and allocates nothing, and once for
 * the pixels at a sampling that is still comfortably larger than what the
 * caller is about to scale to.
 *
 * Never below the target. [sampleSize] only halves while the half would still
 * be at least the edge asked for, so the final scaling has as much detail to
 * work from as it ever did. Nothing about the saved image changes — this is
 * about what it costs to get there.
 */
object PhotoDecode {

    /**
     * @param edge the size the caller will end up at.
     * @param keepShortEdge true when the caller crops to a square, since it is
     *        the short side that survives that; false when it fits the long
     *        side into [edge].
     */
    fun read(context: Context, source: Uri, edge: Int, keepShortEdge: Boolean): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }

        try {
            // Only the stream's absence is a failure here. decodeStream ALWAYS
            // returns null under inJustDecodeBounds — that is the whole point
            // of the flag, it fills in the options and allocates nothing — so
            // the elvis has to sit on openInputStream and nowhere else.
            //
            // It sat on the whole expression once, which meant every photograph
            // in the app failed to save with "that image could not be saved".
            // The build was green and the tests passed: they cover the
            // arithmetic below, and nothing in a JUnit run touches
            // BitmapFactory at all.
            val stream = context.contentResolver.openInputStream(source) ?: return null
            stream.use { BitmapFactory.decodeStream(it, null, bounds) }
        } catch (e: Exception) {
            return null
        }

        // Whether the first pass worked is read from the options, never from
        // its return value.
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, edge, keepShortEdge)
        }

        val decoded = try {
            context.contentResolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } catch (e: Exception) {
            null
        } ?: return null

        return upright(decoded, rotationOf(context, source))
    }

    /**
     * The camera's word for which way is up.
     *
     * A phone held upright does not rotate the sensor; it stores the pixels
     * sideways and writes a flag saying so. Every gallery honours the flag,
     * so the photo LOOKS right everywhere — until it is decoded, saved, and
     * shown from the saved copy, at which point the flag is gone and a
     * customer's face lies on its side. Read after the sampled decode and
     * applied to the small bitmap, where a rotation costs a few megabytes
     * instead of two hundred.
     */
    private fun rotationOf(context: Context, source: Uri): Int = try {
        context.contentResolver.openInputStream(source)?.use {
            when (ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    } catch (e: Exception) {
        // No orientation is not an error; it is a photo that is already up.
        0
    }

    private fun upright(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    /**
     * How many times the decoder may halve the picture and still hand back
     * something at least [edge] across. Always a power of two — anything else
     * is rounded down to one by Android, silently.
     */
    fun sampleSize(width: Int, height: Int, edge: Int, keepShortEdge: Boolean): Int {
        if (edge <= 0) return 1
        val measured = if (keepShortEdge) minOf(width, height) else maxOf(width, height)

        var sample = 1
        while (measured / (sample * 2) >= edge) sample *= 2
        return sample
    }
}
