package com.innovation313.roshankhata.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.innovation313.roshankhata.R
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

    private val fileFmt = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.ENGLISH)

    /** @return the written file, or null if it could not be written. */
    fun ledger(context: Context, entries: List<EntryWithParty>): File? {
        return try {
            // The column names in the app's language, like every other word the
            // owner reads; the text itself is shared with the computer (LedgerCsv).
            val text = LedgerCsv.text(
                entries,
                listOf(
                    context.getString(R.string.date_label),
                    context.getString(R.string.name),
                    context.getString(R.string.note_label),
                    context.getString(R.string.i_gave),
                    context.getString(R.string.i_got)
                )
            )
            val dir = File(context.cacheDir, "statements").apply { mkdirs() }
            val file = File(dir, "RoshanKhata_Report_${fileFmt.format(Date())}.csv")
            file.writeText(text, Charsets.UTF_8)
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
}
