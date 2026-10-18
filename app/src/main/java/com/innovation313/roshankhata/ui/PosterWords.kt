package com.innovation313.roshankhata.ui

import com.innovation313.roshankhata.data.quantity

import android.content.Context
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.Digits
import com.innovation313.roshankhata.data.PosterFields
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

/**
 * The words a poster prints for its fields, in the POSTER's language (the
 * context passed in), which may differ from the app's (12 Oct).
 *
 * Times: Urdu, Roman Urdu and Hindi say them as people do — "صبح 9 تا رات
 * 8 بجے", "subah 9 se raat 8 baje" — with the part of the day before the
 * hour; other languages print "9:00 AM – 8:00 PM" with their own AM/PM
 * words. Figures are always 0–9, as everywhere in the app (see Digits).
 *
 * Days: the week starts on Monday, as a Pakistani shop's does; open days
 * are grouped into runs ("Mon – Sat"), and so are holidays.
 */
object PosterWords {

    /** One line of a panel. [closed] shows the value in red, [good] in green (the day it opens again). */
    data class Row(val label: String, val value: String, val closed: Boolean = false, val good: Boolean = false)

    /** Monday first. */
    val WEEK = listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
        Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY)

    /** The days of [days] as runs of neighbours in a Monday-first week. Pure; unit-tested. */
    fun runs(days: Set<Int>): List<List<Int>> {
        val out = mutableListOf<MutableList<Int>>()
        for (d in WEEK) {
            if (d !in days) continue
            val last = out.lastOrNull()
            if (last != null && WEEK.indexOf(last.last()) == WEEK.indexOf(d) - 1) last += d else out += mutableListOf(d)
        }
        return out
    }

    private enum class Style { URDU, ROMAN, HINDI, CLOCK }

    private fun style(ctx: Context): Style {
        val l = ctx.resources.configuration.locales[0] ?: Locale.getDefault()
        return when {
            l.language == "ur" && l.script == "Latn" -> Style.ROMAN
            l.language == "ur" -> Style.URDU
            l.language == "hi" -> Style.HINDI
            else -> Style.CLOCK
        }
    }

    private fun locale(ctx: Context) = ctx.resources.configuration.locales[0] ?: Locale.getDefault()

    /** Morning, noon, evening, night, for an hour of the day. */
    private fun period(style: Style, minutes: Int): String {
        val h = minutes / 60
        val i = when (h) { in 4..11 -> 0; in 12..15 -> 1; in 16..18 -> 2; else -> 3 }
        return when (style) {
            Style.URDU -> listOf("صبح", "دوپہر", "شام", "رات")[i]
            Style.ROMAN -> listOf("subah", "dopahar", "shaam", "raat")[i]
            Style.HINDI -> listOf("सुबह", "दोपहर", "शाम", "रात")[i]
            Style.CLOCK -> ""
        }
    }

    private fun clock12(minutes: Int, alwaysMinutes: Boolean): String {
        val h = (minutes / 60) % 24
        val m = minutes % 60
        val h12 = if (h % 12 == 0) 12 else h % 12
        return if (m == 0 && !alwaysMinutes) "$h12" else "$h12:${"%02d".format(Locale.ROOT, m)}"
    }

    private fun amPm(ctx: Context, minutes: Int): String {
        val ap = DateFormatSymbols.getInstance(locale(ctx)).amPmStrings
        return ap[if (minutes / 60 % 24 < 12) 0 else 1]
    }

    private fun bajay(style: Style) = when (style) { Style.URDU -> "بجے"; Style.ROMAN -> "baje"; Style.HINDI -> "बजे"; else -> "" }
    private fun taa(style: Style) = when (style) { Style.URDU -> "تا"; Style.ROMAN -> "se"; Style.HINDI -> "से"; else -> "–" }

    /** One time: "صبح 9 بجے", "subah 9 baje", "9:00 AM". */
    fun time(ctx: Context, minutes: Int): String {
        val s = style(ctx)
        if (s == Style.CLOCK) return "${clock12(minutes, true)} ${amPm(ctx, minutes)}"
        return "${period(s, minutes)} ${clock12(minutes, false)} ${bajay(s)}"
    }

    /** From–to: "صبح 9 تا رات 8 بجے" (the second part of the day left out when it is the same), "9:00 AM – 8:00 PM". */
    fun range(ctx: Context, from: Int, to: Int): String {
        val s = style(ctx)
        if (s == Style.CLOCK) return ctx.getString(R.string.poster_time_range, time(ctx, from), time(ctx, to))
        val p1 = period(s, from)
        val p2 = period(s, to)
        val second = if (p1 == p2) clock12(to, false) else "$p2 ${clock12(to, false)}"
        return "$p1 ${clock12(from, false)} ${taa(s)} $second ${bajay(s)}"
    }

    private val ROMAN_DAYS = mapOf(
        Calendar.MONDAY to "Peer", Calendar.TUESDAY to "Mangal", Calendar.WEDNESDAY to "Budh",
        Calendar.THURSDAY to "Jumeraat", Calendar.FRIDAY to "Jumma", Calendar.SATURDAY to "Hafta", Calendar.SUNDAY to "Itwar"
    )

    /**
     * A weekday's name. English and Indonesian use the short form in a range
     * ("Mon – Sat"); the others the whole word. Roman Urdu has its own names:
     * the phone's data for it would fall back to Urdu letters.
     */
    fun day(ctx: Context, calDay: Int, short: Boolean = false): String {
        if (style(ctx) == Style.ROMAN) return ROMAN_DAYS.getValue(calDay)
        val l = locale(ctx)
        val sym = DateFormatSymbols.getInstance(l)
        val shortOk = l.language == "en" || l.language == "in" || l.language == "id"
        return (if (short && shortOk) sym.shortWeekdays[calDay] else sym.weekdays[calDay]).trim()
    }

    private fun runLabel(ctx: Context, run: List<Int>): String =
        if (run.size == 1) day(ctx, run[0])
        else ctx.getString(R.string.poster_day_range, day(ctx, run.first(), true), day(ctx, run.last(), true))

    /** The timings as rows: the open days, the Friday prayer break, the holidays. */
    fun hoursRows(ctx: Context, f: PosterFields): List<Row> {
        val open = WEEK.toSet() - f.holidays
        val rows = mutableListOf<Row>()
        val hours = range(ctx, f.opens, f.closes)
        if (open.size == 7) rows += Row(ctx.getString(R.string.poster_every_day), hours)
        else runs(open).forEach { rows += Row(runLabel(ctx, it), hours) }
        if (f.prayerBreak && Calendar.FRIDAY in open) rows += Row(ctx.getString(R.string.poster_friday_break), range(ctx, f.breakFrom, f.breakTo))
        runs(f.holidays).forEach { rows += Row(runLabel(ctx, it), ctx.getString(R.string.poster_day_closed), closed = true) }
        return rows
    }

    /** The words for a [PosterFields.Reason] key, in this language. */
    fun reason(ctx: Context, key: String): String? = when (key) {
        PosterFields.Reason.FRIDAY.key -> ctx.getString(R.string.poster_reason_friday)
        PosterFields.Reason.EID.key -> ctx.getString(R.string.poster_reason_eid)
        PosterFields.Reason.WEDDING.key -> ctx.getString(R.string.poster_reason_wedding)
        PosterFields.Reason.BEREAVEMENT.key -> ctx.getString(R.string.poster_reason_bereavement)
        PosterFields.Reason.STRIKE.key -> ctx.getString(R.string.poster_reason_strike)
        PosterFields.Reason.HOLIDAY.key -> ctx.getString(R.string.poster_reason_holiday)
        else -> null
    }

    /** The day it opens again, in words. */
    fun reopenDay(ctx: Context, key: String): String? {
        val r = PosterFields.Reopen.of(key) ?: return null
        return when (r) {
            PosterFields.Reopen.TOMORROW -> ctx.getString(R.string.poster_tomorrow)
            PosterFields.Reopen.DAY_AFTER -> ctx.getString(R.string.poster_day_after)
            else -> day(ctx, r.weekday)
        }
    }

    /** Closed: why, and when it opens again. Either may be missing. */
    fun closedRows(ctx: Context, f: PosterFields): List<Row> {
        val rows = mutableListOf<Row>()
        val why = if (f.reason == PosterFields.CUSTOM_REASON) f.reasonText.trim().takeIf { it.isNotEmpty() } else reason(ctx, f.reason)
        if (why != null) rows += Row(ctx.getString(R.string.poster_reason_label), why)
        reopenDay(ctx, f.reopen)?.let { d ->
            rows += Row(ctx.getString(R.string.poster_opens_again), ctx.getString(R.string.poster_day_at_time, d, time(ctx, f.reopenAt)), good = true)
        }
        return rows
    }

    /** Ramazan: the day opening and the night one. */
    fun ramazanRows(ctx: Context, f: PosterFields): List<Row> {
        if (!f.ramazanHours) return emptyList()
        val night = if (f.afterTaraweeh) ctx.getString(R.string.poster_after_taraweeh_until, time(ctx, f.nightTo))
            else range(ctx, f.nightFrom, f.nightTo)
        return listOf(
            Row(ctx.getString(R.string.poster_day_label), range(ctx, f.dayFrom, f.dayTo)),
            Row(ctx.getString(R.string.poster_night_label), night)
        )
    }

    /** "Eid holidays: the shop is closed for 3 days", or null. */
    fun eidNote(ctx: Context, f: PosterFields): String? =
        if (f.eidDays <= 0) null else Digits.quantity(ctx.resources, R.plurals.poster_eid_holidays, f.eidDays, f.eidDays)
}
