package com.innovation313.roshankhata.data

import java.util.TimeZone

/** Year, month (1-12) and day of [millis] in the Umm al-Qura Hijri calendar. */
internal expect fun hijriUmmAlQura(millis: Long, zone: TimeZone): Triple<Int, Int, Int>
