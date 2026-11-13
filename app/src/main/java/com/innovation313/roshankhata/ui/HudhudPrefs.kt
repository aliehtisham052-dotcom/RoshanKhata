package com.innovation313.roshankhata.ui

import android.content.Context
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.DayMath

/**
 * What the owner chose for Hudhud on this phone (13 Nov 2026), in the sheet's own preferences ("hudhud"): how he
 * speaks, which of the phone's voices, what he calls the owner, and whether the owner said no to the microphone.
 * Kept on the phone only — never in the book, a backup or live sync: a name and a voice are this phone's matter.
 */
object HudhudPrefs {

    private const val PREFS = "hudhud"
    private const val KEY_STYLE = "voice_style"
    private const val KEY_VOICE_NAME = "voice_name_"
    private const val KEY_CALL_ME = "call_me"
    private const val KEY_MIC_DENIED = "mic_denied"
    private const val KEY_HELLO_DAY = "hello_day"
    private const val KEY_NAME_WAY = "name_way_"

    /** The longest name he is given to say. */
    const val CALL_ME_MAX = 30

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** What he calls the owner, as typed ("Ali bhai"); null when nothing was given. */
    fun callMe(context: Context): String? =
        prefs(context).getString(KEY_CALL_ME, null)?.trim()?.takeIf { it.isNotEmpty() }

    fun setCallMe(context: Context, name: String?) {
        val kept = name?.trim()?.take(CALL_ME_MAX)?.takeIf { it.isNotEmpty() }
        prefs(context).edit().apply { if (kept == null) remove(KEY_CALL_ME) else putString(KEY_CALL_ME, kept) }.apply()
    }

    /** The phone's voice chosen for [language] (the voice's own name), or null for the best one there is. */
    fun voiceName(context: Context, language: String): String? =
        prefs(context).getString(KEY_VOICE_NAME + language, null)

    fun setVoiceName(context: Context, language: String, name: String?) {
        prefs(context).edit().apply {
            if (name == null) remove(KEY_VOICE_NAME + language) else putString(KEY_VOICE_NAME + language, name)
        }.apply()
    }

    fun style(context: Context): HudhudStyle =
        prefs(context).getString(KEY_STYLE, null)?.let { s -> HudhudStyle.values().firstOrNull { it.name == s } }
            ?: HudhudStyle.PROFESSIONAL

    fun setStyle(context: Context, style: HudhudStyle) {
        prefs(context).edit().putString(KEY_STYLE, style.name).apply()
    }

    /**
     * How he says his own name in [language]'s voice (13 Nov 2026, the owner: "apna naam bhi sahi tarah nahi bolta"):
     * which of [com.innovation313.roshankhata.data.HudhudSay.nameWays] the owner chose; null for the first.
     */
    fun nameWay(context: Context, language: String): Int? =
        prefs(context).getInt(KEY_NAME_WAY + language, -1).takeIf { it >= 0 }

    fun setNameWay(context: Context, language: String, way: Int) {
        prefs(context).edit().putInt(KEY_NAME_WAY + language, way).apply()
    }

    /** The owner said no to the microphone: Hudhud uses the phone's voice-search box and does not ask again. */
    fun micDenied(context: Context): Boolean = prefs(context).getBoolean(KEY_MIC_DENIED, false)

    fun setMicDenied(context: Context, denied: Boolean) {
        prefs(context).edit().putBoolean(KEY_MIC_DENIED, denied).apply()
    }

    /** His greeting is said aloud once a day, the first time his sheet opens. */
    fun helloDue(context: Context, now: Long = System.currentTimeMillis()): Boolean =
        prefs(context).getLong(KEY_HELLO_DAY, Long.MIN_VALUE) != DayMath.dayNumber(now)

    fun helloSaid(context: Context, now: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(KEY_HELLO_DAY, DayMath.dayNumber(now)).apply()
    }
}

/**
 * How Hudhud speaks (13 Nov 2026, the owner: "Naram, Josh wala, Sanjeeda", and later the same day "professional
 * sound"): the phone's own voice at a pace and a pitch. Professional is the phone's voice as it is, and the one he
 * starts with; gentle a little slower; lively a little quicker and brighter; serious slower and deeper. Kept by name
 * (an enum's name survives the shrinker).
 */
enum class HudhudStyle(val rate: Float, val pitch: Float, val label: Int) {
    PROFESSIONAL(1.0f, 1.0f, R.string.hudhud_style_professional),
    GENTLE(0.95f, 1.0f, R.string.hudhud_style_gentle),
    LIVELY(1.08f, 1.15f, R.string.hudhud_style_lively),
    SERIOUS(0.9f, 0.86f, R.string.hudhud_style_serious)
}
