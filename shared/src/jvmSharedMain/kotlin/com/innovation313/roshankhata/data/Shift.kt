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
@Entity(tableName = "shifts", indices = [Index("openedAt")])
data class Shift(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Who: the staff member's name, or null for the owner (as on every sale). */
    val user: String? = null,
    /** Which counter (the device's counter code), where set. */
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
