package com.innovation313.roshankhata.data

/**
 * Customers from another ledger app (balance-only, as agreed): a CSV of name,
 * phone and current balance becomes one party each with one opening entry.
 *
 * Other apps differ in everything but the idea, so the reader is forgiving:
 * comma, semicolon or tab between cells; quoted cells (a comma or a line
 * break inside a name); a header row in any of the app's nine languages or
 * none at all; amounts written "Rs 1,200", "1,200.50", "(500)" or in Urdu or
 * Arabic digits. What it cannot read it leaves out rather than guesses: a
 * row without a name is skipped, a balance that is not a number is 0.
 *
 * The sign is the book's: positive, they owe the shop (an opening "you
 * gave"); negative, the shop owes them. Apps that write it the other way
 * are handled by the caller's flip, shown to the owner before anything is
 * written.
 */
object CsvImport {

    data class Row(val name: String, val phone: String?, val balance: Double)

    private val NAME_WORDS = listOf(
        "name", "naam", "nama", "customer", "party", "نام", "اسم", "گاہک", "نالو", "नाम", "নাম"
    )
    private val PHONE_WORDS = listOf(
        "phone", "mobile", "contact", "number", "nomor", "telepon", "whatsapp",
        "فون", "موبائل", "هاتف", "تلفن", "موبایل", "फ़ोन", "फोन", "मोबाइल", "ফোন", "মোবাইল"
    )
    private val BALANCE_WORDS = listOf(
        "balance", "amount", "due", "baqaya", "baqi", "saldo", "outstanding",
        "بیلنس", "بيلنس", "رقم", "باقی", "بقایا", "رصيد", "مانده", "بیلانس",
        "बैलेंस", "बकाया", "शेष", "राशि", "ব্যালেন্স", "বাকি"
    )

    fun parse(text: String): List<Row> {
        val table = cells(text.removePrefix("﻿")).filter { r -> r.any { it.isNotBlank() } }
        if (table.isEmpty()) return emptyList()
        val first = table.first().map { it.trim().lowercase() }
        val dots = dotGroups(text)
        fun figure(s: String): Double? = amount(s, dots)
        // A header names its columns; a data row carries a figure somewhere.
        val hasHeader = first.none { figure(it) != null } && first.any { it.isNotEmpty() }
        val body = if (hasHeader) table.drop(1) else table
        if (body.isEmpty()) return emptyList()
        val width = body.maxOf { it.size }

        fun find(words: List<String>, taken: Set<Int>): Int? =
            if (!hasHeader) null
            else first.indices.firstOrNull { i -> i !in taken && words.any { first[i].contains(it) } }

        var balanceCol = find(BALANCE_WORDS, emptySet())
        var phoneCol = find(PHONE_WORDS, setOfNotNull(balanceCol))
        var nameCol = find(NAME_WORDS, setOfNotNull(balanceCol, phoneCol))

        fun column(i: Int) = body.map { it.getOrNull(i).orEmpty().trim() }
        fun phoneLike(v: String) = v.count { it.isDigit() } in 10..15 && v.all { it.isDigit() || it in "+-() " }
        if (phoneCol == null) {
            phoneCol = (0 until width).firstOrNull { i ->
                i != balanceCol && column(i).filter { it.isNotEmpty() }.let { vs -> vs.isNotEmpty() && vs.count(::phoneLike) * 2 >= vs.size }
            }
        }
        if (balanceCol == null) {
            // The right-most column that is mostly figures and is not the phone.
            balanceCol = (width - 1 downTo 0).firstOrNull { i ->
                i != phoneCol && column(i).filter { it.isNotEmpty() }.let { vs -> vs.isNotEmpty() && vs.count { figure(it) != null } * 2 >= vs.size }
            }
        }
        if (nameCol == null) {
            nameCol = (0 until width).firstOrNull { it != phoneCol && it != balanceCol } ?: return emptyList()
        }

        return body.mapNotNull { r ->
            val name = r.getOrNull(nameCol).orEmpty().trim().replace(Regex("\\s+"), " ")
            if (name.isEmpty()) return@mapNotNull null
            val phone = phoneCol?.let { r.getOrNull(it) }?.trim()?.takeIf { it.any(Char::isDigit) }
            val balance = balanceCol?.let { r.getOrNull(it) }?.let { figure(it) } ?: 0.0
            Row(name, phone, balance)
        }
    }

    /**
     * Whether a lone dot before three digits ("15.000") groups thousands in
     * this file: the reader's language writes figures so ([Format.money]
     * prints "Rp 15.000" in Indonesian), or the file is semicolon-separated,
     * which is what a spreadsheet set to a comma-decimal region writes.
     */
    fun dotGroups(text: String): Boolean =
        separatorOf(text.removePrefix("\uFEFF")) == ';' || readerGroupsWithDots()

    private fun readerGroupsWithDots(): Boolean =
        java.util.Locale.getDefault().language.let { it == "in" || it == "id" }

    /**
     * "Rs 1,200.50", "-300", "(500)", "۱۲۰۰", "Rp 15.000", "1.250,50", "12,5"
     * -> a number; anything else -> null.
     *
     * Which mark is the decimal one (23 Oct 2026, the review: an Indonesian
     * sheet's "15.000" became 15 and "12,5" became 125): with both marks, the
     * last is the decimal and the other groups; a mark used twice or more
     * groups ("1.500.000", "1,50,000"); a lone comma before exactly three
     * digits groups ("1,200"), before one or two it is the decimal ("12,5");
     * a lone dot before exactly three digits groups only when [groupDots] (see
     * [dotGroups]) or the cell says "Rp", and is otherwise the decimal point
     * as before.
     */
    fun amount(raw: String, groupDots: Boolean = readerGroupsWithDots()): Double? {
        var s = buildString {
            raw.trim().forEach { ch ->
                append(
                    when (ch) {
                        in '٠'..'٩' -> '0' + (ch - '٠')
                        in '۰'..'۹' -> '0' + (ch - '۰')
                        in '०'..'९' -> '0' + (ch - '०')
                        in '০'..'৯' -> '0' + (ch - '০')
                        '٫' -> '.'
                        '٬', '،' -> ','
                        '−' -> '-'
                        else -> ch
                    }
                )
            }
        }
        val rupiah = Regex("(?i)rp").containsMatchIn(s)
        s = s.replace(Regex("(?i)rs\\.?|pkr|inr|rp|₹|৳|﷼"), "").replace(" ", "").replace(" ", "")
        // The rupee's "/-" ("1,200/-") closes a figure; it is not a minus (21 Oct 2026: such a balance
        // was read as nothing, or as owed the other way).
        s = s.replace("/-", "").replace("/=", "")
        var negative = false
        if (s.startsWith("(") && s.endsWith(")")) { negative = true; s = s.substring(1, s.length - 1) }
        if (s.endsWith("-")) { negative = true; s = s.dropLast(1) }
        s = decimalPoint(s, groupDots || rupiah)
        if (s.isEmpty() || s.none { it.isDigit() }) return null
        val v = s.toDoubleOrNull() ?: return null
        return if (negative) -v else v
    }

    /** [s] with only digits, signs and at most one '.' as the decimal point; see [amount]. */
    private fun decimalPoint(s: String, dotGroups: Boolean): String {
        val lastDot = s.lastIndexOf('.')
        val lastComma = s.lastIndexOf(',')
        val dots = s.count { it == '.' }
        val commas = s.count { it == ',' }
        fun threeAfter(i: Int) = s.length - i - 1 == 3 && s.substring(i + 1).all { it.isDigit() }
        return when {
            dots > 0 && commas > 0 ->
                if (lastComma > lastDot) s.replace(".", "").replace(',', '.') else s.replace(",", "")
            commas > 1 -> s.replace(",", "")
            commas == 1 -> if (threeAfter(lastComma)) s.replace(",", "") else s.replace(',', '.')
            dots > 1 -> s.replace(".", "")
            dots == 1 && dotGroups && threeAfter(lastDot) -> s.replace(".", "")
            else -> s
        }
    }

    /** Whichever of , ; tab the first line uses most (outside quotes); a comma when none. */
    private fun separatorOf(text: String): Char {
        val firstLine = text.lineSequence().firstOrNull().orEmpty()
        var quoted = false
        val counts = mutableMapOf(',' to 0, ';' to 0, '\t' to 0)
        firstLine.forEach { ch -> if (ch == '"') quoted = !quoted else if (!quoted && ch in counts) counts[ch] = counts.getValue(ch) + 1 }
        return counts.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key ?: ','
    }

    /** RFC 4180 cells; the separator is whichever of , ; tab the first line uses most. */
    fun cells(text: String): List<List<String>> {
        val sep = separatorOf(text)

        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { cell.append('"'); i++ } else inQuotes = false
                } else cell.append(ch)
            } else when (ch) {
                '"' -> inQuotes = true
                sep -> { row.add(CsvCell.unguard(cell.toString())); cell.clear() }
                '\r' -> {}
                '\n' -> { row.add(CsvCell.unguard(cell.toString())); cell.clear(); rows.add(row); row = mutableListOf() }
                else -> cell.append(ch)
            }
            i++
        }
        // A cell this app's own export marked as text (CsvCell.guard) reads back as it was written (R153).
        if (cell.isNotEmpty() || row.isNotEmpty()) { row.add(CsvCell.unguard(cell.toString())); rows.add(row) }
        return rows
    }

    /** Same person already in the book: same name (any case, any spacing) and, when both have one, the same number. */
    fun alreadyIn(row: Row, existing: List<PartyWithBalance>): Boolean {
        val key = row.name.lowercase().replace(Regex("\\s+"), " ")
        val digits = row.phone?.filter(Char::isDigit)?.takeLast(10)
        return existing.any { p ->
            p.name.trim().lowercase().replace(Regex("\\s+"), " ") == key &&
                (digits.isNullOrEmpty() || p.phone.isNullOrBlank() || p.phone!!.filter(Char::isDigit).takeLast(10) == digits)
        }
    }
}
