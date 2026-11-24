package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.AnswerCard.Mood
import com.innovation313.roshankhata.data.AnswerCard.Row
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.WP
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
internal object AnswerCounterFacts {


    // Every gate is asked through AnswerGate (the catalog holds each fact to the gate of the screen that shows it).
    private fun reports() = AnswerGate.reports()

    private fun ownerOnly(c: AnswerEnv, label: String) =
        AnswerCard(Mood.CALM, label = label, lines = listOf(c.getString(W.hudhud_owner_only)))

    private fun line(label: String, value: String) = label + ": " + Format.isolate(value)

    private fun line(c: AnswerEnv, label: String, value: String) = line(c.getString(label), value)

    private fun whenOf(c: AnswerEnv, t: Long) = WhenWords.whenWords(t, c.now(), c.words)

    private fun n(count: Int): String = count.toString()

    private fun money(v: Double) = AnswerBook.money(v)

    private fun heading(c: AnswerEnv, title: String, window: VoiceQuestion.Window?): String =
        if (window == null) c.getString(title) else c.getString(title) + " · " + c.getString(AnswerBook.windowWord(window))

    /** Nothing written in this time. */
    private fun none(c: AnswerEnv, label: String, open: ScreenId): AnswerCard =
        AnswerCard(Mood.CALM, label = label, lines = listOf(c.getString(W.hudhud_counter_none)), open = open)

    /** More than a phone adds up at once: the span is too wide for this question. */
    private fun tooMany(c: AnswerEnv, label: String, open: ScreenId): AnswerCard =
        AnswerCard(Mood.CALM, label = label, lines = listOf(c.getString(W.hudhud_counter_too_many)), open = open)

    /** A person's name as the books keep it: a null name is the owner. */
    private fun who(c: AnswerEnv, name: String?): String = name?.takeIf { it.isNotBlank() } ?: c.getString(W.access_owner)

    private fun customerOf(c: AnswerEnv, name: String?): String = name?.takeIf { it.isNotBlank() } ?: c.getString(W.pos_walk_in)

    private fun wayWord(c: AnswerEnv, method: String): String =
        if (method == SaleReturn.ACCOUNT) c.getString(W.pos_refund_account) else PaymentMethod.label(c.words, method)

    private fun reasonWord(c: AnswerEnv, reason: String): String = when (reason) {
        AdjustReason.COUNT -> c.getString(W.adjust_reason_count)
        AdjustReason.DAMAGE -> c.getString(W.adjust_reason_damage)
        AdjustReason.EXPIRED -> c.getString(W.adjust_reason_expired)
        AdjustReason.THEFT -> c.getString(W.adjust_reason_theft)
        AdjustReason.OTHER -> c.getString(W.adjust_reason_other)
        else -> reason
    }

    /** A facet's own name, without any span (the chips that offer the same question over other spans say it). */
    fun titleOf(c: AnswerEnv, facet: Hudhud.CounterFacet): String = c.getString(when (facet) {
        Hudhud.CounterFacet.BIGGEST -> W.hudhud_t_biggest
        Hudhud.CounterFacet.LAST_TICKET -> W.hudhud_t_last_ticket
        Hudhud.CounterFacet.BY_CASHIER -> W.hudhud_t_by_cashier
        Hudhud.CounterFacet.DISCOUNTS -> W.hudhud_t_discounts
        Hudhud.CounterFacet.TAX -> W.hudhud_t_tax
        Hudhud.CounterFacet.ON_ACCOUNT -> W.hudhud_t_on_account
        Hudhud.CounterFacet.PAY_WAYS -> W.hudhud_t_pay_ways
        Hudhud.CounterFacet.VOIDED -> W.hudhud_t_voided
        Hudhud.CounterFacet.RETURN_REASONS -> W.hudhud_t_return_reasons
        Hudhud.CounterFacet.RETURN_WAYS -> W.hudhud_t_return_ways
        Hudhud.CounterFacet.RETURN_GOODS -> W.hudhud_t_return_goods
        Hudhud.CounterFacet.SHIFTS, Hudhud.CounterFacet.SHIFT_SHORT -> W.shift_title
        Hudhud.CounterFacet.DAY_COUNT -> W.hudhud_t_day_count
        Hudhud.CounterFacet.LOYALTY -> W.loyalty_section
        Hudhud.CounterFacet.WRITTEN_OFF -> W.hudhud_t_written_off
        Hudhud.CounterFacet.STOCK_CHANGES -> W.hudhud_t_stock_changes
        Hudhud.CounterFacet.LAST_COUNT -> W.hudhud_t_last_count
    })

    /** The one question the sheet asks, sent to the reader of its facet. */
    suspend fun answer(c: AnswerEnv, dao: KhataDao, ask: Hudhud.Ask.Counter): AnswerCard {
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
    suspend fun lastTicket(c: AnswerEnv, dao: KhataDao): AnswerCard {
        val title = c.getString(W.hudhud_t_last_ticket)
        if (!reports()) return ownerOnly(c, title)
        return ticket(c, dao, dao.newestSaleId(), title, emptyList())
    }

    /** The biggest bill of a span (today when none was said), whole, with the next biggest under it. */
    suspend fun biggest(c: AnswerEnv, dao: KhataDao, window: VoiceQuestion.Window?): AnswerCard {
        val w = window ?: VoiceQuestion.Window.TODAY
        val title = heading(c, W.hudhud_t_biggest, w)
        if (!reports()) return ownerOnly(c, title)
        val (from, to) = Hudhud.span(w, c.now())
        val top = dao.biggestSalesBetween(from, to, TOP + 1)
        return ticket(c, dao, top.firstOrNull()?.id, title, top.drop(1))
    }

    /**
     * One finished bill as the counter wrote it: its number, who it was for and who rang it up, its goods with their
     * rate and discount, the bill's own discount, tax, round-off, what was paid and how (a payment's reference is shown
     * and never said aloud), what is still to pay, and its note. [more] are the other bills of the list (as rows).
     */
    suspend fun ticket(c: AnswerEnv, dao: KhataDao, id: Long?, title: String, more: List<SaleSummary>): AnswerCard {
        if (!reports()) return ownerOnly(c, title)
        val open = ScreenId.COUNTER_SALE
        val full = id?.let { dao.saleWithLines(it) } ?: return none(c, title, open)
        val s = full.sale
        val lines = ArrayList<String>()
        lines += listOf(s.saleNumber, customerOf(c, s.customerName), whenOf(c, s.timestamp)).joinToString(" · ")
        lines += line(c, W.hudhud_l_by, who(c, s.createdBy))
        for (i in full.items.filter { !it.isDeleted }.take(TOP)) {
            val off = if (Money.isPositive(i.discount)) " − " + money(i.discount) else ""
            // The line's own rate of tax (schema 36), as the counter's line shows it ("Tax %: 18"; the catalog's last column GAP).
            val tax = i.taxRate?.takeIf { it > 0.0 }?.let { " · " + c.getString(W.pos_tax) + ": " + Format.percent(it) } ?: ""
            lines += i.itemName + " · " + Format.qty(i.quantity, i.unit) + " × " + money(i.rate) + off + tax
        }
        if (full.items.count { !it.isDeleted } > TOP) lines += line(c, W.hudhud_l_total, n(full.items.count { !it.isDeleted }))
        if (Money.isPositive(s.discountAmount)) lines += line(c, W.pos_discount, money(s.discountAmount))
        if (Money.isPositive(s.taxAmount)) {
            val rate = s.taxPercent?.takeIf { it > 0.0 }?.let { " " + Format.percent(it) + "%" } ?: ""
            lines += line(c.getString(W.pos_tax_total) + rate, money(s.taxAmount))
        }
        if (Money.isNotZero(s.roundOff)) lines += line(c, W.pos_round_off, money(s.roundOff))
        lines += line(c, W.pos_subtotal, money(s.subtotal))
        lines += line(c, W.pos_paid, money(s.paidTotal))
        val secrets = ArrayList<String>()
        for (p in full.payments) {
            val ref = p.reference?.trim()?.takeIf { it.isNotEmpty() }
            lines += line(PaymentMethod.label(c.words, p.method), money(p.amount)) + (ref?.let { " · " + Format.ltr(it) } ?: "")
            if (ref != null) secrets += ref
        }
        val credit = s.creditAmount
        if (Money.isPositive(credit)) lines += line(c, W.pos_still_due, money(credit))
        s.note?.trim()?.takeIf { it.isNotEmpty() }?.let { lines += it }
        return AnswerCard(Mood.CALM, label = title, figure = money(s.grandTotal), lines = lines,
            rows = more.map { r ->
                Row(0, r.saleNumber, money(r.grandTotal), listOf(customerOf(c, r.customerName), whenOf(c, r.timestamp)).joinToString(" · "), open = open)
            },
            open = open, money = true, secrets = secrets)
    }

    /** What each cashier rang up over a span (today when none was said): his bills and their total; a null name is the owner. */
    suspend fun byCashier(c: AnswerEnv, dao: KhataDao, window: VoiceQuestion.Window?): AnswerCard {
        val w = window ?: VoiceQuestion.Window.TODAY
        val title = heading(c, W.hudhud_t_by_cashier, w)
        if (!reports()) return ownerOnly(c, title)
        val (from, to) = Hudhud.span(w, c.now())
        val open = ScreenId.COUNTER_SALE
        val all = dao.salesByCashierBetween(from, to)
        if (all.isEmpty()) return none(c, title, open)
        return AnswerCard(Mood.CALM, label = title, figure = money(all.sumOf { it.total }),
            lines = listOf(line(c, W.hudhud_l_bills, n(all.sumOf { it.tickets }))),
            rows = all.take(TOP).map { r -> Row(0, who(c, r.cashier), money(r.total), line(c, W.hudhud_l_bills, n(r.tickets)), open = open) },
            open = open, money = true)
    }

    /**
     * The day's discounts, tax, credit or ways of paying, by the day report's own rule ([ZReport.compute]): the discount
     * is the bill's and the lines', tax what was put on and what the prices hold, credit what the bills left unpaid, the
     * ways what the payments say. A span with more bills than [MAX_TICKETS] is not added up here (the day report is).
     */
    suspend fun dayFigures(c: AnswerEnv, dao: KhataDao, facet: Hudhud.CounterFacet, window: VoiceQuestion.Window?): AnswerCard {
        val w = window ?: VoiceQuestion.Window.TODAY
        val title = heading(c, when (facet) {
            Hudhud.CounterFacet.DISCOUNTS -> W.hudhud_t_discounts
            Hudhud.CounterFacet.TAX -> W.hudhud_t_tax
            Hudhud.CounterFacet.ON_ACCOUNT -> W.hudhud_t_on_account
            else -> W.hudhud_t_pay_ways
        }, w)
        if (!reports()) return ownerOnly(c, title)
        val open = ScreenId.DAY_BOOK
        val (from, to) = Hudhud.span(w, c.now())
        if (dao.salesBetween(from, to).size > MAX_TICKETS) return tooMany(c, title, open)
        val z = ZReport.compute(dao, from, to)
        if (z.saleCount == 0) return none(c, title, open)
        val bills = line(c, W.hudhud_l_bills, n(z.saleCount))
        return when (facet) {
            Hudhud.CounterFacet.DISCOUNTS ->
                AnswerCard(Mood.CALM, label = title, figure = money(z.discounts), lines = listOf(bills), open = open, money = true)
            Hudhud.CounterFacet.TAX -> AnswerCard(Mood.CALM, label = title, figure = money(z.tax),
                lines = listOf(bills, line(c, W.pos_round_off, money(dao.roundOffBetween(from, to)))), open = open, money = true)
            Hudhud.CounterFacet.ON_ACCOUNT ->
                AnswerCard(Mood.CALM, label = title, figure = money(z.onAccount),
                    lines = listOf(bills, line(c, W.hudhud_l_total, money(z.counterSales))), open = open, money = true)
            else -> AnswerCard(Mood.CALM, label = title, figure = money(z.byMethod.values.sum()),
                lines = listOf(bills) + z.byMethod.map { (m, v) -> line(PaymentMethod.label(c.words, m), money(v)) },
                open = open, money = true)
        }
    }

    // ------------------------------------------------------------------------------------------------ cancelled

    /**
     * Bills and returns that were voided over a span (today when none was said), newest first: its number, the amount,
     * who rang it up, who voided it (from the Who did what row), and the reason written. The sales' voidReason/voidedAt
     * and the returns' are read by [KhataDao.voidedSalesBetween] and [KhataDao.voidedReturnsBetween].
     */
    suspend fun voided(c: AnswerEnv, dao: KhataDao, window: VoiceQuestion.Window?): AnswerCard {
        val w = window ?: VoiceQuestion.Window.TODAY
        val title = heading(c, W.hudhud_t_voided, w)
        if (!reports()) return ownerOnly(c, title)
        val (from, to) = Hudhud.span(w, c.now())
        val open = ScreenId.COUNTER_SALE
        val sales = dao.voidedSalesBetween(from, to, 200)
        val returns = dao.voidedReturnsBetween(from, to, 200)
        val all = (sales + returns).sortedByDescending { it.whenAt }
        if (all.isEmpty()) return none(c, title, open)
        return AnswerCard(Mood.CALM, label = title, figure = n(all.size),
            lines = listOf(
                line(c.getString(W.pos_voided) + " · " + c.getString(W.hudhud_l_bills), n(sales.size) + " · " + money(sales.sumOf { it.amount })),
                line(c.getString(W.pos_voided) + " · " + c.getString(W.pos_returns), n(returns.size) + " · " + money(returns.sumOf { it.amount }))
            ),
            rows = all.take(TOP).map { d ->
                val reason = d.reason?.trim()?.takeIf { it.isNotEmpty() } ?: c.getString(W.hudhud_l_no_reason)
                Row(0, d.docNumber, money(d.amount),
                    listOf(customerOf(c, d.customerName), c.getString(W.hudhud_l_by) + " " + who(c, d.rungBy),
                        c.getString(W.hudhud_l_cancelled_by) + " " + who(c, d.voidedBy), reason, whenOf(c, d.whenAt)).joinToString(" · "),
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
    suspend fun returnReasons(c: AnswerEnv, dao: KhataDao, window: VoiceQuestion.Window?): AnswerCard {
        val w = window ?: VoiceQuestion.Window.TODAY
        val title = heading(c, W.hudhud_t_return_reasons, w)
        if (!reports()) return ownerOnly(c, title)
        val (from, to) = Hudhud.span(w, c.now())
        val open = ScreenId.COUNTER_SALE
        val rows = dao.returnsWhyBetween(from, to, RETURN_READ)
        if (rows.isEmpty()) return none(c, title, open)
        val tally = HudhudCounter.tallyReasons(rows.map { it.reason to it.amount })
        return AnswerCard(Mood.CALM, label = title, figure = n(rows.size),
            lines = tally.take(TOP).map { t ->
                line(t.reason.ifEmpty { c.getString(W.hudhud_l_no_reason) }, n(t.count) + " · " + money(t.worth))
            },
            rows = rows.take(TOP).map { r ->
                Row(0, r.reason.trim().ifEmpty { c.getString(W.hudhud_l_no_reason) }, money(r.amount),
                    listOfNotNull(r.docNumber, customerOf(c, r.customerName), who(c, r.rungBy), r.note?.trim()?.takeIf { it.isNotEmpty() }, whenOf(c, r.whenAt))
                        .joinToString(" · "), open = open)
            },
            open = open, money = true)
    }

    /**
     * How the money went back over a span (today when none was said): cash, bank, wallet or kept on the customer's
     * account, each with what it came to, by the return's own refundMethod and refundAmount.
     */
    suspend fun returnWays(c: AnswerEnv, dao: KhataDao, window: VoiceQuestion.Window?): AnswerCard {
        val w = window ?: VoiceQuestion.Window.TODAY
        val title = heading(c, W.hudhud_t_return_ways, w)
        if (!reports()) return ownerOnly(c, title)
        val (from, to) = Hudhud.span(w, c.now())
        val open = ScreenId.COUNTER_SALE
        val rows = dao.returnsWhyBetween(from, to, RETURN_READ)
        if (rows.isEmpty()) return none(c, title, open)
        val byWay = rows.groupBy { it.refundMethod }.map { (m, g) -> Triple(m, g.size, g.sumOf { it.refundAmount }) }
            .sortedByDescending { it.third }
        return AnswerCard(Mood.CALM, label = title, figure = money(rows.sumOf { it.refundAmount }),
            lines = listOf(line(c, W.pos_returns, n(rows.size) + " · " + money(rows.sumOf { it.amount }))) +
                byWay.map { (m, count, sum) -> line(wayWord(c, m), n(count) + " · " + money(sum)) },
            open = open, money = true)
    }

    /** What came back over a span (today when none was said): the goods of the finished returns, by name and unit, never across units. */
    suspend fun returnGoods(c: AnswerEnv, dao: KhataDao, window: VoiceQuestion.Window?): AnswerCard {
        val w = window ?: VoiceQuestion.Window.TODAY
        val title = heading(c, W.hudhud_t_return_goods, w)
        if (!reports()) return ownerOnly(c, title)
        val (from, to) = Hudhud.span(w, c.now())
        val open = ScreenId.COUNTER_SALE
        val lines = dao.returnedGoodsBetween(from, to, GOODS_READ)
        if (lines.isEmpty()) return none(c, title, open)
        if (lines.size >= GOODS_READ) return tooMany(c, title, open)
        val tally = HudhudCounter.tallyGoods(lines.map { HudhudCounter.GoodsLine(it.itemName, it.unit, it.quantity, it.quantity * it.rate) }, TOP)
        return AnswerCard(Mood.CALM, label = title, figure = n(HudhudCounter.tallyGoods(lines.map { HudhudCounter.GoodsLine(it.itemName, it.unit, it.quantity, it.quantity * it.rate) }).size),
            rows = tally.map { t -> Row(0, t.name, Format.qty(t.qty, t.unit), money(t.worth), open = open) },
            open = open, money = true)
    }

    // ------------------------------------------------------------------------------------------------ the till

    /**
     * The shifts opened over a span (this week when none was said), newest first: who, when it opened and closed, the
     * hours, the float it started with, what was counted against what the tickets said, and the difference ([Shift],
     * [ShiftMath]). With [onlyShort] only the closed shifts whose count differed, the biggest difference first.
     */
    suspend fun shifts(c: AnswerEnv, dao: KhataDao, window: VoiceQuestion.Window?, onlyShort: Boolean): AnswerCard {
        val w = window ?: VoiceQuestion.Window.THIS_WEEK
        val title = heading(c, W.shift_title, w)
        if (!reports()) return ownerOnly(c, title)
        val (from, to) = Hudhud.span(w, c.now())
        val open = ScreenId.SHIFT
        val all = dao.shiftsBetween(from, to).let { list -> if (onlyShort) list.filter { s -> s.difference?.let { Money.isNotZero(it) } == true } else list }
        if (all.isEmpty()) return none(c, title, open)
        val ordered = if (onlyShort) all.sortedByDescending { abs(it.difference ?: 0.0) } else all.sortedByDescending { it.openedAt }
        return AnswerCard(Mood.CALM, label = title, figure = n(all.size),
            rows = ordered.take(TOP).map { s ->
                val figure = when {
                    s.closedAt == null -> c.getString(W.hudhud_l_still_open)
                    s.difference == null -> ""
                    Money.isZero(s.difference!!) -> c.getString(W.dayclose_match)
                    Money.isNegative(s.difference!!) -> c.getString(W.dayclose_short, money(abs(s.difference!!)))
                    else -> c.getString(W.dayclose_over, money(s.difference!!))
                }
                val sub = listOfNotNull(
                    whenOf(c, s.openedAt),
                    s.closedAt?.let { c.getString(W.shift_hours, Format.qty(s.hours ?: 0.0, null)) },
                    line(c, W.hudhud_l_starting_cash, money(s.openingFloat)),
                    if (s.counted != null && s.expected != null)
                        line(c, W.stock_counted_hint, money(s.counted!!)) + " · " + line(c, W.hudhud_l_books_said, money(s.expected!!)) else null,
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
    suspend fun dayCount(c: AnswerEnv, dao: KhataDao, window: VoiceQuestion.Window?): AnswerCard {
        val title = heading(c, W.hudhud_t_day_count, window)
        if (!reports()) return ownerOnly(c, title)
        val open = ScreenId.CASHBOOK
        val closes = if (window == null) listOfNotNull(dao.lastDayClose()) else {
            val (from, to) = Hudhud.span(window, c.now())
            dao.dayClosesBetween(DayCloseMath.dayOf(from), DayCloseMath.dayOf(to - 1) + 1, TOP)
        }.sortedByDescending { it.day }
        val first = closes.firstOrNull()
        if (first == null) {
            val words = if (window == VoiceQuestion.Window.YESTERDAY) c.getString(W.z_yesterday_open) else c.getString(W.hudhud_counter_none)
            return AnswerCard(Mood.CALM, label = title, lines = listOf(words), open = open)
        }
        val diff = first.difference
        val state = when {
            Money.isZero(diff) -> c.getString(W.dayclose_match)
            Money.isNegative(diff) -> c.getString(W.dayclose_short, money(abs(diff)))
            else -> c.getString(W.dayclose_over, money(diff))
        }
        return AnswerCard(if (Money.isZero(diff)) Mood.GOOD else Mood.CARE, label = title, figure = money(first.counted),
            lines = listOfNotNull(
                state,
                line(c, W.hudhud_l_books_said, money(first.expected)),
                line(c, W.dayclose_opening, money(first.opening)),
                line(c, W.z_cash_in, money(first.cashIn)),
                line(c, W.z_cash_out, money(first.cashOut)),
                first.note?.trim()?.takeIf { it.isNotEmpty() },
                whenOf(c, first.closedAt)
            ),
            rows = closes.drop(1).map { d ->
                Row(0, whenOf(c, d.closedAt), money(d.counted), if (Money.isZero(d.difference)) c.getString(W.dayclose_match)
                    else if (Money.isNegative(d.difference)) c.getString(W.dayclose_short, money(abs(d.difference)))
                    else c.getString(W.dayclose_over, money(d.difference)), open = open)
            },
            open = open, money = true)
    }

    // ------------------------------------------------------------------------------------------------ points and stock

    /**
     * Loyalty points. A customer named: what he has left now, and what he earned and spent over the span (the whole
     * book when none was said). Nobody named: who earned most over the span (this month when none was said), by the
     * rows of [KhataDao.pointRowsBetween] (points, timestamp); each a tap that opens him.
     */
    suspend fun loyalty(c: AnswerEnv, dao: KhataDao, ids: List<Long>, window: VoiceQuestion.Window?): AnswerCard {
        val w = window ?: if (ids.isEmpty()) VoiceQuestion.Window.THIS_MONTH else VoiceQuestion.Window.ALL_TIME
        val title = heading(c, W.loyalty_section, w)
        if (!reports()) return ownerOnly(c, title)
        val open = ScreenId.KHATA
        val (from, to) = Hudhud.span(w, c.now())
        if (ids.size == 1) {
            val id = ids.first()
            val name = dao.getPartyName(id) ?: return none(c, title, open)
            val mine = dao.pointRowsBetween(from, to, POINT_READ).filter { it.partyId == id }
            val earned = mine.filter { it.points > 0.0 }.sumOf { it.points }
            val spent = -mine.filter { it.points < 0.0 }.sumOf { it.points }
            return AnswerCard(Mood.CALM, label = name, figure = Format.qty(dao.loyaltyBalance(id), null),
                lines = listOf(c.getString(W.hudhud_l_points_left), line(c, W.hudhud_l_earned, Format.qty(earned, null)),
                    line(c, W.hudhud_l_spent, Format.qty(spent, null))), openParty = id)
        }
        if (ids.size > 1) {
            return AnswerCard(Mood.CALM, label = title, rows = ids.take(TOP).map { id ->
                Row(id, dao.getPartyName(id) ?: "", "", null, ask = Hudhud.Ask.Counter(Hudhud.CounterFacet.LOYALTY, window, listOf(id)))
            }, open = open)
        }
        val rows = dao.pointRowsBetween(from, to, POINT_READ)
        if (rows.isEmpty()) return none(c, title, open)
        if (rows.size >= POINT_READ) return tooMany(c, title, open)
        val tally = HudhudCounter.tallyPoints(rows.map { it.partyId to it.points })
        return AnswerCard(Mood.CALM, label = title, figure = Format.qty(tally.sumOf { it.earned }, null),
            lines = listOf(line(c, W.hudhud_l_spent, Format.qty(tally.sumOf { it.spent }, null))),
            rows = tally.take(TOP).map { t ->
                Row(t.partyId, dao.getPartyName(t.partyId) ?: "", Format.qty(t.earned, null), line(c, W.hudhud_l_spent, Format.qty(t.spent, null)))
            },
            open = open)
    }

    /**
     * Goods written off over a span (this month when none was said): damaged, expired or thrown out, theft or lost —
     * by product and unit, the quantity that left the shelf. Never a money value (no cost is guessed). The row's reason
     * and moment are the adjustment's own ([StockAdjustment.reason], timestamp, quantity, unit).
     */
    suspend fun writtenOff(c: AnswerEnv, dao: KhataDao, window: VoiceQuestion.Window?): AnswerCard {
        val w = window ?: VoiceQuestion.Window.THIS_MONTH
        val title = heading(c, W.hudhud_t_written_off, w)
        if (!reports()) return ownerOnly(c, title)
        val (from, to) = Hudhud.span(w, c.now())
        val open = ScreenId.PRODUCTS
        val rows = dao.adjustmentsBetween(from, to, ADJUST_READ)
        if (rows.size >= ADJUST_READ) return tooMany(c, title, open)
        val lost = HudhudCounter.tallyLost(rows.map { HudhudCounter.AdjustLine(it.productId, it.quantity, it.unit, it.reason) })
        if (lost.isEmpty()) return none(c, title, open)
        return AnswerCard(Mood.CALM, label = title, figure = n(lost.size),
            rows = lost.take(TOP).map { t ->
                Row(0, dao.productById(t.productId)?.name ?: "", Format.qty(t.lost, t.unit), reasonWord(c, t.reason), open = open)
            },
            open = open)
    }

    /**
     * The stock's own corrections over a span (this month when none was said), newest first: the product, the signed
     * quantity, the reason, who made it and its note ([StockAdjustment]; a count's differences and a transfer included).
     */
    suspend fun stockChanges(c: AnswerEnv, dao: KhataDao, window: VoiceQuestion.Window?): AnswerCard {
        val w = window ?: VoiceQuestion.Window.THIS_MONTH
        val title = heading(c, W.hudhud_t_stock_changes, w)
        if (!reports()) return ownerOnly(c, title)
        val (from, to) = Hudhud.span(w, c.now())
        val open = ScreenId.PRODUCTS
        val rows = dao.adjustmentsBetween(from, to, ADJUST_READ)
        if (rows.isEmpty()) return none(c, title, open)
        return AnswerCard(Mood.CALM, label = title, figure = if (rows.size < ADJUST_READ) n(rows.size) else null,
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
    suspend fun lastCount(c: AnswerEnv, dao: KhataDao): AnswerCard {
        val title = c.getString(W.hudhud_t_last_count)
        if (!reports()) return ownerOnly(c, title)
        val open = ScreenId.PRODUCTS
        val rows = dao.lastCountRows()
        val first = rows.firstOrNull() ?: return none(c, title, open)
        return AnswerCard(Mood.CALM, label = title, figure = n(rows.size),
            lines = listOf(whenOf(c, first.timestamp), line(c, W.hudhud_l_by, who(c, first.createdBy))),
            rows = rows.sortedByDescending { abs(it.quantity) }.take(TOP).map { a ->
                val book = a.bookQty?.let { Format.qty(it, a.unit) } ?: ""
                val counted = a.countedQty?.let { Format.qty(it, a.unit) } ?: ""
                Row(0, dao.productById(a.productId)?.name ?: "", (if (a.quantity > 0.0) "+" else "") + Format.qty(a.quantity, a.unit),
                    c.getString(W.hudhud_l_book_counted, book, counted), open = open)
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
