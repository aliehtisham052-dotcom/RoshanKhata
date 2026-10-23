package com.innovation313.roshankhata.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/** The registry keeps the phone's rules: Business 1 always there, never deleted; the open one never deleted. */
class BusinessesTest {
    @Test
    fun `create, switch, survive a restart, delete only what may go`() {
        val home = Files.createTempDirectory("rk-biz").toFile()
        try {
            val b = Businesses(home)
            assertEquals(listOf(1L), b.list().map { it.id })
            assertEquals(1L, b.activeId())
            val shop2 = b.create("Bhatti Seeds")
            assertEquals(2L, shop2.id)
            assertEquals("khata_b2.db", b.dbFile(2).name)
            assertEquals("khata.db", b.dbFile(1).name)
            b.switchTo(2)
            val again = Businesses(home)
            assertEquals(2L, again.activeId())
            assertEquals("Bhatti Seeds", again.list().last().name)
            assertFalse("Business 1 is never deleted", again.canDelete(1))
            assertFalse("the open business is never deleted", again.canDelete(2))
            again.switchTo(1)
            assertTrue(again.canDelete(2))
            again.dbFile(2).writeText("x")
            again.delete(2)
            assertFalse(again.dbFile(2).exists())
            assertEquals(listOf(1L), Businesses(home).list().map { it.id })
        } finally {
            home.deleteRecursively()
        }
    }

    /**
     * R04/R43 (23 Oct 2026): a deleted business's number is never given out again, and nothing that was
     * that shop's by its number is left for another to pick up.
     */
    @Test
    fun `a deleted business number and its files are never reused`() {
        val home = Files.createTempDirectory("rk-biz-reuse").toFile()
        try {
            val b = Businesses(home)
            assertEquals(2L, b.create("Shop Two").id)
            // What shop 2 leaves on this computer, by its number.
            b.dbFile(2).writeText("x")
            b.settingsFile(2).writeText("businessName=Shop Two")
            java.io.File(home, "card_b2.properties").writeText("x")
            java.io.File(home, "poster_b2.properties").writeText("x")
            java.io.File(DailyCopies.dir(home, 2), "2026-10-23.txt").apply { parentFile.mkdirs(); writeText("x") }
            java.io.File(home, "profile/b2/logo.png").apply { parentFile.mkdirs(); writeText("x") }
            java.io.File(Photos.partyDir(home, 2), "party_5.jpg").apply { parentFile.mkdirs(); writeText("x") }
            java.io.File(home, "khata_b2.db.corrupt-2026-10-01_101010").writeText("x")
            b.delete(2)
            for (name in listOf("khata_b2.db", "shop_b2.properties", "card_b2.properties", "poster_b2.properties",
                    "daily/b2", "profile/b2", "party_photos_b2", "khata_b2.db.corrupt-2026-10-01_101010")) {
                assertFalse("$name is left behind", java.io.File(home, name).exists())
            }
            assertEquals("the next business takes a new number", 3L, b.create("Shop Three").id)
            assertEquals("also after a restart", 4L, Businesses(home).create("Shop Four").id)
        } finally {
            home.deleteRecursively()
        }
    }

    @Test
    fun `files left by a business deleted before the highest number was kept move the next number past them`() {
        val home = Files.createTempDirectory("rk-biz-leftover").toFile()
        try {
            java.io.File(home, "profile/b7").mkdirs()
            java.io.File(home, "poster_b5.properties").writeText("x")
            assertEquals(7L, Businesses(home).leftoverMax())
            assertEquals(8L, Businesses(home).create("New").id)
        } finally {
            home.deleteRecursively()
        }
    }

    /** R26: party ids start at 1 in every book, so each business has its own folder of faces. */
    @Test
    fun `customer photos are kept per business`() {
        val home = java.io.File("home")
        assertEquals(java.io.File(home, "party_photos/party_5.jpg"), Photos.partyFile(home, 1, 5))
        assertEquals(java.io.File(home, "party_photos_b2/party_5.jpg"), Photos.partyFile(home, 2, 5))
        assertTrue(Photos.partyFile(home, 2, 5) != Photos.partyFile(home, 3, 5))
    }

    /** R29: the business that keeps a live book is found, never the one asking. */
    @Test
    fun `the business keeping a live book is named`() {
        val home = Files.createTempDirectory("rk-biz-book").toFile()
        try {
            val b = Businesses(home)
            b.create("Two")
            b.setBook(1, "book-x")
            assertEquals(1L, b.holderOf("book-x", except = 2))
            assertEquals(null, b.holderOf("book-x", except = 1))
            assertEquals(null, b.holderOf("book-y", except = 2))
            assertEquals("kept across a restart", 1L, Businesses(home).holderOf("book-x", except = 2))
            b.setBook(1, null)
            assertEquals(null, Businesses(home).holderOf("book-x", except = 2))
        } finally {
            home.deleteRecursively()
        }
    }
}
