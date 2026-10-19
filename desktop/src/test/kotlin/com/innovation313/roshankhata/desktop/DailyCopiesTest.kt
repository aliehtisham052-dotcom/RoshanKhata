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
            runBlocking { DailyCopies.makeToday(db.khataDao(), settings, dir, label = "before-restore-1") }
            assertEquals(7, DailyCopies.list(dir).size)
        } finally {
            db.close()
            home.deleteRecursively()
        }
    }
}
