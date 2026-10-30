package com.innovation313.roshankhata.desktop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A photo path from a backup is never read over the network (30 Oct 2026, the security review): a UNC path in a
 * customer's face made Windows connect over SMB on its own when the Khata list drew it, offering the user's hash.
 */
class PhotoPathTest {

    @Test
    fun `network paths are recognised in every spelling`() {
        assertTrue(Photos.isNetworkPath("\\\\attacker.host\\s\\a.png"))
        assertTrue(Photos.isNetworkPath("//attacker.host/s/a.png"))
        assertTrue(Photos.isNetworkPath("  \\\\?\\UNC\\host\\s\\a.png"))
        assertTrue(Photos.isNetworkPath("file://host/s/a.png"))
    }

    @Test
    fun `the app's own pictures are not`() {
        assertFalse(Photos.isNetworkPath("C:\\Users\\Shop\\AppData\\Roaming\\Roshan Khata\\bills\\b1.jpg"))
        assertFalse(Photos.isNetworkPath("/tmp/rk/party_photos/p1.jpg"))
    }

    @Test
    fun `a network path draws nothing`() {
        assertNull(Photos.bitmap("\\\\attacker.host\\s\\a.png"))
    }
}
