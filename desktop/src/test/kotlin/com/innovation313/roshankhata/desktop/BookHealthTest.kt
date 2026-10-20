package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.FileShopSettings
import com.innovation313.roshankhata.data.LedgerEntry
import com.innovation313.roshankhata.data.Party
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.RandomAccessFile
import java.nio.file.Files

/** A damaged book comes back from its daily copy; a sound one is left alone; the bad file is kept. */
class BookHealthTest {

    @Test
    fun `a sound book opens as it is, nothing set aside`() {
        val dir = Files.createTempDirectory("rk-health").toFile()
        val file = dir.resolve("khata.db")
        val settings = FileShopSettings(dir.resolve("shop.properties"))
        val opened = BookHealth.open(file, settings, listOf(dir.resolve("daily")))
        try {
            assertNull(opened.corruptKept)
            assertNull(opened.recoveredFrom)
            assertTrue(BookHealth.isSound(file))
        } finally { opened.db.close(); dir.deleteRecursively() }
    }

    @Test
    fun `a damaged book is set aside and rebuilt from the newest good copy`() {
        val dir = Files.createTempDirectory("rk-health").toFile()
        val file = dir.resolve("khata.db")
        val daily = dir.resolve("daily")
        val settings = FileShopSettings(dir.resolve("shop.properties"))
        settings.setBusinessName("Bhatti Traders")
        // A book with one customer, and today's copy of it.
        val db = BookHealth.open(file, settings, listOf(daily)).db
        runBlocking {
            val id = db.khataDao().insertParty(Party(name = "Bilal", phone = "0300 1234567", isCustomer = true))
            db.khataDao().insertEntryWithItems(LedgerEntry(partyId = id, amount = 1550.0, isGiven = true, note = "urea", entryNumber = ""), emptyList())
            DailyCopies.makeToday(db.khataDao(), settings, daily)
        }
        db.close()
        assertEquals(1, DailyCopies.list(daily).size)
        // Then the disk eats the middle of the file.
        RandomAccessFile(file, "rw").use { f ->
            f.seek(100)
            f.write(ByteArray(2000) { 0x5A })
        }
        assertFalse(BookHealth.isSound(file))
        val opened = BookHealth.open(file, settings, listOf(daily))
        try {
            assertNotNull(opened.corruptKept)
            assertTrue(opened.corruptKept!!.isFile)
            assertFalse(opened.nothingToRecover)
            assertEquals(DailyCopies.list(daily).single(), opened.recoveredFrom)
            assertTrue(BookHealth.isSound(file))
            val parties = runBlocking { opened.db.khataDao().partiesWithBalanceOnce() }
            assertEquals("Bilal", parties.single().name)
            assertEquals(1550.0, parties.single().balance, 0.001)
        } finally { opened.db.close(); dir.deleteRecursively() }
    }

    @Test
    fun `a damaged book with no copy starts empty and says so`() {
        val dir = Files.createTempDirectory("rk-health").toFile()
        val file = dir.resolve("khata.db")
        file.writeBytes(ByteArray(8000) { 0x5A })
        val opened = BookHealth.open(file, FileShopSettings(dir.resolve("shop.properties")), listOf(dir.resolve("daily")))
        try {
            assertTrue(opened.nothingToRecover)
            assertNotNull(opened.corruptKept)
            assertEquals(0, runBlocking { opened.db.khataDao().partiesWithBalanceOnce() }.size)
        } finally { opened.db.close(); dir.deleteRecursively() }
    }

    @Test
    fun `a version is newer only when a part is larger`() {
        assertTrue(UpdateCheck.isNewer("1.9.0", "1.8.4"))
        assertTrue(UpdateCheck.isNewer("1.8.10", "1.8.9"))
        assertFalse(UpdateCheck.isNewer("1.8.4", "1.8.4"))
        assertFalse(UpdateCheck.isNewer("1.8.3", "1.8.4"))
        assertEquals("1.9.0", UpdateCheck.versionIn("""{"version": "1.9.0", "commit": "abc"}"""))
        assertNull(UpdateCheck.versionIn("not json"))
    }
}
