package com.innovation313.roshankhata.ui

import android.app.Activity
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.Digits
import com.innovation313.roshankhata.data.SeasonBook
import com.innovation313.roshankhata.data.TradeFeatures

/**
 * "When will he pay?" on the phone: a week, two weeks or a month from today,
 * the next two harvest ends where the shop keeps seasons, any other day on
 * the calendar, or — when one is set — taking it back. [onPick] gets the day
 * (its last minute) or null for "remove". A day only: no late fee or markup
 * goes with it.
 *
 * Moved here from PartyDetailActivity (24 Oct 2026) so the counter's sale on
 * account asks the same question with the same choices as a khata entry.
 */
object DuePicker {

    fun choose(activity: Activity, partyName: String, current: Long?, onPick: (Long?) -> Unit) {
        val now = System.currentTimeMillis()
        fun endOfDayIn(days: Int) = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_YEAR, days)
            set(java.util.Calendar.HOUR_OF_DAY, 23); set(java.util.Calendar.MINUTE, 59)
            set(java.util.Calendar.SECOND, 59); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val quick = listOf(7, 15, 30).map { d ->
            activity.getString(R.string.entry_due_in_days, String.format(Digits.latinIn(), "%d", d), Format.dateOnly(endOfDayIn(d))) to endOfDayIn(d)
        }
        val y = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val harvests = if (!TradeFeatures.seasons(activity)) emptyList() else listOf(
            SeasonBook.Season(SeasonBook.Crop.RABI, y - 1), SeasonBook.Season(SeasonBook.Crop.KHARIF, y),
            SeasonBook.Season(SeasonBook.Crop.RABI, y), SeasonBook.Season(SeasonBook.Crop.KHARIF, y + 1)
        ).map { it to SeasonBook.harvestEnd(it) }.filter { it.second > now }.sortedBy { it.second }.take(2)
            .map { (s, at) -> activity.getString(R.string.harvest_promise_option, SeasonText.name(activity, s), Format.dateOnly(at)) to at }
        val fixed = quick + harvests

        val choices = fixed.mapIndexed { i, (label, _) ->
            if (i < quick.size) ChoiceSheet.date(label)
            else ChoiceSheet.Option(label, icon = R.drawable.ic_tile_season, iconTint = 0, tile = R.color.tile_plans_bg)
        } + ChoiceSheet.date(activity.getString(R.string.harvest_promise_other_date)) +
            listOfNotNull(current?.let { ChoiceSheet.delete(activity.getString(R.string.harvest_promise_remove)) })

        ChoiceSheet.show(activity, activity.getString(R.string.harvest_promise_title, partyName), choices) { i ->
            when {
                i < fixed.size -> onPick(fixed[i].second)
                i == fixed.size -> {
                    val c = java.util.Calendar.getInstance()
                    BrandDatePickerDialog(activity, { _, yy, mm, dd ->
                        val at = java.util.Calendar.getInstance().apply {
                            clear(); set(yy, mm, dd, 23, 59, 59)
                        }.timeInMillis
                        onPick(at)
                    }, c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH))
                        .apply { datePicker.minDate = now }
                        .show()
                }
                else -> onPick(null)
            }
        }
    }
}
