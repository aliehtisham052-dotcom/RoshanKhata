package com.innovation313.roshankhata.data

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The book every Play phone holds today, brought up to this build (3 Nov 2026 audit, M01).
 *
 * The build on Google Play (versionCode 3, 13 Oct 2026) keeps its ledger at schema 31. The other migration tests
 * build today's schema and strip what each version added (SchemaRewind); an index forgotten in a migration would
 * pass them and leave every Play phone unable to open its book. This one builds the file from the schema Room
 * itself wrote for version 31 (app/schemas/…/31.json), fills every table with two made-up rows, opens it through
 * the migrations to [KHATA_DB_VERSION] — Room refuses to open a file whose result differs from the entities in
 * any column or index — and checks that every row, sum and balance came through. The phone runs the same
 * statement lists (Schema32 … Schema42).
 */
class PlayBookMigrationTest {

    private fun schema31(): JSONObject {
        val path = "app/schemas/com.innovation313.roshankhata.data.KhataDatabase/31.json"
        val file = listOf(File("../$path"), File(path)).first { it.isFile }
        return JSONObject(file.readText()).getJSONObject("database")
    }

    private fun SQLiteConnection.long(sql: String): Long {
        val st = prepare(sql)
        try { check(st.step()); return st.getLong(0) } finally { st.close() }
    }

    private fun SQLiteConnection.double(sql: String): Double {
        val st = prepare(sql)
        try { check(st.step()); return st.getDouble(0) } finally { st.close() }
    }

    /** A value for [column] in row [row] (1 or 2): keys and unique columns differ per row, links point at row [row]. */
    private fun valueFor(column: String, affinity: String, notNull: Boolean, row: Int, distinct: Boolean, link: Boolean): String = when {
        column == "isDeleted" || column == "isClosed" || column == "isPaidInFull" -> "0"
        column == "deletedAt" && !notNull -> "NULL"
        column == "amount" && affinity == "REAL" -> if (row == 1) "250.5" else "300.25"
        column == "isGiven" -> "1"
        link || (distinct && affinity == "INTEGER") -> "$row"
        distinct && affinity == "REAL" -> "$row.0"
        distinct -> "'k$row'"
        affinity == "INTEGER" -> "1"
        affinity == "REAL" -> "10.0"
        else -> "'T$row'"
    }

    @Test
    fun `a Play phone's version 31 book opens at today's version with every row, sum and balance intact`() {
        val db31 = schema31()
        assertEquals(31, db31.getInt("version"))
        val dir = Files.createTempDirectory("rk-play31").toFile()
        val file = File(dir, "khata.db")
        val tables = mutableListOf<String>()
        try {
            BundledSQLiteDriver().open(file.absolutePath).let { c ->
                try {
                    val entities = db31.getJSONArray("entities")
                    for (i in 0 until entities.length()) {
                        val e = entities.getJSONObject(i)
                        val table = e.getString("tableName")
                        tables += table
                        c.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                        val unique = mutableSetOf<String>()
                        e.optJSONArray("indices")?.let { idx ->
                            for (j in 0 until idx.length()) {
                                val ix = idx.getJSONObject(j)
                                c.execSQL(ix.getString("createSql").replace("\${INDEX_NAME}", ix.getString("name")).replace("\${TABLE_NAME}", table))
                                if (ix.optBoolean("unique")) ix.getJSONArray("columnNames").let { cols -> for (k in 0 until cols.length()) unique += cols.getString(k) }
                            }
                        }
                        val keys = e.getJSONObject("primaryKey").getJSONArray("columnNames").let { cols -> (0 until cols.length()).map { cols.getString(it) } }
                        val links = mutableSetOf<String>()
                        e.optJSONArray("foreignKeys")?.let { fks ->
                            for (j in 0 until fks.length()) fks.getJSONObject(j).getJSONArray("columns").let { cols -> for (k in 0 until cols.length()) links += cols.getString(k) }
                        }
                        val fields = e.getJSONArray("fields")
                        for (row in 1..2) {
                            val names = mutableListOf<String>()
                            val values = mutableListOf<String>()
                            for (j in 0 until fields.length()) {
                                val f = fields.getJSONObject(j)
                                val col = f.getString("columnName")
                                names += "`$col`"
                                values += valueFor(col, f.getString("affinity"), f.optBoolean("notNull"), row, col in keys || col in unique, col in links)
                            }
                            c.execSQL("INSERT INTO `$table` (${names.joinToString(",")}) VALUES (${values.joinToString(",")})")
                        }
                    }
                    val setup = db31.getJSONArray("setupQueries")
                    for (i in 0 until setup.length()) c.execSQL(setup.getString(i))
                    c.execSQL("PRAGMA user_version = 31")
                } finally { c.close() }
            }
            assertEquals("the Play schema's 18 tables", 18, tables.size)

            // Opened as the app opens it: Room runs the chain and refuses any column or index that differs.
            val db = DesktopDatabase.open(file)
            try {
                val balances = runBlocking { db.khataDao().partiesWithBalanceOnce() }.map { it.balance }.sorted()
                assertEquals(listOf(250.5, 300.25), balances)
            } finally { db.close() }

            BundledSQLiteDriver().open(file.absolutePath).let { c ->
                try {
                    assertEquals(KHATA_DB_VERSION.toLong(), c.long("PRAGMA user_version"))
                    for (t in tables) assertEquals("rows of $t", 2L, c.long("SELECT COUNT(*) FROM `$t`"))
                    assertEquals(550.75, c.double("SELECT SUM(amount) FROM transactions"), 0.0)
                    for (t in listOf("parties", "transactions", "cashbook", "products", "invoices", "supplier_bills")) {
                        assertEquals("every $t row got a uid", 0L, c.long("SELECT COUNT(*) FROM `$t` WHERE uid IS NULL"))
                        assertEquals("and its own", 2L, c.long("SELECT COUNT(DISTINCT uid) FROM `$t`"))
                    }
                    assertTrue("no broken links", c.prepare("PRAGMA foreign_key_check").let { st -> try { !st.step() } finally { st.close() } })
                } finally { c.close() }
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
