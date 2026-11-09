package com.innovation313.roshankhata.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which cloud folder the daily-copies offer would name (OneDrive first, then Google Drive for Desktop). */
class CloudFolderTest {
    private fun find(env: Map<String, String>, dirs: Set<String>) =
        CloudFolder.find({ env[it] }, "C:\\Users\\Shop", { it in dirs })

    @Test
    fun `nothing there gives nothing`() {
        assertNull(find(emptyMap(), emptySet()))
    }

    @Test
    fun `OneDrive wins over Google Drive`() {
        val f = find(mapOf("OneDrive" to "C:\\Users\\Shop\\OneDrive"), setOf("C:\\Users\\Shop\\OneDrive", "G:\\My Drive"))!!
        assertEquals(CloudFolder.Kind.ONEDRIVE, f.kind)
        assertEquals("C:\\Users\\Shop\\OneDrive", f.root)
    }

    @Test
    fun `a OneDrive variable naming a missing folder is passed over`() {
        val f = find(mapOf("OneDrive" to "C:\\gone", "OneDriveCommercial" to "C:\\Work\\OneDrive"), setOf("C:\\Work\\OneDrive"))!!
        assertEquals("C:\\Work\\OneDrive", f.root)
    }

    @Test
    fun `Google Drive on a drive letter`() {
        val f = find(emptyMap(), setOf("H:\\My Drive"))!!
        assertEquals(CloudFolder.Kind.GOOGLE_DRIVE, f.kind)
        assertEquals("H:\\My Drive", f.root)
    }

    @Test
    fun `Google Drive under the profile`() {
        val f = find(emptyMap(), setOf("C:\\Users\\Shop\\Google Drive\\My Drive"))!!
        assertEquals(CloudFolder.Kind.GOOGLE_DRIVE, f.kind)
    }

    @Test
    fun `an empty variable is not a folder`() {
        assertNull(find(mapOf("OneDrive" to "  "), setOf("")))
    }
}
