package com.innovation313.roshankhata.data

import android.content.Context

// The shop's calendar and currency choices are kept in its Profile
// (SharedPreferences), so reading and saving them stays on the phone side;
// ShopCalendar and Currency themselves are shared.

/** Re-read the active shop's choice (same places Currency is refreshed). */
fun ShopCalendar.refresh(context: Context) {
    current = BusinessProfile.calendar(context)
}

fun ShopCalendar.set(context: Context, kind: ShopCalendar.Kind) {
    BusinessProfile.setCalendar(context, kind)
    refresh(context)
}

/** Re-read the active shop's choice. Cheap: SharedPreferences is in memory after first load. */
fun Currency.refresh(context: Context) {
    symbol = BusinessProfile.currency(context)
}

/** Save the shop's choice and print with it from now on. */
fun Currency.set(context: Context, value: String?) {
    BusinessProfile.setCurrency(context, value)
    refresh(context)
}
