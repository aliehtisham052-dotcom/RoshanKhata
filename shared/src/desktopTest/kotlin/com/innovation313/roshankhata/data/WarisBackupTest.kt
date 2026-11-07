package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
 *
 * The security review (7 Nov 2026): W, the key every Waris copy is sealed with, is in no copy of the book at all (W3);
 * a copy that leaves the owner's hands carries none of the customer pages' pass and keys (W4); a restore keeps the
 * vault this book has as it is, with both lists of removed heirs, and a book restored without W makes a new one (W5).
 */
class WarisBackupTest {

    private lateinit var dir: File
    private val dbs = ArrayList<KhataDatabase>()

    @Before
    fun open() { dir = Files.createTempDirectory("rk-waris").toFile() }

    @After
    fun close() { dbs.forEach { it.close() }; dir.deleteRecursively() }

    private fun book(name: String): KhataDao = DesktopDatabase.open(dir.resolve("$name.db")).also { dbs += it }.khataDao()

    private suspend fun export(dao: KhataDao, secrets: Boolean = true): String =
        StringWriter().also { BackupCodec.exportTo(dao, "Shop", JSONObject(), it, secrets = secrets) }.toString()

    private suspend fun copyOf(dao: KhataDao): BackupCodec.ParsedBackup = BackupCodec.parseText(export(dao)).second!!

    @Test
    fun `Waris keys stay in the owner's backup and never reach a copy that leaves his hands - W in none at all`() = runBlocking {
        val dao = book("book")
        // "." closes each value: "secret-waris_key." is not inside "secret-waris_key_version.".
        Waris.SECRET_KEYS.forEach { dao.setSetting(it, "secret-$it.") }
        dao.setSetting(BookSettings.PRICES_INCLUDE_TAX, "1")
        val own = export(dao)
        val helper = export(dao, secrets = false)
        Waris.SECRET_KEYS.forEach {
            assertEquals(it, it != Waris.K_KEY, own.contains("secret-$it."))
            assertFalse(it, helper.contains("secret-$it."))
        }
        val copy = BackupCodec.parseText(helper).second!!
        assertEquals(listOf(BookSettings.PRICES_INCLUDE_TAX), copy.settings.map { it.key })
        val full = BackupCodec.parseText(own).second!!
        assertEquals(Waris.SECRET_KEYS - Waris.K_KEY, full.settings.map { it.key }.filter { Waris.isSecret(it) }.toSet())
    }

    @Test
    fun `a copy that leaves the owner's hands carries none of the customer pages' pass and keys`() = runBlocking {
        val dao = book("pages")
        val uid = "0123456789abcdef0123456789abcdef"
        val linkId = "fedcba9876543210fedcba9876543210"
        val pages = listOf(
            CustomerLink.SHOP to "$linkId.${"a".repeat(64)}",
            CustomerLink.PREFIX + uid to "$linkId.AAAAAAAAAAAAAAAAAAAAAA",
            CustomerLink.GONE + linkId to "1",
            CustomerLink.GEN + uid to "2",
            CustomerLink.MODE to "balance",
            CustomerLink.TITLE to "Al-Noor Traders",
            CustomerLink.MONEY to "Rs",
            CustomerLink.LANG to "ur"
        )
        pages.forEach { (k, v) -> dao.setSetting(k, v) }
        dao.setSetting(BookSettings.PRICES_INCLUDE_TAX, "1")
        pages.forEach { (k, _) -> assertTrue(k, BookSettings.isCustomerLink(k)) }
        assertFalse(BookSettings.isCustomerLink(BookSettings.PRICES_INCLUDE_TAX))
        val helper = BackupCodec.parseText(export(dao, secrets = false)).second!!
        assertEquals(listOf(BookSettings.PRICES_INCLUDE_TAX), helper.settings.map { it.key })
        assertFalse(export(dao, secrets = false).contains("a".repeat(64)))
        // The owner's own backup (and live sync) keep them: the computer and the phone share the same links.
        val own = BackupCodec.parseText(export(dao)).second!!
        assertEquals(pages.toMap(), own.settings.filter { BookSettings.isCustomerLink(it.key) }.associate { it.key to it.value })
    }

    private val v1 = "0123456789abcdef0123456789abcdef"
    private val secret = B64.encode(ByteArray(32) { 7 })
    private val heirA = "00000000000000aa"
    private val heirB = "00000000000000bb"
    private val removedHere = "00000000000000e1"
    private val removedThere = "00000000000000ff"

    private fun heirs(vararg hids: String) =
        JSONArray().apply { hids.forEach { put(JSONObject().put("hid", it).put("name", "Heir").put("wait", 30)) } }.toString()

    private fun ids(text: String?): List<String> = JSONArray(text!!).let { a ->
        (0 until a.length()).map { i -> a.optJSONObject(i)?.getString("hid") ?: a.getString(i) }
    }

    /** A book that kept a vault an older build made: W in it, the heir it removed later still on its list. */
    private suspend fun olderBook(): KhataDao = book("older").also { d ->
        d.setSetting(Waris.K_VAULT, v1)
        d.setSetting(Waris.K_OWNER_SECRET, secret)
        d.setSetting(Waris.K_KEY, B64.encode(ByteArray(32) { 9 }))
        d.setSetting(Waris.K_KEY_VERSION, "2")
        d.setSetting(Waris.K_HEIRS, heirs(heirA, removedThere))
        d.setSetting(Waris.K_REMOVED, JSONArray(listOf(removedThere)).toString())
    }

    @Test
    fun `a restore keeps this book's vault as it is with both lists of removed heirs`() = runBlocking {
        val copy = copyOf(olderBook())
        val here = book("here")
        val w = B64.encode(ByteArray(32) { 3 })
        here.setSetting(Waris.K_VAULT, v1)
        here.setSetting(Waris.K_OWNER_SECRET, secret)
        here.setSetting(Waris.K_KEY, w)
        here.setSetting(Waris.K_KEY_SAFE, "1")
        here.setSetting(Waris.K_KEY_VERSION, "3")
        here.setSetting(Waris.K_HEIRS, heirs(heirA, heirB))
        here.setSetting(Waris.K_REMOVED, JSONArray(listOf(removedHere)).toString())
        BackupCodec.restoreLedger(here, copy)
        assertEquals(w, here.settingValue(Waris.K_KEY))
        assertEquals("3", here.settingValue(Waris.K_KEY_VERSION))
        assertEquals(listOf(heirA, heirB), ids(here.settingValue(Waris.K_HEIRS)))
        assertEquals(listOf(removedHere, removedThere), ids(here.settingValue(Waris.K_REMOVED)))
    }

    @Test
    fun `a book restored without W drops the removed heir and makes a new W, a pairing takes no vault`() = runBlocking {
        val copy = copyOf(olderBook())
        assertTrue("no copy carries W any more", copy.settings.none { it.key == Waris.K_KEY })
        // A backup an older build wrote, W inside: restored, it still leaves W out.
        val withW = copy.copy(settings = copy.settings + BookSetting(Waris.K_KEY, B64.encode(ByteArray(32) { 9 })))
        val fresh = book("fresh")
        BackupCodec.restoreLedger(fresh, withW)
        assertEquals(v1, fresh.settingValue(Waris.K_VAULT))
        assertNull(fresh.settingValue(Waris.K_KEY))
        assertEquals(listOf(heirA), ids(fresh.settingValue(Waris.K_HEIRS)))
        val store = object : Waris.Store {
            override suspend fun get(key: String) = fresh.settingValue(key)
            override suspend fun put(key: String, value: String?) = fresh.setSetting(key, value)
        }
        val relays = Waris.Relays({ _, _, _, _ -> throw java.io.IOException("no network here") },
            listOf(LiveCloud.Road("https://one.test", ""))) { false }
        val st = Waris.Owner(store, relays).state()
        assertNotNull(st)
        assertEquals(3, st!!.keyVersion)
        assertEquals(B64.encode(st.w), fresh.settingValue(Waris.K_KEY))
        assertEquals("1", fresh.settingValue(Waris.K_KEY_SAFE))
        assertEquals("[\"\"]", fresh.settingValue(Waris.K_DIRTY))
        // A live pairing: the other device's vault stays with that device.
        val paired = book("paired")
        BackupCodec.restoreLedger(paired, copy, keepAccess = false)
        assertNull(paired.settingValue(Waris.K_VAULT))
        assertNull(paired.settingValue(Waris.K_OWNER_SECRET))
    }
}
