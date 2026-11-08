package com.innovation313.roshankhata.data

import android.content.Context

/**
 * Where the phone keeps the reminder ladder ([ReminderLadder], shared since
 * 25 Oct 2026): its preferences, per shop, one line per customer in the
 * ladder's own stored form. The same calls as before the move, as extensions.
 */
private const val LADDER_PREFS = "reminder_ladder"

private fun ladderPrefs(context: Context) =
    context.getSharedPreferences(LADDER_PREFS + Businesses.suffix(context), Context.MODE_PRIVATE)

fun ReminderLadder.state(context: Context, partyId: Long): ReminderLadder.State? =
    decode(ladderPrefs(context).getString(partyId.toString(), null))

fun ReminderLadder.record(context: Context, partyId: Long, balanceNow: Double, now: Long = System.currentTimeMillis()) {
    val s = next(state(context, partyId), balanceNow, now)
    ladderPrefs(context).edit().putString(partyId.toString(), encode(s)).apply()
}
