package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.ui.NameSearch

/** A payment that arrived, as read from a wallet's or bank's notification. Nothing here is saved as-is. */
data class IncomingPayment(
    val amount: Double,
    /** Who sent it, as the app wrote it ("Ahmed Ali"), or null when the text names nobody. */
    val sender: String? = null,
    /** The sender's number as written, masked digits kept as '*' ("0300****567"), always starting 03. */
    val phone: String? = null,
    /** The app's own id for the payment: TID, Trx ID, Transaction ID, Ref… */
    val reference: String? = null
)

/**
 * "Paisa aaya ki awaaz" (5 Nov 2026): reading a payment notification of a wallet or bank app the owner chose.
 *
 * Deliberately narrow, as [PaymentScan] is — a wrong figure said out loud is worse than silence:
 *  - anything that looks like a code (OTP, PIN, verification) is never read as a payment;
 *  - a word of money going OUT (sent to, paid, debited, bill payment, withdraw…) — or no word of money coming
 *    in — means no payment: outgoing or unclear is ignored;
 *  - the amount must be named by its currency (Rs, PKR, Rupees, روپے, before or after the figure); a balance,
 *    fee or charge is never it, and two different figures leave no amount at all.
 *
 * All the words live in [Words] below, in one place, so the real JazzCash and Easypaisa texts the owner
 * sends can be added there (and to PaymentNoticeTest) without touching the rules.
 *
 * Pure Kotlin, no Android: every rule is unit-tested.
 */
object PaymentNotice {

    /** Every pattern the reader uses. Extend here when a real notification text says it differently. */
    object Words {
        /** Money coming in. One must be present. */
        val INCOMING = Regex(
            """\b(received|recieved|receive|credited|deposit(ed)?|sent you|paid you|transferred to you(r)?|aa ?gaye|aa ?gaya|aaye|aye|aaya|wusool|wasool|mil gaye|mile)\b|وصول|موصول|جمع|آ گئے|آئے|ملے""",
            RegexOption.IGNORE_CASE
        )

        /** Money going out. Any of these means the notice is not a payment to the shop. */
        val OUTGOING = Regex(
            """\b(sent to(?! you)|you sent|you have sent|paid(?! you)|debited|transferred to(?! you)|bill payment|purchase[ds]?|withdraw(n|al)?|bheje|bheja|cash ?out)\b|ادا|بھیجے|بھیجا|نکالے|کٹوتی""",
            RegexOption.IGNORE_CASE
        )

        /** A code, never a payment: these texts are not read at all. */
        val CODE = Regex(
            """\b(otp|one[- ]?time|verification|verify|passcode|password|pin|code)\b|کوڈ|او ٹی پی|پاس ورڈ""",
            RegexOption.IGNORE_CASE
        )

        /** The currency's names around a figure. */
        private const val SIGN = """(?:rs\.?|pkr|rupees?|rupay|روپے|روپیہ|روپئے)"""

        /** "5,000", "1,25,000", "5000.50" — never part of a longer run of digits. */
        private const val FIGURE = """(\d{1,3}(?:,\d{2,3})+(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)"""

        val SIGN_THEN_FIGURE = Regex("""(?<![\p{L}])$SIGN\s*$FIGURE(?![\d,])""", RegexOption.IGNORE_CASE)
        val FIGURE_THEN_SIGN = Regex("""(?<![\d.,])$FIGURE\s*$SIGN(?![\p{L}])""", RegexOption.IGNORE_CASE)

        /** Words just before a figure that make it some other figure than the payment. */
        val NOT_THE_PAYMENT = Regex(
            """\b(balance|bal\.?|available|avl|fee|fees|charges?|tax|limit|cashback|commission)\b|بیلنس|فیس|چارجز""",
            RegexOption.IGNORE_CASE
        )

        /** "from NAME", stopping at a word that starts something else, a digit or punctuation. */
        val FROM_NAME = Regex(
            """\bfrom\s+([A-Za-z][A-Za-z' ]*?)(?=\s+(?:via|on|ref|tid|trx|txn|for|at|to|in|with|through|thru|account|a/c|acc|mobile|number|no|is|has|have|was)\b|\s*[\d.,;:!()\[\]\n\r*/|\-]|\s*$)""",
            RegexOption.IGNORE_CASE
        )

        /** Roman Urdu "Ahmed Ali se 5000…": the name before "se", after "ko"/"ne"/a colon or the start. */
        val SE_NAME_LATIN = Regex(
            """(?:^|[\n:.]\s*|\b(?:ko|ne|aap ko|apko)\s+)([A-Za-z][A-Za-z']*(?:\s+[A-Za-z][A-Za-z']*){0,3}?)\s+se\b""",
            RegexOption.IGNORE_CASE
        )

        /** Urdu "احمد علی سے": the name before "سے", after "کو"/"نے"/a colon or the start. */
        val SE_NAME_URDU = Regex(
            """(?:^|[\n:۔]\s*|(?:کو|نے)\s+)([؀-ۿ]+(?:\s+[؀-ۿ]+){0,3}?)\s+سے(?![؀-ۿ])"""
        )

        /** Words that are never a name, whatever the sentence around them. */
        val NOT_A_NAME = setOf(
            "your", "you", "account", "bank", "wallet", "jazzcash", "easypaisa", "mobile", "number", "the", "a",
            "customer", "dear", "aap", "apko", "آپ", "اکاؤنٹ", "بینک", "والٹ"
        )

        /** Words that end the part of a sentence before the sender's name ("aap ko", "آپ کو"). */
        val BEFORE_A_NAME = setOf("ko", "ne", "کو", "نے")

        /** A Pakistani mobile number, masked digits allowed: 0300 1234567, +92 300 1234567, 0300****567. */
        val PHONE = Regex("""(?<![\d*])(?:\+?92[\s-]?|0)3\d{2}[\s-]?[\d*xX•]{3,4}[\s-]?[\d*xX•]{3,4}(?![\d*])""")
    }

    /** The largest figure a shop's payment notification is believed for. */
    private const val MAX_AMOUNT = 100_000_000.0

    /**
     * The payment in a notification's [title], [text] and [bigText], or null when it is not clearly money coming
     * in. Digits in Urdu, Persian or Arabic script are read as 0-9.
     */
    fun parse(title: String?, text: String?, bigText: String? = null): IncomingPayment? {
        val body = Digits.toLatin(joined(title, text, bigText))
        if (body.isBlank()) return null
        if (Words.CODE.containsMatchIn(body)) return null
        if (Words.OUTGOING.containsMatchIn(body)) return null
        if (!Words.INCOMING.containsMatchIn(body)) return null
        val amount = amountIn(body) ?: return null
        val phone = phoneIn(body)
        return IncomingPayment(
            amount = amount,
            sender = senderIn(body),
            phone = phone,
            reference = PaymentScan.referenceIn(body.lines())
        )
    }

    /** The three parts once each: a big text usually repeats the short one, and is then the whole of it. */
    private fun joined(title: String?, text: String?, bigText: String?): String {
        val t = text?.trim().orEmpty()
        val b = bigText?.trim().orEmpty()
        val main = when {
            b.isEmpty() -> t
            t.isEmpty() || b.contains(t) -> b
            else -> "$t\n$b"
        }
        return listOfNotNull(title?.trim()?.takeIf { it.isNotEmpty() }, main.takeIf { it.isNotEmpty() }).joinToString("\n")
    }

    internal fun amountIn(body: String): Double? {
        val found = ArrayList<Double>()
        for (rx in listOf(Words.SIGN_THEN_FIGURE, Words.FIGURE_THEN_SIGN)) {
            for (m in rx.findAll(body)) {
                // The words just before the figure, on its own line: "Your balance is Rs 9,000" is not the payment.
                val lineStart = body.lastIndexOf('\n', m.range.first).let { if (it < 0) 0 else it + 1 }
                val before = body.substring(maxOf(lineStart, m.range.first - 30), m.range.first)
                val after = body.substring(m.range.last + 1, minOf(body.length, m.range.last + 12))
                if (Words.NOT_THE_PAYMENT.containsMatchIn(before) || Words.NOT_THE_PAYMENT.containsMatchIn(after.takeWhile { it != '.' && it != '\n' })) continue
                val value = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: continue
                if (value > 0.0 && value < MAX_AMOUNT) found += value
            }
        }
        return found.distinct().singleOrNull()
    }

    internal fun senderIn(body: String): String? {
        val candidates = sequenceOf(Words.FROM_NAME, Words.SE_NAME_LATIN, Words.SE_NAME_URDU)
            .mapNotNull { rx -> rx.find(body)?.groupValues?.get(1) }
        for (raw in candidates) {
            val name = cleanName(raw) ?: continue
            return name
        }
        return null
    }

    /** A name as it is said: trimmed, single-spaced, a shouted "MUHAMMAD ASLAM" as "Muhammad Aslam". */
    private fun cleanName(raw: String): String? {
        val all = raw.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
        // "Aap ko Bilal se…", "آپ کو احمد سے…": the name is what follows the last ko / ne / کو / نے.
        val cut = all.indexOfLast { it.lowercase() in Words.BEFORE_A_NAME }
        val words = all.drop(cut + 1).dropWhile { it.lowercase() in Words.NOT_A_NAME }
        if (words.isEmpty()) return null
        if (words.all { it.lowercase() in Words.NOT_A_NAME }) return null
        val name = words.joinToString(" ")
        if (name.count { it.isLetter() } < 2) return null
        val shouted = name.any { it in 'A'..'Z' } && name.none { it in 'a'..'z' }
        return if (!shouted) name else words.joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.uppercase() } }
    }

    /** The first Pakistani mobile number in [body], as 03xxxxxxxxx with masked digits as '*'; null when none. */
    internal fun phoneIn(body: String): String? {
        for (m in Words.PHONE.findAll(body)) {
            var s = m.value.filter { it.isDigit() || it in "*xX•" }.map { if (it.isDigit()) it else '*' }.joinToString("")
            if (s.startsWith("92")) s = "0" + s.substring(2)
            if (s.length == 11 && s.startsWith("03")) return s
        }
        return null
    }

    // ------------------------------------------------------------------------------------------- the customer

    /** Who in the book a payment is from: [one] when exactly one is clear, else the [candidates] to choose from. */
    data class Match<T>(val one: T?, val candidates: List<T>)

    /** Digits a number is matched on, at least: fewer known digits could be anybody's. */
    const val MIN_PHONE_DIGITS = 7

    /**
     * True when the book's [stored] number is the notice's [masked] one: both read as 03xxxxxxxxx (92 and +92
     * folded in), compared right-aligned; every digit the notice shows must agree, and at least
     * [MIN_PHONE_DIGITS] of them must have been compared ('*' compares nothing).
     */
    fun phoneMatches(masked: String?, stored: String?): Boolean {
        if (masked.isNullOrBlank() || stored.isNullOrBlank()) return false
        val a = localForm(masked.filter { it.isDigit() || it == '*' })
        val b = localForm(Digits.toLatin(stored).filter { it.isDigit() })
        if (b.length < MIN_PHONE_DIGITS) return false
        var known = 0
        var i = a.length - 1
        var j = b.length - 1
        while (i >= 0 && j >= 0) {
            val c = a[i]
            if (c != '*') {
                if (c != b[j]) return false
                known++
            }
            i--; j--
        }
        return known >= MIN_PHONE_DIGITS
    }

    private fun localForm(s: String): String = when {
        s.startsWith("0092") -> "0" + s.substring(4)
        s.startsWith("92") && s.length >= 12 -> "0" + s.substring(2)
        else -> s
    }

    /**
     * The party a payment is from. A number that matches exactly one party decides; else the sender's name
     * through the app's one rule for names said aloud ([NameSearch.confidentMatch]: a word recognised, the
     * winner clearly ahead). Several numbers matching, or a name not clear, give candidates and no [Match.one]:
     * the owner chooses. Nothing is ever chosen on a guess.
     */
    fun <T> match(
        parties: List<T>,
        payment: IncomingPayment,
        nameOf: (T) -> String,
        phoneOf: (T) -> String?
    ): Match<T> {
        val byPhone = if (payment.phone == null) emptyList() else parties.filter { phoneMatches(payment.phone, phoneOf(it)) }
        if (byPhone.size == 1) return Match(byPhone[0], byPhone)
        if (byPhone.size > 1) {
            // Two parties on one number: the name may still tell them apart, but only among those two.
            val among = payment.sender?.let { s -> NameSearch.confidentMatch(byPhone, nameWords(s), nameOf) }
            return Match(among, byPhone)
        }
        val sender = payment.sender ?: return Match(null, emptyList())
        val words = nameWords(sender)
        if (words.isEmpty()) return Match(null, emptyList())
        val one = NameSearch.confidentMatch(parties, words, nameOf)
        if (one != null) return Match(one, listOf(one))
        val near = NameSearch.scoreSpoken(parties, words, nameOf).filter { it.strongHits > 0 }.map { it.item }
        return Match(null, near)
    }

    private fun nameWords(name: String): List<String> =
        name.split(Regex("""[\s.,'-]+""")).map { it.trim() }.filter { it.length >= 2 }

    // ------------------------------------------------------------------------------------------- repeats

    /**
     * The same payment is often posted twice, or updated in place: the second is not said again. A repeat is the
     * same app, the same amount and the same reference (or, with none, the same text) within [windowMs].
     * Kept in memory only; nothing of it is written anywhere.
     */
    class Repeats(private val windowMs: Long = 10 * 60_000L) {
        private val seen = ArrayList<Pair<String, Long>>()

        /** True when this payment was already taken within the window; otherwise remembers it and says false. */
        @Synchronized
        fun isRepeat(pkg: String, amount: Double, reference: String?, text: String, now: Long): Boolean {
            seen.removeAll { now - it.second > windowMs || it.second > now }
            val key = pkg + "|" + Math.round(amount * 100) + "|" + (reference?.trim()?.lowercase() ?: ("#" + text.trim().hashCode()))
            if (seen.any { it.first == key }) return true
            seen += key to now
            if (seen.size > 200) seen.removeAt(0)
            return false
        }
    }
}
