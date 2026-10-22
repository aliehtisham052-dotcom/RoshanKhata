package com.innovation313.roshankhata.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.innovation313.roshankhata.ui.Calc
import com.innovation313.roshankhata.ui.NameSearch

/**
 * The ticket being rung up at the counter (POS Phase 1) — the one state
 * both counters draw, the phone's and the computer's. Everything the screen
 * types lands here as text; the figures are read through [Calc] the way
 * the phone's amount pad reads them (sums, Urdu digits), and the totals are
 * [SaleMath]'s, so what is shown is what [KhataDao.postSale] will write.
 *
 * Compose state, so a screen recomposes as the ticket changes; no Android,
 * no Swing — the same class on both.
 */
class CounterDraft {

    class Line(
        val productId: Long?,
        name: String,
        qty: String,
        unit: String?,
        rate: String,
        discount: String = "",
        val billItemId: Long? = null,
        /** What the shelf holds, where the book knows; null = unknown. In the product's own unit. */
        val onHand: Double? = null,
        /** The product rung up (schema 36): its pack and its tax. Null for a line typed free. */
        val product: Product? = null,
        /** The line's tax rate in percent, from its product; null = none of its own. */
        val taxRate: Double? = null,
        /** Whether the line's price already holds its tax (the book's setting when it was rung up). */
        val taxIncluded: Boolean = false,
        /** A weighed item off the scale's label: its own line, never added to by a scan. */
        val weighed: Boolean = false,
        /** The shelf's rate the label was read at ([ScaleCode.Line.shelf]); for a weighed line only. */
        val labelShelf: Double? = null
    ) {
        var name by mutableStateOf(name)
        var qty by mutableStateOf(qty)
        var unit by mutableStateOf(unit)
        var rate by mutableStateOf(rate)
        var discount by mutableStateOf(discount)

        val qtyValue: Double get() = Calc.evalAmount(qty) ?: 0.0
        /** The rate as typed; a rate of 0 is written "0" (a free item), never left empty. */
        val rateValue: Double get() = Calc.evalPad(rate) ?: 0.0
        /** Before any discount: what the line's quantity comes to at its rate. */
        val grossValue: Double get() = SaleMath.lineTotal(qtyValue, rateValue)
        /** In rupees, or "10%" of the line. */
        val discountValue: Double get() = Calc.evalDiscount(discount, grossValue) ?: 0.0
        val total: Double get() = SaleMath.lineTotal(qtyValue, rateValue, discountValue)

        /**
         * No rate, or a rate that is not a figure (21 Oct 2026): until then it
         * counted as 0 and the ticket posted with the goods given away — a
         * typed "1,250" or a forgotten rate on a typed item. Now the ticket
         * waits; a free item is written as 0.
         */
        val rateMissing: Boolean get() = Calc.evalPad(rate) == null
        val discountUnreadable: Boolean get() = discount.isNotBlank() && Calc.evalDiscount(discount, grossValue) == null

        /** The quantity in the product's own unit (a carton of 12 is 12). */
        val baseQty: Double get() = qtyValue * Units.factor(product, unit)

        /** More than the shelf holds, where the shelf is known (in the product's own unit). */
        val shortBy: Double? get() = onHand?.let { if (baseQty > it + 1e-9) baseQty - it else null }

        /** The product is also sold in a pack: the line can switch between its own unit and the pack. */
        val canSwitchUnit: Boolean get() = Units.hasPack(product)

        /** The other unit, for the switch's label. */
        val otherUnit: String? get() = product?.let { p -> if (Units.isPack(p, unit)) p.defaultUnit else p.packUnit }

        /**
         * Between the product's own unit and its pack (schema 36). The rate
         * follows: the pack's price (or the unit rate × the pack size), or
         * back to one unit's. The quantity stays as typed.
         */
        fun switchUnit(unitRate: Double) {
            val p = product ?: return
            if (!Units.hasPack(p)) return
            if (Units.isPack(p, unit)) {
                unit = p.defaultUnit
                rate = Calc.trim(unitRate)
            } else {
                unit = p.packUnit
                rate = Calc.trim(Units.rateFor(p, p.packUnit, unitRate))
            }
        }

        fun toItem() = SaleItem(
            saleId = 0, productId = productId, itemName = name.trim(), quantity = qtyValue,
            unit = unit, rate = rateValue, discount = discountValue, billItemId = billItemId,
            taxRate = taxRate?.takeIf { it > 0.0 }, taxIncluded = if ((taxRate ?: 0.0) > 0.0) taxIncluded else null
        )
    }

    class Payment(method: String, amount: String, reference: String = "") {
        var method by mutableStateOf(method)
        var amount by mutableStateOf(amount)
        var reference by mutableStateOf(reference)
        val amountValue: Double get() = Calc.evalAmount(amount) ?: 0.0
        /** Something typed that is not a figure: it would have gone on the account unseen. */
        val unreadable: Boolean get() = amount.isNotBlank() && Calc.evalAmount(amount) == null
        fun toPayment() = SalePayment(
            saleId = 0, method = method, amount = amountValue, reference = reference.trim().ifBlank { null }
        )
    }

    var partyId by mutableStateOf<Long?>(null)
    var partyName by mutableStateOf<String?>(null)
    val lines = mutableStateListOf<Line>()
    var discount by mutableStateOf("")
    var taxPercent by mutableStateOf("")
    var note by mutableStateOf("")
    val payments = mutableStateListOf<Payment>()
    var roundToWhole by mutableStateOf(false)
    /** The held ticket this draft came off, to drop once it is rung up. */
    var heldId by mutableStateOf<Long?>(null)
    /** The book's setting (schema 36): products' prices already hold their tax. Read by the screen when it opens. */
    var pricesIncludeTax by mutableStateOf(false)

    /**
     * Loyalty (21 Oct 2026): the book's rule (null = off, read by the screen
     * when it opens), the chosen customer's points (read when they are
     * chosen), and how many this ticket spends. Spent points are a discount
     * on the ticket, said apart on the screen and the receipt.
     */
    var loyalty by mutableStateOf<LoyaltyRule?>(null)
    var pointsBalance by mutableStateOf(0.0)
    var pointsUsed by mutableStateOf(0.0)
    val pointsValue: Double get() = if (hasParty) loyalty?.value(pointsUsed) ?: 0.0 else 0.0

    val hasParty: Boolean get() = partyId != null
    val isEmpty: Boolean get() = lines.isEmpty() && payments.isEmpty() && !hasParty

    /** The lines' own totals added up, before the ticket's discount: what "10%" off the ticket is a share of. */
    val linesTotal: Double get() = LineMath.round(lines.sumOf { it.total })

    /** The cashier's discount on the whole ticket, in rupees or "10%" of it (21 Oct 2026). */
    val handTyped: Double get() = Calc.evalDiscount(discount, linesTotal) ?: 0.0

    /** The ticket's tax %: "17" and "17%" alike (until 21 Oct "17%" meant 0.17%). */
    val taxValue: Double? get() = Calc.evalPercent(taxPercent)

    val totals: SaleMath.Totals
        get() = SaleMath.totals(
            lines.map { it.toItem() }, LineMath.round(handTyped + pointsValue),
            taxValue, roundToWhole
        )

    /** The discount the cashier gave, without the points: what the cashier's cap is measured on. */
    val handDiscount: Double get() = LineMath.round(totals.discount - pointsValue)

    /** The most points this ticket may spend: never more than its goods come to after the hand discount. */
    val pointsMax: Double
        get() {
            val rule = loyalty ?: return 0.0
            if (!hasParty) return 0.0
            val before = SaleMath.totals(lines.map { it.toItem() }, handTyped, taxValue, roundToWhole)
            return rule.maxSpend(pointsBalance, LineMath.round(before.subtotal - before.discount))
        }

    /** Spend the customer's points on this ticket (as many as it allows), or none. */
    fun usePoints(on: Boolean) { pointsUsed = if (on) pointsMax else 0.0 }

    /** What the customer handed over, as typed (cash may be more than the ticket: see [change]). */
    val paid: Double get() = SaleMath.paid(payments.map { it.toPayment() })

    /** What is still owed on the ticket after the payments typed so far; below zero, the change. */
    val remaining: Double get() = LineMath.round(totals.grandTotal - paid)

    /**
     * Cash handed back (21 Oct 2026): what was handed over beyond the ticket,
     * when cash covers it — 5,000 in notes for a 4,350 ticket is 650 change.
     * Until then a ticket paid with more than it came to could not be posted
     * at all ("Paid more than the total") while the screen showed the change.
     * Money paid by bank, cheque or online beyond the ticket is still refused:
     * there is no change to give from a transfer.
     */
    val change: Double
        get() {
            val over = LineMath.round(paid - totals.grandTotal)
            if (over <= 0.005) return 0.0
            val cash = LineMath.round(payments.filter { it.method == PaymentMethod.CASH }.sumOf { it.amountValue })
            return if (cash + 0.005 >= over) over else 0.0
        }

    /** A ticket being posted right now: the buttons and keys wait, so one sale is never written twice. */
    var posting by mutableStateOf(false)

    /**
     * The first reason the ticket cannot be posted as it stands, or null: a
     * figure that is not a figure first (21 Oct 2026 — until then a rate,
     * discount, tax or payment that did not read counted as nothing), then
     * the sale's own checks on what will be written.
     */
    val problem: SaleMath.Problem?
        get() = when {
            lines.any { it.rateMissing } -> SaleMath.Problem.RATE_MISSING
            lines.any { it.discountUnreadable } || (discount.isNotBlank() && Calc.evalDiscount(discount, linesTotal) == null) -> SaleMath.Problem.UNREADABLE_DISCOUNT
            taxPercent.isNotBlank() && taxValue == null -> SaleMath.Problem.BAD_TAX
            payments.any { it.unreadable } -> SaleMath.Problem.UNREADABLE_PAYMENT
            else -> SaleMath.check(lines.map { it.toItem() }, toPayments(), totals.grandTotal, hasParty)
        }

    /**
     * The customer's account after this ticket, where it would pass the
     * credit limit the owner set (POS Phase 9): the limit and the balance it
     * would reach, or null when there is no limit or it is kept.
     */
    fun creditOver(balanceNow: Double?, creditLimit: Double?): Pair<Double, Double>? {
        if (!hasParty || creditLimit == null || balanceNow == null) return null
        val after = LineMath.round(balanceNow + remaining)
        return if (after > creditLimit + 0.005) creditLimit to after else null
    }

    /** The ticket's lines that would sell more than the shelf holds. */
    val short: List<Line> get() = lines.filter { it.shortBy != null }

    fun setParty(id: Long?, name: String?) {
        if (id != partyId) { pointsUsed = 0.0; pointsBalance = 0.0 }
        val before = lines.map { it to listRate(it) }
        partyId = id
        partyName = name
        // Lines still at the old list price follow the ticket to its new one (the cash price for a
        // walk-in, the credit price on an account): a customer chosen after the scanning is the
        // usual order at a counter, and a line left at the walk-in price would read as repriced.
        for ((l, old) in before) {
            if (old != null && kotlin.math.abs(l.rateValue - old) <= 0.005) listRate(l)?.let { l.rate = Calc.trim(it) }
        }
    }

    /** The price the counter rings [line] up at, in its unit; null for an item typed in by hand. */
    fun listRate(line: Line): Double? = line.product?.let { p -> Units.rateFor(p, line.unit, unitRate(p)) }

    /**
     * Lines whose price is not the product's own (21 Oct 2026): changed on the
     * ticket, or an item typed in by hand. Until then anyone at the counter
     * could sell a 3,500 bag for 0 with nothing written down; now these need
     * the price permission (or a manager's PIN), and each is logged. A weighed
     * line's price comes off the scale's label: it is a price change only when
     * its money is not the shelf's for its weight, beyond half a gram's worth
     * (22 Oct 2026, the review: a price label typed by hand sold any item for
     * Rs 0.10, with no permission asked and nothing logged).
     */
    val repriced: List<Line>
        get() = lines.filter { l ->
            if (l.weighed) {
                val shelf = l.labelShelf ?: listRate(l) ?: return@filter true
                kotlin.math.abs(l.qtyValue * (l.rateValue - shelf)) > shelf * 0.0005 + 0.01
            } else {
                listRate(l)?.let { kotlin.math.abs(l.rateValue - it) > 0.005 } ?: true
            }
        }

    /** What the repriced lines were and became, for the audit log. */
    fun repricedDetail(): String = repriced.joinToString("; ") { l ->
        l.name.trim() + ": " + (listRate(l)?.let { Calc.trim(it) } ?: "-") + " > " + Calc.trim(l.rateValue)
    }

    /** The goods at their rates before any discount: what the cashier's discount cap is measured on. */
    val grossTotal: Double get() = LineMath.round(lines.sumOf { it.grossValue })

    /** Every discount the cashier gave on this ticket, the lines' and the ticket's (not the points). */
    val allDiscounts: Double get() = LineMath.round(handDiscount + lines.sumOf { it.discountValue })

    /**
     * A product rung up. The same product again adds one to its line rather
     * than opening a second — a scanner beeps once per pack. The rate is the
     * product's counter price (cash) or its credit price when the ticket is
     * on an account and one is set; the owner may change it on the line.
     */
    fun add(product: Product, onHand: Double? = null, qty: Double = 1.0): Line {
        // Only onto a line in the product's own unit (21 Oct 2026): one more bottle scanned onto a
        // line switched to cartons made it a whole carton more, and a weighed line a kilo more.
        lines.firstOrNull { it.productId == product.id && !it.weighed && it.unit == product.defaultUnit }?.let {
            it.qty = Calc.trim(it.qtyValue + qty)
            return it
        }
        val rate = unitRate(product)
        val line = Line(
            product.id, product.name, Calc.trim(qty), product.defaultUnit, if (rate == 0.0) "" else Calc.trim(rate), onHand = onHand,
            product = product, taxRate = product.taxRate, taxIncluded = pricesIncludeTax
        )
        lines += line
        return line
    }

    /**
     * A weighed item from the scale's label ([ScaleCode]): always a line of
     * its own — two bags of tomatoes are two lines, each its own weight.
     */
    fun addWeighed(w: ScaleCode.Line, onHand: Double? = null): Line {
        val line = Line(
            w.product.id, w.product.name, Calc.trim(w.quantity), w.product.defaultUnit, Calc.trim(w.rate), onHand = onHand,
            product = w.product, taxRate = w.product.taxRate, taxIncluded = pricesIncludeTax, weighed = true, labelShelf = w.shelf
        )
        lines += line
        return line
    }

    /** One of [product]'s own unit at this ticket's price: the counter price, or the credit price on an account. */
    fun unitRate(product: Product): Double =
        (if (hasParty) product.creditPrice ?: product.salePrice else product.salePrice ?: product.creditPrice) ?: 0.0

    /** A line typed free, for a thing not in the products list. */
    fun addFree(name: String, unit: String? = null): Line {
        val line = Line(null, name.trim(), "1", unit, "")
        lines += line
        return line
    }

    fun remove(line: Line) { lines.remove(line) }

    fun addPayment(method: String = PaymentMethod.CASH, amount: Double? = null): Payment {
        val p = Payment(method, amount?.let { Calc.trim(it) } ?: "")
        payments += p
        return p
    }

    /** One cash payment for whatever is still owed. */
    fun payRestInCash() {
        val rest = remaining
        if (rest <= 0.0) return
        payments.firstOrNull { it.method == PaymentMethod.CASH && it.amountValue == 0.0 }
            ?.let { it.amount = Calc.trim(rest); return }
        addPayment(PaymentMethod.CASH, rest)
    }

    fun removePayment(p: Payment) { payments.remove(p) }

    fun clear() {
        partyId = null; partyName = null
        lines.clear(); payments.clear()
        discount = ""; taxPercent = ""; note = ""; heldId = null
        pointsUsed = 0.0; pointsBalance = 0.0
    }

    /**
     * The ticket as it stands, taken once on the screen's own thread before
     * anything is written (22 Oct 2026, the review: posting read the lines,
     * then the money a moment later, so an item scanned for the next customer
     * in between made the first one's ticket "overpaid", or was paid by him).
     * [notes] are audit rows written with the sale, in its transaction.
     */
    fun snapshot(createdBy: String? = Access.userName, now: Long = System.currentTimeMillis(), notes: List<AuditLog> = emptyList()): Counter.Ticket =
        Counter.Ticket(
            sale = toSale(createdBy, now), items = toItems(), payments = toPayments(), roundToWhole = roundToWhole,
            pointsUsed = if (hasParty) pointsUsed else 0.0, heldId = heldId, lines = lines.toList(), notes = notes, now = now
        )

    /** After a ticket is posted: cleared, but for the lines rung up while it was being written — the next customer's. */
    fun clearAfter(posted: Counter.Ticket) {
        val next = lines.filter { l -> posted.lines.none { it === l } }
        clear()
        lines += next
    }

    /** A held ticket back on the counter. */
    fun load(held: SaleWithLines, onHandOf: (Long) -> Double? = { null }, productOf: (Long) -> Product? = { null }) {
        clear()
        heldId = held.sale.id
        partyId = held.sale.partyId
        partyName = held.sale.customerName
        note = held.sale.note.orEmpty()
        if (held.sale.discountAmount > 0.0) discount = Calc.trim(held.sale.discountAmount)
        held.sale.taxPercent?.let { taxPercent = Calc.trim(it) }
        for (i in held.items) {
            lines += Line(
                i.productId, i.itemName, Calc.trim(i.quantity), i.unit, Calc.trim(i.rate),
                if (i.discount > 0.0) Calc.trim(i.discount) else "", i.billItemId,
                onHand = i.productId?.let(onHandOf), product = i.productId?.let(productOf),
                taxRate = i.taxRate, taxIncluded = i.taxIncluded == true
            )
        }
    }

    /** The document [KhataDao.postSale] is given; its figures are recomputed there. */
    fun toSale(createdBy: String? = Access.userName, timestamp: Long = System.currentTimeMillis()) = Sale(
        partyId = partyId, customerName = partyName?.trim()?.ifBlank { null },
        // The points spent are part of the ticket's discount (postSale checks them against the customer's points).
        timestamp = timestamp, subtotal = totals.subtotal, discountAmount = LineMath.round(handTyped + pointsValue),
        taxPercent = taxValue?.takeIf { it > 0.0 }, taxAmount = totals.tax,
        roundOff = totals.roundOff, grandTotal = totals.grandTotal, paidTotal = SaleMath.paid(toPayments()),
        note = note.trim().ifBlank { null }, createdBy = createdBy
    )

    fun toItems(): List<SaleItem> = lines.map { it.toItem() }
    /**
     * The payments as the sale keeps them: the change comes off the cash,
     * the last cash payment first, so the book holds what stayed in the
     * drawer (5,000 handed over, 650 back: 4,350 cash).
     */
    fun toPayments(): List<SalePayment> {
        val list = payments.map { it.toPayment() }.filter { it.amount > 0.0 }.toMutableList()
        var give = change
        for (i in list.indices.reversed()) {
            if (give <= 0.005) break
            val p = list[i]
            if (p.method != PaymentMethod.CASH) continue
            val take = minOf(p.amount, give)
            list[i] = p.copy(amount = LineMath.round(p.amount - take))
            give = LineMath.round(give - take)
        }
        return list.filter { it.amount > 0.0 }
    }

    companion object {
        /**
         * The products that answer a counter search: an exact barcode first,
         * then names and companies by the app's one search rule. At most
         * [limit], for a list that fits beside the ticket.
         */
        fun search(products: List<Product>, query: String, limit: Int = 12): List<Product> {
            val q = query.trim()
            if (q.isEmpty()) return products.take(limit)
            products.firstOrNull { it.barcode != null && it.barcode == q }?.let { return listOf(it) }
            val typed = q.lowercase()
            return products.filter {
                NameSearch.matches(it.name, null, typed) || (it.company?.let { c -> NameSearch.matches(c, null, typed) } ?: false)
            }.take(limit)
        }
    }
}

/**
 * The counter's database work, shared by both screens: what a ticket needs
 * read before it is rung up, and the one call that rings it up.
 */
object Counter {

    /** Every live product's stock on hand, by id (null where the units disagree). */
    suspend fun onHand(dao: KhataDao): Map<Long, Double?> =
        Stock.of(dao).associate { it.productId to it.onHand }

    /**
     * The cost a line's profit is measured against, per one of the line's
     * [unit]: the product's own cost, else its weighted average purchase
     * cost, else its last purchase rate ([Cost]) — times the pack size for a pack.
     */
    suspend fun costOf(dao: KhataDao, product: Product?, unit: String? = product?.defaultUnit): Double? =
        Cost.perUnit(dao, product)?.let { LineMath.round(it * Units.factor(product, unit)) }

    /** A line the shelf cannot sell: only expired stock is left of [item] ([fresh] may still go, in its unit). */
    class ExpiredStock(val item: String, val fresh: Double) : IllegalArgumentException("sale refused: expired stock of $item")

    /**
     * Ring the draft up: cost rates filled in per line, each product line's
     * batches chosen first-expiry-first-out ([Fefo]) and an expired batch
     * never sold, the sale posted in one transaction, the held ticket it came
     * off dropped. Returns the sale's id. Throws [ExpiredStock] for a line
     * only expired stock could fill, and IllegalArgumentException with the
     * problem's name if the ticket fails its checks — the screen should have
     * shown [CounterDraft.problem] before offering the button.
     *
     * The ticket carries who rang it up — the signed-in name, the owner as
     * null — so the sales report's cashier rows, the audit and a cashier's
     * shift ([ShiftMath]) all read the same person (until 21 Oct 2026 this
     * default was null and every ticket read as the owner's).
     */
    suspend fun post(dao: KhataDao, draft: CounterDraft, createdBy: String? = Access.userName, now: Long = System.currentTimeMillis()): Long =
        post(dao, draft.snapshot(createdBy, now))

    /** One ticket as the screen took it ([CounterDraft.snapshot]): everything below reads only this. */
    class Ticket(
        val sale: Sale,
        val items: List<SaleItem>,
        val payments: List<SalePayment>,
        val roundToWhole: Boolean,
        val pointsUsed: Double,
        val heldId: Long?,
        val lines: List<CounterDraft.Line>,
        val notes: List<AuditLog> = emptyList(),
        val now: Long = System.currentTimeMillis()
    )

    /** [post] for a ticket already taken from its draft. */
    suspend fun post(dao: KhataDao, ticket: Ticket): Long {
        val now = ticket.now
        val products = dao.productsOnce().associateBy { it.id }
        val today = Fefo.today(now)
        val items = ArrayList<SaleItem>()
        val takes = ArrayList<List<Fefo.Take>?>()
        // Each product's batches as this ticket leaves them (22 Oct 2026, the review): two lines of one product
        // (a carton and loose bottles, two scale labels) were each given the whole of the same batch, so it
        // went below nothing and an expired batch behind it was never refused.
        val lotsOf = HashMap<Long, MutableList<Fefo.Lot>>()
        for (item in ticket.items) {
            val p = item.productId?.let { products[it] }
            items += item.copy(costRate = p?.let { costOf(dao, it, item.unit) })
            if (p == null) { takes += null; continue }
            val lots = lotsOf.getOrPut(p.id) { Fefo.lots(dao, p).toMutableList() }
            val factor = Units.factor(p, item.unit)
            if (item.billItemId != null) {
                // A batch chosen already (a held ticket): never an expired one.
                val at = lots.indexOfFirst { it.billItemId == item.billItemId }
                if (at >= 0 && lots[at].expired(today)) throw ExpiredStock(item.itemName, 0.0)
                if (at >= 0 && !factor.isNaN()) lots[at] = lots[at].copy(left = lots[at].left - item.quantity * factor)
                takes += null
                continue
            }
            when (val a = Fefo.allocate(lots, item.quantity, factor, today)) {
                is Fefo.Allocation.ExpiredOnly -> throw ExpiredStock(item.itemName, a.fresh)
                is Fefo.Allocation.Ok -> {
                    takes += a.takes
                    for (t in a.takes) {
                        val at = lots.indexOfFirst { it.billItemId == t.billItemId }
                        if (t.billItemId != null && at >= 0) lots[at] = lots[at].copy(left = lots[at].left - t.qty * factor)
                    }
                }
            }
        }
        // The held ticket comes off the hook in the sale's own transaction, and its audit notes go with it.
        return dao.postSale(ticket.sale, items, ticket.payments, ticket.roundToWhole, takes,
            pointsUsed = ticket.pointsUsed, heldId = ticket.heldId, notes = ticket.notes)
    }

    /** Park the draft; returns the held row's id. */
    suspend fun hold(dao: KhataDao, draft: CounterDraft, createdBy: String? = Access.userName): Long {
        // Points are spent when the ticket is paid, not while it waits.
        draft.usePoints(false)
        val id = dao.holdSale(draft.toSale(createdBy), draft.toItems())
        draft.heldId?.let { dao.dropHeldSale(it) }
        return id
    }
}
