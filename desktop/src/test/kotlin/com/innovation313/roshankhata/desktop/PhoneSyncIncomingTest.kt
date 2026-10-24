package com.innovation313.roshankhata.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/** R41: a book from a phone is the whole book in plain text; it does not stay in from-phone once answered. */
class PhoneSyncIncomingTest {

    @Test
    fun `only the newest arrival is kept`() {
        val home = Files.createTempDirectory("rk-incoming").toFile()
        try {
            val first = PhoneSync.saveIncoming(home, "one".toByteArray())
            Thread.sleep(1100) // the name carries the second
            val second = PhoneSync.saveIncoming(home, "two".toByteArray())
            assertFalse(first.exists())
            assertTrue(second.exists())
            assertEquals("two", second.readText())
        } finally {
            home.deleteRecursively()
        }
    }

    @Test
    fun `an answered arrival is removed but a chosen backup never is`() {
        val home = Files.createTempDirectory("rk-incoming").toFile()
        try {
            val arrived = PhoneSync.saveIncoming(home, "book".toByteArray())
            val chosen = home.resolve("Documents").apply { mkdirs() }.resolve("RoshanKhata_FromPhone_kept.txt")
            chosen.writeText("the owner's own file")
            PhoneSync.forgetIncoming(home, chosen)
            assertTrue(chosen.exists())
            PhoneSync.forgetIncoming(home, arrived)
            assertFalse(arrived.exists())
        } finally {
            home.deleteRecursively()
        }
    }

    @Test
    fun `opening clears arrivals nobody answered and leaves other files`() {
        val home = Files.createTempDirectory("rk-incoming").toFile()
        try {
            val arrived = PhoneSync.saveIncoming(home, "book".toByteArray())
            val half = arrived.resolveSibling(arrived.name + ".tmp").apply { writeText("half") }
            val other = arrived.resolveSibling("notes.txt").apply { writeText("not a book") }
            PhoneSync.clearIncoming(home)
            assertFalse(arrived.exists())
            assertFalse(half.exists())
            assertTrue(other.exists())
        } finally {
            home.deleteRecursively()
        }
    }
}
