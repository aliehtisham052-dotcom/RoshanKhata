package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The staff list as the assistant reads it (16 Nov 2026, audit T25): [KhataDao.staffCardsOnce] gives every staff row,
 * removed ones too, with what the Staff screen shows — and never the PIN's hash or salt, which only the backup reads
 * ([KhataDao.allStaffForBackup]).
 */
class StaffCardsTest {

    private lateinit var dir: File
    private val books = ArrayList<KhataDatabase>()

    @Before
    fun open() {
        dir = Files.createTempDirectory("rk-staff-cards").toFile()
    }

    @After
    fun close() {
        books.forEach { it.close() }
        dir.deleteRecursively()
    }

    private fun book(): KhataDao = DesktopDatabase.open(dir.resolve("book.db")).also { books += it }.khataDao()

    @Test
    fun everyStaffRowComesWithWhatTheScreenShowsAndNoPinKeys() = runBlocking {
        val dao = book()
        dao.insertStaff(Staff(name = "Tariq Butt", phone = "0300 1234567", monthlySalary = 25000.0, role = Role.CASHIER,
            pinHash = "made-up-hash", pinSalt = "made-up-salt", createdAt = 1_700_000_000_000L))
        dao.insertStaff(Staff(name = "Bashir Ahmad", monthlySalary = 18000.0, isActive = false, createdAt = 1_700_000_100_000L))
        val full = dao.allStaffForBackup()
        val cards = dao.staffCardsOnce()
        assertEquals(full.size, cards.size)
        for ((s, c) in full.zip(cards)) {
            assertEquals(s.id, c.id)
            assertEquals(s.name, c.name)
            assertEquals(s.phone, c.phone)
            assertEquals(s.monthlySalary, c.monthlySalary, 0.0)
            assertEquals(s.isActive, c.isActive)
            assertEquals(s.createdAt, c.createdAt)
            assertEquals(s.role, c.role)
        }
        // The card has no field for the PIN's keys at all, so none can be shown: its text carries neither.
        val said = cards.joinToString { it.toString() }
        assertFalse(said.contains("made-up-hash"))
        assertFalse(said.contains("made-up-salt"))
    }
}
