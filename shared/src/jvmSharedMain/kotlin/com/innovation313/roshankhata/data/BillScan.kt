package com.innovation313.roshankhata.data

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/** One line of text the phone read off a photo, with where it sat on the page. */
data class OcrLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

/** One product line read off a supplier's bill, before the owner has checked it. */
data class ScannedItem(
    val name: String,
    val quantity: Double,
    val rate: Double,
    val amount: Double,
    val batch: String? = null,
    val expiry: Long? = null,
    val unit: String? = null
)

/** Everything read off one bill. Any field may be missing; nothing here is saved as-is. */
data class ScannedBill(
    /** The company the bill is from: its printed heading, or the shop's own spelling of it. */
    val supplierName: String? = null,
    val billNumber: String? = null,
    val billDate: Long? = null,
    val total: Double? = null,
    val items: List<ScannedItem> = emptyList()
) {
    val itemsTotal: Double get() = items.sumOf { it.amount }
    val isEmpty: Boolean get() =
        supplierName == null && billNumber == null && billDate == null && total == null && items.isEmpty()
}

/**
 * Reading a supplier's printed bill into the bill form (1 Oct).
 *
 * The phone's text reader hands back lines of text and where each sat. This
 * turns them into a bill number, a date, a total and product lines — and is
 * deliberately strict, because what it produces lands in a financial record:
 *
 *  - A product line is accepted ONLY when its own figures agree: quantity
 *    times rate equals the line amount (within 1%, or a rupee). A misread
 *    digit breaks that sum, so a misread line is dropped rather than filled
 *    in wrong. Lines with a discount or tax column do not add up this way
 *    and are left for the owner to type; a missed line is visible, a wrong
 *    one might not be.
 *  - Nothing is saved from here. Every field goes to the owner's form, and
 *    the review shows whether the lines add up to the bill's own total.
 *  - Only printed Latin-script bills (the pesticide and seed companies'
 *    invoices) are readable: the phone's on-device reader has no Urdu.
 *
 * Pure Kotlin, no Android, so every rule here is unit-tested.
 */
object BillScan {

    // ------------------------------------------------------------- rows

    /**
     * Lines that sit at the same height are one row of the bill, read left to
     * right. A table's columns come back as separate lines; this puts
     * "Coragen" and its "10  2,800  28,000" back on one row.
     */
    fun rows(lines: List<OcrLine>): List<String> {
        data class Row(var top: Int, var bottom: Int, val parts: MutableList<OcrLine>)

        val rows = mutableListOf<Row>()
        for (line in lines.filter { it.text.isNotBlank() }.sortedBy { (it.top + it.bottom) / 2 }) {
            val cy = (line.top + line.bottom) / 2
            val h = (line.bottom - line.top).coerceAtLeast(1)
            val row = rows.lastOrNull()?.takeIf { r ->
                val rh = (r.bottom - r.top).coerceAtLeast(1)
                abs(cy - (r.top + r.bottom) / 2) <= minOf(h, rh) * 0.6
            }
            if (row == null) {
                rows += Row(line.top, line.bottom, mutableListOf(line))
            } else {
                row.parts += line
                row.top = minOf(row.top, line.top)
                row.bottom = maxOf(row.bottom, line.bottom)
            }
        }
        return rows.map { r -> r.parts.sortedBy { it.left }.joinToString("  ") { it.text.trim() } }
    }

    // ------------------------------------------------------------ parse

    fun parse(
        lines: List<OcrLine>,
        knownProducts: List<String> = emptyList(),
        now: Long = System.currentTimeMillis(),
        tz: TimeZone = TimeZone.getDefault(),
        knownSuppliers: List<String> = emptyList()
    ): ScannedBill {
        val fromRows = parseRows(rows(lines), knownProducts, now, tz, knownSuppliers)
        val heading = supplierFromLines(lines)?.let { tidyName(it, knownSuppliers) }
        return if (heading != null) fromRows.copy(supplierName = heading) else fromRows
    }

    fun parseRows(
        rows: List<String>,
        knownProducts: List<String> = emptyList(),
        now: Long = System.currentTimeMillis(),
        tz: TimeZone = TimeZone.getDefault(),
        knownSuppliers: List<String> = emptyList()
    ): ScannedBill {
        val supplier = supplierFromRows(rows)?.let { tidyName(it, knownSuppliers) }
        val billNumber = rows.firstNotNullOfOrNull { billNumberIn(it) }
            ?: rows.firstNotNullOfOrNull { shortBillNumberIn(it) }
        val billDate = billDateIn(rows, tz)
        val total = totalIn(rows)
        val rateBeforeQty = rows.any { headerSaysRateFirst(it) }

        val items = rows
            .filterNot { isSummaryRow(it) }
            .mapNotNull { itemIn(it, rateBeforeQty, billDate ?: now, tz) }
            .map { it.copy(name = knownName(it.name, knownProducts)) }

        return ScannedBill(supplier, billNumber, billDate, total, items)
    }

    // ------------------------------------------------------ bill number

    private val BILL_NO = Regex(
        """\b(?:invoice|inv|bill|challan|voucher|memo|receipt)\s*(?:no|num|number|#)?\.?\s*[:#\-]?\s*([A-Za-z0-9][A-Za-z0-9\-/]{1,24})""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Distributors number their paper as a supply order or delivery challan
     * ("S.O No.", "D.C No.", "Order #", "Ref No."). Short labels, so "No",
     * "#" or "Number" must follow them, or "so 12" in a sentence would count.
     * Tried only when no invoice or bill number was found.
     */
    private val SHORT_NO = Regex(
        """\b(?:s\.?\s*o|d\.?\s*c|order|ref|gp)\.?\s*(?:no|num|number|#)\.?\s*[:#\-]?\s*([A-Za-z0-9][A-Za-z0-9\-/]{1,24})""",
        RegexOption.IGNORE_CASE
    )

    private fun shortBillNumberIn(row: String): String? =
        SHORT_NO.findAll(row).map { it.groupValues[1] }
            .firstOrNull { v -> v.any { it.isDigit() } && !looksLikeDate(v) }

    private fun billNumberIn(row: String): String? =
        BILL_NO.findAll(row).map { it.groupValues[1] }
            .firstOrNull { v -> v.any { it.isDigit() } && !looksLikeDate(v) }

    // ------------------------------------------------------------- dates

    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    private val DMY = Regex("""\b(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{2,4})\b""")
    private val D_MON_Y = Regex(
        """\b(\d{1,2})[\s\-.]*(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\b[\s\-.,]*(\d{2,4})\b""",
        RegexOption.IGNORE_CASE
    )
    private val MON_Y = Regex(
        """\b(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\b[\s\-./,]*(\d{2,4})\b""",
        RegexOption.IGNORE_CASE
    )
    private val M_YYYY = Regex("""(?<![\d/.\-])(\d{1,2})[/\-](20\d{2})\b""")

    private fun looksLikeDate(s: String) = DMY.containsMatchIn(s) || M_YYYY.containsMatchIn(s)

    private fun year(y: String): Int = y.toInt().let { if (y.length == 2) 2000 + it else it }

    private fun dayMillis(d: Int, m: Int, y: Int, tz: TimeZone): Long? {
        if (m !in 1..12 || d !in 1..31 || y !in 2000..2099) return null
        val c = Calendar.getInstance(tz).apply {
            clear()
            isLenient = false
            set(y, m - 1, d, 12, 0, 0)
        }
        return runCatching { c.timeInMillis }.getOrNull()
    }

    private fun endOfMonth(m: Int, y: Int, tz: TimeZone): Long? {
        if (m !in 1..12 || y !in 2000..2099) return null
        val c = Calendar.getInstance(tz).apply { clear(); set(y, m - 1, 1, 12, 0, 0) }
        c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH))
        return c.timeInMillis
    }

    /** Every date in [text]: full dates as that day, month-and-year as the month's last day. */
    private fun datesIn(text: String, tz: TimeZone): List<Long> {
        val out = mutableListOf<Long>()
        var rest = text
        for (m in DMY.findAll(text)) {
            dayMillis(m.groupValues[1].toInt(), m.groupValues[2].toInt(), year(m.groupValues[3]), tz)?.let { out += it }
            rest = rest.replace(m.value, " ")
        }
        for (m in D_MON_Y.findAll(rest)) {
            val mon = MONTHS.indexOf(m.groupValues[2].lowercase(Locale.ROOT).take(3)) + 1
            dayMillis(m.groupValues[1].toInt(), mon, year(m.groupValues[3]), tz)?.let { out += it }
            rest = rest.replace(m.value, " ")
        }
        for (m in MON_Y.findAll(rest)) {
            val mon = MONTHS.indexOf(m.groupValues[1].lowercase(Locale.ROOT).take(3)) + 1
            endOfMonth(mon, year(m.groupValues[2]), tz)?.let { out += it }
            rest = rest.replace(m.value, " ")
        }
        for (m in M_YYYY.findAll(rest)) {
            endOfMonth(m.groupValues[1].toInt(), m.groupValues[2].toInt(), tz)?.let { out += it }
        }
        return out
    }

    private val NOT_BILL_DATE = Regex("""\b(exp|expiry|mfg|mfd|manuf\w*|due|batch)\b""", RegexOption.IGNORE_CASE)
    private val DATE_LABEL = Regex("""\bdate\b|\bdated\b""", RegexOption.IGNORE_CASE)

    /** The bill's own date: a row labelled "Date" first, else the first date outside the product lines. */
    private fun billDateIn(rows: List<String>, tz: TimeZone): Long? {
        rows.filter { DATE_LABEL.containsMatchIn(it) && !NOT_BILL_DATE.containsMatchIn(it) }
            .firstNotNullOfOrNull { datesIn(it, tz).firstOrNull() }
            ?.let { return it }
        return rows.filter { !NOT_BILL_DATE.containsMatchIn(it) && numbersIn(it).size < 3 }
            .firstNotNullOfOrNull { datesIn(it, tz).firstOrNull() }
    }

    // ------------------------------------------------------------ numbers

    private data class Num(val value: Double, val token: Int)

    private val NUMBER = Regex("""^(?:rs\.?|pkr)?([0-9][0-9,]*(?:\.[0-9]{1,2})?)(?:/-|/=|-)?$""", RegexOption.IGNORE_CASE)

    private fun tokens(row: String): List<String> = row.split(Regex("""\s+""")).filter { it.isNotBlank() }

    private fun numberOf(token: String): Double? {
        val m = NUMBER.matchEntire(token.trim(':', ';', '|')) ?: return null
        return m.groupValues[1].replace(",", "").toDoubleOrNull()
    }

    private fun numbersIn(row: String): List<Num> =
        tokens(row).mapIndexedNotNull { i, t -> numberOf(t)?.let { Num(it, i) } }

    // -------------------------------------------------------------- total

    private val SUMMARY = Regex(
        """\b(total|sub\s*total|grand|net|payable|balance|discount|disc|tax|gst|sales\s*tax|freight|carriage|paid|previous|advance|round(?:\s*off)?)\b""",
        RegexOption.IGNORE_CASE
    )

    private fun isSummaryRow(row: String) = SUMMARY.containsMatchIn(row)

    private val SUB_TOTAL = Regex("""\bsub\s*-?total""", RegexOption.IGNORE_CASE)

    private val TOTAL_RANK = listOf(
        Regex("""\b(grand\s*total|net\s*total|net\s*amount|net\s*payable|amount\s*payable|total\s*payable|bill\s*amount|invoice\s*total)""", RegexOption.IGNORE_CASE),
        Regex("""\btotal\s*amount\b""", RegexOption.IGNORE_CASE),
        Regex("""(?<!sub)(?<!sub\s)\btotal\b""", RegexOption.IGNORE_CASE)
    )

    /**
     * The bill's total: the best-labelled total row, its last figure; among
     * rows of equal rank, the lowest on the page, where a bill ends.
     */
    private fun totalIn(rows: List<String>): Double? {
        for (rank in TOTAL_RANK) {
            rows.filter {
                rank.containsMatchIn(it) && !SUB_TOTAL.containsMatchIn(it) && !QTY_HEAD.containsMatchIn(it) &&
                    !Regex("""\b(items?|pcs|units)\b""", RegexOption.IGNORE_CASE).containsMatchIn(it)
            }
                .lastOrNull { numbersIn(it).isNotEmpty() }
                ?.let { row -> return numbersIn(row).last().value.takeIf { it > 0 } }
        }
        return null
    }

    // ----------------------------------------------------------- supplier

    /**
     * Words that mark a line as anything but the company's name: the title,
     * labels, the address, the CUSTOMER (which is the shop itself — the
     * worst possible thing to file a bill under).
     */
    private val NOT_A_NAME = Regex(
        """\b(invoice|bill|order|challan|receipt|memo|quotation|estimate|customer|name|address|date|phone|ph|tel|cell|mobile|mob|ntn|gst|strn|cnic|road|street|bazar|bazaar|market|tehsil|district|distt|city|territory|total|thank|page|tax|sales)\b""",
        RegexOption.IGNORE_CASE
    )

    private fun couldBeName(text: String): Boolean {
        val t = text.trim()
        return t.length in 4..48 && t.count { it.isLetter() } >= 4 && t.none { it.isDigit() } &&
            !NOT_A_NAME.containsMatchIn(t)
    }

    /** The tallest line in the top part of the page that could be a name: the printed heading. */
    private fun supplierFromLines(lines: List<OcrLine>): String? {
        if (lines.isEmpty()) return null
        val top = lines.minOf { it.top }
        val bottom = lines.maxOf { it.bottom }
        val band = top + (bottom - top) * 0.3
        return lines.filter { it.top <= band && couldBeName(it.text) }
            .maxByOrNull { it.bottom - it.top }
            ?.text?.trim()
    }

    /** Without positions: the first name-like row among the first few. */
    private fun supplierFromRows(rows: List<String>): String? =
        rows.take(4).firstOrNull { couldBeName(it) }?.trim()

    /** The book's own spelling when this supplier is already in it; else the heading in Title Case. */
    private fun tidyName(read: String, known: List<String>): String {
        val matched = knownName(read, known)
        if (matched != read) return matched
        val letters = read.filter { it.isLetter() }
        return if (letters.isNotEmpty() && letters.all { it.isUpperCase() }) {
            read.lowercase(Locale.ROOT).split(Regex("""\s+""")).joinToString(" ") { w ->
                w.replaceFirstChar { it.titlecase(Locale.ROOT) }
            }
        } else read
    }

    // -------------------------------------------------------------- items

    private val QTY_HEAD = Regex("""\b(qty|quantity|qnty)\b""", RegexOption.IGNORE_CASE)
    private val RATE_HEAD = Regex("""\b(rate|price|unit\s*price|t\.?p\.?)\b""", RegexOption.IGNORE_CASE)

    /** A header row that lists Rate before Qty, so the two are read the other way round. */
    private fun headerSaysRateFirst(row: String): Boolean {
        val q = QTY_HEAD.find(row) ?: return false
        val r = RATE_HEAD.find(row) ?: return false
        return r.range.first < q.range.first
    }

    private val UNITS = mapOf(
        "bag" to "bag", "bags" to "bag", "kg" to "kg", "kgs" to "kg",
        "bottle" to "bottle", "bottles" to "bottle", "btl" to "bottle",
        "packet" to "packet", "packets" to "packet", "pkt" to "packet", "pkts" to "packet",
        "litre" to "litre", "litres" to "litre", "liter" to "litre", "ltr" to "litre", "ltrs" to "litre",
        "piece" to "piece", "pieces" to "piece", "pcs" to "piece", "pc" to "piece",
        "box" to "box", "boxes" to "box", "ton" to "ton", "tons" to "ton"
    )

    /** Pack sizes and formulations that belong to the product's name: 100ml, 1L, 20SC, 5EC, 75WG. */
    private val PACK = Regex("""^\d+(\.\d+)?(ml|l|ltr|g|gm|gms|kg|sc|ec|wp|wg|wdg|sl|gr|sp|cs|od|%)$""", RegexOption.IGNORE_CASE)

    /** Letters and digits together, no lower case, as batch numbers are printed: B2401, AB-1234. */
    private fun looksLikeBatch(t: String): Boolean {
        val s = t.trim(',', ';', ':', '|')
        return s.length in 3..16 && s.any { it.isDigit() } && s.any { it.isLetter() } &&
            s.none { it.isLowerCase() } && !PACK.matches(s) && s.all { it.isLetterOrDigit() || it == '-' || it == '/' }
    }

    private val BATCH_LABEL = Regex("""\b(?:batch|b\.?\s*no)\.?\s*[:#\-]?\s*([A-Za-z0-9][A-Za-z0-9\-/]{2,15})""", RegexOption.IGNORE_CASE)

    private fun itemIn(row: String, rateFirst: Boolean, billDay: Long, tz: TimeZone): ScannedItem? {
        val nums = numbersIn(row)
        if (nums.size < 3) return null

        // The figures that agree: q x r = a, the amount after both. The one
        // furthest right wins, since the line amount is the last column.
        var best: Triple<Num, Num, Num>? = null
        search@ for (k in nums.indices.reversed()) {
            val a = nums[k].value
            if (a <= 0) continue
            for (j in (k - 1) downTo 1) for (i in (j - 1) downTo 0) {
                val x = nums[i].value
                val y = nums[j].value
                if (x <= 0 || y <= 0) continue
                if (abs(x * y - a) <= maxOf(1.0, a * 0.01)) {
                    best = Triple(nums[i], nums[j], nums[k])
                    break@search
                }
            }
        }
        val (first, second, amount) = best ?: return null
        val qty = if (rateFirst) second.value else first.value
        val rate = if (rateFirst) first.value else second.value

        val toks = tokens(row)
        // Dates out first, whole: "Mar 2027" is two words and neither alone
        // looks like a date, so they are cut from the text before it is split.
        var namePart = toks.subList(0, first.token).joinToString(" ")
        for (re in listOf(DMY, D_MON_Y, MON_Y, M_YYYY)) namePart = re.replace(namePart, " ")
        var nameToks = tokens(namePart).toMutableList()

        // A serial number in front: "1", "2.", "01".
        if (nameToks.size > 1 && Regex("""^\d{1,3}[.)]?$""").matches(nameToks.first())) nameToks.removeAt(0)

        var batch: String? = BATCH_LABEL.find(row)?.groupValues?.get(1)
        var unit: String? = null
        val dates = datesIn(row, tz)

        nameToks = nameToks.filterNot { t ->
            when {
                looksLikeDate(t) -> true
                UNITS.containsKey(t.lowercase(Locale.ROOT).trim('.', ',')) -> {
                    unit = unit ?: UNITS[t.lowercase(Locale.ROOT).trim('.', ',')]; true
                }
                Regex("""^(batch|b\.?no\.?|exp\.?|expiry|mfg\.?)[:]?$""", RegexOption.IGNORE_CASE).matches(t) -> true
                batch != null && t.trim(',', ':') == batch -> true
                else -> false
            }
        }.toMutableList()
        // Pack sizes mended first ("1OOML" -> "100ML"), so a misread size is
        // never mistaken for a batch number below and cut from the name.
        nameToks = fixPackSize(nameToks).toMutableList()

        // A batch printed as bare digits ("20260706", "202501"), in the column
        // just before the figures. Five digits or more: a pack size or a
        // strength in a name is never that long.
        if (nameToks.size > 1 && Regex("""^\d{5,16}$""").matches(nameToks.last())) {
            val b = nameToks.removeAt(nameToks.size - 1)
            if (batch == null) batch = b
        }
        while (nameToks.size > 1 && looksLikeBatch(nameToks.last())) {
            val b = nameToks.removeAt(nameToks.size - 1).trim(',', ';', ':', '|')
            if (batch == null) batch = b
        }
        // Units written after the figures: "10 Btl 2,800 28,000".
        if (unit == null) {
            unit = toks.drop(first.token).firstNotNullOfOrNull { UNITS[it.lowercase(Locale.ROOT).trim('.', ',')] }
        }

        val name = fixPackSize(nameToks).joinToString(" ").trim(' ', '-', ':', '|', ',', '.')
        if (name.count { it.isLetter() } < 3) return null
        if (qty > 100_000 || amount.value > 100_000_000) return null

        // Expiry: the latest date on the line, if it lies after the bill date.
        // A manufacturing date alone is in the past and is not mistaken for it.
        val expiry = dates.maxOrNull()?.takeIf { it > billDay }

        return ScannedItem(name, qty, rate, amount.value, batch, expiry, unit)
    }

    // -------------------------------------------------- misread pack size

    /** Letters the reader confuses with digits on a printed bill. */
    private val LOOKS_LIKE_DIGIT = mapOf('O' to '0', 'o' to '0', 'D' to '0', 'Q' to '0',
        'B' to '8', 'I' to '1', 'l' to '1', 'S' to '5', 'Z' to '2')
    private val SIZE_UNIT = Regex("""^(ml|l|ltr|ltrs|litre|liter|gm|gms|g|kg|kgs)$""", RegexOption.IGNORE_CASE)
    private val SIZE_WITH_UNIT = Regex("""^([0-9OoDQBIlSZ]{1,5})(ml|ltr|gm|gms|kg|kgs)$""", RegexOption.IGNORE_CASE)

    /**
     * "800 ML" read as "BOO ML" (1 Oct, the owner's Leptokill line). Only a
     * pack size is mended: a short run made of nothing but digits and
     * digit-shaped letters, standing right before a unit. A real word in
     * front of "ML" ("BIO", "SOIL") has letters outside that set and is
     * left alone.
     */
    private fun fixPackSize(toks: List<String>): List<String> = toks.mapIndexed { i, t ->
        fun mend(run: String): String? {
            if (run.isEmpty() || run.length > 5) return null
            if (run.none { it in LOOKS_LIKE_DIGIT }) return null           // already digits
            if (!run.all { it.isDigit() || it in LOOKS_LIKE_DIGIT }) return null
            val fixed = run.map { LOOKS_LIKE_DIGIT[it] ?: it }.joinToString("")
            return fixed.takeIf { it.first() != '0' }                       // "OO" is not a size
        }
        val next = toks.getOrNull(i + 1)
        if (next != null && SIZE_UNIT.matches(next)) {
            mend(t)?.let { return@mapIndexed it }
        }
        SIZE_WITH_UNIT.matchEntire(t)?.let { m ->
            mend(m.groupValues[1])?.let { return@mapIndexed it + m.groupValues[2] }
        }
        t
    }

    // ------------------------------------------------------ known names

    private fun norm(s: String) = s.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    /**
     * The shop's own spelling of a product it already stocks, when the bill's
     * line is that product: "CORAGEN 20 SC 100ML" becomes "Coragen 20SC" if
     * that is the name in the book. One product must not become two because a
     * supplier prints it differently.
     */
    /**
     * A name already in the khata that the scanned heading is PROBABLY a
     * misreading of (2 Oct): "Suncrop Pesticides" read once as "Sincrop".
     * Only ever offered to the owner, never applied by itself — "Shah" and
     * "Shan" are one letter apart and can be two different men. Matched on
     * the first word (one letter off, at least 4 letters) or the whole name
     * (one letter in six). Null when nothing is that close, or when the
     * heading already matches exactly (that case needs no question).
     */
    fun similarKnown(read: String, known: List<String>): String? {
        val r = norm(read)
        if (r.length < 4) return null
        val rFirst = norm(read.trim().split(Regex("""\s+""")).first())
        return known
            .filter { k -> norm(k) != r }
            .mapNotNull { k ->
                val n = norm(k)
                if (n.length < 4) return@mapNotNull null
                val kFirst = norm(k.trim().split(Regex("""\s+""")).first())
                val firstWord = if (rFirst.length >= 4 && kFirst.length >= 4) distance(rFirst, kFirst) else Int.MAX_VALUE
                val whole = distance(r, n)
                when {
                    firstWord <= 1 -> k to firstWord
                    whole <= maxOf(1, minOf(r.length, n.length) / 6) -> k to whole
                    else -> null
                }
            }
            .minByOrNull { it.second }?.first
    }

    /** Edit distance (letters added, dropped or changed). */
    private fun distance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return prev[b.length]
    }

    fun knownName(read: String, known: List<String>): String {
        val r = norm(read)
        if (r.length < 4) return read
        return known
            .filter { k -> norm(k).length >= 4 && (r.startsWith(norm(k)) || norm(k).startsWith(r)) }
            .maxByOrNull { norm(it).length }
            ?: read
    }
}
