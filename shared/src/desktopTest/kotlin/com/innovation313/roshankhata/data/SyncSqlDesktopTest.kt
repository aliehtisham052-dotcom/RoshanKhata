package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

/** The sync door on the computer: rows written and read back by name, typed, in one transaction. */
class SyncSqlDesktopTest {

    @Test
    fun `a row written through the door reads back with its types`() {
        val dir = Files.createTempDirectory("rk-sync-sql").toFile()
        val db = DesktopDatabase.open(dir.resolve("khata.db"))
        try {
            runBlocking {
                val id = db.khataDao().insertParty(Party(name = "Ali"))
                val sql = db.syncSql()
                sql.write {
                    exec("UPDATE parties SET creditLimit = ?, isCustomer = ? WHERE id = ?", listOf(2500.5, true, id))
                }
                val row = sql.read { rows("SELECT name, creditLimit, isCustomer, phone, uid FROM parties WHERE id = ?", listOf(id), "TRITT") }.single()
                assertEquals("Ali", row[0])
                assertEquals(2500.5, row[1] as Double, 0.0)
                assertEquals(1L, row[2])
                assertNull(row[3])
                assertEquals(32, (row[4] as String).length)
            }
        } finally {
            db.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a failed block leaves nothing behind`() {
        val dir = Files.createTempDirectory("rk-sync-sql").toFile()
        val db = DesktopDatabase.open(dir.resolve("khata.db"))
        try {
            runBlocking {
                val id = db.khataDao().insertParty(Party(name = "Ali"))
                val sql = db.syncSql()
                runCatching {
                    sql.write {
                        exec("UPDATE parties SET name = 'Bilal' WHERE id = ?", listOf(id))
                        error("stop")
                    }
                }
                assertEquals("Ali", db.khataDao().getParty(id)!!.name)
            }
        } finally {
            db.close()
            dir.deleteRecursively()
        }
    }
}
