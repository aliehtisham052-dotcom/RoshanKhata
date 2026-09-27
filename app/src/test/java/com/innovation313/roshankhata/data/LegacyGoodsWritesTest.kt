package com.innovation313.roshankhata.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Goods have ONE home from v20: the entry_items table.
 *
 * The five goods columns still on `transactions` (itemName, quantity, unit,
 * productId, billItemId) are legacy — copied into lines by migration 19→20
 * and frozen. If new code ever wrote them again, the same fact would live in
 * two places, and the first edit that updated only one would make the khata
 * screen, the stock count and the register disagree about what a customer
 * took. This reads the source and fails the build before that can ship.
 *
 * Two places are allowed, and only these:
 *  - Backup.kt's jsonToEntry, which puts a restored entry's legacy columns
 *    back EXACTLY as the file had them (a restore must not alter a record).
 *  - Nothing else.
 */
class LegacyGoodsWritesTest {

    private val legacy = Regex("""\b(itemName|quantity|unit|productId|billItemId)\s*=""")

    private fun sourceRoot(): File =
        listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }

    /** Every `LedgerEntry( … )` call, as (file, line, argument text). */
    private fun ledgerEntryCalls(): List<Triple<String, Int, String>> {
        val found = mutableListOf<Triple<String, Int, String>>()
        sourceRoot().walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            val text = file.readText()
            Regex("""\bLedgerEntry\(""").findAll(text).forEach { m ->
                var i = m.range.last + 1
                var depth = 1
                while (depth > 0 && i < text.length) {
                    when (text[i]) {
                        '(' -> depth++
                        ')' -> depth--
                    }
                    i++
                }
                val line = text.substring(0, m.range.first).count { it == '\n' } + 1
                found += Triple(file.name, line, text.substring(m.range.last + 1, i))
            }
        }
        return found
    }

    @Test
    fun `no code creates an entry with goods on it, except restoring a backup`() {
        val calls = ledgerEntryCalls()
        assertTrue("found no LedgerEntry( calls at all — the scan is broken", calls.isNotEmpty())

        val offenders = calls.filter { (file, _, args) ->
            file != "Backup.kt" && legacy.containsMatchIn(args)
        }
        assertTrue(
            "These build a LedgerEntry with legacy goods columns — goods belong " +
                "in EntryItem lines now (see EntryItem.ofGoods):\n" +
                offenders.joinToString("\n") { "  ${it.first}:${it.second}" },
            offenders.isEmpty()
        )
    }

    @Test
    fun `no SQL writes the legacy goods columns of transactions`() {
        val dao = sourceRoot().walkTopDown().first { it.name == "KhataDao.kt" }.readText()
        val writes = Regex(
            """UPDATE\s+transactions\s+SET\s+[^"]*\b(itemName|quantity|unit|productId|billItemId)\s*=""",
            RegexOption.IGNORE_CASE
        ).findAll(dao).map { it.value }.toList()
        assertTrue(
            "KhataDao updates legacy goods columns on transactions: $writes",
            writes.isEmpty()
        )
    }
}
