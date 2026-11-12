package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.WP
import com.innovation313.roshankhata.words.Words
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.flow.first

/**
 * The shop's daily work, said at its own time (31 Oct 2026, the owner: "koi automation rakh ke hum behtar
 * tareeqe se manage karein"). The bell and the daily sweep only said THAT something was due; these five
 * say what to do next and open the place to do it:
 *
 *  - [Kind.COLLECT] in the morning: who to remind today, by Follow-up's own rule ([FollowUpRank]), with the
 *    total; a tap opens Follow-up and starts its one-by-one WhatsApp queue.
 *  - [Kind.DAY_END] at closing: today's sales and payments and what the drawer should hold
 *    ([DayCloseMath.drawerByTheBooks]); a tap opens the cashbook's Close the day.
 *  - [Kind.REORDER]: products at or under their low level that no open order already carries, each with a
 *    quantity for the next 30 days and the supplier it came from last ([reorder]); a tap opens a ready draft.
 *  - [Kind.MONTH] on the 1st: last month's Khata Report figures; a tap opens that month's report to share.
 *  - [Kind.EXPIRY]: the batch closest to expiring, by name, with what is left and its day ([Fefo.expiring]).
 *
 * The rule the owner was given, and the one these keep: they TELL. None sends a message or writes a row on its
 * own — a background write on two synced devices would make every draft twice, and a WhatsApp message needs
 * the paid Cloud API, which was decided against (29 Oct 2026). Each is a switch with an hour, per device
 * ([Settings]); each says itself at most once a day (the month's once a month), at or after its hour.
 */
object Automations {

    enum class Kind(val key: String, val defaultHour: Int, val word: String) {
        COLLECT("collect", 9, W.auto_collect_name),
        REORDER("reorder", 10, W.auto_reorder_name),
        EXPIRY("expiry", 10, W.auto_expiry_name),
        MONTH("month", 9, W.auto_month_name),
        DAY_END("dayend", 21, W.auto_dayend_name);

        /** Monthly, not daily: said on the first day it may be, once in a calendar month. */
        val monthly: Boolean get() = this == MONTH
    }

    /** Where each platform keeps the switches, the hours and when each was last said (per device). */
    interface Settings {
        fun isOn(kind: Kind): Boolean
        fun hour(kind: Kind): Int
        /** [stamp] of the last time [kind] was said, or null. */
        fun lastSaid(kind: Kind): Long?
        fun setSaid(kind: Kind, stamp: Long)
    }

    /** The day (or, for [Kind.MONTH], the month) a saying belongs to: what "once" is counted by. */
    fun stamp(kind: Kind, now: Long, tz: TimeZone = TimeZone.getDefault()): Long {
        if (!kind.monthly) return DayMath.dayNumber(now, tz)
        val c = Calendar.getInstance(tz).apply { timeInMillis = now }
        return c.get(Calendar.YEAR) * 12L + c.get(Calendar.MONTH)
    }

    /**
     * Whether [kind] should be said now: switched on, its hour reached today, not past its last hour
     * ([lastHour]), and not said yet in this day (month). A phone asleep through the hour says it when it wakes —
     * late, never twice — but not into the night (3 Nov 2026 audit, M23: a phone that first ran the check at
     * 23:30 said the morning's collection, orders and expiry together, with sound). A day missed is missed; the
     * month's report is asked again the next day, as its once is the month.
     */
    fun due(kind: Kind, settings: Settings, now: Long, tz: TimeZone = TimeZone.getDefault()): Boolean {
        if (!settings.isOn(kind)) return false
        val c = Calendar.getInstance(tz).apply { timeInMillis = now }
        val hour = settings.hour(kind).coerceIn(0, 23)
        val nowHour = c.get(Calendar.HOUR_OF_DAY)
        if (nowHour < hour || nowHour >= lastHour(hour)) return false
        return settings.lastSaid(kind) != stamp(kind, now, tz)
    }

    /**
     * The hour a notice set for [hour] is said no later than (exclusive): 22:00, the start of the quiet night —
     * or three hours after its own hour when the owner set it late (21:00's closing summary until midnight).
     */
    fun lastHour(hour: Int): Int = maxOf(QUIET_FROM, hour + 3).coerceAtMost(24)

    /** Nothing is said from this hour to midnight unless the owner set a notice that late. */
    const val QUIET_FROM = 22

    /** One thing to say: [title] and [body] in the owner's language, and where a tap goes. */
    data class Notice(val kind: Kind, val title: String, val body: String, val extra: Long? = null)

    // ---------------------------------------------------------------- the five

    data class Collect(
        val count: Int,
        val total: Double,
        val names: List<String>,
        val likely: Int = 0,
        /** The first three to remind, as Follow-up orders them (12 Nov 2026: Hudhud's morning card lists them). */
        val top: List<PartyWithBalance> = emptyList(),
        /** Of those to remind, who is in his usual paying days ([PaymentRadar.likelyNow]). */
        val likelyIds: Set<Long> = emptySet()
    )

    /**
     * Follow-up's "remind today" (a promise come due, or late by his own habit), less those already
     * reminded today ([remindedToday]: the phone's ReminderLog, the computer's ReminderStore), with a phone.
     */
    suspend fun collect(
        dao: KhataDao,
        remindedToday: Set<Long>,
        now: Long,
        tz: TimeZone = TimeZone.getDefault(),
        /** The Payment Radar's switch on this device: those in their usual paying days join the list and lead it. */
        radar: Boolean = false
    ): Collect {
        val parties = dao.partiesWithBalanceOnce()
        val debtors = parties.filter { Money.isPositive(it.balance) }
        if (debtors.isEmpty()) return Collect(0, 0.0, emptyList())
        val points = LedgerLines.owingOf(dao, parties)
        val habits = PaymentHabit.forAll(points)
        val promises = dao.observePromises().first().associate { it.partyId to it.due }
        val likely = if (!radar) emptySet() else runCatching {
            PaymentRadar.likelyNow(points, PaymentRadar.shopTypicalDays(LedgerLines.customers(dao)), promises, now, tz)
        }.getOrDefault(emptySet())
        val due = FollowUpRank.order(debtors, habits, promises, now, tz, remindedToday, likely).filter {
            !it.phone.isNullOrBlank() && FollowUpRank.remindToday(it.id, habits, promises, now, tz, remindedToday, likely)
        }
        return Collect(due.size, due.sumOf { it.balance }, due.take(3).map { it.name }, due.count { it.id in likely },
            top = due.take(3), likelyIds = likely)
    }

    data class DayEnd(val sales: Double, val received: Double, val drawer: Double, val counted: Boolean, val moved: Boolean)

    /** Today, from midnight: sales (khata and counter), payments in, and the drawer by the books. */
    suspend fun dayEnd(dao: KhataDao, now: Long, tz: TimeZone = TimeZone.getDefault()): DayEnd {
        val from = DayMath.startOfDay(now, tz)
        // The whole day, not up to [now]: an entry dated later today (a clock set back, a synced device) is today's.
        val to = DayMath.startOfDay(from + 36L * 60 * 60 * 1000, tz)
        val sales = dao.salesTotalBetween(from, to)
        // From customers and walk-ins, not a supplier's credit bill (3 Nov 2026 audit, M15).
        val received = dao.customerReceivedBetween(from, to)
        val drawer = DayCloseMath.drawerByTheBooks(dao, now)
        // By working day, as the close is filed (M15: a close at 00:20 is the evening before's).
        val counted = drawer.last?.let { it.day == DayCloseMath.businessDayOf(now, tz) } == true
        // Cash that moved TODAY (8 Nov 2026, L81): the drawer's own cash-in/out run since the last count, so a drawer
        // left uncounted for days made every evening's notice say "moved".
        val cashToday = dao.cashbookCashBetween(true, from - 1, to) + dao.cashbookCashBetween(false, from - 1, to) +
            dao.khataCashBetween(true, from - 1, to) + dao.khataCashBetween(false, from - 1, to)
        val moved = Money.isPositive(sales) || Money.isPositive(received) || Money.isPositive(cashToday)
        return DayEnd(sales, received, drawer.expected, counted, moved)
    }

    /** One product to order: how much (in its own unit) and from whom it came last. */
    data class ReorderLine(val productId: Long, val name: String, val qty: Double, val unit: String?, val supplierId: Long?)

    /**
     * The low shelf ([Valuation.low]: at or under the level the owner set, where the shelf is known), less what
     * an order not yet received already carries. The quantity covers the next 30 days by the last 30 days' sales
     * of that product (same name, same unit) and refills to twice the low level, whichever is more, less what is
     * on the shelf, at least 1 — the "Order the low stock" button's old figure was only the second half. The
     * supplier is the one on the newest bill that carried the product (by product, else by name).
     */
    suspend fun reorder(dao: KhataDao, now: Long): List<ReorderLine> {
        val low = runCatching { Valuation.compute(dao).low }.getOrDefault(emptyList())
        if (low.isEmpty()) return emptyList()
        val onOrder = mutableSetOf<Long>()
        // Only an order still on its way (3 Nov 2026 audit, M30): a CLOSED one carried its lines for ever and the
        // product was never offered again.
        dao.observeOrders().first().filter { it.status == OrderStatus.DRAFT || it.status == OrderStatus.SENT }.forEach { o ->
            dao.orderItems(o.id).filter { !it.isDeleted }.forEach { i -> i.productId?.let(onOrder::add) }
        }
        val sold = dao.topProductsBetween(now - SALES_WINDOW_MS, now + 24L * 60 * 60 * 1000, 10_000)
            .groupBy { key(it.name) }
        val products = dao.productsOnce().associateBy { it.id }
        val bills = dao.allBillsForBackup().filter { !it.isDeleted }.associateBy { it.id }
        val lastSupplier = mutableMapOf<String, Pair<Long, Long>>() // product key -> (billDate, partyId)
        for (item in dao.allBillItemsForBackup()) {
            if (item.isDeleted) continue
            val bill = bills[item.billId] ?: continue
            val k = item.productId?.let { "#$it" } ?: key(item.productName)
            val seen = lastSupplier[k]
            if (seen == null || bill.billDate > seen.first) lastSupplier[k] = bill.billDate to bill.partyId
        }
        return low.filter { it.productId !in onOrder }.map { r ->
            val p = products[r.productId]
            val unit = r.unit ?: p?.defaultUnit
            // Packs counted in the product's own unit (3 Nov 2026 audit, M31): 8 cartons of 12 and 10 bottles are
            // 106 bottles, not 10.
            val month = sold[key(r.name)].orEmpty()
                .map { s -> Units.toBase(p, s.qty, s.unit) }
                .filter { (_, u) -> u.isNullOrBlank() || unit.isNullOrBlank() || UnitWords.canonical(u) == UnitWords.canonical(unit) }
                .sumOf { it.first }
            val onHand = r.onHand ?: 0.0
            val want = maxOf(month, (r.lowStockLevel ?: 0.0) * 2) - onHand
            val supplier = (lastSupplier["#${r.productId}"] ?: lastSupplier[key(r.name)])?.second
            ReorderLine(r.productId, r.name, kotlin.math.ceil(want).coerceAtLeast(1.0), unit, supplier)
        }
    }

    /**
     * One order goes to one supplier: the supplier who carries the most of [lines] (ties: the first met), and
     * those lines. Lines with no known supplier come only when no supplier is known for any; the rest stay low
     * and are offered again by the next "Order the low stock" (they are on no open order yet).
     */
    fun firstOrder(lines: List<ReorderLine>): Pair<Long?, List<ReorderLine>> {
        val known = lines.filter { it.supplierId != null }.groupBy { it.supplierId }
        if (known.isEmpty()) return null to lines
        val best = known.maxByOrNull { it.value.size }!!
        return best.key to best.value
    }

    /** Last calendar month, [from, to] with [to] its last millisecond (the Khata Report's custom window). */
    fun lastMonth(now: Long, tz: TimeZone = TimeZone.getDefault()): Pair<Long, Long> {
        val c = Calendar.getInstance(tz).apply {
            timeInMillis = now; set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val to = c.timeInMillis - 1
        c.add(Calendar.MONTH, -1)
        return c.timeInMillis to to
    }

    data class Month(val from: Long, val to: Long, val gave: Double, val got: Double, val entries: Int)

    /** Last month's Khata Report figures (every entry in the window, as the report counts them). */
    suspend fun month(dao: KhataDao, now: Long, tz: TimeZone = TimeZone.getDefault()): Month {
        val (from, to) = lastMonth(now, tz)
        val rows = dao.entriesInRange(from, to)
        return Month(from, to, rows.filter { it.isGiven }.sumOf { it.amount }, rows.filter { !it.isGiven }.sumOf { it.amount }, rows.size)
    }

    /** The batches still holding something inside the warning window, soonest first. */
    suspend fun expiring(dao: KhataDao, now: Long): List<ExpiringBatch> =
        runCatching { Fefo.expiring(dao, DayMath.startOfDay(now) + ExpiryWindow.WARN_MS) }.getOrDefault(emptyList())
            .sortedBy { it.expiryDate }

    // ---------------------------------------------------------------- what is said

    /**
     * Every notice due now, in the owner's words, for the open book; nothing when there is nothing to do (a
     * morning with no one to remind is not a notice). Marks each kind said for this day, so a second run (a
     * second opening of the app, a worker run an hour later) says nothing again — including the quiet ones,
     * so an empty morning is not looked at again all day.
     */
    suspend fun noticesDue(
        dao: KhataDao,
        settings: Settings,
        words: Words,
        money: (Double) -> String,
        date: (Long) -> String,
        monthName: (Long) -> String,
        remindedToday: Set<Long>,
        now: Long = System.currentTimeMillis(),
        tz: TimeZone = TimeZone.getDefault(),
        /** The Payment Radar's switch on this device (the morning list's likely names, [collect]). */
        radar: Boolean = false
    ): List<Notice> {
        val out = mutableListOf<Notice>()
        for (kind in Kind.values()) {
            if (!due(kind, settings, now, tz)) continue
            val tried = runCatching { noticeOf(kind, dao, words, money, date, monthName, remindedToday, now, tz, radar) }
            // A computation that failed (a busy book) is not "said today": the next run tries again (8 Nov 2026 review, L151).
            if (tried.isFailure) continue
            settings.setSaid(kind, stamp(kind, now, tz))
            tried.getOrNull()?.let { out += it }
        }
        return out
    }

    private suspend fun noticeOf(
        kind: Kind, dao: KhataDao, words: Words, money: (Double) -> String, date: (Long) -> String,
        monthName: (Long) -> String, remindedToday: Set<Long>, now: Long, tz: TimeZone, radar: Boolean
    ): Notice? = when (kind) {
        Kind.COLLECT -> collect(dao, remindedToday, now, tz, radar).takeIf { it.count > 0 }?.let { c ->
            val names = c.names.joinToString(", ") { Format.isolate(it) }
            // How many of them usually pay around now (the Payment Radar) — they lead the names.
            val likely = if (c.likely > 0) "\n" + words.text(W.auto_collect_likely, c.likely.toString()) else ""
            Notice(kind, words.text(W.auto_collect_title),
                words.plural(WP.auto_collect_body, c.count, c.count, money(c.total)) + "\n" + names + likely)
        }
        Kind.DAY_END -> dayEnd(dao, now, tz).takeIf { it.moved }?.let { d ->
            val body = words.text(W.auto_dayend_body, money(d.sales), money(d.received), (if (Money.isNegative(d.drawer)) "\u2212 " else "") + money(d.drawer))
            Notice(kind, words.text(W.auto_dayend_title),
                if (d.counted) body else body + "\n" + words.text(W.auto_dayend_uncounted))
        }
        Kind.REORDER -> reorder(dao, now).takeIf { it.isNotEmpty() }?.let { lines ->
            val names = lines.take(3).joinToString(", ") { Format.isolate(it.name) }
            Notice(kind, words.text(W.auto_reorder_title), words.plural(WP.auto_reorder_body, lines.size, lines.size, names))
        }
        Kind.MONTH -> month(dao, now, tz).takeIf { it.entries > 0 }?.let { m ->
            Notice(kind, words.text(W.auto_month_title, monthName(m.from)),
                words.text(W.auto_month_body, money(m.gave), money(m.got)), extra = m.from)
        }
        Kind.EXPIRY -> expiring(dao, now).takeIf { it.isNotEmpty() }?.let { list ->
            val b = list.first()
            val left = Format.qty(b.quantity, b.unit)
            // Already past its day (3 Nov 2026 audit, M32): never "sell it, give a discount" — for medicine and
            // sprays that is unsafe advice. Written off or sent back.
            val expired = b.expiryDate < DayMath.startOfDay(now)
            val head = words.text(if (expired) W.auto_expiry_expired_body else W.auto_expiry_body, Format.isolate(b.productName),
                b.batchNumber?.takeIf { it.isNotBlank() } ?: "-", left, date(b.expiryDate))
            val more = if (list.size > 1) "\n" + words.plural(WP.auto_expiry_more, list.size - 1, list.size - 1) else ""
            Notice(kind, words.text(if (expired) W.auto_expiry_expired_title else W.auto_expiry_title), head + more, extra = b.itemId)
        }
    }

    private const val SALES_WINDOW_MS = 30L * 24 * 60 * 60 * 1000

    private fun key(name: String?): String = name.orEmpty().trim().lowercase().replace(Regex("\\s+"), " ")
}
