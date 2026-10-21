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

    /** Every migration a computer's book may need, in order. */
    val MIGRATIONS = arrayOf<Migration>(MIGRATION_31_32, MIGRATION_32_33, MIGRATION_33_34, MIGRATION_34_35)

    fun open(file: File): KhataDatabase {
        file.parentFile?.mkdirs()
        return Room.databaseBuilder<KhataDatabase>(name = file.absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addMigrations(*MIGRATIONS)
            .build()
    }

    /** Where the book lives: %APPDATA%\Roshan Khata on Windows, ~/.roshankhata elsewhere. */
    fun home(): File {
        val appData = System.getenv("APPDATA")
        return if (!appData.isNullOrBlank()) File(appData, "Roshan Khata")
        else File(System.getProperty("user.home"), ".roshankhata")
    }
}
