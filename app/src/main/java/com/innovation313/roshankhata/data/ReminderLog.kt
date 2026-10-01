package com.innovation313.roshankhata.data

import android.content.Context
import java.util.TimeZone

/**
 * Which customers the owner already opened a WhatsApp reminder for TODAY, so
 * the Follow-up list can move them down and not have the same person
 * reminded twice in a day (1 Oct).
 *
 * Honest about what it knows: the app hands the text to WhatsApp and cannot
 * see whether Send was pressed there, so this records "reminder opened", and
 * the row says exactly that. Kept per business (party ids repeat across
 * shops), on this phone only; yesterday's marks are dropped on every write.
 */
object ReminderLog {

    private const val PREFS = "reminder_log"
    private const val DAY = 24L * 60 * 60 * 1000

    fun dayOf(millis: Long, tz: TimeZone = TimeZone.getDefault()): Long =
        (millis + tz.getOffset(millis)).floorDiv(DAY)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS + Businesses.suffix(context), Context.MODE_PRIVATE)

    fun record(context: Context, partyId: Long, now: Long = System.currentTimeMillis()) {
        val today = dayOf(now)
        val p = prefs(context)
        val edit = p.edit()
        p.all.forEach { (k, v) -> if ((v as? Long) != today) edit.remove(k) }
        edit.putLong(partyId.toString(), today).apply()
    }

    fun openedToday(context: Context, now: Long = System.currentTimeMillis()): Set<Long> {
        val today = dayOf(now)
        return prefs(context).all
            .filter { (_, v) -> (v as? Long) == today }
            .keys.mapNotNull { it.toLongOrNull() }.toSet()
    }
}
