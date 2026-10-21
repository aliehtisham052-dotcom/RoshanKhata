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
        // A header names its columns; a data row carries a figure somewhere.
        val hasHeader = first.none { amount(it) != null } && first.any { it.isNotEmpty() }
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
                i != phoneCol && column(i).filter { it.isNotEmpty() }.let { vs -> vs.isNotEmpty() && vs.count { amount(it) != null } * 2 >= vs.size }
            }
        }
        if (nameCol == null) {
            nameCol = (0 until width).firstOrNull { it != phoneCol && it != balanceCol } ?: return emptyList()
        }

        return body.mapNotNull { r ->
            val name = r.getOrNull(nameCol).orEmpty().trim().replace(Regex("\\s+"), " ")
            if (name.isEmpty()) return@mapNotNull null
            val phone = phoneCol?.let { r.getOrNull(it) }?.trim()?.takeIf { it.any(Char::isDigit) }
            val balance = balanceCol?.let { r.getOrNull(it) }?.let { amount(it) } ?: 0.0
            Row(name, phone, balance)
        }
    }

    /** "Rs 1,200.50", "-300", "(500)", "۱۲۰۰" -> a number; anything else -> null. */
    fun amount(raw: String): Double? {
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
        s = s.replace(Regex("(?i)rs\\.?|pkr|inr|rp|₹|৳|﷼"), "").replace(",", "").replace(" ", "").replace(" ", "")
        // The rupee's "/-" ("1,200/-") closes a figure; it is not a minus (21 Oct 2026: such a balance
        // was read as nothing, or as owed the other way).
        s = s.removeSuffix("/-").removeSuffix("/=")
        var negative = false
        if (s.startsWith("(") && s.endsWith(")")) { negative = true; s = s.substring(1, s.length - 1) }
        if (s.endsWith("-")) { negative = true; s = s.dropLast(1) }
        if (s.isEmpty() || s.none { it.isDigit() }) return null
        val v = s.toDoubleOrNull() ?: return null
        return if (negative) -v else v
    }

    /** RFC 4180 cells; the separator is whichever of , ; tab the first line uses most. */
    fun cells(text: String): List<List<String>> {
        val firstLine = text.lineSequence().firstOrNull().orEmpty()
        var quoted = false
        val counts = mutableMapOf(',' to 0, ';' to 0, '\t' to 0)
        firstLine.forEach { ch -> if (ch == '"') quoted = !quoted else if (!quoted && ch in counts) counts[ch] = counts.getValue(ch) + 1 }
        val sep = counts.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key ?: ','

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
                sep -> { row.add(cell.toString()); cell.clear() }
                '\r' -> {}
                '\n' -> { row.add(cell.toString()); cell.clear(); rows.add(row); row = mutableListOf() }
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row.add(cell.toString()); rows.add(row) }
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
