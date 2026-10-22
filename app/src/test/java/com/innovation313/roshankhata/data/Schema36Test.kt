package com.innovation313.roshankhata.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Schema 36 (21 Oct 2026, the critical review): a version-35 book opens with
 * every row given a uid; the backup carries the book's settings, loyalty,
 * shifts, uids, packs, line tax and the audit seal; the owner's PIN is kept as
 * a hash; the seal finds a row edited behind the app's back.
 */
@RunWith(AndroidJUnit4::class)
class Schema36Test {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: KhataDatabase
    private lateinit var dao: KhataDao

    @Before
    fun open(): Unit = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java).build()
        dao = db.khataDao()
    }

    @After
    fun close() {
        db.close()
        Access.resetForTest()
    }

    private val hex32 = Regex("[0-9a-f]{32}")

    @Test
    fun aVersion35BookOpensAt36AndEveryOldRowGetsItsOwnUid() = runBlocking {
        val name = "migration_35_36_test.db"
        context.deleteDatabase(name)
        val file = Room.databaseBuilder(context, KhataDatabase::class.java, name).addMigrations(*ALL_MIGRATIONS).build()
        val d = file.khataDao()
        val ali = d.insertParty(Party(name = "Ali"))
        d.insertEntryNumbered(LedgerEntry(partyId = ali, amount = 500.0, isGiven = true, entryNumber = ""))
        d.insertEntryNumbered(LedgerEntry(partyId = ali, amount = 200.0, isGiven = false, entryNumber = ""))
        d.insertCashEntry(CashEntry(amount = 50.0, isIncome = false, category = "Tea"))
        file.close()
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
            SchemaRewind.undo36(raw); raw.version = 35
        }
        val again = Room.databaseBuilder(context, KhataDatabase::class.java, name).addMigrations(*ALL_MIGRATIONS).build()
        try {
            val a = again.khataDao()
            val entries = a.entriesPageForBackup(0, 100)
            assertEquals(2, entries.size)
            entries.forEach { assertTrue(it.uid.orEmpty(), hex32.matches(it.uid.orEmpty())) }
            assertNotEquals("each row its own", entries[0].uid, entries[1].uid)
            assertTrue(hex32.matches(a.allCashForBackup().single().uid.orEmpty()))
            assertNull(a.settingValue(BookSettings.OWNER_PIN_HASH))
            assertEquals(0.0, a.loyaltyBalance(ali), 0.0)
            assertNull(a.openShiftOf(null))
        } finally { again.close(); context.deleteDatabase(name) }
    }

    @Test
    fun theFileCarriesTheNewTablesAndColumnsAndKeepsEveryUid() = runBlocking {
        val ali = dao.insertParty(Party(name = "Ali"))
        val soap = dao.insertProduct(Product(name = "Soap", nameKey = "soap", normalisedName = "soap", defaultUnit = "piece",
            packUnit = "carton", packSize = 24.0, packPrice = 2_300.0, taxRate = 18.0))
        dao.insertProduct(Product(name = "Soap Rose", nameKey = "soap rose", normalisedName = "soaprose", parentId = soap, variant = "Rose"))
        dao.insertEntryNumbered(LedgerEntry(partyId = ali, amount = 100.0, isGiven = true, entryNumber = ""))
        dao.setSetting(BookSettings.PRICES_INCLUDE_TAX, "1")
        dao.insertLoyalty(LoyaltyEntry(partyId = ali, points = 12.5))
        dao.insertShift(Shift(user = "Imran", openingFloat = 1_000.0))
        dao.log(AuditAction.SIGN_IN, "Imran")
        val code = Access.setOwnerPin(dao, "246813")
        assertTrue(code.length == 9)
        assertNotEquals("246813", dao.settingValue(BookSettings.OWNER_PIN_HASH))
        assertTrue(Access.ownerPin(dao)!!.matches("246813"))
        assertTrue(Access.recoveryMatches(dao, code.lowercase()))
        assertFalse(Access.recoveryMatches(dao, "AAAA-AAAA"))

        val uidBefore = dao.entriesPageForBackup(0, 10).single().uid
        val out = java.io.StringWriter()
        BackupCodec.exportTo(dao, "T", org.json.JSONObject(), out)
        assertFalse("never the PIN itself", out.toString().contains("\"2468\""))
        val (res, parsed) = BackupCodec.parseText(out.toString())
        assertTrue(res is BackupCodec.ImportResult.Ok)
        parsed!!
        assertEquals(1, parsed.loyalty.size); assertEquals(1, parsed.shifts.size)
        assertTrue(parsed.settings.any { it.key == BookSettings.OWNER_PIN_HASH })
        assertEquals(24.0, parsed.products.first { it.name == "Soap" }.packSize!!, 0.0)
        assertEquals(soap, parsed.products.first { it.name == "Soap Rose" }.parentId)
        assertTrue(parsed.audit.all { it.hash != null })

        val other = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java).build()
        try {
            val o = other.khataDao()
            BackupCodec.restoreLedger(o, parsed)
            assertEquals(uidBefore, o.entriesPageForBackup(0, 10).single().uid)
            assertEquals(12.5, o.loyaltyBalance(ali), 0.0)
            assertEquals("Imran", o.openShiftOf("Imran")!!.user)
            assertEquals("1", o.settingValue(BookSettings.PRICES_INCLUDE_TAX))
            assertTrue(Access.ownerPin(o)!!.matches("246813"))
            val seal = AuditChain.verify(o.auditInOrder())
            assertTrue("the restored log still holds, the restore row sealed onto it", seal.intact)
        } finally { other.close() }
    }

    @Test
    fun aFileFromBeforeFormat12GivesEveryRowANewUid() = runBlocking {
        val ali = dao.insertParty(Party(name = "Ali"))
        dao.insertEntryNumbered(LedgerEntry(partyId = ali, amount = 100.0, isGiven = true, entryNumber = ""))
        val out = java.io.StringWriter()
        BackupCodec.exportTo(dao, "T", org.json.JSONObject(), out)
        val old = out.toString().replace(Regex("\"uid\":\"[0-9a-f]{32}\""), "\"uid\":null").replace("\"version\": ${BackupCodec.FORMAT_VERSION}", "\"version\": 11")
        val (_, parsed) = BackupCodec.parseText(old)
        assertTrue(hex32.matches(parsed!!.entries.single().uid.orEmpty()))
    }

    @Test
    fun theSealFindsALogRowChangedBehindTheAppsBack() = runBlocking {
        dao.log(AuditAction.SALE_POSTED, null, "sales", 1, "S-000001 500.00")
        dao.log(AuditAction.SALE_VOIDED, "Imran", "sales", 1, "S-000001 mistake")
        dao.log(AuditAction.SIGN_IN, "Asad")
        assertTrue(AuditChain.verify(dao.auditInOrder()).intact)
        val second = dao.auditInOrder()[1].id
        db.openHelper.writableDatabase.execSQL("UPDATE audit_log SET detail = 'S-000001 customer asked' WHERE id = $second")
        val r = AuditChain.verify(dao.auditInOrder())
        assertEquals(second, r.brokenAt)
        assertNotNull(dao.lastAuditHash())
    }
}
