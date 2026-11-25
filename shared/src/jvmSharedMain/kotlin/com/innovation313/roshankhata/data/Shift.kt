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
    /** Hours on the clock, exact; null while open. Hours are not money: never rounded to the currency's decimals (8 Nov 2026 review, M14). */
    val hours: Double? get() = closedAt?.let { (it - openedAt) / 3_600_000.0 }
}

/**
 * A shift's drawer (21 Oct 2026): what it should hold when it is closed —
 * the float it was opened with, plus the cash taken on that person's own
 * counter sales, less the cash they handed back on returns, between the
 * shift's opening and [until] — and, since 17 Nov 2026 (L108), plus and less
 * the cash they moved by hand in the khata and the cashbook ([byHand]). Cash
 * only: a bank transfer or a cheque never reaches the drawer. Summed from the
 * rows, never stored until the close.
 */
object ShiftMath {

    suspend fun expected(dao: KhataDao, shift: Shift, until: Long = System.currentTimeMillis()): Double =
        expectedOf(drawer(dao, shift, until))

    /** What the drawer should hold, from its parts, to the shop's coin. */
    fun expectedOf(d: ShiftDrawer): Double = LineMath.round(d.sum)

    /**
     * Every part of the drawer until [until] ([ShiftDrawer]): the one call the close and both platforms' shift screen
     * make, so the figure the screen shows is the figure the close keeps.
     */
    suspend fun drawer(dao: KhataDao, shift: Shift, until: Long = System.currentTimeMillis()): ShiftDrawer {
        val (handIn, handOut) = byHand(dao, shift, until)
        return ShiftDrawer(shift.openingFloat, taken(dao, shift, until), back(dao, shift, until), handIn, handOut)
    }

    /**
     * The cash the shift's person moved by hand between its opening and [until] (17 Nov 2026, the audit's L108): khata
     * entries and cashbook rows they wrote in cash, in and out (the drawer's own rules, [KhataDao.khataCashBy] and
     * [KhataDao.cashbookCashBy]); never a counter ticket's or return's rows, which [taken] and [back] count. By person, not
     * by device: a person has one shift open at a time ([KhataDao.openShift]), and a cashbook row carries no device letter.
     *
     * The owner's shift (user null) gets (0, 0): the owner's rows carry no name, and neither does any row written before
     * names were kept (cashbook rows before schema 46, khata entries before 8 Nov 2026), so a row with no name is counted
     * in no shift — exactly as no khata or cashbook row was before this.
     */
    suspend fun byHand(dao: KhataDao, shift: Shift, until: Long): Pair<Double, Double> {
        val user = shift.user?.takeIf { it.isNotBlank() } ?: return 0.0 to 0.0
        val (from, to) = span(shift, until)
        val handIn = dao.khataCashBy(user, given = false, from = from, to = to) + dao.cashbookCashBy(user, income = true, from = from, to = to)
        val handOut = dao.khataCashBy(user, given = true, from = from, to = to) + dao.cashbookCashBy(user, income = false, from = from, to = to)
        return handIn to handOut
    }

    /**
     * Which device's tickets a shift's drawer holds (23 Oct 2026, the review): the letter of the device
     * it was opened on ([Shift.device], set from [NumberMark] since then), else this device's while the
     * book syncs; "" — every ticket — on a book that does not sync.
     */
    fun markOf(shift: Shift): String = shift.device?.takeIf { it.isNotBlank() }?.let { "-$it" } ?: NumberMark.suffix()

    /** The cash taken on the shift's own tickets until [until]. */
    suspend fun taken(dao: KhataDao, shift: Shift, until: Long): Double =
        span(shift, until).let { (from, to) -> dao.cashTakenBy(shift.user, from, to, markOf(shift)) }

    /** The cash handed back on the shift's own returns until [until]. */
    suspend fun back(dao: KhataDao, shift: Shift, until: Long): Double =
        span(shift, until).let { (from, to) -> dao.cashRefundedBy(shift.user, from, to, markOf(shift)) }

    /**
     * The shift's stretch as its `[from, to)` queries read it: from its opening up to AND including [until] (D8, BC-297,
     * [DaySpans.through]). The day's drawer counts a line written at the moment of its close ([DayCloseMath.drawerByTheBooks],
     * `(from, to]`); the shift did not, so a cash line written in the very millisecond the shift closed was in one and not the other.
     */
    fun span(shift: Shift, until: Long): Pair<Long, Long> = shift.openedAt to DaySpans.through(until)

    /** Hours on the clock per person (null = the owner) over [shifts], closed ones only, to two places. */
    fun hours(shifts: List<Shift>): Map<String?, Double> =
        shifts.filter { !it.isOpen }.groupBy { it.user }.mapValues { (_, ss) -> Math.round(ss.sumOf { it.hours ?: 0.0 } * 100.0) / 100.0 }

    /** Why a close is refused: a short or over drawer needs its reason, as the day's close does. */
    fun needsReason(expected: Double, counted: Double, note: String?): Boolean =
        Money.isNotZero(counted - expected) && note.isNullOrBlank()
}

