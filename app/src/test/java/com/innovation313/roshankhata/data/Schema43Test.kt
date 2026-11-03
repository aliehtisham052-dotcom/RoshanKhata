package com.innovation313.roshankhata.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Schema 43 (4 Nov 2026): a version-42 book opens with every party, cashbook row and cheque keeping what it had and
 * no note or picture (none was ever written); a row written afterwards keeps its note or picture path; and the backup
 * (format 18) carries all three, so a restored book still has the owner's notes and still points at its pictures.
 * Made-up names and figures only.
 */
@RunWith(AndroidJUnit4::class)
class Schema43Test {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun aVersion42BookOpensAt43WithNoNoteOrPictureOnItsOldRows() = runBlocking {
        val name = "migration_42_43_test.db"
        context.deleteDatabase(name)
        val file = Room.databaseBuilder(context, KhataDatabase::class.java, name).addMigrations(*ALL_MIGRATIONS).build()
        val d = file.khataDao()
        val ali = d.insertParty(Party(name = "Ali", village = "Chak 7"))
        d.insertCashEntry(CashEntry(amount = 500.0, isIncome = false, category = "Electricity"))
        d.insertCheque(Cheque(partyId = ali, amount = 2_000.0, isReceived = true, chequeNumber = "000123", dueDate = 1_000L))
        file.close()
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
            SchemaRewind.undo43(raw); raw.version = 42
        }
        val again = Room.databaseBuilder(context, KhataDatabase::class.java, name).addMigrations(*ALL_MIGRATIONS).build()
        try {
            val a = again.khataDao()
            val party = a.allPartiesForBackup().single()
            assertEquals("Chak 7", party.village)
            assertNull("nothing was written before the column existed", party.note)
            assertNull(a.allCashForBackup().single().photoPath)
            assertEquals("000123", a.allChequesForBackup().single().chequeNumber)
            assertNull(a.allChequesForBackup().single().photoPath)

            a.updateParty(party.copy(note = "Pays on the 10th"))
            val cash = a.insertCashEntry(CashEntry(amount = 300.0, isIncome = false, category = "Rent", photoPath = "/files/bills/bill_1.jpg"))
            a.setChequePhotoPath(a.allChequesForBackup().single().id, "/files/bills/bill_2.jpg")
            assertEquals("Pays on the 10th", a.getParty(ali)?.note)
            assertEquals(listOf(cash to "/files/bills/bill_1.jpg"), a.cashWithPhoto().map { it.id to it.photoPath })
            assertEquals(listOf("/files/bills/bill_2.jpg"), a.chequesWithPhoto().map { it.photoPath })
            a.setCashPhotoPath(cash, null)
            assertTrue("a picture taken away is no longer pointed at", a.cashWithPhoto().isEmpty())

            for (table in listOf("parties", "cashbook", "cheques")) {
                val columns = again.openHelper.readableDatabase.query("PRAGMA table_info($table)").use { c ->
                    buildSet { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
                }
                assertTrue(table, Schema43.newColumns.getValue(table).all { it in columns })
            }
        } finally { again.close(); context.deleteDatabase(name) }
    }

    @Test
    fun theBackupCarriesTheNoteAndThePicturePathsAndARestoreKeepsThem() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java).build()
        val other = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java).build()
        try {
            val dao = db.khataDao()
            val ali = dao.insertParty(Party(name = "Ali", note = "Only cash\nAsk for his son"))
            dao.insertParty(Party(name = "Bilal"))
            dao.insertCashEntry(CashEntry(amount = 300.0, isIncome = false, category = "Rent", photoPath = "/files/bills/bill_1.jpg"))
            dao.insertCashEntry(CashEntry(amount = 80.0, isIncome = false, category = "Tea"))
            dao.insertCheque(Cheque(partyId = ali, amount = 2_000.0, isReceived = true, dueDate = 1_000L, photoPath = "/files/bills/bill_2.jpg"))

            val out = java.io.StringWriter()
            BackupCodec.exportTo(dao, "T", org.json.JSONObject(), out)
            val (res, parsed) = BackupCodec.parseText(out.toString())
            assertTrue(res is BackupCodec.ImportResult.Ok)
            parsed!!
            // Written at the format of the day (18, 4 Nov 2026, brought the note and the two pictures).
            assertEquals(BackupCodec.FORMAT_VERSION, parsed.version)
            assertTrue(parsed.version >= 18)
            val o = other.khataDao()
            BackupCodec.restoreLedger(o, parsed)
            val parties = o.allPartiesForBackup()
            assertEquals("Only cash\nAsk for his son", parties.single { it.name == "Ali" }.note)
            assertNull("a party with none restores with none, not the word null", parties.single { it.name == "Bilal" }.note)
            val cash = o.allCashForBackup()
            assertEquals("/files/bills/bill_1.jpg", cash.single { it.category == "Rent" }.photoPath)
            assertNull(cash.single { it.category == "Tea" }.photoPath)
            assertEquals("/files/bills/bill_2.jpg", o.allChequesForBackup().single().photoPath)
        } finally { db.close(); other.close() }
    }
}
