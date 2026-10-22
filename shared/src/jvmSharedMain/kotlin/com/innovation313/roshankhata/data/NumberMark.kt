package com.innovation313.roshankhata.data

/**
 * This device's letter in the book's document numbers while the book is kept
 * live with another device (22 Oct 2026, the owner: "jo professional tareeqa
 * hai"). Two devices that each write a ticket while apart would otherwise both
 * hand out S-000013: with the letter they write S-000013-A and S-000013-B.
 *
 * The letter goes AFTER the number on purpose: every "highest number" query
 * reads the digits after the prefix with SQLite's CAST, which stops at the
 * first character that is not a digit — so S-000013-B still counts as 13 on
 * this device and on one running an older version, and the series keeps
 * running forward across both devices (A writes 14 after B's 13).
 *
 * Null while the open book does not sync: numbers are exactly as before.
 * Set by whoever opens a book or turns its live sync on or off (the phone's
 * database callback and live sync screens, the computer's LiveDesk); one book
 * is open at a time on each device.
 */
object NumberMark {
    @Volatile var mark: String? = null

    /** "-B", or nothing while the open book does not sync. */
    fun suffix(): String = mark?.let { "-$it" }.orEmpty()
}
