package com.innovation313.roshankhata.ui

/**
 * Which numbers a poster shows (13 Nov 2026, the owner: "call aur WhatsApp ka alag number").
 *
 * A shop often takes calls on one number and answers WhatsApp on another. The call number is the shop profile's
 * phone, what every poster has always shown; the WhatsApp number is the poster's own: typed on the poster screen,
 * kept with the poster's other choices and never written into the profile, so an invoice or a statement says what it
 * always said. Each number is drawn with its own mark, a handset for the call and a chat bubble for WhatsApp, so no
 * word has to be set in nine languages beside it.
 *
 * Pure rules on text, no drawing (PosterKit draws): which numbers there are, in reading order, and whether the call
 * number carries the chat mark that says it is on WhatsApp too.
 */
object PosterContact {

    enum class Kind { CALL, WHATSAPP }

    class Item(val kind: Kind, val number: String)

    /**
     * [items] in reading order, the call number first. [chatMark]: the one call number is also on WhatsApp, which the
     * call button shows as a chat mark at its other end (as it always did).
     */
    class Contact(val items: List<Item>, val chatMark: Boolean) {
        val any: Boolean get() = items.isNotEmpty()
        val both: Boolean get() = items.size > 1
        fun has(kind: Kind): Boolean = items.any { it.kind == kind }
    }

    /** The fewest and the most digits a phone number has (E.164 allows 15). */
    const val MIN_DIGITS = 7
    const val MAX_DIGITS = 15

    /** The longest a typed number is kept, spaces and marks included. */
    const val MAX_LENGTH = 24

    /**
     * What a poster shows. [phone] is the profile's phone (null when the owner does not show it), [onWhatsapp] the
     * owner's "the call number is on WhatsApp too" tick, [whatsappNumber] the poster's own WhatsApp number ("" = none).
     *
     *  - No WhatsApp number of its own: the call number alone, with its chat mark when the owner ticked it.
     *  - The same number typed again: it is one number and it is on WhatsApp, so the chat mark and no second item.
     *  - A different number: the call number (if shown) and the WhatsApp one, no chat mark on the call number, which
     *    is not the WhatsApp one. A WhatsApp number can stand alone when the call number is not shown.
     */
    fun of(phone: String?, onWhatsapp: Boolean, whatsappNumber: String): Contact {
        val call = phone?.trim()?.takeIf { it.isNotEmpty() }
        val wa = whatsappNumber.trim().takeIf { it.isNotEmpty() }
        val callItem = call?.let { Item(Kind.CALL, it) }
        if (wa == null) return Contact(listOfNotNull(callItem), chatMark = call != null && onWhatsapp)
        if (call != null && same(call, wa)) return Contact(listOf(Item(Kind.CALL, call)), chatMark = true)
        return Contact(listOfNotNull(callItem, Item(Kind.WHATSAPP, wa)), chatMark = false)
    }

    /** A digit of any script as 0-9, or null for anything that is not a decimal digit. */
    private fun asciiDigit(c: Char): Char? {
        val v = Character.digit(c, 10)
        return if (v in 0..9) ('0' + v) else null
    }

    /** The digits of [s], in 0-9 whatever script they were typed in. */
    fun digits(s: String): String = buildString { for (c in s) asciiDigit(c)?.let { append(it) } }

    /**
     * Whether two typed numbers are one number. 0300 1234567, 300-1234567 and +92 300 1234567 are: the last ten
     * digits are compared when both have that many, else the whole of each without its leading zeros. A number too
     * short to be told apart ([MIN_DIGITS]) is never the same as another.
     */
    fun same(a: String, b: String): Boolean {
        val x = digits(a)
        val y = digits(b)
        if (x.length < MIN_DIGITS || y.length < MIN_DIGITS) return false
        if (x.length >= 10 && y.length >= 10) return x.takeLast(10) == y.takeLast(10)
        return x.trimStart('0') == y.trimStart('0')
    }

    /**
     * A typed number made ready to keep: digits in 0-9, and only the marks a number is written with (+ - ( ) and a
     * single space between groups), trimmed, at most [MAX_LENGTH] long. Anything else typed is dropped.
     */
    fun clean(raw: String): String {
        val sb = StringBuilder()
        for (ch in raw) {
            val d = asciiDigit(ch)
            when {
                d != null -> sb.append(d)
                ch == '+' || ch == '-' || ch == '(' || ch == ')' -> sb.append(ch)
                ch.isWhitespace() -> if (sb.isNotEmpty() && sb.last() != ' ') sb.append(' ')
            }
        }
        return sb.toString().trim().take(MAX_LENGTH).trim()
    }

    /** Whether a cleaned number has the digits of a phone number. */
    fun usable(number: String): Boolean = digits(number).length in MIN_DIGITS..MAX_DIGITS

    /**
     * What is kept of what was typed: the cleaned number once it has the digits of a phone number, else nothing. The
     * phone's dialog and the computer's box both go through this, so a half-typed number is never drawn on a poster.
     */
    fun kept(typed: String): String = clean(typed).takeIf { usable(it) }.orEmpty()
}
