package com.innovation313.roshankhata.data

/**
 * The reminder ladder (6 Oct 2026): the first reminder is gentle, the second
 * plain, the third serious — and all three stay polite; nothing here threatens.
 *
 * The app counts the reminders opened for a customer in the current chase.
 * A chase starts again at step 1 when the customer has paid anything since
 * the last reminder (the balance went down), or when 60 days have passed
 * since it. What WhatsApp did with the text the app cannot see, so this
 * counts reminders OPENED, as ReminderLog does. Kept per shop, on each device
 * (the phone's preferences, the computer's own file — never in the book).
 *
 * Shared since 25 Oct 2026 (Marhala 4: the computer's customer page and
 * Follow-up climb the same three steps): the arithmetic and the stored form
 * live here; where it is kept is each platform's (ReminderLadderAndroid on the
 * phone, ReminderStore on the computer). [step] is plain arithmetic over a
 * [State], unit-tested (ReminderLadderTest).
 */
object ReminderLadder {

    const val GENTLE = 1
    const val PLAIN = 2
    const val SERIOUS = 3

    private const val DAY = 24L * 60 * 60 * 1000
    private const val RESET_DAYS = 60

    /**
     * What is remembered for one customer: the step of the last reminder sent ([count] — the chase climbs one
     * step a reminder), the first and last time, the balance at the last, and [worked]: the step after which he
     * last paid (0: not learnt yet). Learnt since 2 Nov 2026 (the Payment Radar): a customer who pays after a
     * plain reminder starts the next chase plain, not gentle; one who pays after a gentle one is never pushed.
     */
    data class State(val count: Int, val firstAt: Long, val lastAt: Long, val balanceAtLast: Double, val worked: Int = 0)

    /** True when this chase is over and the next reminder starts again (gently, or at the step that worked). */
    fun reset(state: State?, balanceNow: Double, now: Long): Boolean =
        state == null ||
            now - state.lastAt > RESET_DAYS * DAY ||
            balanceNow < state.balanceAtLast - 0.004

    /**
     * The step after which this customer last paid: the last reminder's, when he paid something since it and
     * within the chase's [RESET_DAYS]; else what was learnt before; 0 when nothing is known.
     */
    fun learned(state: State?, balanceNow: Double, now: Long): Int = when {
        state == null -> 0
        balanceNow < state.balanceAtLast - 0.004 && now - state.lastAt <= RESET_DAYS * DAY -> state.count.coerceIn(GENTLE, SERIOUS)
        else -> state.worked
    }

    /** The step the NEXT reminder should be: 1, 2 or 3 (3 stays 3); a new chase starts at the step that worked. */
    fun step(state: State?, balanceNow: Double, now: Long): Int =
        if (reset(state, balanceNow, now)) learned(state, balanceNow, now).coerceIn(GENTLE, SERIOUS)
        else minOf(SERIOUS, state!!.count + 1)

    /** The state after one more reminder at [balanceNow], sent at [step]'s step. */
    fun next(state: State?, balanceNow: Double, now: Long): State =
        if (reset(state, balanceNow, now)) {
            val worked = learned(state, balanceNow, now)
            State(worked.coerceIn(GENTLE, SERIOUS), now, now, balanceNow, worked)
        } else state!!.copy(count = minOf(SERIOUS, state.count + 1), lastAt = now, balanceAtLast = balanceNow)

    /** The stored form, the same on both platforms: "count,firstAt,lastAt,balanceAtLast[,worked]". */
    fun encode(s: State): String =
        "${s.count},${s.firstAt},${s.lastAt},${s.balanceAtLast}" + if (s.worked > 0) ",${s.worked}" else ""

    /** [encode]'s form read back (four fields from before the learnt step, or five); null for anything else. */
    fun decode(raw: String?): State? {
        val p = raw?.split(',') ?: return null
        if (p.size != 4 && p.size != 5) return null
        return runCatching {
            State(p[0].toInt(), p[1].toLong(), p[2].toLong(), p[3].toDouble(), if (p.size == 5) p[4].toInt() else 0)
        }.getOrNull()
    }
}
