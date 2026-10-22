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

    private fun rowsOf(sql: SyncSql): Map<String, JSONObject> = runBlocking {
        LiveSync.snapshot(sql).changes.associate { (it.table + "/" + it.uid) to it.row!! }
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
            d.insertProduct(Product(name = "Urea", nameKey = "urea", normalisedName = "urea"))
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
