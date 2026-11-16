package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.AnswerCard.Mood
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import kotlin.math.abs

/**
 * Hudhud's answer to "why" about a figure of the book ([Hudhud.Ask.Why], 16 Nov 2026, audit T8, the owner's H-10: by rules, never
 * guessed), on both platforms. Only facts the book holds, each by the rule of the screen that shows that figure — never a second
 * formula (lesson 128):
 *
 * - **the two figures**: the span asked and the span before it ([HudhudWhyAsk.spans]: the same length of time while the span is
 *   still running), read with the very calls the span comparison ([Hudhud.Ask.Compare], [AnswerBook]) uses — the sales by
 *   [KhataDao.salesTotalBetween], the profit by [Profit.between]'s gross figure (none when nothing could be costed); the expenses by
 *   [Profit.between]'s own expenses rule (the cashbook's money out that is an expense, and the staff's pay); the money that came in
 *   by [KhataDao.customerReceivedBetween] (Insights' "received"); the customers' credit as what each customer's balance moved. The
 *   comparison's own `spanFigure` takes a whole span by its name, and the span before a running one is cut to the same length of
 *   time, which no span's name says — so the calls are the same, not the function;
 * - **the signed difference**, and when the sentence asked the other way ("why did it fall" and it rose) that is said plainly;
 * - **what moved most** the way the whole moved ([HudhudWhyAsk.movers]): customers, goods by quantity, products' profit, expense
 *   categories, the staff's pay, the counter's walk-ins; for the profit also the expenses beside it and the sale lines with no cost
 *   price, which leave part of it unknown.
 *
 * It never names a cause the book cannot show (weather, prices, competition): the card ends by saying so. Only the owner or a
 * manager sees it ([AnswerGate.reports]), as every figure in it alone. Nothing is written.
 */
object AnswerWhy {

    private const val PARTS = 3

    /** Rows asked of the grouped reads: every part that sold, paid or moved in the span (the figure itself is never cut by this). */
    private const val ALL_ROWS = 100_000

    suspend fun answer(c: AnswerEnv, dao: KhataDao, ask: Hudhud.Ask.Why): AnswerCard {
        val title = c.getString(titleOf(ask.of))
        if (!AnswerGate.reports()) return AnswerCard(Mood.CALM, label = title, lines = listOf(c.getString(W.hudhud_owner_only)))
        val spans = HudhudWhyAsk.spans(ask.window) ?: return AnswerCard(Mood.CALM, label = title, lines = listOf(c.getString(W.hudhud_unknown)))
        val (nowFrom, nowTo) = spans.now
        val (beforeFrom, beforeTo) = spans.before
        val figures = when (ask.of) {
            Hudhud.WhyOf.SALES -> sales(dao, spans)
            Hudhud.WhyOf.PROFIT -> profit(c, dao, spans)
            Hudhud.WhyOf.EXPENSES -> expenses(c, dao, spans)
            Hudhud.WhyOf.COLLECTED -> collected(c, dao, spans)
            Hudhud.WhyOf.DUES -> dues(dao, spans)
        }
        val lines = ArrayList<String>()
        lines += c.getString(W.hudhud_why_now, days(nowFrom, nowTo)) + ": " + shown(figures.now)
        lines += c.getString(if (spans.soFar) W.hudhud_why_before_so_far else W.hudhud_why_before, days(beforeFrom, beforeTo)) +
            ": " + shown(figures.before)
        val a = figures.now
        val b = figures.before
        var figure: String? = null
        var mood = Mood.CALM
        if (a == null || b == null) {
            lines += c.getString(W.hudhud_compare_unknown)
        } else {
            val change = a - b
            figure = signed(change)
            lines += c.getString(W.z_variance) + ": " + figure
            // "Why did it fall" when it rose, or the other way: said plainly, never explained away.
            when {
                Money.isZero(change) -> lines += c.getString(W.hudhud_why_same)
                ask.down == true && change > 0 -> lines += c.getString(W.hudhud_why_not_down, AnswerBook.money(change))
                ask.down == false && change < 0 -> lines += c.getString(W.hudhud_why_not_up, AnswerBook.money(-change))
            }
            // A fall in sales, profit or money in is the owner's care; a rise in expenses or in credit is too.
            val worse = if (ask.of == Hudhud.WhyOf.EXPENSES || ask.of == Hudhud.WhyOf.DUES) change > 0 else change < 0
            if (worse && !Money.isZero(change)) mood = Mood.CARE
            val moved = figures.parts
                .flatMap { group -> HudhudWhyAsk.movers(group.now, group.before, change, PARTS, Money::isZero).map { group to it } }
                .sortedByDescending { (group, p) -> if (group.money) abs(p.change) else 0.0 }
            if (moved.isNotEmpty()) {
                lines += c.getString(W.hudhud_why_moved)
                for ((group, p) in moved) {
                    lines += c.getString(W.hudhud_why_part, Format.isolate(p.name), group.show(p.change, true),
                        group.show(p.now, false), group.show(p.before, false))
                }
            }
        }
        lines += figures.notes
        lines += c.getString(W.hudhud_why_only_book)
        return AnswerCard(mood, label = title, figure = figure, lines = lines, open = openOf(ask.of), money = true)
    }

    /** The two figures, the parts each is made of, and what else the book says about them. */
    private class Figures(val now: Double?, val before: Double?, val parts: List<Group>, val notes: List<String> = emptyList())

    /** One kind of part (customers, goods…), in the two spans. [money]: the values are money, else quantities (the unit is in the name). */
    private class Group(val now: Map<String, Double>, val before: Map<String, Double>, val money: Boolean = true) {
        fun show(v: Double, sign: Boolean): String = if (money) {
            if (sign) signed(v) else AnswerBook.money(v)
        } else {
            (if (sign && v > 0) "+" else "") + Format.ltr(Format.qty(v, null))
        }
    }

    private suspend fun sales(dao: KhataDao, s: HudhudWhyAsk.Spans): Figures {
        val (a1, a2) = s.now
        val (b1, b2) = s.before
        val customers = Group(
            HudhudWhyAsk.summed(dao.topCustomersBetween(a1, a2, ALL_ROWS).map { it.name to it.total }),
            HudhudWhyAsk.summed(dao.topCustomersBetween(b1, b2, ALL_ROWS).map { it.name to it.total })
        )
        val goods = Group(goodsOf(dao.topProductsBetween(a1, a2, ALL_ROWS)), goodsOf(dao.topProductsBetween(b1, b2, ALL_ROWS)), money = false)
        return Figures(dao.salesTotalBetween(a1, a2), dao.salesTotalBetween(b1, b2), listOf(customers, goods))
    }

    /** Goods sold by quantity, each name with its unit ("Urea (bag)"): two units of one name are two parts, never added. */
    private fun goodsOf(rows: List<ProductStat>): Map<String, Double> =
        HudhudWhyAsk.summed(rows.map { r ->
            (r.unit?.takeIf { it.isNotBlank() }?.let { "${r.name} (${UnitWords.label(it)})" } ?: r.name) to r.qty
        })

    private suspend fun profit(c: AnswerEnv, dao: KhataDao, s: HudhudWhyAsk.Spans): Figures {
        val now = Profit.between(dao, s.now.first, s.now.second)
        val before = Profit.between(dao, s.before.first, s.before.second)
        val products = Group(
            HudhudWhyAsk.summed(now.products.map { it.name to it.profit }),
            HudhudWhyAsk.summed(before.products.map { it.name to it.profit })
        )
        val notes = ArrayList<String>()
        // The gross figure is before expenses; what the expenses did beside it is said on its own line, never folded in.
        if (!Money.isZero(now.expenses) || !Money.isZero(before.expenses)) {
            notes += c.getString(W.hudhud_why_part, c.getString(W.srep_expenses), signed(now.expenses - before.expenses),
                AnswerBook.money(now.expenses), AnswerBook.money(before.expenses))
        }
        // Lines with no cost leave part of the profit unknown: said as counts, never guessed into the figure.
        if (now.skippedLines > 0 || before.skippedLines > 0) notes += c.getString(W.hudhud_why_no_cost, now.skippedLines, before.skippedLines)
        return Figures(now.takeUnless { it.isEmpty }?.grossProfit, before.takeUnless { it.isEmpty }?.grossProfit, listOf(products), notes)
    }

    private suspend fun expenses(c: AnswerEnv, dao: KhataDao, s: HudhudWhyAsk.Spans): Figures {
        // Profit.between's own rule: the cashbook's (from, to] shifted by one to read [from, to), and the staff's pay.
        suspend fun of(span: Pair<Long, Long>): Pair<Double, Map<String, Double>> {
            val (from, to) = span
            val staff = dao.staffPaidBetween(from - 1, to - 1)
            val parts = HudhudWhyAsk.summed(dao.cashbookExpensesByCategoryBetween(from - 1, to - 1).map { categoryOf(c, it.name) to it.total }) +
                (if (Money.isZero(staff)) emptyMap() else mapOf(c.getString(W.hudhud_t_staff) to staff))
            return (dao.cashbookExpensesBetween(from - 1, to - 1) + staff) to parts
        }
        val (now, nowParts) = of(s.now)
        val (before, beforeParts) = of(s.before)
        return Figures(now, before, listOf(Group(nowParts, beforeParts)))
    }

    /** A category as the cashbook shows it: the counter's own in the owner's language, the owner's typed ones as typed. */
    private fun categoryOf(c: AnswerEnv, category: String): String = when (category) {
        CashCategory.SALE -> c.getString(W.cash_category_sale)
        CashCategory.RETURN -> c.getString(W.cash_category_return)
        else -> category
    }

    private suspend fun collected(c: AnswerEnv, dao: KhataDao, s: HudhudWhyAsk.Spans): Figures {
        suspend fun of(span: Pair<Long, Long>): Pair<Double, Map<String, Double>> {
            val (from, to) = span
            val total = dao.customerReceivedBetween(from, to)
            val khata = HudhudWhyAsk.summed(dao.customerReceivedByPartyBetween(from, to).map { it.name to it.total })
            // The rest of Insights' "received" is the counter's walk-in takings (its other half), never worked out another way.
            val walkIn = total - khata.values.sum()
            return total to (if (Money.isZero(walkIn)) khata else khata + (c.getString(W.pos_walk_in) to walkIn))
        }
        val (now, nowParts) = of(s.now)
        val (before, beforeParts) = of(s.before)
        return Figures(now, before, listOf(Group(nowParts, beforeParts)))
    }

    private suspend fun dues(dao: KhataDao, s: HudhudWhyAsk.Spans): Figures {
        val now = HudhudWhyAsk.summed(dao.customerBalanceMovesBetween(s.now.first, s.now.second).map { it.name to it.total })
        val before = HudhudWhyAsk.summed(dao.customerBalanceMovesBetween(s.before.first, s.before.second).map { it.name to it.total })
        return Figures(now.values.sum(), before.values.sum(), listOf(Group(now, before)))
    }

    private fun titleOf(of: Hudhud.WhyOf): String = when (of) {
        Hudhud.WhyOf.SALES -> W.hudhud_why_title_sales
        Hudhud.WhyOf.PROFIT -> W.hudhud_why_title_profit
        Hudhud.WhyOf.EXPENSES -> W.hudhud_why_title_expenses
        Hudhud.WhyOf.COLLECTED -> W.hudhud_why_title_collected
        Hudhud.WhyOf.DUES -> W.hudhud_why_title_dues
    }

    /** The page that shows the figure: Insights (this month against the last), the cashbook, dues by age. */
    private fun openOf(of: Hudhud.WhyOf): ScreenId = when (of) {
        Hudhud.WhyOf.SALES, Hudhud.WhyOf.PROFIT, Hudhud.WhyOf.COLLECTED -> ScreenId.INSIGHTS
        Hudhud.WhyOf.EXPENSES -> ScreenId.CASHBOOK
        Hudhud.WhyOf.DUES -> ScreenId.AGING
    }

    /** The days of a span as [from, to): "1 Nov – 16 Nov", or one day. */
    private fun days(from: Long, to: Long): String {
        val first = Format.dateOnly(from)
        val last = Format.dateOnly(to - 1)
        return Format.ltr(if (first == last) first else "$first – $last")
    }

    private fun signed(v: Double): String = (if (Money.isPositive(v)) "+" else "") + AnswerBook.money(v)

    private fun shown(v: Double?): String = v?.let { AnswerBook.money(it) } ?: "—"
}
