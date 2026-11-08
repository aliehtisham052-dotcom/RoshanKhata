package com.innovation313.roshankhata.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A cashier's shift at the counter (schema 36): opened with the float in the
 * drawer, closed with what was counted. What should be there is worked out
 * from that person's own cash at the counter between the two moments
 * ([ShiftMath]); the difference needs its reason, as the day's close does.
 * The open and close times are also the person's hours (the time clock).
 */
@Entity(tableName = "shifts", indices = [Index("openedAt"), Index("uid")])
data class Shift(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Who: the staff member's name, or null for the owner (as on every sale). */
    val user: String? = null,
    /** Which counter: the device's document letter ([NumberMark.mark]) when the book synced as it opened, else null. */
    val device: String? = null,
    val openedAt: Long = System.currentTimeMillis(),
    val openingFloat: Double,
    val closedAt: Long? = null,
    val counted: Double? = null,
    val expected: Double? = null,
    val note: String? = null,
    val uid: String? = Uid.next()
) {
    val isOpen: Boolean get() = closedAt == null
    val difference: Double? get() = if (counted != null && expected != null) LineMath.round(counted - expected) else null
    /** Hours on the clock, to two places; null while open. */
    val hours: Double? get() = closedAt?.let { LineMath.round((it - openedAt) / 3_600_000.0) }
}

/**
 * A shift's drawer (21 Oct 2026): what it should hold when it is closed —
 * the float it was opened with, plus the cash taken on that person's own
 * counter sales, less the cash they handed back on returns, between the
 * shift's opening and [until]. Cash only: a bank transfer or a cheque never
 * reaches the drawer. Summed from the tickets, never stored until the close.
 */
object ShiftMath {

    suspend fun expected(dao: KhataDao, shift: Shift, until: Long = System.currentTimeMillis()): Double =
        LineMath.round(shift.openingFloat + taken(dao, shift, until) - back(dao, shift, until))

    /**
     * Which device's tickets a shift's drawer holds (23 Oct 2026, the review): the letter of the device
     * it was opened on ([Shift.device], set from [NumberMark] since then), else this device's while the
     * book syncs; "" — every ticket — on a book that does not sync.
     */
    fun markOf(shift: Shift): String = shift.device?.takeIf { it.isNotBlank() }?.let { "-$it" } ?: NumberMark.suffix()

    /** The cash taken on the shift's own tickets until [until]. */
    suspend fun taken(dao: KhataDao, shift: Shift, until: Long): Double = dao.cashTakenBy(shift.user, shift.openedAt, until, markOf(shift))

    /** The cash handed back on the shift's own returns until [until]. */
    suspend fun back(dao: KhataDao, shift: Shift, until: Long): Double = dao.cashRefundedBy(shift.user, shift.openedAt, until, markOf(shift))

    /** Hours on the clock per person (null = the owner) over [shifts], closed ones only, to two places. */
    fun hours(shifts: List<Shift>): Map<String?, Double> =
        shifts.filter { !it.isOpen }.groupBy { it.user }.mapValues { (_, ss) -> LineMath.round(ss.sumOf { it.hours ?: 0.0 }) }

    /** Why a close is refused: a short or over drawer needs its reason, as the day's close does. */
    fun needsReason(expected: Double, counted: Double, note: String?): Boolean =
        kotlin.math.abs(counted - expected) > 0.005 && note.isNullOrBlank()
}

