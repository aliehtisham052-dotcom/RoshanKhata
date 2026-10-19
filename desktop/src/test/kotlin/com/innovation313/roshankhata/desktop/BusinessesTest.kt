package com.innovation313.roshankhata.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/** The registry keeps the phone's rules: Business 1 always there, never deleted; the open one never deleted. */
class BusinessesTest {
    @Test
    fun `create, switch, survive a restart, delete only what may go`() {
        val home = Files.createTempDirectory("rk-biz").toFile()
        try {
            val b = Businesses(home)
            assertEquals(listOf(1L), b.list().map { it.id })
            assertEquals(1L, b.activeId())
            val shop2 = b.create("Bhatti Seeds")
            assertEquals(2L, shop2.id)
            assertEquals("khata_b2.db", b.dbFile(2).name)
            assertEquals("khata.db", b.dbFile(1).name)
            b.switchTo(2)
            val again = Businesses(home)
            assertEquals(2L, again.activeId())
            assertEquals("Bhatti Seeds", again.list().last().name)
            assertFalse("Business 1 is never deleted", again.canDelete(1))
            assertFalse("the open business is never deleted", again.canDelete(2))
            again.switchTo(1)
            assertTrue(again.canDelete(2))
            again.dbFile(2).writeText("x")
            again.delete(2)
            assertFalse(again.dbFile(2).exists())
            assertEquals(listOf(1L), Businesses(home).list().map { it.id })
        } finally {
            home.deleteRecursively()
        }
    }
}
