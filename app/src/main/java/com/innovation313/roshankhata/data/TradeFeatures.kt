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

    /**
     * Crop seasons, the season row on a sale, the harvest promise. An agri
     * shop's, where the country farms by Rabi and Kharif (Pakistan, India;
     * 21 Oct 2026): an Indonesian toko tani has no "Rabi 2026-27".
     */
    fun seasons(context: Context): Boolean = Trade.current(context) == Trade.AGRI && ShopCountry.current.seasons

    /** "Write spray advice" on a sold line. */
    fun sprayAdvice(context: Context): Boolean = Trade.current(context) == Trade.AGRI

    /**
     * The inspector's label details — a product's technical name, formulation and registration, and the
     * registers' compliance columns, checklist and signatures — for the trades an inspector visits. The
     * stock register and the sales & purchase register themselves are every shop's (31 Oct 2026; RegisterStyle).
     */
    fun inspectorReports(context: Context): Boolean =
        when (Trade.current(context)) { Trade.AGRI, Trade.MEDICAL -> true; else -> false }
}
