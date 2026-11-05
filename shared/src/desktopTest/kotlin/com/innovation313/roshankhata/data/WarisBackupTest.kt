package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.StringWriter
import java.nio.file.Files

/**
 * Waris ki Chaabi's keys in the book (4 Nov 2026): the owner's own backup keeps them (a new phone restored from it
 * keeps the vault working); a copy that leaves his hands — the helper's copy, and the Waris snapshot itself, both
 * made with secrets = false — carries none of them, nor the owner's PIN.
 */
class WarisBackupTest {

    private lateinit var dir: File
    private var db: KhataDatabase? = null

    @Before
    fun open() { dir = Files.createTempDirectory("rk-waris").toFile() }

    @After
    fun close() { db?.close(); dir.deleteRecursively() }

    @Test
    fun `Waris keys stay in the owner's backup and never reach a copy that leaves his hands`() = runBlocking {
        val dao = DesktopDatabase.open(dir.resolve("book.db")).also { db = it }.khataDao()
        Waris.SECRET_KEYS.forEach { dao.setSetting(it, "secret-$it") }
        dao.setSetting(BookSettings.PRICES_INCLUDE_TAX, "1")
        val own = StringWriter().also { BackupCodec.exportTo(dao, "Shop", JSONObject(), it) }.toString()
        val helper = StringWriter().also { BackupCodec.exportTo(dao, "Shop", JSONObject(), it, secrets = false) }.toString()
        Waris.SECRET_KEYS.forEach {
            assertTrue(it, own.contains("secret-$it"))
            assertFalse(it, helper.contains("secret-$it"))
        }
        val copy = BackupCodec.parseText(helper).second!!
        assertEquals(listOf(BookSettings.PRICES_INCLUDE_TAX), copy.settings.map { it.key })
        val full = BackupCodec.parseText(own).second!!
        assertTrue(full.settings.map { it.key }.containsAll(Waris.SECRET_KEYS))
    }
}
