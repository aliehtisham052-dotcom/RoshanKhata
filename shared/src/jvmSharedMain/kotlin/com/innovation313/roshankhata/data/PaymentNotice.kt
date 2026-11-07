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
 *  - money that has not come in — a request to pay, a failed, pending, reversed or refunded transfer, a loan, an
 *    offer, cashback, a bill, a mobile load, an order ([Words.NOT_RECEIVED], 7 Nov 2026) — is never a payment;
 *  - a word of money going OUT (sent to, paid, debited, bill payment, withdraw…) — or no word of money coming
 *    in — means no payment: outgoing or unclear is ignored;
 *  - the amount must be named by its currency (Rs, PKR, Rupees, روپے, before or after the figure); a balance,
 *    fee or charge is never it, a figure with a minus or running on into more digits is not one, and two
 *    different figures leave no amount at all;
 *  - the sender's number is read only where the sender is named ("from …", "… se", "… سے"): a number elsewhere
 *    is the one the money went to, or a phone that was topped up.
 *
 * Only the first [MAX_TEXT] characters are read, with every run of spaces made one ([tidy]), so a long or
 * hostile text costs no more than a short one.
 *
 * All the words live in [Words] below, in one place, so the real JazzCash and Easypaisa texts the owner
 * sends can be added there (and to PaymentNoticeTest) without touching the rules.
 *
 * Pure Kotlin, no Android: every rule is unit-tested.
 */
object PaymentNotice {

    /** Every pattern the reader uses. Extend here when a real notification text says it differently. */
    object Words {
        /** Urdu words stand alone: "جمعہ" (Friday) is not "جمع", and "ملے گا" (will get) is not "ملے". */
        private const val UR_START = """(?<![\p{L}\p{M}])"""
        private const val UR_END = """(?![\p{L}\p{M}])"""

        /** Money coming in. One must be present. */
        val INCOMING = Regex(
            """\b(received|recieved|receive|credited|deposit(ed)?|sent (to )?you(r)?|paid you|transferred to you(r)?|aa ?gaye|aa ?gaya|aaye|aye|aaya|wusool|wasool|mil gaye|mile)\b""" +
                """|$UR_START(?:وصولی|وصول|موصول|جمع|آ گئے|آئے|ملے)$UR_END""",
            RegexOption.IGNORE_CASE
        )

        /**
         * Money that has NOT come in, whatever else the text says (7 Nov 2026, the review's P1): "You have received a
         * payment request of Rs 3,000" is a request, "Receive up to Rs 5,000 … apply for a loan" an advertisement,
         * "Rs 5,000 could not be credited" a failure. Checked before the words of money coming in. English, Roman
         * Urdu and Urdu; a future ("will be", "ملے گا", "گا/گی/گے") is not money that came.
         */
        val NOT_RECEIVED = Regex(
            """\b(request(s|ed|ing)?|(tap|click)( here)? to pay|pay now|fail(s|ed|ure)?|unsuccessful|could ?not|couldn['’]?t|can ?not|can['’]?t|unable|declined|revers(ed|al)|refund(s|ed)?|pending|will be|not (been )?(received|credited|deposited|completed|successful)""" +
                """|loans?|offers?|win(s|ner|ners|ning)?|up ?to|enjoy|cash ?back|bonus(es)?|rewards?|prizes?|bills?|easy ?load|load(ed)?|top[- ]?up|recharge(d)?|orders?""" +
                """|darkh(w)?a+st|na ?ka+m(i|yab)?|ina+m(at)?|karwa(y|e|i)+n|mil(e|ay|ain|ein|en)? ?(ga|gi|ge)|ja(y|e)+(n|in|ein)? ?(ga|gi|ge))\b""" +
                """|$UR_START(?:درخواست|ناکام|کیش ?بیک|انعام|کروائیں|کروایں|جیت|آفر|پیش ?کش|ریفنڈ|زیر التوا|پینڈنگ|بونس|آرڈر|ایزی ?لوڈ""" +
                """|(?:لوڈ|بل|لون|فیل|گا|گی|گے)$UR_END)""",
            RegexOption.IGNORE_CASE
        )

        /** Money going out. Any of these means the notice is not a payment to the shop. */
        val OUTGOING = Regex(
            """\b(sent to(?! you)|you sent|you have sent|(money|payment|amount|funds) sent(?! to you)|sent successfully(?! to you)|successfully sent(?! to you)|paid(?! you)|debit(ed)?|transferred to(?! you)|received by(?! you)|beneficiary|bill payment|purchase[ds]?|withdraw(n|al)?|bheje|bheja|cash ?out)\b|ادا|بھیجے|بھیجا|نکالے|کٹوتی""",
            RegexOption.IGNORE_CASE
        )

        /** A code, never a payment: these texts are not read at all. */
        val CODE = Regex(
            """\b(otp|one[- ]?time|verification|verify|passcode|password|pin|code)\b|کوڈ|او ٹی پی|پاس ورڈ""",
            RegexOption.IGNORE_CASE
        )

        /** The currency's names around a figure. */
        private const val SIGN = """(?:rs\.?|pkr|rupees?|rupay|روپے|روپیہ|روپئے)"""

        /** "5,000", "1,25,000", "5000.50" — bounded, so no run of digits or groups is ever walked far. */
        private const val FIGURE = """(\d{1,3}(?:,\d{2,3}){1,6}(?:\.\d{1,2})?|\d{1,12}(?:\.\d{1,2})?)"""

        /**
         * Where a figure must end (7 Nov 2026, the review's P5): not before another digit, a Latin letter ("Rs 1e12"
         * is not 1) or a point or comma that a digit follows ("Rs 5.000" is not 5). A full stop that ends the
         * sentence is fine.
         */
        private const val FIGURE_END = """(?![\dA-Za-z]|[.,]\d)"""

        /** A minus or a dash stuck to the front ("-Rs 500", "-500 PKR") is money going out, never a payment. */
        private const val NO_MINUS = """\-\u2212\u2013"""

        val SIGN_THEN_FIGURE = Regex("""(?<![\p{L}$NO_MINUS])$SIGN\s*$FIGURE$FIGURE_END""", RegexOption.IGNORE_CASE)
        val FIGURE_THEN_SIGN = Regex("""(?<![\d.,A-Za-z$NO_MINUS])$FIGURE\s*$SIGN(?![\p{L}])""", RegexOption.IGNORE_CASE)

        /** Words just before a figure that make it some other figure than the payment. */
        val NOT_THE_PAYMENT = Regex(
            """\b(balance|bal\.?|available|avl|fee|fees|charges?|tax|limit|cashback|commission)\b|بیلنس|فیس|چارجز""",
            RegexOption.IGNORE_CASE
        )

        /** "from NAME", stopping at a word that starts something else, a digit or punctuation. At most 60 letters. */
        val FROM_NAME = Regex(
            """\bfrom\s+([A-Za-z][A-Za-z' ]{0,60}?)(?=\s+(?:via|on|ref|tid|trx|txn|for|at|to|in|with|through|thru|account|a/c|acc|mobile|number|no|is|has|have|was)\b|\s*[\d.,;:!()\[\]\n\r*/|\-]|\s*$)""",
            RegexOption.IGNORE_CASE
        )

        /** Roman Urdu "Ahmed Ali se 5000…": the name before "se", after "ko"/"ne"/a colon or the start. */
        val SE_NAME_LATIN = Regex(
            """(?:^|[\n:.]\s*|\b(?:ko|ne|aap ko|apko)\s+)([A-Za-z][A-Za-z']{0,24}(?:\s+[A-Za-z][A-Za-z']{0,24}){0,3}?)\s+se\b""",
            RegexOption.IGNORE_CASE
        )

        /** Urdu "احمد علی سے": the name before "سے", after "کو"/"نے"/a colon or the start. */
        val SE_NAME_URDU = Regex(
            """(?:^|[\n:۔]\s*|(?:کو|نے)\s+)([؀-ۿ]{1,25}(?:\s+[؀-ۿ]{1,25}){0,3}?)\s+سے(?![؀-ۿ])"""
        )

        /** Where a sender is named in English: what follows "from", "sender" or "payer". */
        val SENDER_AFTER = Regex("""\b(?:from|sender|payer)\b""", RegexOption.IGNORE_CASE)

        /**
         * Where that part ends: a line break, a sentence's end (not an initial's point, "M. Aslam"), or "to"/"for",
         * which name someone else ("from your account to 0300…").
         */
        val SENDER_AFTER_END = Regex("""\n|[!?;]|(?<![\s.][A-Za-z])\.(?=\s|$)|\b(?:to|for)\b""", RegexOption.IGNORE_CASE)

        /** Where a sender is named in Roman Urdu and Urdu: what comes before "se" / "سے". */
        val SENDER_BEFORE = Regex("""\bse\b|${UR_START}سے$UR_END""", RegexOption.IGNORE_CASE)

        /** Where that part starts: its line, its sentence, or the "ko"/"ne"/"کو"/"نے" before the sender. */
        val SENDER_BEFORE_START = Regex("""\n|[.!?;:۔،](?=\s)|\b(?:ko|ne)\b|$UR_START(?:کو|نے)$UR_END""", RegexOption.IGNORE_CASE)

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
     * The most of a notification that is read (7 Nov 2026, the review's P5). A payment notice is a sentence or two;
     * a text of thousands of characters is not one, and every pattern below is walked over this much at most.
     */
    const val MAX_TEXT = 1_000

    /** How far either side of "from" or "se" a sender's number is looked for. */
    private const val SENDER_PART = 80

    /**
     * The payment in a notification's [title], [text] and [bigText], or null when it is not clearly money coming
     * in. Digits in Urdu, Persian or Arabic script are read as 0-9.
     */
    fun parse(title: String?, text: String?, bigText: String? = null): IncomingPayment? {
        val body = tidy(Digits.toLatin(joined(title?.take(MAX_TEXT), text?.take(MAX_TEXT), bigText?.take(MAX_TEXT))))
        if (body.isBlank()) return null
        if (Words.CODE.containsMatchIn(body)) return null
        if (Words.NOT_RECEIVED.containsMatchIn(body)) return null
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

    /**
     * [text] made safe to read (7 Nov 2026, the review's P5): at most [MAX_TEXT] characters, every run of spaces one
     * space and every run of line breaks one break, the invisible marks that can split a word ("re\u200Bquest")
     * taken out. A pattern then never meets a long run of anything: a run of spaces after "from" took twelve
     * seconds at 15,000 characters, and a chain of ",000" overflowed the stack.
     */
    internal fun tidy(text: String): String {
        val out = StringBuilder(minOf(text.length, MAX_TEXT))
        var space = false
        var newline = false
        for (c in text) {
            when {
                c in INVISIBLE -> Unit
                c == '\n' || c == '\r' || c == '\u2028' || c == '\u2029' || c == '\u0085' -> newline = true
                c.isWhitespace() -> space = true
                else -> {
                    val gap = out.isNotEmpty() && (space || newline)
                    if (out.length + (if (gap) 2 else 1) > MAX_TEXT) break
                    if (gap) out.append(if (newline) '\n' else ' ')
                    space = false
                    newline = false
                    out.append(c)
                }
            }
        }
        return out.toString()
    }

    /** Marks that show nothing: soft hyphen, zero-width space, word joiner, the direction marks and isolates, BOM. */
    private val INVISIBLE: Set<Char> = buildSet {
        add('\u00AD'); add('\u200B'); add('\u200E'); add('\u200F'); add('\uFEFF')
        for (c in '\u202A'..'\u202E') add(c)
        for (c in '\u2060'..'\u2064') add(c)
        for (c in '\u2066'..'\u2069') add(c)
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

    /**
     * The sender's Pakistani mobile number, as 03xxxxxxxxx with masked digits as '*'; null when none. Looked for only
     * where the sender is named ([senderParts], 7 Nov 2026, the review's P1): "Rs 1,000 to Ali Raza 0300****567" and
     * "Easyload … for 03001234567" name the number the money went to, and that one was taken as the payer's.
     */
    internal fun phoneIn(body: String): String? {
        for (part in senderParts(body)) {
            for (m in Words.PHONE.findAll(part)) {
                var s = m.value.filter { it.isDigit() || it in "*xX•" }.map { if (it.isDigit()) it else '*' }.joinToString("")
                if (s.startsWith("92")) s = "0" + s.substring(2)
                if (s.length == 11 && s.startsWith("03")) return s
            }
        }
        return null
    }

    /**
     * The stretches of [body] that name who paid: what follows "from", "sender" or "payer", to the end of its clause
     * and never past "to" or "for"; and what comes before "se" or "سے", from the start of its clause or the
     * "ko"/"ne"/"کو"/"نے" before it. Each at most [SENDER_PART] characters.
     */
    internal fun senderParts(body: String): List<String> {
        val parts = ArrayList<String>()
        for (m in Words.SENDER_AFTER.findAll(body)) {
            val from = m.range.last + 1
            val window = body.substring(from, minOf(body.length, from + SENDER_PART))
            parts += window.substring(0, Words.SENDER_AFTER_END.find(window)?.range?.first ?: window.length)
        }
        for (m in Words.SENDER_BEFORE.findAll(body)) {
            val to = m.range.first
            val window = body.substring(maxOf(0, to - SENDER_PART), to)
            val start = Words.SENDER_BEFORE_START.findAll(window).lastOrNull()?.let { it.range.last + 1 } ?: 0
            parts += window.substring(start)
        }
        return parts
    }

    // ------------------------------------------------------------------------------------------- the customer

    /** Who in the book a payment is from: [one] when exactly one is clear, else the [candidates] to choose from. */
    data class Match<T>(val one: T?, val candidates: List<T>)

    /** A Pakistani mobile number's own digits, after its 03xx network code: what tells one subscriber from another. */
    const val SUBSCRIBER_DIGITS = 7

    /**
     * Of those, how many the notice must show (7 Nov 2026, the review's P4): "0300****567" shows three, and the four
     * of the network code are shared by millions — seven "known" digits that way matched anybody's 0300…567.
     */
    const val MIN_SUBSCRIBER_DIGITS = 6

    /**
     * True when the book's [stored] number is the notice's [masked] one: both read as 03xxxxxxxxx (92 and +92
     * folded in), compared right-aligned; every digit the notice shows must agree, and at least
     * [MIN_SUBSCRIBER_DIGITS] of the last [SUBSCRIBER_DIGITS] must have been shown ('*' compares nothing).
     */
    fun phoneMatches(masked: String?, stored: String?): Boolean {
        if (masked.isNullOrBlank() || stored.isNullOrBlank()) return false
        val a = localForm(Digits.toLatin(masked).filter { it.isDigit() || it == '*' })
        val b = localForm(Digits.toLatin(stored).filter { it.isDigit() })
        if (b.length < SUBSCRIBER_DIGITS) return false
        var known = 0
        var i = a.length - 1
        var j = b.length - 1
        while (i >= 0 && j >= 0) {
            val c = a[i]
            if (c != '*') {
                if (c != b[j]) return false
                // Only the subscriber's own digits count: the network code says which company, not who.
                if (a.length - 1 - i < SUBSCRIBER_DIGITS) known++
            }
            i--; j--
        }
        return known >= MIN_SUBSCRIBER_DIGITS
    }

    private fun localForm(s: String): String = when {
        s.startsWith("0092") -> "0" + s.substring(4)
        s.startsWith("92") && s.length >= 12 -> "0" + s.substring(2)
        else -> s
    }

    /**
     * The party a payment is from. A number that matches exactly one party decides, unless the notice also names
     * the sender and that name is not his (7 Nov 2026, the review's P4): then the owner chooses between him and the
     * names near the sender's. Else the sender's name through the app's one rule for names said aloud
     * ([NameSearch.confidentMatch]: a word recognised, the winner clearly ahead). Several numbers matching, or a
     * name not clear, give candidates and no [Match.one]: the owner chooses. Nothing is ever chosen on a guess.
     */
    fun <T> match(
        parties: List<T>,
        payment: IncomingPayment,
        nameOf: (T) -> String,
        phoneOf: (T) -> String?
    ): Match<T> {
        val byPhone = if (payment.phone == null) emptyList() else parties.filter { phoneMatches(payment.phone, phoneOf(it)) }
        val words = payment.sender?.let { nameWords(it) }.orEmpty()
        if (byPhone.size == 1) {
            val one = byPhone[0]
            if (words.isEmpty() || nameAgrees(one, words, nameOf)) return Match(one, byPhone)
            val near = NameSearch.scoreSpoken(parties, words, nameOf).filter { it.strongHits > 0 }.map { it.item }
            return Match(null, (byPhone + near).distinct())
        }
        if (byPhone.size > 1) {
            // Two parties on one number: the name may still tell them apart, but only among those two.
            val among = if (words.isEmpty()) null else NameSearch.confidentMatch(byPhone, words, nameOf)
            return Match(among, byPhone)
        }
        if (words.isEmpty()) return Match(null, emptyList())
        val one = NameSearch.confidentMatch(parties, words, nameOf)
        if (one != null) return Match(one, listOf(one))
        val near = NameSearch.scoreSpoken(parties, words, nameOf).filter { it.strongHits > 0 }.map { it.item }
        return Match(null, near)
    }

    /** A word of the sender's name recognised in [party]'s name — matched outright or begun with, not merely near. */
    private fun <T> nameAgrees(party: T, words: List<String>, nameOf: (T) -> String): Boolean =
        NameSearch.scoreSpoken(listOf(party), words, nameOf).firstOrNull()?.let { it.strongHits > 0 } ?: false

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

    // ------------------------------------------------------------------------------------------- whose notice

    /** Notification categories of a person's words, never a wallet's: a chat, an email, a social post, a call. */
    val PERSONAL_CATEGORIES = setOf("msg", "email", "social", "call", "missed_call")

    /** Extras only a conversation's notice carries (Android's MessagingStyle, the platform's and AndroidX's). */
    val PERSONAL_EXTRAS = setOf(
        "android.messages", "android.messagingUser", "android.messagingStyleUser", "android.selfDisplayName",
        "android.conversationTitle", "android.isGroupConversation"
    )

    /**
     * True for a notice that carries a person's words (7 Nov 2026, the review's P2): a chat, an email, a social post
     * or a call, by its [category], its style's [template] or its extras' [keys]. Never read as a payment, and an app
     * seen only with such notices is never offered to tick: a customer could type "JazzCash: You have received
     * Rs 50,000" into a chat.
     */
    fun isPersonal(category: String?, template: String?, keys: Set<String>): Boolean =
        category in PERSONAL_CATEGORIES || template?.contains("MessagingStyle") == true || keys.any { it in PERSONAL_EXTRAS }

    /**
     * True for a notice that is never read, nor its app noted (7 Nov 2026, the review's P2 and P8): one still going on
     * ([ongoing]: music, a download, a call in progress — Android 12+ keeps them away by the manifest's filter, 7 to
     * 11 do not), or a person's words ([isPersonal]).
     */
    fun passedOver(ongoing: Boolean, category: String?, template: String?, keys: Set<String>): Boolean =
        ongoing || isPersonal(category, template, keys)

    // ------------------------------------------------------------------------------------------- how it is said

    /**
     * How often payments are said aloud (7 Nov 2026, the review's P5): at most [most] in any [windowMs]; the next one
     * in that stretch gets a single chime; after that nothing is heard until the stretch has room again. The notices
     * themselves still come — only the sound waits, so a flood of notices cannot keep the phone talking.
     */
    class Pace(private val most: Int = 3, private val windowMs: Long = 60_000L) {
        enum class Say { VOICE, CHIME, QUIET }

        private val said = ArrayDeque<Long>()
        private var chimedAt: Long? = null

        /**
         * What the next payment gets at [now]. With [aloud] false — the phone locked, or App Lock on (the review's
         * P6) — a payment that could have been said gets the chime instead: a name and an amount are not spoken
         * where they are not shown.
         */
        @Synchronized
        fun next(now: Long, aloud: Boolean = true): Say {
            while (said.isNotEmpty() && (now - said.first() >= windowMs || said.first() > now)) said.removeFirst()
            if (said.size < most) {
                said.addLast(now)
                return if (aloud) Say.VOICE else Say.CHIME
            }
            val chimed = chimedAt
            if (chimed == null || now - chimed >= windowMs || chimed > now) {
                chimedAt = now
                return Say.CHIME
            }
            return Say.QUIET
        }
    }

    /** What the phone's speech engine says of one of its voices. */
    data class VoiceFacts(
        val language: String,
        val country: String,
        /** Synthesised on a server: the words would leave the phone. */
        val needsNetwork: Boolean,
        /** Listed, but its data is not on the phone yet. */
        val notInstalled: Boolean,
        /** The engine's own grade, higher is better. */
        val quality: Int
    )

    /**
     * The voice to speak with (7 Nov 2026, the review's P6): one of [language] that is on the phone — never one that
     * needs the network, which would send a customer's name and an amount away, nor one still to be downloaded —
     * [country]'s first, then the best sounding. Null when there is none, and the chime plays instead.
     */
    fun <V> offlineVoice(voices: Collection<V>, language: String, country: String, facts: (V) -> VoiceFacts): V? =
        voices.map { it to facts(it) }
            .filter { (_, f) -> !f.needsNetwork && !f.notInstalled && f.language.equals(language, ignoreCase = true) }
            .sortedWith(
                compareByDescending<Pair<V, VoiceFacts>> { (_, f) -> country.isNotEmpty() && f.country.equals(country, ignoreCase = true) }
                    .thenByDescending { (_, f) -> f.quality }
            )
            .firstOrNull()?.first

    // ------------------------------------------------------------------------------------------- the door

    /**
     * What a payment notice's tap opens, decided when it is opened (7 Nov 2026, the review's P3 and P7) from its
     * [ticket], the matched customer's row as the book has it now ([party], null when there was none or it is
     * gone), and the newest entry or cashbook row already carrying the payment's reference ([earlier], null when
     * none):
     *  - a reference already in the book is said, and no second form is opened;
     *  - the customer the notice matched opens only while he is still in the book as he was — not binned, the same
     *    uid (or, for a row without one, the same name) — else the list to choose from, narrowed to the sender.
     */
    fun opening(ticket: PaymentTicket, party: PartyNow?, earlier: PaymentRefUse?): Opening {
        if (earlier != null) return Opening.Already(earlier.timestamp, earlier.partyName)
        val id = ticket.partyId
        if (id != null && party != null && !party.deleted && sameParty(ticket, party)) return Opening.Customer(id)
        return Opening.Choose(if (ticket.narrow || id != null) ticket.sender else null)
    }

    private fun sameParty(ticket: PaymentTicket, party: PartyNow): Boolean {
        val uid = ticket.partyUid
        if (uid != null) return uid == party.uid
        val name = ticket.partyName ?: return false
        return name.trim().equals(party.name.trim(), ignoreCase = true)
    }

    /** See [opening]. */
    sealed class Opening {
        /** The payment is already in the book: when it was written, and whose it is (null: the cashbook). */
        data class Already(val at: Long, val partyName: String?) : Opening()

        /** The customer the notice matched: his "I got" form, filled in. */
        data class Customer(val partyId: Long) : Opening()

        /** The Khata list to choose the customer from, its search set to [search] when there is one. */
        data class Choose(val search: String?) : Opening()
    }

    /** A ticket's customer as the book has him now: his uid, his name, and whether he is in the bin. */
    data class PartyNow(val uid: String?, val name: String, val deleted: Boolean)
}

/**
 * What a payment notice opens, kept inside the app with its one-time key (7 Nov 2026, the review's P3): the notice
 * itself carries only the key ([PaymentTickets]), so its payment can be given out once, within a day, and no other
 * app can fill a form through the door. The customer it matched is named three ways (row id, [partyUid] and
 * [partyName]) so a row binned or replaced since is not opened by its id alone (P7).
 */
data class PaymentTicket(
    /** The notice's own id, so opening the form can take the notice away. */
    val noticeId: Int,
    val issuedAt: Long,
    /** The shop whose book the notice was matched in. */
    val business: Long,
    val amount: Double,
    val note: String,
    /** A [PaymentMethod] key. */
    val method: String,
    val reference: String? = null,
    /** Who the wallet said sent it. */
    val sender: String? = null,
    /** Several customers came near the sender's name: the list opens narrowed to it. */
    val narrow: Boolean = false,
    val partyId: Long? = null,
    val partyUid: String? = null,
    val partyName: String? = null
) {
    /** One line: the fields in order, apart by U+001F, which free text is cleaned of first. */
    fun line(): String = listOf(
        VERSION, noticeId.toString(), issuedAt.toString(), business.toString(), amount.toString(),
        clean(note), clean(method), clean(reference), clean(sender), if (narrow) "1" else "0",
        partyId?.toString().orEmpty(), clean(partyUid), clean(partyName)
    ).joinToString(SEP.toString())

    companion object {
        private const val VERSION = "t1"
        private const val SEP = '\u001F'
        private const val FIELDS = 13

        private fun clean(s: String?): String = s.orEmpty().filterNot { it == SEP || it == '\u001E' || it == '\n' || it == '\r' }

        /** The ticket a [line] wrote, or null for anything else. */
        fun of(line: String): PaymentTicket? {
            val f = line.split(SEP)
            if (f.size != FIELDS || f[0] != VERSION) return null
            return PaymentTicket(
                noticeId = f[1].toIntOrNull() ?: return null,
                issuedAt = f[2].toLongOrNull() ?: return null,
                business = f[3].toLongOrNull() ?: return null,
                amount = f[4].toDoubleOrNull()?.takeIf { it > 0.0 && it.isFinite() } ?: return null,
                note = f[5],
                method = f[6],
                reference = f[7].ifEmpty { null },
                sender = f[8].ifEmpty { null },
                narrow = f[9] == "1",
                partyId = if (f[10].isEmpty()) null else (f[10].toLongOrNull() ?: return null),
                partyUid = f[11].ifEmpty { null },
                partyName = f[12].ifEmpty { null }
            )
        }
    }
}

/**
 * The payment notices' one-time keys (7 Nov 2026, the review's P3), over a [Store] the app keeps privately. A key
 * opens its ticket once ([redeem] takes it away whatever it answers), only within [TTL_MS] of the notice, and at
 * most [MAX] are kept. The key it gives out was made by the caller, from a secure random source.
 */
class PaymentTickets(private val store: Store) {

    /** Where the tickets live: key to [PaymentTicket.line]. Each [save] is written before it returns. */
    interface Store {
        fun all(): Map<String, String>
        fun save(put: Map<String, String>, drop: Collection<String>)
    }

    /** Keeps [ticket] under [key], letting go of tickets past their day and the oldest beyond [MAX]. */
    @Synchronized
    fun issue(key: String, ticket: PaymentTicket) {
        val now = ticket.issuedAt
        val all = store.all()
        val kept = all.mapNotNull { (k, v) -> PaymentTicket.of(v)?.takeIf { fresh(it, now) }?.let { k to it } }
            .filter { it.first != key }.sortedByDescending { it.second.issuedAt }.take(MAX - 1).map { it.first }.toSet()
        store.save(mapOf(key to ticket.line()), all.keys - kept - key)
    }

    /** The ticket [key] names, once: gone after this whatever the answer. Null when unknown, used, or past its day. */
    @Synchronized
    fun redeem(key: String?, now: Long): PaymentTicket? {
        if (key.isNullOrBlank()) return null
        val line = store.all()[key] ?: return null
        store.save(emptyMap(), listOf(key))
        return PaymentTicket.of(line)?.takeIf { fresh(it, now) }
    }

    /** Every ticket let go (the feature turned off): what they were, so their notices can be taken away too. */
    @Synchronized
    fun clear(): List<PaymentTicket> {
        val all = store.all()
        if (all.isNotEmpty()) store.save(emptyMap(), all.keys)
        return all.values.mapNotNull { PaymentTicket.of(it) }
    }

    companion object {
        /** A day, as long as the notice itself is shown. */
        const val TTL_MS = 24 * 60 * 60_000L

        /** Kept at most; a shop busy enough for more lets the oldest go. */
        const val MAX = 100

        /** Within a day of [now], either way: a clock set back does not keep a ticket alive. */
        fun fresh(ticket: PaymentTicket, now: Long): Boolean = kotlin.math.abs(now - ticket.issuedAt) <= TTL_MS
    }
}

/**
 * The newest live entry already carrying a payment's reference (7 Nov 2026, the review's P3): when it was written and
 * whose it is. Read by [KhataDao.entryWithPaymentRef].
 */
data class PaymentRefUse(val timestamp: Long, val partyName: String?)
