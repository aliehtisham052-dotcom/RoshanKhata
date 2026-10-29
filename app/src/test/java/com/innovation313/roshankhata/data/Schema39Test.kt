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
 * Schema 39 (28 Oct 2026, the payment reference): a version-38 book opens
 * with every khata and cashbook row keeping what it had and no reference
 * (nothing was ever recorded), a row written afterwards keeps the id its
 * payment came with, and the backup (format 16) carries it so a restored
 * book can still answer "which transfer paid this".
 */
@RunWith(AndroidJUnit4::class)
class Schema39Test {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun aVersion38BookOpensAt39WithNoReferenceOnItsOldRows() = runBlocking {
        val name = "migration_38_39_test.db"
        context.deleteDatabase(name)
        val file = Room.databaseBuilder(context, KhataDatabase::class.java, name).addMigrations(*ALL_MIGRATIONS).build()
        val d = file.khataDao()
        val ali = d.insertParty(Party(name = "Ali"))
        d.insertEntryWithItems(
            LedgerEntry(partyId = ali, amount = 900.0, isGiven = false, entryNumber = "", paymentMethod = PaymentMethod.BANK),
            emptyList()
        )
        d.insertCashEntry(CashEntry(amount = 500.0, isIncome = true, category = CashCategory.SALE, paymentMethod = PaymentMethod.ONLINE))
        file.close()
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
            SchemaRewind.undo39(raw); raw.version = 38
        }
        val again = Room.databaseBuilder(context, KhataDatabase::class.java, name).addMigrations(*ALL_MIGRATIONS).build()
        try {
            val a = again.khataDao()
            val old = a.allEntriesForBackup().single()
            assertEquals(900.0, old.amount, 0.0)
            assertEquals(PaymentMethod.BANK, old.paymentMethod)
            assertNull("nothing was recorded before the column existed", old.paymentRef)
            val oldCash = a.allCashForBackup().single()
            assertEquals(PaymentMethod.ONLINE, oldCash.paymentMethod)
            assertNull(oldCash.paymentRef)

            a.insertEntryWithItems(
                LedgerEntry(partyId = ali, amount = 1200.0, isGiven = false, entryNumber = "", paymentMethod = PaymentMethod.ONLINE, paymentRef = "TID 7781234509"),
                emptyList()
            )
            a.insertCashEntry(CashEntry(amount = 300.0, isIncome = true, category = CashCategory.SALE, paymentMethod = PaymentMethod.BANK, paymentRef = "RAAST 20261028-0042"))
            assertEquals("TID 7781234509", a.allEntriesForBackup().single { it.amount == 1200.0 }.paymentRef)
            assertEquals("RAAST 20261028-0042", a.allCashForBackup().single { it.amount == 300.0 }.paymentRef)

            val columns = again.openHelper.readableDatabase.query("PRAGMA table_info(transactions)").use { c ->
                buildSet { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
            }
            assertTrue("paymentRef" in columns)
        } finally { again.close(); context.deleteDatabase(name) }
    }

    @Test
    fun theBackupCarriesTheReferenceAndARestoreKeepsIt() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java).build()
        val other = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java).build()
        try {
            val dao = db.khataDao()
            val ali = dao.insertParty(Party(name = "Ali"))
            dao.insertEntryWithItems(
                LedgerEntry(partyId = ali, amount = 1200.0, isGiven = false, entryNumber = "", paymentMethod = PaymentMethod.ONLINE, paymentRef = "TID 7781234509"),
                emptyList()
            )
            dao.insertEntryWithItems(LedgerEntry(partyId = ali, amount = 400.0, isGiven = false, entryNumber = ""), emptyList())
            dao.insertCashEntry(CashEntry(amount = 300.0, isIncome = true, category = CashCategory.SALE, paymentMethod = PaymentMethod.BANK, paymentRef = "RAAST 20261028-0042"))

            val out = java.io.StringWriter()
            BackupCodec.exportTo(dao, "T", org.json.JSONObject(), out)
            val (res, parsed) = BackupCodec.parseText(out.toString())
            assertTrue(res is BackupCodec.ImportResult.Ok)
            parsed!!
            // Written at the format of the day (16, 28 Oct 2026, brought the reference).
            assertEquals(BackupCodec.FORMAT_VERSION, parsed.version)
            assertTrue(parsed.version >= 16)
            val o = other.khataDao()
            BackupCodec.restoreLedger(o, parsed)
            val entries = o.allEntriesForBackup()
            assertEquals("TID 7781234509", entries.single { it.amount == 1200.0 }.paymentRef)
            assertNull("a row with none restores with none, not the word null", entries.single { it.amount == 400.0 }.paymentRef)
            assertEquals("RAAST 20261028-0042", o.allCashForBackup().single().paymentRef)
        } finally { db.close(); other.close() }
    }
}
