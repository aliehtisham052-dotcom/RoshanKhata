package com.innovation313.roshankhata.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/*
 * Opening the ledger on Android (KMP Phase 3, 18 Oct). The database class
 * itself (tables, DAO) is in the shared module; how Android opens it - the
 * active shop's file, the 31 migrations, the unit clean-up on open, the
 * read-only copy on a helper's phone - stays here. Called exactly as before:
 * KhataDatabase.get(context), openOther(...), closeActive().
 */

/** The one open instance, and which shop's file it was opened for. Guarded by its own lock. */
private object KhataDatabaseHolder {
    @Volatile
    var instance: KhataDatabase? = null

    @Volatile
    var instanceFile: String? = null
}

fun KhataDatabase.Companion.get(context: Context): KhataDatabase {
    // The check-and-balance for multi-business lives on this line and
    // nowhere else: every caller in the app reaches the database
    // through get(), and get() will not return an instance unless it
    // was opened for the file the ACTIVE business owns. A stale
    // instance — one still pointing at another shop's ledger after a
    // switch — cannot leave this method.
    val file = Businesses.activeDbFile(context)
    KhataDatabaseHolder.instance?.let { if (KhataDatabaseHolder.instanceFile == file) return it }
    return synchronized(KhataDatabaseHolder) {
        val current = KhataDatabaseHolder.instance
        if (current != null && KhataDatabaseHolder.instanceFile == file) current
        else {
            // A different business is active than the one this
            // instance was opened for (or nothing is open yet).
            // Close before opening: two live handles would let a
            // write race a switch.
            current?.close()
            Room.databaseBuilder(
                context.applicationContext,
                KhataDatabase::class.java,
                file
            )
            // Real migrations, not a destructive rebuild.
            //
            // This database holds a shopkeeper's entire book of debts.
            // An app update must never be the thing that destroys it —
            // so every schema step from v1 onwards is written out and
            // registered here.
            //
            // Note what is deliberately NOT called:
            // fallbackToDestructiveMigration(). If a future version bump
            // ever arrives without its migration, the app will crash on
            // open rather than silently wipe the ledger. A crash is
            // reported and fixed; a silent wipe is discovered by a
            // shopkeeper who has lost a year of records and has no idea
            // why. Loud failure is the kinder failure.
            .addMigrations(*ALL_MIGRATIONS)
            // Units typed by hand as one of the app's own words for
            // a unit become that unit's saved key (see UnitWords).
            // Data only, no schema: a few UPDATEs that match nothing
            // in a book already in order, and can never stop it
            // opening.
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    UnitWords.normalizeStored(db)
                }
            })
            .build()
            .also {
                if (file == ViewerMode.VIEWER_FILE) it.viewer = ViewerDao(it.ledgerDao())
                KhataDatabaseHolder.instance = it
                KhataDatabaseHolder.instanceFile = file
            }
        }
    }
}

/**
 * A SEPARATE, short-lived handle on ANOTHER business's file — never
 * the singleton, and never the active business.
 *
 * [get] keeps exactly one instance, and opening a second business
 * through it would close the one the open screens are using. That is
 * why the daily reminder sweep could only ever see the shop that
 * happened to be open, and a cheque due in a closed shop passed in
 * silence. This gives a background job its own handle instead: it
 * reads, it closes, and KhataDatabaseHolder.instance is never touched.
 *
 * Two live handles on ONE file would let a write race a switch, so
 * this refuses the active file outright rather than trusting callers.
 * Returns null when the business has no database file yet (created
 * but never written to) — a reader must not bring a file into
 * existence. The caller MUST close what it receives.
 */
fun KhataDatabase.Companion.openOther(context: Context, business: Businesses.Business): KhataDatabase? {
    val app = context.applicationContext
    if (business.file == Businesses.activeDbFile(app)) return null
    if (!app.getDatabasePath(business.file).exists()) return null
    return Room.databaseBuilder(app, KhataDatabase::class.java, business.file)
        .addMigrations(*ALL_MIGRATIONS)
        .build()
}

/**
 * Called by [Businesses.switchTo] after the active business changes.
 * The next [get] opens the newly active business's own file.
 */
fun KhataDatabase.Companion.closeActive() {
    synchronized(KhataDatabaseHolder) {
        KhataDatabaseHolder.instance?.close()
        KhataDatabaseHolder.instance = null
        KhataDatabaseHolder.instanceFile = null
    }
}
