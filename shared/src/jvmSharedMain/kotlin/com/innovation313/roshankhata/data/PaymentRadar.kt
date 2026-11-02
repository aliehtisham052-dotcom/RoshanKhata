package com.innovation313.roshankhata.data

import java.util.Calendar
import java.util.TimeZone

/**
 * The Payment Radar (2 Nov 2026, the owner: "a blockbuster feature, for every
 * business worldwide"): how a customer usually pays, worked out on the device
 * from the shop's own khata — no server, no model, no network — and said with
 * its reasons so the owner can see why.
 *
 * - **How long credit takes to come back.** Each "I gave" is a lot; each "I got"
 *   pays the oldest lots first (FIFO), so a customer's lots each have a day
 *   they were cleared. A lot cleared within the hour is a sale paid at once
 *   (paid-now, the counter), not credit, and is not counted.
 * - **Against this shop's own normal.** Every trade has its own terms (a
 *   grocer's week, a wholesaler's two months), so a customer is judged against
 *   the median of all this shop's customers ([shopTypicalDays]), never a fixed
 *   number — which is what makes it work for every business in every country.
 * - **The same rhythm Follow-up reads.** His usual gap between payments and
 *   whether he is behind it are [PaymentHabit]'s, so the Radar and Follow-up's
 *   "late" never disagree; a customer who keeps to one day of the month is told
 *   that day instead.
 * - **Promises kept.** Each promised day the owner set is in the audit log
 *   ([PromiseLog]); one is kept when money came in by two days after it.
 *
 * Only for customers; only what the khata says (no guessing at entries never
 * written); and only shown to the owner — the screens keep it off a helper's
 * phone and never put it on anything the customer receives.
 */
object PaymentRadar {

    enum class Light { NONE, GREEN, AMBER, RED }

    /** What the Radar says of one customer. Every figure is in whole days. */
    data class Reading(
        val light: Light,
        /** How many credit lots this customer has paid back in full (the history the rest stands on). */
        val repaid: Int,
        /** The median days his last [RECENT] repaid lots took, or null with fewer than [MIN_REPAID]. */
        val typicalDays: Int?,
        /** This shop's own median, or null when the shop has too little history ([shopTypicalDays]). */
        val shopDays: Int?,
        /** How long the oldest amount still unpaid has waited, or null when nothing is owed. */
        val oldestOpenDays: Int?,
        /** The day of the month he usually pays on (1..31), when his payments keep to one. */
        val payDay: Int?,
        /** The likely window of his next payment (start of its first and last day), when he owes and keeps a rhythm. */
        val nextFrom: Long?,
        val nextTo: Long?,
        /** The day he promised has passed and he still owes. */
        val promiseOverdue: Boolean,
        /** What he owes now (positive) or has paid ahead (negative). */
        val balance: Double,
        /** Behind his own rhythm, as Follow-up says it ([PaymentHabit.Habit.isLate]). */
        val behindHabit: Boolean = false,
        /** Promised days that have come and gone: kept (money came by two days after) and not. */
        val promisesKept: Int = 0,
        val promisesBroken: Int = 0
    ) {
        /** He owes, and today is within the days he usually pays: the morning list's "likely now". */
        fun likelyNow(now: Long): Boolean {
            val from = nextFrom ?: return false
            val to = nextTo ?: return false
            return balance > 0.5 && now >= from && now < to + DAY
        }
    }

    /** One promise: when the owner wrote it, and the day promised. */
    data class Promise(val madeAt: Long, val day: Long)

    /** Lots paid back before a customer's own pace is trusted. */
    const val MIN_REPAID = 3
    /** The newest repaid lots his pace is taken from: what he does now, not five years ago. */
    const val RECENT = 12
    /** A whole shop's normal needs at least this many repaid lots, across its customers. */
    const val MIN_SHOP_REPAID = 8
    /** The normal used before the shop has one of its own: about a month. */
    const val FALLBACK_DAYS = 30
    /** No shop's normal is taken as less than a week: a shop of mostly cash sales would call every credit late. */
    const val FLOOR_DAYS = 7
    /** A promise is kept when money came in by this many days after the day promised. */
    const val PROMISE_GRACE_DAYS = 2

    private const val DAY = 24L * 60 * 60 * 1000
    private const val HOUR = 60L * 60 * 1000
    private const val PENNY = 0.5

    /** One customer's repaid lots (days each took, oldest first), what is still open, and his payments. */
    internal class Book(val repaidDays: List<Int>, val openSince: Long?, val payments: List<Long>, val balance: Double)

    /** [rows] of one customer, any order. */
    internal fun book(rows: List<LedgerPoint>): Book {
        class Lot(var left: Double, val at: Long)
        val lots = ArrayDeque<Lot>()
        val repaid = ArrayList<Int>()
        val payments = ArrayList<Long>()
        var ahead = 0.0 // paid before it was owed: the next credit takes from it first
        var balance = 0.0
        for (r in rows.sortedWith(compareBy<LedgerPoint> { it.timestamp }.thenBy { if (it.isGiven) 0 else 1 })) {
            if (r.amount.isNaN() || r.amount <= 0.0) continue
            if (r.isGiven) {
                balance += r.amount
                val fromAhead = minOf(ahead, r.amount)
                ahead -= fromAhead
                val owed = r.amount - fromAhead
                if (owed > PENNY) lots.addLast(Lot(owed, r.timestamp))
            } else {
                balance -= r.amount
                payments += r.timestamp
                var pay = r.amount
                while (pay > 0.0 && lots.isNotEmpty()) {
                    val lot = lots.first()
                    if (lot.left <= pay + PENNY) {
                        pay -= lot.left
                        lots.removeFirst()
                        val took = r.timestamp - lot.at
                        // Paid within the hour: a cash sale written as two lines, not credit.
                        if (took >= HOUR) repaid += (took / DAY).toInt()
                    } else {
                        lot.left -= pay
                        pay = 0.0
                    }
                }
                if (pay > PENNY) ahead += pay
            }
        }
        return Book(repaid, lots.firstOrNull()?.at, payments, balance)
    }

    /**
     * This shop's normal: the median days a credit took to come back, over every customer's recent repaid
     * lots ([rows] of all customers, [KhataDao.radarRows]); null below [MIN_SHOP_REPAID] lots.
     */
    fun shopTypicalDays(rows: List<LedgerPoint>): Int? {
        val all = rows.groupBy { it.partyId }.values.flatMap { book(it).repaidDays.takeLast(RECENT) }
        return if (all.size < MIN_SHOP_REPAID) null else median(all)
    }

    /**
     * Promises that have come and gone, counted kept or not ([PROMISE_GRACE_DAYS]); a promise replaced by a
     * newer one before its day had passed was moved, not broken, and is not counted.
     */
    internal fun promiseRecord(promises: List<Promise>, payments: List<Long>, now: Long, tz: TimeZone): Pair<Int, Int> {
        val sorted = promises.sortedBy { it.madeAt }
        var kept = 0
        var broken = 0
        sorted.forEachIndexed { i, p ->
            val end = DayMath.startOfDay(p.day, tz) + (PROMISE_GRACE_DAYS + 1) * DAY
            val paid = payments.any { it >= p.madeAt && it < end }
            val replacedBeforeEnd = sorted.getOrNull(i + 1)?.let { it.madeAt < end } == true
            when {
                paid -> kept++
                replacedBeforeEnd -> Unit
                now >= end -> broken++
            }
        }
        return kept to broken
    }

    /**
     * The reading for one customer from his own [rows] (any order), at [now], against [shopDays] (null: the
     * shop has no normal yet, [FALLBACK_DAYS] stands in), his current promised day [promise]
     * (Party.harvestPromise) and the promises he was ever given ([promises], the audit log's).
     */
    fun read(
        rows: List<LedgerPoint>,
        now: Long,
        shopDays: Int?,
        promise: Long? = null,
        promises: List<Promise> = emptyList(),
        tz: TimeZone = TimeZone.getDefault()
    ): Reading {
        val b = book(rows)
        val recent = b.repaidDays.takeLast(RECENT)
        val typical = if (recent.size >= MIN_REPAID) median(recent) else null
        val owes = b.balance > PENNY
        val oldest = if (owes) b.openSince?.let { ((now - it).coerceAtLeast(0) / DAY).toInt() } else null
        val today = DayMath.startOfDay(now, tz)
        val promiseOverdue = owes && promise != null && promise < today
        val habit = PaymentHabit.of(rows, now, tz)
        val (kept, broken) = promiseRecord(promises, b.payments, now, tz)

        val norm = maxOf(shopDays ?: FALLBACK_DAYS, FLOOR_DAYS)
        var light = Light.NONE
        fun atLeast(l: Light) { if (l.ordinal > light.ordinal) light = l }
        if (typical != null) {
            when {
                typical > norm * 5 / 2 -> atLeast(Light.RED)
                typical > norm * 3 / 2 -> atLeast(Light.AMBER)
                else -> atLeast(Light.GREEN)
            }
        }
        // An amount long unpaid is evidence of its own, even with little history behind it.
        if (oldest != null) {
            when {
                oldest > maxOf(norm * 4, 90) -> atLeast(Light.RED)
                oldest > maxOf(norm * 2, 30) -> atLeast(Light.AMBER)
            }
        }
        if (promiseOverdue) atLeast(Light.AMBER)
        if (habit?.isLate == true) atLeast(Light.AMBER)
        // Promises: two broken and more broken than kept is a pattern; three broken and none kept, a strong one.
        if (broken >= 3 && kept == 0) atLeast(Light.RED)
        else if (broken >= 2 && broken > kept) atLeast(Light.AMBER)
        // A customer who keeps his promises but has few repaid lots yet is still worth a green.
        if (light == Light.NONE && kept >= 2 && broken == 0) atLeast(Light.GREEN)

        val payDay = payDayOf(b.payments, tz)
        var from: Long? = null
        var to: Long? = null
        if (owes) {
            if (payDay != null) {
                val day = nextDayOfMonth(payDay, today, tz)
                from = maxOf(today, day - 2 * DAY)
                to = DayMath.startOfDay(day + 2 * DAY + HOUR * 12, tz)
            } else if (habit != null) {
                // Follow-up's own rhythm: his usual gap from the later of his last payment and the debt's start.
                val gap = habit.typicalDays
                val spread = maxOf(1, gap / 4)
                val due = DayMath.startOfDay(today + (gap - habit.waitingDays).toLong() * DAY + HOUR * 12, tz)
                from = maxOf(today, due - spread * DAY)
                to = maxOf(from + DAY, DayMath.startOfDay(due + spread * DAY + HOUR * 12, tz))
            }
        }
        return Reading(
            light, b.repaidDays.size, typical, shopDays, oldest, payDay, from, to, promiseOverdue, b.balance,
            behindHabit = habit?.isLate == true, promisesKept = kept, promisesBroken = broken
        )
    }

    /**
     * The customers who owe and are in their usual paying days today ([Reading.likelyNow]) — the morning
     * list's first names. [points] are the owing customers' lines ([KhataDao.observeOwingLedgerPoints]).
     */
    fun likelyNow(
        points: List<LedgerPoint>,
        shopDays: Int?,
        promises: Map<Long, Long>,
        now: Long,
        tz: TimeZone = TimeZone.getDefault()
    ): Set<Long> = points.groupBy { it.partyId }.mapNotNull { (id, rows) ->
        id.takeIf { read(rows, now, shopDays, promises[id], tz = tz).likelyNow(now) }
    }.toSet()

    /**
     * The day of the month a customer keeps to, when at least three of his last six payments fall in three
     * different months and three in four of them within two days of the same day; else null.
     */
    internal fun payDayOf(payments: List<Long>, tz: TimeZone): Int? {
        val last = payments.sorted().takeLast(6)
        if (last.size < 3) return null
        val cal = Calendar.getInstance(tz)
        val days = last.map { cal.timeInMillis = it; cal.get(Calendar.DAY_OF_MONTH) }
        val months = last.map { cal.timeInMillis = it; cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH) }.toSet()
        if (months.size < 3) return null
        // Days near a month's end are one habit ("the last day"): the 30th and the 1st are a day apart.
        fun apart(a: Int, b: Int): Int { val d = kotlin.math.abs(a - b); return minOf(d, 31 - d) }
        val best = (1..31).maxByOrNull { c -> days.count { apart(it, c) <= 2 } } ?: return null
        val near = days.count { apart(it, best) <= 2 }
        return if (near * 4 >= days.size * 3) median(days.filter { apart(it, best) <= 2 }) else null
    }

    /** The start of the next day numbered [dayOfMonth] (clamped to the month's length) on or after [today]. */
    internal fun nextDayOfMonth(dayOfMonth: Int, today: Long, tz: TimeZone): Long {
        val cal = Calendar.getInstance(tz)
        cal.timeInMillis = today
        repeat(3) {
            val max = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val c = cal.clone() as Calendar
            c.set(Calendar.DAY_OF_MONTH, minOf(dayOfMonth, max))
            c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            // Two days of grace: a customer due on the 10th is still "about now" on the 12th.
            if (c.timeInMillis + 2 * DAY >= today) return c.timeInMillis
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.add(Calendar.MONTH, 1)
        }
        return today
    }

    private fun median(xs: List<Int>): Int {
        val s = xs.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }
}

/**
 * Promised days kept as history (2 Nov 2026, for the Payment Radar): the party row holds only the current
 * promise, so each one the owner sets is also written to the audit log ([AuditAction.PROMISE_SET], the
 * party's id, the day's millis as the detail) — no schema change, and "who did what" shows it. Per device,
 * as the audit log is.
 */
object PromiseLog {
    fun of(rows: List<AuditLog>): List<PaymentRadar.Promise> =
        rows.mapNotNull { a -> a.detail?.toLongOrNull()?.let { PaymentRadar.Promise(a.timestamp, it) } }
}

/**
 * Sets the promised day as the party row keeps it, and — for a new day — writes it to the promise history
 * ([PromiseLog]). Clearing a promise, or putting back the same day, writes nothing.
 */
suspend fun KhataDao.promiseDay(partyId: Long, at: Long?) {
    val before = getParty(partyId)?.harvestPromise
    setHarvestPromise(partyId, at)
    if (at != null && at != before) audit(AuditAction.PROMISE_SET, "parties", partyId, at.toString())
}
