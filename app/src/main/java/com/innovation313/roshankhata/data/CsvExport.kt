package com.innovation313.roshankhata.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The ledger report as a CSV — the accountant's copy.
 *
 * A PDF is for reading; this is for working on: tax, totals, moving to a
 * computer. Written so that EXCEL specifically opens it right, because that
 * is the program it will actually be opened in:
 *
 * - UTF-8 with a BOM. Without the BOM, Excel guesses the encoding and every
 *   Urdu customer name arrives as mojibake. The BOM is three bytes that make
 *   the guess unnecessary.
 * - CRLF line ends, which every Excel on Windows expects.
 * - Fields quoted, with quotes doubled — a note containing a comma or a
 *   newline must not shift every column after it.
 * - Amounts as bare numbers in separate Gave/Got columns, no "Rs" and no
 *   thousands commas, so SUM() works on them immediately.
 */
object CsvExport {

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH)
    private val fileFmt = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.ENGLISH)

    /** @return the written file, or null if it could not be written. */
    fun ledger(context: Context, entries: List<EntryWithParty>): File? {
        return try {
            val sb = StringBuilder()
            sb.append('\uFEFF') // BOM — see the header comment.
            row(sb, listOf("Date", "Name", "Note", "I Gave", "I Got"))
            // Oldest first, the order a book is read and a spreadsheet is
            // summed — the screen's newest-first is for glancing, not for
            // working.
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

            val dir = File(context.cacheDir, "statements").apply { mkdirs() }
            val file = File(dir, "RoshanKhata_Report_${fileFmt.format(Date())}.csv")
            file.writeText(sb.toString(), Charsets.UTF_8)
            file
        } catch (e: Exception) {
            null
        }
    }

    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, file.name)
        )
    }

    /** A whole number stays whole; paise appear only when they exist. */
    private fun plain(value: Double): String =
        if (value % 1.0 == 0.0) "%.0f".format(value) else "%.2f".format(value)

    private fun row(sb: StringBuilder, cells: List<String>) {
        cells.joinTo(sb, ",") { "\"" + it.replace("\"", "\"\"") + "\"" }
        sb.append("\r\n")
    }
}
