package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.BackupCodec
import com.innovation313.roshankhata.data.BackupPassword
import com.innovation313.roshankhata.data.BackupSeal
import com.innovation313.roshankhata.data.DesktopDatabase
import com.innovation313.roshankhata.data.FileShopSettings
import com.innovation313.roshankhata.data.LiveSync
import com.innovation313.roshankhata.data.Party
import com.innovation313.roshankhata.data.syncSql
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/**
 * The computer's paths for the backup password (17 Nov 2026): a daily copy written for the outside folder is locked
 * and reads back only with the password, one written without stays plain as before; a computer with no lock keeps no
 * password at all (never a plain one); a locked file read with nobody to ask says it is locked, it does not wait.
 */
class BackupLockDesktopTest {
    private val pw = "Ali Raza 2026"

    @Test fun `a copy for the outside folder is locked, reads back with its password and is the same book`() {
        val home = Files.createTempDirectory("rk-lock").toFile()
        val db = DesktopDatabase.open(home.resolve("khata.db"))
        try {
            val settings = FileShopSettings(home.resolve("shop.properties"))
            val outside = home.resolve("usb/RoshanKhata-Backup/Business-1 (PC)")
            val own = DailyCopies.dir(home, 1)
            val copy = runBlocking {
                db.khataDao().insertParty(Party(name = "Bashir Ahmad", phone = null, isCustomer = true))
                DailyCopies.makeToday(db.khataDao(), settings, own)
                DailyCopies.makeToday(db.khataDao(), settings, outside, refresh = true, password = pw)
            }!!
            assertTrue("the outside copy is locked", BackupSeal.isSealed(copy))
            assertFalse("this computer's own copy stays plain", BackupSeal.isSealed(DailyCopies.list(own).single()))
            assertEquals(BackupCodec.ImportResult.Why.LOCKED, (BackupCodec.parseFile(copy).first as BackupCodec.ImportResult.Failed).why)
            val (r, data) = BackupCodec.parseFile(copy, listOf(pw))
            assertTrue("$r", r is BackupCodec.ImportResult.Ok)
            val there = DesktopDatabase.open(home.resolve("there.db"))
            try {
                runBlocking {
                    BackupCodec.restoreLedger(there.khataDao(), data!!)
                    assertEquals(LiveSync.fingerprint(db.syncSql()), LiveSync.fingerprint(there.syncSql()))
                }
            } finally { there.close() }
        } finally {
            db.close(); home.deleteRecursively()
        }
    }

    @Test fun `a computer with no lock keeps no password, so nothing is ever locked with a plain one`() {
        val home = Files.createTempDirectory("rk-lock").toFile()
        try {
            // Windows always has DPAPI; the Linux test machine has no lock (KeyVault.wrap is null).
            if (System.getProperty("os.name").orEmpty().lowercase().startsWith("windows")) return
            assertEquals(BackupPassword.Saved.NO_LOCK, DeskBackupPassword.save(pw, home))
            assertFalse(DeskBackupPassword.file(home).exists())
            assertTrue(DeskBackupPassword.state(home) is BackupPassword.State.None)
            assertNull(DeskBackupPassword.forSealing(home))
            assertEquals(emptyList<String>(), DeskBackupPassword.toTry(null, home))
        } finally { home.deleteRecursively() }
    }

    @Test fun `a kept password the lock cannot read stops the outside copy instead of writing it plain`() {
        val home = Files.createTempDirectory("rk-lock").toFile()
        try {
            // What a book carried from another Windows user or computer would hold: a kept password this lock refuses.
            DeskBackupPassword.file(home).writeText("p1:0011223344556677:AAAA")
            assertTrue(DeskBackupPassword.state(home) is BackupPassword.State.Unreadable)
            try { DeskBackupPassword.forSealing(home); throw AssertionError("sealed with nothing") } catch (e: BackupPassword.Unavailable) { }
        } finally { home.deleteRecursively() }
    }

    /** Answers every question the prompt asks with [answer] while [block] runs; how many were asked. */
    private fun answering(answer: String?, block: suspend () -> Unit): Int = runBlocking {
        var asks = 0
        BackupPasswordPrompt.hosts++
        val answerer = launch {
            while (true) {
                val a = BackupPasswordPrompt.asking.value
                if (a != null && !a.answer.isCompleted) { asks++; a.answer.complete(answer) }
                delay(5)
            }
        }
        try { block() } finally { answerer.cancel(); BackupPasswordPrompt.hosts-- }
        asks
    }

    @Test fun `three wrong passwords end the asking with a clear refusal, a cancel is locked`() {
        val home = Files.createTempDirectory("rk-lock").toFile()
        try {
            val f = home.resolve("locked.txt")
            BackupSeal.writer(f.outputStream(), pw, BackupSeal.MIN_ITERATIONS).use { it.write("{\"format\": \"RoshanKhata\"}") }
            var got: BackupCodec.ImportResult? = null
            val asks = answering("Tariq Butt 300") { got = readBackup(f, home).first }
            assertEquals(BackupPassword.MAX_TRIES, asks)
            assertEquals(BackupCodec.ImportResult.Why.WRONG_PASSWORD, (got as BackupCodec.ImportResult.Failed).why)
            val cancelled = answering(null) { got = readBackup(f, home).first }
            assertEquals(1, cancelled)
            assertEquals(BackupCodec.ImportResult.Why.LOCKED, (got as BackupCodec.ImportResult.Failed).why)
            // The right one, typed after a wrong one, opens it (the text inside is no book: it is refused for that, not locked).
            var tries = 0
            BackupPasswordPrompt.hosts++
            try {
                runBlocking {
                    val answerer = launch {
                        while (true) {
                            val a = BackupPasswordPrompt.asking.value
                            if (a != null && !a.answer.isCompleted) { a.answer.complete(if (tries++ == 0) "Tariq Butt 300" else pw) }
                            delay(5)
                        }
                    }
                    got = readBackup(f, home).first
                    answerer.cancel()
                }
            } finally { BackupPasswordPrompt.hosts-- }
            assertEquals(2, tries)
            val why = (got as BackupCodec.ImportResult.Failed).why
            assertTrue(why != BackupCodec.ImportResult.Why.LOCKED && why != BackupCodec.ImportResult.Why.WRONG_PASSWORD)
        } finally { home.deleteRecursively() }
    }

    @Test fun `a locked file read with no window to ask says locked and does not wait`() {
        val home = Files.createTempDirectory("rk-lock").toFile()
        try {
            val f = home.resolve("locked.txt")
            BackupSeal.writer(f.outputStream(), pw, BackupSeal.MIN_ITERATIONS).use { it.write("{\"format\": \"RoshanKhata\"}") }
            val (r, d) = runBlocking { readBackup(f, home) }
            assertEquals(BackupCodec.ImportResult.Why.LOCKED, (r as BackupCodec.ImportResult.Failed).why)
            assertNull(d)
        } finally { home.deleteRecursively() }
    }
}
