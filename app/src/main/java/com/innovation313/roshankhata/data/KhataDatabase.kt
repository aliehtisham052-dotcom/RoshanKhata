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
        BillItem::class
    ],
    version = 8,
    exportSchema = true
)
abstract class KhataDatabase : RoomDatabase() {

    abstract fun khataDao(): KhataDao

    companion object {
        @Volatile
        private var INSTANCE: KhataDatabase? = null

        fun get(context: Context): KhataDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KhataDatabase::class.java,
                    "roshan_khata.db"
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
                    .also { INSTANCE = it }
            }
    }
}

/** Builds the next human-facing reference, e.g. "RK-000042". */
object EntryNumber {
    fun next(existingCount: Int): String =
        "RK-%06d".format(existingCount + 1)
}
