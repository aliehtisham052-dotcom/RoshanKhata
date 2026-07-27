package com.innovation313.roshankhata.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Photographs of bills and slips, kept beside the ledger entry they belong to.
 *
 * They live in the app's own private files rather than the shared gallery, so
 * a bill does not turn up in the phone's photo roll for anyone flicking
 * through it — and it is deliberately left out of statements and shares, the
 * same way a customer's photo is. A bill often carries another buyer's name or
 * a rate the owner would rather not forward.
 */
object BillPhoto {

    private const val DIR = "bills"

    /**
     * Copy the picked image into private storage, scaled down, and return the
     * path to keep on the entry. Null if it could not be read.
     *
     * Scaled because a modern phone camera produces several megabytes per
     * shot, and a shopkeeper photographing every bill would fill their storage
     * within a season. A bill only has to be legible.
     */
    fun save(context: Context, uri: Uri): String? = try {
        // Sampled on the way in — a bill photographed at fifty megapixels
        // used to be decoded whole before being shrunk, which is where the
        // memory went. scaleToFit below fits the long side, so that is the
        // side the sampling must not go under.
        val source = PhotoDecode.read(context, uri, MAX_EDGE, keepShortEdge = false)

        if (source == null) {
            null
        } else {
            val scaled = scaleToFit(source, MAX_EDGE)
            val dir = File(context.filesDir, DIR).apply { mkdirs() }
            val file = File(dir, "bill_${System.currentTimeMillis()}.jpg")

            FileOutputStream(file).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            }
            if (scaled !== source) scaled.recycle()
            source.recycle()

            file.absolutePath
        }
    } catch (e: Exception) {
        null
    }

    /** The stored photo, or null if it is missing — a file can be cleared. */
    fun load(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists()) return null
        return try {
            BitmapFactory.decodeFile(path)
        } catch (e: Exception) {
            null
        }
    }

    /** Remove a photo's file. Safe to call when it has already gone. */
    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        try {
            File(path).delete()
        } catch (e: Exception) {
            // Nothing to do about it, and nothing worth interrupting the owner
            // over: the entry no longer points here either way.
        }
    }

    private fun scaleToFit(source: Bitmap, maxEdge: Int): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= maxEdge) return source

        val ratio = maxEdge.toFloat() / longest
        return Bitmap.createScaledBitmap(
            source,
            (source.width * ratio).toInt().coerceAtLeast(1),
            (source.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }

    /**
     * Long edge in pixels. Enough to read a handwritten bill, far short of a
     * full frame.
     *
     * Down from 1600. Bills are the one photo that never stops arriving —
     * a customer's face is saved once, but a bill is saved per entry, so this
     * is where a season's storage actually goes. At 1280 a bill's figures and
     * signature are still plainly readable on the screen it is read on, and
     * the file is roughly half what it was.
     */
    private const val MAX_EDGE = 1280

    /**
     * Down from 80. On a photograph of paper — flat, high contrast, no skin
     * tones or gradients for the artefacts to show up in — the difference is
     * not visible, and it takes another slice off every bill kept.
     */
    private const val QUALITY = 75
}
