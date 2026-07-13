package com.innovation313.roshankhata.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Party::class, LedgerEntry::class],
    version = 1,
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
                ).build().also { INSTANCE = it }
            }
    }
}

/** Builds the next human-facing reference, e.g. "RK-000042". */
object EntryNumber {
    fun next(existingCount: Int): String =
        "RK-%06d".format(existingCount + 1)
}
