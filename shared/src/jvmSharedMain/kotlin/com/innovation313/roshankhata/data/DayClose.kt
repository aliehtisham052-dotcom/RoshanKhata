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
        DayMath.dayNumber(millis, tz)

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

    /**
     * What should be in the drawer at [now], by the books — the one rule for
     * Home's cash card, the Cashbook's "Should be in drawer" and Close the day
     * on both platforms (25 Oct 2026, the audit; shared 29 Oct 2026 — the
     * computer had it as a function and the phone's day-close dialog wrote the
     * same four queries and arithmetic inline).
     *
     * Since the last count ([KhataDao.lastDayClose], or the beginning of the
     * book): cashbook money in and out that moved in cash (an unmarked row is
     * cash, a bank, online, wallet or cheque row is not), plus khata receipts
     * and payments marked Cash — unmarked khata entries are not guessed. The
     * stretch is `closedAt < t <= now`, the DAO's own bounds.
     */
    suspend fun drawerByTheBooks(dao: KhataDao, now: Long = System.currentTimeMillis()): DrawerFigures {
        val last = dao.lastDayClose()
        val from = last?.closedAt ?: 0L
        return DrawerFigures(
            now = now,
            last = last,
            cbIn = dao.cashbookCashBetween(true, from, now),
            cbOut = dao.cashbookCashBetween(false, from, now),
            kIn = dao.khataCashBetween(false, from, now),
            kOut = dao.khataCashBetween(true, from, now)
        )
    }
}

/**
 * The drawer's figures since the last count ([DayCloseMath.drawerByTheBooks]):
 * the four sums the day-close dialog prints in its breakdown, the two totals
 * Close the day writes, and what should be in the drawer.
 */
data class DrawerFigures(
    /** The moment the figures run to. */
    val now: Long,
    /** The last count, or null when the drawer was never counted. */
    val last: DayClose?,
    /** Cashbook income in cash since the count. */
    val cbIn: Double,
    /** Cashbook expenses in cash since the count. */
    val cbOut: Double,
    /** Khata "I got" marked Cash since the count. */
    val kIn: Double,
    /** Khata "I gave" marked Cash since the count. */
    val kOut: Double
) {
    val cashIn: Double get() = cbIn + kIn
    val cashOut: Double get() = cbOut + kOut

    /** What should be in the drawer, opening on the last count (nothing when never counted). */
    val expected: Double get() = expectedFrom(last?.counted ?: 0.0)

    /** The same figure from an opening the owner typed over the last count. */
    fun expectedFrom(opening: Double): Double = DayCloseMath.expected(opening, cashIn, cashOut)
}
