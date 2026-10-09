package com.innovation313.roshankhata.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream

/**
 * Loading the owner's book onto a viewer phone — the first time, and on every
 * refresh after. One path for both, and for both sources (Drive or a file the
 * owner sent), so none of them can skip a check the others make.
 *
 * Order matters, and is the same as a restore's:
 *  1. Parse and validate the WHOLE text first. A bad file changes nothing.
 *  2. Only then switch this phone to its viewer file and replace the copy,
 *     in Room's single all-or-nothing transaction ([KhataDao.restoreAll]).
 *  3. If that fails on a phone that was not a viewer before, the role is
 *     turned off again and the half-made file removed — the phone is back
 *     exactly as it was, its own book untouched throughout.
 *  4. Photos last, and only once the book itself is in: the pictures held
 *     from the previous copy are removed (they are filed by customer number,
 *     and would otherwise sit on the wrong customer), then this copy's own
 *     are unpacked — if it brought any. A photo that fails to unpack never
 *     fails the load: the balances are what a helper came for.
 */
object ViewerSync {

    sealed class Outcome {
        object Loaded : Outcome()
        /** The book loaded, but the photos sent with it could not be unpacked. */
        object LoadedWithoutPhotos : Outcome()
        /** The text was not a Roshan Khata backup, or was from a newer app. */
        data class Rejected(val reason: String) : Outcome()
        data class Failed(val cause: Throwable) : Outcome()
    }

    suspend fun load(
        context: Context,
        text: String,
        source: String,
        account: String?,
        driveBusinessId: Long,
        images: (() -> InputStream?)? = null
    ): Outcome = withContext(Dispatchers.IO) {
        val ctx = context.applicationContext
        val (result, data) = Backup.parseText(text)
        if (result is Backup.ImportResult.Failed || data == null) {
            val reason = (result as? Backup.ImportResult.Failed)?.reason(ctx) ?: ""
            return@withContext Outcome.Rejected(reason)
        }

        val wasViewer = ViewerMode.isOn(ctx)
        try {
            ViewerMode.switchOn(ctx)
            // The raw DAO, deliberately: this is the one write a viewer's copy
            // ever receives, and it replaces the copy whole.
            val dao = KhataDatabase.get(ctx).ledgerDao()
            Backup.restore(ctx, dao, data)
            ViewerMode.recordCopy(
                ctx,
                source = source,
                account = account,
                driveBusinessId = driveBusinessId,
                shopName = data.businessName ?: data.businessProfile?.businessName,
                dataAt = exportedAt(text)
            )
            val photosOk = try {
                BackupImages.clearViewerImages(ctx)
                val stream = images?.invoke()
                if (stream != null) BackupImages.restore(ctx, dao, stream)
                true
            } catch (e: Exception) {
                android.util.Log.e("Viewer", "photos failed", e)
                false
            }
            PartyPhoto.dropCaches()
            if (photosOk) Outcome.Loaded else Outcome.LoadedWithoutPhotos
        } catch (e: Exception) {
            if (!wasViewer) ViewerMode.leave(ctx)
            Outcome.Failed(e)
        }
    }

    /** When the owner made this backup, from the file itself. 0 if absent. */
    internal fun exportedAt(text: String): Long = try {
        JSONObject(text).optLong("exportedAt", 0L)
    } catch (e: Exception) {
        0L
    }
}
