package com.innovation313.roshankhata.ui

import android.app.Activity
import android.app.DatePickerDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * "Show me a particular stretch of days" — the same control on the customer
 * list and inside a customer's ledger.
 *
 * The named ranges come first because they are what actually gets asked for:
 * today's takings at closing, this month's dealings when a supplier calls.
 * The custom pair is there for the times a shopkeeper needs a season or a
 * disputed fortnight, which no fixed list can guess.
 */
object DateRangeFilter {

    /**
     * An inclusive window. [from] and [to] are millisecond timestamps;
     * [ALL] means no filtering at all.
     */
    data class Range(val from: Long, val to: Long, val labelRes: Int) {
        fun contains(timestamp: Long): Boolean =
            this == ALL || (timestamp in from..to)

        companion object {
            val ALL = Range(0L, Long.MAX_VALUE, R.string.range_all)
        }
    }

    /** Ask which stretch of days to show, then hand it back. */
    fun choose(activity: Activity, current: Range, onPicked: (Range) -> Unit) {
        val options = arrayOf(
            activity.getString(R.string.range_all),
            activity.getString(R.string.range_today),
            activity.getString(R.string.range_yesterday),
            activity.getString(R.string.range_this_week),
            activity.getString(R.string.range_this_month),
            activity.getString(R.string.range_custom)
        )

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.filter_by_date)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> onPicked(Range.ALL)
                    1 -> onPicked(today())
                    2 -> onPicked(yesterday())
                    3 -> onPicked(thisWeek())
                    4 -> onPicked(thisMonth())
                    5 -> pickCustom(activity, current, onPicked)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** A label for the button, e.g. "Today" or "1 Jul – 20 Jul". */
    fun label(activity: Activity, range: Range): String =
        if (range.labelRes != R.string.range_custom) {
            activity.getString(range.labelRes)
        } else {
            val fmt = SimpleDateFormat("d MMM", Locale.getDefault())
            "${fmt.format(Date(range.from))} – ${fmt.format(Date(range.to))}"
        }

    private fun pickCustom(activity: Activity, current: Range, onPicked: (Range) -> Unit) {
        val start = Calendar.getInstance().apply {
            timeInMillis = if (current == Range.ALL) System.currentTimeMillis() else current.from
        }

        DatePickerDialog(
            activity,
            { _, y, m, d ->
                val from = dayStart(y, m, d)

                // Straight on to the second date, seeded at the first, so the
                // pair is picked in one gesture and the second cannot land
                // before the first by accident.
                val endCal = Calendar.getInstance().apply { timeInMillis = from }
                DatePickerDialog(
                    activity,
                    { _, y2, m2, d2 ->
                        val to = dayEnd(y2, m2, d2)
                        // A backwards pair is a slip, not an instruction: swap
                        // rather than showing an empty list.
                        val range = if (to >= from) {
                            Range(from, to, R.string.range_custom)
                        } else {
                            Range(to, dayEnd(y, m, d), R.string.range_custom)
                        }
                        onPicked(range)
                    },
                    endCal.get(Calendar.YEAR),
                    endCal.get(Calendar.MONTH),
                    endCal.get(Calendar.DAY_OF_MONTH)
                ).apply {
                    datePicker.minDate = from
                    datePicker.maxDate = System.currentTimeMillis()
                }.show()
            },
            start.get(Calendar.YEAR),
            start.get(Calendar.MONTH),
            start.get(Calendar.DAY_OF_MONTH)
        ).apply {
            // A ledger holds what has happened; there is nothing to show past
            // today.
            datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }

    private fun today(): Range {
        val c = Calendar.getInstance()
        return Range(
            dayStart(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)),
            dayEnd(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)),
            R.string.range_today
        )
    }

    private fun yesterday(): Range {
        val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }
        return Range(
            dayStart(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)),
            dayEnd(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)),
            R.string.range_yesterday
        )
    }

    /** From the first day of this week to now, per the phone's own calendar. */
    private fun thisWeek(): Range {
        val c = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
        }
        return Range(
            dayStart(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)),
            System.currentTimeMillis(),
            R.string.range_this_week
        )
    }

    private fun thisMonth(): Range {
        val c = Calendar.getInstance()
        return Range(
            dayStart(c.get(Calendar.YEAR), c.get(Calendar.MONTH), 1),
            System.currentTimeMillis(),
            R.string.range_this_month
        )
    }

    private fun dayStart(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply {
            set(year, month, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /** The last millisecond of the day, so the chosen date is included whole. */
    private fun dayEnd(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply {
            set(year, month, day, 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
}
