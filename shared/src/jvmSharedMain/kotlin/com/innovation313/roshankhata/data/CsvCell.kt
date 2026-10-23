package com.innovation313.roshankhata.data

/**
 * One CSV cell as every export writes it, phone and computer (R153, 23 Oct 2026): quoted, quotes
 * doubled, and a text that Excel would take as a formula made plain text.
 *
 * Excel drops the quotes while it reads a .csv, so a quoted name or note that begins with
 * `=`, `+`, `-`, `@`, a tab or a carriage return is still run as a formula (#NAME?, a wrong
 * figure in the accountant's SUM, or a HYPERLINK). Such a cell gets a leading `'` (OWASP's
 * advice); [CsvImport.cells] takes exactly that mark off again, so a file read back in reads
 * the same.
 *
 * Not guarded: a plain figure ("-1250", "-1250.50", "+92300…" as Excel reads it — no formula
 * can be made of digits, signs, dots and commas alone), so negative balances stay numbers that
 * SUM() adds; and ProductCsv's `="<digits>"`, the deliberate text form of a long barcode.
 */
object CsvCell {

    private val FIGURE = Regex("^[+-]?[0-9][0-9.,]*$")
    private val BARCODE_TEXT = Regex("^=\"[0-9]+\"$")

    private fun risky(c: Char) = c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r'

    /** [text] safe to open in a spreadsheet, not yet quoted. */
    fun guard(text: String): String =
        if (text.isEmpty() || !risky(text[0]) || FIGURE.matches(text) || BARCODE_TEXT.matches(text)) text else "'$text"

    /** [guard] undone: a cell read from a file this app wrote. Only the mark [guard] puts is taken off. */
    fun unguard(cell: String): String =
        if (cell.length >= 2 && cell[0] == '\'' && risky(cell[1])) cell.substring(1) else cell

    /** The cell as written in the file: guarded, quoted, its quotes doubled. */
    fun quote(text: String): String = "\"" + guard(text).replace("\"", "\"\"") + "\""

    /** One CSV line with its CRLF, every cell through [quote]. */
    fun appendRow(sb: StringBuilder, cells: List<String>) {
        cells.joinTo(sb, ",") { quote(it) }
        sb.append("\r\n")
    }
}
