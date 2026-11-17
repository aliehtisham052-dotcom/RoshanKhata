package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.CustomerLink
import com.innovation313.roshankhata.data.DesktopDatabase
import com.innovation313.roshankhata.data.FileShopSettings
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.desktopWords
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Locale

/**
 * Audit L022 (17 Nov 2026), register BC-208: the computer sends and stops a customer's khata link, on the phone's shared
 * rules (CustomerLink). What is the computer's own is held here: its notes live in one file beside app.properties (never
 * in the book), survive the app closing and are one object per file; a computer that never sent a link sends nothing in
 * its quiet rounds; and why a link could not go is said in the phone's own words, refusal by refusal.
 */
class KhataLinkDeskTest {

    private lateinit var dir: File

    @Before
    fun open() { dir = Files.createTempDirectory("rk-clink").toFile() }

    @After
    fun close() { dir.deleteRecursively() }

    @Test
    fun `the computer's notes are kept beside the app, outside the book, and survive closing`() {
        val file = File(dir, KhataLinkDesk.FILE)
        val place = DeskLinkPlace(file)
        assertNull(place.get("clinksent:abc"))
        place.put("clinksent:abc", "1")
        assertEquals("1", DeskLinkPlace(file).get("clinksent:abc"))
        place.put("clinksent:abc", null)
        assertNull(DeskLinkPlace(file).get("clinksent:abc"))
        assertTrue(file.isFile)
        assertFalse("no half-written file is left", File(file.path + ".tmp").exists())
        // One object per file, so two windows never write over each other's notes.
        assertSame(KhataLinkDesk.place(dir), KhataLinkDesk.place(dir))
    }

    @Test
    fun `a computer that never sent a link sends nothing in its rounds`() = runBlocking {
        val db = DesktopDatabase.open(dir.resolve("khata.db"))
        try {
            val place = KhataLinkDesk.place(dir)
            assertFalse(KhataLinkDesk.used(place))
            val look = KhataLinkDesk.look(FileShopSettings(File(dir, "shop.properties")), null)
            assertEquals("no language chosen: English pages", "en", look.language)
            assertEquals(CustomerLink.Result.Idle, KhataLinkDesk.flush(db.khataDao(), db, place, "khata.db", look))
            KhataLinkDesk.markUsed(place)
            assertTrue(KhataLinkDesk.used(DeskLinkPlace(File(dir, KhataLinkDesk.FILE))))
        } finally {
            db.close()
        }
    }

    @Test
    fun `why a link could not go is said in the phone's words`() {
        val words = desktopWords(Locale.US)
        fun said(r: CustomerLink.Result) = KhataLinkDesk.trouble(words, r)
        assertEquals(words.text(W.clink_offline), said(CustomerLink.Result.Unreachable))
        assertEquals(words.text(W.clink_off), said(CustomerLink.Result.Off))
        assertEquals(words.text(W.clink_busy), said(CustomerLink.Result.Refused("busy")))
        assertEquals(words.text(W.clink_limit_today), said(CustomerLink.Result.Refused("day-limit")))
        assertEquals(words.text(W.clink_limit_today), said(CustomerLink.Result.Refused("later")))
        for (full in listOf("full", "disk", "closed")) assertEquals(words.text(W.clink_full), said(CustomerLink.Result.Refused(full)))
        assertEquals(words.text(W.clink_wrong_pass), said(CustomerLink.Result.Refused("wrong-pass")))
        assertEquals(words.text(W.clink_halted), said(CustomerLink.Result.Refused("halted")))
        assertEquals(words.text(W.clink_failed_code, "app IllegalStateException"), said(CustomerLink.Result.Refused("app IllegalStateException")))
        assertEquals(words.text(W.clink_failed_code, "taken"), said(CustomerLink.Result.Done(0, 0, lost = 1)))
    }
}
