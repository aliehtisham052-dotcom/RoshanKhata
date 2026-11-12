package com.innovation313.roshankhata.ui

import android.app.Activity
import android.content.Context
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.DayMath

/**
 * Hudhud's morning greeting on Home (12 Nov 2026, the owner's rule): once a day, the first time Home comes forward,
 * his bubble says "Assalam-o-Alaikum!" in Pakistan and the Muslim countries (else the time of day's own greeting,
 * HudhudBook.greeting) and one line of what needs the owner today; a tap opens today's summary on his sheet.
 *
 * Proactive help must earn its place (the Clippy lesson): it comes once a day and is put away with one tap, says no
 * amount (balances may be hidden), never speaks, and More → Morning greeting turns it off on this phone.
 */
object HudhudGreet {

    private const val PREFS = "hudhud"
    private const val KEY_ON = "greet_on"
    private const val KEY_DAY = "greet_day"

    /** On unless the owner turned it off on this phone. */
    fun on(context: Context): Boolean = prefs(context).getBoolean(KEY_ON, true)

    fun set(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ON, on).apply()
    }

    /** Not yet greeted today (the calendar day), and on. */
    fun due(context: Context, now: Long = System.currentTimeMillis()): Boolean =
        on(context) && prefs(context).getLong(KEY_DAY, Long.MIN_VALUE) != DayMath.dayNumber(now)

    /** Greeted today: not again until tomorrow, whether it was read, tapped or put away. */
    fun greeted(context: Context, now: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(KEY_DAY, DayMath.dayNumber(now)).apply()
    }

    /** Due again today (the screen proofs). */
    fun forget(context: Context) {
        prefs(context).edit().remove(KEY_DAY).apply()
    }

    /** More → Morning greeting: what it is, and the one switch (the Payment Radar's sheet). */
    fun settings(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val isOn = on(activity)
        SettingSheet.show(
            activity,
            icon = R.drawable.ic_menu_greeting,
            tile = R.color.tile_bills_bg,
            title = activity.getString(R.string.hudhud_greet_title),
            on = isOn,
            summary = activity.getString(R.string.hudhud_greet_help),
            more = null,
            actionLabel = activity.getString(if (isOn) R.string.radar_turn_off else R.string.radar_turn_on),
            danger = isOn
        ) { set(activity, !isOn) }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
