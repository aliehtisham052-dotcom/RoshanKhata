package com.innovation313.roshankhata.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Party::class,
        LedgerEntry::class,
        Cheque::class,
        CashEntry::class,
        PaymentPlan::class,
        Installment::class,
        SupplierBill::class,
        BillItem::class,
        Product::class,
        DismissedDuplicate::class,
        Invoice::class,
        InvoiceItem::class
    ],
    version = KHATA_DB_VERSION,
    exportSchema = true
)
abstract class KhataDatabase : RoomDatabase() {

    abstract fun khataDao(): KhataDao

    companion object {
        @Volatile
        private var INSTANCE: KhataDatabase? = null

        /** Which file [INSTANCE] was opened for. Guarded by the same lock. */
        @Volatile
        private var INSTANCE_FILE: String? = null

        fun get(context: Context): KhataDatabase {
            // The check-and-balance for multi-business lives on this line and
            // nowhere else: every caller in the app reaches the database
            // through get(), and get() will not return an instance unless it
            // was opened for the file the ACTIVE business owns. A stale
            // instance — one still pointing at another shop's ledger after a
            // switch — cannot leave this method.
            val file = Businesses.activeDbFile(context)
            INSTANCE?.let { if (INSTANCE_FILE == file) return it }
            return synchronized(this) {
                val current = INSTANCE
                if (current != null && INSTANCE_FILE == file) current
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
                    .build()
                    .also {
                        INSTANCE = it
                        INSTANCE_FILE = file
                    }
                }
            }
        }

        /**
         * Called by [Businesses.switchTo] after the active business changes.
         * The next [get] opens the newly active business's own file.
         */
        fun closeActive() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
                INSTANCE_FILE = null
            }
        }
    }
}

/** Builds the next human-facing reference, e.g. "RK-000042". */
object EntryNumber {
    fun next(existingCount: Int): String =
        "RK-%06d".format(existingCount + 1)
}
