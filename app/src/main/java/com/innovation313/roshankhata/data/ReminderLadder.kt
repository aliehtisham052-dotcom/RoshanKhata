package com.innovation313.roshankhata.data

import android.content.Context

/**
 * The reminder ladder (6 Oct 2026): the first reminder is gentle, the second
 * plain, the third serious — and all three stay polite; nothing here threatens.
 *
 * The app counts the reminders opened for a customer in the current chase.
 * A chase starts again at step 1 when the customer has paid anything since
 * the last reminder (the balance went down), or when 60 days have passed
 * since it. What WhatsApp did with the text the app cannot see, so this
 * counts reminders OPENED, as ReminderLog does. Kept per shop, on this phone.
 *
 * [step] is plain arithmetic over a [State], unit-tested (ReminderLadderTest).
 */
object ReminderLadder {

    const val GENTLE = 1
    const val PLAIN = 2
    const val SERIOUS = 3

    private const val PREFS = "reminder_ladder"
    private const val DAY = 24L * 60 * 60 * 1000
    private const val RESET_DAYS = 60

    /** What is remembered for one customer: how many sent, the first and last time, the balance at the last. */
    data class State(val count: Int, val firstAt: Long, val lastAt: Long, val balanceAtLast: Double)

    /** True when this chase is over and the next reminder starts gently again. */
    internal fun reset(state: State?, balanceNow: Double, now: Long): Boolean =
        state == null ||
            now - state.lastAt > RESET_DAYS * DAY ||
            balanceNow < state.balanceAtLast - 0.004

    /** The step the NEXT reminder should be: 1, 2 or 3 (3 stays 3). */
    fun step(state: State?, balanceNow: Double, now: Long): Int =
        if (reset(state, balanceNow, now)) GENTLE else minOf(SERIOUS, state!!.count + 1)

    /** The state after one more reminder at [balanceNow]. */
    internal fun next(state: State?, balanceNow: Double, now: Long): State =
        if (reset(state, balanceNow, now)) State(1, now, now, balanceNow)
        else state!!.copy(count = state.count + 1, lastAt = now, balanceAtLast = balanceNow)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS + Businesses.suffix(context), Context.MODE_PRIVATE)

    fun state(context: Context, partyId: Long): State? {
        val raw = prefs(context).getString(partyId.toString(), null) ?: return null
        val p = raw.split(',')
        if (p.size != 4) return null
        return runCatching { State(p[0].toInt(), p[1].toLong(), p[2].toLong(), p[3].toDouble()) }.getOrNull()
    }

    fun record(context: Context, partyId: Long, balanceNow: Double, now: Long = System.currentTimeMillis()) {
        val s = next(state(context, partyId), balanceNow, now)
        prefs(context).edit().putString(partyId.toString(), "${s.count},${s.firstAt},${s.lastAt},${s.balanceAtLast}").apply()
    }
}
