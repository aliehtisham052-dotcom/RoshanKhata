package com.innovation313.roshankhata.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Party::class, LedgerEntry::class, Cheque::class, CashEntry::class],
    version = 6,
    exportSchema = false
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
                    // Pre-release only: no real user data exists yet, so a clean
                    // rebuild is safe. This MUST be replaced with a proper
                    // Migration before the app ever ships to a real user.
                    .fallbackToDestructiveMigration()
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
