package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.LedgerCsv
import com.innovation313.roshankhata.data.PartyWithBalance
import java.awt.Graphics
import java.awt.print.PageFormat
import java.awt.print.Printable

/**
 * The whole book out of the computer (Phase 9b).
 *
 * [customersCsv]: every customer and supplier with phone and balance, for
 * Excel — the same rules as the ledger CSV (BOM so Urdu names open right,
 * quoted cells, CRLF, bare figures so SUM() works). Positive: they owe the
 * shop; negative: the shop owes them, as in the book.
 *
 * [AllStatements]: many customers' statements as one print job, each
 * starting on its own page — one trip to the printer, or one PDF.
 */
object BookExport {
    fun customersCsv(
        parties: List<PartyWithBalance>,
        headers: List<String>,
        /** A last column saying customer or supplier, in the reader's words (25 Oct 2026, the audit); none when null. */
        typeOf: ((PartyWithBalance) -> String)? = null
    ): String {
        val sb = StringBuilder("﻿")
        fun row(cells: List<String>) = com.innovation313.roshankhata.data.CsvCell.appendRow(sb, cells)
        row(headers)
        com.innovation313.roshankhata.data.NameOrder.sortedBy(parties) { it.name }.forEach { p ->
            row(listOf(p.name, p.phone.orEmpty(), LedgerCsv.plain(p.balance)) + listOfNotNull(typeOf?.invoke(p)))
        }
        return sb.toString()
    }
}

class AllStatements(private val sheets: List<StatementPrint>) : Printable {
    private val starts: List<Int> = sheets.runningFold(0) { acc, s -> acc + s.pageCount() }

    /** How many pages, without drawing any (21 Oct 2026: counting by drawing every page took minutes). */
    fun pageCount(): Int = starts.last()

    override fun print(graphics: Graphics, format: PageFormat, pageIndex: Int): Int {
        val i = starts.indexOfLast { it <= pageIndex }.takeIf { it in sheets.indices } ?: return Printable.NO_SUCH_PAGE
        return sheets[i].print(graphics, format, pageIndex - starts[i])
    }
}
