package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter
import java.nio.file.Files

/**
 * A live pairing hands the computer the phone's book as a backup; from then on
 * the two must be the very same book (22 Oct 2026: the owner's phone said
 * "the two books do not match" after a real pairing). Here a phone's book is
 * written, sent through the backup format exactly as the pairing sends it,
 * restored on the "computer", and the two compared row by row as they travel.
 * A difference is named — table, uid, column, both values — not just counted.
 */
class LivePairingTest {

    /**
     * Every synced row as it travels (no local id, no file paths, every pointer the uid of the row it names), read
     * here with plain SQL from [SyncSchema] — on its own, not through the engine, so the engine is checked and not
     * believed (8 Nov 2026 review m1 took away the test-only LiveSync.snapshot). The settings that never travel
     * (BookSettings.ACCESS_KEYS) are left out, as the engine leaves them.
     */
    private fun rowsOf(sql: SyncSql): Map<String, JSONObject> = runBlocking {
        val out = LinkedHashMap<String, JSONObject>()
        for (t in SyncSchema.tables) {
            sql.read {
                val cols = rows("PRAGMA table_info(${t.name})", types = "ITTITI")
                    .map { (it[1] as String) to (it[2] as String?).orEmpty().uppercase() }
                    .filter { (name, _) -> name != "id" && name !in t.localOnly }
                val types = cols.joinToString("") { (name, declared) ->
                    when {
                        t.fks[name] != null -> "T"
                        "INT" in declared -> "I"
                        "REAL" in declared || "FLOA" in declared || "DOUB" in declared -> "R"
                        else -> "T"
                    }
                }
                val select = cols.joinToString(", ") { (name, _) ->
                    val target = t.fks[name]
                    if (target != null) "(SELECT x.uid FROM `$target` x WHERE x.id = s.`$name`)" else "s.`$name`"
                }
                val at = cols.indexOfFirst { it.first == t.idColumn }
                for (r in rows("SELECT $select FROM `${t.name}` s WHERE s.`${t.idColumn}` IS NOT NULL", types = types)) {
                    val key = r[at] as String
                    if (t.keyed && key in BookSettings.ACCESS_KEYS) continue
                    val o = JSONObject()
                    cols.forEachIndexed { i, (name, _) -> o.put(name, r[i] ?: JSONObject.NULL) }
                    out[t.name + "/" + key] = o
                }
            }
        }
        out
    }

    private fun differences(a: Map<String, JSONObject>, b: Map<String, JSONObject>): List<String> {
        val out = ArrayList<String>()
        for (k in (a.keys + b.keys).sorted()) {
            val x = a[k]; val y = b[k]
            if (x == null || y == null) { out.add("$k only on the ${if (x == null) "computer" else "phone"}"); continue }
            for (c in (x.keySet() + y.keySet()).sorted()) {
                val vx = x.opt(c)?.toString(); val vy = y.opt(c)?.toString()
                val nx = vx?.toDoubleOrNull(); val ny = vy?.toDoubleOrNull()
                val same = vx == vy || (nx != null && ny != null && nx == ny) || (vx in setOf(null, "null") && vy in setOf(null, "null"))
                if (!same) out.add("$k.$c phone=$vx computer=$vy")
            }
        }
        return out
    }

    @Test
    fun `the computer's copy of the phone's book is the same book, row by row`() = runBlocking {
        val dir = Files.createTempDirectory("rk-pair").toFile()
        val phone = DesktopDatabase.open(dir.resolve("phone.db"))
        val pc = DesktopDatabase.open(dir.resolve("pc.db"))
        try {
            val d = phone.khataDao()
            val ali = d.insertParty(Party(name = "Ali", phone = "0300 1234567"))
            val bilal = d.insertParty(Party(name = "Bilal"))
            d.insertEntryWithItems(
                LedgerEntry(partyId = ali, amount = 900.0, isGiven = true, entryNumber = ""),
                listOf(EntryItem(entryId = 0, itemName = "Urea", quantity = 2.0, rate = 450.0))
            )
            d.insertEntryWithItems(LedgerEntry(partyId = bilal, amount = 250.5, isGiven = false, entryNumber = ""), emptyList())
            d.insertCashEntry(CashEntry(amount = 1200.0, isIncome = false, category = "Rent"))
            d.insertCheque(Cheque(partyId = ali, amount = 5000.0, isReceived = true, dueDate = 1_790_000_000_000L))
            // As the app writes a product: its key and search name from ProductName, as a restore makes them again.
            d.insertProduct(Product(name = "Urea", nameKey = ProductName.key("Urea"), normalisedName = ProductName.normalised("Urea")))
            d.insertStaff(Staff(name = "Munshi", monthlySalary = 25000.0))

            val phoneSql = phone.syncSql()
            val id = LiveSync.enable(phoneSql, "install-phone", "Phone")
            // What the pairing sends: the phone's backup, read back on the computer and restored there.
            val text = StringWriter().also { BackupCodec.exportTo(d, "Shop", JSONObject(), it) }.toString()
            val (result, data) = BackupCodec.parseText(text)
            assertTrue("$result", result is BackupCodec.ImportResult.Ok)
            BackupCodec.restoreLedger(pc.khataDao(), data!!)
            val pcSql = pc.syncSql()
            LiveSync.enable(pcSql, "install-pc", "PC", adopt = id.book to id.key)

            val diff = differences(rowsOf(phoneSql), rowsOf(pcSql))
            assertEquals("rows that differ after pairing:\n" + diff.take(40).joinToString("\n"), emptyList<String>(), diff)
            assertEquals(LiveSync.fingerprint(phoneSql), LiveSync.fingerprint(pcSql))
        } finally {
            phone.close(); pc.close(); dir.deleteRecursively()
        }
    }
}
