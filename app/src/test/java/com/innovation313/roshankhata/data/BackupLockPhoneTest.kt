package com.innovation313.roshankhata.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The phone's side of the backup password (17 Nov 2026, hybrid since 25 Nov 2026, D14): the Drive backup is never locked;
 * every file that leaves the phone is locked by default — with no password set yet the screen must ask first (the
 * copy refuses, never goes open), with one kept that cannot be read (another phone's, the Keystore not answering —
 * Robolectric has no Keystore at all) no file leaves the phone, never a plain one; the owner's switch is the only way to
 * an open file; a locked copy says so in its name; nothing in the kept text is plain.
 */
@RunWith(AndroidJUnit4::class)
class BackupLockPhoneTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences("backup_password", Context.MODE_PRIVATE)

    @After fun clear() { prefs.edit().clear().commit() }

    private fun plainFile(): File = File(context.cacheDir, "backups").apply { mkdirs() }
        .resolve("RoshanKhata_Backup_2026-11-17_0930.txt").apply { writeText("{\"format\": \"RoshanKhata\"}") }

    @Test fun `the Drive backup is never locked, whatever the password and the switch say`() {
        assertTrue(Backup.passwordState(context) is BackupPassword.State.None)
        assertNull(Backup.lockedCopy(context, plainFile()))
        assertNull(Backup.lockedCopy(context, plainFile(), BackupProtection.Way.DRIVE))
        prefs.edit().putString("v", "rubbish").commit()
        assertNull("an unreadable password does not stop the Drive backup", Backup.lockedCopy(context, plainFile(), BackupProtection.Way.DRIVE))
        assertEquals("", Backup.passwordMark(context))
    }

    @Test fun `with no password every file that leaves the phone asks for one first and is never made open`() {
        assertTrue(Backup.filesProtected(context))
        assertEquals(BackupProtection.Status.NEEDS_PASSWORD, Backup.protectionStatus(context))
        for (way in listOf(BackupProtection.Way.DOWNLOADS, BackupProtection.Way.SHARE, BackupProtection.Way.TO_COMPUTER)) {
            val plain = plainFile()
            try {
                Backup.lockedCopy(context, plain, way)
                fail("a file left open with protection on: $way")
            } catch (e: BackupProtection.NeedsPassword) {
            }
            assertTrue(File(plain.parentFile, Backup.lockedName(plain.name)).exists().not())
        }
    }

    @Test fun `the owner's switch is the only way to an open file, and the phone says so`() {
        Backup.setFilesProtected(context, false)
        assertEquals(BackupProtection.Status.OPEN, Backup.protectionStatus(context))
        assertNull(Backup.lockedCopy(context, plainFile(), BackupProtection.Way.DOWNLOADS))
        Backup.setFilesProtected(context, true)
        assertEquals(BackupProtection.Status.NEEDS_PASSWORD, Backup.protectionStatus(context))
    }

    @Test fun `with no password a backup is read as it always was`() {
        assertTrue(Backup.passwordState(context) is BackupPassword.State.None)
        assertEquals(listOf("typed one"), Backup.passwordsToTry(context, listOf("typed one")))
        assertEquals(emptyList<String>(), Backup.passwordsToTry(context, listOf("  ")))
    }

    @Test fun `a kept password that cannot be read stops the backup instead of sending it plain`() {
        for (kept in listOf("rubbish", "p1:0011223344556677:AAAA")) {
            prefs.edit().putString("v", kept).commit()
            assertTrue(kept, Backup.passwordState(context) is BackupPassword.State.Unreadable)
            val plain = plainFile()
            try {
                Backup.lockedCopy(context, plain, BackupProtection.Way.DOWNLOADS)
                fail("a backup left with its password unreadable: $kept")
            } catch (e: BackupPassword.Unavailable) {
            }
            assertTrue("the caller's file is left for the caller", plain.exists())
            assertTrue("no half-locked file is left behind", File(plain.parentFile, Backup.lockedName(plain.name)).exists().not())
            assertEquals(BackupProtection.Status.UNREADABLE, Backup.protectionStatus(context))
        }
    }

    @Test fun `a locked copy is named so`() {
        assertEquals("RoshanKhata_Backup_b2_2026-11-17_0930_locked.txt", Backup.lockedName("RoshanKhata_Backup_b2_2026-11-17_0930.txt"))
        assertEquals("left-RoshanKhata_Backup_2026-11-17_0930_locked.txt", Backup.lockedName("left-RoshanKhata_Backup_2026-11-17_0930.txt"))
        assertEquals("book_locked", Backup.lockedName("book"))
    }

    @Test fun `the phone's own reader opens a locked backup with the password and asks without one`() {
        val plain = plainFile()
        val locked = BackupSeal.sealFile(plain, File(plain.parentFile, "locked.txt"), "Ali Raza 2026", BackupSeal.MIN_ITERATIONS)
        assertEquals(BackupCodec.ImportResult.Why.LOCKED, (Backup.parseFile(locked).first as BackupCodec.ImportResult.Failed).why)
        assertEquals(BackupCodec.ImportResult.Why.WRONG_PASSWORD, (Backup.parseFile(locked, listOf("Tariq Butt 300")).first as BackupCodec.ImportResult.Failed).why)
        // With the password it opens: the text inside is an empty book, so it reads as Ok — the point is only that it is
        // no longer locked or a wrong password.
        val opened = Backup.parseFile(locked, listOf("Ali Raza 2026")).first
        val why = (opened as? BackupCodec.ImportResult.Failed)?.why
        assertTrue("opened, got $opened", why != BackupCodec.ImportResult.Why.LOCKED && why != BackupCodec.ImportResult.Why.WRONG_PASSWORD)
    }
}
