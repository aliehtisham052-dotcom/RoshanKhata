package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

/**
 * The whole road on the computer: a phone's backup restored into the
 * computer's own SQLite book, and the customer list read back with the
 * phone's own query.
 */
class DesktopBookTest {

    @Test
    fun `a phone backup becomes the computer's book`() {
        val dir = Files.createTempDirectory("rk-book").toFile()
        val db = DesktopDatabase.open(dir.resolve("khata.db"))
        try {
            val parsed = BackupCodec.parseText(BackupFixtures.v7).second!!
            runBlocking {
                BackupCodec.restoreLedger(db.khataDao(), parsed)
                val parties = db.khataDao().partiesWithBalanceOnce()
                assertEquals(1, parties.size)
                assertEquals("Ali (علی)", parties[0].name)
                assertEquals(4598.0, parties[0].balance, 0.001)
                assertEquals(1, db.khataDao().entryItemsPageForBackup(0, 10).size)
            }
        } finally {
            db.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a computer backup reads back whole, for the phone to restore`() {
        val dir = Files.createTempDirectory("rk-book").toFile()
        val db = DesktopDatabase.open(dir.resolve("khata.db"))
        try {
            val settings = FileShopSettings(dir.resolve("shop.properties"))
            settings.setBusinessName("Bhatti Traders")
            settings.setCalendar(ShopCalendar.Kind.HIJRI)
            val text = runBlocking {
                val dao = db.khataDao()
                val id = dao.insertParty(Party(name = "Bilal", phone = "0300 1234567", isCustomer = true))
                dao.insertEntryWithItems(LedgerEntry(partyId = id, amount = 1550.0, isGiven = true, note = "urea", entryNumber = ""), emptyList())
                val out = java.io.StringWriter()
                BackupCodec.exportTo(dao, settings.businessName(), BackupCodec.profileToJson(settings), out)
                out.toString()
            }
            val (result, data) = BackupCodec.parseText(text)
            assertEquals(BackupCodec.ImportResult.Ok(parties = 1, entries = 1, cheques = 0, cash = 0), result)
            data!!
            assertEquals("Bilal", data.parties.single().name)
            assertEquals("urea", data.entries.single().note)
            assertEquals("RK-000001", data.entries.single().entryNumber)
            assertEquals("Bhatti Traders", data.businessName)
            assertEquals("hijri", data.businessProfile!!.calendar)
        } finally {
            db.close()
            dir.deleteRecursively()
            ShopCalendar.useForTest(ShopCalendar.Kind.GREGORIAN)
        }
    }

    @Test
    fun `entries written on the computer are numbered and summed as on the phone`() {
        val dir = Files.createTempDirectory("rk-book").toFile()
        val db = DesktopDatabase.open(dir.resolve("khata.db"))
        try {
            runBlocking {
                val dao = db.khataDao()
                val id = dao.insertParty(Party(name = "Bilal", phone = null, isCustomer = true))
                dao.insertEntryWithItems(LedgerEntry(partyId = id, amount = 1550.0, isGiven = true, entryNumber = ""), emptyList())
                dao.insertEntryWithItems(LedgerEntry(partyId = id, amount = 500.0, isGiven = false, note = "cash", entryNumber = ""), emptyList())
                val entries = dao.latestEntriesWithItems(id, 10).map { it.entry }
                assertEquals(setOf("RK-000001", "RK-000002"), entries.map { it.entryNumber }.toSet())
                assertEquals(1050.0, dao.partiesWithBalanceOnce().single().balance, 0.001)
            }
        } finally {
            db.close()
            dir.deleteRecursively()
        }
    }
}
