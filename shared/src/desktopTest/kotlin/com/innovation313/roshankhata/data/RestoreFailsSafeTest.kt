package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * A restore that fails leaves the book exactly as it was (17-24 Nov 2026, plan item A3, data safety). Every way a copy
 * can be refused or fail on the way in, read both through the list path and through the paged path (forced by a lowered
 * [BackupCodec.pagedFromBytes]), plain and locked:
 *
 *  - a copy that cannot be read (cut short, garbage, empty, locked with no or the wrong password, a locked file cut or
 *    changed or added to, another app's file, a newer format) hands nothing to a restore: no [BackupCodec.ParsedBackup];
 *  - a restore that fails midway (a table's insert refused, as a full disk or a killed process would end it) is rolled
 *    back whole — every table's rows and the fingerprint as before — and a second try on the same book then succeeds;
 *  - a paged file that changes between the scan and the restore's passes restores nothing;
 *  - what runs after the commit (the restore's own audit row) cannot make a replaced book look failed, and the sign-in
 *    made in the old book is always ended.
 *
 * Made-up names and figures only.
 */
class RestoreFailsSafeTest {

    private lateinit var dir: File
    private lateinit var source: KhataDatabase
    private val password = "made-up words 123"

    @Before
    fun open() {
        Access.resetForTest()
        dir = Files.createTempDirectory("rk-fails-safe").toFile()
        source = DesktopDatabase.open(dir.resolve("source.db"))
        val dao = source.khataDao()
        runBlocking {
            val customers = (0 until 3).map { dao.insertParty(Party(name = "Ali Raza $it", isCustomer = true)) }
            for ((n, c) in customers.withIndex()) {
                for (e in 0 until 4) dao.insertEntryWithItems(
                    LedgerEntry(partyId = c, amount = 100.0 + e, isGiven = e % 2 == 0, entryNumber = "", note = "Urea $e"),
                    if (e % 2 == 0) listOf(EntryItem(entryId = 0, itemName = "Urea", quantity = 1.0 + e, unit = "bag", rate = 50.0 + n)) else emptyList()
                )
            }
            for (i in 0 until 4) dao.insertCashEntryAudited(CashEntry(amount = 10.0 + i, isIncome = i % 2 == 0, category = "Tea"))
            for (s in 0 until 2) dao.postSale(
                Sale(subtotal = 0.0, grandTotal = 0.0, paidTotal = 0.0),
                listOf(SaleItem(saleId = 0, itemName = "Soap", quantity = 1.0, rate = 90.0), SaleItem(saleId = 0, itemName = "Tea", quantity = 2.0, rate = 40.0)),
                listOf(SalePayment(saleId = 0, method = PaymentMethod.CASH, amount = 170.0))
            )
        }
    }

    @After
    fun close() {
        BackupCodec.pagedFromBytes = BackupCodec.PAGED_RESTORE_BYTES
        Access.resetForTest()
        runCatching { source.close() }
        dir.deleteRecursively()
    }

    private fun export(name: String, password: String? = null): File = runBlocking {
        val file = dir.resolve(name)
        BackupSeal.writer(file.outputStream(), password, BackupSeal.MIN_ITERATIONS).use {
            BackupCodec.exportTo(source.khataDao(), "Shop", JSONObject(), it)
        }
        file
    }

    private fun read(file: File, paged: Boolean, passwords: List<String> = emptyList()): Pair<BackupCodec.ImportResult, BackupCodec.ParsedBackup?> {
        BackupCodec.pagedFromBytes = if (paged) 0L else Long.MAX_VALUE
        try {
            return BackupCodec.parseFile(file, passwords)
        } finally {
            BackupCodec.pagedFromBytes = BackupCodec.PAGED_RESTORE_BYTES
        }
    }

    /** A book of its own with a few rows, so "as it was" is something to lose. */
    private fun target(name: String): KhataDatabase {
        val db = DesktopDatabase.open(dir.resolve(name))
        runBlocking {
            val p = db.khataDao().insertParty(Party(name = "Bilal Shah", isCustomer = true))
            db.khataDao().insertEntryWithItems(LedgerEntry(partyId = p, amount = 300.0, isGiven = true, entryNumber = ""), emptyList())
            db.khataDao().insertCashEntryAudited(CashEntry(amount = 40.0, isIncome = false, category = "Tea"))
        }
        return db
    }

    private suspend fun counts(db: KhataDatabase): Map<String, Long> =
        (SyncSchema.tables.map { it.name } + listOf("audit_log", "book_seals")).associateWith { t ->
            db.syncSql().read { rows("SELECT COUNT(*) FROM `$t`", types = "I").first()[0] as Long }
        }

    /** What "exactly as it was" means: the fingerprint of every synced table and every table's row count. */
    private suspend fun shape(db: KhataDatabase): Pair<String, Map<String, Long>> =
        LiveSync.fingerprint(db.syncSql()) to counts(db)

    private fun failedWith(r: Pair<BackupCodec.ImportResult, BackupCodec.ParsedBackup?>, why: BackupCodec.ImportResult.Why, label: String) {
        val f = r.first
        assertTrue("$label: $f", f is BackupCodec.ImportResult.Failed)
        assertEquals(label, why, (f as BackupCodec.ImportResult.Failed).why)
        assertNull("$label: a refused copy hands over nothing to restore", r.second)
    }

    // ---------- 1. a copy that cannot be read hands nothing to a restore ----------

    @Test
    fun `a plain copy that is cut, garbage or empty is unreadable on both paths`() {
        val whole = export("plain.txt").readBytes()
        for (paged in listOf(false, true)) {
            val label = if (paged) "paged" else "whole"
            for ((name, bytes) in listOf(
                "cut in half" to whole.copyOf(whole.size / 2),
                "last byte gone" to whole.copyOf(whole.size - 1),
                "garbage" to ByteArray(500) { (it * 7 + 3).toByte() },
                "empty" to ByteArray(0)
            )) {
                val f = dir.resolve("bad-$label-${name.replace(' ', '-')}.txt").apply { writeBytes(bytes) }
                failedWith(read(f, paged), BackupCodec.ImportResult.Why.UNREADABLE, "$label, $name")
            }
        }
    }

    @Test
    fun `another app's file and a newer format are refused on both paths`() {
        val file = export("format.txt")
        for (paged in listOf(false, true)) {
            val newer = JSONObject(file.readText()).put("version", BackupCodec.FORMAT_VERSION + 1)
            val a = dir.resolve("newer-$paged.txt").apply { writeText(newer.toString()) }
            failedWith(read(a, paged), BackupCodec.ImportResult.Why.TOO_NEW, "newer, paged=$paged")
            val other = JSONObject(file.readText()).put("format", "SomethingElse")
            val b = dir.resolve("other-$paged.txt").apply { writeText(other.toString()) }
            failedWith(read(b, paged), BackupCodec.ImportResult.Why.NOT_OURS, "other app, paged=$paged")
            // The newest format this build writes is read: the line is "newer than this build", not "this build's".
            val same = JSONObject(file.readText()).put("version", BackupCodec.FORMAT_VERSION)
            val c = dir.resolve("same-$paged.txt").apply { writeText(same.toString()) }
            assertTrue(read(c, paged).first is BackupCodec.ImportResult.Ok)
        }
    }

    @Test
    fun `a locked copy with no password, the wrong one, cut, changed or added to hands nothing over`() {
        val locked = export("locked.rkb", password).readBytes()
        for (paged in listOf(false, true)) {
            val label = if (paged) "paged" else "whole"
            val f = dir.resolve("locked-$label.rkb").apply { writeBytes(locked) }
            failedWith(read(f, paged), BackupCodec.ImportResult.Why.LOCKED, "$label, no password")
            failedWith(read(f, paged, listOf("not the words")), BackupCodec.ImportResult.Why.WRONG_PASSWORD, "$label, wrong password")
            assertTrue("$label, right password", read(f, paged, listOf(password)).first is BackupCodec.ImportResult.Ok)

            val cut = dir.resolve("locked-cut-$label.rkb").apply { writeBytes(locked.copyOf(locked.size - 20)) }
            failedWith(read(cut, paged, listOf(password)), BackupCodec.ImportResult.Why.UNREADABLE, "$label, cut")
            val flipped = locked.copyOf().also { it[it.size - 40] = (it[it.size - 40].toInt() xor 0x10).toByte() }
            val changed = dir.resolve("locked-changed-$label.rkb").apply { writeBytes(flipped) }
            failedWith(read(changed, paged, listOf(password)), BackupCodec.ImportResult.Why.UNREADABLE, "$label, changed")
            val added = dir.resolve("locked-added-$label.rkb").apply { writeBytes(locked + byteArrayOf(1, 2, 3)) }
            failedWith(read(added, paged, listOf(password)), BackupCodec.ImportResult.Why.UNREADABLE, "$label, added to")
        }
    }

    // ---------- 2. a restore that fails midway is rolled back whole ----------

    /** A table's insert refused, the way a full disk or a dying process ends a statement: the restore throws. */
    private suspend fun refuseInserts(db: KhataDatabase, table: String) {
        db.syncSql().write {
            exec("CREATE TRIGGER rk_test_refuse BEFORE INSERT ON `$table` BEGIN SELECT RAISE(ABORT, 'disk full'); END")
        }
    }

    private suspend fun allowInserts(db: KhataDatabase) {
        db.syncSql().write { exec("DROP TRIGGER rk_test_refuse") }
    }

    @Test
    fun `a restore refused halfway leaves the book exactly as it was, on both paths, and a second try works`() = runBlocking {
        val file = export("midway.txt")
        // First table written, a middle one, the last big ones: the wipe has run by then in every case.
        for (table in listOf("parties", "transactions", "cashbook", "sale_payments", "audit_log")) {
            for (paged in listOf(false, true)) {
                val parsed = read(file, paged).second!!
                val db = target("midway-$table-$paged.db")
                try {
                    val before = shape(db)
                    assertTrue("the book has rows to lose", before.second.getValue("transactions") > 0)
                    refuseInserts(db, table)
                    try {
                        BackupCodec.restoreLedger(db.khataDao(), parsed)
                        fail("a restore refused at $table (paged=$paged) went through")
                    } catch (e: Exception) {
                        // Said as a failure to the caller.
                    }
                    assertEquals("$table paged=$paged: the book is as it was", before, shape(db))
                    // The book is whole and usable: its own rows read, and the same copy goes in once the cause is gone.
                    assertEquals(1, db.khataDao().allPartiesForBackup().size)
                    allowInserts(db)
                    BackupCodec.restoreLedger(db.khataDao(), parsed)
                    assertEquals(counts(source).getValue("transactions"), counts(db).getValue("transactions"))
                    assertEquals(counts(source).getValue("cashbook"), counts(db).getValue("cashbook"))
                    assertEquals(counts(source).getValue("sale_payments"), counts(db).getValue("sale_payments"))
                } finally { db.close() }
            }
        }
    }

    @Test
    fun `a locked copy refused halfway leaves the book as it was too`() = runBlocking {
        val file = export("midway-locked.rkb", password)
        for (paged in listOf(false, true)) {
            val parsed = read(file, paged, listOf(password)).second!!
            val db = target("midway-locked-$paged.db")
            try {
                val before = shape(db)
                refuseInserts(db, "cashbook")
                try {
                    BackupCodec.restoreLedger(db.khataDao(), parsed)
                    fail("went through")
                } catch (e: Exception) { }
                assertEquals(before, shape(db))
            } finally { db.close() }
        }
    }

    // ---------- 3. a paged file that changes between the scan and the passes ----------

    @Test
    fun `a paged file cut, changed or added to after its scan restores nothing`() = runBlocking {
        val original = export("moving.txt").readBytes()
        val variants = mapOf(
            "cut" to original.copyOf(original.size - 200),
            "a byte changed" to original.copyOf().also { it[original.size / 2] = (it[original.size / 2].toInt() xor 0x01).toByte() },
            "added to" to original + " ".toByteArray()
        )
        for ((name, bytes) in variants) {
            val file = dir.resolve("moving-${name.replace(' ', '-')}.txt").apply { writeBytes(original) }
            val parsed = read(file, paged = true).second!!
            file.writeBytes(bytes)
            val db = target("moving-${name.replace(' ', '-')}.db")
            try {
                val before = shape(db)
                try {
                    BackupCodec.restoreLedger(db.khataDao(), parsed)
                    fail("$name: a file that changed was restored")
                } catch (e: java.io.IOException) {
                    // A failure said to the caller, inside the transaction.
                }
                assertEquals("$name: the book is as it was", before, shape(db))
            } finally { db.close() }
        }
    }

    @Test
    fun `a locked paged file changed after its scan restores nothing`() = runBlocking {
        val original = export("moving.rkb", password).readBytes()
        val file = dir.resolve("moving-locked.rkb").apply { writeBytes(original) }
        val parsed = read(file, paged = true, passwords = listOf(password)).second!!
        file.writeBytes(original.copyOf().also { it[it.size - 40] = (it[it.size - 40].toInt() xor 0x10).toByte() })
        val db = target("moving-locked.db")
        try {
            val before = shape(db)
            try {
                BackupCodec.restoreLedger(db.khataDao(), parsed)
                fail("a locked file that changed was restored")
            } catch (e: java.io.IOException) { }
            assertEquals(before, shape(db))
        } finally { db.close() }
    }

    // ---------- 4. after the commit ----------

    @Test
    fun `the restore's own log row failing cannot make a replaced book look failed, and the sign-in always ends`() = runBlocking {
        val file = export("after.txt")
        for (paged in listOf(false, true)) {
            val parsed = read(file, paged).second!!
            val db = target("after-$paged.db")
            try {
                // The one row written after the commit is refused.
                db.syncSql().write {
                    exec("CREATE TRIGGER rk_test_no_restore_row BEFORE INSERT ON audit_log WHEN NEW.action = '${AuditAction.RESTORE}' BEGIN SELECT RAISE(ABORT, 'disk full'); END")
                }
                Access.signIn(Staff(name = "Cashier Sana", monthlySalary = 0.0))
                assertEquals("Cashier Sana", Access.userName)
                BackupCodec.restoreLedger(db.khataDao(), parsed) // must not throw: the book is replaced
                val after = counts(db)
                assertEquals("paged=$paged: the book was replaced", counts(source).getValue("transactions"), after.getValue("transactions"))
                assertNotEquals("the old book's row is gone", 1L, after.getValue("parties"))
                assertNull("paged=$paged: a sign-in made in the old book ends", Access.userName)
            } finally { db.close() }
        }
    }

    @Test
    fun `a restore that works writes its row and ends the sign-in`() = runBlocking {
        val parsed = read(export("normal.txt"), paged = false).second!!
        val db = target("normal.db")
        try {
            Access.signIn(Staff(name = "Cashier Sana", monthlySalary = 0.0))
            BackupCodec.restoreLedger(db.khataDao(), parsed)
            val rows = db.syncSql().read {
                rows("SELECT COUNT(*) FROM audit_log WHERE action = ?", listOf(AuditAction.RESTORE), types = "I").first()[0] as Long
            }
            assertEquals(1L, rows)
            assertNull(Access.userName)
        } finally { db.close() }
    }
}
