package com.innovation313.roshankhata.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The ledger report as CSV text — the accountant's copy, the same on the
 * phone (CsvExport) and the computer (KMP Phase 7e). Written so that EXCEL
 * opens it right, because that is the program it will be opened in:
 *
 * - UTF-8 with a BOM. Without it Excel guesses the encoding and every Urdu
 *   name arrives as mojibake.
 * - CRLF line ends, which every Excel on Windows expects.
 * - Fields quoted, with quotes doubled — a note with a comma or a newline
 *   must not shift every column after it.
 * - Amounts as bare numbers in separate Gave/Got columns, no "Rs" and no
 *   thousands commas, so SUM() works on them at once.
 */
object LedgerCsv {

    /**
     * [headers]: date, name, note, gave, got — in the app's language. Rows go
     * oldest first, the order a book is read and a spreadsheet summed.
     */
    fun text(entries: List<EntryWithParty>, headers: List<String>): String {
        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH)
        val sb = StringBuilder()
        sb.append('﻿')
        row(sb, headers)
        entries.sortedBy { it.timestamp }.forEach { e ->
            row(
                sb,
                listOf(
                    dateFmt.format(Date(e.timestamp)),
                    e.partyName,
                    e.note.orEmpty(),
                    if (e.isGiven) plain(e.amount) else "",
                    if (!e.isGiven) plain(e.amount) else ""
                )
            )
        }
        return sb.toString()
    }

    /** A whole number stays whole; paise appear only when they exist. */
    fun plain(value: Double): String =
        if (value % 1.0 == 0.0) "%.0f".format(Digits.FIGURES, value) else "%.2f".format(Digits.FIGURES, value)

    // Quoted, and a name or note Excel would run as a formula made text (CsvCell, R153).
    private fun row(sb: StringBuilder, cells: List<String>) = CsvCell.appendRow(sb, cells)
}
