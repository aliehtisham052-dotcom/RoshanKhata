package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

/**
 * The whole road on the computer: a phone's backup restored into the
 * computer's own SQLite book, and the customer list read back with the
 * phone's own query.
 */
class DesktopBookTest {

    @Test
    fun `a phone backup becomes the computer's book`() {
        val dir = Files.createTempDirectory("rk-book").toFile()
        val db = DesktopDatabase.open(dir.resolve("khata.db"))
        try {
            val parsed = BackupCodec.parseText(BackupFixtures.v7).second!!
            runBlocking {
                BackupCodec.restoreLedger(db.khataDao(), parsed)
                val parties = db.khataDao().partiesWithBalanceOnce()
                assertEquals(1, parties.size)
                assertEquals("Ali (علی)", parties[0].name)
                assertEquals(4598.0, parties[0].balance, 0.001)
                assertEquals(1, db.khataDao().entryItemsPageForBackup(0, 10).size)
            }
        } finally {
            db.close()
            dir.deleteRecursively()
        }
    }
}
