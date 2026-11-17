package com.innovation313.roshankhata.data

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.StringWriter
import java.nio.file.Files

/**
 * Schema 45 and backup format 20 (16 Nov 2026, the audit's D-02 and T34), on the computer's own driver: a version-44 book
 * opens at 45 with every row it had, every entry an ordinary one and every product without other names; a backup carries an
 * entry's kind and a product's other names whole (a helper's copy too: neither is a secret); a format-19 file — written
 * before either existed — restores with every entry ordinary and no other names. Made-up names and figures.
 */
class Schema45Test {

    private fun SQLiteConnection.columns(table: String): Set<String> {
        val st = prepare("PRAGMA table_info($table)")
        try { return buildSet { while (st.step()) add(st.getText(1)) } } finally { st.close() }
    }

    @Test
    fun `a version 44 book opens at 45 with every row and no kind or other name of its own`() = runBlocking {
        val dir = Files.createTempDirectory("rk-schema45").toFile()
        val file = File(dir, "khata.db")
        try {
            val first = DesktopDatabase.open(file)
            val ali: Long
            try {
                val dao = first.khataDao()
                ali = dao.insertParty(Party(name = "Ali Raza", phone = "0300 1234567"))
                dao.insertEntryWithItems(LedgerEntry(partyId = ali, amount = 1_250.0, isGiven = true, entryNumber = ""), emptyList())
                dao.insertEntryWithItems(LedgerEntry(partyId = ali, amount = 250.0, isGiven = false, entryNumber = ""), emptyList())
                dao.insertProduct(Product(name = "Urea", nameKey = ProductName.key("Urea"), normalisedName = ProductName.normalised("Urea"), salePrice = 4_500.0))
            } finally { first.close() }

            // Back to 44: the two columns 45 added taken out — and what every later version added (46: Schema46Test) — the
            // version written back.
            BundledSQLiteDriver().open(file.absolutePath).let { c ->
                try {
                    for (idx in Schema46.newIndices) c.execSQL("DROP INDEX IF EXISTS `$idx`")
                    for ((table, cols) in Schema46.newColumns) for (col in cols) c.execSQL("ALTER TABLE $table DROP COLUMN $col")
                    c.execSQL("ALTER TABLE transactions DROP COLUMN entryKind")
                    c.execSQL("ALTER TABLE products DROP COLUMN otherNames")
                    c.execSQL("PRAGMA user_version = 44")
                    assertTrue("entryKind" !in c.columns("transactions"))
                    assertTrue("otherNames" !in c.columns("products"))
                } finally { c.close() }
            }

            // Opened as the app opens it: Room runs 44 -> 45 and refuses a result that differs from the entities.
            val again = DesktopDatabase.open(file)
            try {
                val dao = again.khataDao()
                val entries = dao.latestEntriesWithItems(ali, 10).map { it.entry }
                assertEquals(2, entries.size)
                assertTrue("every entry written before is an ordinary one", entries.all { it.entryKind == EntryKind.NORMAL })
                assertEquals(1_000.0, dao.liveBalance(ali), 0.001)
                val urea = dao.productsOnce().single()
                assertEquals(4_500.0, urea.salePrice!!, 0.001)
                assertNull(urea.otherNames)
                // And the new columns take what is written now.
                dao.updateProduct(urea.copy(otherNames = "یوریا, यूरिया"))
                assertEquals("یوریا, यूरिया", dao.productsOnce().single().otherNames)
            } finally { again.close() }

            BundledSQLiteDriver().open(file.absolutePath).let { c ->
                try {
                    val st = c.prepare("PRAGMA user_version")
                    try { st.step(); assertEquals(KHATA_DB_VERSION.toLong(), st.getLong(0)) } finally { st.close() }
                    assertTrue(KHATA_DB_VERSION >= 45)
                    for ((table, cols) in Schema45.newColumns) assertTrue(table, c.columns(table).containsAll(cols))
                } finally { c.close() }
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun book(dir: File, name: String) = DesktopDatabase.open(File(dir, name))

    private suspend fun fill(dao: KhataDao) {
        dao.importOpeningBalances(listOf(CsvImport.Row("Ali Raza", null, 30_000.0)), note = "Opening balance")
        val ali = dao.partiesWithBalanceOnce().single().id
        dao.insertEntryWithItems(LedgerEntry(partyId = ali, amount = 900.0, isGiven = true, entryNumber = ""), emptyList())
        dao.insertProduct(Product(name = "Urea", nameKey = ProductName.key("Urea"), normalisedName = ProductName.normalised("Urea"), otherNames = "یوریا, यूरिया"))
        dao.insertProduct(Product(name = "DAP", nameKey = ProductName.key("DAP"), normalisedName = ProductName.normalised("DAP")))
    }

    @Test
    fun `a format 20 backup carries the kind and the other names, the helper's copy too`() = runBlocking {
        val dir = Files.createTempDirectory("rk-format20").toFile()
        try {
            val db = book(dir, "a.db")
            try {
                fill(db.khataDao())
                for (secrets in listOf(true, false)) {
                    val text = StringWriter().also { BackupCodec.exportTo(db.khataDao(), "Shop", JSONObject(), it, secrets = secrets) }.toString()
                    val (result, data) = BackupCodec.parseText(text)
                    assertTrue("$result", result is BackupCodec.ImportResult.Ok)
                    assertEquals(BackupCodec.FORMAT_VERSION, data!!.version)
                    assertTrue(data.version >= 20)
                    val other = book(dir, "b-$secrets.db")
                    try {
                        BackupCodec.restoreLedger(other.khataDao(), data)
                        val o = other.khataDao()
                        val ali = o.partiesWithBalanceOnce().single()
                        assertEquals(30_900.0, ali.balance, 0.001)
                        val kinds = o.latestEntriesWithItems(ali.id, 10).map { it.entry.amount to it.entry.entryKind }.toSet()
                        assertEquals(setOf(30_000.0 to EntryKind.OPENING, 900.0 to EntryKind.NORMAL), kinds)
                        assertEquals("the restored book's sales leave the opening balance out", 900.0, o.salesTotalBetween(0L, Long.MAX_VALUE), 0.001)
                        val products = o.productsOnce().associate { it.name to it.otherNames }
                        assertEquals("یوریا, यूरिया", products.getValue("Urea"))
                        assertNull(products.getValue("DAP"))
                    } finally { other.close() }
                }
            } finally { db.close() }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a format 19 file restores every entry as ordinary and no other names`() = runBlocking {
        val dir = Files.createTempDirectory("rk-format19").toFile()
        try {
            val db = book(dir, "a.db")
            try {
                fill(db.khataDao())
                val now = StringWriter().also { BackupCodec.exportTo(db.khataDao(), "Shop", JSONObject(), it) }.toString()
                // The file as a release before format 20 wrote it: no entryKind, no otherNames, version 19.
                val old = now
                    // A JSON object's keys come in any order: each key goes with the comma after it, and one left
                    // before a closing brace goes too.
                    .replace(Regex("\"entryKind\"\\s*:\\s*\"[A-Z]+\"\\s*,?"), "")
                    .replace(Regex("\"otherNames\"\\s*:\\s*(null|\"[^\"]*\")\\s*,?"), "")
                    .replace(Regex(",\\s*\\}"), "}")
                    .replace(Regex("\"version\"\\s*:\\s*${BackupCodec.FORMAT_VERSION}\\b"), "\"version\": 19")
                assertTrue("entryKind" !in old && "otherNames" !in old)
                val (result, data) = BackupCodec.parseText(old)
                assertTrue("$result", result is BackupCodec.ImportResult.Ok)
                assertEquals(19, data!!.version)
                val other = book(dir, "b.db")
                try {
                    BackupCodec.restoreLedger(other.khataDao(), data)
                    val o = other.khataDao()
                    val ali = o.partiesWithBalanceOnce().single()
                    assertEquals("the balance is whole", 30_900.0, ali.balance, 0.001)
                    assertTrue("nothing then said which was an opening balance", o.latestEntriesWithItems(ali.id, 10).all { it.entry.entryKind == EntryKind.NORMAL })
                    assertTrue(o.productsOnce().all { it.otherNames == null })
                } finally { other.close() }
            } finally { db.close() }
        } finally {
            dir.deleteRecursively()
        }
    }
}
