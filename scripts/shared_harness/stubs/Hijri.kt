package com.innovation313.roshankhata.data
import java.util.TimeZone
// Stand-in (harness only): the desktop's java.time Hijri; good enough for a reading test.
internal fun hijriUmmAlQura(millis: Long, zone: TimeZone): Triple<Int, Int, Int> {
    val d = java.time.chrono.HijrahDate.from(java.time.Instant.ofEpochMilli(millis).atZone(zone.toZoneId()).toLocalDate())
    return Triple(d.get(java.time.temporal.ChronoField.YEAR), d.get(java.time.temporal.ChronoField.MONTH_OF_YEAR), d.get(java.time.temporal.ChronoField.DAY_OF_MONTH))
}
