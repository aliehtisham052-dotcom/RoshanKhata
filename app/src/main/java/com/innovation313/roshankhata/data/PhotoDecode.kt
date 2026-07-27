package com.innovation313.roshankhata.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

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
            context.contentResolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            } ?: return null
        } catch (e: Exception) {
            return null
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, edge, keepShortEdge)
        }

        return try {
            context.contentResolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } catch (e: Exception) {
            null
        }
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
