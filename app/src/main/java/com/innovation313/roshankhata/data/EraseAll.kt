package com.innovation313.roshankhata.data

import android.content.Context
import java.io.File

/**
 * Removes EVERYTHING the app holds on this phone: every business's database,
 * every photo and image, every preference, every cached document and daily
 * copy. The answer to Play's Data Safety question about data deletion — and,
 * more importantly, to the owner who sells the phone, hands the shop over,
 * or simply wants to be certain.
 *
 * The whole promise of Roshan Khata is that the data lives on the phone and
 * nowhere else (Drive backups excepted, and those are the OWNER'S Drive) —
 * so a complete erase is genuinely complete: after this runs, the app holds
 * what a fresh install holds. Nothing is kept back, no flag survives to say
 * the app was ever used.
 *
 * Deliberately NOT touched: backups on the owner's Google Drive. Those are
 * theirs, in their account, and may be the only copy of a book they still
 * need — deleting them here would turn "erase this phone" into "destroy my
 * records", which is a different and far more dangerous instruction. The
 * confirmation dialog says so in plain words.
 */
object EraseAll {

    /**
     * Must be called off the main thread. The caller restarts the app
     * immediately afterwards — every screen above this one was showing data
     * that no longer exists.
     */
    fun wipe(context: Context) {
        // Close the open ledger first: deleting a database out from under a
        // live connection is the one order of operations that can corrupt
        // instead of remove.
        KhataDatabase.closeActive()
        PartyPhoto.dropCaches()

        // Every database file, through the framework's own remover so the
        // -wal/-shm companions go with each one. This catches every
        // business's ledger and every daily-copy snapshot in one sweep,
        // including businesses the registry may have forgotten.
        context.databaseList()?.forEach { context.deleteDatabase(it) }

        // Files: photos, stamps, QRs, signatures, snapshots — everything.
        context.filesDir?.listFiles()?.forEach { it.deleteRecursively() }

        // Cached documents: generated statements, invoices, reports.
        context.cacheDir?.listFiles()?.forEach { it.deleteRecursively() }

        // Preferences, through deleteSharedPreferences rather than deleting
        // the files by hand: the framework clears its own in-memory copy at
        // the same time, so a cached instance cannot quietly write the old
        // values back after the wipe.
        File(context.dataDir, "shared_prefs").listFiles()?.forEach { f ->
            context.deleteSharedPreferences(f.name.removeSuffix(".xml"))
        }
    }
}
