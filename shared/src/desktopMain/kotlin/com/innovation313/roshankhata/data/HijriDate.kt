package com.innovation313.roshankhata.data

import java.time.Instant
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.util.TimeZone

/**
 * The computer's Umm al-Qura tables: java.time's Hijrah chronology is the
 * Umm al-Qura calendar ("Hijrah-umalqura"), the same tables android.icu uses
 * on the phone. It covers 1300-1600 AH (1882-2174 CE).
 */
internal actual fun hijriUmmAlQura(millis: Long, zone: TimeZone): Triple<Int, Int, Int> {
    val day = Instant.ofEpochMilli(millis).atZone(zone.toZoneId()).toLocalDate()
    val h = HijrahDate.from(day)
    return Triple(h.get(ChronoField.YEAR), h.get(ChronoField.MONTH_OF_YEAR), h.get(ChronoField.DAY_OF_MONTH))
}
