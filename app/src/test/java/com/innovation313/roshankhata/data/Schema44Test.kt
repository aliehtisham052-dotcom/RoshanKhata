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
 * Schema 44 (4 Nov 2026, Kitab ki Muhar): a version-43 book opens at 44 with every entry as it was and no seal (none
 * was ever made); a seal written afterwards reads back whole, its leaves as bytes; and the backup (format 19) carries
 * the seals, so a restored book keeps its proofs. Made-up names and figures only.
 */
@RunWith(AndroidJUnit4::class)
class Schema44Test {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun aVersion43BookOpensAt44WithNoSeal() = runBlocking {
        val name = "migration_43_44_test.db"
        context.deleteDatabase(name)
        val file = Room.databaseBuilder(context, KhataDatabase::class.java, name).addMigrations(*ALL_MIGRATIONS).build()
        val d = file.khataDao()
        val ali = d.insertParty(Party(name = "Ali"))
        d.insertEntryNumbered(LedgerEntry(partyId = ali, amount = 900.0, isGiven = true, entryNumber = ""))
        file.close()
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
            SchemaRewind.undo44(raw); raw.version = 43
        }
        val again = Room.databaseBuilder(context, KhataDatabase::class.java, name).addMigrations(*ALL_MIGRATIONS).build()
        try {
            val a = again.khataDao()
            assertEquals(900.0, a.allEntriesForBackup().single().amount, 0.0)
            assertNull("nothing was sealed before the table existed", a.latestSeal())
            val leaves = SealMath.leafHash("e1|x") + SealMath.leafHash("e1|y")
            val id = a.insertSeal(BookSeal(sealedAt = 1_000L, fromId = 0, toId = 1, leafCount = 2, leaves = leaves, digest = "ab"))
            assertTrue(leaves.contentEquals(a.sealById(id)!!.leaves))
            assertEquals(1, a.maxEntryId())
            val tables = again.openHelper.readableDatabase.query("SELECT name FROM sqlite_master").use { c ->
                buildSet { while (c.moveToNext()) add(c.getString(0)) }
            }
            assertTrue((Schema44.newTables + Schema44.newIndices).all { it in tables })
        } finally { again.close(); context.deleteDatabase(name) }
    }

    @Test
    fun theBackupCarriesTheSealsAndARestoreKeepsThem() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java).build()
        val other = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java).build()
        try {
            val dao = db.khataDao()
            val ali = dao.insertParty(Party(name = "Ali"))
            BookSeals.turnOn(dao, 1_000L)
            dao.insertEntryNumbered(LedgerEntry(partyId = ali, amount = 900.0, isGiven = true, entryNumber = ""))
            BookSeals.sealIfDue(dao, 1_000L + 2 * 24 * 60 * 60 * 1000)
            val seals = dao.allSealsForBackup()
            assertEquals(2, seals.size)
            assertEquals(1, seals[1].leafCount)

            val out = java.io.StringWriter()
            BackupCodec.exportTo(dao, "T", org.json.JSONObject(), out)
            val (res, parsed) = BackupCodec.parseText(out.toString())
            assertTrue(res is BackupCodec.ImportResult.Ok)
            parsed!!
            assertEquals(BackupCodec.FORMAT_VERSION, parsed.version)
            assertTrue(parsed.version >= 19)
            BackupCodec.restoreLedger(other.khataDao(), parsed)
            assertEquals(seals, other.khataDao().allSealsForBackup())
        } finally { db.close(); other.close() }
    }
}
