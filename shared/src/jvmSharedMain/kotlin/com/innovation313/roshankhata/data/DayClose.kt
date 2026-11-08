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
        // The calendar's own midnight, not "UTC midnight minus the offset at UTC midnight": Cairo, Beirut and Gaza
        // change their clocks AT midnight, where that sum is an hour out (8 Nov review m39). A moment inside the day
        // (its noon) is found first and the calendar then gives that day's first moment, a midnight that does not
        // exist being the first moment that day does. (java.time needs Android 26; the app starts at 24.)
        val noon = day * 24L * 60 * 60 * 1000 + 12L * 60 * 60 * 1000
        return DayMath.startOfDay(noon - tz.getOffset(noon), tz)
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
     * Since the last count ([KhataDao.lastDayClose]), or — when the drawer was
     * never counted — since the start of today's working day ([firstCountFrom]):
     * cashbook money in and out that moved in cash (an unmarked row is cash, a
     * bank, online, wallet or cheque row is not), plus khata receipts and
     * payments marked Cash — unmarked khata entries are not guessed. The
     * stretch is `from < t <= now`, the DAO's own bounds.
     */
    suspend fun drawerByTheBooks(dao: KhataDao, now: Long = System.currentTimeMillis()): DrawerFigures {
        val last = dao.lastDayClose(now)
        val from = last?.closedAt ?: firstCountFrom(now)
        return DrawerFigures(
            now = now,
            last = last,
            cbIn = dao.cashbookCashBetween(true, from, now),
            cbOut = dao.cashbookCashBetween(false, from, now),
            kIn = dao.khataCashBetween(false, from, now),
            kOut = dao.khataCashBetween(true, from, now),
            from = from,
            otherOut = dao.billsAndStaffCashBetween(from, now)
        )
    }

    /**
     * Where a drawer never counted starts (2 Nov 2026, the owner's first Close the day): the start of today's
     * working day ([businessDayOf], so a count after midnight still takes the evening before). It started at the
     * book's first entry, so the first count added every cash receipt the shop ever wrote ("Khata cash received
     * Rs 1,652", "Should be in drawer Rs 1,695.89") against a drawer the owner had emptied many times since. The
     * owner types the cash the day started with as the opening.
     */
    fun firstCountFrom(now: Long, tz: java.util.TimeZone = java.util.TimeZone.getDefault()): Long =
        startOf(businessDayOf(now, tz), tz) - 1
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
    val kOut: Double,
    /** Where the stretch starts (exclusive): the last count's moment, or [DayCloseMath.firstCountFrom]. */
    val from: Long = 0L,
    /** Cash bills and staff pay since the count ([KhataDao.billsAndStaffCashBetween], 3 Nov 2026 audit, M03). */
    val otherOut: Double = 0.0
) {
    val cashIn: Double get() = cbIn + kIn
    val cashOut: Double get() = cbOut + kOut + otherOut

    /** What should be in the drawer, opening on the last count (nothing when never counted). */
    val expected: Double get() = expectedFrom(last?.counted ?: 0.0)

    /** The same figure from an opening the owner typed over the last count. */
    fun expectedFrom(opening: Double): Double = DayCloseMath.expected(opening, cashIn, cashOut)
}
