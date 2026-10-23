package com.innovation313.roshankhata.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Drive backup of a shop that is not open (23 Oct 2026, the review, R21): its book goes out under
 * its OWN name, profile and file name, never the open shop's. Business 1 is open throughout; the
 * book exported is shop 7's (an in-memory stand-in for the file openOther would give).
 */
@RunWith(AndroidJUnit4::class)
class LeftShopBackupTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var shop7: KhataDatabase

    @Before
    fun open() {
        shop7 = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java).build()
        // The open shop (Business 1, the legacy file) and shop 7, each with its own details.
        context.getSharedPreferences("roshan_khata_prefs", Context.MODE_PRIVATE).edit()
            .putString("business_name", "Open Shop")
            .putString("bank_iban", "PK00OPEN")
            .putString("currency", "Rs")
            .commit()
        context.getSharedPreferences("business_profile_b7", Context.MODE_PRIVATE).edit()
            .putString("business_name", "Left Shop")
            .putString("bank_iban", "PK00LEFT")
            .putString("currency", "Tk")
            .putString("country", ShopCountry.BANGLADESH.code)
            .commit()
    }

    @After
    fun close() {
        shop7.close()
    }

    @Test
    fun aLeftShopsBackupCarriesItsOwnNameAndProfile() = runBlocking {
        val dao = shop7.khataDao()
        val party = dao.insertParty(Party(name = "Customer"))
        dao.insertEntry(LedgerEntry(partyId = party, amount = 500.0, isGiven = true, entryNumber = "RK-000001"))

        val file = Backup.exportToCacheFor(context, dao, 7L)
        try {
            assertTrue("the file is named for shop 7: ${file.name}", file.name.startsWith("left-RoshanKhata_Backup_b7_"))
            val (result, parsed) = Backup.parseFile(file)
            assertTrue("the backup did not parse: $result", result is BackupCodec.ImportResult.Ok)
            val data = requireNotNull(parsed)
            assertEquals("Left Shop", data.businessName)
            val profile = requireNotNull(data.businessProfile)
            assertEquals("Left Shop", profile.businessName)
            assertEquals("PK00LEFT", profile.bankIban)
            assertEquals("Tk", profile.currency)
            assertEquals(ShopCountry.BANGLADESH.code, profile.country)
            assertEquals(1, data.entries.size)
        } finally {
            file.delete()
        }
    }

    @Test
    fun theOpenShopsExportIsUnchanged() = runBlocking {
        val json = Backup.export(context, shop7.khataDao())
        val (_, parsed) = Backup.parseText(json)
        assertEquals("Open Shop", requireNotNull(parsed).businessName)
        assertEquals("PK00OPEN", parsed.businessProfile?.bankIban)
    }

    @Test(expected = UnsupportedOperationException::class)
    fun aClosedShopsProfileIsNeverWrittenThroughTheBackupReader() {
        BusinessProfile.settingsOf(context, 7L).setBusinessName("Other")
    }

    @Test
    fun eachLeftShopHasItsOwnJobBesideTheOpenShops() {
        assertNotEquals("backup_after_change", AutoBackup.leftWorkName(1L))
        assertNotEquals(AutoBackup.leftWorkName(1L), AutoBackup.leftWorkName(7L))
    }
}
