package com.innovation313.roshankhata.desktop

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.innovation313.roshankhata.data.FileShopSettings
import com.innovation313.roshankhata.data.KHATA_DB_VERSION
import com.innovation313.roshankhata.data.LedgerEntry
import com.innovation313.roshankhata.data.Party
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files

/**
 * The computer's book swaps (BC-258, 24 Nov 2026; the phone's SnapshotSwap order): a book is moved with its log as one
 * thing, all or nothing; a book from a newer build is refused before anything is touched; a rebuild whose profile
 * cannot be written is still a rebuilt book. Made-up names and figures only.
 */
class BookSwapSafetyTest {

    private fun temp(): File = Files.createTempDirectory("rk-swap").toFile()

    @Test
    fun `a book moves with its log and shared index, and nothing is left behind`() {
        val dir = temp()
        try {
            val from = dir.resolve("khata.db"); val to = dir.resolve("khata.db.corrupt-x")
            from.writeText("main"); File(from.path + "-wal").writeText("log"); File(from.path + "-shm").writeText("shm")
            assertTrue(BookHealth.moveBook(from, to))
            assertEquals("main", to.readText()); assertEquals("log", File(to.path + "-wal").readText()); assertEquals("shm", File(to.path + "-shm").readText())
            assertFalse(from.exists()); assertFalse(File(from.path + "-wal").exists()); assertFalse(File(from.path + "-shm").exists())
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun `the log goes before the book and the book goes last`() {
        val dir = temp()
        try {
            val from = dir.resolve("khata.db"); val to = dir.resolve("kept")
            from.writeText("main"); File(from.path + "-wal").writeText("log")
            val order = ArrayList<String>()
            assertTrue(BookHealth.moveBook(from, to, { a, b -> order += a.name; Files.move(a.toPath(), b.toPath()) }))
            assertEquals(listOf("khata.db-wal", "khata.db"), order)
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun `a book that will not move leaves the book and its log exactly where they were`() {
        val dir = temp()
        try {
            val from = dir.resolve("khata.db"); val to = dir.resolve("kept")
            from.writeText("main"); File(from.path + "-wal").writeText("log"); File(from.path + "-shm").writeText("shm")
            // The main file's rename is refused (Windows: another program holds it): the log that was moved goes back.
            val done = BookHealth.moveBook(from, to, { a, b -> if (a == from) throw java.io.IOException("in use") else Files.move(a.toPath(), b.toPath()) })
            assertFalse(done)
            assertEquals("main", from.readText()); assertEquals("log", File(from.path + "-wal").readText()); assertEquals("shm", File(from.path + "-shm").readText())
            assertFalse(to.exists()); assertFalse(File(to.path + "-wal").exists()); assertFalse(File(to.path + "-shm").exists())
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun `a log that will not move leaves the book where it was, not a new file beside an old log`() {
        val dir = temp()
        try {
            val from = dir.resolve("khata.db"); val to = dir.resolve("kept")
            from.writeText("main"); File(from.path + "-wal").writeText("log")
            val done = BookHealth.moveBook(from, to, { a, b -> if (a.name.endsWith("-wal")) throw java.io.IOException("in use") else Files.move(a.toPath(), b.toPath()) })
            assertFalse(done)
            assertTrue(from.exists()); assertEquals("log", File(from.path + "-wal").readText()); assertFalse(to.exists())
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun `a stale log at the target is never left beside the book that arrives`() {
        val dir = temp()
        try {
            val from = dir.resolve("khata.db.recovering"); val to = dir.resolve("khata.db")
            from.writeText("rebuilt"); File(to.path + "-wal").writeText("the old book's log")
            assertTrue(BookHealth.moveBook(from, to))
            assertEquals("rebuilt", to.readText()); assertFalse(File(to.path + "-wal").exists())
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun `a book from a newer build is refused before anything is touched`() {
        val dir = temp()
        try {
            val file = dir.resolve("khata.db")
            val settings = FileShopSettings(dir.resolve("shop.properties"))
            BookHealth.open(file, settings, listOf(dir.resolve("daily"))).db.close()
            setUserVersion(file, KHATA_DB_VERSION + 1)
            assertEquals(KHATA_DB_VERSION + 1, BookHealth.userVersionOf(file))
            assertFalse(BookHealth.canOpen(KHATA_DB_VERSION + 1)); assertTrue(BookHealth.canOpen(KHATA_DB_VERSION)); assertTrue(BookHealth.canOpen(0))
            val before = file.readBytes(); val names = dir.list()!!.sorted()
            try { BookHealth.open(file, settings, listOf(dir.resolve("daily"))); fail("a newer book was opened") }
            catch (e: BookHealth.CannotOpen) { assertTrue(e.cause is BookHealth.NewerBook) }
            assertArrayEquals("the book is as it was", before, file.readBytes())
            assertEquals("nothing was made or set aside", names, dir.list()!!.sorted())
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun `a rebuild killed between its two moves is finished on the next opening`() {
        val dir = temp()
        try {
            val file = dir.resolve("khata.db"); val daily = dir.resolve("daily")
            val settings = FileShopSettings(dir.resolve("shop.properties"))
            val db = BookHealth.open(file, settings, listOf(daily)).db
            runBlocking { db.khataDao().insertParty(Party(name = "Bilal", phone = null, isCustomer = true)) }
            db.close()
            // The state a kill leaves: the damaged book kept, the finished rebuild waiting, no book.
            Files.move(file.toPath(), File(file.path + ".recovering").toPath())
            File(file.path + ".corrupt-2026-11-24_100000").writeBytes(ByteArray(300) { 0x5A })
            assertTrue(BookHealth.finishInterrupted(file))
            assertTrue(file.isFile); assertFalse(File(file.path + ".recovering").exists())
            val opened = BookHealth.open(file, settings, listOf(daily))
            try { assertEquals("Bilal", runBlocking { opened.db.khataDao().partiesWithBalanceOnce() }.single().name) } finally { opened.db.close() }
            // No kept book, or a rebuild that is not sound: nothing is put in place.
            assertFalse(BookHealth.finishInterrupted(dir.resolve("other.db")))
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun `a rebuilt book whose profile cannot be written is still the rebuilt book`() {
        val dir = temp()
        try {
            val file = dir.resolve("khata.db"); val daily = dir.resolve("daily")
            val settings = FileShopSettings(dir.resolve("shop.properties"))
            settings.setBusinessName("Al-Noor Seeds")
            val db = BookHealth.open(file, settings, listOf(daily)).db
            runBlocking {
                val id = db.khataDao().insertParty(Party(name = "Bilal", phone = "0300 1234567", isCustomer = true))
                db.khataDao().insertEntryWithItems(LedgerEntry(partyId = id, amount = 1550.0, isGiven = true, note = "urea", entryNumber = ""), emptyList())
                DailyCopies.makeToday(db.khataDao(), settings, daily)
            }
            db.close()
            RandomAccessFile(file, "rw").use { f -> f.seek(100); f.write(ByteArray(2000) { 0x5A }) }
            // The profile's file cannot be replaced: its temporary name is a folder.
            val blocked = FileShopSettings(dir.resolve("blocked.properties"))
            assertTrue(File(dir, "blocked.properties.tmp").mkdirs())
            val opened = BookHealth.open(file, blocked, listOf(daily))
            try {
                assertNotNull(opened.corruptKept); assertNotNull(opened.recoveredFrom)
                assertTrue("the owner can be told", opened.profileNotSet)
                assertEquals(1550.0, runBlocking { opened.db.khataDao().partiesWithBalanceOnce() }.single().balance, 0.001)
            } finally { opened.db.close() }
        } finally { dir.deleteRecursively() }
    }

    private fun setUserVersion(file: File, v: Int) {
        val c = BundledSQLiteDriver().open(file.absolutePath)
        try {
            val s = c.prepare("PRAGMA user_version = $v")
            try { s.step() } finally { s.close() }
        } finally { c.close() }
    }
}
