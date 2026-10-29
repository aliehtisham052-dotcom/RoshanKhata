package com.innovation313.roshankhata.data

import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * The computer's book: the same Room database as the phone (KhataDatabase,
 * schema 31), in one SQLite file. SQLite itself comes bundled with the app
 * (BundledSQLiteDriver), so nothing has to be installed on the computer.
 *
 * A new file starts at the current schema. The computer's first books were
 * written at 31 (18 Oct 2026), so every step from there is migrated here,
 * with the same SQL the phone runs (the shared Schema lists): a book the
 * owner already keeps must open on the next version, never be rebuilt.
 * A phone's book arrives through a backup (BackupCodec).
 */
object DesktopDatabase {

    /** 31 → 32: the counter (POS Phase 1, 21 Oct 2026). */
    private val MIGRATION_31_32 = object : Migration(31, 32) {
        override fun migrate(connection: SQLiteConnection) {
            for (sql in Schema32.statements) connection.execSQL(sql)
        }
    }

    /** 32 → 33: returns (POS Phase 3, 21 Oct 2026). */
    private val MIGRATION_32_33 = object : Migration(32, 33) {
        override fun migrate(connection: SQLiteConnection) {
            for (sql in Schema33.statements) connection.execSQL(sql)
        }
    }

    /** 33 → 34: staff logins (POS Phase 5, 21 Oct 2026). */
    private val MIGRATION_33_34 = object : Migration(33, 34) {
        override fun migrate(connection: SQLiteConnection) {
            for (sql in Schema34.statements) connection.execSQL(sql)
        }
    }

    /** 34 → 35: stock adjustments and purchase orders (POS Phase 6, 21 Oct 2026). */
    private val MIGRATION_34_35 = object : Migration(34, 35) {
        override fun migrate(connection: SQLiteConnection) {
            for (sql in Schema35.statements) connection.execSQL(sql)
        }
    }

    /** 35 → 36: the counter's second night — book settings, loyalty, shifts, packs, tax, variants, uids (21 Oct 2026). */
    private val MIGRATION_35_36 = object : Migration(35, 36) {
        override fun migrate(connection: SQLiteConnection) {
            for (sql in Schema36.statements) connection.execSQL(sql)
        }
    }

    /** 36 → 37: a stock adjustment's batch (an expired lot written off), the same night. */
    private val MIGRATION_36_37 = object : Migration(36, 37) {
        override fun migrate(connection: SQLiteConnection) {
            for (sql in Schema37.statements) connection.execSQL(sql)
        }
    }

    /** 37 → 38: a uid on every row a sync can carry, and an index on each (live sync, 22 Oct 2026). */
    private val MIGRATION_37_38 = object : Migration(37, 38) {
        override fun migrate(connection: SQLiteConnection) {
            for (sql in Schema38.statements) connection.execSQL(sql)
        }
    }

    /** 38 → 39: the payment reference on khata and cashbook rows (28 Oct 2026). */
    private val MIGRATION_38_39 = object : Migration(38, 39) {
        override fun migrate(connection: SQLiteConnection) {
            for (sql in Schema39.statements) connection.execSQL(sql)
        }
    }

    /** 39 → 40: the documents — an invoice's kind, status and the document it was made from (29 Oct 2026). */
    private val MIGRATION_39_40 = object : Migration(39, 40) {
        override fun migrate(connection: SQLiteConnection) {
            for (sql in Schema40.statements) connection.execSQL(sql)
        }
    }

    /** Every migration a computer's book may need, in order. */
    val MIGRATIONS = arrayOf<Migration>(
        MIGRATION_31_32, MIGRATION_32_33, MIGRATION_33_34, MIGRATION_34_35, MIGRATION_35_36, MIGRATION_36_37, MIGRATION_37_38,
        MIGRATION_38_39, MIGRATION_39_40
    )

    fun open(file: File): KhataDatabase {
        file.parentFile?.mkdirs()
        return Room.databaseBuilder<KhataDatabase>(name = file.absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addMigrations(*MIGRATIONS)
            // A larger page cache and temporary tables in memory (21 Oct 2026, the critical review):
            // SQLite's default 2 MB cache made every balance list read the file again on a laptop's
            // slow disk. Settings of this connection only; nothing in the file changes.
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onOpen(connection: androidx.sqlite.SQLiteConnection) {
                    runCatching { connection.execSQL("PRAGMA cache_size = -16000") }
                    runCatching { connection.execSQL("PRAGMA temp_store = MEMORY") }
                    // Units saved as a label ("بوری", "bori") become their key ("bag"), as the phone
                    // does on every open (23 Oct 2026, R02): otherwise stock reads "?" and a product splits.
                    // One read first, then one savepoint kept out of live sync's log (UnitWords.repairStored).
                    UnitWords.repairStored(
                        needed = { sql ->
                            val st = connection.prepare(sql)
                            try { st.step() && st.getLong(0) != 0L } finally { st.close() }
                        },
                        inTransaction = { body ->
                            // A savepoint begins a transaction where there is none and nests where there is one.
                            connection.execSQL("SAVEPOINT rk_units")
                            try {
                                body()
                                connection.execSQL("RELEASE rk_units")
                            } catch (e: Throwable) {
                                runCatching { connection.execSQL("ROLLBACK TO rk_units") }
                                runCatching { connection.execSQL("RELEASE rk_units") }
                                throw e
                            }
                        },
                        exec = { sql -> connection.execSQL(sql) }
                    )
                }
            })
            .build()
    }

    /** Where the book lives: %APPDATA%\Roshan Khata on Windows, ~/.roshankhata elsewhere. */
    fun home(): File {
        val appData = System.getenv("APPDATA")
        return if (!appData.isNullOrBlank()) File(appData, "Roshan Khata")
        else File(System.getProperty("user.home"), ".roshankhata")
    }
}
