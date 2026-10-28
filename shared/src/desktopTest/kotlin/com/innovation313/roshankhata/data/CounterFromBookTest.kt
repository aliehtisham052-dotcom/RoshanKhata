package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/**
 * Counter mode is the book's word too (21 Oct 2026, the critical review): a
 * counter computer whose settings file is edited or deleted stays a counter;
 * a device the owner has made theirs (counter mode off with the PIN) does not.
 */
class CounterFromBookTest {

    private class Prefs : Access.DeviceStore {
        val map = HashMap<String, String>()
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String?) { if (value == null) map.remove(key) else map[key] = value }
    }

    @After fun forget() = Access.resetForTest()

    @Test
    fun `a counter whose settings file is wiped is still a counter`() {
        val dir = Files.createTempDirectory("rk-counter").toFile()
        val db = DesktopDatabase.open(dir.resolve("khata.db"))
        try {
            runBlocking {
                val dao = db.khataDao()
                // The owner makes this computer a counter.
                Access.store = Prefs()
                Access.switchCounterMode(true)
                Access.recordCounterMode(dao, true)
                // Someone deletes app.properties: a fresh store, no counter flag, a new device id.
                Access.store = Prefs()
                assertFalse(Access.counterMode)
                assertTrue("the book turns it back on", Access.applyBook(dao))
                assertTrue(Access.counterMode)
                assertTrue(Access.locked)
            }
        } finally { db.close(); dir.deleteRecursively() }
    }

    @Test
    fun `the owner's own device stays the owner's, and a book that never had a counter changes nothing`() {
        val dir = Files.createTempDirectory("rk-counter").toFile()
        val db = DesktopDatabase.open(dir.resolve("khata.db"))
        try {
            runBlocking {
                val dao = db.khataDao()
                val owners = Prefs()
                Access.store = owners
                assertFalse("no counter ever: nothing to do", Access.applyBook(dao))
                assertFalse(Access.counterMode)
                // A counter elsewhere on this book; this device then turned off with the owner's PIN.
                Access.switchCounterMode(true); Access.recordCounterMode(dao, true)
                Access.switchCounterMode(false); Access.recordCounterMode(dao, false)
                Access.store = owners
                assertFalse(Access.applyBook(dao))
                assertFalse(Access.counterMode)
                assertTrue(Access.isOwner)
            }
        } finally { db.close(); dir.deleteRecursively() }
    }
}
