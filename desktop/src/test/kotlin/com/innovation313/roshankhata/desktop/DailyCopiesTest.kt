package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.BackupCodec
import com.innovation313.roshankhata.data.DesktopDatabase
import com.innovation313.roshankhata.data.FileShopSettings
import com.innovation313.roshankhata.data.Party
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/** Today's copy is made once, reads back as a backup, and only seven are kept. */
class DailyCopiesTest {
    @Test
    fun `one checked copy a day, seven kept`() {
        val home = Files.createTempDirectory("rk-daily").toFile()
        val db = DesktopDatabase.open(home.resolve("khata.db"))
        try {
            val settings = FileShopSettings(home.resolve("shop.properties"))
            val dir = DailyCopies.dir(home, 1)
            runBlocking {
                db.khataDao().insertParty(Party(name = "Bilal", phone = null, isCustomer = true))
                DailyCopies.makeToday(db.khataDao(), settings, dir)
                DailyCopies.makeToday(db.khataDao(), settings, dir)
            }
            val copies = DailyCopies.list(dir)
            assertEquals("once a day", 1, copies.size)
            assertTrue(BackupCodec.parseFile(copies.single()).first is BackupCodec.ImportResult.Ok)
            (1..9).forEach { dir.resolve("2020-01-0$it.txt").apply { writeText("x"); setLastModified(1_000_000L * it) } }
            val safety = runBlocking { DailyCopies.makeToday(db.khataDao(), settings, dir, label = "before-restore-1") }
            // Seven day copies (today's and the six newest days), and the safety copy beside them:
            // copies taken before a restore are kept apart, so they cannot push the week out (21 Oct 2026).
            val left = DailyCopies.list(dir)
            assertEquals(8, left.size)
            assertTrue(safety != null && safety in left)
            assertTrue("the oldest days went", left.none { it.name == "2020-01-01.txt" || it.name == "2020-01-02.txt" || it.name == "2020-01-03.txt" })
        } finally {
            db.close()
            home.deleteRecursively()
        }
    }

    @Test
    fun `a clock gone back deletes nothing, and the copy just written is never the one deleted`() {
        val home = Files.createTempDirectory("rk-daily").toFile()
        val db = DesktopDatabase.open(home.resolve("khata.db"))
        try {
            val settings = FileShopSettings(home.resolve("shop.properties"))
            val dir = DailyCopies.dir(home, 1)
            dir.mkdirs()
            // A week of copies from 2026, then the laptop's clock falls back to 2015 (a dead CMOS battery).
            (12..20).forEach { d -> dir.resolve("2026-10-$d.txt").writeText("x") }
            val back = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ENGLISH).parse("2015-06-01")!!.time
            assertTrue(DailyCopies.clockBehind(dir, back))
            val made = runBlocking {
                db.khataDao().insertParty(Party(name = "Bilal", phone = null, isCustomer = true))
                DailyCopies.makeToday(db.khataDao(), settings, dir, now = back)
            }
            assertTrue("the copy is written", made != null && made.isFile)
            assertEquals("and nothing is deleted", 10, DailyCopies.list(dir).size)
        } finally {
            db.close()
            home.deleteRecursively()
        }
    }

    @Test
    fun `each computer keeps its own folder in the outside place, and a copy there reads back whole`() {
        val home = Files.createTempDirectory("rk-daily").toFile()
        val stick = Files.createTempDirectory("rk-stick").toFile()
        val db = DesktopDatabase.open(home.resolve("khata.db"))
        try {
            // A shop computer and a home computer on one Google Drive folder (22 Oct 2026, the review).
            val shop = DailyCopies.outside(stick, 1, "SHOP-PC")
            val house = DailyCopies.outside(stick, 1, "HOME-PC")
            assertEquals("Business-1 (SHOP-PC)", shop.name)
            assertTrue(shop != house && shop.parentFile == house.parentFile)
            val name = DailyCopies.computerName()
            assertTrue(name, name.isNotBlank() && name.none { it == '/' || it == '\\' || it == ':' })
            val settings = FileShopSettings(home.resolve("shop.properties"))
            val made = runBlocking {
                db.khataDao().insertParty(Party(name = "Bilal", phone = null, isCustomer = true))
                DailyCopies.makeToday(db.khataDao(), settings, shop)
            }
            assertTrue(made != null && made.parentFile == shop)
            assertTrue(BackupCodec.parseFile(made!!).first is BackupCodec.ImportResult.Ok)
            assertTrue("nothing half-written is left beside it", shop.listFiles()!!.none { it.name.endsWith(".tmp") })
        } finally {
            db.close()
            home.deleteRecursively()
            stick.deleteRecursively()
        }
    }
}
