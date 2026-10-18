package com.innovation313.roshankhata.data

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** What was read off a payment slip or a payment app's screenshot. Nothing here is saved as-is. */
data class ScannedPayment(
    val amount: Double? = null,
    /** The app's or bank's own reference: TID, Transaction ID, RRN… */
    val reference: String? = null,
    val date: Long? = null
) {
    val isEmpty: Boolean get() = amount == null && reference == null && date == null
}

/**
 * Reading money received (1 Oct): a JazzCash or Easypaisa screenshot, a
 * bank's transfer receipt, a deposit slip — into an "I got" entry.
 *
 * Deliberately narrow, because a wrong amount in a ledger is worse than an
 * empty box:
 *
 *  - The amount is taken ONLY from a row that names it ("Amount", "Rs",
 *    "PKR", "Transferred", "Received", "Paid"), never from a row about a
 *    fee, a charge, a tax or anyone's balance. If two such rows disagree the
 *    amount is left empty for the owner to type.
 *  - The reference is taken only after a label that names it (TID,
 *    Transaction ID, Trx ID, Ref, RRN, STAN) and must have at least six
 *    letters or digits — phone numbers and CNICs are never references.
 *  - Printed or on-screen Latin text only: the phone's reader has no Urdu.
 *
 * Built from the common shapes of these receipts, not from a set of the
 * owner's own screenshots yet: every result is checked by the owner before
 * it reaches the form.
 *
 * Pure Kotlin, no Android, so every rule here is unit-tested.
 */
object PaymentScan {

    // "Rs" and "PKR" may run straight into the figure ("Rs5,000").
    private val MONEY_WORD = Regex(
        """\b(amount|amt|transferred|received|paid|sent|deposit(ed)?|credited)\b|\b(rs|pkr)\.?(?=\s|\d|$)""",
        RegexOption.IGNORE_CASE
    )

    /** Rows about some other figure than the payment itself. */
    private val NOT_THE_PAYMENT = Regex(
        """\b(fee|fees|charges?|tax|fed|wht|balance|bal\.?|available|limit|commission|cashback|discount|points)\b""",
        RegexOption.IGNORE_CASE
    )

    // A label, then optionally its kind ("ID", "Reference") and "No."/"#":
    // "TID:", "Transaction ID", "Transaction Reference No.", "Ref #", "RRN".
    private val REF_LABEL = Regex(
        """\b(tid|trx|txn|transaction|ref|reference|rrn|stan)\b\.?(\s*(id|ref|reference)\b)?(\s*(no\b\.?|number\b|#))?\s*[:#.\-]?\s*""",
        RegexOption.IGNORE_CASE
    )

    /** "5,000", "5000.00", "1,25,000" — a figure, not a date or a phone number. */
    private val FIGURE = Regex("""(?<![\d/:.\-])\d{1,3}(?:,\d{2,3})+(?:\.\d{1,2})?(?![\d/:])|(?<![\d/:.\-])\d{1,7}(?:\.\d{1,2})?(?![\d/:\-])""")

    private val MONTHS = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
    )
    private val DMY = Regex("""\b(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{2}|\d{4})\b""")
    private val D_MON_Y = Regex("""\b(\d{1,2})[\s\-]*([A-Za-z]{3})[a-z]*[\s\-,]*(\d{4})\b""")
    private val MON_D_Y = Regex("""\b([A-Za-z]{3})[a-z]*\s+(\d{1,2}),?\s+(\d{4})\b""")

    fun parse(rows: List<String>, tz: TimeZone = TimeZone.getDefault()): ScannedPayment =
        ScannedPayment(amountIn(rows), referenceIn(rows), dateIn(rows, tz))

    // ---------------------------------------------------------------- amount

    private fun figuresIn(text: String): List<Double> =
        FIGURE.findAll(text).mapNotNull { it.value.replace(",", "").toDoubleOrNull() }
            .filter { it > 0.0 }.toList()

    internal fun amountIn(rows: List<String>): Double? {
        val found = mutableListOf<Double>()
        rows.forEachIndexed { i, row ->
            if (NOT_THE_PAYMENT.containsMatchIn(row)) return@forEachIndexed
            // A row carrying the reference is about the reference, not money
            // ("Transaction amount" still is money: no reference follows it).
            if (referenceIn(listOf(row)) != null) return@forEachIndexed
            if (!MONEY_WORD.containsMatchIn(row)) return@forEachIndexed
            val here = figuresIn(row.replace(MONEY_WORD, " "))
            when {
                here.size == 1 -> found += here[0]
                // "Amount" alone on its row, the figure on the next one —
                // the usual layout of a payment app's receipt.
                here.isEmpty() && i + 1 < rows.size -> {
                    val next = rows[i + 1]
                    if (!NOT_THE_PAYMENT.containsMatchIn(next) && referenceIn(listOf(next)) == null && !looksLikeDateOrTime(next)) {
                        figuresIn(next).singleOrNull()?.let { found += it }
                    }
                }
            }
        }
        // The same figure may be printed twice (headline and detail); two
        // DIFFERENT figures mean the reading cannot tell which was paid.
        val distinct = found.distinct()
        return distinct.singleOrNull()
    }

    private fun looksLikeDateOrTime(s: String) =
        DMY.containsMatchIn(s) || D_MON_Y.containsMatchIn(s) || MON_D_Y.containsMatchIn(s) ||
            Regex("""\b\d{1,2}:\d{2}\b""").containsMatchIn(s)

    // ------------------------------------------------------------- reference

    internal fun referenceIn(rows: List<String>): String? {
        rows.forEachIndexed { i, row ->
            for (label in REF_LABEL.findAll(row)) {
                val after = row.substring(label.range.last + 1).trim()
                val candidate = tokenAsReference(after)
                    ?: if (after.isEmpty() && i + 1 < rows.size) tokenAsReference(rows[i + 1].trim()) else null
                if (candidate != null) return candidate
            }
        }
        return null
    }

    private fun tokenAsReference(text: String): String? {
        val token = text.split(Regex("""\s+""")).firstOrNull()?.trim(':', '#', '.', ',', '-') ?: return null
        if (token.count { it.isLetterOrDigit() } < 6) return null
        if (!token.all { it.isLetterOrDigit() || it == '-' }) return null
        if (!token.any { it.isDigit() }) return null
        // A mobile number (03xx…) or a CNIC is somebody's identity, not a reference.
        val digits = token.filter { it.isDigit() }
        if (token.all { it.isDigit() } && digits.length == 11 && digits.startsWith("03")) return null
        if (digits.length == 13 && token.count { it == '-' } == 2) return null
        return token
    }

    // ------------------------------------------------------------------ date

    internal fun dateIn(rows: List<String>, tz: TimeZone): Long? {
        for (row in rows) {
            DMY.find(row)?.let { m ->
                val (d, mo, y) = m.destructured
                dayMillis(d.toInt(), mo.toInt(), year(y), tz)?.let { return it }
            }
            D_MON_Y.find(row)?.let { m ->
                val (d, mon, y) = m.destructured
                MONTHS[mon.lowercase(Locale.ROOT)]?.let { mo -> dayMillis(d.toInt(), mo, y.toInt(), tz)?.let { return it } }
            }
            MON_D_Y.find(row)?.let { m ->
                val (mon, d, y) = m.destructured
                MONTHS[mon.lowercase(Locale.ROOT)]?.let { mo -> dayMillis(d.toInt(), mo, y.toInt(), tz)?.let { return it } }
            }
        }
        return null
    }

    private fun year(y: String): Int = y.toInt().let { if (y.length == 2) 2000 + it else it }

    /** Noon of that day, so a time-zone shift never moves it to another date. */
    private fun dayMillis(d: Int, m: Int, y: Int, tz: TimeZone): Long? {
        if (m !in 1..12 || d !in 1..31 || y !in 2000..2099) return null
        val c = Calendar.getInstance(tz).apply { clear(); isLenient = false; set(y, m - 1, d, 12, 0, 0) }
        return runCatching { c.timeInMillis }.getOrNull()
    }
}
