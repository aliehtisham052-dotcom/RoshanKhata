package com.innovation313.roshankhata.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * The computer's book: the same Room database as the phone (KhataDatabase,
 * schema 31), in one SQLite file. SQLite itself comes bundled with the app
 * (BundledSQLiteDriver), so nothing has to be installed on the computer.
 *
 * A new file starts at the current schema; there is no older computer book
 * to migrate yet. A phone's book arrives through a backup (BackupCodec).
 */
object DesktopDatabase {

    fun open(file: File): KhataDatabase {
        file.parentFile?.mkdirs()
        return Room.databaseBuilder<KhataDatabase>(name = file.absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }

    /** Where the book lives: %APPDATA%\Roshan Khata on Windows, ~/.roshankhata elsewhere. */
    fun home(): File {
        val appData = System.getenv("APPDATA")
        return if (!appData.isNullOrBlank()) File(appData, "Roshan Khata")
        else File(System.getProperty("user.home"), ".roshankhata")
    }
}
