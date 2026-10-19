package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The accountant's CSV, as Excel needs it, on both phone and computer. */
class LedgerCsvTest {

    private fun e(id: Long, name: String, amount: Double, given: Boolean, note: String?, at: Long) =
        EntryWithParty(id, 1, name, amount, given, note, "RK-$id", at)

    @Test
    fun `BOM, quoted cells, CRLF, oldest first, bare figures`() {
        val text = LedgerCsv.text(
            listOf(
                e(2, "Bilal", 500.5, false, "cash, \"part\"", 2_000_000_000_000),
                e(1, "علی", 1550.0, true, null, 1_000_000_000_000)
            ),
            listOf("Date", "Name", "Note", "Gave", "Got")
        )
        assertTrue(text.startsWith("﻿\"Date\",\"Name\""))
        val lines = text.removePrefix("﻿").split("\r\n")
        assertEquals(4, lines.size) // header, two rows, and the empty after the last CRLF
        assertTrue(lines[1].contains("\"علی\",\"\",\"1550\",\"\""))
        assertTrue(lines[2].endsWith("\"Bilal\",\"cash, \"\"part\"\"\",\"\",\"500.50\""))
    }
}
