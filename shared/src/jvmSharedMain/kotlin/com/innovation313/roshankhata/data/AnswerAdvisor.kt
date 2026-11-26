package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.AnswerCard.Mood
import com.innovation313.roshankhata.data.AnswerCard.Row
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.WP
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs

/**
 * Hudhud the business advisor (25 Nov 2026, plan H5, first part): three questions the shop asks of its own book, and the two lines
 * the Today card adds, all by figures the screens already hold — never a second formula (lesson 128), never a guess:
 *
 * - **the trend** ([Hudhud.Ask.Trend]): the sales of the span asked against the span before it, the same two figures [AnswerWhy]
 *   and the comparison read (`salesTotalBetween`, F07; the span before a running one cut to the same length of time,
 *   [HudhudWhyAsk.spans]), said as up, down or about the same with both figures, the difference and its percentage;
 * - **whom to chase first** ([Hudhud.AdviseOf.CHASE]): the customers who owe, the oldest piece of each as [Aging] lays the balance
 *   over the entries (F29), joined with the Payment Radar's reading where the owner has it on (F39–F41: "often pays late", "pays very
 *   late"), the five to ask first with the days and the amount behind each;
 * - **the next 30 days** ([Hudhud.AdviseOf.NEXT_30]): the Cash flow page's own promised and due figures ([CashFlow], F49) — what
 *   is dated to come in and go out, the first week the money out runs ahead, and what is left out and why;
 * - **what needs the owner today** ([needsToday], two lines at most, under the Today card's greeting): a customer who promised to pay
 *   today and owes, and a product the shelf of which lasts under seven days at its last 30 days' pace ([StockCover], F33).
 *
 * Every one is the owner's or a manager's ([AnswerGate.reports]), as each figure is alone. Nothing is written and nothing is sent: the
 * chase is a list, the reminder stays Follow-up's own tap.
 */
object AnswerAdvisor {

    private const val TOP = 5

    private const val DAY = 24L * 60 * 60 * 1000

    suspend fun trend(c: AnswerEnv, dao: KhataDao, ask: Hudhud.Ask.Trend): AnswerCard {
        val title = c.getString(W.hudhud_t_trend)
        if (!AnswerGate.reports()) return AnswerCard(Mood.CALM, label = title, lines = listOf(c.getString(W.hudhud_owner_only)))
        val spans = HudhudWhyAsk.spans(ask.window, c.now())
            ?: return AnswerCard(Mood.CALM, label = title, lines = listOf(c.getString(W.hudhud_unknown)))
        val label = title + " · " + c.getString(AnswerBook.windowWord(ask.window))
        val (nowFrom, nowTo) = spans.now
        val (beforeFrom, beforeTo) = spans.before
        val a = dao.salesTotalBetween(nowFrom, nowTo)
        val b = dao.salesTotalBetween(beforeFrom, beforeTo)
        val v = HudhudAdviceRules.verdict(a, b)
        val lines = ArrayList<String>()
        lines += c.getString(
            when (v.way) {
                HudhudAdviceRules.Way.UP -> W.hudhud_trend_up
                HudhudAdviceRules.Way.DOWN -> W.hudhud_trend_down
                HudhudAdviceRules.Way.FLAT -> W.hudhud_trend_flat
                HudhudAdviceRules.Way.NONE -> W.hudhud_trend_none
            }
        )
        lines += c.getString(W.hudhud_why_now, AnswerWhy.days(nowFrom, nowTo)) + ": " + AnswerBook.money(a)
        lines += c.getString(if (spans.soFar) W.hudhud_why_before_so_far else W.hudhud_why_before, AnswerWhy.days(beforeFrom, beforeTo)) +
            ": " + AnswerBook.money(b)
        var figure: String? = null
        if (v.way != HudhudAdviceRules.Way.NONE) {
            figure = AnswerWhy.signed(v.change)
            lines += c.getString(W.z_variance) + ": " + figure + (v.percent?.let { " (" + percentWords(it) + ")" } ?: "")
            if (v.percent == null) lines += c.getString(W.hudhud_trend_from_nothing)
        }
        lines += c.getString(W.hudhud_why_only_book)
        return AnswerCard(
            when (v.way) { HudhudAdviceRules.Way.UP -> Mood.GOOD; HudhudAdviceRules.Way.DOWN -> Mood.CARE; else -> Mood.CALM },
            label = label, figure = figure, lines = lines, open = ScreenId.INSIGHTS, money = true
        )
    }

    /** "+12%" or "−8%" as one left-to-right run, whole percents. */
    private fun percentWords(p: Double): String {
        val whole = Math.round(abs(p)).toDouble()
        return Format.ltr((if (p > 0) "+" else "−") + Format.percent(whole) + "%")
    }

    // ------------------------------------------------------------------------------------------------ the two advice cards

    /** "Whom to chase first" and "what comes in in the next 30 days": REPORTS, as the figures they are made of. */
    suspend fun advise(c: AnswerEnv, dao: KhataDao, ask: Hudhud.Ask.Advise, parties: List<PartyWithBalance>): AnswerCard {
        val title = c.getString(titleOf(ask.of))
        if (!AnswerGate.reports()) return AnswerCard(Mood.CALM, label = title, lines = listOf(c.getString(W.hudhud_owner_only)))
        return when (ask.of) {
            Hudhud.AdviseOf.CHASE -> chase(c, dao, parties, title)
            Hudhud.AdviseOf.NEXT_30 -> next30(c, dao, parties, title)
            Hudhud.AdviseOf.MORE_CREDIT -> moreCredit(c, dao, ask.ids, parties, title)
            Hudhud.AdviseOf.CONCENTRATION -> concentration(c, parties, title)
            Hudhud.AdviseOf.MAGIC -> magic(c, parties, title)
        }
    }

    /** The heading of each of the advisor's cards (and its chip). */
    fun titleOf(of: Hudhud.AdviseOf): String = when (of) {
        Hudhud.AdviseOf.CHASE -> W.hudhud_t_chase
        Hudhud.AdviseOf.NEXT_30 -> W.hudhud_t_next30
        Hudhud.AdviseOf.MORE_CREDIT -> W.hudhud_t_more_credit
        Hudhud.AdviseOf.CONCENTRATION -> W.hudhud_t_concentration
        Hudhud.AdviseOf.MAGIC -> W.vq_who_owes_most
    }

    // ------------------------------------------------------------------------------------------------ more credit (D8)

    /**
     * "Aslam ko aur udhaar doon?" (26 Nov 2026, Marhala 10, D8): exactly three facts of the book and the owner's decision — his
     * balance (F04), his pace by the Payment Radar's own reading (F39: the median days of his last repaid lots, against the shop's;
     * only where the Radar is on), and whether he is late now by Follow-up's one count ([FollowUpRank.lateCount], F63). Never "yes"
     * or "no", never the Radar's light or any other risk label: the card says the decision is the owner's. Nothing is written.
     */
    private suspend fun moreCredit(
        c: AnswerEnv, dao: KhataDao, ids: List<Long>, parties: List<PartyWithBalance>, title: String
    ): AnswerCard {
        if (ids.isEmpty()) return AnswerCard(Mood.CALM, label = title, lines = listOf(c.getString(W.hudhud_credit_whose)))
        val found = ids.mapNotNull { id -> parties.firstOrNull { it.id == id } }
        if (found.isEmpty()) return AnswerBook.unknown(c)
        if (found.size > 1) {
            return AnswerCard(
                Mood.CALM, label = c.getString(W.hudhud_pick_party),
                rows = found.map {
                    Row(it.id, it.name, AnswerBook.money(abs(it.balance)), AnswerBook.side(c, it.balance),
                        ask = Hudhud.Ask.Advise(Hudhud.AdviseOf.MORE_CREDIT, listOf(it.id)))
                }
            )
        }
        val p = found.first()
        if (!p.isCustomer) {
            return AnswerCard(Mood.CALM, label = p.name, figure = AnswerBook.money(abs(p.balance)),
                lines = listOf(AnswerBook.side(c, p.balance), c.getString(W.hudhud_radar_customers)), openParty = p.id, money = true)
        }
        val now = c.now()
        val points = LedgerLines.of(dao, listOf(p.id))
        val lines = ArrayList<String>()
        // 1. The balance, in the Khata list's own words.
        lines += c.getString(W.hudhud_credit_balance, AnswerBook.money(abs(p.balance)), AnswerBook.side(c, p.balance))
        // 2. His pace (F39), only where the owner has the Radar on (WhenPays' own rule); the light is never said.
        lines += if (!c.radarOn()) c.getString(W.radar_state_off) else {
            val r = PaymentRadar.read(points, now, c.radarShopDays(dao), dao.getParty(p.id)?.harvestPromise,
                PromiseLog.of(dao.promiseLog(p.id)))
            val typical = r.typicalDays
            if (typical == null) c.getString(W.radar_new) else {
                c.getString(W.radar_typical, c.quantity(WP.radar_days, typical, typical)) +
                    (r.shopDays?.let { " · " + c.getString(W.radar_shop_typical, c.quantity(WP.radar_days, it, it)) } ?: "")
            }
        }
        // 3. Late now by his own habit: Follow-up's one count (F63) for this one customer, with the days behind it.
        val promises = dao.observePromises().first().associate { it.partyId to it.due }
        val habit = PaymentHabit.forAll(points, now)[p.id]
        lines += when {
            !Money.isPositive(p.balance) -> c.getString(W.hudhud_credit_not_late)
            FollowUpRank.lateCount(listOf(p), points, promises, now) > 0 && habit != null ->
                c.getString(W.hudhud_credit_late, c.quantity(WP.radar_days, habit.waitingDays, habit.waitingDays),
                    c.quantity(WP.radar_days, habit.typicalDays, habit.typicalDays))
            habit == null -> c.getString(W.hudhud_credit_late_unknown)
            else -> c.getString(W.hudhud_credit_not_late)
        }
        lines += c.getString(W.hudhud_credit_yours)
        return AnswerCard(Mood.CALM, label = p.name + " · " + title, figure = AnswerBook.money(abs(p.balance)), lines = lines,
            openParty = p.id, money = true)
    }

    // ------------------------------------------------------------------------------------------------ concentration (D19)

    /**
     * "Kul udhaar ka kitna hissa aik party ke paas hai" (26 Nov 2026, Marhala 10, D19): F65 — the largest balance owed to the shop as a
     * share of all money owed to it ([PriceMath.concentration]), with his name, and the next two beside it. The share only: no alert,
     * no threshold and no advice about him (D19: an alert comes later, set by the owner or by measurement).
     */
    private fun concentration(c: AnswerEnv, parties: List<PartyWithBalance>, title: String): AnswerCard {
        val conc = PriceMath.concentration(parties.map { it.balance })
            ?: return AnswerCard(Mood.GOOD, label = title, lines = listOf(c.getString(W.vq_nobody)))
        val first = parties[conc.index]
        val top = HudhudAdviceRules.owedMost(parties, 3) { it.balance }
        val total = BigDecimal.valueOf(conc.total)
        fun shareOf(b: Double): String =
            AnswerKnowledge.percent(BigDecimal.valueOf(b).multiply(BigDecimal(100)).divide(total, MathContext(16, RoundingMode.HALF_UP)).toDouble())
        val shown = AnswerKnowledge.percent(conc.share * 100.0)
        return AnswerCard(
            Mood.CALM, label = title, figure = shown,
            lines = listOf(c.getString(W.hudhud_conc_line, Format.isolate(first.name), shown),
                c.getString(W.hudhud_conc_total, AnswerBook.money(conc.total))),
            rows = top.map { Row(it.id, it.name, AnswerBook.money(it.balance), c.getString(W.hudhud_conc_share, shareOf(it.balance))) },
            open = ScreenId.KHATA, money = true
        )
    }

    // ------------------------------------------------------------------------------------------------ a joke at the dues

    /**
     * "Jadoo kar ke udhaar wapas la do" (26 Nov 2026, Marhala 10): a light line and the five who owe the most, by who-owes-most's own
     * rule ([HudhudAdviceRules.owedMost]); the Follow-up page to remind them. One rule, never a joke engine.
     */
    private fun magic(c: AnswerEnv, parties: List<PartyWithBalance>, title: String): AnswerCard {
        val top = HudhudAdviceRules.owedMost(parties, TOP) { it.balance }
        if (top.isEmpty()) return AnswerCard(Mood.GOOD, label = title, lines = listOf(c.getString(W.vq_nobody)))
        return AnswerCard(
            Mood.CALM, label = title, lines = listOf(c.getString(W.hudhud_magic_line)),
            rows = top.map { Row(it.id, it.name, AnswerBook.money(it.balance), null) },
            open = ScreenId.FOLLOW_UP, money = true
        )
    }

    // ------------------------------------------------------------------------------------------------ whom to chase

    /** The Radar's light as the chase ranks it: none (or the Radar off) 0, green 1, amber 2, red 3. */
    private fun lightRank(l: PaymentRadar.Light?): Int = when (l) {
        null, PaymentRadar.Light.NONE -> 0
        PaymentRadar.Light.GREEN -> 1
        PaymentRadar.Light.AMBER -> 2
        PaymentRadar.Light.RED -> 3
    }

    private suspend fun chase(c: AnswerEnv, dao: KhataDao, parties: List<PartyWithBalance>, title: String): AnswerCard {
        val now = c.now()
        val report = Aging.compute(parties, LedgerLines.agingMoves(dao, parties), now)
        // The Radar's reading only where it is the owner's to see and switched on (WhenPays' own rule).
        val lights: Map<Long, PaymentRadar.Light> = if (!c.radarOn()) emptyMap() else {
            val shop = c.radarShopDays(dao)
            val promises = dao.observePromises().first().associate { it.partyId to it.due }
            LedgerLines.owingOf(dao, parties).groupBy { it.partyId }
                .mapValues { (id, rows) -> PaymentRadar.read(rows, now, shop, promises[id]).light }
        }
        val owing = report.receivable.filter { it.isCustomer && Money.isPositive(it.balance) }
        val by = owing.associateBy { it.partyId }
        val first = owing.map { r ->
            HudhudAdviceRules.Candidate(r.partyId, r.name, r.balance, Aging.bucketOf(r.oldest ?: now, now), lightRank(lights[r.partyId]))
        }.sortedWith(HudhudAdviceRules.CHASE_ORDER).take(TOP)
        if (first.isEmpty()) {
            return AnswerCard(Mood.GOOD, label = title, lines = listOf(c.getString(W.aging_none)), open = ScreenId.AGING)
        }
        val rows = first.map { ch ->
            val sub = ArrayList<String>()
            // The oldest unpaid credit's age, counted as Dues by age counts it (Aging.bucketOf's whole days).
            val days = by[ch.id]?.oldest?.let { ((now - it).coerceAtLeast(0L) / DAY_MS).toInt() }
            if (days != null) sub += c.getString(W.radar_oldest, c.quantity(WP.radar_days, days, days))
            when (lights[ch.id]) {
                PaymentRadar.Light.RED -> sub += c.getString(W.radar_very_late)
                PaymentRadar.Light.AMBER -> sub += c.getString(W.radar_late)
                else -> {}
            }
            Row(ch.id, ch.name, AnswerBook.money(ch.balance), sub.joinToString(" · "))
        }
        return AnswerCard(Mood.CARE, label = title, lines = listOf(c.getString(W.hudhud_chase_note)), rows = rows, open = ScreenId.FOLLOW_UP, money = true)
    }

    // ------------------------------------------------------------------------------------------------ the next 30 days

    /** How many days ahead the answer looks: the Cash flow page's own default. */
    const val AHEAD_DAYS = 30

    /**
     * The Cash flow page's outlook for [days] from the start of [today]: the same inputs the page and the bell's cash-flow row
     * give [CashFlow.items] (instalments, pending cheques of a party still in the book, promised days, credit bills, and what the
     * shop owes each supplier). One loader with the page and the bell: [CashFlow.load] (BC-285).
     */
    suspend fun outlook(dao: KhataDao, parties: List<PartyWithBalance>, today: Long, days: Int = AHEAD_DAYS): CashFlow.Outlook {
        val (items, undated) = CashFlow.load(dao, parties)
        return CashFlow.outlook(items, undated, today, days)
    }

    private suspend fun next30(c: AnswerEnv, dao: KhataDao, parties: List<PartyWithBalance>, title: String): AnswerCard {
        val o = outlook(dao, parties, DayMath.startOfDay(c.now()))
        val lateIn = o.overdue.filter { it.kind.isIn }
        if (o.items.isEmpty() && o.overdue.isEmpty() && o.undatedBills == 0) {
            return AnswerCard(Mood.CALM, label = title, lines = listOf(c.getString(W.cf_empty)), open = ScreenId.CASH_FLOW)
        }
        val lines = ArrayList<String>()
        lines += c.getString(W.cf_out) + ": " + AnswerBook.money(o.going)
        lines += c.getString(W.cf_net) + ": " + AnswerWhy.signed(o.coming - o.going)
        lines += o.gapWeek?.let { c.getString(W.cf_gap, DateWords.format("d MMM", it.from), AnswerBook.money(o.gap)) } ?: c.getString(W.cf_no_gap)
        if (lateIn.isNotEmpty()) lines += c.getString(W.cf_overdue) + ": " + AnswerBook.money(lateIn.sumOf { it.amount })
        if (o.undatedBills > 0) lines += c.getString(W.cf_undated, o.undatedBills.toString())
        lines += c.getString(W.cf_note)
        val rows = o.items.filter { it.kind.isIn }.take(TOP).map { i ->
            Row(i.partyId, i.name, AnswerBook.money(i.amount), DateWords.format("d MMM", i.date) + " · " + c.getString(kindWord(c, i.kind)))
        }
        return AnswerCard(
            if (o.gapWeek != null) Mood.CARE else if (Money.isPositive(o.coming)) Mood.GOOD else Mood.CALM,
            label = title + " · " + c.getString(W.cf_in), figure = AnswerBook.money(o.coming),
            lines = lines, rows = rows, open = ScreenId.CASH_FLOW, money = true
        )
    }

    /** The Cash flow page's own words for a kind of item (a promised day is "after harvest" only where the shop keeps seasons). */
    private fun kindWord(c: AnswerEnv, k: CashFlow.Kind): String = when (k) {
        CashFlow.Kind.PLAN -> W.cf_kind_plan
        CashFlow.Kind.CHEQUE_IN -> W.cf_kind_cheque_in
        CashFlow.Kind.HARVEST -> if (c.seasons()) W.cf_kind_harvest else W.cf_kind_promised
        CashFlow.Kind.BILL -> W.cf_kind_bill
        CashFlow.Kind.CHEQUE_OUT -> W.cf_kind_cheque_out
    }

    // ------------------------------------------------------------------------------------------------ what needs the owner today

    /**
     * At most [HudhudAdviceRules.TODAY_MAX] facts, the first two of: a customer who promised to pay today and still owes (the biggest first), a
     * product whose shelf lasts under [HudhudAdviceRules.COVER_WARN_DAYS] days (the soonest first), then the next promise, the next shelf. Nothing for
     * somebody who may not see figures, nothing when nothing needs him. A customer on an open instalment plan is left to the bell,
     * which already says his plan is due (lesson 119: no notice twice). Read, never written.
     */
    suspend fun needsToday(c: AnswerEnv, dao: KhataDao, parties: List<PartyWithBalance>, now: Long): List<String> {
        if (!AnswerGate.reports()) return emptyList()
        val promises = promisesToday(c, dao, parties, now)
        val shelves = shelvesRunningOut(c, dao, now)
        return HudhudAdviceRules.interleave(promises, shelves)
    }

    private suspend fun promisesToday(c: AnswerEnv, dao: KhataDao, parties: List<PartyWithBalance>, now: Long): List<String> {
        val today = DayMath.startOfDay(now)
        val onPlan = dao.openPlansOnce().map { it.partyId }.toSet()
        val owing = parties.filter { it.isCustomer && Money.isPositive(it.balance) }.associateBy { it.id }
        return dao.observePromises().first()
            .filter { it.partyId in owing && it.partyId !in onPlan && DayMath.startOfDay(it.due) == today }
            .mapNotNull { owing[it.partyId] }
            .sortedWith(compareByDescending<PartyWithBalance> { it.balance }.thenBy { it.name })
            .map { c.getString(W.hudhud_today_promise, Format.isolate(it.name), AnswerBook.money(it.balance)) }
    }

    private suspend fun shelvesRunningOut(c: AnswerEnv, dao: KhataDao, now: Long): List<String> {
        // The same shelf, the same last 30 days' sales and the same pace as the product's own cover answer (AnswerFacts, COVER): a book
        // younger than 30 days is divided by its own age.
        val first = dao.firstSaleAt()
        val windowDays = if (first != null && first > now - StockCover.WINDOW_MS)
            kotlin.math.ceil((now - first) / DAY.toDouble()).toInt().coerceIn(1, StockCover.DAYS) else StockCover.DAYS
        val shelf = Valuation.compute(dao).rows.filter { it.onHand != null && it.onHand > 0.0 }
        if (shelf.isEmpty()) return emptyList()
        val sold = dao.topProductsBetween(now - StockCover.WINDOW_MS, now + DAY, 10_000).groupBy { it.name.trim().lowercase() }
        val products = dao.productsOnce().associateBy { it.id }
        return shelf.mapNotNull { r ->
            val p = products[r.productId]
            val rows = sold[r.name.trim().lowercase()].orEmpty()
            val cover = StockCover.of(r.onHand, StockCover.soldBase(rows, p, r.unit ?: p?.defaultUnit), windowDays)
            (cover as? StockCover.Cover.Days)?.takeIf { it.days < HudhudAdviceRules.COVER_WARN_DAYS }?.let { r to it.days }
        }.sortedWith(compareBy<Pair<Valuation.Row, Int>> { it.second }.thenBy { it.first.name })
            .map { (r, days) ->
                if (days == 0) c.getString(W.hudhud_today_cover_now, Format.isolate(r.name))
                else c.getString(W.hudhud_today_cover, Format.isolate(r.name), c.quantity(WP.radar_days, days, days))
            }
    }

    private const val DAY_MS = 24L * 60 * 60 * 1000
}
