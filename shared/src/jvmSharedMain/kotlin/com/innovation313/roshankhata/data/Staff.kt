package com.innovation313.roshankhata.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Calendar

/**
 * The people who work at the shop (v29, 5–6 Oct 2026): their attendance,
 * the advances they take and the salary they are paid.
 *
 * Kept apart from the customer ledger on purpose. An advance to a helper is
 * not a customer's udhar, and putting it in the khata would swell "to get"
 * with money that is not a sale. No CNIC picture is kept (rejected earlier);
 * a name, an optional phone and the monthly salary are all this needs.
 */
@Entity(tableName = "staff", indices = [Index("uid")])
data class Staff(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String? = null,
    val monthlySalary: Double,
    /** False once the owner removes them from the list; their records stay. */
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),

    // ---- Logins (POS Phase 5, v34) ----

    /**
     * What this person may do at the counter, one of [Role]; null = on the
     * payroll only, no login. Stored as a key, never a translated word.
     */
    val role: String? = null,

    /** The PIN they sign in with, as a salted hash ([PinHash]) — never the PIN. Null = no PIN set. */
    val pinHash: String? = null,
    val pinSalt: String? = null,
    /** This row's identity across devices and backups (schema 38, live sync). */
    val uid: String? = Uid.next()
)

/**
 * Who may do what (POS Phase 5). The owner is not a staff row: the book is
 * theirs, and on the computer the App Lock PIN is theirs. A manager may do
 * everything at the counter the owner can, short of the settings and the
 * book itself; a cashier rings up and takes money, and asks for a manager
 * when something must be undone or marked down.
 */
object Role {
    const val MANAGER = "manager"
    const val CASHIER = "cashier"
    val ALL = listOf(MANAGER, CASHIER)
}

/** What version 34 adds (staff logins), as the SQL both migrations run — see [Schema32]. */
object Schema34 {
    val statements: List<String> = listOf(
        "ALTER TABLE staff ADD COLUMN role TEXT",
        "ALTER TABLE staff ADD COLUMN pinHash TEXT",
        "ALTER TABLE staff ADD COLUMN pinSalt TEXT"
    )
    val newColumns = mapOf("staff" to listOf("role", "pinHash", "pinSalt"))
}

/**
 * One marked day. A day with no row is a day present — the owner marks only
 * the exceptions, which is how a shop actually keeps a register.
 */
@Entity(tableName = "staff_attendance", indices = [Index(value = ["staffId", "day"], unique = true), Index("uid")])
data class StaffAttendance(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val staffId: Long,
    /** Local midnight of the day, epoch ms. */
    val day: Long,
    val status: Int,
    /** This row's identity across devices and backups (schema 38, live sync). */
    val uid: String? = Uid.next()
)

/** Money handed over: an advance, or salary paid. Never interest — an advance is returned as it was given. */
@Entity(tableName = "staff_payments", indices = [Index(value = ["staffId"]), Index("uid")])
data class StaffPayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val staffId: Long,
    val amount: Double,
    val kind: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String? = null,
    val isDeleted: Boolean = false,

    /**
     * This row's identity across copies of the book (schema 36): a random id
     * given once, kept by every backup and restore, the same on the phone and
     * the computer for a row that came from one copy. What a restore compares
     * to say what would be lost, and what a merge matches rows by. Null only
     * for a moment on a row restored from an old file; restoreAll fills it.
     */
    val uid: String? = Uid.next()
)

/** The month's sums for one person. Plain Kotlin, unit-tested (PayrollTest). */
object Payroll {
    const val PRESENT = 1
    const val ABSENT = 2
    const val HALF = 3
    /** Paid leave: counted as a day worked. */
    const val LEAVE = 4

    const val ADVANCE = 1
    const val SALARY = 2

    data class Month(
        val absent: Int,
        val half: Int,
        val leave: Int,
        val earned: Double,
        val advances: Double,
        val paid: Double
    ) {
        /** Above zero: still to pay. Below zero: more handed over than earned. */
        val due: Double get() = earned - advances - paid
    }

    /**
     * Earned = salary × (days in month − absent − ½ × half days) ÷ days in month.
     * Advances and payments count in the month they were handed over.
     */
    fun month(salary: Double, daysInMonth: Int, statuses: List<Int>, payments: List<StaffPayment>): Month {
        val absent = statuses.count { it == ABSENT }
        val half = statuses.count { it == HALF }
        val leave = statuses.count { it == LEAVE }
        val days = daysInMonth.coerceAtLeast(1)
        val worked = (days - absent - 0.5 * half).coerceIn(0.0, days.toDouble())
        val earned = Math.round(salary * worked / days * 100.0) / 100.0
        val live = payments.filter { !it.isDeleted }
        return Month(
            absent, half, leave, earned,
            advances = live.filter { it.kind == ADVANCE }.sumOf { it.amount },
            paid = live.filter { it.kind == SALARY }.sumOf { it.amount }
        )
    }

    /** Local midnight of [millis] ([DayMath.startOfDay]; attendance days are keyed on it). */
    fun dayOf(millis: Long): Long = DayMath.startOfDay(millis)

    /** [from, to) of the month holding [millis], and its number of days. */
    fun monthRange(millis: Long): Triple<Long, Long, Int> {
        val c = Calendar.getInstance().apply { timeInMillis = dayOf(millis); set(Calendar.DAY_OF_MONTH, 1) }
        val from = c.timeInMillis
        val days = c.getActualMaximum(Calendar.DAY_OF_MONTH)
        c.add(Calendar.MONTH, 1)
        return Triple(from, c.timeInMillis, days)
    }
}
