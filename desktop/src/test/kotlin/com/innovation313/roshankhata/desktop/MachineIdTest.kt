package com.innovation313.roshankhata.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which computer this is comes from Windows, kept as a hash (22 Oct 2026, the
 * review): an id typed into app.properties no longer decides whose device a
 * counter is.
 */
class MachineIdTest {

    private val shape = Regex("w[0-9a-f]{31}")

    @Test
    fun `the id is a hash of Windows' machine id, never the id itself`() {
        val reg = "\r\nHKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Cryptography\r\n" +
            "    MachineGuid    REG_SZ    3F6F5A2E-1B2C-4D5E-8F90-0A1B2C3D4E5F\r\n\r\n"
        val guid = MachineId.parse(reg)
        assertEquals("3f6f5a2e-1b2c-4d5e-8f90-0a1b2c3d4e5f", guid)
        val id = MachineId.of(guid!!)
        assertTrue(id, shape.matches(id))
        assertEquals("the same machine, however it is written", id, MachineId.of(guid.uppercase()))
        assertFalse(id.contains("3f6f5a2e"))
        assertFalse("another machine, another id", id == MachineId.of("3f6f5a2e-1b2c-4d5e-8f90-0a1b2c3d4e50"))
        assertNull(MachineId.parse("ERROR: The system was unable to find the specified registry key or value."))
    }

    @Test
    fun `on Windows the machine id is read, and nowhere else`() {
        val id = MachineId.read()
        if (System.getProperty("os.name").orEmpty().startsWith("Windows")) {
            assertTrue("read on Windows: $id", id != null && shape.matches(id))
            assertEquals("the same each time", id, MachineId.read())
        } else {
            assertNull(id)
        }
    }
}
