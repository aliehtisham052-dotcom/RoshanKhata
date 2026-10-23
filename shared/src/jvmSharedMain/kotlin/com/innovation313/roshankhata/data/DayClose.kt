package com.innovation313.roshankhata.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Galla milan (2 Oct, v26): the owner closes the day by counting the cash
 * drawer, and the app sets that beside what the books say should be there.
 *
 * Kept as the figures of that evening, not recomputed later: an entry edited
 * next week must not silently rewrite what the drawer was found to hold.
 * One row per working day ([DayCloseMath.businessDayOf]: the small hours
 * count to the day before); closing again the same day replaces it.
 */
@Entity(tableName = "day_close", indices = [Index(value = ["day"], unique = true), Index("uid")])
data class DayClose(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Local calendar day number (days since 1970 in the phone's zone). */
    val day: Long,
    /** Cash in the drawer when this stretch began (last count, or typed). */
    val opening: Double,
    /** Cash in: cashbook income + khata receipts marked Cash. */
    val cashIn: Double,
    /** Cash out: cashbook expenses + khata payments out marked Cash. */
    val cashOut: Double,
    /** What the owner counted. */
    val counted: Double,
    val note: String? = null,
    val closedAt: Long = System.currentTimeMillis(),
    /** This row's identity across devices and backups (schema 38, live sync). */
    val uid: String? = Uid.next()
) {
    val expected: Double get() = DayCloseMath.expected(opening, cashIn, cashOut)
    val difference: Double get() = DayCloseMath.difference(counted, expected)
}

object DayCloseMath {
    /** The local calendar day of a moment, as [DayClose.day] keys it (the phone's ReminderLog.dayOf). */
    fun dayOf(millis: Long, tz: java.util.TimeZone = java.util.TimeZone.getDefault()): Long =
        Math.floorDiv(millis + tz.getOffset(millis), 24L * 60 * 60 * 1000)

    /**
     * The hour a shop's working day ends by (23 Oct 2026, the review): a drawer
     * counted before 04:00 is the evening before's — a shop that closes
     * Friday's till at 00:20 on Saturday files it under Friday, and Saturday's
     * own close that night no longer overwrites it.
     */
    const val DAY_ENDS_HOUR = 4

    /** The working day a close made at [millis] belongs to: [dayOf], with the small hours counted to the day before. */
    fun businessDayOf(millis: Long, tz: java.util.TimeZone = java.util.TimeZone.getDefault(), endHour: Int = DAY_ENDS_HOUR): Long =
        dayOf(millis - endHour * 3_600_000L, tz)

    /** The first millisecond of a local day number. */
    fun startOf(day: Long, tz: java.util.TimeZone = java.util.TimeZone.getDefault()): Long {
        val utc = day * 24L * 60 * 60 * 1000
        return utc - tz.getOffset(utc)
    }

    fun expected(opening: Double, cashIn: Double, cashOut: Double): Double =
        LineMath.round(opening + cashIn - cashOut)

    /** Positive = more in the drawer than the books say; negative = short. */
    fun difference(counted: Double, expected: Double): Double = LineMath.round(counted - expected)
}
