package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.BackupCodec
import com.innovation313.roshankhata.data.BackupPassword
import com.innovation313.roshankhata.data.BackupProtection
import com.innovation313.roshankhata.data.BackupProtection.Way
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
 * The computer's side of the hybrid backup protection (25 Nov 2026, D14): which way is locked by default, the switch and
 * the hint kept in a small file of their own, the first-use question, the hint shown with the restore question, and a
 * wrong password changing nothing. (CI only: the Compose host and DPAPI are not exercised here; the Linux test machine
 * has no lock, so a password is never kept there.)
 */
class BackupProtectionDesktopTest {
    private val pw = "Ali Raza 2026"

    private fun home() = Files.createTempDirectory("rk-prot").toFile()
    private val isWindows get() = System.getProperty("os.name").orEmpty().lowercase().startsWith("windows")

    @Test fun `with no password every file that leaves the computer waits for a first one, the computer's own copies do not`() {
        val home = home()
        try {
            assertTrue(DeskBackupPassword.filesProtected(home))
            for (way in listOf(Way.BACK_UP_NOW, Way.OUTSIDE_FOLDER, Way.FOR_PHONE)) {
                assertEquals(way.name, BackupProtection.Plan.NeedFirstPassword, DeskBackupPassword.plan(way, home))
            }
            assertEquals(BackupProtection.Plan.Plain, DeskBackupPassword.plan(Way.DAILY_COPY, home))
            assertEquals(BackupProtection.Plan.Plain, DeskBackupPassword.plan(Way.DRIVE, home))
            assertEquals(BackupProtection.Status.NEEDS_PASSWORD, DeskBackupPassword.protectionStatus(home))
        } finally { home.deleteRecursively() }
    }

    @Test fun `the switch is kept in its own file, off means open files, and an unreadable file means protected`() {
        val home = home()
        try {
            DeskBackupPassword.setFilesProtected(false, home)
            assertFalse(DeskBackupPassword.filesProtected(home))
            assertEquals(BackupProtection.Status.OPEN, DeskBackupPassword.protectionStatus(home))
            for (way in Way.values()) assertEquals(way.name, BackupProtection.Plan.Plain, DeskBackupPassword.plan(way, home))
            assertTrue(DeskBackupPassword.protectionFile(home).isFile)
            DeskBackupPassword.setFilesProtected(true, home)
            assertTrue(DeskBackupPassword.filesProtected(home))
            // A file nobody can read is "protected", never "open".
            DeskBackupPassword.protectionFile(home).delete()
            DeskBackupPassword.protectionFile(home).mkdirs()
            assertTrue(DeskBackupPassword.filesProtected(home))
        } finally { home.deleteRecursively() }
    }

    @Test fun `a password the lock cannot read stops every file that would leave, never an open one`() {
        val home = home()
        try {
            DeskBackupPassword.file(home).writeText("p1:0011223344556677:AAAA")
            for (way in listOf(Way.BACK_UP_NOW, Way.OUTSIDE_FOLDER, Way.FOR_PHONE)) {
                assertTrue(way.name, DeskBackupPassword.plan(way, home) is BackupProtection.Plan.Unavailable)
            }
            assertEquals(BackupProtection.Status.UNREADABLE, DeskBackupPassword.protectionStatus(home))
            // The owner's own switch is the only way to an open file.
            DeskBackupPassword.setFilesProtected(false, home)
            assertEquals(BackupProtection.Plan.Plain, DeskBackupPassword.plan(Way.BACK_UP_NOW, home))
        } finally { home.deleteRecursively() }
    }

    @Test fun `a hint is kept beside a password only when it does not give it away, and goes with the password`() {
        if (!isWindows) return // DPAPI keeps the password; the Linux test machine has no lock
        val home = home()
        try {
            assertEquals(BackupPassword.Saved.OK, DeskBackupPassword.save(pw, home, "my shop's first customer"))
            assertEquals("my shop's first customer", BackupProtection.hint(DeskBackupPassword.protectionStore(home)))
            assertEquals(BackupPassword.Saved.OK, DeskBackupPassword.save(pw, home, "it is ali raza 2026"))
            assertNull(BackupProtection.hint(DeskBackupPassword.protectionStore(home)))
            DeskBackupPassword.save(pw, home, "a hint")
            DeskBackupPassword.remove(home)
            assertNull(BackupProtection.hint(DeskBackupPassword.protectionStore(home)))
        } finally { home.deleteRecursively() }
    }

    @Test fun `the hint is read from the locked file before any password and the copy for the outside folder carries it`() {
        val home = home()
        val db = DesktopDatabase.open(home.resolve("khata.db"))
        try {
            val settings = FileShopSettings(home.resolve("shop.properties"))
            val outside = home.resolve("usb/Business-1 (PC)")
            val copy = runBlocking {
                db.khataDao().insertParty(Party(name = "Bashir Ahmad", phone = null, isCustomer = true))
                DailyCopies.makeToday(db.khataDao(), settings, outside, refresh = true, password = pw, hint = "the shop's first customer")
            }!!
            assertTrue(BackupSeal.isSealed(copy))
            assertEquals("the shop's first customer", BackupSeal.hintOf(copy))
            assertNull("a copy kept inside the app's folder has none",
                BackupSeal.hintOf(runBlocking { DailyCopies.makeToday(db.khataDao(), settings, home.resolve("own"), refresh = true) }!!))
        } finally { db.close(); home.deleteRecursively() }
    }

    @Test fun `asking a locked file shows its hint, and a wrong password restores nothing`() {
        val home = home()
        val db = DesktopDatabase.open(home.resolve("khata.db"))
        try {
            val settings = FileShopSettings(home.resolve("shop.properties"))
            val locked = runBlocking {
                db.khataDao().insertParty(Party(name = "Bashir Ahmad", phone = null, isCustomer = true))
                DailyCopies.makeToday(db.khataDao(), settings, home.resolve("usb"), refresh = true, password = pw, hint = "first customer")
            }!!
            val before = runBlocking { LiveSync.fingerprint(db.syncSql()) }
            var shown: String? = "unset"
            var got: Pair<BackupCodec.ImportResult, BackupCodec.ParsedBackup?>? = null
            runBlocking {
                BackupPasswordPrompt.hosts++
                val answerer = launch {
                    while (true) {
                        val a = BackupPasswordPrompt.asking.value
                        if (a != null && !a.answer.isCompleted) { shown = a.hint; a.answer.complete("Tariq Butt 300") }
                        delay(5)
                    }
                }
                try { got = readBackup(locked, home) } finally { answerer.cancel(); BackupPasswordPrompt.hosts-- }
            }
            assertEquals("first customer", shown)
            val (result, data) = got!!
            assertEquals(BackupCodec.ImportResult.Why.WRONG_PASSWORD, (result as BackupCodec.ImportResult.Failed).why)
            assertNull("nothing is handed over to restore", data)
            assertEquals("the book is as it was", before, runBlocking { LiveSync.fingerprint(db.syncSql()) })
        } finally { db.close(); home.deleteRecursively() }
    }

    @Test fun `the first-use question, nobody to ask means cancelled, a cancel writes nothing, no question is needed when open`() {
        val home = home()
        try {
            // No window hosts the question: it does not wait, and nothing is kept.
            assertEquals(BackupPasswordPrompt.First.CANCELLED, runBlocking { BackupPasswordPrompt.first(Way.BACK_UP_NOW, null, home) })
            assertFalse(DeskBackupPassword.file(home).exists())
            // A window that answers Cancel.
            runBlocking {
                BackupPasswordPrompt.hosts++
                val answerer = launch {
                    while (true) {
                        val c = BackupPasswordPrompt.creating.value
                        if (c != null && !c.answer.isCompleted) c.answer.complete(null)
                        delay(5)
                    }
                }
                try { assertEquals(BackupPasswordPrompt.First.CANCELLED, BackupPasswordPrompt.first(Way.BACK_UP_NOW, null, home)) }
                finally { answerer.cancel(); BackupPasswordPrompt.hosts-- }
            }
            assertFalse(DeskBackupPassword.file(home).exists())
            // The owner's own copies need no password, and neither does an open choice.
            assertEquals(BackupPasswordPrompt.First.READY, runBlocking { BackupPasswordPrompt.first(Way.DAILY_COPY, null, home) })
            DeskBackupPassword.setFilesProtected(false, home)
            assertEquals(BackupPasswordPrompt.First.READY, runBlocking { BackupPasswordPrompt.first(Way.BACK_UP_NOW, null, home) })
        } finally { home.deleteRecursively() }
    }

    @Test fun `a first password typed is kept with its hint, where this computer has a lock`() {
        val home = home()
        try {
            var outcome: BackupPasswordPrompt.First? = null
            runBlocking {
                BackupPasswordPrompt.hosts++
                val answerer = launch {
                    while (true) {
                        val c = BackupPasswordPrompt.creating.value
                        if (c != null && !c.answer.isCompleted) c.answer.complete(pw to "first customer")
                        delay(5)
                    }
                }
                try { outcome = BackupPasswordPrompt.first(Way.BACK_UP_NOW, null, home) } finally { answerer.cancel(); BackupPasswordPrompt.hosts-- }
            }
            if (isWindows) {
                assertEquals(BackupPasswordPrompt.First.READY, outcome)
                val plan = DeskBackupPassword.plan(Way.BACK_UP_NOW, home) as BackupProtection.Plan.Seal
                assertEquals(pw, plan.password)
                assertEquals("first customer", plan.hint)
                assertEquals(BackupProtection.Status.PROTECTED, DeskBackupPassword.protectionStatus(home))
            } else {
                // No lock here: no password is kept, so no file would be made open instead.
                assertEquals(BackupPasswordPrompt.First.NO_LOCK, outcome)
                assertFalse(DeskBackupPassword.file(home).exists())
                assertEquals(BackupProtection.Plan.NeedFirstPassword, DeskBackupPassword.plan(Way.BACK_UP_NOW, home))
            }
        } finally { home.deleteRecursively() }
    }
}
