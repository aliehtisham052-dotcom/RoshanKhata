package com.innovation313.roshankhata.data

import android.content.Context

/**
 * Which trade-only features this shop shows (4 Oct 2026, worldwide phase 3).
 *
 * Three features speak one trade and would only puzzle another: crop
 * seasons (Rabi/Kharif) with the "after the harvest" promise, spray advice
 * on a sold line, and the inspector's stock/register reports. They read the
 * shop's [Trade] here, in one place, so a kiryana or a mobile shop never
 * sees them and an agri shop sees exactly what it did before.
 *
 * Only SCREENS consult this. Data is untouched: a season or an advice line
 * already written stays in the book and in every backup, and comes back
 * the moment the trade is set back. The core — udhar, payments, invoices,
 * bills with batch and expiry — shows for every trade.
 */
object TradeFeatures {

    /** Crop seasons, the season row on a sale, the harvest promise. */
    fun seasons(context: Context): Boolean = Trade.current(context) == Trade.AGRI

    /** "Write spray advice" on a sold line. */
    fun sprayAdvice(context: Context): Boolean = Trade.current(context) == Trade.AGRI

    /** Inspector stock report and the in/out register — the trades an inspector visits. */
    fun inspectorReports(context: Context): Boolean =
        when (Trade.current(context)) { Trade.AGRI, Trade.MEDICAL -> true; else -> false }
}
