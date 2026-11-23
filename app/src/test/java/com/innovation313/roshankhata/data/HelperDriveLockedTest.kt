package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * A helper's phone reading the owner's Drive backup (BC-167, BC-168): a backup locked with the owner's password is
 * never read as text and never said as the generic "load failed"; a plain backup still loads, a missing one is "no
 * backup", any other failure is "load failed". Made-up book text only.
 */
class HelperDriveLockedTest {
    private val plain = """{"version":21,"parties":[{"id":1,"name":"Ali Raza"}]}"""

    private fun locked(): ByteArray {
        val out = ByteArrayOutputStream()
        BackupSeal.sealing(out, "Ali Raza 2026", BackupSeal.MIN_ITERATIONS).use { it.write(plain.toByteArray()) }
        return out.toByteArray()
    }

    @Test fun `a locked Drive backup is LockedOnDrive, never text`() {
        val read = DriveBackup.helperCopy(locked(), 1_700_000_000_000L)
        assertTrue(read.exceptionOrNull() is DriveBackup.LockedOnDrive)
        assertEquals(DriveBackup.HelperRead.LOCKED, DriveBackup.helperReadOf(read))
    }

    @Test fun `a plain Drive backup is its text and its day`() {
        val read = DriveBackup.helperCopy(plain.toByteArray(), 1_700_000_000_000L)
        assertEquals(plain to 1_700_000_000_000L, read.getOrNull())
        assertEquals(DriveBackup.HelperRead.READ, DriveBackup.helperReadOf(read))
    }

    @Test fun `no backup and other failures keep their own words`() {
        assertEquals(DriveBackup.HelperRead.NONE, DriveBackup.helperReadOf(Result.success(null)))
        assertEquals(DriveBackup.HelperRead.FAILED, DriveBackup.helperReadOf(Result.failure(java.io.IOException("no network"))))
    }

    @Test fun `a locked backup is told as locked even beside other failures of its kind`() {
        // The locked case must be decided before the generic failure: it is a failure too.
        val locked: Result<Pair<String, Long>?> = Result.failure(DriveBackup.LockedOnDrive())
        assertTrue(locked.isFailure)
        assertEquals(DriveBackup.HelperRead.LOCKED, DriveBackup.helperReadOf(locked))
    }
}
