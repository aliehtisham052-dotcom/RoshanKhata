package com.innovation313.roshankhata.ui

import com.innovation313.roshankhata.data.Digits

import android.content.Context
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.SeasonBook

/** Words for a crop season, in the app's language: "Rabi 2026-27", "Kharif 2026". */
object SeasonText {

    fun name(context: Context, s: SeasonBook.Season): String =
        if (s.crop == SeasonBook.Crop.RABI) Digits.string(context.resources, R.string.season_rabi, s.year, (s.year + 1) % 100)
        else Digits.string(context.resources, R.string.season_kharif, s.year)

    fun harvest(context: Context, s: SeasonBook.Season): String =
        if (s.crop == SeasonBook.Crop.RABI) Digits.string(context.resources, R.string.season_harvest_rabi, s.year + 1)
        else Digits.string(context.resources, R.string.season_harvest_kharif, s.year)

    /** One customer's last few seasons, newest first: "Rabi 2025-26: cleared 12 days after harvest · …". */
    fun record(context: Context, rows: List<SeasonBook.PartySeason>, max: Int = 3): String =
        rows.sortedByDescending { it.season }.take(max).joinToString("  ·  ") { r ->
            val n = name(context, r.season)
            val at = r.clearedAt
            when {
                at == null -> context.getString(R.string.season_rec_due, n, Format.money(r.outstanding))
                else -> {
                    val d = SeasonBook.daysBetween(SeasonBook.harvestEnd(r.season), at)
                    if (d > 0) Digits.quantity(context.resources, R.plurals.season_rec_after, d, n, d)
                    else context.getString(R.string.season_rec_before, n)
                }
            }
        }
}
