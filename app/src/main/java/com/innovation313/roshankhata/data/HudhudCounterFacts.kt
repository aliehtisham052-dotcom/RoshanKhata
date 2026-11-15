package com.innovation313.roshankhata.data

import android.content.Context
import com.innovation313.roshankhata.CashbookActivity
import com.innovation313.roshankhata.CounterSaleActivity
import com.innovation313.roshankhata.DayBookActivity
import com.innovation313.roshankhata.KhataActivity
import com.innovation313.roshankhata.ProductsActivity
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.ShiftActivity
import com.innovation313.roshankhata.data.HudhudBook.Answer
import com.innovation313.roshankhata.data.HudhudBook.Mood
import com.innovation313.roshankhata.data.HudhudBook.Row
import com.innovation313.roshankhata.ui.Format
import kotlin.math.abs

/**
 * What Hudhud tells of the counter's own books (15 Nov 2026, the catalog's counter group; importance 8: counter money and
 * stock): the biggest and the last bill, what each cashier rang up, the day's discounts, tax, credit and ways of paying,
 * bills and returns that were voided and by whom, why goods came back and how the money went back, shifts and the
 * drawer's count, loyalty points, and the stock's own corrections (written off, a count).
 *
 * Every figure is read by the rule of the screen it opens — the day's sums by [ZReport] (the day report), a shift's by
 * [Shift] and [ShiftMath], the drawer's by the close's own row ([DayClose]), points by the loyalty rows — and nothing is
 * estimated or valued: goods written off are told in quantities, never in guessed money. **Every card here is behind the
 * REPORTS right** (the owner or a manager): the counter screens are open to a cashier, but a sum, a list of who rang what,
 * who voided what and what a shift was short is told only to who may see reports, until the owner decides otherwise. He
 * only READS: he writes nothing, voids nothing, and the card opens the screen where the owner acts.
 */
internal object HudhudCounterFacts {

    private fun now() = System.currentTimeMillis()

    // Every gate is asked through HudhudGate (the catalog holds each fact to the gate of the screen that shows it).
    private fun reports() = HudhudGate.reports()

    private fun ownerOnly(c: Context, label: String) =
        Answer(Mood.CALM, label = label, lines = listOf(c.getString(R.string.hudhud_owner_only)))

    private fun line(label: String, value: String) = label + ": " + Format.isolate(value)

    private fun line(c: Context, label: Int, value: String) = line(c.getString(label), value)

    private fun whenOf(c: Context, t: Long) = WhenWords.whenWords(t, now(), AndroidWords(c))

    private fun n(count: Int): String = count.toString()

    private fun money(v: Double) = HudhudBook.money(v)

    private fun heading(c: Context, title: Int, window: VoiceQuestion.Window?): String =
        if (window == null) c.getString(title) else c.getString(title) + " · " + c.getString(HudhudBook.windowWord(window))

    /** Nothing written in this time. */
    private fun none(c: Context, label: String, open: Class<*>): Answer =
        Answer(Mood.CALM, label = label, lines = listOf(c.getString(R.string.hudhud_counter_none)), open = open)

    /** More than a phone adds up at once: the span is too wide for this question. */
    private fun tooMany(c: Context, label: String, open: Class<*>): Answer =
        Answer(Mood.CALM, label = label, lines = listOf(c.getString(R.string.hudhud_counter_too_many)), open = open)

    /** A person's name as the books keep it: a null name is the owner. */
    private fun who(c: Context, name: String?): String = name?.takeIf { it.isNotBlank() } ?: c.getString(R.string.access_owner)

    private fun customerOf(c: Context, name: String?): String = name?.takeIf { it.isNotBlank() } ?: c.getString(R.string.pos_walk_in)

    private fun wayWord(c: Context, method: String): String =
        if (method == SaleReturn.ACCOUNT) c.getString(R.string.pos_refund_account) else PaymentMethod.label(AndroidWords(c), method)

    private fun reasonWord(c: Context, reason: String): String = when (reason) {
        AdjustReason.COUNT -> c.getString(R.string.adjust_reason_count)
        AdjustReason.DAMAGE -> c.getString(R.string.adjust_reason_damage)
        AdjustReason.EXPIRED -> c.getString(R.string.adjust_reason_expired)
        AdjustReason.THEFT -> c.getString(R.string.adjust_reason_theft)
        AdjustReason.OTHER -> c.getString(R.string.adjust_reason_other)
        else -> reason
    }

    /** A facet's own name, without any span (the chips that offer the same question over other spans say it). */
    fun titleOf(c: Context, facet: Hudhud.CounterFacet): String = c.getString(when (facet) {
        Hudhud.CounterFacet.BIGGEST -> R.string.hudhud_t_biggest
        Hudhud.CounterFacet.LAST_TICKET -> R.string.hudhud_t_last_ticket
        Hudhud.CounterFacet.BY_CASHIER -> R.string.hudhud_t_by_cashier
        Hudhud.CounterFacet.DISCOUNTS -> R.string.hudhud_t_discounts
        Hudhud.CounterFacet.TAX -> R.string.hudhud_t_tax
        Hudhud.CounterFacet.ON_ACCOUNT -> R.string.hudhud_t_on_account
        Hudhud.CounterFacet.PAY_WAYS -> R.string.hudhud_t_pay_ways
        Hudhud.CounterFacet.VOIDED -> R.string.hudhud_t_voided
        Hudhud.CounterFacet.RETURN_REASONS -> R.string.hudhud_t_return_reasons
        Hudhud.CounterFacet.RETURN_WAYS -> R.string.hudhud_t_return_ways
        Hudhud.CounterFacet.RETURN_GOODS -> R.string.hudhud_t_return_goods
        Hudhud.CounterFacet.SHIFTS, Hudhud.CounterFacet.SHIFT_SHORT -> R.string.shift_title
        Hudhud.CounterFacet.DAY_COUNT -> R.string.hudhud_t_day_count
        Hudhud.CounterFacet.LOYALTY -> R.string.loyalty_section
        Hudhud.CounterFacet.WRITTEN_OFF -> R.string.hudhud_t_written_off
        Hudhud.CounterFacet.STOCK_CHANGES -> R.string.hudhud_t_stock_changes
        Hudhud.CounterFacet.LAST_COUNT -> R.string.hudhud_t_last_count
    })

    /** The one question the sheet asks, sent to the reader of its facet. */
    suspend fun answer(c: Context, dao: KhataDao, ask: Hudhud.Ask.Counter): Answer {
        val w = ask.window
        return when (ask.facet) {
            Hudhud.CounterFacet.BIGGEST -> biggest(c, dao, w)
            Hudhud.CounterFacet.LAST_TICKET -> lastTicket(c, dao)
            Hudhud.CounterFacet.BY_CASHIER -> byCashier(c, dao, w)
            Hudhud.CounterFacet.DISCOUNTS, Hudhud.CounterFacet.TAX, Hudhud.CounterFacet.ON_ACCOUNT, Hudhud.CounterFacet.PAY_WAYS ->
                dayFigures(c, dao, ask.facet, w)
            Hudhud.CounterFacet.VOIDED -> voided(c, dao, w)
            Hudhud.CounterFacet.RETURN_REASONS -> returnReasons(c, dao, w)
            Hudhud.CounterFacet.RETURN_WAYS -> returnWays(c, dao, w)
            Hudhud.CounterFacet.RETURN_GOODS -> returnGoods(c, dao, w)
            Hudhud.CounterFacet.SHIFTS -> shifts(c, dao, w, onlyShort = false)
            Hudhud.CounterFacet.SHIFT_SHORT -> shifts(c, dao, w, onlyShort = true)
            Hudhud.CounterFacet.DAY_COUNT -> dayCount(c, dao, w)
            Hudhud.CounterFacet.LOYALTY -> loyalty(c, dao, ask.ids, w)
            Hudhud.CounterFacet.WRITTEN_OFF -> writtenOff(c, dao, w)
            Hudhud.CounterFacet.STOCK_CHANGES -> stockChanges(c, dao, w)
            Hudhud.CounterFacet.LAST_COUNT -> lastCount(c, dao)
        }
    }

    // ------------------------------------------------------------------------------------------------ the day's bills

    /** The newest finished bill, whole. */
    suspend fun lastTicket(c: Context, dao: KhataDao): Answer {
        val title = c.getString(R.string.hudhud_t_last_ticket)
        if (!reports()) return ownerOnly(c, title)
        return ticket(c, dao, dao.newestSaleId(), title, emptyList())
    }

    /** The biggest bill of a span (today when none was said), whole, with the next biggest under it. */
    suspend fun biggest(c: Context, dao: KhataDao, window: VoiceQuestion.Window?): Answer {
        val w = window ?: VoiceQuestion.Window.TODAY
        val title = heading(c, R.string.hudhud_t_biggest, w)
        if (!reports()) return ownerOnly(c, title)
        val (from, to) = Hudhud.span(w)
        val top = dao.biggestSalesBetween(from, to, TOP + 1)
        return ticket(c, dao, top.firstOrNull()?.id, title, top.drop(1))
    }

    /**
     * One finished bill as the counter wrote it: its number, who it was for and who rang it up, its goods with their
     * rate and discount, the bill's own discount, tax, round-off, what was paid and how (a payment's reference is shown
     * and never said aloud), what is still to pay, and its note. [more] are the other bills of the list (as rows).
     */
    suspend fun ticket(c: Context, dao: KhataDao, id: Long?, title: String, more: List<SaleSummary>): Answer {
        if (!reports()) return ownerOnly(c, title)
        val open = CounterSaleActivity::class.java
        val full = id?.let { dao.saleWithLines(it) } ?: return none(c, title, open)
        val s = full.sale
        val lines = ArrayList<String>()
        lines += listOf(s.saleNumber, customerOf(c, s.customerName), whenOf(c, s.timestamp)).joinToString(" · ")
        lines += line(c, R.string.hudhud_l_by, who(c, s.createdBy))
        for (i in full.items.filter { !it.isDeleted }.take(TOP)) {
            val off = if (Money.isPositive(i.discount)) " − " + money(i.discount) else ""
            lines += i.itemName + " · " + Format.qty(i.quantity, i.unit) + " × " + money(i.rate) + off
        }
        if (full.items.count { !it.isDeleted } > TOP) lines += line(c, R.string.hudhud_l_total, n(full.items.count { !it.isDeleted }))
        if (Money.isPositive(s.discountAmount)) lines += line(c, R.string.pos_discount, money(s.discountAmount))
        if (Money.isPositive(s.taxAmount)) {
            val rate = s.taxPercent?.takeIf { it > 0.0 }?.let { " " + Format.percent(it) + "%" } ?: ""
            lines += line(c.getString(R.string.pos_tax_total) + rate, money(s.taxAmount))
        }
        if (Money.isNotZero(s.roundOff)) lines += line(c, R.string.pos_round_off, money(s.roundOff))
        lines += line(c, R.string.pos_subtotal, money(s.subtotal))
        lines += line(c, R.string.pos_paid, money(s.paidTotal))
        val secrets = ArrayList<String>()
        for (p in full.payments) {
            val ref = p.reference?.trim()?.takeIf { it.isNotEmpty() }
            lines += line(PaymentMethod.label(AndroidWords(c), p.method), money(p.amount)) + (ref?.let { " · " + Format.ltr(it) } ?: "")
            if (ref != null) secrets += ref
        }
        val credit = s.creditAmount
        if (Money.isPositive(credit)) lines += line(c, R.string.pos_still_due, money(credit))
        s.note?.trim()?.takeIf { it.isNotEmpty() }?.let { lines += it }
        return Answer(Mood.CALM, label = title, figure = money(s.grandTotal), lines = lines,
            rows = more.map { r ->
                Row(0, r.saleNumber, money(r.grandTotal), listOf(customerOf(c, r.customerName), whenOf(c, r.timestamp)).joinToString(" · "), open = open)
            },
            open = open, money = true, secrets = secrets)
    }

    /** What each cashier rang up over a span (today when none was said): his bills and their total; a null name is the owner. */
    suspend fun byCashier(c: Context, dao: KhataDao, window: VoiceQuestion.Window?): Answer {
        val w = window ?: VoiceQuestion.Window.TODAY
        val title = heading(c, R.string.hudhud_t_by_cashier, w)
        if (!reports()) return ownerOnly(c, title)
        val (from, to) = Hudhud.span(w)
        val open = CounterSaleActivity::class.java
        val all = dao.salesByCashierBetween(from, to)
        if (all.isEmpty()) return none(c, title, open)
        return Answer(Mood.CALM, label = title, figure = money(all.sumOf { it.total }),
            lines = listOf(line(c, R.string.hudhud_l_bills, n(all.sumOf { it.tickets }))),
            rows = all.take(TOP).map { r -> Row(0, who(c, r.cashier), money(r.total), line(c, R.string.hudhud_l_bills, n(r.tickets)), open = open) },
            open = open, money = true)
    }

    /**
     * The day's discounts, tax, credit or ways of paying, by the day report's own rule ([ZReport.compute]): the discount
     * is the bill's and the lines', tax what was put on and what the prices hold, credit what the bills left unpaid, the
     * ways what the payments say. A span with more bills than [MAX_TICKETS] is not added up here (the day report is).
     */
    suspend fun dayFigures(c: Context, dao: KhataDao, facet: Hudhud.CounterFacet, window: VoiceQuestion.Window?): Answer {
        val w = window ?: VoiceQuestion.Window.TODAY
        val title = heading(c, when (facet) {
            Hudhud.CounterFacet.DISCOUNTS -> R.string.hudhud_t_discounts
            Hudhud.CounterFacet.TAX -> R.string.hudhud_t_tax
            Hudhud.CounterFacet.ON_ACCOUNT -> R.string.hudhud_t_on_account
            else -> R.string.hudhud_t_pay_ways
        }, w)
        if (!reports()) return ownerOnly(c, title)
        val open = DayBookActivity::class.java
        val (from, to) = Hudhud.span(w)
        if (dao.salesBetween(from, to).size > MAX_TICKETS) return tooMany(c, title, open)
        val z = ZReport.compute(dao, from, to)
        if (z.saleCount == 0) return none(c, title, open)
        val bills = line(c, R.string.hudhud_l_bills, n(z.saleCount))
        return when (facet) {
            Hudhud.CounterFacet.DISCOUNTS ->
                Answer(Mood.CALM, label = title, figure = money(z.discounts), lines = listOf(bills), open = open, money = true)
            Hudhud.CounterFacet.TAX -> Answer(Mood.CALM, label = title, figure = money(z.tax),
                lines = listOf(bills, line(c, R.string.pos_round_off, money(dao.roundOffBetween(from, to)))), open = open, money = true)
            Hudhud.CounterFacet.ON_ACCOUNT ->
                Answer(Mood.CALM, label = title, figure = money(z.onAccount),
                    lines = listOf(bills, line(c, R.string.hudhud_l_total, money(z.counterSales))), open = open, money = true)
            else -> Answer(Mood.CALM, label = title, figure = money(z.byMethod.values.sum()),
                lines = listOf(bills) + z.byMethod.map { (m, v) -> line(PaymentMethod.label(AndroidWords(c), m), money(v)) },
                open = open, money = true)
        }
    }

    // ------------------------------------------------------------------------------------------------ cancelled

    /**
     * Bills and returns that were voided over a span (today when none was said), newest first: its number, the amount,
     * who rang it up, who voided it (from the Who did what row), and the reason written. The sales' voidReason/voidedAt
     * and the returns' are read by [KhataDao.voidedSalesBetween] and [KhataDao.voidedReturnsBetween].
     */
    suspend fun voided(c: Context, dao: KhataDao, window: VoiceQuestion.Window?): Answer {
        val w = window ?: VoiceQuestion.Window.TODAY
        val title = heading(c, R.string.hudhud_t_voided, w)
        if (!reports()) return ownerOnly(c, title)
        val (from, to) = Hudhud.span(w)
        val open = CounterSaleActivity::class.java
        val sales = dao.voidedSalesBetween(from, to, 200)
        val returns = dao.voidedReturnsBetween(from, to, 200)
        val all = (sales + returns).sortedByDescending { it.whenAt }
        if (all.isEmpty()) return none(c, title, open)
        return Answer(Mood.CALM, label = title, figure = n(all.size),
            lines = listOf(
                line(c.getString(R.string.pos_voided) + " · " + c.getString(R.string.hudhud_l_bills), n(sales.size) + " · " + money(sales.sumOf { it.amount })),
                line(c.getString(R.string.pos_voided) + " · " + c.getString(R.string.pos_returns), n(returns.size) + " · " + money(returns.sumOf { it.amount }))
            ),
            rows = all.take(TOP).map { d ->
                val reason = d.reason?.trim()?.takeIf { it.isNotEmpty() } ?: c.getString(R.string.hudhud_l_no_reason)
                Row(0, d.docNumber, money(d.amount),
                    listOf(customerOf(c, d.customerName), c.getString(R.string.hudhud_l_by) + " " + who(c, d.rungBy),
                        c.getString(R.string.hudhud_l_cancelled_by) + " " + who(c, d.voidedBy), reason, whenOf(c, d.whenAt)).joinToString(" · "),
                    open = open)
            },
            open = open, money = true)
    }

    // ------------------------------------------------------------------------------------------------ goods that came back

    /**
     * Why goods came back over a span (today when none was said): the reasons the cashiers wrote, the commonest first
     * ("Damaged" and "damaged " are one), and the newest returns with their number, who took them and the note.
     * [KhataDao.returnsWhyBetween] gives each return's reason, note and createdBy.
     */
    suspend fun returnReasons(c: Context, dao: KhataDao, window: VoiceQuestion.Window?): Answer {
        val w = window ?: VoiceQuestion.Window.TODAY
        val title = heading(c, R.string.hudhud_t_return_reasons, w)
        if (!reports()) return ownerOnly(c, title)
        val (from, to) = Hudhud.span(w)
        val open = CounterSaleActivity::class.java
        val rows = dao.returnsWhyBetween(from, to, RETURN_READ)
        if (rows.isEmpty()) return none(c, title, open)
        val tally = HudhudCounter.tallyReasons(rows.map { it.reason to it.amount })
        return Answer(Mood.CALM, label = title, figure = n(rows.size),
            lines = tally.take(TOP).map { t ->
                line(t.reason.ifEmpty { c.getString(R.string.hudhud_l_no_reason) }, n(t.count) + " · " + money(t.worth))
            },
            rows = rows.take(TOP).map { r ->
                Row(0, r.reason.trim().ifEmpty { c.getString(R.string.hudhud_l_no_reason) }, money(r.amount),
                    listOfNotNull(r.docNumber, customerOf(c, r.customerName), who(c, r.rungBy), r.note?.trim()?.takeIf { it.isNotEmpty() }, whenOf(c, r.whenAt))
                        .joinToString(" · "), open = open)
            },
            open = open, money = true)
    }

    /**
     * How the money went back over a span (today when none was said): cash, bank, wallet or kept on the customer's
     * account, each with what it came to, by the return's own refundMethod and refundAmount.
     */
    suspend fun returnWays(c: Context, dao: KhataDao, window: VoiceQuestion.Window?): Answer {
        val w = window ?: VoiceQuestion.Window.TODAY
        val title = heading(c, R.string.hudhud_t_return_ways, w)
        if (!reports()) return ownerOnly(c, title)
        val (from, to) = Hudhud.span(w)
        val open = CounterSaleActivity::class.java
        val rows = dao.returnsWhyBetween(from, to, RETURN_READ)
        if (rows.isEmpty()) return none(c, title, open)
        val byWay = rows.groupBy { it.refundMethod }.map { (m, g) -> Triple(m, g.size, g.sumOf { it.refundAmount }) }
            .sortedByDescending { it.third }
        return Answer(Mood.CALM, label = title, figure = money(rows.sumOf { it.refundAmount }),
            lines = listOf(line(c, R.string.pos_returns, n(rows.size) + " · " + money(rows.sumOf { it.amount }))) +
                byWay.map { (m, count, sum) -> line(wayWord(c, m), n(count) + " · " + money(sum)) },
            open = open, money = true)
    }

    /** What came back over a span (today when none was said): the goods of the finished returns, by name and unit, never across units. */
    suspend fun returnGoods(c: Context, dao: KhataDao, window: VoiceQuestion.Window?): Answer {
        val w = window ?: VoiceQuestion.Window.TODAY
        val title = heading(c, R.string.hudhud_t_return_goods, w)
        if (!reports()) return ownerOnly(c, title)
        val (from, to) = Hudhud.span(w)
        val open = CounterSaleActivity::class.java
        val lines = dao.returnedGoodsBetween(from, to, GOODS_READ)
        if (lines.isEmpty()) return none(c, title, open)
        if (lines.size >= GOODS_READ) return tooMany(c, title, open)
        val tally = HudhudCounter.tallyGoods(lines.map { HudhudCounter.GoodsLine(it.itemName, it.unit, it.quantity, it.quantity * it.rate) }, TOP)
        return Answer(Mood.CALM, label = title, figure = n(HudhudCounter.tallyGoods(lines.map { HudhudCounter.GoodsLine(it.itemName, it.unit, it.quantity, it.quantity * it.rate) }).size),
            rows = tally.map { t -> Row(0, t.name, Format.qty(t.qty, t.unit), money(t.worth), open = open) },
            open = open, money = true)
    }

    // ------------------------------------------------------------------------------------------------ the till

    /**
     * The shifts opened over a span (this week when none was said), newest first: who, when it opened and closed, the
     * hours, the float it started with, what was counted against what the tickets said, and the difference ([Shift],
     * [ShiftMath]). With [onlyShort] only the closed shifts whose count differed, the biggest difference first.
     */
    suspend fun shifts(c: Context, dao: KhataDao, window: VoiceQuestion.Window?, onlyShort: Boolean): Answer {
        val w = window ?: VoiceQuestion.Window.THIS_WEEK
        val title = heading(c, R.string.shift_title, w)
        if (!reports()) return ownerOnly(c, title)
        val (from, to) = Hudhud.span(w)
        val open = ShiftActivity::class.java
        val all = dao.shiftsBetween(from, to).let { list -> if (onlyShort) list.filter { s -> s.difference?.let { Money.isNotZero(it) } == true } else list }
        if (all.isEmpty()) return none(c, title, open)
        val ordered = if (onlyShort) all.sortedByDescending { abs(it.difference ?: 0.0) } else all.sortedByDescending { it.openedAt }
        return Answer(Mood.CALM, label = title, figure = n(all.size),
            rows = ordered.take(TOP).map { s ->
                val figure = when {
                    s.closedAt == null -> c.getString(R.string.hudhud_l_still_open)
                    s.difference == null -> ""
                    Money.isZero(s.difference!!) -> c.getString(R.string.dayclose_match)
                    Money.isNegative(s.difference!!) -> c.getString(R.string.dayclose_short, money(abs(s.difference!!)))
                    else -> c.getString(R.string.dayclose_over, money(s.difference!!))
                }
                val sub = listOfNotNull(
                    whenOf(c, s.openedAt),
                    s.closedAt?.let { c.getString(R.string.shift_hours, Format.qty(s.hours ?: 0.0, null)) },
                    line(c, R.string.hudhud_l_starting_cash, money(s.openingFloat)),
                    if (s.counted != null && s.expected != null)
                        line(c, R.string.stock_counted_hint, money(s.counted!!)) + " · " + line(c, R.string.hudhud_l_books_said, money(s.expected!!)) else null,
                    s.note?.trim()?.takeIf { it.isNotEmpty() }
                ).joinToString(" · ")
                Row(0, who(c, s.user), figure, sub, open = open)
            },
            open = open, money = true)
    }

    /**
     * The drawer's count when the day was closed: the newest close when no day was said, else the day's own (yesterday's
     * is the usual question). What was counted, what the books said it should be, the difference in words, the
     * opening cash, the cash in and out that day, the note, and when it was closed ([DayClose]).
     */
    suspend fun dayCount(c: Context, dao: KhataDao, window: VoiceQuestion.Window?): Answer {
        val title = heading(c, R.string.hudhud_t_day_count, window)
        if (!reports()) return ownerOnly(c, title)
        val open = CashbookActivity::class.java
        val closes = if (window == null) listOfNotNull(dao.lastDayClose()) else {
            val (from, to) = Hudhud.span(window)
            dao.dayClosesBetween(DayCloseMath.dayOf(from), DayCloseMath.dayOf(to - 1) + 1, TOP)
        }.sortedByDescending { it.day }
        val first = closes.firstOrNull()
        if (first == null) {
            val words = if (window == VoiceQuestion.Window.YESTERDAY) c.getString(R.string.z_yesterday_open) else c.getString(R.string.hudhud_counter_none)
            return Answer(Mood.CALM, label = title, lines = listOf(words), open = open)
        }
        val diff = first.difference
        val state = when {
            Money.isZero(diff) -> c.getString(R.string.dayclose_match)
            Money.isNegative(diff) -> c.getString(R.string.dayclose_short, money(abs(diff)))
            else -> c.getString(R.string.dayclose_over, money(diff))
        }
        return Answer(if (Money.isZero(diff)) Mood.GOOD else Mood.CARE, label = title, figure = money(first.counted),
            lines = listOfNotNull(
                state,
                line(c, R.string.hudhud_l_books_said, money(first.expected)),
                line(c, R.string.dayclose_opening, money(first.opening)),
                line(c, R.string.z_cash_in, money(first.cashIn)),
                line(c, R.string.z_cash_out, money(first.cashOut)),
                first.note?.trim()?.takeIf { it.isNotEmpty() },
                whenOf(c, first.closedAt)
            ),
            rows = closes.drop(1).map { d ->
                Row(0, whenOf(c, d.closedAt), money(d.counted), if (Money.isZero(d.difference)) c.getString(R.string.dayclose_match)
                    else if (Money.isNegative(d.difference)) c.getString(R.string.dayclose_short, money(abs(d.difference)))
                    else c.getString(R.string.dayclose_over, money(d.difference)), open = open)
            },
            open = open, money = true)
    }

    // ------------------------------------------------------------------------------------------------ points and stock

    /**
     * Loyalty points. A customer named: what he has left now, and what he earned and spent over the span (the whole
     * book when none was said). Nobody named: who earned most over the span (this month when none was said), by the
     * rows of [KhataDao.pointRowsBetween] (points, timestamp); each a tap that opens him.
     */
    suspend fun loyalty(c: Context, dao: KhataDao, ids: List<Long>, window: VoiceQuestion.Window?): Answer {
        val w = window ?: if (ids.isEmpty()) VoiceQuestion.Window.THIS_MONTH else VoiceQuestion.Window.ALL_TIME
        val title = heading(c, R.string.loyalty_section, w)
        if (!reports()) return ownerOnly(c, title)
        val open = KhataActivity::class.java
        val (from, to) = Hudhud.span(w)
        if (ids.size == 1) {
            val id = ids.first()
            val name = dao.getPartyName(id) ?: return none(c, title, open)
            val mine = dao.pointRowsBetween(from, to, POINT_READ).filter { it.partyId == id }
            val earned = mine.filter { it.points > 0.0 }.sumOf { it.points }
            val spent = -mine.filter { it.points < 0.0 }.sumOf { it.points }
            return Answer(Mood.CALM, label = name, figure = Format.qty(dao.loyaltyBalance(id), null),
                lines = listOf(c.getString(R.string.hudhud_l_points_left), line(c, R.string.hudhud_l_earned, Format.qty(earned, null)),
                    line(c, R.string.hudhud_l_spent, Format.qty(spent, null))), openParty = id)
        }
        if (ids.size > 1) {
            return Answer(Mood.CALM, label = title, rows = ids.take(TOP).map { id ->
                Row(id, dao.getPartyName(id) ?: "", "", null, ask = Hudhud.Ask.Counter(Hudhud.CounterFacet.LOYALTY, window, listOf(id)))
            }, open = open)
        }
        val rows = dao.pointRowsBetween(from, to, POINT_READ)
        if (rows.isEmpty()) return none(c, title, open)
        if (rows.size >= POINT_READ) return tooMany(c, title, open)
        val tally = HudhudCounter.tallyPoints(rows.map { it.partyId to it.points })
        return Answer(Mood.CALM, label = title, figure = Format.qty(tally.sumOf { it.earned }, null),
            lines = listOf(line(c, R.string.hudhud_l_spent, Format.qty(tally.sumOf { it.spent }, null))),
            rows = tally.take(TOP).map { t ->
                Row(t.partyId, dao.getPartyName(t.partyId) ?: "", Format.qty(t.earned, null), line(c, R.string.hudhud_l_spent, Format.qty(t.spent, null)))
            },
            open = open)
    }

    /**
     * Goods written off over a span (this month when none was said): damaged, expired or thrown out, theft or lost —
     * by product and unit, the quantity that left the shelf. Never a money value (no cost is guessed). The row's reason
     * and moment are the adjustment's own ([StockAdjustment.reason], timestamp, quantity, unit).
     */
    suspend fun writtenOff(c: Context, dao: KhataDao, window: VoiceQuestion.Window?): Answer {
        val w = window ?: VoiceQuestion.Window.THIS_MONTH
        val title = heading(c, R.string.hudhud_t_written_off, w)
        if (!reports()) return ownerOnly(c, title)
        val (from, to) = Hudhud.span(w)
        val open = ProductsActivity::class.java
        val rows = dao.adjustmentsBetween(from, to, ADJUST_READ)
        if (rows.size >= ADJUST_READ) return tooMany(c, title, open)
        val lost = HudhudCounter.tallyLost(rows.map { HudhudCounter.AdjustLine(it.productId, it.quantity, it.unit, it.reason) })
        if (lost.isEmpty()) return none(c, title, open)
        return Answer(Mood.CALM, label = title, figure = n(lost.size),
            rows = lost.take(TOP).map { t ->
                Row(0, dao.productById(t.productId)?.name ?: "", Format.qty(t.lost, t.unit), reasonWord(c, t.reason), open = open)
            },
            open = open)
    }

    /**
     * The stock's own corrections over a span (this month when none was said), newest first: the product, the signed
     * quantity, the reason, who made it and its note ([StockAdjustment]; a count's differences and a transfer included).
     */
    suspend fun stockChanges(c: Context, dao: KhataDao, window: VoiceQuestion.Window?): Answer {
        val w = window ?: VoiceQuestion.Window.THIS_MONTH
        val title = heading(c, R.string.hudhud_t_stock_changes, w)
        if (!reports()) return ownerOnly(c, title)
        val (from, to) = Hudhud.span(w)
        val open = ProductsActivity::class.java
        val rows = dao.adjustmentsBetween(from, to, ADJUST_READ)
        if (rows.isEmpty()) return none(c, title, open)
        return Answer(Mood.CALM, label = title, figure = if (rows.size < ADJUST_READ) n(rows.size) else null,
            rows = rows.take(TOP).map { a ->
                val signed = (if (a.quantity > 0.0) "+" else "") + Format.qty(a.quantity, a.unit)
                Row(0, dao.productById(a.productId)?.name ?: "", signed,
                    listOfNotNull(reasonWord(c, a.reason), who(c, a.createdBy), a.note?.trim()?.takeIf { it.isNotEmpty() }, whenOf(c, a.timestamp))
                        .joinToString(" · "), open = open)
            },
            open = open)
    }

    /**
     * The newest stock count: when, who counted, how many products differed and the biggest differences, each with what
     * the book said and what was counted ([StockAdjustment.bookQty] and countedQty, which only a count writes).
     */
    suspend fun lastCount(c: Context, dao: KhataDao): Answer {
        val title = c.getString(R.string.hudhud_t_last_count)
        if (!reports()) return ownerOnly(c, title)
        val open = ProductsActivity::class.java
        val rows = dao.lastCountRows()
        val first = rows.firstOrNull() ?: return none(c, title, open)
        return Answer(Mood.CALM, label = title, figure = n(rows.size),
            lines = listOf(whenOf(c, first.timestamp), line(c, R.string.hudhud_l_by, who(c, first.createdBy))),
            rows = rows.sortedByDescending { abs(it.quantity) }.take(TOP).map { a ->
                val book = a.bookQty?.let { Format.qty(it, a.unit) } ?: ""
                val counted = a.countedQty?.let { Format.qty(it, a.unit) } ?: ""
                Row(0, dao.productById(a.productId)?.name ?: "", (if (a.quantity > 0.0) "+" else "") + Format.qty(a.quantity, a.unit),
                    c.getString(R.string.hudhud_l_book_counted, book, counted), open = open)
            },
            open = open)
    }

    private const val TOP = 5
    /** A span with more finished bills than this is not added up on the phone ([ZReport] reads each bill's lines). */
    private const val MAX_TICKETS = 400
    private const val RETURN_READ = 500
    private const val GOODS_READ = 3_000
    private const val POINT_READ = 5_000
    private const val ADJUST_READ = 2_000
}
