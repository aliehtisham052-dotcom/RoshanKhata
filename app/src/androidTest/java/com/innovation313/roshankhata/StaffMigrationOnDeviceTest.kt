package com.innovation313.roshankhata

import androidx.room.Room
import androidx.room.util.TableInfo
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.MIGRATION_28_29
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The v29 migration builds the three staff tables EXACTLY as Room expects them.
 *
 * MigrationChainTest only proves the chain is unbroken, and every other
 * on-device test starts from a fresh install, which Room builds from the
 * entities without running any migration. So a wrong column in the migration
 * would pass all of CI and then crash every phone that updates. This test
 * runs MIGRATION_28_29 on an empty database and compares each table, column
 * by column and index by index, with the table Room creates itself — the same
 * comparison Room makes when it opens a migrated database.
 */
class StaffMigrationOnDeviceTest {

    @Test
    fun migration_tables_match_what_room_builds() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val migrated = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {}
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        ).writableDatabase
        MIGRATION_28_29.migrate(migrated)

        val room = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java).build()
        val fresh = room.openHelper.writableDatabase

        for (table in listOf("staff", "staff_attendance", "staff_payments")) {
            assertEquals("table $table", TableInfo.read(fresh, table), TableInfo.read(migrated, table))
        }
        room.close()
        migrated.close()
    }
}
