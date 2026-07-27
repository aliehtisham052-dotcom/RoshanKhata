package com.innovation313.roshankhata.data

/**
 * Reads a spoken sentence and works out what entry it describes.
 *
 * "Bilal ko paanch hazaar diye" has to become a party, an amount, and a
 * direction. All three are guessed here and none is acted on: the owner is
 * shown what was understood and presses Save themselves. This is money, and a
 * microphone in a noisy shop is not a witness worth trusting on its own.
 *
 * Everything below runs on the phone. Only the audio leaves it, and only as
 * far as the system's own speech service — this code never sees the customer
 * list travel anywhere.
 */
object VoiceEntry {

    /** What a sentence turned out to mean. Any field may be missing. */
    data class Parsed(
        val partyName: String?,
        val amount: Double?,
        /** true = the shop gave, false = the shop received, null = unclear. */
        val isGiven: Boolean?
    )

    // Both scripts, because the recogniser returns whichever the speaker used.
    private val GAVE = listOf(
        "diye", "diya", "di", "dee", "dena", "dene", "de diye", "de dia",
        "دیے", "دیا", "دی", "دیں", "دینا", "دیئے",
        "gave", "give", "given", "paid", "pay", "lent"
    )
    private val GOT = listOf(
        "liye", "liya", "li", "mile", "mila", "mili", "wapis", "wasool",
        "aaye", "aya",
        "لیے", "لیا", "لی", "لیں", "ملے", "ملا", "ملی", "واپس", "وصول", "آئے",
        "got", "received", "took", "taken", "collected", "returned", "repaid",
        // Deliberately here as well as in GAVE. "Gave back" and "paid back"
        // read as both directions at once, so the sentence is reported as
        // unclear and the owner is asked — which is the honest answer, since
        // neither phrase says who handed what to whom.
        "gave back", "paid back"
    )

    /** Urdu and Arabic-Indic digits, so ٥٠٠٠ and ۵۰۰۰ read as 5000. */
    private val EASTERN_DIGITS = mapOf(
        '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
        '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9',
        '۰' to '0', '۱' to '1', '۲' to '2', '۳' to '3', '۴' to '4',
        '۵' to '5', '۶' to '6', '۷' to '7', '۸' to '8', '۹' to '9'
    )

    /** Spoken numbers, in the forms a shopkeeper actually uses. */
    private val UNITS = mapOf(
        "ek" to 1.0, "aik" to 1.0, "ایک" to 1.0,
        "do" to 2.0, "دو" to 2.0,
        "teen" to 3.0, "tin" to 3.0, "تین" to 3.0,
        "char" to 4.0, "chaar" to 4.0, "چار" to 4.0,
        "panch" to 5.0, "paanch" to 5.0, "پانچ" to 5.0,
        "che" to 6.0, "chay" to 6.0, "چھ" to 6.0,
        "saat" to 7.0, "سات" to 7.0,
        "aath" to 8.0, "ath" to 8.0, "آٹھ" to 8.0,
        "nau" to 9.0, "no" to 9.0, "نو" to 9.0,
        "das" to 10.0, "dus" to 10.0, "دس" to 10.0,
        "bees" to 20.0, "بیس" to 20.0,
        "pachas" to 50.0, "pachaas" to 50.0, "پچاس" to 50.0,

        // English, for an owner who has set the app to English. Written out to
        // nineteen and then by tens, because English says "twenty five" where
        // Urdu says "pachees" — the tens and the units arrive as two words and
        // are added together by the reader below.
        "one" to 1.0, "two" to 2.0, "three" to 3.0, "four" to 4.0,
        "five" to 5.0, "six" to 6.0, "seven" to 7.0, "eight" to 8.0,
        "nine" to 9.0, "ten" to 10.0,
        "eleven" to 11.0, "twelve" to 12.0, "thirteen" to 13.0,
        "fourteen" to 14.0, "fifteen" to 15.0, "sixteen" to 16.0,
        "seventeen" to 17.0, "eighteen" to 18.0, "nineteen" to 19.0,
        "twenty" to 20.0, "thirty" to 30.0, "forty" to 40.0, "fifty" to 50.0,
        "sixty" to 60.0, "seventy" to 70.0, "eighty" to 80.0, "ninety" to 90.0
    )

    private val MULTIPLIERS = mapOf(
        "sau" to 100.0, "سو" to 100.0,
        "hazar" to 1000.0, "hazaar" to 1000.0, "hazār" to 1000.0, "ہزار" to 1000.0,
        "lakh" to 100_000.0, "lac" to 100_000.0, "لاکھ" to 100_000.0,
        // Crore was missing on both sides. A shop that deals in lakhs
        // eventually says the next word up.
        "crore" to 10_000_000.0, "karor" to 10_000_000.0, "کروڑ" to 10_000_000.0,
        "hundred" to 100.0, "thousand" to 1000.0, "million" to 1_000_000.0
    )

    /**
     * Read [spoken] against the shop's own customer names.
     *
     * [knownNames] is passed in rather than looked up, so this stays pure and
     * can be checked without a database behind it.
     */
    fun parse(spoken: String, knownNames: List<String>): Parsed {
        val text = normalise(spoken)
        return Parsed(
            partyName = findParty(text, knownNames),
            amount = findAmount(text),
            isGiven = findDirection(text)
        )
    }

    private fun normalise(s: String): String {
        val sb = StringBuilder()
        for (ch in s.lowercase()) sb.append(EASTERN_DIGITS[ch] ?: ch)
        return sb.toString()
    }

    /**
     * Words that are grammar, not names.
     *
     * This list is the whole reason the matcher can be trusted. "میں نے"
     * reduces to the skeleton "mn" — and so do Moon, Amin, Meena, Mannan and
     * Munna. Because the old matcher poured the entire sentence into one run
     * of letters and looked for the name anywhere inside it, every sentence
     * beginning "میں نے" silently matched whichever of those names the shop
     * had on its books, and the owner was shown a stranger above their money.
     *
     * Grammar is stripped before any name is looked for, so it can never
     * stand in for one. Words that appear inside real names — جی، بھائی،
     * صاحب، چاچا — are deliberately absent: "Abu G" is a customer.
     */
    private val STOPWORDS = setOf(
        "میں", "مین", "نے", "کو", "کا", "کی", "کے", "سے", "پر", "کہ",
        "ہے", "ہیں", "ہوں", "ہوا", "ہوئے", "تھا", "تھی", "تھے",
        "اور", "بھی", "تو", "یہ", "وہ", "روپے", "روپیہ",
        "main", "mein", "ne", "ko", "ka", "ki", "ke", "se", "par", "keh",
        // The same pronouns as one word, which is how they are actually
        // transcribed. "Main ne" was covered; "Maine" was not, and that is
        // what comes back when the sentence is spoken at normal speed. Its
        // consonants are m-n — the same as Moon, Mona, Mani or Amna — so it
        // was matching customers by name. The rest of the family is here for
        // the same reason, before they are found the same way.
        "maine", "mainay", "mainey", "mene", "menay", "meine",
        "tumne", "tumnay", "usne", "usnay", "humne", "humnay",
        "unhone", "unhonay", "isne", "isnay",
        // Words that stand in for a name instead of being one. The logs
        // caught all of these being taken as customers: "Maine kisi ko 50000
        // diya" searched the book for a man called Kisi, and "Mere original
        // company" for one called Mere.
        "kisi", "kis", "kuch", "kuchh", "koi", "kissi",
        "mera", "meri", "mere", "apna", "apni", "apne",
        "کسی", "کچھ", "کوئی", "میرا", "میری", "میرے", "اپنا", "اپنی", "اپنے",
        "hai", "hain", "hun", "tha", "thi", "the", "aur", "bhi", "to",
        "rupay", "rupaye", "rupee", "rupees", "rs",
        // English grammar. Same reasoning as above: these are words a sentence
        // is built from, never a customer. Words that could be part of a name
        // are left out.
        "i", "you", "he", "she", "we", "they", "him", "her", "them",
        "a", "an", "of", "for", "from", "and", "is", "are", "was", "were",
        "has", "have", "had", "it", "this", "that", "on", "in", "at",
        "total", "amount", "money", "cash"
    )

    /** Split on anything that is not a letter or a digit, in either script. */
    private fun tokenise(text: String): List<String> =
        text.split(Regex("[^\\p{L}\\p{Nd}]+")).filter { it.isNotBlank() }

    /** Grammar, figures and verbs — everything a name is not. */
    /**
     * The words of [spoken] that could be part of a name.
     *
     * Grammar, figures and the giving and taking verbs are already known here
     * and thrown away, so what comes back is what the owner would have typed
     * into the search box if they had typed instead of spoken. Exposed so the
     * picker can search with it: when this reader cannot name the customer
     * with confidence, the next best thing is to search for them the way any
     * other screen would, not to hand back the whole book.
     */
    fun nameWords(spoken: String): List<String> =
        tokenise(normalise(spoken)).filterNot { isNoise(it) }

    /**
     * Which of the recogniser's answers to act on, by index.
     *
     * It offers up to five and this app took the first, on the assumption that
     * first means best. Three logs of real entries say otherwise. The list is
     * not even ordered by the recogniser's own confidence — one attempt acted
     * on a candidate scored 0.70 while the third in the list stood at 0.75 —
     * and the first is often the one that lost the end of the sentence:
     * "Maine Memorial School" was acted on and refused for having no amount,
     * while every other candidate carried the 5000. Another attempt heard
     * "Kripa", which answers nobody in the book, when three of its five
     * candidates said "khurpa", which fifty customers carry.
     *
     * So each candidate is asked what it would actually produce. An amount is
     * what makes an entry possible at all, so it outweighs everything else; a
     * direction is worth a little; and beyond that the candidate whose words
     * answer somebody in this book beats the one whose words answer nobody.
     *
     * Confidence is deliberately not consulted. The recogniser gives the
     * truncated variant the HIGHER score — 0.89 for "Maine Memorial School"
     * against 0.83 for the sentence with the figure in it — so trusting it
     * would sharpen exactly the wrong edge.
     *
     * Ties keep the recogniser's own order, so nothing changes for the many
     * sentences whose candidates differ only in how the verb was spelled.
     *
     * @param nameScore how well a set of spoken words answers the book. Passed
     *   in rather than reached for, so this rule can be checked without one.
     */
    fun bestCandidate(
        candidates: List<String>,
        knownNames: List<String>,
        nameScore: (List<String>) -> Double
    ): Int {
        if (candidates.size < 2) return 0

        // Most candidates differ only in the verb — "diye" against "di hai" —
        // and leave the name alone. Asking the book once per distinct set of
        // name words rather than once per candidate keeps this to a single
        // pass in the ordinary case, against a book of eleven hundred.
        val asked = HashMap<List<String>, Double>()

        var bestAt = 0
        var best = Double.NEGATIVE_INFINITY

        for ((i, candidate) in candidates.withIndex()) {
            val parsed = parse(candidate, knownNames)
            val amount = parsed.amount

            var score = 0.0
            if (amount != null && amount > 0.0) score += AMOUNT_IS_EVERYTHING
            if (parsed.isGiven != null) score += DIRECTION_HELPS

            val words = nameWords(candidate)
            if (words.isNotEmpty()) score += asked.getOrPut(words) { nameScore(words) }

            if (score > best) {
                best = score
                bestAt = i
            }
        }
        return bestAt
    }

    /** Without a figure there is no entry to make, whatever else was heard. */
    private const val AMOUNT_IS_EVERYTHING = 100.0

    /** Worth having, but the owner confirms it before anything is saved. */
    private const val DIRECTION_HELPS = 10.0

    private fun isNoise(word: String): Boolean =
        word in STOPWORDS ||
            UNITS.containsKey(word) ||
            MULTIPLIERS.containsKey(word) ||
            word in GAVE ||
            word in GOT ||
            word.all { it.isDigit() }

    /**
     * Which customer the sentence is about, or null when it cannot be told.
     *
     * The sentence is cut into words, grammar and figures are thrown away, and
     * what remains is matched a **whole word at a time**. Matching runs of
     * words rather than a flat run of letters is what stops a name from being
     * found straddling the middle of two unrelated ones.
     *
     * Null is returned both when nothing matches and when two customers match
     * equally well. Guessing between two people would put the money on a
     * stranger's page; the caller opens the customer list instead, which costs
     * one tap and cannot be wrong.
     */
    private fun findParty(text: String, knownNames: List<String>): String? {
        val words = tokenise(text).filterNot { isNoise(it) }
        if (words.isEmpty()) return null

        // Every run of consecutive words, in both representations. A run is
        // whole words by construction, so boundaries are respected for free.
        val runsSkeleton = mutableListOf<String>()
        val runsSounds = mutableListOf<String>()
        for (i in words.indices) {
            val sk = StringBuilder()
            val so = StringBuilder()
            for (j in i until words.size) {
                sk.append(skeleton(words[j]))
                so.append(sounds(words[j]))
                runsSkeleton += sk.toString()
                runsSounds += so.toString()
            }
        }

        var best: String? = null
        var bestScore = 0
        var tied = false
        for (name in knownNames) {
            val s = scoreName(name, runsSkeleton, runsSounds)
            if (s <= 0) continue
            when {
                s > bestScore -> {
                    best = name
                    bestScore = s
                    tied = false
                }
                s == bestScore && !name.equals(best, ignoreCase = true) -> tied = true
            }
        }
        return if (tied) null else best
    }

    /**
     * How well a stored name fits what was said. Zero means it does not.
     *
     * A whole run equalling the name outranks everything; a name only partly
     * spoken scores by how much of it was heard, and needs three consonants
     * before it counts at all.
     *
     * A short skeleton is not the problem, and a guard against one does real
     * damage. Two consonants was briefly treated as too thin to trust unless
     * the vowels agreed too — which broke the very thing the skeleton is for.
     * A shop saves "Abu G" in Latin and says it in Urdu, where the short
     * vowels are not written at all; asking the vowels to agree across two
     * scripts asks for something neither script can give. The tests caught it.
     *
     * The name that went wrong was never a short name. It was a grammar word
     * being read as one: "Maine" reduces to m-n and so does Moon, and with
     * the pronoun still in play a whole match on it outscored a partial match
     * on the customer actually named. Take the pronoun out and there is
     * nothing left for Moon to match — "Ahmad Ali" wins on its own merit.
     * Grammar belongs in the stopword list. Nothing here needed changing.
     */
    private fun scoreName(
        name: String,
        runsSkeleton: List<String>,
        runsSounds: List<String>
    ): Int {
        val sk = skeleton(name)
        if (sk.length >= 2) {
            if (runsSkeleton.any { it == sk }) return 1000 + sk.length
            // Part of a long stored name was spoken: "Abdul Rahman" standing
            // in for "Abdul Rahman Chacha Lamber Lappay Wali".
            val partial = runsSkeleton
                .filter { it.length >= 3 && sk.startsWith(it) }
                .maxByOrNull { it.length }
            return partial?.length ?: 0
        }
        // Names too short to leave two consonants behind — Ali is only "l".
        // These were unmatchable before. They are compared with their vowels
        // kept and only on an exact whole run, which is safe where a bare
        // consonant would not be.
        val so = sounds(name)
        return if (so.isNotEmpty() && runsSounds.any { it == so }) 1000 + so.length else 0
    }

    /** Urdu letters to the sound a Latin spelling would use. */
    private val LETTERS = mapOf(
        'ا' to "a", 'آ' to "a", 'ب' to "b", 'پ' to "p", 'ت' to "t", 'ٹ' to "t",
        'ث' to "s", 'ج' to "j", 'چ' to "ch", 'ح' to "h", 'خ' to "kh", 'د' to "d",
        'ڈ' to "d", 'ذ' to "z", 'ر' to "r", 'ڑ' to "r", 'ز' to "z", 'ژ' to "zh",
        'س' to "s", 'ش' to "sh", 'ص' to "s", 'ض' to "z", 'ط' to "t", 'ظ' to "z",
        'ع' to "a", 'غ' to "gh", 'ف' to "f", 'ق' to "q", 'ک' to "k", 'ك' to "k",
        'گ' to "g", 'ل' to "l", 'م' to "m", 'ن' to "n", 'ں' to "n", 'و' to "u",
        'ہ' to "h", 'ھ' to "h", 'ة' to "h", 'ء' to "", 'ی' to "i", 'ي' to "i",
        'ے' to "e", 'أ' to "a", 'إ' to "a", 'ؤ' to "u", 'ئ' to "i"
    )

    /** Sounds a Pakistani ear treats as the same when spelling a name. */
    private val SAME_SOUND = mapOf('g' to 'j', 'q' to 'k', 'v' to 'b', 'c' to 'k')

    private const val VOWELS = "aeiou"

    /**
     * The consonants of a name, in whichever script it was written.
     *
     * Vowels go because Urdu does not write the short ones; doubles collapse
     * because Urdu marks them rather than repeating the letter; and g/j, q/k,
     * v/b, c/k fold together because a name spelled one way is routinely
     * spoken the other.
     */
    private fun skeleton(s: String): String {
        val sb = StringBuilder()
        for (ch in s.lowercase()) {
            val mapped = LETTERS[ch] ?: if (ch.isLetter()) ch.toString() else ""
            sb.append(mapped)
        }
        val folded = StringBuilder()
        for (ch in sb) {
            if (!ch.isLetter()) continue
            val f = SAME_SOUND[ch] ?: ch
            if (f in VOWELS) continue
            if (folded.isEmpty() || folded.last() != f) folded.append(f)
        }
        return folded.toString()
    }

    /**
     * The same transliteration as [skeleton], but with the vowels left in.
     *
     * Used only for names so short that dropping vowels leaves nothing to
     * match on. "Ali" and "علی" both become "ali"; the skeleton of either is
     * the single letter "l", which is far too little to risk a match on.
     */
    private fun sounds(s: String): String {
        val sb = StringBuilder()
        for (ch in s.lowercase()) {
            val mapped = LETTERS[ch] ?: if (ch.isLetter()) ch.toString() else ""
            sb.append(mapped)
        }
        val folded = StringBuilder()
        for (ch in sb) {
            if (!ch.isLetter()) continue
            val f = SAME_SOUND[ch] ?: ch
            if (folded.isEmpty() || folded.last() != f) folded.append(f)
        }
        return folded.toString()
    }

    /**
     * The amount, whether spoken as digits or as words.
     *
     * Digits win when present: someone who says "5000" means 5000, and no
     * word-reading can improve on that.
     */
    private fun findAmount(text: String): Double? {
        Regex("\\d[\\d,]*(\\.\\d+)?").find(text)?.let { m ->
            val cleaned = m.value.replace(",", "")
            cleaned.toDoubleOrNull()?.let { digits ->
                // "5 hazaar" — a digit followed by a multiplier word.
                val after = text.substring(m.range.last + 1).trim().split(" ").firstOrNull()
                val mult = MULTIPLIERS[after]
                return if (mult != null) digits * mult else digits
            }
        }
        return fromWords(text)
    }

    /**
     * Spoken numbers: "paanch hazaar" is 5000, "do lakh" is 200000, and a
     * bare "hazaar" on its own is 1000.
     *
     * A multiplier of a thousand or more closes off what came before it and
     * banks the result; a smaller one — a hundred — folds into what is being
     * built. This is what lets English say a figure the way English says it.
     * "One hundred thousand" is a hundred thousands, not a hundred and then a
     * thousand: reading it as the latter, which the earlier version did, gave
     * 1,100 for a figure a shopkeeper meant as 100,000.
     */
    private fun fromWords(text: String): Double? {
        var total = 0.0
        var current = 0.0
        var seen = false

        for (w in text.split(" ", "،", ",")) {
            val unit = UNITS[w]
            if (unit != null) {
                current += unit
                seen = true
                continue
            }
            val mult = MULTIPLIERS[w] ?: continue
            seen = true
            // A multiplier with nothing before it counts once: "hazaar" is one
            // thousand, not none.
            val take = if (current == 0.0) 1.0 else current
            if (mult >= 1000.0) {
                total += take * mult
                current = 0.0
            } else {
                current = take * mult
            }
        }
        return if (seen) total + current else null
    }

    /**
     * Which way the money went.
     *
     * Matched a whole word at a time. The old test padded the sentence with
     * spaces and looked for " دیا " inside it, with an endsWith as a fallback —
     * which meant "میں نے احمد کو پانچ ہزار دی ہے" found nothing at all: the
     * verb was "دی", and the sentence ended on "ہے" rather than the verb.
     * Splitting into words removes the need for either trick.
     *
     * Null when the sentence does not say. A guess here would put the amount
     * on the wrong side of the ledger, which is worse than asking.
     */
    private fun findDirection(text: String): Boolean? {
        val words = tokenise(text)
        fun spoken(terms: List<String>) = terms.any { term ->
            if (term.contains(' ')) text.contains(term) else words.contains(term)
        }
        val gave = spoken(GAVE)
        val got = spoken(GOT)
        return when {
            gave && !got -> true
            got && !gave -> false
            else -> null
        }
    }
}
