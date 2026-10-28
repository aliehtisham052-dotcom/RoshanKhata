package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/**
 * Each device's own letter in document numbers while a book is kept live
 * (22 Oct 2026): two devices apart never hand out the same number, and the
 * series still runs forward across both.
 */
class NumberMarkTest {

    @After
    fun clear() { NumberMark.mark = null }

    @Test
    fun `the book's maker is A, a joiner takes the letter it was given, else never A`() = runBlocking {
        val dir = Files.createTempDirectory("rk-mark").toFile()
        val phone = DesktopDatabase.open(dir.resolve("phone.db"))
        val pc = DesktopDatabase.open(dir.resolve("pc.db"))
        val pc2 = DesktopDatabase.open(dir.resolve("pc2.db"))
        try {
            val id = LiveSync.enable(phone.syncSql(), "install-phone", "Phone")
            assertEquals("A", LiveSync.mark(phone.syncSql()))
            val given = LiveSync.nextJoinerMark(phone.syncSql())
            assertEquals("B", given)
            assertEquals("C", LiveSync.nextJoinerMark(phone.syncSql()))
            LiveSync.enable(pc.syncSql(), "install-pc", "PC", adopt = id.book to id.key, mark = given)
            assertEquals("B", LiveSync.mark(pc.syncSql()))
            // An older phone sent no letter: one from the computer's own id, never the maker's A.
            LiveSync.enable(pc2.syncSql(), "install-pc2", "PC 2", adopt = id.book to id.key)
            val own = LiveSync.mark(pc2.syncSql())!!
            assertTrue(own, LiveSync.validMark(own))
            assertNotEquals("A", own)
            // Off: no letter.
            LiveSync.disable(pc.syncSql())
            assertNull(LiveSync.mark(pc.syncSql()))
        } finally {
            phone.close(); pc.close(); pc2.close(); dir.deleteRecursively()
        }
    }

    @Test
    fun `a whole book file copied to another device does not keep the maker's A`() = runBlocking {
        val dir = Files.createTempDirectory("rk-mark-copy").toFile()
        val db = DesktopDatabase.open(dir.resolve("book.db"))
        try {
            LiveSync.enable(db.syncSql(), "install-1", "Phone")
            assertEquals("A", LiveSync.mark(db.syncSql()))
            LiveSync.enable(db.syncSql(), "install-2", "Other phone")
            assertNotEquals("A", LiveSync.mark(db.syncSql()))
        } finally {
            db.close(); dir.deleteRecursively()
        }
    }

    @Test
    fun `numbers carry the letter and keep counting across devices`() = runBlocking {
        assertEquals("S-000013", SaleNumber.next(12))
        NumberMark.mark = "B"
        assertEquals("S-000013-B", SaleNumber.next(12))
        assertEquals("R-000004-B", ReturnNumber.next(3))
        assertEquals("PO-000008-B", OrderNumber.next(7))
        assertEquals("INV-000010-B", InvoiceNumber.next(9))
        assertEquals("RK-000002-B", EntryNumber.next(1))

        val dir = Files.createTempDirectory("rk-mark-num").toFile()
        val db = DesktopDatabase.open(dir.resolve("book.db"))
        try {
            val d = db.khataDao()
            val ali = d.insertParty(Party(name = "Ali"))
            NumberMark.mark = "A"
            val first = d.insertEntryWithItems(LedgerEntry(partyId = ali, amount = 100.0, isGiven = true, entryNumber = ""), emptyList())
            NumberMark.mark = "B"
            val second = d.insertEntryWithItems(LedgerEntry(partyId = ali, amount = 50.0, isGiven = false, entryNumber = ""), emptyList())
            NumberMark.mark = null
            val third = d.insertEntryWithItems(LedgerEntry(partyId = ali, amount = 25.0, isGiven = false, entryNumber = ""), emptyList())
            val numbers = d.allEntriesForBackup().associate { it.id to it.entryNumber }
            assertEquals("RK-000001-A", numbers[first])
            // The other device's lettered number still counts as 1: the series runs on.
            assertEquals("RK-000002-B", numbers[second])
            assertEquals("RK-000003", numbers[third])
            assertEquals(3, d.highestEntryNumber())
        } finally {
            db.close(); dir.deleteRecursively()
        }
    }
}
