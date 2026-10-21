package com.innovation313.roshankhata.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Schema 38 (22 Oct 2026, live sync Marhala 1): a version-37 book opens with
 * every party, product, plan, line, staff row, scheme and day close given its
 * own uid, every uid table indexed on it, and the backup (format 14) carries
 * those uids so a restored book is the same rows, not new ones.
 */
@RunWith(AndroidJUnit4::class)
class Schema38Test {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val hex32 = Regex("[0-9a-f]{32}")

    @Test
    fun aVersion37BookOpensAt38AndEveryOldRowGetsItsOwnUid() = runBlocking {
        val name = "migration_37_38_test.db"
        context.deleteDatabase(name)
        val file = Room.databaseBuilder(context, KhataDatabase::class.java, name).addMigrations(*ALL_MIGRATIONS).build()
        val d = file.khataDao()
        val ali = d.insertParty(Party(name = "Ali"))
        d.insertParty(Party(name = "Bilal"))
        d.insertProduct(Product(name = "Urea", nameKey = "urea", normalisedName = "urea"))
        d.insertEntryWithItems(
            LedgerEntry(partyId = ali, amount = 900.0, isGiven = true, entryNumber = ""),
            listOf(EntryItem(entryId = 0, lineNo = 0, itemName = "Urea", quantity = 2.0, rate = 450.0))
        )
        d.insertPlan(PaymentPlan(partyId = ali, totalAmount = 900.0))
        val imran = d.insertStaff(Staff(name = "Imran", monthlySalary = 20_000.0))
        d.markAttendance(StaffAttendance(staffId = imran, day = 20_000L, status = 1))
        d.saveDayClose(DayClose(day = 20_000L, opening = 0.0, cashIn = 0.0, cashOut = 0.0, counted = 0.0))
        file.close()
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
            SchemaRewind.undo38(raw); raw.version = 37
        }
        val again = Room.databaseBuilder(context, KhataDatabase::class.java, name).addMigrations(*ALL_MIGRATIONS).build()
        try {
            val a = again.khataDao()
            val parties = a.allPartiesForBackup()
            assertEquals(2, parties.size)
            parties.forEach { assertTrue(it.uid.orEmpty(), hex32.matches(it.uid.orEmpty())) }
            assertNotEquals("each row its own", parties[0].uid, parties[1].uid)
            assertTrue(hex32.matches(a.allProductsForBackup().single().uid.orEmpty()))
            assertTrue(hex32.matches(a.allEntryItemsForBackup().single().uid.orEmpty()))
            assertTrue(hex32.matches(a.allPlansForBackup().single().uid.orEmpty()))
            assertTrue(hex32.matches(a.allStaffForBackup().single().uid.orEmpty()))
            assertTrue(hex32.matches(a.allAttendanceForBackup().single().uid.orEmpty()))
            assertTrue(hex32.matches(a.allDayClosesForBackup().single().uid.orEmpty()))
            val indices = again.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type = 'index' AND name LIKE 'index_%_uid'").use { c ->
                buildSet { while (c.moveToNext()) add(c.getString(0)) }
            }
            assertEquals(Schema38.newIndices.toSet(), indices)
        } finally { again.close(); context.deleteDatabase(name) }
    }

    @Test
    fun theBackupCarriesEveryNewUidAndARestoreKeepsThem() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java).build()
        val other = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java).build()
        try {
            val dao = db.khataDao()
            val ali = dao.insertParty(Party(name = "Ali"))
            dao.insertProduct(Product(name = "Urea", nameKey = "urea", normalisedName = "urea"))
            dao.insertEntryWithItems(
                LedgerEntry(partyId = ali, amount = 900.0, isGiven = true, entryNumber = ""),
                listOf(EntryItem(entryId = 0, lineNo = 0, itemName = "Urea", quantity = 2.0, rate = 450.0))
            )
            val imran = dao.insertStaff(Staff(name = "Imran", monthlySalary = 20_000.0))
            dao.markAttendance(StaffAttendance(staffId = imran, day = 20_000L, status = 1))
            dao.saveDayClose(DayClose(day = 20_000L, opening = 0.0, cashIn = 0.0, cashOut = 0.0, counted = 0.0))

            val out = java.io.StringWriter()
            BackupCodec.exportTo(dao, "T", org.json.JSONObject(), out)
            val (res, parsed) = BackupCodec.parseText(out.toString())
            assertTrue(res is BackupCodec.ImportResult.Ok)
            parsed!!
            assertEquals(14, parsed.version)
            val o = other.khataDao()
            BackupCodec.restoreLedger(o, parsed)
            assertEquals(dao.allPartiesForBackup().single().uid, o.allPartiesForBackup().single().uid)
            assertEquals(dao.allProductsForBackup().single().uid, o.allProductsForBackup().single().uid)
            assertEquals(dao.allEntryItemsForBackup().single().uid, o.allEntryItemsForBackup().single().uid)
            assertEquals(dao.allStaffForBackup().single().uid, o.allStaffForBackup().single().uid)
            assertEquals(dao.allAttendanceForBackup().single().uid, o.allAttendanceForBackup().single().uid)
            assertEquals(dao.allDayClosesForBackup().single().uid, o.allDayClosesForBackup().single().uid)
        } finally { db.close(); other.close() }
    }
}
