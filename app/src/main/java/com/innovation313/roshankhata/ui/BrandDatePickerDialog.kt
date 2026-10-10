package com.innovation313.roshankhata.ui

import android.app.DatePickerDialog
import android.content.Context
import android.content.DialogInterface
import android.view.View
import com.innovation313.roshankhata.R
import java.util.Calendar

/**
 * The app's date picker (10 Oct): the platform calendar in the brand green
 * (ThemeOverlay.RoshanKhata.Picker, set app-wide), with a "Today" button.
 *
 * Today moves the calendar to today without closing it - the owner still
 * sees the day and confirms with OK - since most dates written in a shop
 * are today's, and finding today after scrolling back months took several
 * taps. It is hidden when today lies outside the range the caller allows
 * (minDate / maxDate set after construction, as before), so it can never
 * offer a date the screen would refuse.
 *
 * Same constructor as DatePickerDialog, so a call site changes only its name.
 */
class BrandDatePickerDialog(
    context: Context,
    listener: DatePickerDialog.OnDateSetListener?,
    year: Int,
    month: Int,
    dayOfMonth: Int
) : DatePickerDialog(context, listener, year, month, dayOfMonth) {

    init {
        // The listener is replaced in onStart so the tap does not close the dialog.
        setButton(
            DialogInterface.BUTTON_NEUTRAL,
            context.getString(R.string.range_today),
            null as DialogInterface.OnClickListener?
        )
    }

    override fun onStart() {
        super.onStart()
        val button = getButton(DialogInterface.BUTTON_NEUTRAL) ?: return
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val at = today.timeInMillis
        val picker = datePicker
        // minDate/maxDate default to the far past and far future.
        val allowed = at >= startOfDay(picker.minDate) && at <= endOfDay(picker.maxDate)
        button.visibility = if (allowed) View.VISIBLE else View.GONE
        button.setOnClickListener {
            picker.updateDate(today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH))
        }
    }

    private fun startOfDay(t: Long) = Calendar.getInstance().apply {
        timeInMillis = t
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun endOfDay(t: Long) = Calendar.getInstance().apply {
        timeInMillis = t
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
    }.timeInMillis
}
