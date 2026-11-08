package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.PinHash
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** The computer's App Lock keeps only a salted hash, and only the right PIN opens it. */
class AppLockTest {
    @Test
    fun `the right PIN opens, a wrong one does not, and the PIN is never written`() {
        val dir = Files.createTempDirectory("rk-lock").toFile()
        val file = File(dir, "app.properties")
        val prefs = AppPrefs(file)
        assertFalse(prefs.hasPin())
        prefs.setPin("4826")
        assertTrue(prefs.hasPin())
        assertTrue(prefs.pinMatches("4826"))
        assertFalse(prefs.pinMatches("4827"))
        assertFalse("the PIN itself is on disk", file.readText().contains("4826"))
        // Read back by a fresh instance, as on the next opening.
        assertTrue(AppPrefs(file).pinMatches("4826"))
        prefs.clearPin()
        assertFalse(AppPrefs(file).hasPin())
    }

    @Test
    fun `only 4 to 8 digits are a PIN`() {
        assertTrue(PinHash.valid("1234"))
        assertTrue(PinHash.valid("12345678"))
        assertFalse(PinHash.valid("123"))
        assertFalse(PinHash.valid("123456789"))
        assertFalse(PinHash.valid("12a4"))
    }

    @Test
    fun `app lock takes a new PIN of 6 to 8 digits only, and an older short one still opens`() {
        // The owner PIN's rule, the owner's choice (25 Oct 2026); a 4-digit App Lock set before still unlocks.
        assertFalse(PinHash.validOwner("1234"))
        assertTrue(PinHash.validOwner("123456"))
        assertTrue(PinHash.validOwner("12345678"))
        assertFalse(PinHash.validOwner("123456789"))
        val dir = Files.createTempDirectory("rk-lock-old").toFile()
        try {
            val file = File(dir, "app.properties")
            AppPrefs(file).setPin("1234")
            assertTrue(AppPrefs(file).pinMatches("1234"))
        } finally { dir.deleteRecursively() }
    }
}
