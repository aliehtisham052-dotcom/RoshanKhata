package com.innovation313.roshankhata.ui

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Button
import com.innovation313.roshankhata.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * The "when did this happen" control, shared by every screen that records
 * something.
 *
 * A ledger stamps an entry with the moment it was typed, which is rarely the
 * moment it happened — the day's cash gets totalled at closing time, a cheque
 * is written up the morning after it was handed over. Every one of those
 * screens needs the same control, and five copies of the same picker is five
 * places for the rules to drift apart.
 */
object DateTimeField {

    private val format = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    /**
     * Wire [button] to pick a date and then a time, starting from [initial].
     *
     * [onPicked] receives each new choice; the caller keeps the value and uses
     * it when saving. Nothing is stored here.
     */
    fun attach(
        activity: Activity,
        button: Button,
        initial: Long,
        onPicked: (Long) -> Unit
    ) {
        var chosen = initial

        fun render() {
            button.text = activity.getString(R.string.entry_date, format.format(Date(chosen)))
        }
        render()

        button.setOnClickListener {
            val cal = Calendar.getInstance().apply { timeInMillis = chosen }
            DatePickerDialog(
                activity,
                { _, year, month, day ->
                    cal.set(Calendar.YEAR, year)
                    cal.set(Calendar.MONTH, month)
                    cal.set(Calendar.DAY_OF_MONTH, day)
                    // Straight on to the time, so one gesture sets both rather
                    // than leaving the hour at whatever it happened to be.
                    TimePickerDialog(
                        activity,
                        { _, hour, minute ->
                            cal.set(Calendar.HOUR_OF_DAY, hour)
                            cal.set(Calendar.MINUTE, minute)
                            chosen = cal.timeInMillis
                            render()
                            onPicked(chosen)
                        },
                        cal.get(Calendar.HOUR_OF_DAY),
                        cal.get(Calendar.MINUTE),
                        false
                    ).show()
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).apply {
                // A ledger records what has happened. A date that has not
                // arrived yet is a mistake every time, so it is not offered.
                datePicker.maxDate = System.currentTimeMillis()
            }.show()
        }
    }
}
