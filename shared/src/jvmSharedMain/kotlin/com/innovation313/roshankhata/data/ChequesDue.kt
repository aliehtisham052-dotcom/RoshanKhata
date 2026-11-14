package com.innovation313.roshankhata.data

import java.util.TimeZone

/**
 * "A cheque is due", said once (14 Nov 2026, the cheque reminders): the bell ([LedgerAlerts]), the phone's daily
 * sweep (its count) and the Cheque reminders automation ([Automations.Kind.CHEQUES]) all ask THIS, so they can
 * never name different cheques (lesson 65: two things that must agree are one thing — the 3 Nov audit's M14 was
 * exactly a notice that counted by its own rule and named what the bell did not show).
 *
 * Due = still [ChequeStatus.PENDING] and its written day is today or earlier. A cleared, bounced or cancelled
 * cheque is settled whatever its day says. "Which way": [Cheque.isReceived] true is a cheque held to bank
 * (money coming in), false one issued (money going out).
 *
 * Plain Kotlin on purpose: no Room, no Android — a test can run it on any machine.
 */
object ChequesDue {

    /**
     * The first moment of tomorrow, by the calendar: a cheque is due when its day is BEFORE this. Not
     * `startOfDay + 24 h`: on the day a zone puts its clocks forward that sum lands an hour into the NEXT
     * day and tomorrow's cheque (dated midnight) would read as due today. Thirty-six hours from midnight is
     * always inside tomorrow whichever way the clocks moved, and [DayMath.startOfDay] snaps it back to its
     * midnight — the rule `Automations.dayEnd` already uses for "the whole day".
     */
    fun endOfToday(now: Long, tz: TimeZone = TimeZone.getDefault()): Long =
        DayMath.startOfDay(DayMath.startOfDay(now, tz) + 36L * 60 * 60 * 1000, tz)

    /** Pending, and its day is today or before. [endOfToday] is [endOfToday]'s answer, worked out once by the caller. */
    fun isDue(c: Cheque, endOfToday: Long): Boolean = c.status == ChequeStatus.PENDING && c.dueDate < endOfToday

    /** Past its day: due, and its day is before today's midnight — the bell's "Late" tag. */
    fun isLate(c: Cheque, now: Long, tz: TimeZone = TimeZone.getDefault()): Boolean = c.dueDate < DayMath.startOfDay(now, tz)

    /**
     * What the notice says: how many are to be banked and how many to pay (each with its sum), how many of the
     * two are past their day, and up to three names, the oldest cheque's first.
     */
    data class Summary(
        val received: Int,
        val receivedTotal: Double,
        val issued: Int,
        val issuedTotal: Double,
        val late: Int,
        val names: List<String>
    ) {
        val count: Int get() = received + issued

        companion object {
            val NONE = Summary(0, 0.0, 0, 0.0, 0, emptyList())
        }
    }

    /**
     * The due ones among [cheques], counted by direction. [names] is party id to name; a party with no name
     * here (deleted, or not looked up) is left out of the names, never shown blank, and one name is said once
     * however many of its cheques are due.
     */
    fun summarise(cheques: List<Cheque>, names: Map<Long, String>, now: Long, tz: TimeZone = TimeZone.getDefault()): Summary {
        val end = endOfToday(now, tz)
        val due = cheques.filter { isDue(it, end) }.sortedBy { it.dueDate }
        if (due.isEmpty()) return Summary.NONE
        val in_ = due.filter { it.isReceived }
        val out = due.filter { !it.isReceived }
        val named = due.mapNotNull { names[it.partyId]?.trim()?.takeIf { n -> n.isNotEmpty() } }.distinct().take(3)
        return Summary(in_.size, in_.sumOf { it.amount }, out.size, out.sumOf { it.amount },
            due.count { isLate(it, now, tz) }, named)
    }
}
