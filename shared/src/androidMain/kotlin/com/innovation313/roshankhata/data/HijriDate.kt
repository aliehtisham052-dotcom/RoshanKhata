package com.innovation313.roshankhata.data

import java.util.TimeZone

/** The phone's Umm al-Qura tables (android.icu, API 24 = this app's minSdk). */
internal actual fun hijriUmmAlQura(millis: Long, zone: TimeZone): Triple<Int, Int, Int> {
    val c = android.icu.util.IslamicCalendar(
        android.icu.util.TimeZone.getTimeZone(zone.id), android.icu.util.ULocale.ENGLISH
    )
    c.calculationType = android.icu.util.IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
    c.timeInMillis = millis
    return Triple(
        c.get(android.icu.util.Calendar.YEAR),
        c.get(android.icu.util.Calendar.MONTH) + 1,
        c.get(android.icu.util.Calendar.DAY_OF_MONTH)
    )
}
