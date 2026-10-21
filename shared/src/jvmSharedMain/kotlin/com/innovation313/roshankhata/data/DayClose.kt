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
 * One row per calendar day; closing again the same day replaces it.
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
