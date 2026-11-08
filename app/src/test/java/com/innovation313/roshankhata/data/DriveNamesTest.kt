package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The Play app and the TEST build never read each other's Drive backups (8 Nov 2026). */
class DriveNamesTest {
    @Test
    fun `the shop app reads its own names and never a Dev_ file`() {
        assertEquals(1L, DriveBackup.businessIdFromBackupName("RoshanKhata_Backup.txt", ""))
        assertEquals(7L, DriveBackup.businessIdFromBackupName("RoshanKhata_Backup_b7.txt", ""))
        assertNull(DriveBackup.businessIdFromBackupName("Dev_RoshanKhata_Backup.txt", ""))
        assertNull(DriveBackup.businessIdFromBackupName("Dev_RoshanKhata_Backup_b7.txt", ""))
    }

    @Test
    fun `the test build reads only its Dev_ names`() {
        assertEquals(1L, DriveBackup.businessIdFromBackupName("Dev_RoshanKhata_Backup.txt", "Dev_"))
        assertEquals(7L, DriveBackup.businessIdFromBackupName("Dev_RoshanKhata_Backup_b7.txt", "Dev_"))
        assertNull(DriveBackup.businessIdFromBackupName("RoshanKhata_Backup.txt", "Dev_"))
        assertNull(DriveBackup.businessIdFromBackupName("RoshanKhata_Backup_b7.txt", "Dev_"))
    }
}
