package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words
import java.util.Date
import java.util.TimeZone

/**
 * "When was it", said shortly — the one rule on both platforms (29 Oct 2026,
 * the cleanup: the computer's `whenWords` and the phone's report headings each
 * tested today and yesterday on their own with the same two strings).
 *
 * Compose-free: the words come in as [Words], the clock as [now], so a test
 * can hold it to a fixed day. Days are the zone's civil days ([DayMath]).
 */
object WhenWords {

    /**
     * Today or Yesterday for a moment on one of those two days, else null — the
     * caller writes the date in its own shape (the phone's report heading says
     * "Today · 12 Oct 2026", the computer's rows a weekday or "12 Oct").
     */
    fun relativeDay(at: Long, now: Long, words: Words, tz: TimeZone = TimeZone.getDefault()): String? {
        val day = DayMath.dayNumber(at, tz)
        val today = DayMath.dayNumber(now, tz)
        return when (day) {
            today -> words.text(W.range_today)
            today - 1 -> words.text(W.range_yesterday)
            else -> null
        }
    }

    /**
     * The computer's day word (Home's dealings, the backup chip, the counter's
     * tickets): Today, Yesterday, the weekday within the last week ("Thu"),
     * else the day and month ("12 Oct") — in the app's language through
     * [DateWords].
     */
    fun dayWord(at: Long, now: Long, words: Words, tz: TimeZone = TimeZone.getDefault()): String {
        relativeDay(at, now, words, tz)?.let { return it }
        val day = DayMath.dayNumber(at, tz)
        val today = DayMath.dayNumber(now, tz)
        return if (day > today - 7) DateWords.formatter("EEE").format(Date(at))
        else DateWords.formatter("d MMM").format(Date(at))
    }

    /** [dayWord] with the time of day: "Today 9:40 AM", "Yesterday 5:15 PM", "Thu 4:10 PM", "12 Oct 6:20 PM". */
    fun whenWords(at: Long, now: Long, words: Words, tz: TimeZone = TimeZone.getDefault()): String =
        dayWord(at, now, words, tz) + " " + DateWords.formatter("h:mm a").format(Date(at))
}
