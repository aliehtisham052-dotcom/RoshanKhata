package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.AnswerCard.Mood
import com.innovation313.roshankhata.data.AnswerCard.Row
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.WP
import com.innovation313.roshankhata.ui.Format
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

/**
 * Hudhud's wider knowledge (13 Nov 2026, the owner: "Hudhud ko har cheez ka pata ho… kya kab aaya, kya kab gaya…
 * lekin accuracy 100%, fake data nahi"): a customer's number, last payment and limit; a product's price, stock,
 * purchase rate and which supplier is cheaper; the shop's profile; how many of anything were made today; where
 * cheques, plans, bills, stock and salaries stand; what came in and what happened today and who did it; the date.
 *
 * Every figure is read from the open shop's book by the rule of the screen the card opens — the stock by [Valuation]
 * (Products and the bell), money in by [KhataDao.customerReceivedBetween] (Insights), salaries by [Payroll] (Staff), suppliers' rates
 * by the bill lines Purchase rates reads — so Hudhud and the screen never disagree. Nothing is estimated: what the
 * book does not hold he says it does not hold ("No price is set for this item"), and the app's own counts that began
 * with this build say since when they count. Every answer only READS. Money (purchase rates, the day's takings) is
 * behind the REPORTS right like every figure; salaries and the shop's private details are the owner's alone (STAFF and
 * SETTINGS, the owner exactly — a manager is told so); numbers (phones, IBAN, tax, and any long number inside a customer's
 * private note) are shown and never said aloud — a customer may be standing at the counter. A customer's private note is
 * told in full, written and spoken, to whoever holds the phone, as his page shows it (14 Nov 2026, the owner).
 */
object AnswerFacts {

    // Every gate is asked through AnswerGate (the catalog holds each fact to the gate of the screen that shows it).
    internal fun reports() = AnswerGate.reports()

    internal fun staff() = AnswerGate.staff()

    private fun settings() = AnswerGate.settings()

    /** A figure behind the REPORTS right: the owner or a manager. */
    internal fun ownerOnly(c: AnswerEnv, label: String) =
        AnswerCard(Mood.CALM, label = label, lines = listOf(c.getString(W.hudhud_owner_only)))

    /** A fact that only the owner may see (Staff, Business Profile's private part): a manager is told so too. */
    internal fun ownerExact(c: AnswerEnv, label: String) =
        AnswerCard(Mood.CALM, label = label, lines = listOf(c.getString(W.hudhud_owner_only_exact)))

    /** "Label: value", the value isolated so a name or a figure reads right in a right-to-left line. */
    fun line(label: String, value: String) = label + ": " + Format.isolate(value)

    internal fun line(c: AnswerEnv, label: String, value: String) = line(c.getString(label), value)

    internal fun whenOf(c: AnswerEnv, t: Long) = WhenWords.whenWords(t, c.now(), c.words)

    internal fun dayOf(c: AnswerEnv, t: Long) = WhenWords.dayWord(t, c.now(), c.words)

    /** A count as a figure. */
    internal fun n(count: Int): String = count.toString()

    /**
     * A party's opening balance ([EntryKind.OPENING], schema 45: owed from before this book, brought in when the book began),
     * netted, with its side, as the statement's "Opening balance" says it; null when none of his lines is one (17 Nov 2026,
     * audit L015). His first entry's day is then the day the book took him in, not the day he began dealing.
     */
    private fun openingLine(c: AnswerEnv, points: List<LedgerPoint>): String? {
        val opening = points.filter { it.isOpening }
        if (opening.isEmpty()) return null
        val net = opening.sumOf { if (it.isGiven) it.amount else -it.amount }
        return line(c.getString(W.pdf_stmt_opening), AnswerBook.money(abs(net)) + " · " + AnswerBook.side(c, net))
    }

    /**
     * Days given outright instead of a window (16 Nov 2026, audit T31: a day or a month named, "15 tareekh", "November"):
     * the card's heading, the days as [from, to), and whether they are one day (the day book then opens on it).
     */
    class Over(val label: String, val span: Pair<Long, Long>, val oneDay: Boolean)

    /** The span asked, or today when none was. */
    private fun spanOf(c: AnswerEnv, window: VoiceQuestion.Window?): Pair<Long, Long> = Hudhud.span(window ?: VoiceQuestion.Window.TODAY, c.now())

    internal fun heading(c: AnswerEnv, title: String, window: VoiceQuestion.Window?): String =
        if (window == null) c.getString(title) else c.getString(title) + " · " + c.getString(AnswerBook.windowWord(window))

    // ------------------------------------------------------------------------------------------------ people

    /**
     * A named customer's or supplier's fact. A payment is what he paid (a customer's "I got") or what the shop paid
     * him (a supplier's "I gave"), as his page shows it; his private note is asked for by its own words ([Hudhud.Facet.NOTE])
     * and is not in his details, which stay what they were.
     */
    suspend fun person(
        c: AnswerEnv, dao: KhataDao, ids: List<Long>, facet: Hudhud.Facet, parties: List<PartyWithBalance>
    ): AnswerCard {
        val found = ids.mapNotNull { id -> parties.firstOrNull { it.id == id } }
        if (found.isEmpty()) return AnswerBook.unknown(c)
        if (found.size > 1) return AnswerBook.pick(c, found, facet)
        val p = found.first()
        val party = dao.getParty(p.id)
        val name = p.name
        val balance = line(c.getString(W.hudhud_l_balance), AnswerBook.money(abs(p.balance)) + " · " + AnswerBook.side(c, p.balance))
        return when (facet) {
            Hudhud.Facet.PHONE -> {
                val phone = party?.phone?.takeIf { it.isNotBlank() }
                if (phone == null) {
                    AnswerCard(Mood.CALM, label = name, lines = listOf(c.getString(W.hudhud_no_phone, name)), openParty = p.id)
                } else {
                    AnswerCard(Mood.CALM, label = name, figure = Format.ltr(phone), lines = listOf(c.getString(W.hudhud_l_phone)),
                        openParty = p.id, spoken = { w -> name + ": " + w.getString(W.hudhud_on_screen) })
                }
            }
            Hudhud.Facet.DETAILS -> {
                val points = LedgerLines.of(dao, listOf(p.id))
                val first = points.minOfOrNull { it.timestamp }
                val lines = listOfNotNull(
                    c.getString(if (p.isCustomer) W.customer else W.supplier),
                    party?.fatherName?.takeIf { it.isNotBlank() }?.let { line(c, W.hudhud_l_father, it) },
                    party?.village?.takeIf { it.isNotBlank() }?.let { line(c, W.hudhud_l_village, it) },
                    party?.phone?.takeIf { it.isNotBlank() }?.let { line(c.getString(W.hudhud_l_phone), Format.ltr(it)) },
                    first?.let { line(c, W.hudhud_l_since, Format.dateOnly(it)) },
                    openingLine(c, points),
                    if (points.isEmpty()) c.getString(W.party_no_entries) else balance
                )
                AnswerCard(Mood.CALM, label = name, lines = lines, openParty = p.id,
                    secrets = listOfNotNull(party?.phone?.takeIf { it.isNotBlank() }))
            }
            Hudhud.Facet.LAST_PAYMENT -> {
                val points = LedgerLines.of(dao, listOf(p.id))
                // A customer pays with an "I got"; the shop pays a supplier with an "I gave".
                val last = points.filter { it.isGiven != p.isCustomer }.maxByOrNull { it.timestamp }
                if (last == null) {
                    AnswerCard(Mood.CALM, label = name, lines = listOf(c.getString(W.hudhud_no_payment), balance), openParty = p.id)
                } else {
                    val at = whenOf(c, last.timestamp)
                    // How it came and its reference, when the entry holds them (15 Nov 2026); an unrecorded way is not cash.
                    val entry = dao.getEntry(last.id)
                    val paid = entry?.let { PaymentMethod.said(c.words, it.paymentMethod, it.paymentRef) }
                    AnswerCard(Mood.CALM, label = name, figure = AnswerBook.money(last.amount),
                        lines = listOfNotNull(
                            line(c, W.hudhud_l_last_payment, at),
                            entry?.entryNumber?.takeIf { it.isNotBlank() }?.let { line(c, W.hudhud_l_entry_no, it) },
                            paid?.let { line(c, W.hudhud_l_entry_paid, it) },
                            balance
                        ),
                        openParty = p.id, money = true,
                        secrets = listOfNotNull(entry?.paymentRef?.takeIf { it.isNotBlank() }))
                }
            }
            Hudhud.Facet.LAST_ENTRY -> {
                val last = LedgerLines.of(dao, listOf(p.id)).maxByOrNull { it.timestamp }
                if (last == null) {
                    AnswerCard(Mood.CALM, label = name, lines = listOf(c.getString(W.party_no_entries)), openParty = p.id)
                } else {
                    // An opening balance (schema 45) says so: it is money owed from before the book, not a sale or a payment.
                    val way = c.getString(if (last.isGiven) W.i_gave else W.i_got) +
                        (if (last.isOpening) " · " + c.getString(W.pdf_stmt_opening) else "")
                    // What the entry itself says (15 Nov 2026): its number, how it was paid, the goods, its note and, for who may
                    // see who wrote what, the writer — each only when the entry holds it (an unrecorded way is not cash).
                    val entry = dao.getEntry(last.id)
                    val items = if (entry == null) emptyList() else dao.itemsOfEntry(entry.id)
                    val paid = entry?.let { PaymentMethod.said(c.words, it.paymentMethod, it.paymentRef) }
                    val entryNote = entry?.note?.takeIf { it.isNotBlank() }
                    val writer = if (AnswerGate.reports()) entry?.createdBy?.takeIf { it.isNotBlank() } else null
                    // The entry's own rate list, season and chance of recovery (15 Nov 2026, the catalog's last columns): each only when
                    // the entry holds it. The season is the owner's own pick (never the one worked out from the date) and only where the
                    // shop keeps seasons; a doubtful debt is the owner's private judgement, so it is told with REPORTS only.
                    val rateWord = when (entry?.rateType) {
                        RateType.CASH -> c.getString(W.rate_type_cash)
                        RateType.CREDIT -> c.getString(W.rate_type_credit)
                        else -> null
                    }
                    val seasonWord = if (entry != null && last.isGiven && c.seasons())
                        SeasonBook.Season.fromKey(entry.season)?.let { c.seasonName(it) } else null
                    val doubtWord = if (AnswerGate.reports() && entry?.recovery == Recovery.DOUBTFUL)
                        c.getString(W.recovery_doubtful) else null
                    // Each goods line with what the receipt says of it (15 Nov 2026, records group): its rate, a free item's mark and the
                    // dealer's advice (crop, pest, dose) — ReceiptGoods' own words, so the card and the receipt agree.
                    val goods = if (items.isNotEmpty()) items.joinToString("; ") { ei ->
                        val what = Format.goods(ei.itemName, ei.quantity, ei.unit).orEmpty()
                        val rate: Double? = ei.rate
                        val price = when {
                            ei.isBonus -> " (" + c.getString(W.bonus_tag) + ")"
                            rate != null -> " \u00D7 " + AnswerBook.money(rate)
                            else -> ""
                        }
                        what + price + (ReceiptGoods.advice(ei)?.let { a -> " \u2014 $a" }.orEmpty())
                    } else entry?.let { Format.goods(it.itemName, it.quantity, it.unit) }
                    AnswerCard(Mood.CALM, label = name, figure = AnswerBook.money(last.amount),
                        lines = listOfNotNull(
                            line(c, W.hudhud_l_last_entry, whenOf(c, last.timestamp) + " · " + way),
                            entry?.entryNumber?.takeIf { it.isNotBlank() }?.let { line(c, W.hudhud_l_entry_no, it) },
                            paid?.let { line(c, W.hudhud_l_entry_paid, it) },
                            goods?.takeIf { it.isNotBlank() }?.let { line(c, W.hudhud_l_entry_goods, it) },
                            entryNote?.let { line(c, W.hudhud_l_entry_note, it) },
                            writer?.let { line(c, W.hudhud_l_entry_by, it) },
                            rateWord?.let { line(c, W.rate_type_label, it) },
                            seasonWord?.let { c.getString(W.season_entry, it) },
                            doubtWord?.let { line(c, W.recovery_label, it) },
                            balance
                        ),
                        openParty = p.id, money = true,
                        secrets = entryNote?.let { HudhudNote.secretsIn(it) }.orEmpty() +
                            listOfNotNull(entry?.paymentRef?.takeIf { it.isNotBlank() }))
                }
            }
            Hudhud.Facet.SINCE -> {
                val points = LedgerLines.of(dao, listOf(p.id))
                val first = points.minOfOrNull { it.timestamp }
                if (first == null) {
                    AnswerCard(Mood.CALM, label = name, lines = listOf(c.getString(W.party_no_entries)), openParty = p.id)
                } else {
                    AnswerCard(Mood.CALM, label = name, figure = Format.dateOnly(first),
                        lines = listOfNotNull(c.getString(W.hudhud_l_since), line(c, W.hudhud_l_entries, n(points.size)),
                            party?.let { line(c, W.hudhud_l_added, Format.dateOnly(it.createdAt)) }, openingLine(c, points)),
                        openParty = p.id)
                }
            }
            Hudhud.Facet.ENTRIES -> {
                val points = LedgerLines.of(dao, listOf(p.id))
                val gave = points.filter { it.isGiven }
                val got = points.filter { !it.isGiven }
                AnswerCard(Mood.CALM, label = name, figure = n(points.size),
                    lines = listOf(
                        c.getString(W.hudhud_l_entries),
                        line(c.getString(W.i_gave), n(gave.size) + " · " + AnswerBook.money(gave.sumOf { it.amount })),
                        line(c.getString(W.i_got), n(got.size) + " · " + AnswerBook.money(got.sumOf { it.amount }))
                    ),
                    openParty = p.id)
            }
            Hudhud.Facet.LIMIT -> {
                val limit = party?.creditLimit?.takeIf { it > 0.0 }
                if (limit == null) {
                    AnswerCard(Mood.CALM, label = name, lines = listOf(c.getString(W.hudhud_limit_none), balance), openParty = p.id)
                } else {
                    val used = if (p.balance > 0) Format.percent(p.balance / limit * 100.0) + "%" else "0%"
                    val over = p.balance > limit
                    AnswerCard(if (over) Mood.CARE else Mood.CALM, label = name, figure = AnswerBook.money(limit),
                        lines = listOf(c.getString(W.credit_limit), balance, line(c, W.hudhud_l_limit_used, used)),
                        openParty = p.id, money = true)
                }
            }
            Hudhud.Facet.PROMISE -> {
                val day = party?.harvestPromise
                if (day == null) {
                    AnswerCard(Mood.CALM, label = name, lines = listOf(c.getString(W.hudhud_promise_none), balance), openParty = p.id)
                } else {
                    val passed = DayMath.startOfDay(day) < DayMath.startOfDay(c.now()) && Money.isPositive(p.balance)
                    AnswerCard(if (passed) Mood.CARE else Mood.CALM, label = name, figure = Format.dateOnly(day),
                        lines = listOfNotNull(c.getString(W.hudhud_l_promise), if (passed) c.getString(W.hudhud_promise_passed) else null, balance),
                        openParty = p.id)
                }
            }
            Hudhud.Facet.NOTE -> {
                // The owner's private note (14 Nov 2026, the owner: whoever holds the phone sees everything): told whole, a line of
                // the note to a line of the card, as the customer's page shows it. A long number in it is shown and never said.
                val note = party?.note?.trim().orEmpty()
                when {
                    note.isNotEmpty() -> AnswerCard(Mood.CALM, label = name,
                        lines = listOf(c.getString(W.party_note_label)) + HudhudNote.linesOf(note).map { Format.isolate(it) },
                        openParty = p.id, secrets = HudhudNote.secretsIn(note),
                        // At a counter the note is written, never said: the customer may be standing there (D8, T39).
                        quiet = AnswerGate.atCounter())
                    // A helper's copy of the book carries no private note at all: "none saved" would not be true there.
                    c.viewer() -> AnswerCard(Mood.CALM, label = name,
                        lines = listOf(c.getString(W.hudhud_note_helper)), openParty = p.id)
                    else -> AnswerCard(Mood.CALM, label = name,
                        lines = listOf(c.getString(W.hudhud_note_none, Format.isolate(name))), openParty = p.id)
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ goods

    /**
     * A product's facts: price and stock for everyone at the counter; what it cost, which supplier charged what and how
     * much sold, behind REPORTS. Suppliers' rates are their last rate each, from the bill lines (Purchase rates' own
     * reading), cheapest first, with the day each was charged — an old rate stands beside its date, never as today's.
     */
    suspend fun goods(c: AnswerEnv, dao: KhataDao, ids: List<Long>, facet: Hudhud.GoodsFacet, window: VoiceQuestion.Window?): AnswerCard {
        val products = dao.productsOnce()
        val found = ids.mapNotNull { id -> products.firstOrNull { it.id == id } }
        if (found.isEmpty()) return AnswerBook.unknown(c)
        if (found.size > 1) {
            return AnswerCard(Mood.CALM, label = c.getString(W.hudhud_pick_party),
                rows = found.map { Row(0, it.name, it.salePrice?.let { v -> AnswerBook.money(v) } ?: "", it.company, open = ScreenId.PRODUCTS) }, quiet = true)
        }
        val p = found.first()
        val name = p.name
        val unit = p.defaultUnit?.takeIf { it.isNotBlank() }
        fun perUnit(v: Double) = AnswerBook.money(v) + (unit?.let { " / " + UnitWords.label(it) } ?: "")
        // Not caught (stage 7, F2): a valuation that failed is no product with "no stock line" or one that is not low.
        val stock = Valuation.compute(dao).rows.firstOrNull { it.productId == p.id }
        val lines = dao.supplierRateLinesOnce().filter { l ->
            l.rate > 0.0 && (l.productId == p.id || (l.productId == null && l.productName.trim().equals(name.trim(), ignoreCase = true)))
        }.sortedByDescending { it.billDate }
        fun priceLines(): List<String> = listOfNotNull(
            p.salePrice?.let { line(c, W.hudhud_l_cash_price, perUnit(it)) },
            p.creditPrice?.let { line(c, W.credit_price_hint, perUnit(it)) }
        )
        // Read once into locals: the shared module's properties do not smart-cast here.
        val onHand: Double? = stock?.onHand
        val stockUnit: String? = stock?.unit
        val isLow = stock?.isLow == true
        fun stockLine(): String? = if (stock == null) null else line(c, W.hudhud_l_stock,
            if (onHand == null) c.getString(W.stock_no_movement) else Format.qty(onHand, stockUnit))
        val open = ScreenId.PRODUCTS
        return when (facet) {
            Hudhud.GoodsFacet.PRICE -> {
                val pl = priceLines()
                if (pl.isEmpty()) {
                    AnswerCard(Mood.CALM, label = name, lines = listOf(c.getString(W.hudhud_price_none)), open = open)
                } else {
                    val v = p.salePrice ?: p.creditPrice ?: 0.0
                    AnswerCard(Mood.CALM, label = name, figure = perUnit(v), lines = pl, open = open, money = true)
                }
            }
            Hudhud.GoodsFacet.STOCK -> {
                if (onHand == null) {
                    AnswerCard(Mood.CALM, label = name, lines = listOf(c.getString(W.stock_no_movement)), open = open)
                } else {
                    val left = Format.qty(onHand, stockUnit)
                    AnswerCard(if (isLow) Mood.CARE else Mood.CALM, label = name, figure = left,
                        lines = listOfNotNull(c.getString(W.hudhud_l_stock), if (isLow) c.getString(W.hudhud_t_low) else null),
                        open = open)
                }
            }
            Hudhud.GoodsFacet.COST -> {
                if (!reports()) return ownerOnly(c, name)
                val cost = p.costPrice ?: stock?.cost
                val last = lines.firstOrNull()
                if (cost == null && last == null) {
                    AnswerCard(Mood.CALM, label = name, lines = listOf(c.getString(W.hudhud_cost_none)), open = ScreenId.SUPPLIER_RATES)
                } else {
                    val shown = cost ?: last!!.rate
                    AnswerCard(Mood.CALM, label = name, figure = perUnit(shown),
                        lines = listOfNotNull(c.getString(W.hudhud_l_cost),
                            last?.let { line(c, W.hudhud_l_last_in, it.supplierName + " · " + Format.dateOnly(it.billDate) + " · " + AnswerBook.money(it.rate)) }),
                        open = ScreenId.SUPPLIER_RATES, money = true)
                }
            }
            Hudhud.GoodsFacet.SUPPLIERS -> {
                if (!reports()) return ownerOnly(c, name)
                // Each supplier's own last rate, cheapest first.
                val each = lines.groupBy { it.supplierId }.values.map { it.first() }.sortedBy { it.rate }
                if (each.isEmpty()) {
                    AnswerCard(Mood.CALM, label = name, lines = listOf(c.getString(W.hudhud_rates_none)), open = ScreenId.SUPPLIER_RATES)
                } else {
                    val cheap = each.first()
                    val dear = each.last()
                    AnswerCard(Mood.CALM, label = name,
                        lines = listOfNotNull(
                            line(c, W.hudhud_l_cheapest, cheap.supplierName + " · " + AnswerBook.money(cheap.rate)),
                            if (each.size > 1) line(c, W.hudhud_l_dearest, dear.supplierName + " · " + AnswerBook.money(dear.rate)) else null
                        ),
                        rows = each.take(TOP).map { Row(it.supplierId, it.supplierName, AnswerBook.money(it.rate), Format.dateOnly(it.billDate)) },
                        open = ScreenId.SUPPLIER_RATES, money = true)
                }
            }
            Hudhud.GoodsFacet.LAST_IN -> {
                val last = lines.firstOrNull()
                if (last == null) {
                    AnswerCard(Mood.CALM, label = name, lines = listOf(c.getString(W.hudhud_rates_none)), open = ScreenId.BILLS)
                } else {
                    val rate = if (reports()) " · " + AnswerBook.money(last.rate) else ""
                    AnswerCard(Mood.CALM, label = name, figure = Format.dateOnly(last.billDate),
                        lines = listOf(c.getString(W.hudhud_l_last_in), last.supplierName + rate),
                        rows = listOf(Row(last.supplierId, last.supplierName, if (reports()) AnswerBook.money(last.rate) else "", Format.dateOnly(last.billDate))),
                        open = ScreenId.BILLS)
                }
            }
            Hudhud.GoodsFacet.BUYERS -> {
                // The days asked, when a span was said ("aaj Urea kis ne li"); else every sale of it.
                val (from, to) = if (window == null) 0L to Long.MAX_VALUE else Hudhud.span(window, c.now())
                val who = dao.customersWhoBought(p.id, from, to)
                val shown = if (window == null) name else name + " · " + c.getString(AnswerBook.windowWord(window))
                // The screen opens on the same days (17 Nov 2026, audit L020); with no span, or the whole book, on its own windows.
                val extras = if (window == null || window == VoiceQuestion.Window.ALL_TIME) OpenWith.ProductBuyers(p.id, p.name)
                else OpenWith.ProductBuyers(p.id, p.name, from, to)
                // The buyers query carries no balance (it says 0 for everyone): each one's balance is read as Khata adds
                // it up, or a buyer who owes would be said to be settled.
                val balances = if (who.isEmpty()) emptyMap() else dao.partiesWithBalanceOnce().associate { it.id to it.balance }
                if (who.isEmpty()) {
                    AnswerCard(Mood.CALM, label = shown, lines = listOf(c.getString(if (window == null) W.hudhud_buyers_none else W.hudhud_buyers_none_span)))
                } else {
                    AnswerCard(Mood.CALM, label = shown, figure = n(who.size), lines = listOf(c.getString(W.hudhud_t_customers)),
                        rows = who.take(TOP).map { b ->
                            val balance = balances[b.id] ?: 0.0
                            Row(b.id, b.name, AnswerBook.money(abs(balance)), AnswerBook.side(c, balance))
                        },
                        open = ScreenId.PRODUCT_CUSTOMERS, openWith = extras)
                }
            }
            Hudhud.GoodsFacet.SOLD -> {
                val w = window ?: VoiceQuestion.Window.THIS_MONTH
                val label = name + " · " + c.getString(AnswerBook.windowWord(w))
                if (!reports()) return ownerOnly(c, label)
                val (from, to) = Hudhud.span(w, c.now())
                val sold = dao.topProductsBetween(from, to, ALL_PRODUCTS).firstOrNull { it.name.trim().equals(name.trim(), ignoreCase = true) }
                if (sold == null || sold.qty <= 0.0) {
                    AnswerCard(Mood.CALM, label = label, lines = listOf(c.getString(W.hudhud_sold_none)))
                } else {
                    val q = Format.qty(sold.qty, sold.unit)
                    AnswerCard(Mood.GOOD, label = label, figure = q, lines = listOf(c.getString(W.hudhud_l_sold)),
                        open = ScreenId.SALES_REPORT)
                }
            }
            Hudhud.GoodsFacet.COVER -> {
                // How long the shelf lasts at the last 30 days' pace (StockCover); a book younger than that is divided by its own age.
                val label = name + " · " + c.getString(W.hudhud_chip_cover)
                if (!reports()) return ownerOnly(c, label)
                val now = c.now()
                val first = dao.firstSaleAt()
                val windowDays = if (first != null && first > now - StockCover.WINDOW_MS)
                    kotlin.math.ceil((now - first) / 86_400_000.0).toInt().coerceIn(1, StockCover.DAYS) else StockCover.DAYS
                val soldRows = dao.topProductsBetween(now - StockCover.WINDOW_MS, now + 86_400_000L, ALL_PRODUCTS)
                    .filter { it.name.trim().equals(name.trim(), ignoreCase = true) }
                val sold = StockCover.soldBase(soldRows, p, stockUnit ?: unit)
                when (val cover = StockCover.of(onHand, sold, windowDays)) {
                    is StockCover.Cover.Days -> {
                        val basis = c.getString(W.hudhud_cover_basis, windowDays, Format.qty(cover.perDay, stockUnit ?: unit))
                        if (cover.days == 0) {
                            AnswerCard(Mood.CARE, label = label, lines = listOf(c.getString(W.hudhud_cover_under_day), basis), open = open)
                        } else {
                            AnswerCard(if (isLow || cover.days <= 7) Mood.CARE else Mood.CALM, label = label,
                                figure = c.quantity(WP.radar_days, cover.days, cover.days),
                                lines = listOf(line(c, W.hudhud_l_stock, Format.qty(onHand ?: 0.0, stockUnit)), basis), open = open)
                        }
                    }
                    StockCover.Cover.NoStock -> AnswerCard(Mood.CARE, label = label, lines = listOf(c.getString(W.hudhud_cover_out)), open = open)
                    StockCover.Cover.NoSales -> AnswerCard(Mood.CALM, label = label, lines = listOf(c.getString(W.hudhud_cover_no_sales)), open = open)
                    StockCover.Cover.Unknown -> AnswerCard(Mood.CALM, label = label, lines = listOf(c.getString(W.stock_no_movement)), open = open)
                }
            }
            // ---- The pack and the record (15 Nov 2026, the catalog's records group). What is printed on the pack, where it is kept
            // and when it came into the book is told to everyone who can open the product's screen; what it cost stays behind REPORTS.
            Hudhud.GoodsFacet.LABEL -> {
                val said = listOfNotNull(
                    p.company, p.category, p.productType, p.technicalName, p.formulation, p.registrationNumber
                ).any { it.isNotBlank() }
                if (!said) {
                    AnswerCard(Mood.CALM, label = name, lines = listOf(c.getString(W.hudhud_product_fields_none)), open = open)
                } else {
                    fun text(v: String?, label: String) = v?.trim()?.takeIf { it.isNotEmpty() }?.let { line(c, label, it) }
                    AnswerCard(Mood.CALM, label = name,
                        lines = listOfNotNull(
                            text(p.company, W.csv_col_company), text(p.category, W.csv_col_category),
                            text(p.productType, W.csv_col_type), text(p.technicalName, W.hudhud_l_technical),
                            text(p.formulation, W.hudhud_l_formulation), text(p.registrationNumber, W.hudhud_l_reg)
                        ),
                        open = open, secrets = listOfNotNull(p.registrationNumber?.trim()?.takeIf { it.isNotEmpty() }))
                }
            }
            Hudhud.GoodsFacet.BARCODE -> {
                val code = p.barcode?.trim()?.takeIf { it.isNotEmpty() }
                if (code == null) {
                    AnswerCard(Mood.CALM, label = name, lines = listOf(c.getString(W.hudhud_product_fields_none)), open = open)
                } else {
                    AnswerCard(Mood.CALM, label = name, figure = Format.ltr(code), lines = listOf(c.getString(W.csv_col_barcode)),
                        open = open, secrets = listOf(code))
                }
            }
            Hudhud.GoodsFacet.PACK -> {
                val packUnit = p.packUnit?.trim()?.takeIf { it.isNotEmpty() }
                val size = p.packSize
                val tax = p.taxRate
                val variant = p.variant?.trim()?.takeIf { it.isNotEmpty() }
                if (packUnit == null && size == null && p.packPrice == null && variant == null) {
                    AnswerCard(Mood.CALM, label = name, lines = listOf(c.getString(W.hudhud_product_fields_none)), open = open)
                } else {
                    AnswerCard(Mood.CALM, label = name,
                        lines = listOfNotNull(
                            packUnit?.let { line(c, W.hudhud_l_pack, it + (size?.let { s -> " \u00B7 " + Format.qty(s, unit) } ?: "")) },
                            p.packPrice?.let { line(c, W.product_pack_price, AnswerBook.money(it)) },
                            variant?.let { line(c, W.hudhud_l_variant, it) }
                        ),
                        open = open)
                }
            }
            Hudhud.GoodsFacet.TAX -> {
                val rate = p.taxRate
                if (rate == null) {
                    AnswerCard(Mood.CALM, label = name, lines = listOf(c.getString(W.hudhud_product_fields_none)), open = open)
                } else {
                    AnswerCard(Mood.CALM, label = name, figure = c.getString(W.scheme_reward_pct_value, Format.percent(rate)),
                        lines = listOf(c.getString(W.product_tax_rate)), open = open)
                }
            }
            Hudhud.GoodsFacet.SHELF -> {
                val place = p.note?.trim()?.takeIf { it.isNotEmpty() }
                if (place == null) {
                    AnswerCard(Mood.CALM, label = name, lines = listOf(c.getString(W.hudhud_product_fields_none)), open = open)
                } else {
                    AnswerCard(Mood.CALM, label = name, lines = listOf(line(c, W.hudhud_l_shelf, place)), open = open,
                        secrets = HudhudNote.secretsIn(place))
                }
            }
            Hudhud.GoodsFacet.ADDED -> {
                AnswerCard(Mood.CALM, label = name, figure = Format.dateOnly(p.createdAt), lines = listOf(c.getString(W.hudhud_l_added)), open = open)
            }
            Hudhud.GoodsFacet.BATCHES -> {
                // What is left of each batch, soonest expiry first: Fefo's own figure, the one the counter and Expiry read.
                val lots = Fefo.lots(dao, p).filter { it.left > 0.0 }
                    .sortedWith(compareBy({ it.expiryDate ?: Long.MAX_VALUE }, { it.batchNumber.orEmpty() }))
                if (lots.isEmpty()) {
                    AnswerCard(Mood.CALM, label = name, lines = listOf(c.getString(W.hudhud_batches_none)), open = open)
                } else {
                    val soonest = lots.firstNotNullOfOrNull { it.expiryDate }
                    val now = c.now()
                    AnswerCard(if (soonest != null && soonest < now) Mood.CARE else Mood.CALM, label = name,
                        figure = soonest?.let { Format.dateOnly(it) } ?: n(lots.size),
                        lines = listOf(c.getString(if (soonest != null) W.expiry_date else W.inspector_count_batches)),
                        rows = lots.take(TOP).map { l ->
                            Row(0, l.batchNumber?.takeIf { it.isNotBlank() } ?: name, Format.qty(l.left, l.unit ?: stockUnit ?: unit),
                                l.expiryDate?.let { d -> Format.dateOnly(d) })
                        },
                        open = open, secrets = lots.mapNotNull { it.batchNumber?.takeIf { b -> b.isNotBlank() } })
                }
            }
            Hudhud.GoodsFacet.SCHEME -> {
                // The company schemes of this product's company (the Schemes screen's own match is by the company's name).
                val company = p.company?.trim()?.takeIf { it.isNotEmpty() }
                val mine = if (company == null) emptyList() else dao.observeSchemes().first()
                    .filter { !it.isDeleted && it.company?.trim().equals(company, ignoreCase = true) }
                if (mine.isEmpty()) {
                    AnswerCard(Mood.CALM, label = name, lines = listOf(c.getString(W.hudhud_scheme_none)), open = ScreenId.SCHEMES)
                } else {
                    schemeAnswer(c, dao, mine, name)
                }
            }
            Hudhud.GoodsFacet.OVERVIEW -> {
                val all = priceLines() + listOfNotNull(stockLine()) +
                    if (reports()) listOfNotNull(lines.firstOrNull()?.let {
                        line(c, W.hudhud_l_last_in, it.supplierName + " · " + Format.dateOnly(it.billDate) + " · " + AnswerBook.money(it.rate))
                    }) else emptyList()
                val price = p.salePrice ?: p.creditPrice
                AnswerCard(Mood.CALM, label = name, lines = all.ifEmpty { listOf(c.getString(W.hudhud_price_none)) }, open = open, money = true)
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ the shop

    /**
     * The shop's profile as Business Profile holds it; a field never filled in is said to be empty, not invented.
     *
     * Who is told what (14 Nov 2026, the owner's choice A: a cashier sees only what his own screens show). Business
     * Profile is the owner's screen (SETTINGS), but the shop's name, address, phone, currency and trade are on what a
     * cashier hands out and reads every day (receipts, cards, rate lists, posters, every amount), so they are told to
     * everyone. The shop's country (no screen of a cashier names it), email, tax number and bank details (account
     * title, IBAN, wallet) are the private part: the owner only, here as there — and they are left out of the
     * whole-profile card too, so a number a cashier may not see is never in a line or in [AnswerCard.secrets].
     * `scripts/gen_hudhud_catalog.py` holds each of these to the screen that shows it.
     */
    fun shop(c: AnswerEnv, facet: Hudhud.ShopFacet): AnswerCard {
        val name = c.profile.businessName()
        val address = c.profile.businessAddress()
        val phone = c.profile.businessPhone()
        val owner = settings()
        val email = if (owner) c.profile.businessEmail() else null
        val tax = if (owner) c.profile.strn() else null
        val country = c.profile.country()
        val countryName = Locale("", country.code).getDisplayCountry(c.locale).ifBlank { country.code }
        val trade = c.profile.trade()?.let { c.tradeLabel(it) }
        val nameUrdu = c.profile.businessNameUrdu()?.takeIf { it.isNotBlank() }
        val terms = if (owner) c.profile.termsAndConditions()?.takeIf { it.isNotBlank() } else null
        val open = ScreenId.BUSINESS_SETTINGS
        /** One field: written on the card; said aloud only when it is words, never a number (a phone, a tax number). */
        fun one(label: (AnswerEnv) -> String, value: String?, sayIt: Boolean): AnswerCard =
            if (value.isNullOrBlank()) {
                AnswerCard(Mood.CALM, label = label(c), lines = listOf(c.getString(W.hudhud_not_set)), open = open)
            } else {
                AnswerCard(Mood.CALM, label = label(c), figure = if (sayIt) value else Format.ltr(value), open = open,
                    secrets = if (sayIt) emptyList() else listOf(value))
            }
        fun res(id: String): (AnswerEnv) -> String = { it.getString(id) }
        return when (facet) {
            Hudhud.ShopFacet.NAME -> one(res(W.business_name), name, true)
            Hudhud.ShopFacet.ADDRESS -> one(res(W.business_address), address, true)
            Hudhud.ShopFacet.PHONE -> one(res(W.hudhud_l_phone), phone, false)
            Hudhud.ShopFacet.EMAIL ->
                if (!owner) ownerExact(c, c.getString(W.hudhud_l_email)) else one(res(W.hudhud_l_email), email, false)
            Hudhud.ShopFacet.TAX ->
                if (!owner) ownerExact(c, c.profile.taxLabel()) else one({ it.profile.taxLabel() }, tax, false)
            Hudhud.ShopFacet.CURRENCY -> one(res(W.hudhud_l_currency), c.profile.currency(), true)
            Hudhud.ShopFacet.COUNTRY ->
                if (!owner) ownerExact(c, c.getString(W.hudhud_l_country)) else one(res(W.hudhud_l_country), countryName, true)
            Hudhud.ShopFacet.TRADE -> one(res(W.hudhud_l_trade), trade, true)
            Hudhud.ShopFacet.BANK -> if (!owner) ownerExact(c, c.getString(W.hudhud_l_bank)) else {
                val bank = listOfNotNull(
                    c.profile.bankName()?.takeIf { it.isNotBlank() },
                    c.profile.bankAccountTitle()?.takeIf { it.isNotBlank() },
                    c.profile.bankIban()?.takeIf { it.isNotBlank() }?.let { Format.ltr(it) },
                    c.profile.bankJazzCash()?.takeIf { it.isNotBlank() }?.let { Format.ltr(it) }
                )
                if (bank.isEmpty()) one(res(W.hudhud_l_bank), null, false)
                else AnswerCard(Mood.CALM, label = c.getString(W.hudhud_l_bank), lines = bank, open = open,
                    secrets = listOfNotNull(c.profile.bankIban(), c.profile.bankJazzCash()).filter { it.isNotBlank() })
            }
            Hudhud.ShopFacet.ALL -> {
                val lines = listOfNotNull(
                    address?.takeIf { it.isNotBlank() }?.let { line(c, W.business_address, it) },
                    phone?.takeIf { it.isNotBlank() }?.let { line(c.getString(W.hudhud_l_phone), Format.ltr(it)) },
                    email?.takeIf { it.isNotBlank() }?.let { line(c.getString(W.hudhud_l_email), Format.ltr(it)) },
                    tax?.takeIf { it.isNotBlank() }?.let { line(c.profile.taxLabel(), Format.ltr(it)) },
                    trade?.let { line(c, W.hudhud_l_trade, it) },
                    if (owner) line(c, W.hudhud_l_country, countryName) else null,
                    line(c, W.hudhud_l_currency, c.profile.currency()),
                    // What the shop prints (15 Nov 2026, the catalog's profile fields): the name it uses in Urdu and the terms on every
                    // invoice. The Urdu name is on posters and cards anyone prints, so it is everyone's; the terms are written on the settings screen, so they are the owner's, cut at 200 letters.
                    nameUrdu?.let { line(c, W.business_name_urdu, it) },
                    terms?.let { line(c, W.invoice_terms, if (it.length > 200) it.take(200).trimEnd() + "\u2026" else it) }
                )
                AnswerCard(Mood.CALM, label = name?.takeIf { it.isNotBlank() } ?: c.getString(W.hudhud_chip_shop),
                    lines = if (name.isNullOrBlank()) listOf(c.getString(W.hudhud_not_set)) + lines else lines, open = open,
                    secrets = listOfNotNull(phone, email, tax).filter { it.isNotBlank() } + HudhudNote.secretsIn(terms.orEmpty()))
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ about

    fun about(c: AnswerEnv, topic: Hudhud.AboutTopic): AnswerCard = when (topic) {
        Hudhud.AboutTopic.APP -> said(c, W.hudhud_about_app, ScreenId.ABOUT)
        // The developer, said by Hudhud himself (14 Nov 2026, the owner), and always with the one line that the work is by
        // Allah's grace and tawfiq — the only place that line is part of an answer nobody asked it of.
        Hudhud.AboutTopic.MAKER -> AnswerCard(
            Mood.CALM, lines = listOf(c.getString(W.hudhud_about_maker), c.getString(W.hudhud_faith_credit)),
            open = ScreenId.ABOUT
        )
        Hudhud.AboutTopic.TAWHEED -> faith(c, HudhudFaith.Topic.TAWHEED, W.hudhud_faith_tawheed_1, W.hudhud_faith_tawheed_2)
        Hudhud.AboutTopic.HAMD -> faith(c, HudhudFaith.Topic.HAMD, W.hudhud_faith_hamd_1, W.hudhud_faith_hamd_2)
        Hudhud.AboutTopic.RISALAT -> faith(c, HudhudFaith.Topic.RISALAT, W.hudhud_faith_risalat_1, W.hudhud_faith_risalat_2)
        // The app's own history and his own story (14 Nov 2026, [HudhudStory]): asked for by their own words, nothing else.
        Hudhud.AboutTopic.APP_STARTED, Hudhud.AboutTopic.APP_PUBLISHED, Hudhud.AboutTopic.HUDHUD_ADDED,
        Hudhud.AboutTopic.APP_JOURNEY -> history(c, topic)
        Hudhud.AboutTopic.HOOPOE_STORY -> story(c)
        Hudhud.AboutTopic.PRIVACY -> said(c, W.hudhud_about_privacy, ScreenId.ABOUT)
        Hudhud.AboutTopic.LANGUAGES -> said(c, W.hudhud_about_languages, ScreenId.LANGUAGE)
        Hudhud.AboutTopic.NAME_STORY -> AnswerCard(Mood.GOOD, lines = listOf(c.getString(W.hudhud_about_name_story)))
        Hudhud.AboutTopic.VERSION -> {
            val version = HudhudReadFailure.bestEffort<String?>(null) { c.appVersion() } ?: "?"
            AnswerCard(Mood.CALM, lines = listOf(c.getString(W.hudhud_about_version, version)), open = ScreenId.ABOUT)
        }
        Hudhud.AboutTopic.OWNER_NAME -> {
            val me = c.callMe()
            if (me == null) {
                AnswerCard(Mood.CALM, lines = listOf(c.getString(W.hudhud_owner_unnamed)), openLabel = W.hudhud_settings,
                    voiceSettings = true)
            } else {
                AnswerCard(Mood.GOOD, label = me, lines = listOf(c.getString(W.hudhud_owner_named, Format.isolate(me))),
                    openLabel = W.hudhud_settings, voiceSettings = true)
            }
        }
    }

    /**
     * Tawheed, hamd and sana, the Prophet's ﷺ risalat — asked for by name and nothing else ([HudhudFaith]): his two spoken
     * paragraphs, then the verses as quotations, which are shown and never said, and the note that the words are a brief
     * meaning and not a translation.
     */
    private fun faith(c: AnswerEnv, topic: HudhudFaith.Topic, first: String, second: String): AnswerCard {
        val words = listOf(first, second)
        return AnswerCard(
            Mood.CALM, lines = words.map { c.getString(it) },
            verses = HudhudFaith.versesOf(topic).map { AnswerCard.Verse(it.surah, it.ref, it.arabic) },
            verseNote = c.getString(W.hudhud_faith_note),
            // A matter of faith is told quietly: no call before it, no glad hop after it, and its Personal tab (HudhudSheet).
            reverent = true,
            spoken = { w -> words.joinToString(" ") { HudhudSpeech.withoutVerseRefs(w.getString(it)) } }
        )
    }

    // ------------------------------------------------------------------------------------------------ his story, the app's history

    /** The ten parts of Hudhud's own story, then the Quran's outcome ([HudhudStory]): one string each, in the app's nine languages. */
    private val STORY = listOf(
        W.hudhud_story_1, W.hudhud_story_2, W.hudhud_story_3, W.hudhud_story_4,
        W.hudhud_story_5, W.hudhud_story_6, W.hudhud_story_7, W.hudhud_story_8,
        W.hudhud_story_9, W.hudhud_story_10, W.hudhud_story_11
    )

    /**
     * The hoopoe of Prophet Sulaiman (peace be upon him), in his own first person as far as the Quran speaks of him
     * (Surah An-Naml, 27:16-44), then the Quran's own telling from where it goes on without him, and the outcome as the
     * Quran says it: the queen's submission to Allah (27:44). The two verses that matter most are shown in Arabic under
     * the words and never said aloud; the words themselves are a brief telling, not a translation, and say so, and send
     * the owner to a qualified scholar. Everything is read out in the voice's own language, a sentence at a time, with
     * the verse addresses left out for the ear.
     */
    private fun story(c: AnswerEnv): AnswerCard = AnswerCard(
        Mood.CALM, label = c.getString(W.hudhud_t_story),
        lines = STORY.map { c.getString(it) },
        verses = HudhudStory.VERSES.map { AnswerCard.Verse(it.surah, it.ref, it.arabic) },
        verseNote = c.getString(W.hudhud_story_note),
        reverent = true,
        spoken = { w -> STORY.joinToString(" ") { HudhudSpeech.withoutVerseRefs(w.getString(it)) } }
    )

    /** A day of the app's history as a moment: midday, so that no time zone moves it to another day. */
    private fun millisOf(d: HistoryDay): Long =
        Calendar.getInstance().apply { clear(); set(d.year, d.month - 1, d.day, 12, 0, 0) }.timeInMillis

    /** [d] written in [c]'s language (the voice's own words when he is asked again for another voice). */
    private fun dayText(c: AnswerEnv, d: HistoryDay): String =
        DateWords.format("d MMMM yyyy", millisOf(d), c.locale)

    /** Whole calendar days from [d] to today, never below nought. */
    private fun daysSince(c: AnswerEnv, d: HistoryDay): Long =
        (DayMath.dayNumber(c.now()) - DayMath.dayNumber(millisOf(d))).coerceAtLeast(0)

    /**
     * When the app began, when it was published and when Hudhud was added — only the days the project's own records hold
     * ([AppHistory]): the first work saved, Google's production access, the day it was confirmed public, the first work on
     * Hudhud. The exact first day everyone could see it on Google Play is not in them, and he says so. The word is always
     * "published", never "launched" (the owner's rule). A test build says that it is one. Read from nothing in the book.
     */
    private fun history(c: AnswerEnv, topic: Hudhud.AboutTopic): AnswerCard {
        val started = Format.isolate(dayText(c, AppHistory.STARTED))
        val hudhud = Format.isolate(dayText(c, AppHistory.HUDHUD))
        val startedLine = c.getString(W.hudhud_hist_started, started)
        val publishedLine = c.getString(
            W.hudhud_hist_published, Format.isolate(dayText(c, AppHistory.PLAY_ACCESS)), Format.isolate(dayText(c, AppHistory.PUBLIC_BY))
        )
        val hudhudLine = c.getString(W.hudhud_hist_hudhud, hudhud)
        val testBuild = c.testBuild()
        val testLine = c.getString(W.hudhud_hist_test_build)
        val sinceFirst = line(c, W.hudhud_l_days_since, n(daysSince(c, AppHistory.STARTED).toInt()))
        val sinceHudhud = line(c, W.hudhud_l_days_hudhud, n(daysSince(c, AppHistory.HUDHUD).toInt()))
        return when (topic) {
            Hudhud.AboutTopic.APP_STARTED -> AnswerCard(Mood.CALM, lines = listOf(startedLine, sinceFirst))
            Hudhud.AboutTopic.APP_PUBLISHED ->
                AnswerCard(Mood.CALM, lines = listOfNotNull(publishedLine, testLine.takeIf { testBuild }))
            Hudhud.AboutTopic.HUDHUD_ADDED -> AnswerCard(Mood.CALM, lines = listOf(hudhudLine, sinceHudhud))
            else -> AnswerCard(
                Mood.CALM, label = c.getString(W.hudhud_t_history),
                lines = listOfNotNull(startedLine, publishedLine, hudhudLine, sinceFirst, sinceHudhud, testLine.takeIf { testBuild })
            )
        }
    }

    private fun said(c: AnswerEnv, words: String, open: ScreenId?) =
        AnswerCard(Mood.CALM, lines = listOf(c.getString(words)), open = open)

    /**
     * Today's date in the calendar everyone uses, the Islamic date worked out by the Umm al-Qura calendar (said to be
     * worked out: by the moon it may be a day off), the Persian date where the shop keeps that calendar, and the time.
     */
    fun clock(c: AnswerEnv): AnswerCard {
        val t = c.now()
        val date = DateWords.format("EEEE, d MMMM yyyy", t)
        val key = monthKey(c)
        val (hy, hm, hd) = ShopCalendar.ymd(ShopCalendar.Kind.HIJRI, t)
        val hijri = "$hd ${ShopCalendar.monthNames(ShopCalendar.Kind.HIJRI, key).getOrElse(hm - 1) { "" }} $hy"
        val time = DateWords.format("h:mm a", t)
        val jalali = if (c.profile.calendar() == ShopCalendar.Kind.JALALI) {
            val (jy, jm, jd) = ShopCalendar.ymd(ShopCalendar.Kind.JALALI, t)
            "$jd ${ShopCalendar.monthNames(ShopCalendar.Kind.JALALI, key, c.profile.country()).getOrElse(jm - 1) { "" }} $jy"
        } else null
        return AnswerCard(Mood.CALM, label = c.getString(W.range_today), figure = date,
            lines = listOfNotNull(
                line(c, W.hudhud_l_islamic, hijri), jalali, line(c, W.hudhud_l_time, time),
                c.getString(W.hudhud_hijri_note)
            ),
            spoken = { w ->
                val l = w.locale
                val hm2 = ShopCalendar.monthNames(ShopCalendar.Kind.HIJRI, monthKey(w)).getOrElse(hm - 1) { "" }
                w.getString(W.range_today) + ": " + DateWords.format("EEEE, d MMMM yyyy", t, l) + ". " +
                    w.getString(W.hudhud_l_islamic) + ": " + "$hd $hm2 $hy"
            })
    }

    private fun monthKey(c: AnswerEnv): String {
        val l = c.locale
        return when {
            l.language == "ur" && l.script.equals("Latn", ignoreCase = true) -> "en"
            l.language == "in" -> "id"
            else -> l.language
        }
    }

    // ------------------------------------------------------------------------------------------------ counts

    /**
     * How many of something: the people and goods the book holds now; what was made over a span of days (today's
     * when none was said, with the whole book's count beside it); the app's own counts, with the day they began.
     */
    suspend fun count(c: AnswerEnv, dao: KhataDao, thing: Hudhud.Thing, window: VoiceQuestion.Window?, parties: List<PartyWithBalance>): AnswerCard {
        val (from, to) = spanOf(c, window)
        val w = window ?: VoiceQuestion.Window.TODAY
        return when (thing) {
            Hudhud.Thing.CUSTOMERS -> {
                val all = parties.filter { it.isCustomer }
                val owe = all.count { Money.isPositive(it.balance) }
                counted(c, W.hudhud_t_customers, null, all.size, listOf(line(c.getString(W.you_will_get), n(owe))), ScreenId.KHATA)
            }
            Hudhud.Thing.SUPPLIERS -> {
                val all = parties.filter { !it.isCustomer }
                val owed = all.count { Money.isNegative(it.balance) }
                counted(c, W.hudhud_t_suppliers, null, all.size, listOf(line(c.getString(W.you_will_give), n(owed))), ScreenId.KHATA)
            }
            Hudhud.Thing.PRODUCTS -> {
                // Not caught (stage 7, F2): no low-stock line must never mean "nothing is low" for "the read failed".
                val v = Valuation.compute(dao)
                val count = dao.productsOnce().size
                counted(c, W.tile_products, null, count, listOfNotNull(v.low.size.takeIf { it > 0 }?.let { line(c, W.hudhud_t_low, n(it)) }),
                    ScreenId.PRODUCTS)
            }
            // Staff is the owner's screen (STAFF): who works here is his to know, not a cashier's or a manager's.
            Hudhud.Thing.STAFF -> if (!staff()) ownerExact(c, c.getString(W.hudhud_t_staff))
            else counted(c, W.hudhud_t_staff, null, dao.staffCardsOnce().count { it.isActive }, emptyList(), ScreenId.STAFF)
            Hudhud.Thing.ENTRIES -> {
                val shown = dao.entryCountBetween(from, to)
                val total = if (window == null) dao.entryCountBetween(0L, Long.MAX_VALUE) else null
                // The newest of them as rows, each who, which way and how much (the day book's own entries); the
                // amounts only for those who may see figures, as the other counts.
                val money = reports()
                val rows = if (shown == 0) emptyList() else dao.dayBookEntries(from, to).sortedByDescending { it.timestamp }.take(TOP).map { e ->
                    Row(0, e.partyName, if (money) AnswerBook.money(e.amount) else "",
                        c.getString(if (e.isGiven) W.i_gave else W.i_got) + " \u00B7 " + whenOf(c, e.timestamp),
                        open = ScreenId.KHATA)
                }
                counted(c, W.hudhud_l_entries, w, shown, listOfNotNull(total?.let { line(c, W.range_all, n(it)) }), ScreenId.KHATA, rows)
            }
            Hudhud.Thing.INVOICES -> {
                val made = dao.observeInvoicesFrom(from).first().filter { it.invoiceDate < to && it.kind == DocKind.INVOICE }
                    .sortedByDescending { it.invoiceDate }
                val total = if (window == null) dao.observeDocCount(DocKind.INVOICE).first() else null
                val money = reports()
                val sum = if (money && made.isNotEmpty()) line(c, W.hudhud_l_total, AnswerBook.money(made.sumOf { it.grandTotal })) else null
                val rows = made.take(TOP).map { i ->
                    Row(0, i.customerName, if (money) AnswerBook.money(i.grandTotal) else "", i.invoiceNumber + " \u00B7 " + dayOf(c, i.invoiceDate),
                        open = ScreenId.INVOICES)
                }
                counted(c, W.invoices, w, made.size, listOfNotNull(sum, total?.let { line(c, W.range_all, n(it)) }), ScreenId.INVOICES, rows)
            }
            Hudhud.Thing.BILLS -> {
                // Bills by their own date, as the Bills page lists them: who sent them, what they came to, the first items.
                val bills = dao.observeBills().first()
                val came = bills.filter { it.billDate >= from && it.billDate < to }.sortedByDescending { it.billDate }
                val money = reports()
                val sum = if (money && came.isNotEmpty()) line(c, W.hudhud_l_total, AnswerBook.money(came.sumOf { it.totalAmount })) else null
                val rows = came.take(TOP).map { b ->
                    val items = dao.billItems(b.id).filter { !it.isDeleted }
                    val what = items.take(2).joinToString(", ") { it.productName } + if (items.size > 2) "\u2026" else ""
                    Row(b.partyId, b.partyName, if (money) AnswerBook.money(b.totalAmount) else "",
                        listOf(dayOf(c, b.billDate), what).filter { it.isNotBlank() }.joinToString(" \u00B7 "))
                }
                counted(c, W.supplier_bills, w, came.size,
                    listOfNotNull(sum, if (window == null) line(c, W.range_all, n(bills.size)) else null), ScreenId.BILLS, rows)
            }
            Hudhud.Thing.TICKETS -> {
                val sales = dao.salesBetween(from, to).filter { it.status == "done" }
                val money = reports()
                val sum = if (money && sales.isNotEmpty()) line(c, W.hudhud_l_total, AnswerBook.money(sales.sumOf { it.grandTotal })) else null
                val rows = sales.take(TOP).map { t ->
                    Row(0, t.customerName?.takeIf { it.isNotBlank() } ?: c.getString(W.pos_walk_in),
                        if (money) AnswerBook.money(t.grandTotal) else "", t.saleNumber + " \u00B7 " + whenOf(c, t.timestamp),
                        open = ScreenId.COUNTER_SALE)
                }
                counted(c, W.hudhud_t_tickets, w, sales.size, listOfNotNull(sum), ScreenId.COUNTER_SALE, rows)
            }
            Hudhud.Thing.CALCULATIONS, Hudhud.Thing.APP_OPENS -> {
                val what = thing
                val shown = c.usageCount(what, from, to)
                val since = c.usageSince()
                val title = if (thing == Hudhud.Thing.CALCULATIONS) W.hudhud_t_calculations else W.hudhud_t_app_opens
                // Asked with no day: today's, and every one since this phone began counting.
                val all = if (window == null) c.usageCount(what, 0L, Long.MAX_VALUE) else null
                counted(c, title, w, shown,
                    listOfNotNull(
                        all?.let { line(c, W.hudhud_l_since_counting, n(it)) },
                        since?.let { c.getString(W.hudhud_counted_since, Format.dateOnly(it)) }
                    ),
                    if (thing == Hudhud.Thing.CALCULATIONS) ScreenId.CALCULATOR else null)
            }
        }
    }

    /** A count with its heading; none said plainly ("None in this time"), not as a bare nought. */
    private fun counted(
        c: AnswerEnv, title: String, window: VoiceQuestion.Window?, count: Int, lines: List<String>, open: ScreenId?, rows: List<Row> = emptyList()
    ): AnswerCard {
        val label = heading(c, title, window)
        if (count == 0) {
            val none = c.getString(if (window != null) W.hudhud_count_none else W.hudhud_none_yet)
            return AnswerCard(Mood.CALM, label = label, lines = listOf(none) + lines, open = open)
        }
        return AnswerCard(Mood.CALM, label = label, figure = n(count), lines = lines, rows = rows, open = open)
    }

    // ------------------------------------------------------------------------------------------------ the pages

    suspend fun status(
        c: AnswerEnv, dao: KhataDao, of: Hudhud.StatusOf, parties: List<PartyWithBalance>, state: Hudhud.ChequeState? = null
    ): AnswerCard = when (of) {
        Hudhud.StatusOf.CHEQUES -> if (state == null) cheques(c, dao) else chequesIn(c, dao, state)
        Hudhud.StatusOf.PLANS -> plans(c, dao)
        Hudhud.StatusOf.BILLS -> bills(c, dao, parties)
        Hudhud.StatusOf.LOW_STOCK -> lowStock(c, dao)
        Hudhud.StatusOf.BOOK -> book(c, dao, parties)
        Hudhud.StatusOf.STAFF -> salaries(c, dao)
        Hudhud.StatusOf.INVOICES -> invoicesDue(c, dao)
    }

    /** Cheques not yet cleared, bounced or cancelled: those to bank and those to be cashed, and how many are due. */
    private suspend fun cheques(c: AnswerEnv, dao: KhataDao): AnswerCard {
        val label = c.getString(W.cheques)
        val pending = dao.pendingChequesOnce()
        if (pending.isEmpty()) {
            return AnswerCard(Mood.GOOD, label = label, lines = listOf(c.getString(W.hudhud_cheques_none)), open = ScreenId.CHEQUES)
        }
        val endOfToday = Hudhud.span(VoiceQuestion.Window.TODAY, c.now()).second
        val due = pending.count { it.dueDate < endOfToday }
        val got = pending.filter { it.isReceived }
        val gave = pending.filter { !it.isReceived }
        val money = reports()
        fun part(title: String, list: List<Cheque>) =
            line(c, title, n(list.size) + if (money && list.isNotEmpty()) " · " + AnswerBook.money(list.sumOf { it.amount }) else "")
        return AnswerCard(if (due > 0) Mood.CARE else Mood.CALM, label = label, figure = n(pending.size),
            lines = listOfNotNull(part(W.hudhud_l_cheques_in, got), part(W.hudhud_l_cheques_out, gave),
                if (due > 0) line(c, W.hudhud_l_due, n(due)) else null),
            open = ScreenId.CHEQUES)
    }

    /**
     * Cheques that bounced or cleared (16 Nov 2026), newest first, with the number, the bank and the day on each row. A
     * bounced cheque is still owed; a cleared one already posted its payment. Each row carries the day it was written
     * and the day it was settled, read from the cheque's own table ([AnswerRecordFacts.chequeRows]).
     */
    private suspend fun chequesIn(c: AnswerEnv, dao: KhataDao, state: Hudhud.ChequeState): AnswerCard {
        val bounced = state == Hudhud.ChequeState.BOUNCED
        val label = c.getString(W.cheques) + " \u00B7 " + c.getString(if (bounced) W.cheque_bounced else W.cheque_cleared)
        val want = if (bounced) ChequeStatus.BOUNCED else ChequeStatus.CLEARED
        val list = dao.observeCheques().first().filter { it.status == want }.sortedByDescending { it.dueDate }
        if (list.isEmpty()) {
            return AnswerCard(Mood.CALM, label = label,
                lines = listOf(c.getString(if (bounced) W.hudhud_cheques_bounced_none else W.hudhud_cheques_cleared_none)),
                open = ScreenId.CHEQUES)
        }
        val money = reports()
        fun part(title: String, of: List<ChequeWithParty>) =
            line(c, title, n(of.size) + if (money && of.isNotEmpty()) " \u00B7 " + AnswerBook.money(of.sumOf { it.amount }) else "")
        return AnswerCard(if (bounced) Mood.CARE else Mood.GOOD, label = label, figure = n(list.size),
            lines = listOf(part(W.hudhud_l_cheques_in, list.filter { it.isReceived }),
                part(W.hudhud_l_cheques_out, list.filter { !it.isReceived })),
            rows = AnswerRecordFacts.chequeRows(c, dao, list.take(TOP), money),
            open = ScreenId.CHEQUES)
    }

    /**
     * Invoices with money still due: the screen's own rule — a balance exists where an amount received is written on the
     * invoice and it is less than the total (`totals.balanceDue`). A document that has a due day and no payment written is
     * counted apart, never called unpaid: the book does not say whether it was.
     */
    private suspend fun invoicesDue(c: AnswerEnv, dao: KhataDao): AnswerCard {
        val label = c.getString(W.hudhud_t_invoices_due)
        val endOfToday = Hudhud.span(VoiceQuestion.Window.TODAY, c.now()).second
        val owing = ArrayList<Pair<Invoice, Double>>()
        var unrecorded = 0
        for (inv in dao.invoicesWithPaymentOnce()) {
            if (inv.receivedAmount == null) { unrecorded++; continue }
            val owed = inv.totals(dao.invoiceItems(inv.id)).balanceDue
            if (Money.isPositive(owed)) owing += inv to owed
        }
        val unrecordedLine = if (unrecorded > 0) line(c, W.hudhud_l_invoices_unrecorded, n(unrecorded)) else null
        if (owing.isEmpty()) {
            return AnswerCard(Mood.GOOD, label = label, lines = listOfNotNull(c.getString(W.hudhud_invoices_none), unrecordedLine),
                open = ScreenId.INVOICES)
        }
        val late = owing.count { (inv, _) -> (inv.dueDate ?: Long.MAX_VALUE) < endOfToday }
        val money = reports()
        return AnswerCard(if (late > 0) Mood.CARE else Mood.CALM, label = label, figure = n(owing.size),
            lines = listOfNotNull(
                if (money) line(c, W.hudhud_l_remaining, AnswerBook.money(owing.sumOf { it.second })) else null,
                if (late > 0) line(c, W.hudhud_l_due, n(late)) else null,
                unrecordedLine
            ),
            rows = owing.take(TOP).map { (inv, owed) ->
                Row(0, inv.customerName, if (money) AnswerBook.money(owed) else "",
                    inv.invoiceNumber + (inv.dueDate?.let { " \u00B7 " + dayOf(c, it) } ?: ""), open = ScreenId.INVOICES)
            },
            open = ScreenId.INVOICES)
    }

    /** Open payment plans: how many, how many are due today or late, and the soonest few. */
    private suspend fun plans(c: AnswerEnv, dao: KhataDao): AnswerCard {
        val label = c.getString(W.nav_plans)
        val open = dao.openPlansOnce()
        if (open.isEmpty()) {
            return AnswerCard(Mood.CALM, label = label, lines = listOf(c.getString(W.hudhud_plans_none)), open = ScreenId.PLANS)
        }
        val endOfToday = Hudhud.span(VoiceQuestion.Window.TODAY, c.now()).second
        val due = open.filter { (it.nextDueDate ?: Long.MAX_VALUE) < endOfToday }
        return AnswerCard(if (due.isNotEmpty()) Mood.CARE else Mood.CALM, label = label, figure = n(open.size),
            lines = listOfNotNull(
                if (due.isNotEmpty()) line(c, W.hudhud_l_due, n(due.size)) else null,
                if (reports()) line(c, W.hudhud_l_remaining, AnswerBook.money(open.sumOf { it.remaining })) else null
            ),
            rows = open.take(TOP).map { Row(it.partyId, it.partyName, if (reports()) AnswerBook.money(it.remaining) else "", it.nextDueDate?.let { d -> Format.dateOnly(d) }) },
            open = ScreenId.PLANS)
    }

    /** Supplier bills not paid in full, what is owed to suppliers (Home's sum over them), and the next due. */
    private suspend fun bills(c: AnswerEnv, dao: KhataDao, parties: List<PartyWithBalance>): AnswerCard {
        val label = c.getString(W.supplier_bills)
        val unpaid = dao.observeBills().first().filter { !it.isPaidInFull }.sortedBy { it.dueDate ?: Long.MAX_VALUE }
        if (unpaid.isEmpty()) {
            return AnswerCard(Mood.GOOD, label = label, lines = listOf(c.getString(W.hudhud_bills_none)), open = ScreenId.BILLS)
        }
        val owed = PartyTotals.of(parties.filter { !it.isCustomer }.map { it.balance }).give
        val endOfToday = Hudhud.span(VoiceQuestion.Window.TODAY, c.now()).second
        val due = unpaid.count { (it.dueDate ?: Long.MAX_VALUE) < endOfToday }
        return AnswerCard(if (due > 0) Mood.CARE else Mood.CALM, label = label, figure = n(unpaid.size),
            lines = listOfNotNull(
                if (reports()) line(c, W.hudhud_l_owed_suppliers, AnswerBook.money(owed)) else null,
                if (due > 0) line(c, W.hudhud_l_due, n(due)) else null
            ),
            rows = unpaid.take(TOP).map { Row(it.partyId, it.partyName, if (reports()) AnswerBook.money(it.totalAmount) else "", it.dueDate?.let { d -> Format.dateOnly(d) }) },
            open = ScreenId.BILLS)
    }

    /** Goods at or under the level the owner set: the bell's own list ([Valuation.low]). */
    private suspend fun lowStock(c: AnswerEnv, dao: KhataDao): AnswerCard {
        val label = c.getString(W.hudhud_t_low)
        val v = Valuation.compute(dao)
        val low = v.low
        if (low.isEmpty()) {
            return AnswerCard(Mood.GOOD, label = label, lines = listOf(c.getString(W.hudhud_low_none), line(c, W.tile_products, n(v.rows.size))),
                open = ScreenId.PRODUCTS)
        }
        return AnswerCard(Mood.CARE, label = label, figure = n(low.size),
            rows = low.take(TOP + 1).map { Row(0, it.name, it.onHand?.let { h -> Format.qty(h, it.unit) } ?: "", null, open = ScreenId.PRODUCTS) },
            open = ScreenId.PRODUCTS)
    }

    /**
     * The whole book in a few lines ("jitna data hai uski information"): its people, goods, entries and documents, its
     * last entry and last backup, and — for the owner — what is to get and to give.
     */
    private suspend fun book(c: AnswerEnv, dao: KhataDao, parties: List<PartyWithBalance>): AnswerCard {
        val customers = parties.count { it.isCustomer }
        val suppliers = parties.size - customers
        val entries = dao.entryCountBetween(0L, Long.MAX_VALUE)
        val products = dao.productsOnce().size
        val invoices = dao.observeDocCount(DocKind.INVOICE).first()
        val last = parties.maxOfOrNull { it.lastActivity }?.takeIf { it > 0L }
        val backup = c.lastBackupAt().takeIf { it > 0L }
        val totals = PartyTotals.of(parties.map { it.balance })
        val lines = listOfNotNull(
            line(c, W.hudhud_t_customers, n(customers)),
            line(c, W.hudhud_t_suppliers, n(suppliers)),
            line(c, W.hudhud_l_entries, n(entries)),
            line(c, W.tile_products, n(products)),
            line(c, W.invoices, n(invoices)),
            last?.let { line(c, W.hudhud_l_last_entry, whenOf(c, it)) },
            line(c, W.hudhud_l_last_backup, backup?.let { whenOf(c, it) } ?: c.getString(W.na_backup_never)),
            if (reports()) line(c, W.you_will_get, AnswerBook.money(totals.get)) else null,
            if (reports()) line(c, W.you_will_give, AnswerBook.money(totals.give)) else null
        )
        return AnswerCard(Mood.CALM, label = c.getString(W.hudhud_t_book), lines = lines, open = ScreenId.KHATA)
    }

    /**
     * This month's salaries still to pay, by Staff's own month rule ([Payroll.month]). Staff is the owner's screen
     * (STAFF, not REPORTS), so a manager is not told them either.
     */
    private suspend fun salaries(c: AnswerEnv, dao: KhataDao): AnswerCard {
        val label = c.getString(W.hudhud_t_salary)
        if (!staff()) return ownerExact(c, label)
        val (from, to, days) = Payroll.monthRange(c.now())
        val staff = dao.staffCardsOnce().filter { it.isActive }.sortedBy { it.name }
        val marks = dao.observeAttendanceBetween(from, to).first()
        val pays = dao.observeStaffPaymentsBetween(from, to).first()
        val due = staff.map { person ->
            person to Payroll.month(person.monthlySalary, days, marks.filter { it.staffId == person.id }.map { it.status },
                pays.filter { it.staffId == person.id }).due
        }.filter { it.second > 0.004 }
        if (due.isEmpty()) {
            return AnswerCard(Mood.GOOD, label = label, lines = listOf(c.getString(W.hudhud_salary_none)), open = ScreenId.STAFF)
        }
        val total = due.sumOf { it.second }
        return AnswerCard(Mood.CALM, label = label, figure = AnswerBook.money(total),
            rows = due.take(TOP + 1).map { (s, d) -> Row(0, s.name, AnswerBook.money(d), null, open = ScreenId.STAFF) },
            open = ScreenId.STAFF, money = true)
    }

    // ------------------------------------------------------------------------------------- what came, what happened

    /**
     * Goods that came in on supplier bills over a span (the last few bills when none was said): who sent them, when,
     * the first items of each, and — for the owner — what each bill came to.
     */
    suspend fun deliveries(c: AnswerEnv, dao: KhataDao, window: VoiceQuestion.Window?): AnswerCard {
        val label = heading(c, W.hudhud_t_deliveries, window)
        val all = dao.observeBills().first().sortedByDescending { it.billDate }
        val bills = if (window == null) all.take(TOP) else Hudhud.span(window, c.now()).let { (from, to) -> all.filter { it.billDate >= from && it.billDate < to } }
        if (bills.isEmpty()) {
            return AnswerCard(Mood.CALM, label = label, lines = listOf(c.getString(W.hudhud_deliveries_none)), open = ScreenId.BILLS)
        }
        val rows = bills.take(TOP).map { b ->
            val items = dao.billItems(b.id).filter { !it.isDeleted }
            val what = items.take(2).joinToString(", ") { it.productName } + if (items.size > 2) "…" else ""
            Row(b.partyId, b.partyName, if (reports()) AnswerBook.money(b.totalAmount) else "",
                listOf(dayOf(c, b.billDate), what).filter { it.isNotBlank() }.joinToString(" · "))
        }
        return AnswerCard(Mood.CALM, label = label, figure = if (window != null) n(bills.size) else null, rows = rows, open = ScreenId.BILLS)
    }

    /**
     * What happened in the book over a span (today when none was said) and who did it — the owner's check and balance:
     * entries written, money in (customers' real payments, Insights' rule) and out (the day book's rows), invoices, bills, counter tickets, what was edited or
     * deleted and by whom (the audit log), the calculator's sums and how often the app came to the front.
     */
    suspend fun activity(c: AnswerEnv, dao: KhataDao, window: VoiceQuestion.Window?, over: Over? = null): AnswerCard {
        val w = window ?: VoiceQuestion.Window.TODAY
        val label = over?.label ?: heading(c, W.hudhud_t_activity, w)
        if (!reports()) return ownerOnly(c, label)
        val (from, to) = over?.span ?: Hudhud.span(w, c.now())
        val oneDay = over?.oneDay ?: (w == VoiceQuestion.Window.TODAY || w == VoiceQuestion.Window.YESTERDAY)
        val entries = dao.entryCountBetween(from, to)
        val rows = DayBook.rows(dao, from, to)
        // Money in by the one rule (16 Nov 2026, audit T3 / D-01): what Insights and [collected] say came in.
        val moneyIn = dao.customerReceivedBetween(from, to)
        val moneyOut = rows.filter { it.kind == DayBook.Kind.CASH_OUT || it.kind == DayBook.Kind.BILL_PAID || it.kind == DayBook.Kind.STAFF_PAID }.sumOf { it.amount }
        val invoices = dao.observeInvoicesFrom(from).first().count { it.invoiceDate < to && it.kind == DocKind.INVOICE }
        val bills = dao.observeBills().first().count { it.billDate >= from && it.billDate < to }
        val tickets = dao.doneSaleCount(from, to)
        val audit = dao.latestAudit(AUDIT_READ).filter { it.timestamp >= from && it.timestamp < to }
        val edited = audit.count { it.action in EDITS }
        val deleted = audit.count { it.action in DELETES }
        val voided = audit.count { it.action == AuditAction.SALE_VOIDED || it.action == AuditAction.RETURN_VOIDED }
        val by = audit.mapNotNull { it.user?.takeIf { u -> u.isNotBlank() } }.distinct()
        val sums = c.usageCount(Hudhud.Thing.CALCULATIONS, from, to)
        val opens = c.usageCount(Hudhud.Thing.APP_OPENS, from, to)
        val lines = listOfNotNull(
            line(c, W.hudhud_l_entries, n(entries)),
            if (moneyIn > 0.004) line(c, W.hudhud_l_money_in, AnswerBook.money(moneyIn)) else null,
            if (moneyOut > 0.004) line(c, W.hudhud_l_money_out, AnswerBook.money(moneyOut)) else null,
            if (invoices > 0) line(c, W.invoices, n(invoices)) else null,
            if (bills > 0) line(c, W.supplier_bills, n(bills)) else null,
            if (tickets > 0) line(c, W.hudhud_t_tickets, n(tickets)) else null,
            if (edited > 0 || deleted > 0) line(c.getString(W.hudhud_l_edited), n(edited)) + " · " + line(c.getString(W.hudhud_l_deleted), n(deleted)) else null,
            if (voided > 0) line(c, W.hudhud_l_voided, n(voided)) else null,
            if (by.isNotEmpty()) line(c, W.hudhud_l_by, by.joinToString(", ")) else null,
            if (sums > 0) line(c, W.hudhud_t_calculations, n(sums)) else null,
            if (opens > 0) line(c, W.hudhud_t_app_opens, n(opens)) else null
        )
        val nothing = entries == 0 && rows.isEmpty() && invoices == 0 && bills == 0 && tickets == 0 && audit.isEmpty()
        val open = if (oneDay) ScreenId.DAY_BOOK else ScreenId.AUDIT
        val extras = if (open == ScreenId.DAY_BOOK) OpenWith.Day(from) else null
        return AnswerCard(Mood.CALM, label = label,
            lines = if (nothing) listOf(c.getString(W.hudhud_activity_none)) + lines.drop(1).filter { it.isNotEmpty() } else lines,
            rows = AnswerRecordFacts.auditRows(c, dao, audit),
            open = open, openWith = extras)
    }

    /** A day book row's party is a customer: a supplier's "I got" is goods that came, not money. */
    private fun isCustomerRow(r: DayBook.Row): Boolean = r.customer

    /**
     * Money that came in over a span (today when none was said), by the Insights rule (16 Nov 2026, audit T3 / D-01, the
     * owner: "money received" means customers' real payments): the figure is [KhataDao.customerReceivedBetween] — the same
     * call Insights' "Received today" and the 21:00 closing notice make — so Hudhud and the screen can never disagree. It is
     * customers' "I got" that is money (a goods return carries its goods as lines and is not) and the counter's walk-in
     * payments (cashbook income of category Sale); the owner's own money put in the cashbook is not money received. Before,
     * Hudhud summed the day book's rows itself and counted both. The parts and the ways of paying are those same rows.
     */
    suspend fun collected(c: AnswerEnv, dao: KhataDao, window: VoiceQuestion.Window?, over: Over? = null): AnswerCard {
        val w = window ?: VoiceQuestion.Window.TODAY
        val label = over?.label ?: heading(c, W.hudhud_t_collected, w)
        if (!reports()) return ownerOnly(c, label)
        val (from, to) = over?.span ?: Hudhud.span(w, c.now())
        val oneDay = over?.oneDay ?: (w == VoiceQuestion.Window.TODAY || w == VoiceQuestion.Window.YESTERDAY)
        val total = dao.customerReceivedBetween(from, to)
        val all = DayBook.rows(dao, from, to)
        val khataRows = all.filter { it.kind == DayBook.Kind.GOT && isCustomerRow(it) && !it.opening && dao.itemsOfEntry(it.ref).isEmpty() }
        val counterRows = all.filter { it.kind == DayBook.Kind.CASH_IN && it.who == COUNTER_SALE }
        val rows = khataRows + counterRows
        val khata = khataRows.sumOf { it.amount }
        val counter = counterRows.sumOf { it.amount }
        val words = c.words
        val ways = rows.groupBy { it.method ?: "" }.filterKeys { it.isNotEmpty() }
            .map { (m, list) -> line(PaymentMethod.label(words, m), AnswerBook.money(list.sumOf { it.amount })) }
        val open = if (oneDay) ScreenId.DAY_BOOK else null
        val extras = if (open != null) OpenWith.Day(from) else null
        return AnswerCard(if (Money.isPositive(total)) Mood.GOOD else Mood.CALM, label = label, figure = AnswerBook.money(total),
            lines = listOf(line(c, W.hudhud_l_from_customers, AnswerBook.money(khata)), line(c, W.pos_walk_in, AnswerBook.money(counter))) + ways,
            open = open, openWith = extras, money = true)
    }

    /** The cashbook category the counter writes a walk-in's payment under ([KhataDao.customerReceivedBetween]'s 'Sale'). */
    private const val COUNTER_SALE = "Sale"

    // ------------------------------------------------------------------------------------------------ told, not asked

    /**
     * A question about somebody that names nobody ("uska number", "limit kitni hai"), told rather than asked back (13 Nov
     * 2026, the owner: "aap ka kaam batana hai, poochhna nahi"): the customers dealt with last, each with that fact —
     * his number, his last payment, his credit limit, his promised day — or else his balance. A name in the question
     * gives the one man.
     */
    suspend fun needName(c: AnswerEnv, dao: KhataDao, facet: Hudhud.Facet?, parties: List<PartyWithBalance>): AnswerCard {
        if (facet == Hudhud.Facet.NOTE) return notesList(c, dao, parties)
        val title = when (facet) {
            Hudhud.Facet.PHONE -> W.hudhud_need_phone
            Hudhud.Facet.LAST_PAYMENT -> W.hudhud_need_payment
            Hudhud.Facet.LIMIT -> W.hudhud_need_limit
            Hudhud.Facet.PROMISE -> W.hudhud_need_promise
            else -> W.hudhud_need_recent
        }
        val recent = parties.sortedByDescending { it.lastActivity }.take(TOP)
        if (recent.isEmpty()) {
            return AnswerCard(Mood.CALM, label = c.getString(title), lines = listOf(c.getString(W.voice_no_party)), open = ScreenId.KHATA)
        }
        val secrets = ArrayList<String>()
        val rows = recent.map { p ->
            when (facet) {
                Hudhud.Facet.PHONE -> {
                    val phone = p.phone?.takeIf { it.isNotBlank() }
                    phone?.let { secrets += it }
                    Row(p.id, p.name, phone?.let { Format.ltr(it) } ?: c.getString(W.hudhud_no_phone_short), null)
                }
                Hudhud.Facet.LAST_PAYMENT -> {
                    // A customer pays with an "I got"; the shop pays a supplier with an "I gave".
                    val last = LedgerLines.of(dao, listOf(p.id)).filter { it.isGiven != p.isCustomer }.maxByOrNull { it.timestamp }
                    if (last == null) Row(p.id, p.name, "", c.getString(W.hudhud_no_payment))
                    else Row(p.id, p.name, AnswerBook.money(last.amount), whenOf(c, last.timestamp))
                }
                Hudhud.Facet.LIMIT -> {
                    val limit = dao.getParty(p.id)?.creditLimit?.takeIf { it > 0.0 }
                    if (limit == null) Row(p.id, p.name, "", c.getString(W.hudhud_limit_none))
                    else Row(p.id, p.name, AnswerBook.money(limit), AnswerBook.money(abs(p.balance)) + " \u00B7 " + AnswerBook.side(c, p.balance))
                }
                Hudhud.Facet.PROMISE -> {
                    val day = dao.getParty(p.id)?.harvestPromise
                    if (day == null) Row(p.id, p.name, "", c.getString(W.hudhud_promise_none))
                    else Row(p.id, p.name, Format.dateOnly(day), null)
                }
                else -> Row(p.id, p.name, AnswerBook.money(abs(p.balance)), AnswerBook.side(c, p.balance))
            }
        }
        return AnswerCard(Mood.CALM, label = c.getString(title), lines = listOf(c.getString(W.hudhud_need_hint)), rows = rows,
            open = ScreenId.KHATA, secrets = secrets)
    }

    /**
     * "Whose private note?" told (14 Nov 2026): the customers and suppliers who have one — the ones dealt with last first,
     * [TOP] of them — how many in all, each a tap that asks for his note; and where a note is written when nobody has one.
     * A helper's phone holds no private notes (its copy of the book carries none) and says so instead of "none saved".
     */
    private suspend fun notesList(c: AnswerEnv, dao: KhataDao, parties: List<PartyWithBalance>): AnswerCard {
        val label = c.getString(W.hudhud_need_note)
        val open = ScreenId.KHATA
        if (c.viewer()) {
            return AnswerCard(Mood.CALM, label = label, lines = listOf(c.getString(W.hudhud_note_helper)), open = open)
        }
        val have = dao.partyIdsWithNote().toSet()
        val holders = parties.filter { it.id in have }.sortedByDescending { it.lastActivity }
        if (holders.isEmpty()) {
            return AnswerCard(Mood.CALM, label = label,
                lines = listOf(c.getString(W.hudhud_note_nobody, c.getString(W.edit_details), c.getString(W.party_note_label))),
                open = open)
        }
        return AnswerCard(Mood.CALM, label = label,
            lines = listOf(line(c, W.hudhud_l_note_count, n(holders.size)), c.getString(W.hudhud_need_note_hint)),
            rows = holders.take(TOP).map { p -> Row(p.id, p.name, "", null, ask = Hudhud.Ask.Person(listOf(p.id), Hudhud.Facet.NOTE)) },
            open = open)
    }

    /**
     * What the cashbook took in and paid out over a span (today when none was said): the two sums, the difference, and
     * its own rows, newest first (13 Nov 2026, the owner: "cashbook mein kya aaya, kya gaya"). Money, behind REPORTS.
     */
    suspend fun cashbook(c: AnswerEnv, dao: KhataDao, window: VoiceQuestion.Window?): AnswerCard {
        val w = window ?: VoiceQuestion.Window.TODAY
        val label = heading(c, W.hudhud_t_cashbook, w)
        if (!reports()) return ownerOnly(c, label)
        val (from, to) = Hudhud.span(w, c.now())
        val rows = dao.cashbookRowsBetween(from, to)
        val open = ScreenId.CASHBOOK
        if (rows.isEmpty()) {
            return AnswerCard(Mood.CALM, label = label, lines = listOf(c.getString(W.hudhud_cashbook_none)), open = open)
        }
        val moneyIn = rows.filter { it.isIncome }.sumOf { it.amount }
        val moneyOut = rows.filter { !it.isIncome }.sumOf { it.amount }
        return AnswerCard(Mood.CALM, label = label, figure = AnswerBook.money(moneyIn - moneyOut),
            lines = listOf(
                c.getString(W.hudhud_l_net),
                line(c, W.hudhud_l_money_in, AnswerBook.money(moneyIn)),
                line(c, W.hudhud_l_money_out, AnswerBook.money(moneyOut))
            ),
            rows = rows.sortedByDescending { it.timestamp }.take(TOP).map { r ->
                Row(0, r.category.ifBlank { c.getString(if (r.isIncome) W.hudhud_l_money_in else W.hudhud_l_money_out) },
                    if (r.isIncome) "+" + AnswerBook.money(r.amount) else AnswerBook.money(-r.amount),
                    // Who typed the row (schema 46, the catalog's last cashbook column): only a name the book holds. A row with no
                    // writer is the owner's or older than the column, so it is left unsaid, never guessed (lesson 155).
                    listOfNotNull(whenOf(c, r.timestamp), r.note?.takeIf { it.isNotBlank() },
                        r.createdBy?.takeIf { it.isNotBlank() }?.let { line(c, W.hudhud_l_by, it) },
                        r.paymentRef?.takeIf { it.isNotBlank() }?.let { Format.ltr(it) }).joinToString(" \u00B7 "), open = open)
            },
            open = open, money = true,
            secrets = rows.sortedByDescending { it.timestamp }.take(TOP).mapNotNull { it.paymentRef?.takeIf { ref -> ref.isNotBlank() } })
    }

    /** Goods that came back at the counter over a span (today when none was said): how many returns, and what for. */
    suspend fun returns(c: AnswerEnv, dao: KhataDao, window: VoiceQuestion.Window?): AnswerCard {
        val w = window ?: VoiceQuestion.Window.TODAY
        val label = heading(c, W.hudhud_t_returns, w)
        val (from, to) = Hudhud.span(w, c.now())
        val all = dao.returnsBetween(from, to).filter { it.status == "done" }
        val open = ScreenId.COUNTER_SALE
        if (all.isEmpty()) {
            return AnswerCard(Mood.CALM, label = label, lines = listOf(c.getString(W.hudhud_returns_none)), open = open)
        }
        val money = reports()
        return AnswerCard(Mood.CALM, label = label, figure = n(all.size),
            lines = if (money) listOf(
                line(c, W.hudhud_l_returned_worth, AnswerBook.money(all.sumOf { it.total })),
                line(c, W.hudhud_l_refunded, AnswerBook.money(all.sumOf { it.refundAmount }))
            ) else emptyList(),
            rows = all.take(TOP).map { r ->
                Row(0, r.customerName?.takeIf { it.isNotBlank() } ?: c.getString(W.pos_walk_in), if (money) AnswerBook.money(r.total) else "",
                    listOf(r.returnNumber, whenOf(c, r.timestamp)).joinToString(" \u00B7 "), open = open)
            },
            open = open)
    }

    private val EDITS = setOf(AuditAction.ENTRY_EDITED, AuditAction.CASH_EDITED, AuditAction.PRICE_CHANGED, AuditAction.STOCK_ADJUSTED)
    private val DELETES = setOf(AuditAction.ENTRY_DELETED, AuditAction.CASH_DELETED, AuditAction.PARTY_DELETED)

    /**
     * What sold most over [window] (this month when none is said; 14 Nov 2026, the owner's list: "sab se zyada kya bik
     * raha hai"): Sale Insights' own count ([KhataDao.topProductsBetween]), quantities as sold, never a guess at money.
     */
    suspend fun topGoods(c: AnswerEnv, dao: KhataDao, window: VoiceQuestion.Window?): AnswerCard {
        val w = window ?: VoiceQuestion.Window.THIS_MONTH
        val (from, to) = Hudhud.span(w, c.now())
        val top = dao.topProductsBetween(from, to, TOP)
        val label = heading(c, W.hudhud_t_top_goods, w)
        if (!reports()) return ownerOnly(c, label)
        if (top.isEmpty()) {
            return AnswerCard(Mood.CALM, label = label, lines = listOf(c.getString(W.hudhud_top_goods_none)),
                open = ScreenId.INSIGHTS)
        }
        val rows = top.map { s ->
            Row(0, s.name, Format.qty(s.qty, s.unit), null, open = ScreenId.INSIGHTS)
        }
        return AnswerCard(Mood.GOOD, label = label, figure = top.first().name, rows = rows, open = ScreenId.INSIGHTS)
    }

    /**
     * The Qarz-e-Hasna in the book (14 Nov 2026): lent without profit and still to come back, by Zakat's own split
     * ([KhataDao.observeZakatBalancesByParty]), the five largest by name, and what it is in one plain line.
     */
    suspend fun qarz(c: AnswerEnv, dao: KhataDao, parties: List<PartyWithBalance>): AnswerCard {
        val label = c.getString(W.hudhud_t_qarz)
        val note = c.getString(W.hudhud_qarz_note)
        if (!reports()) return ownerOnly(c, label)
        // Zakat's own rule per customer (audit parity BC-097): a loan paid back as ordinary payments is counted once.
        val owed = dao.observeZakatBalancesByParty().first().map { it.partyId to Zakat.qarzOf(it) }
            .filter { Money.isPositive(it.second) }.sortedByDescending { it.second }
        if (owed.isEmpty()) {
            return AnswerCard(Mood.CALM, label = label, lines = listOf(c.getString(W.hudhud_qarz_none), note),
                open = ScreenId.KHATA)
        }
        val byId = parties.associateBy { it.id }
        val rows = owed.take(TOP).mapNotNull { (id, q) -> byId[id]?.let { p -> Row(p.id, p.name, AnswerBook.money(q), null) } }
        return AnswerCard(Mood.CALM, label = label, figure = AnswerBook.money(owed.sumOf { it.second }), rows = rows,
            lines = listOf(c.quantity(WP.hudhud_qarz_people, owed.size, owed.size), note),
            open = ScreenId.ZAKAT, money = true)
    }

    /**
     * The automations (14 Nov 2026, the owner: "automations ka bhi Hudhud se link"): which are on and at what hour, on
     * this phone. He changes none of them: "Open" shows their own window, where the owner does.
     */
    fun automations(c: AnswerEnv): AnswerCard {
        val settings = c.automationSettings()
        val words = c.words
        val rows = Automations.Kind.values().map { k ->
            val hour = java.util.Calendar.getInstance().apply {
                timeInMillis = c.now()
                set(java.util.Calendar.HOUR_OF_DAY, settings.hour(k)); set(java.util.Calendar.MINUTE, 0)
            }.timeInMillis
            Row(0, words.text(k.word), c.getString(if (settings.isOn(k)) W.hudhud_auto_on else W.hudhud_auto_off),
                if (settings.isOn(k)) c.getString(W.auto_at_time, DateWords.format("h:mm a", hour)) else null)
        }
        return AnswerCard(Mood.CALM, label = c.getString(W.auto_title), rows = rows,
            lines = listOf(c.getString(W.hudhud_auto_line)), automations = true)
    }

    /**
     * A product's fact asked with no product named (14 Nov 2026: "is cheez ka stock kitna hai", "khad kis kis ne li"):
     * he tells which products he can answer for — the month's best sellers first, then the rest — each a tap away.
     */
    suspend fun needGoods(c: AnswerEnv, dao: KhataDao, facet: Hudhud.GoodsFacet, code: String? = null): AnswerCard {
        val products = dao.productsOnce()
        // A code read the other way round (15 Nov 2026): the one product that carries it, as the counter's scan finds it.
        if (facet == Hudhud.GoodsFacet.BARCODE && code != null) {
            val found = ProductFind.byCode(products, code)
            return if (found == null) {
                AnswerCard(Mood.CALM, label = c.getString(W.csv_col_barcode), lines = listOf(c.getString(W.pos_barcode_unknown)),
                    open = ScreenId.PRODUCTS, secrets = listOf(code))
            } else {
                AnswerCard(Mood.CALM, label = found.name, figure = Format.ltr(code), lines = listOf(c.getString(W.csv_col_barcode)),
                    open = ScreenId.PRODUCTS, secrets = listOf(code))
            }
        }
        val label = c.getString(W.hudhud_need_goods)
        if (products.isEmpty()) {
            return AnswerCard(Mood.CALM, label = label, lines = listOf(c.getString(W.no_products)), open = ScreenId.PRODUCTS)
        }
        val (from, to) = Hudhud.span(VoiceQuestion.Window.THIS_MONTH, c.now())
        val best = dao.topProductsBetween(from, to, TOP).map { it.name.lowercase(Locale.ROOT) }
        val ordered = products.sortedWith(compareBy({ best.indexOf(it.name.lowercase(Locale.ROOT)).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it.name }))
        val rows = ordered.take(TOP).map { p -> Row(0, p.name, "", null, ask = Hudhud.Ask.Goods(listOf(p.id), facet)) }
        return AnswerCard(Mood.CALM, label = label, lines = listOf(c.getString(W.hudhud_need_goods_line)), rows = rows)
    }

    // ------------------------------------------------------------------------------------------------ the papers and the schemes

    /**
     * The papers by what they hold (15 Nov 2026, the catalog's records group): the newest supplier bill, purchase order or
     * invoice — of the person named when one is — with its number, note, state and lines, and a few earlier ones as rows.
     * What a paper's lines say is open to whoever opens its screen; a bill's total and rates and an order's rates and writer
     * are the owner's and a manager's (REPORTS). Numbers are shown and never said aloud.
     */
    suspend fun paper(c: AnswerEnv, dao: KhataDao, of: Hudhud.PaperOf, ids: List<Long>, parties: List<PartyWithBalance>): AnswerCard = when (of) {
        Hudhud.PaperOf.BILL -> lastBill(c, dao, ids)
        Hudhud.PaperOf.ORDER -> lastOrder(c, dao, ids)
        Hudhud.PaperOf.INVOICE -> lastInvoice(c, dao, ids, parties)
        Hudhud.PaperOf.SCHEMES -> schemeAnswer(c, dao, dao.observeSchemes().first().filter { !it.isDeleted }, c.getString(W.schemes_title))
    }

    private suspend fun lastBill(c: AnswerEnv, dao: KhataDao, ids: List<Long>): AnswerCard {
        val label = c.getString(W.hudhud_t_last_bill)
        val bills = dao.observeBills().first().filter { ids.isEmpty() || it.partyId in ids }
            .sortedWith(compareByDescending<BillSummary> { it.billDate }.thenByDescending { it.id })
        val bill = bills.firstOrNull()
            ?: return AnswerCard(Mood.CALM, label = label, lines = listOf(c.getString(W.hudhud_none_yet)), open = ScreenId.BILLS)
        val full = dao.getBill(bill.id)
        val items = dao.billItems(bill.id).filter { !it.isDeleted }
        val number = bill.billNumber?.trim()?.takeIf { it.isNotEmpty() }
        val note = bill.note?.trim()?.takeIf { it.isNotEmpty() }
        val batches = items.mapNotNull { it.batchNumber?.trim()?.takeIf { b -> b.isNotEmpty() } }
        val itemLines = items.take(ITEM_LINES).map { i ->
            val rate: Double? = i.rate
            val parts = listOfNotNull(
                Format.goods(i.productName, i.quantity, i.unit),
                i.batchNumber?.takeIf { it.isNotBlank() }?.let { c.getString(W.batch_label, it) },
                i.expiryDate?.let { d -> c.getString(W.expiry_date) + ": " + Format.dateOnly(d) },
                if (reports() && rate != null) "\u00D7 " + AnswerBook.money(rate) else null,
                i.note?.trim()?.takeIf { it.isNotEmpty() }
            )
            Format.isolate(parts.joinToString(" \u00B7 "))
        }
        return AnswerCard(Mood.CALM, label = label + " \u00B7 " + bill.partyName,
            figure = number?.let { Format.ltr(it) } ?: Format.dateOnly(bill.billDate),
            lines = listOfNotNull(
                number?.let { line(c, W.bill_number_hint, it) },
                line(c, W.bill_date, Format.dateOnly(bill.billDate)),
                full?.createdAt?.let { line(c, W.hudhud_l_added, Format.dateOnly(it)) },
                if (reports()) line(c, W.bill_total_hint, AnswerBook.money(bill.totalAmount)) else null,
                note?.let { line(c, W.hudhud_l_entry_note, it) },
            ) + (if (itemLines.isEmpty()) emptyList<String>() else listOf(c.getString(W.hudhud_l_entry_goods)) + itemLines),
            rows = bills.drop(1).take(TOP - 1).map { b ->
                Row(b.partyId, b.partyName, if (reports()) AnswerBook.money(b.totalAmount) else "",
                    listOfNotNull(b.billNumber?.takeIf { it.isNotBlank() }, Format.dateOnly(b.billDate)).joinToString(" \u00B7 "))
            },
            open = ScreenId.BILLS,
            secrets = listOfNotNull(number) + batches + (note?.let { HudhudNote.secretsIn(it) }.orEmpty()))
    }

    private fun orderStatus(c: AnswerEnv, status: String): String = c.getString(
        when (status) {
            OrderStatus.SENT -> W.po_status_sent
            OrderStatus.RECEIVED -> W.po_status_received
            OrderStatus.CLOSED -> W.po_status_closed
            else -> W.po_status_draft
        }
    )

    private suspend fun lastOrder(c: AnswerEnv, dao: KhataDao, ids: List<Long>): AnswerCard {
        val label = c.getString(W.hudhud_t_last_order)
        val orders = dao.observeOrders().first().filter { ids.isEmpty() || it.partyId in ids }
            .sortedWith(compareByDescending<OrderSummary> { it.timestamp }.thenByDescending { it.id })
        val order = orders.firstOrNull()
            ?: return AnswerCard(Mood.CALM, label = label, lines = listOf(c.getString(W.po_none)), open = ScreenId.ORDERS)
        val full = dao.orderWithLines(order.id)
        val lines = full?.items?.filter { !it.isDeleted }.orEmpty()
        val note = full?.order?.note?.trim()?.takeIf { it.isNotEmpty() }
        val writer = if (reports()) full?.order?.createdBy?.takeIf { it.isNotBlank() } else null
        val itemLines = lines.take(ITEM_LINES).map { i ->
            val rate: Double? = i.rate
            Format.isolate(listOfNotNull(
                Format.goods(i.itemName, i.quantity, i.unit),
                if (reports() && rate != null) "\u00D7 " + AnswerBook.money(rate) else null
            ).joinToString(" "))
        }
        return AnswerCard(if (order.status == OrderStatus.DRAFT) Mood.CARE else Mood.CALM, label = label + " \u00B7 " + order.partyName,
            figure = orderStatus(c, order.status),
            lines = listOfNotNull(
                line(c, W.hudhud_l_order_no, order.orderNumber),
                line(c, W.po_supplier, order.partyName),
                line(c, W.hudhud_l_status, orderStatus(c, order.status)),
                line(c, W.date_label, Format.dateOnly(order.timestamp)),
                note?.let { line(c, W.hudhud_l_entry_note, it) },
                writer?.let { line(c, W.hudhud_l_entry_by, it) },
            ) + (if (itemLines.isEmpty()) emptyList<String>() else listOf(c.getString(W.hudhud_l_entry_goods)) + itemLines),
            rows = orders.drop(1).take(TOP - 1).map { o ->
                Row(o.partyId, o.partyName, orderStatus(c, o.status), listOf(o.orderNumber, Format.dateOnly(o.timestamp)).joinToString(" \u00B7 "))
            },
            open = ScreenId.ORDERS,
            secrets = listOf(order.orderNumber) + (note?.let { HudhudNote.secretsIn(it) }.orEmpty()))
    }

    private suspend fun lastInvoice(c: AnswerEnv, dao: KhataDao, ids: List<Long>, parties: List<PartyWithBalance>): AnswerCard {
        val label = c.getString(W.hudhud_t_last_invoice)
        val named = parties.filter { it.id in ids }.map { it.name.trim().lowercase(Locale.ROOT) }.toSet()
        val invoices = dao.observeInvoices().first()
            .filter { it.kind == DocKind.INVOICE && (named.isEmpty() || it.customerName.trim().lowercase(Locale.ROOT) in named) }
            .sortedWith(compareByDescending<InvoiceSummary> { it.invoiceDate }.thenByDescending { it.id })
        val inv = invoices.firstOrNull()
            ?: return AnswerCard(Mood.CALM, label = label, lines = listOf(c.getString(W.hudhud_none_yet)), open = ScreenId.INVOICES)
        val lines = dao.invoiceItems(inv.id).filter { !it.isDeleted }
        val itemLines = lines.take(ITEM_LINES).map { i ->
            Format.isolate(Format.goods(i.itemName, i.quantity, i.unit).orEmpty() + " \u00D7 " + AnswerBook.money(i.rate))
        }
        // The invoice's own record beside its summary (15 Nov 2026, the catalog's invoice columns): the day it was written, the phone
        // printed for the customer, the extra charge with its own label, and the note. The charge and the note can hold sums, so
        // they are told with REPORTS like a cheque's note; the phone is a number, shown and never said.
        val full = dao.getInvoice(inv.id)
        val phoneText = full?.customerPhone?.takeIf { it.isNotBlank() }
        // Across modules there are no smart casts on public vals (lesson): copy to locals first.
        val chargeAmount: Double? = full?.additionalChargeAmount
        val chargeLabel: String? = full?.additionalChargeLabel
        val chargeLine = if (reports() && chargeAmount != null && chargeAmount != 0.0)
            line(chargeLabel?.takeIf { it.isNotBlank() } ?: c.getString(W.pdf_label_extra_charges),
                AnswerBook.money(chargeAmount)) else null
        val noteText = if (reports()) full?.note?.takeIf { it.isNotBlank() } else null
        return AnswerCard(Mood.CALM, label = label + " \u00B7 " + inv.customerName, figure = Format.ltr(inv.invoiceNumber),
            lines = listOf(
                line(c, W.hudhud_l_invoice_no, inv.invoiceNumber),
                line(c, W.date_label, Format.dateOnly(inv.invoiceDate)),
            ) + listOfNotNull(
                full?.let { line(c, W.hudhud_l_added, Format.dateOnly(it.createdAt)) },
                phoneText?.let { line(c.getString(W.hudhud_l_phone), Format.ltr(it)) },
                chargeLine,
                noteText?.let { line(c, W.hudhud_l_entry_note, it) }
            ) + (if (itemLines.isEmpty()) emptyList<String>() else listOf(c.getString(W.invoice_items_label)) + itemLines),
            rows = invoices.drop(1).take(TOP - 1).map { i ->
                Row(0, i.customerName, AnswerBook.money(i.grandTotal), listOf(i.invoiceNumber, Format.dateOnly(i.invoiceDate)).joinToString(" \u00B7 "), open = ScreenId.INVOICES)
            },
            open = ScreenId.INVOICES,
            secrets = listOf(inv.invoiceNumber) + listOfNotNull(phoneText) + noteText?.let { HudhudNote.secretsIn(it) }.orEmpty())
    }

    /** One scheme's reward as the Schemes screen words it: a percent, free units or a fixed amount. */
    private fun rewardText(c: AnswerEnv, s: Scheme, reward: Double): String = when (s.rewardKind) {
        SchemeMath.PERCENT -> c.getString(W.scheme_reward_pct_value, Format.percent(reward))
        SchemeMath.UNITS -> c.getString(W.scheme_reward_units_value, Format.qty(reward, s.unit))
        else -> AnswerBook.money(reward)
    }

    /**
     * The company schemes in [schemes], running first and the soonest to end first: each one's name, its days and whether it
     * runs. How far it has got, its targets and rewards and what was claimed are money of the owner's and a manager's
     * (REPORTS), read by the Schemes screen's own rule ([SchemeMath], [KhataDao.schemePurchased]).
     */
    private suspend fun schemeAnswer(c: AnswerEnv, dao: KhataDao, schemes: List<Scheme>, label: String): AnswerCard {
        if (schemes.isEmpty()) {
            return AnswerCard(Mood.CALM, label = label, lines = listOf(c.getString(W.schemes_empty)), open = ScreenId.SCHEMES)
        }
        val now = c.now()
        val ordered = schemes.sortedWith(compareBy({ it.endDate < now }, { it.endDate }))
        val money = reports()
        fun amountText(s: Scheme, v: Double) = if (s.measure == SchemeMath.BY_VALUE) AnswerBook.money(v) else Format.qty(v, s.unit)
        val shown = ordered.take(TOP)
        val progress = if (money) shown.associate { s ->
            s.id to SchemeMath.progress(s, dao.schemePurchased(s.measure == SchemeMath.BY_VALUE, s.startDate, s.endDate, s.partyId, s.company, s.unit))
        } else emptyMap<Long, SchemeMath.Progress>()
        fun statusOf(s: Scheme): String {
            val p = progress[s.id]
            return when {
                p != null -> when (SchemeMath.status(s, p, now)) {
                    SchemeMath.Status.RUNNING -> c.getString(W.scheme_status_running)
                    SchemeMath.Status.CLAIM_DUE -> c.getString(W.scheme_status_claim_due)
                    SchemeMath.Status.CLAIMED -> c.getString(W.scheme_status_claimed, s.claimedAmount?.let { a -> AnswerBook.money(a) } ?: "")
                    SchemeMath.Status.MISSED -> c.getString(W.scheme_status_missed)
                }
                now <= s.endDate -> c.getString(W.scheme_status_running)
                else -> ""
            }
        }
        fun period(s: Scheme) = c.getString(W.scheme_period, Format.dateOnly(s.startDate), Format.dateOnly(s.endDate))
        val first = shown.first()
        val running = ordered.count { now <= it.endDate }
        val detail = if (!money) emptyList<String>() else listOfNotNull(
            c.getString(W.scheme_slabs_title)
        ) + SchemeMath.slabs(first).map { slab -> Format.isolate(amountText(first, slab.target) + " \u2192 " + rewardText(c, first, slab.reward)) } +
            listOfNotNull(
                progress[first.id]?.let { p -> c.getString(W.scheme_bought, amountText(first, p.purchased)) },
                first.claimedAmount?.let { a ->
                    c.getString(W.scheme_status_claimed, AnswerBook.money(a) + (first.claimedAt?.let { d -> " \u00B7 " + Format.dateOnly(d) } ?: ""))
                },
            )
        return AnswerCard(if (running > 0) Mood.CALM else Mood.CARE, label = label, figure = n(running),
            lines = listOfNotNull(
                Format.isolate(first.name),
                first.company?.trim()?.takeIf { it.isNotEmpty() }?.let { line(c, W.csv_col_company, it) },
                period(first),
                first.note?.trim()?.takeIf { it.isNotEmpty() }?.let { line(c, W.hudhud_l_entry_note, it) },
            ) + detail,
            rows = shown.map { s -> Row(0, s.name, statusOf(s), period(s), open = ScreenId.SCHEMES) },
            open = ScreenId.SCHEMES)
    }

    internal const val TOP = 5
    /** The lines of a paper told on its card: more are on its screen. */
    private const val ITEM_LINES = 8
    /** Every product's sales in the span (the product asked for is found among them by its name). */
    private const val ALL_PRODUCTS = 100_000
    /** The newest audit rows read for a span's edits and deletes: far more than any shop writes in a day. */
    private const val AUDIT_READ = 5_000
}
