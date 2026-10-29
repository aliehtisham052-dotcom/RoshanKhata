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

/**
 * When each customer was last reminded from this phone (the ladder's last time), for Follow-up's
 * "reminded, nothing received since" ([PaymentRequest], 29 Oct 2026). The computer's ReminderStore.lastReminded.
 */
fun ReminderLadder.lastReminded(context: Context): Map<Long, Long> =
    ladderPrefs(context).all.mapNotNull { (k, v) ->
        val id = k.toLongOrNull() ?: return@mapNotNull null
        val s = decode(v as? String) ?: return@mapNotNull null
        id to s.lastAt
    }.toMap()
