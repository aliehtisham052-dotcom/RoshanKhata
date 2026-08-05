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

    /**
     * Decoded thumbnails, kept warm.
     *
     * The customer list binds a row every time it scrolls past, and every
     * bind was a disk stat plus a JPEG decode on the main thread — per row,
     * per pass, over a book of eleven hundred. The photos never change
     * between binds; only save() and remove() can change them, and both are
     * right here. So each thumbnail is decoded once and served from memory
     * after that, and a customer known to have no photo is answered without
     * touching the disk at all.
     *
     * Twelve megabytes holds roughly twenty thumbnails at this size — more
     * than a screen shows — and LruCache quietly lets the oldest go.
     */
    private val cache = object : android.util.LruCache<Long, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: Long, value: Bitmap) = value.byteCount
    }

    /** What the disk holds, learned once per customer instead of per bind. */
    private val onDisk = java.util.concurrent.ConcurrentHashMap<Long, Boolean>()

    /**
     * Cleared on every business switch, by [Businesses.switchTo].
     *
     * Both caches are keyed by partyId alone, and party ids are NOT unique
     * across businesses — each database file numbers its own parties from 1.
     * A warm cache carried across a switch would show Shop A's customer's
     * face on Shop B's same-numbered customer. Dropping the caches is what
     * makes the id collision harmless: after a switch, every answer comes
     * fresh from the right shop's own folder.
     */
    fun dropCaches() {
        cache.evictAll()
        onDisk.clear()
    }

    /** The active business's photo folder — for the image backup to read and restore into. */
    fun folder(context: Context): File = dir(context)

    private fun dir(context: Context): File {
        // Business 1 keeps the folder its photos are already in. Any other
        // business gets its own, for the same id-collision reason as above:
        // party_5.jpg exists once per shop, so it must live once per shop.
        val id = Businesses.active(context).id
        val name = if (id == 1L) "party_photos" else "party_photos_b$id"
        return File(context.filesDir, name).apply { mkdirs() }
    }

    fun file(context: Context, partyId: Long): File =
        File(dir(context), "party_$partyId.jpg")

    fun exists(context: Context, partyId: Long): Boolean {
        onDisk[partyId]?.let { return it }
        return file(context, partyId).exists().also { onDisk[partyId] = it }
    }

    /**
     * The thumbnail if it is already decoded and in memory, else null.
     *
     * Never touches the disk, so it is safe to call on the main thread while
     * a screen is being built. A caller that gets null here can fall back to
     * [load] on a background thread; a caller that gets a bitmap can show it
     * on this very frame, with no flicker from initials to photo.
     */
    fun cached(partyId: Long): Bitmap? = cache.get(partyId)

    /**
     * True when this customer is already known to have no photo — learned
     * from an earlier look, so answering costs nothing and needs no thread.
     */
    fun knownAbsent(partyId: Long): Boolean = onDisk[partyId] == false

    /** The photo if it is already in memory — never touches the disk. */
    fun cached(partyId: Long): Bitmap? = cache.get(partyId)

    fun load(context: Context, partyId: Long): Bitmap? {
        cache.get(partyId)?.let { return it }
        if (onDisk[partyId] == false) return null

        val f = file(context, partyId)
        if (!f.exists()) {
            onDisk[partyId] = false
            return null
        }
        return try {
            BitmapFactory.decodeFile(f.absolutePath)?.also {
                cache.put(partyId, it)
                onDisk[partyId] = true
            }
        } catch (e: Exception) {
            null
        }
    }

    /** @return the saved path, or null if it could not be written. */
    fun save(context: Context, partyId: Long, source: Uri): String? {
        return try {
            // Sampled on the way in. The square crop below keeps the short
            // side, so that is the side that has to survive the sampling.
            val original = PhotoDecode.read(context, source, EDGE, keepShortEdge = true)
                ?: return null

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

            // The saved bitmap is exactly what load() would decode back, so
            // the cache takes it now and the next bind costs nothing.
            cache.put(partyId, scaled)
            onDisk[partyId] = true

            target.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun remove(context: Context, partyId: Long) {
        file(context, partyId).delete()
        cache.remove(partyId)
        onDisk[partyId] = false
    }

    /**
     * Called once, right after a duplicate-customer merge has moved
     * everything else. If [survivorId] already has a photo, [loserId]'s is
     * simply left on disk rather than overwritten — the owner never chose
     * which face to keep, so this never chooses for them. A future recycle-
     * bin purge of the merged-away party is free to clean it up; nothing
     * reads it again once the party it belonged to is gone.
     */
    fun transferOnMerge(context: Context, loserId: Long, survivorId: Long) {
        if (exists(context, survivorId)) return
        val from = file(context, loserId)
        if (!from.exists()) return
        val to = file(context, survivorId)
        if (from.renameTo(to)) {
            cache.remove(loserId)
            onDisk[loserId] = false
            cache.remove(survivorId)
            onDisk[survivorId] = true
        }
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
