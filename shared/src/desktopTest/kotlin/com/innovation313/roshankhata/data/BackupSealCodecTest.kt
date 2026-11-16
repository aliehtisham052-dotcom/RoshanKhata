package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * A real book through a locked backup (17 Nov 2026, [BackupSeal]): written locked, read with its password and restored
 * into another book, it is the same book (LiveSync's fingerprint); without the password, with a wrong one, changed or
 * cut short, nothing is read and nothing would be restored; an old plain backup still reads as it always did.
 * Made-up names and figures only.
 */
class BackupSealCodecTest {
    private lateinit var dir: File
    private lateinit var here: KhataDatabase
    private val pw = "Bashir Ahmad 2026"
    private val rounds = BackupSeal.MIN_ITERATIONS

    @Before fun open() {
        dir = Files.createTempDirectory("rk-seal").toFile()
        here = DesktopDatabase.open(dir.resolve("here.db"))
        runBlocking {
            val dao = here.khataDao()
            // Enough lines that the book spans several 64 KiB records.
            for (p in 0 until 40) {
                val id = dao.insertParty(Party(name = "Ali Raza $p", isCustomer = true, note = "علی رضا $p"))
                for (e in 0 until 30) {
                    dao.insertEntryWithItems(LedgerEntry(partyId = id, amount = 100.0 + e, isGiven = e % 2 == 0, entryNumber = "", note = "Urea bag $e for Tariq Butt"), emptyList())
                }
            }
            dao.insertCashEntry(CashEntry(amount = 300.0, isIncome = true, category = CashCategory.SALE))
        }
    }

    @After fun close() { here.close(); dir.deleteRecursively() }

    private fun export(file: File, password: String?) = runBlocking {
        BackupSeal.writer(file.outputStream(), password, rounds).use { BackupCodec.exportTo(here.khataDao(), "Shop", JSONObject(), it) }
        file
    }

    @Test fun `a locked backup restores to the same book, and reads as the plain one does`() = runBlocking {
        val plain = export(dir.resolve("plain.txt"), null)
        val locked = export(dir.resolve("locked.txt"), pw)
        assertTrue(locked.length() > 3 * BackupSeal.CHUNK)
        assertTrue(BackupSeal.isSealed(locked))
        assertTrue(!BackupSeal.isSealed(plain))
        val (r, data) = BackupCodec.parseFile(locked, listOf("not it at all", pw))
        assertTrue("$r", r is BackupCodec.ImportResult.Ok)
        assertEquals(BackupCodec.parseFile(plain).first, r)
        val there = DesktopDatabase.open(dir.resolve("there.db"))
        try {
            BackupCodec.restoreLedger(there.khataDao(), data!!)
            assertEquals(LiveSync.fingerprint(here.syncSql()), LiveSync.fingerprint(there.syncSql()))
        } finally { there.close() }
    }

    @Test fun `without the password, or with a wrong one, nothing is read`() {
        val locked = export(dir.resolve("locked.txt"), pw)
        val none = BackupCodec.parseFile(locked)
        assertEquals(BackupCodec.ImportResult.Why.LOCKED, (none.first as BackupCodec.ImportResult.Failed).why)
        assertNull(none.second)
        val wrong = BackupCodec.parseFile(locked, listOf("Tariq Butt 300"))
        assertEquals(BackupCodec.ImportResult.Why.WRONG_PASSWORD, (wrong.first as BackupCodec.ImportResult.Failed).why)
        assertNull(wrong.second)
    }

    @Test fun `a changed byte or a cut file is unreadable, never a book read in part`() {
        val locked = export(dir.resolve("locked.txt"), pw)
        val bytes = locked.readBytes()
        for (at in listOf(bytes.size / 2, bytes.size - 3)) {
            val bad = bytes.copyOf(); bad[at] = (bad[at].toInt() xor 0x40).toByte()
            val f = dir.resolve("bad-$at.txt").apply { writeBytes(bad) }
            val (r, d) = BackupCodec.parseFile(f, listOf(pw))
            assertEquals("byte $at", BackupCodec.ImportResult.Why.UNREADABLE, (r as BackupCodec.ImportResult.Failed).why)
            assertNull(d)
        }
        // Cut where the text inside still looks whole to a reader stopping early: only the lock's end says it is not.
        val cut = dir.resolve("cut.txt").apply { writeBytes(bytes.copyOf(bytes.size - 1)) }
        assertEquals(BackupCodec.ImportResult.Why.UNREADABLE, (BackupCodec.parseFile(cut, listOf(pw)).first as BackupCodec.ImportResult.Failed).why)
        val added = dir.resolve("added.txt").apply { writeBytes(bytes + byteArrayOf(0x20)) }
        assertEquals(BackupCodec.ImportResult.Why.UNREADABLE, (BackupCodec.parseFile(added, listOf(pw)).first as BackupCodec.ImportResult.Failed).why)
    }

    @Test fun `an old plain backup still reads, whatever passwords are offered`() {
        val f = dir.resolve("v7.txt").apply { writeText(BackupFixtures.v7) }
        assertTrue(BackupCodec.parseFile(f).first is BackupCodec.ImportResult.Ok)
        assertTrue(BackupCodec.parseFile(f, listOf(pw)).first is BackupCodec.ImportResult.Ok)
        assertTrue(BackupCodec.parseText(BackupFixtures.v7).first is BackupCodec.ImportResult.Ok)
        // A stream that cannot mark is wrapped, not refused.
        val noMark = object : java.io.FilterInputStream(f.inputStream()) { override fun markSupported() = false }
        assertTrue(noMark.use { BackupCodec.parseStream(it, listOf(pw)) }.first is BackupCodec.ImportResult.Ok)
    }

    @Test fun `a locked backup through a stream that cannot mark opens too`() {
        val locked = export(dir.resolve("locked.txt"), pw)
        val noMark = object : java.io.FilterInputStream(locked.inputStream()) { override fun markSupported() = false }
        assertTrue(noMark.use { BackupCodec.parseStream(it, listOf(pw)) }.first is BackupCodec.ImportResult.Ok)
    }
}
