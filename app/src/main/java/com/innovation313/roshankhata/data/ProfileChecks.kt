package com.innovation313.roshankhata.data

import java.util.Locale

/**
 * The small checks behind the Business Profile screen. Plain JVM, no Android,
 * so every rule here is unit-tested directly.
 *
 * All three are WARNINGS, never refusals: the screen still saves. An account
 * number that is not an IBAN is legitimate, and a shop may well print a
 * number this app has never seen the shape of.
 */
object ProfileChecks {

    /**
     * A Pakistani IBAN is 24 characters: "PK", two check digits, then 20
     * letters and digits (a 4-letter bank code and a 16-character account).
     *
     * Only text that tries to be an IBAN is judged — anything containing a
     * letter. A box of plain digits is an account number (the field is
     * "IBAN / account number") and is left alone. Spaces are ignored, since
     * IBANs are usually written in groups of four.
     */
    fun ibanLooksWrong(raw: String, pakistani: Boolean = true): Boolean {
        val t = raw.replace(" ", "").replace("-", "").uppercase(Locale.ROOT)
        if (t.isEmpty() || t.none { it.isLetter() }) return false
        // Another country's shop (4 Oct 2026): any IBAN shape — country, check digits, 11-30 more.
        if (!pakistani) return !Regex("^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$").matches(t)
        return !Regex("^PK[0-9]{2}[A-Z0-9]{20}$").matches(t)
    }

    /**
     * A Pakistani mobile (JazzCash / EasyPaisa are tied to one) is 11 digits
     * starting 03, also written +92 3.. or 92 3.. — all three are accepted.
     * Spaces and dashes are ignored.
     */
    fun mobileLooksWrong(raw: String, pakistani: Boolean = true): Boolean {
        val t = raw.filter { !it.isWhitespace() && it != '-' }
        if (t.isEmpty()) return false
        // Another country's shop (4 Oct 2026): 7 to 15 digits, an optional leading +.
        if (!pakistani) return !Regex("^\\+?[0-9]{7,15}$").matches(t)
        val local = when {
            t.startsWith("+92") -> "0" + t.drop(3)
            t.startsWith("0092") -> "0" + t.drop(4)
            t.startsWith("92") && t.length == 12 -> "0" + t.drop(2)
            else -> t
        }
        return !Regex("^03[0-9]{9}$").matches(local)
    }

    /**
     * Up to two initials for the logo square: the first letter of the first
     * two words. Works for any script — Urdu names give Urdu letters.
     */
    fun initials(name: String): String =
        name.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { String(Character.toChars(it.codePointAt(0))).uppercase(Locale.getDefault()) }
}
