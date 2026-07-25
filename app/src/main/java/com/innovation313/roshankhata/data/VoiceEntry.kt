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
        "diye", "diya", "dena", "dene", "de diye", "de dia",
        "دیے", "دیا", "دینا", "دیئے"
    )
    private val GOT = listOf(
        "liye", "liya", "mile", "mila", "wapis", "wasool", "aaye", "aya",
        "لیے", "لیا", "ملے", "ملا", "واپس", "وصول", "آئے"
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
        "pachas" to 50.0, "pachaas" to 50.0, "پچاس" to 50.0
    )

    private val MULTIPLIERS = mapOf(
        "sau" to 100.0, "سو" to 100.0,
        "hazar" to 1000.0, "hazaar" to 1000.0, "hazār" to 1000.0, "ہزار" to 1000.0,
        "lakh" to 100_000.0, "lac" to 100_000.0, "لاکھ" to 100_000.0
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
     * The longest customer name that appears in the sentence.
     *
     * Longest wins so "Bilal Bhai" is preferred over "Bilal" when both are on
     * the books and both are in the sentence — the fuller name is the more
     * deliberate one.
     */
    private fun findParty(text: String, knownNames: List<String>): String? =
        knownNames
            .filter { it.isNotBlank() && text.contains(it.lowercase()) }
            .maxByOrNull { it.length }

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
     */
    private fun fromWords(text: String): Double? {
        val words = text.split(" ", "،", ",")
        var total: Double? = null
        var pending: Double? = null

        for (w in words) {
            val unit = UNITS[w]
            if (unit != null) {
                pending = (pending ?: 0.0) + unit
                continue
            }
            val mult = MULTIPLIERS[w]
            if (mult != null) {
                total = (total ?: 0.0) + (pending ?: 1.0) * mult
                pending = null
            }
        }
        if (pending != null) total = (total ?: 0.0) + pending
        return total
    }

    /**
     * Which way the money went.
     *
     * Null when the sentence does not say. A guess here would put the amount
     * on the wrong side of the ledger, which is worse than asking.
     */
    private fun findDirection(text: String): Boolean? {
        val padded = " $text "
        val gave = GAVE.any { padded.contains(" $it ") || text.endsWith(it) }
        val got = GOT.any { padded.contains(" $it ") || text.endsWith(it) }
        return when {
            gave && !got -> true
            got && !gave -> false
            else -> null
        }
    }
}
